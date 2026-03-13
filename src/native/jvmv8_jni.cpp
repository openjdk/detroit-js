/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "v8.h"
#include "org_openjdk_engine_javascript_internal_V8.h"

#include <assert.h>
#include <iostream>
#include <memory.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

#include "jvmv8_jni.hpp"
#include "jvmv8_jni_support.hpp"
#include "jvmv8_java_classes.hpp"
#include "jvmv8.hpp"
#include "jvmv8_primitives.hpp"

// max frames to be captured in V8 stack traces
#define MAX_SCRIPT_STACK_TRACE_DEPTH 100

// Debugging routine for displaying Java string from C code
void print(V8Scope& scope, const char* tag, jstring string) {
    if (string) {
        JNIForJS jni(scope);
        const char *cstring = jni.GetStringUTFChars(string);
        if (cstring != nullptr) {
            printf("%s %s\n", tag, cstring);
            fflush(stdout);
            jni.ReleaseStringUTFChars(string, cstring);
        }
    }
}

Isolate* node_isolate = nullptr;

JNIEnv* GetJVMEnv(JavaVM *jvm) {
    assert(jvm != nullptr);
    JNIEnv* env;

    if (jvm->GetEnv((void **)&env, JNI_VERSION_1_8) != JNI_OK || env == nullptr) {
        return nullptr;
    }

    return env;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {

    if (node_isolate) {
        return JNI_VERSION_1_8;
    }

    JNIEnv* env = GetJVMEnv(jvm);
    assert(env != nullptr);
    if (!env) {
        return -1;
    }

    const char* v8_flags = getenv("JVMV8_FLAGS");
    if (v8_flags != nullptr) {
        V8::SetFlagsFromString(v8_flags, (int)strlen(v8_flags));
    }

    init_platform();
    initJVMV8Classes(env);

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNI_OnUnload(JavaVM *jvm, void *reserved) {
    if (node_isolate) {
        return;
    }

    JNIEnv* env = GetJVMEnv(jvm);
    if (!env) {
        return;
    }

    termJVMV8Classes(env);
    term_platform();
}

static ScriptOrigin moduleScriptOrigin(Local<String> name) {
    return ScriptOrigin(name,
        /* int resource_line_offset = */ 0,
        /* int resource_column_offset = */ 0,
        /* bool resource_is_shared_cross_origin = */ false,
        /* int script_id = */ -1,
        /* Local<Value> source_map_url = */ Local<Value>(),
        /* bool resource_is_opaque = */ false, /* bool is_wasm = */ false,
        /* bool is_module = */ true);
}

static MaybeLocal<Module> resolve_module_impl(V8Scope& scope, Local<Context> context, Local<String> specifier,
      Local<FixedArray> import_attributes, bool attribs_have_source) {
    JNI jni(scope);

    int numAttrs = import_attributes->Length();
    int javaAttrs = numAttrs;
    if (attribs_have_source) {
        javaAttrs -= numAttrs / 3;
    }
    jobjectArray jarr = jni.NewObjectArray(javaAttrs, stringClass, nullptr);
    for (int src = 0, dst = 0; src < numAttrs; src += (attribs_have_source ? 3 : 2)) {
        Local<String> key = import_attributes->Get(src).As<String>();
        Local<String> value = import_attributes->Get(src + 1).As<String>();

        jni.SetObjectArrayElement(jarr, dst++, v2j_string(scope, key));
        jni.SetObjectArrayElement(jarr, dst++, v2j_string(scope, value));
    }

    // upcall into Java (don't use the utility method, since it clears the exception)
    JNIEnv* env = scope.getEnv();
    jobject moduleContents = env->CallStaticObjectMethod(v8Class, v8ClassResolveModuleMethodID,
        v2j_string(scope, specifier), jarr);
    jthrowable ex = env->ExceptionOccurred();
    if (ex != nullptr) {
        // propagate this exception back to the JS side
        env->ExceptionClear();
        scope.throwV8Exception(j2v(scope, ex));
        scope.rethrowV8Exception();
        return MaybeLocal<Module>();
    }

    ScriptOrigin origin = moduleScriptOrigin(specifier);
    ScriptCompiler::Source script_source(j2v_string(scope, (jstring) moduleContents), origin);
    return ScriptCompiler::CompileModule(scope.isolate, &script_source);
}

static MaybeLocal<Module> resolve_module(Local<Context> context, Local<String> specifier,
      Local<FixedArray> import_attributes, Local<Module> referrer) {
    TRACE("resolve_module");
    V8Scope scope(Isolate::GetCurrent());
    return resolve_module_impl(scope, context, specifier, import_attributes, true);
}

// using HostImportModuleDynamicallyCallback = MaybeLocal<Promise> (*)(
//     Local<Context> context, Local<Data> host_defined_options,
//     Local<Value> resource_name, Local<String> specifier,
//     Local<FixedArray> import_attributes);
static MaybeLocal<Promise> resolve_module_dynamic(Local<Context> context, Local<Data> host_defined_options,
      Local<Value> resource_name, Local<String> specifier,
      Local<FixedArray> import_attributes) {
    TRACE("resolve_module_dynamic");
    V8Scope scope(Isolate::GetCurrent());

    MaybeLocal<Promise::Resolver> maybeResolver = Promise::Resolver::New(scope.context);
    if (scope.checkV8Exception() || maybeResolver.IsEmpty()) return MaybeLocal<Promise>();

    Local<Promise::Resolver> resolver = maybeResolver.ToLocalChecked();

    MaybeLocal<Module> res = resolve_module_impl(scope, context, specifier, import_attributes, false);
    if (scope.checkV8Exception()) {
        if (resolver->Reject(context, scope.getV8Exception()).IsEmpty()) return MaybeLocal<Promise>();
    } else {
        Local<Module> mod = res.ToLocalChecked();
        (void)mod->InstantiateModule(scope.context, &resolve_module);
        if (scope.checkV8Exception()) {
            if (resolver->Reject(context, scope.getV8Exception()).IsEmpty()) return MaybeLocal<Promise>();
        } else {
            if (resolver->Resolve(context, mod->GetModuleNamespace()).IsEmpty()) return MaybeLocal<Promise>();
        }
    }

    return resolver->GetPromise();
}

JNIEXPORT jlong JNICALL Java_org_openjdk_engine_javascript_internal_V8_createIsolate0
(JNIEnv* env, jclass cls, jboolean javaSupport, jboolean inspector) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_createIsolate0");
    Isolate::CreateParams create_params;
    create_params.array_buffer_allocator = ArrayBuffer::Allocator::NewDefaultAllocator();
    Isolate* isolate = Isolate::New(create_params);
    assert(isolate != nullptr);
    JavaVM *jvm;
    env->GetJavaVM(&jvm);
    JVMV8IsolateData::create(env, isolate, jvm, javaSupport, inspector);

    // without this set V8 does not fill stack trace for exceptions not caught in script
    isolate->SetCaptureStackTraceForUncaughtExceptions(true, MAX_SCRIPT_STACK_TRACE_DEPTH,
        StackTrace::kDetailed);

    isolate->SetHostImportModuleDynamicallyCallback(&resolve_module_dynamic);

    return toReference(isolate);
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_disposeIsolate0
(JNIEnv* env, jclass cls, jlong isolateRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_disposeIsolate0");
    if (isolateRef != 0L) {
        Isolate* isolate = fromReference<Isolate*>(isolateRef);
        {
            V8Scope scope(env, isolate);

            JVMV8IsolateData::destroy(isolate);
        }
        isolate->Dispose();
    }
}

static jobject contextGlobal(JNIEnv* env, Isolate* isolate, Local<Context> context) {
    V8Scope scope(env, isolate, context);
    Local<Object> global = scope.context->Global();
    assert(!global.IsEmpty());

    return v2j_object_object(scope, global);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_createGlobal0
(JNIEnv* env, jclass cls, jlong isolateRef, jboolean setSecurityToken, jstring contextName) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_createGlobal0");
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);

    Local<ObjectTemplate> globalTemplate = JVMV8IsolateData::getGlobalTemplate(isolate);
    assert(!globalTemplate.IsEmpty());
    Local<Context> context = Context::New(isolate, nullptr, globalTemplate);
    assert(!context.IsEmpty());

    JVMV8IsolateData::inspectorContextCreated(isolate, context, contextName == nullptr? Local<String>() : j2v_string(scope, contextName));

    if (JVMV8IsolateData::hasJavaSupport(scope.isolate)) {
        V8Scope scope(env, isolate, context);

        run_boot_script(scope);

        if (scope.handledV8Exception()) return 0L;
    }

    if (setSecurityToken) {
        Global<Value>& securityToken = JVMV8IsolateData::getSecurityToken(isolate);
        if (securityToken.IsEmpty()) {
            // first Context - save security token for future use
            securityToken.Reset(isolate, context->GetSecurityToken());
        } else {
            context->SetSecurityToken(securityToken.Get(isolate));
        }
    }

    return contextGlobal(env, isolate, context);
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_releaseReference0
  (JNIEnv* env, jclass cls, jlong isolateRef, jlong ref) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_releaseReference0");
    if (ref != 0L) {
        V8Scope scope(env, fromReference<Isolate*>(isolateRef), Local<Context>());
        Global<Value>* value = fromReference<Global<Value>*>(ref);
        delete value; // delete the handle
    }
}

static jobjectArray stackTraceAsJavaArray(V8Scope& scope, JNIForJava& jni, Local<StackTrace> stackTrace) {
    if (stackTrace.IsEmpty()) return nullptr;
    int frameCount = stackTrace->GetFrameCount();
    jobjectArray frames = jni.NewObjectArray(frameCount, stackTraceElementClass, nullptr);
    if (jni.checkException() || frames == nullptr) return nullptr;

    for (int index = 0; index < frameCount; index++) {
        Local<StackFrame> stackFrame = stackTrace->GetFrame(scope.isolate, index);
        if (stackFrame.IsEmpty()) return nullptr;

        int lineNumber = stackFrame->GetLineNumber();
        int column = stackFrame->GetColumn();
        bool isEval = stackFrame->IsEval();
        bool isConstructor = stackFrame->IsConstructor();

        // class name does not make sense for V8 frames. But, we can use it to stuff
        // additional details like isEval, isConstructor, line and column numbers.
        const char *functype = isEval? "V8-eval" : (isConstructor? "V8-new" : "V8");
        char nameBuf[100];
        snprintf(nameBuf, sizeof(nameBuf), "<%s @ %d:%d>", functype, lineNumber, column);
        jstring className = jni.NewStringUTF(nameBuf);

        Local<String> functionName = stackFrame->GetFunctionName();
        jstring methodName = functionName.IsEmpty() ? jni.NewStringUTF("") : v2j_string(scope, functionName);

        Local<String> scriptName = stackFrame->GetScriptNameOrSourceURL();
        jstring fileName = scriptName.IsEmpty()? jni.NewStringUTF("") : v2j_string(scope, scriptName);

        jobject frame = jni.NewObject(stackTraceElementClass, stackTraceElementClassInitMethodID,
            className, methodName, fileName, lineNumber);
        if (jni.checkException() || frame == nullptr) return nullptr;

        jni.SetObjectArrayElement(frames, index, frame);
        if (jni.checkException()) return nullptr;
    }

    return frames;
}

JNIEXPORT jobjectArray JNICALL Java_org_openjdk_engine_javascript_internal_V8_getStackTrace0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong exceptionRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getStackTrace0");
    assert(isolateRef != 0L);
    assert(exceptionRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    JNIForJava jni(scope);
    Local<Value> exception = j2v_reference(scope, exceptionRef);

    return stackTraceAsJavaArray(scope, jni, Exception::GetStackTrace(exception));
}

JNIEXPORT jobjectArray JNICALL Java_org_openjdk_engine_javascript_internal_V8_getStackTrace1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getStackTrace1");
    assert(isolateRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    JNIForJava jni(scope);

    int options = StackTrace::kDetailed | StackTrace::kExposeFramesAcrossSecurityOrigins;
    Local<StackTrace> stackTrace = StackTrace::CurrentStackTrace(scope.isolate,
        MAX_SCRIPT_STACK_TRACE_DEPTH,
        static_cast<StackTrace::StackTraceOptions>(options));
    return stackTraceAsJavaArray(scope, jni, stackTrace);
}

JNIEXPORT jstring JNICALL Java_org_openjdk_engine_javascript_internal_V8_toString0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong ref) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_toString0");
    assert(isolateRef != 0L);
    assert(ref != 0L);
    V8Scope scope(env, isolateRef, ref);
    Local<Value> value = j2v_reference(scope, ref);

    // For objects, convert by calling ToString here so that any script exception
    // can be propagated to the caller!
    if (value->IsObject()) {
        MaybeLocal<String> maybeString = value->ToString(scope.context);
        if (scope.handledV8Exception() || maybeString.IsEmpty()) return nullptr;
        value = maybeString.ToLocalChecked();
    }

    return v2j_string(scope, value);
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_strictEquals0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong ref1, jlong ref2) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_strictEquals0");
    assert(isolateRef != 0L);
    assert(ref1 != 0);
    assert(ref2 != 0);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    Local<Value> value1 = j2v_reference(scope, ref1);
    Local<Value> value2 = j2v_reference(scope, ref2);

    return value1->StrictEquals(value2);
}

JNIEXPORT jlong JNICALL Java_org_openjdk_engine_javascript_internal_V8_compile0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jstring name, jstring code) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_compile0");
    assert(isolateRef != 0L);
    assert(name != nullptr);
    assert(code != nullptr);
    V8Scope scope(env, isolateRef, globalRef);
    Local<String> scriptString = j2v_string(scope, code);
    ScriptOrigin origin(j2v_string(scope, name));

    MaybeLocal<Script> maybeScript = Script::Compile(scope.context, scriptString, &origin);
    if (scope.handledV8Exception()) return 0L;
    return v2j_unboundscript(scope, maybeScript);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_compileFunctionInContext0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jstring name, jstring code,
        jobjectArray arguments, jobjectArray extensions) {
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(name != nullptr);
    assert(code != nullptr);
    V8Scope scope(env, isolateRef, globalRef);
    JNIForJava jni(scope);

    Local<String> scriptString = j2v_string(scope, code);
    const ScriptOrigin origin(j2v_string(scope, name));
    ScriptCompiler::Source source(scriptString, origin);

    jsize numArguments = arguments == nullptr? 0 : jni.GetArrayLength(arguments);
    jsize numExtensions = extensions == nullptr? 0 : jni.GetArrayLength(extensions);

    std::unique_ptr<Local<String>[]> jsArguments(numArguments == 0? nullptr : new Local<String>[numArguments]);
    std::unique_ptr<Local<Object>[]> jsExtensions(numExtensions == 0? nullptr : new Local<Object>[numExtensions]);

    // convert arguments
    for (int i = 0; i < numArguments; i++) {
        jsArguments[i] = j2v_string(scope, (jstring)jni.GetObjectArrayElement(arguments, i));
        if (jni.checkException()) return nullptr;
    }

    // convert extension objects
    for (int i = 0; i < numExtensions; i++) {
       jobject arrayElement = jni.GetObjectArrayElement(extensions, i);
       if (jni.checkException()) return nullptr;

       Local<Value> arrayElemVal = j2v_java_wrap(scope, arrayElement);
       if (jni.checkException() || arrayElemVal.IsEmpty()) return nullptr;

       jsExtensions[i] = arrayElemVal.As<Object>();
    }

    // compile in function in context
    MaybeLocal<Function> maybeFunction = ScriptCompiler::CompileFunction(scope.context,
        &source, numArguments, jsArguments.get(), numExtensions, jsExtensions.get());

    if (scope.handledV8Exception() || maybeFunction.IsEmpty()) return nullptr;

    Local<Function> function = maybeFunction.ToLocalChecked();
    return v2j_function_object(scope, function);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_loadModule0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jstring name, jstring code) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_loadModule0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(name != nullptr);
    assert(code != nullptr);
    V8Scope scope(env, isolateRef, globalRef);
    JNIForJS jni(scope);
    JNILocalFrame locals(jni);
    Local<String> scriptString = j2v_string(scope, code);
    ScriptOrigin origin = moduleScriptOrigin(j2v_string(scope, name));
    ScriptCompiler::Source script_source(scriptString, origin);
    MaybeLocal<Module> maybeModule = ScriptCompiler::CompileModule(scope.isolate, &script_source);
    if (scope.handledV8Exception() || maybeModule.IsEmpty()) return nullptr;

    Local<Module> module = maybeModule.ToLocalChecked();
    Maybe<bool> instResult = module->InstantiateModule(scope.context, &resolve_module);
    if (scope.handledV8Exception() || instResult.IsEmpty()) return nullptr;

    MaybeLocal<Value> maybeResult = module->Evaluate(scope.context);
    if (scope.handledV8Exception()) return nullptr;

    Local<Object> ns = module->GetModuleNamespace().As<Object>();

    return locals.pop(v2j_java_unwrap(scope, ns));
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_eval0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jstring name, jstring code) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_eval0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(name != nullptr);
    assert(code != nullptr);
    V8Scope scope(env, isolateRef, globalRef);
    JNIForJS jni(scope);
    JNILocalFrame locals(jni);
    Local<String> scriptString = j2v_string(scope, code);
    ScriptOrigin origin(j2v_string(scope, name));
    MaybeLocal<Script> maybeScript = Script::Compile(scope.context, scriptString, &origin);
    if (scope.handledV8Exception() || maybeScript.IsEmpty()) return nullptr;

    Local<Script> script = maybeScript.ToLocalChecked();
    MaybeLocal<Value> maybeResult = script->Run(scope.context);
    if (scope.handledV8Exception()) return nullptr;

    return locals.pop(v2j_java_unwrap(scope, maybeResult));
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_eval1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jlong unboundScriptRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_eval1");
    assert(isolateRef != 0L);
    assert(globalRef != 0);
    assert(unboundScriptRef != 0);
    V8Scope scope(env, isolateRef, globalRef);
    Local<UnboundScript> unboundScript = j2v_unboundscript(scope, unboundScriptRef);
    MaybeLocal<Value> maybeResult = unboundScript->BindToCurrentContext()->Run(scope.context);
    if (scope.handledV8Exception()) return nullptr;

    return v2j_java_unwrap(scope, maybeResult);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_invoke0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject self, jobjectArray argObjects) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_invoke0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);
    JNILocalFrame locals(jni);

    Local<Object> obj = j2v_reference(scope, objectRef).As<Object>();
    Local<Value> thiz = j2v_java_wrap(scope, self);
    if (jni.checkException() || thiz.IsEmpty()) return nullptr;

    jsize length = argObjects == nullptr? 0 : jni.GetArrayLength(argObjects);
    std::unique_ptr<Local<Value>[]> args(new Local<Value>[length]);

    for (int i = 0; i < length; i++) {
        jobject arrayElement = jni.GetObjectArrayElement(argObjects, i);
        if (jni.checkException()) return nullptr;

        args[i] = j2v_java_wrap(scope, arrayElement);
        if (jni.checkException() || args[i].IsEmpty()) return nullptr;
    }

    MaybeLocal<Value> maybeResult = obj->CallAsFunction(scope.context, thiz, length, args.get());
    if (scope.handledV8Exception()) return nullptr;

    return locals.pop(v2j_java_unwrap(scope, maybeResult));
}

JNIEXPORT jstring JNICALL Java_org_openjdk_engine_javascript_internal_V8_getFunctionName0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong functionRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getFunctionName0");
    assert(isolateRef != 0L);
    assert(functionRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);

    Local<Value> name = j2v_reference(scope, functionRef).As<Function>()->GetName();
    return v2j_string(scope, name);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newObject0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobjectArray argObjects) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newObject0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);
    JNILocalFrame locals(jni);

    Local<Object> obj  = j2v_reference(scope, objectRef).As<Object>();
    jsize length = argObjects == nullptr? 0 : jni.GetArrayLength(argObjects);
    std::unique_ptr<Local<Value>[]> args(new Local<Value>[length]);

    for (int i = 0; i < length; i++) {
        jobject arrayElement = jni.GetObjectArrayElement(argObjects, i);
        if (jni.checkException()) return nullptr;

        args[i] = j2v_java_wrap(scope, arrayElement);
        if (jni.checkException() || args[i].IsEmpty()) return nullptr;
    }

    MaybeLocal<Value> maybeResult = obj->CallAsConstructor(scope.context, length, args.get());
    if (scope.handledV8Exception()) return nullptr;

    return locals.pop(v2j(scope, maybeResult));
}

JNIEXPORT jstring JNICALL Java_org_openjdk_engine_javascript_internal_V8_getConstructorName0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getConstructorName0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);

    Local<Value> local = j2v_reference(scope, objectRef);
    assert(!local.IsEmpty() && local->IsObject());

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_string(scope, Local<String>());
    Local<Object> object = maybeObject.ToLocalChecked();

    Local<String> name = object->GetConstructorName();

    return v2j_string(scope, name);
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_isCallable0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_isCallable0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);

    Local<Value> local = j2v_reference(scope, objectRef);
    assert(!local.IsEmpty() && local->IsObject());

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return JNI_FALSE;
    Local<Object> object = maybeObject.ToLocalChecked();

    return object->IsCallable()? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_get0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jstring keyString) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_get0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(keyString != nullptr);
    V8Scope scope(env, isolateRef, objectRef);

    Local<Value> local = j2v_reference(scope, objectRef);
    assert(!local.IsEmpty());

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    Local<String> key = j2v_string(scope, keyString);
    MaybeLocal<Value> result = object->Get(scope.context, key);
    if (scope.handledV8Exception() || result.IsEmpty()) return v2j_undefined(scope);

    return v2j_java_unwrap(scope, result);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_get1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jint index) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_get1");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Value> result = object->Get(scope.context, index);
    if (scope.handledV8Exception() || result.IsEmpty()) return v2j_undefined(scope);

    return v2j_java_unwrap(scope, result);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_get2
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject keyObject) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_get2");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    Local<Value> key = j2v_java_wrap(scope, keyObject);
    if (jni.checkException() || key.IsEmpty()) return v2j_undefined(scope);

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Value> result = object->Get(scope.context, key);
    if (scope.handledV8Exception() || result.IsEmpty()) return v2j_undefined(scope);

    return v2j_java_unwrap(scope, result);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_put0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jstring keyString, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_put0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(keyString != nullptr);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    Local<String> key = j2v_string(scope, keyString);

    MaybeLocal<Value> oldValue = object->Get(scope.context, key);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    Local<Value> newValue = j2v_java_wrap(scope, value);
    if (jni.checkException() || newValue.IsEmpty()) return v2j_undefined(scope);

    Maybe<bool> unused = object->Set(scope.context, key, newValue);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    return v2j_java_unwrap(scope, oldValue);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_put1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jint index, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_put1");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Value> oldValue = object->Get(scope.context, index);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    Local<Value> newValue = j2v_java_wrap(scope, value);
    if (jni.checkException() || newValue.IsEmpty()) return v2j_undefined(scope);

    Maybe<bool> unused = object->Set(scope.context, index, newValue);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    return v2j_java_unwrap(scope, oldValue);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_put2
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject keyObject, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_put2");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    Local<Value> key = j2v_java_wrap(scope, keyObject);
    if (jni.checkException() || key.IsEmpty()) return v2j_undefined(scope);

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Value> oldValue = object->Get(scope.context, key);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    Local<Value> newValue = j2v_java_wrap(scope, value);
    if (jni.checkException() || newValue.IsEmpty()) return v2j_undefined(scope);

    Maybe<bool> unused = object->Set(scope.context, key, newValue);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    return v2j_java_unwrap(scope, oldValue);
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_set0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jstring keyString, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_set0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(keyString != nullptr);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (local->IsObject()) {
        Local<String> key = j2v_string(scope, keyString);

        Local<Value> newValue = j2v_java_wrap(scope, value);
        if (jni.checkException() || newValue.IsEmpty()) return JNI_FALSE;

        MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
        if (maybeObject.IsEmpty()) return JNI_FALSE;
        Local<Object> object = maybeObject.ToLocalChecked();

        Maybe<bool> maybeBool = object->Set(scope.context, key, newValue);
        if (scope.handledV8Exception()) return JNI_FALSE;

        return maybeBool.FromMaybe(false)? JNI_TRUE : JNI_FALSE;
    }

    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_set1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jint index, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_set1");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(0 <= index);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (local->IsObject()) {
        Local<Value> newValue = j2v_java_wrap(scope, value);
        if (jni.checkException() || newValue.IsEmpty()) return JNI_FALSE;

        MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
        if (maybeObject.IsEmpty()) return JNI_FALSE;
        Local<Object> object = maybeObject.ToLocalChecked();

        Maybe<bool> maybeBool = object->Set(scope.context, index, newValue);
        if (scope.handledV8Exception()) return JNI_FALSE;

        return maybeBool.FromMaybe(false)? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_set2
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject keyObject, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_set2");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (local->IsObject()) {
        Local<Value> key = j2v_java_wrap(scope, keyObject);
        if (jni.checkException() || key.IsEmpty()) return JNI_FALSE;

        Local<Value> newValue = j2v_java_wrap(scope, value);
        if (jni.checkException() || newValue.IsEmpty()) return JNI_FALSE;

        MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
        if (maybeObject.IsEmpty()) return JNI_FALSE;
        Local<Object> object = maybeObject.ToLocalChecked();

        Maybe<bool> maybeBool = object->Set(scope.context, key, newValue);
        if (scope.handledV8Exception()) return JNI_FALSE;

        return maybeBool.FromMaybe(false)? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_contains0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jstring keyString) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_contains0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(keyString != nullptr);
    V8Scope scope(env, isolateRef, objectRef);
    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return JNI_FALSE;
    }

    Local<String> key = j2v_string(scope, keyString);
    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return JNI_FALSE;
    Local<Object> object = maybeObject.ToLocalChecked();

    Maybe<bool> result = object->Has(scope.context, key);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return result.IsJust() && result.FromJust();
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_contains1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jint index) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_contains1");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return JNI_FALSE;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return JNI_FALSE;
    Local<Object> object = maybeObject.ToLocalChecked();

    Maybe<bool> result = object->Has(scope.context, index);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return result.IsJust() && result.FromJust();
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_contains2
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject keyObject) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_contains2");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return JNI_FALSE;
    }

    Local<Value> key = j2v_java_wrap(scope, keyObject);
    if (jni.checkException() || key.IsEmpty()) return JNI_FALSE;

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return JNI_FALSE;
    Local<Object> object = maybeObject.ToLocalChecked();

    Maybe<bool> result = object->Has(scope.context, key);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return result.IsJust() && result.FromJust();
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_remove0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jstring keyString) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_remove0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    Local<String> key = j2v_string(scope, keyString);

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Value> oldValue = object->Get(scope.context, key);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    Maybe<bool> result = object->Delete(scope.context, key);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    return result.IsJust() ? v2j_java_unwrap(scope, oldValue) : v2j_undefined(scope);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_remove1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jint index) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_remove1");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Value> oldValue = object->Get(scope.context, index);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    Maybe<bool> result = object->Delete(scope.context, index);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    return result.IsJust() ? v2j_java_unwrap(scope, oldValue) : v2j_undefined(scope);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_remove2
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject keyObject) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_remove2");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return v2j_undefined(scope);
    }

    Local<Value> key = j2v_java_wrap(scope, keyObject);
    if (jni.checkException() || key.IsEmpty()) return v2j_undefined(scope);

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return v2j_undefined(scope);
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Value> oldValue = object->Get(scope.context, key);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    Maybe<bool> result = object->Delete(scope.context, key);
    if (scope.handledV8Exception()) return v2j_undefined(scope);

    return result.IsJust() ? v2j_java_unwrap(scope, oldValue) : v2j_undefined(scope);
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_delete0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jstring keyString) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_delete0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(keyString != nullptr);
    V8Scope scope(env, isolateRef, objectRef);
    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return JNI_FALSE;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return JNI_FALSE;
    Local<Object> object = maybeObject.ToLocalChecked();

    Local<String> key = j2v_string(scope, keyString);

    Maybe<bool> result = object->Delete(scope.context, key);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return result.FromMaybe(false)? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_delete1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jint index) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_delete1");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return JNI_FALSE;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return JNI_FALSE;
    Local<Object> object = maybeObject.ToLocalChecked();

    Maybe<bool> result = object->Delete(scope.context, index);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return result.FromMaybe(false)? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_delete2
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject keyObject) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return JNI_FALSE;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return JNI_FALSE;
    Local<Object> object = maybeObject.ToLocalChecked();

    Local<Value> key = j2v_java_wrap(scope, keyObject);
    if (jni.checkException() || key.IsEmpty()) return JNI_FALSE;

    Maybe<bool> result = object->Delete(scope.context, key);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return result.FromMaybe(false);
}

JNIEXPORT jint JNICALL Java_org_openjdk_engine_javascript_internal_V8_length0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_length0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsArray()) {
        return 0;
    }

    Local<Object> array = local->ToObject(scope.context).ToLocalChecked();
    return array.As<Array>()->Length();
}

JNIEXPORT jint JNICALL Java_org_openjdk_engine_javascript_internal_V8_propertyAttributes0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jstring name) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_propertyAttributes0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(name != nullptr);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return 0;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return 0;
    Local<Object> object = maybeObject.ToLocalChecked();

    Maybe<v8::PropertyAttribute> maybeAttr = object->GetPropertyAttributes(scope.context, j2v_string(scope, name));
    if (scope.handledV8Exception() || maybeAttr.IsNothing()) return 0;

    return static_cast<int>(maybeAttr.FromJust());
}

JNIEXPORT int JNICALL Java_org_openjdk_engine_javascript_internal_V8_propertyAttributes1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jint index) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_propertyAttributes1");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return 0;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return 0;
    Local<Object> object = maybeObject.ToLocalChecked();

    Maybe<v8::PropertyAttribute> maybeAttr = object->GetPropertyAttributes(scope.context, scope.integer(index));
    if (scope.handledV8Exception() || maybeAttr.IsNothing()) return 0;

    return static_cast<int>(maybeAttr.FromJust());
}

JNIEXPORT jint JNICALL Java_org_openjdk_engine_javascript_internal_V8_propertyAttributes2
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef, jobject keyObject) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(keyObject != nullptr);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return 0;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return 0;
    Local<Object> object = maybeObject.ToLocalChecked();

    Local<Value> key = j2v_java_wrap(scope, keyObject);
    if (jni.checkException() || key.IsEmpty()) return 0;

    Maybe<v8::PropertyAttribute> maybeAttr = object->GetPropertyAttributes(scope.context, key);
    if (scope.handledV8Exception() || maybeAttr.IsNothing()) return 0;

    return static_cast<int>(maybeAttr.FromJust());
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_defineOwnProperty0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong objectRef, jstring name, jobject value, jint flags) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);
    if (local->IsObject()) {
        Local<String> key = j2v_string(scope, name);

        Local<Value> newValue = j2v_java_wrap(scope, value);
        if (jni.checkException() || newValue.IsEmpty()) return JNI_FALSE;

        MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
        if (maybeObject.IsEmpty()) return JNI_FALSE;
        Local<Object> object = maybeObject.ToLocalChecked();

        Maybe<bool> maybeBool = object->DefineOwnProperty(scope.context, key, newValue,
             static_cast<v8::PropertyAttribute>(flags));
        if (scope.handledV8Exception() || maybeBool.IsNothing()) return JNI_FALSE;

        return maybeBool.FromJust()? JNI_TRUE : JNI_FALSE;
    }

    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_defineOwnProperty1
(JNIEnv *env, jclass cls, jlong isolateRef, jlong objectRef, jlong symbolRef, jobject value, jint flags) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);
    if (local->IsObject()) {
        Local<Symbol> key = j2v_reference(scope, symbolRef).As<Symbol>();

        Local<Value> newValue = j2v_java_wrap(scope, value);
        if (jni.checkException() || newValue.IsEmpty()) return JNI_FALSE;

        MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
        if (maybeObject.IsEmpty()) return JNI_FALSE;
        Local<Object> object = maybeObject.ToLocalChecked();

        Maybe<bool> maybeBool = object->DefineOwnProperty(scope.context, key, newValue,
             static_cast<v8::PropertyAttribute>(flags));
        if (scope.handledV8Exception() || maybeBool.IsNothing()) return JNI_FALSE;

        return maybeBool.FromJust()? JNI_TRUE : JNI_FALSE;
    }

    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_setAccessorProperty0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong objectRef, jstring name, jobject get, jobject set, jint flags) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(name != nullptr);
    assert(get != nullptr);

    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);
    if (local->IsObject()) {
        Local<String> key = j2v_string(scope, name);
        Local<Function> getterFunc = j2v(scope, get).As<Function>();
        Local<Function> setterFunc = (set == nullptr)? Local<Function>() : j2v(scope, set).As<Function>();

        MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
        if (maybeObject.IsEmpty()) return JNI_FALSE;
        Local<Object> object = maybeObject.ToLocalChecked();

        object->SetAccessorProperty(key, getterFunc,
             setterFunc, static_cast<v8::PropertyAttribute>(flags));
        return scope.handledV8Exception()? JNI_FALSE : JNI_TRUE;
    }

    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_setAccessorProperty1
(JNIEnv *env, jclass cls, jlong isolateRef, jlong objectRef, jlong symbolRef, jobject get, jobject set, jint flags) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    assert(symbolRef != 0L);
    assert(get != nullptr);

    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);
    if (local->IsObject()) {
        Local<Symbol> key = j2v_reference(scope, symbolRef).As<Symbol>();
        Local<Function> getterFunc = j2v(scope, get).As<Function>();
        Local<Function> setterFunc = (set == nullptr)? Local<Function>() : j2v(scope, set).As<Function>();

        MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
        if (maybeObject.IsEmpty()) return JNI_FALSE;
        Local<Object> object = maybeObject.ToLocalChecked();

        object->SetAccessorProperty(key, getterFunc,
             setterFunc, static_cast<v8::PropertyAttribute>(flags));
        return scope.handledV8Exception()? JNI_FALSE : JNI_TRUE;
    }

    return JNI_FALSE;
}

// caller has to check null and return an empty array as appropriate
JNIEXPORT jobjectArray JNICALL Java_org_openjdk_engine_javascript_internal_V8_keys0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_keys0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return nullptr;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return nullptr;
    Local<Object> object = maybeObject.ToLocalChecked();

    MaybeLocal<Array> maybeArray = object->GetPropertyNames(scope.context);
    if (scope.handledV8Exception() || maybeArray.IsEmpty()) return nullptr;

    Local<Array> array = maybeArray.ToLocalChecked();

    int length = array->Length();
    jobjectArray keys = jni.NewObjectArray(length, objectClass, nullptr);
    if (jni.checkException() || keys == nullptr) return nullptr;

    for (int i = 0; i < length; i++) {
        MaybeLocal<Value> key = array->Get(scope.context, i);
        if (scope.handledV8Exception() || key.IsEmpty()) return nullptr;

        jni.SetObjectArrayElement(keys, i, v2j_java_unwrap(scope, key));
        if (jni.checkException()) return nullptr;
    }

    return keys;
}

// caller has to check null and return an empty array as appropriate
JNIEXPORT jobjectArray JNICALL Java_org_openjdk_engine_javascript_internal_V8_namedKeys0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_namedKeys0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return nullptr;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return nullptr;
    Local<Object> object = maybeObject.ToLocalChecked();

    // Get only enumerable non-index properties (all inherited properties included)
    MaybeLocal<Array> maybeArray = object->GetPropertyNames(scope.context,
        v8::KeyCollectionMode::kIncludePrototypes,
        static_cast<v8::PropertyFilter>(v8::ONLY_ENUMERABLE|v8::SKIP_SYMBOLS),
        v8::IndexFilter::kSkipIndices);
    if (scope.handledV8Exception() || maybeArray.IsEmpty()) return nullptr;

    Local<Array> array = maybeArray.ToLocalChecked();

    int length = array->Length();
    jobjectArray names = jni.NewObjectArray(length, stringClass, nullptr);
    if (jni.checkException()) return nullptr;

    for (int i = 0; i < length; i++) {
        MaybeLocal<Value> key = array->Get(scope.context, i);
        if (scope.handledV8Exception() || key.IsEmpty()) return nullptr;

        Local<Value> name = key.ToLocalChecked();
        jni.SetObjectArrayElement(names, i, v2j_string(scope, name));
        if (jni.checkException()) return nullptr;
    }

    return names;
}

// caller has to check null and return an empty array as appropriate
JNIEXPORT jintArray JNICALL Java_org_openjdk_engine_javascript_internal_V8_indexedKeys0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_indexedKeys0");
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return nullptr;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return nullptr;
    Local<Object> object = maybeObject.ToLocalChecked();

    // Get only enumerable indexed properties (all inherited properties included)
    MaybeLocal<Array> maybeArray = object->GetPropertyNames(scope.context,
        v8::KeyCollectionMode::kIncludePrototypes,
        static_cast<v8::PropertyFilter>(v8::ONLY_ENUMERABLE|v8::SKIP_SYMBOLS),
        v8::IndexFilter::kIncludeIndices);
    if (scope.handledV8Exception() || maybeArray.IsEmpty()) return nullptr;

    Local<Array> array = maybeArray.ToLocalChecked();
    int length = array->Length();

    // If we use SKIP_STRINGS filter, V8 filters index properties too!!
    // To return index properties only, we've to get string & index
    // properties and filter out! There seems to be no simpler way to
    // get index properties only. Walk once to get number of index properties
    // so that we can allocate Java int[] of right size for the result.

    int numIndices = 0;
    for (int i = 0; i < length; i++) {
        MaybeLocal<Value> key = array->Get(scope.context, i);
        if (scope.handledV8Exception() || key.IsEmpty()) return nullptr;
        if (key.ToLocalChecked()->IsInt32()) {
            numIndices++;
        }
    }

    if (numIndices == 0) return nullptr;
    jintArray indices = jni.NewIntArray(numIndices);
    if (jni.checkException()) return nullptr;

    jint* elements = jni.GetIntArrayElements(indices);
    if (elements == nullptr) return nullptr;

    int next = 0;
    for (int i = 0; i < length; i++) {
        MaybeLocal<Value> key = array->Get(scope.context, i);
        if (scope.handledV8Exception() || key.IsEmpty()) {
            jni.ReleaseIntArrayElements(indices, elements);
            return nullptr;
        }

        Local<Value> index = key.ToLocalChecked();
        if (index->IsInt32()) {
            elements[next++] = index->Int32Value(scope.context).ToChecked();
        }
    }

    jni.ReleaseIntArrayElements(indices, elements);
    return indices;
}

// caller has to check null and return an empty array as appropriate
JNIEXPORT jobjectArray JNICALL Java_org_openjdk_engine_javascript_internal_V8_symbolKeys0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong objectRef) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, objectRef);

    if (!local->IsObject()) {
        return nullptr;
    }

    MaybeLocal<Object> maybeObject = local->ToObject(scope.context);
    if (maybeObject.IsEmpty()) return nullptr;
    Local<Object> object = maybeObject.ToLocalChecked();

    // Get only enumerable non-index properties (all inherited properties included)
    MaybeLocal<Array> maybeArray = object->GetPropertyNames(scope.context,
        v8::KeyCollectionMode::kIncludePrototypes,
        static_cast<v8::PropertyFilter>(v8::ONLY_ENUMERABLE||v8::SKIP_STRINGS),
        v8::IndexFilter::kSkipIndices);
    if (scope.handledV8Exception() || maybeArray.IsEmpty()) return nullptr;

    Local<Array> array = maybeArray.ToLocalChecked();
    int length = array->Length();

    // Walk once to get number of Symbol properties
    // so that we can allocate Java V8Symbol[] of right size for the result.
    int numSymbols = 0;
    for (int i = 0; i < length; i++) {
        MaybeLocal<Value> key = array->Get(scope.context, i);
        if (scope.handledV8Exception() || key.IsEmpty()) return nullptr;
        if (key.ToLocalChecked()->IsSymbol()) {
            numSymbols++;
        }
    }

    if (numSymbols == 0) return nullptr;
    jobjectArray names = jni.NewObjectArray(numSymbols, v8SymbolClass, nullptr);
    if (jni.checkException()) return nullptr;

    int next = 0;
    for (int i = 0; i < length; i++) {
        MaybeLocal<Value> key = array->Get(scope.context, i);
        if (scope.handledV8Exception() || key.IsEmpty()) return nullptr;

        Local<Value> value = key.ToLocalChecked();
        if (value->IsSymbol()) {
            Local<Symbol> sym = value.As<Symbol>();
            jni.SetObjectArrayElement(names, next++, v2j_symbol(scope, sym));
            if (jni.checkException()) return nullptr;
        }
    }

    return names;
}

// JSFactory
JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newObject1
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newObject1");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    Local<Object> obj = Object::New(scope.isolate);
    if (scope.handledV8Exception()) return v2j_undefined(scope);
    return v2j_object_object(scope, obj);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newArray0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jint length) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newArray0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    Local<Object> array = Array::New(scope.isolate, length);
    if (scope.handledV8Exception()) return v2j_undefined(scope);
    return v2j_array_object(scope, array);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newArrayBuffer0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jint length) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newArrayBuffer0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    Local<Object> obj = ArrayBuffer::New(scope.isolate, length);
    if (scope.handledV8Exception()) return v2j_undefined(scope);
    return v2j_object_object(scope, obj);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newArrayBuffer1
(JNIEnv *env, jclass v8Class, jlong isolateRef, jlong globalRef, jobject byteBuffer) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newArrayBuffer1");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(byteBuffer != nullptr);
    V8Scope scope(env, isolateRef, globalRef);
    JNIForJava jni(scope);

    void* data = jni.GetDirectBufferAddress(byteBuffer);
    jlong len = jni.GetDirectBufferCapacity(byteBuffer);
    if (data && len != -1) {
        std::shared_ptr<BackingStore> bs = ArrayBuffer::NewBackingStore(data, len, v8::BackingStore::EmptyDeleter, nullptr);
        Local<ArrayBuffer> abuf = ArrayBuffer::New(scope.isolate, bs);
        if (scope.handledV8Exception()) return v2j_undefined(scope);
        memcpy(abuf->Data(), data, len);

        // make sure nio Buffer is alive till V8 ArrayBuffer is alive!
        // We save (an external ref to) java nio Buffer object as a private property.
        abuf->SetPrivate(scope.context, Private::New(scope.isolate), j2v_object(scope, byteBuffer));
        if (scope.handledV8Exception()) return v2j_undefined(scope);

        return v2j_object_object(scope, abuf);
    } else {
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newByteBuffer0
(JNIEnv *env, jclass v8Class, jlong isolateRef, jlong arrayBufRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newByteBuffer0");
    assert(isolateRef != 0L);
    assert(arrayBufRef != 0L);
    V8Scope scope(env, isolateRef, arrayBufRef);
    JNIForJava jni(scope);

    Local<Value> local = j2v_reference(scope, arrayBufRef);
    if (!local->IsArrayBuffer()) {
        jni.ThrowNew(illegalArgumentExceptionClass, "Not an ArrayBuffer object");
        return nullptr;
    }

    Local<ArrayBuffer> abuf = local.As<ArrayBuffer>();
    if (scope.handledV8Exception() || abuf->Data() == nullptr) return v2j_undefined(scope);

    // caller has to make sure V8 ArrayBuffer lives till this new nio ByteBuffer is alive!
    return jni.NewDirectByteBuffer(abuf->Data(), abuf->ByteLength());
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newDate0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jdouble time) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newDate0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    MaybeLocal<Value> maybeDate = Date::New(scope.context, time);
    if (scope.handledV8Exception() || maybeDate.IsEmpty()) return v2j_undefined(scope);

    Local<Object> date = maybeDate.ToLocalChecked().As<Object>();
    return v2j_object_object(scope, date);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newProxy0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jobject target, jobject handler) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newProxy0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(target != nullptr);
    assert(handler != nullptr);
    V8Scope scope(env, isolateRef, globalRef);

    Local<Object> jsTarget = j2v(scope, target).As<Object>();
    Local<Object> jsHandler = j2v(scope, handler).As<Object>();

    if (jsTarget.IsEmpty()) printf("TARGET\n");
    if (jsHandler.IsEmpty()) printf("HANDLER\n");

    MaybeLocal<Proxy> maybeProxy = Proxy::New(scope.context, jsTarget, jsHandler);
    if (scope.handledV8Exception() || maybeProxy.IsEmpty()) return v2j_undefined(scope);

    Local<Object> proxy = maybeProxy.ToLocalChecked();
    return v2j_proxy_object(scope, proxy);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newResolver0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newResolver0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    MaybeLocal<Promise::Resolver> maybeResolver = Promise::Resolver::New(scope.context);
    if (scope.handledV8Exception() || maybeResolver.IsEmpty()) return v2j_undefined(scope);

    Local<Object> resolver = maybeResolver.ToLocalChecked();
    return v2j_resolver_object(scope, resolver);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newRegExp0
(JNIEnv* env, jclass cls, jlong isolateRef, jlong globalRef, jstring pattern, jint flags) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newRegExp0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(pattern != nullptr);
    V8Scope scope(env, isolateRef, globalRef);
    MaybeLocal<RegExp> maybeRegExp = RegExp::New(scope.context, j2v_string(scope, pattern),
            static_cast<RegExp::Flags>(flags));
    if (scope.handledV8Exception() || maybeRegExp.IsEmpty()) return v2j_undefined(scope);

    Local<Object> regExp = maybeRegExp.ToLocalChecked();
    return v2j_object_object(scope, regExp);
}


JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_parseJSON0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong globalRef, jstring jsonString) {
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(jsonString != nullptr);
    V8Scope scope(env, isolateRef, globalRef);

    MaybeLocal<Value> maybeValue = JSON::Parse(scope.context, j2v_string(scope, jsonString));
    if (scope.handledV8Exception() || maybeValue.IsEmpty()) return v2j_undefined(scope);

    Local<Value> value = maybeValue.ToLocalChecked();
    return v2j(scope, value);
}

static jstring valueToJSON(V8Scope& scope, Local<Value> value, jstring gapString) {
    if (value->IsObject()) {
        Local<String> gap = gapString == nullptr? Local<String>() : j2v_string(scope, gapString);
        MaybeLocal<String> maybeString = JSON::Stringify(scope.context, value.As<Object>(), gap);
        if (scope.handledV8Exception() || maybeString.IsEmpty()) return v2j_string(scope, Local<String>());

        return v2j_string(scope, maybeString.ToLocalChecked());
    }

    return nullptr;
}

JNIEXPORT jstring JNICALL Java_org_openjdk_engine_javascript_internal_V8_toJSON0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong objectRef, jstring gapString) {
    assert(isolateRef != 0L);
    assert(objectRef != 0L);
    V8Scope scope(env, isolateRef, objectRef);

    return valueToJSON(scope, j2v_reference(scope, objectRef), gapString);
}

JNIEXPORT jstring JNICALL Java_org_openjdk_engine_javascript_internal_V8_toJSON1
(JNIEnv *env, jclass cls, jlong isolateRef, jlong globalRef, jobject jsObj, jstring gapString) {
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);

    return valueToJSON(scope, j2v(scope, jsObj), gapString);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newSymbol0
(JNIEnv* env, jclass cls, jlong isolateRef, jstring name) {
    assert(isolateRef != 0L);
    assert(name != nullptr);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    Local<Symbol> sym = Symbol::New(scope.isolate, j2v_string(scope, name));
    if (scope.handledV8Exception()) return v2j_undefined(scope);
    return v2j_symbol(scope, sym);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_symbolFor0
(JNIEnv* env, jclass cls, jlong isolateRef, jstring name) {
    assert(isolateRef != 0L);
    assert(name != nullptr);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    Local<Symbol> sym = Symbol::For(scope.isolate, j2v_string(scope, name));
    if (scope.handledV8Exception()) return v2j_undefined(scope);
    return v2j_symbol(scope, sym);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_getIteratorSymbol0
(JNIEnv* env, jclass cls, jlong isolateRef) {
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    return v2j_symbol(scope, Symbol::GetIterator(scope.isolate));
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_getUnscopablesSymbol0
(JNIEnv* env, jclass cls, jlong isolateRef) {
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    return v2j_symbol(scope, Symbol::GetUnscopables(scope.isolate));
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_getToStringTagSymbol0
(JNIEnv* env, jclass cls, jlong isolateRef) {
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    return v2j_symbol(scope, Symbol::GetToStringTag(scope.isolate));
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_getIsConcatSpreadable0
(JNIEnv* env, jclass cls, jlong isolateRef) {
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    return v2j_symbol(scope, Symbol::GetIsConcatSpreadable(scope.isolate));
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_newError0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong globalRef, jstring message, jint type) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_newError0");
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    assert(message != nullptr);
    V8Scope scope(env, isolateRef, globalRef);
    Local<String> vmsg = j2v_string(scope, message);
    Local<Value> error;
    switch(type) {
        case org_openjdk_engine_javascript_internal_V8_ERROR:
            error = Exception::Error(vmsg);
            break;
        case org_openjdk_engine_javascript_internal_V8_RANGE_ERROR:
            error = Exception::RangeError(vmsg);
            break;
        case org_openjdk_engine_javascript_internal_V8_REFERENCE_ERROR:
            error = Exception::ReferenceError(vmsg);
            break;
        case org_openjdk_engine_javascript_internal_V8_SYNTAX_ERROR:
            error = Exception::SyntaxError(vmsg);
            break;
        case org_openjdk_engine_javascript_internal_V8_TYPE_ERROR:
            error = Exception::TypeError(vmsg);
            break;
        default: {
            JNIForJava jni(scope);
            jni.ThrowNew(illegalArgumentExceptionClass, "Invalid V8 Error type");
            return nullptr;
        }
    }

    if (error.IsEmpty() || !error->IsObject()) {
        return v2j_undefined(scope);
    }

    Local<Object> errorObj = error.As<Object>();
    return v2j_object_object(scope, errorObj);
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_resolverResolve0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong resolverRef, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_resolverResolve0");
    assert(isolateRef != 0L);
    assert(resolverRef != 0L);
    V8Scope scope(env, isolateRef, resolverRef);
    JNIForJava jni(scope);

    Local<Promise::Resolver> resolver = j2v_reference(scope, resolverRef).As<Promise::Resolver>();
    Local<Value> jsValue = j2v_java_wrap(scope, value);
    if (jni.checkException() || jsValue.IsEmpty()) return JNI_FALSE;

    Maybe<bool> maybeResolved = resolver->Resolve(scope.context, jsValue);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return maybeResolved.FromMaybe(false)? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_resolverReject0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong resolverRef, jobject value) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_resolverReject0");
    assert(isolateRef != 0L);
    assert(resolverRef != 0L);
    V8Scope scope(env, isolateRef, resolverRef);
    JNIForJava jni(scope);

    Local<Promise::Resolver> resolver = j2v_reference(scope, resolverRef).As<Promise::Resolver>();
    Local<Value> jsValue = j2v_java_wrap(scope, value);
    if (jni.checkException() || jsValue.IsEmpty()) return JNI_FALSE;

    Maybe<bool> maybeRejected = resolver->Reject(scope.context, jsValue);
    if (scope.handledV8Exception()) return JNI_FALSE;

    return maybeRejected.FromMaybe(false)? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_resolverGetPromise0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong resolverRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_resolverGetPromise0");
    assert(isolateRef != 0L);
    assert(resolverRef != 0L);
    V8Scope scope(env, isolateRef, resolverRef);
    Local<Promise::Resolver> resolver = j2v_reference(scope, resolverRef).As<Promise::Resolver>();
    Local<Promise> promise = resolver->GetPromise();
    if (scope.handledV8Exception() || promise.IsEmpty()) return v2j_undefined(scope);

    return v2j_promise_object(scope, promise);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_promiseCatch0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong promiseRef, jobject function) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_promiseCatch0");
    assert(isolateRef != 0L);
    assert(promiseRef != 0L);
    assert(function != nullptr);
    V8Scope scope(env, isolateRef, promiseRef);
    Local<Promise> promise = j2v_reference(scope, promiseRef).As<Promise>();
    Local<Value> value = j2v(scope, function);
    if (value.IsEmpty()) return v2j_undefined(scope);

    if (value->IsFunction()) {
        MaybeLocal<Promise> maybePromise = promise->Catch(scope.context, value.As<Function>());
        if (scope.handledV8Exception() || maybePromise.IsEmpty()) return v2j_undefined(scope);

        return v2j_promise_object(scope, maybePromise.ToLocalChecked());
    } else {
        JNIForJava jni(scope);
        jni.ThrowNew(illegalArgumentExceptionClass, "Not a function");
        return v2j_undefined(scope);
    }
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_promiseThen0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong promiseRef, jobject function) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_promiseThen0");
    assert(isolateRef != 0L);
    assert(promiseRef != 0L);
    assert(function != nullptr);
    V8Scope scope(env, isolateRef, promiseRef);
    Local<Promise> promise = j2v_reference(scope, promiseRef).As<Promise>();
    Local<Value> value = j2v(scope, function);
    if (value.IsEmpty()) return v2j_undefined(scope);

    if (value->IsFunction()) {
        MaybeLocal<Promise> maybePromise = promise->Then(scope.context, value.As<Function>());
        if (scope.handledV8Exception() || maybePromise.IsEmpty()) return v2j_undefined(scope);

        return v2j_promise_object(scope, maybePromise.ToLocalChecked());
    } else {
        JNIForJava jni(scope);
        jni.ThrowNew(illegalArgumentExceptionClass, "Not a function");
        return v2j_undefined(scope);
    }
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_proxyGetTarget0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong proxyRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_proxyGetTarget0");
    assert(isolateRef != 0L);
    assert(proxyRef != 0L);
    V8Scope scope(env, isolateRef, proxyRef);
    Local<Proxy> proxy = j2v_reference(scope, proxyRef).As<Proxy>();
    Local<Value> target = proxy->GetTarget();
    if (scope.handledV8Exception() || target.IsEmpty()) return v2j_undefined(scope);

    return v2j(scope, target);
}

JNIEXPORT jobject JNICALL Java_org_openjdk_engine_javascript_internal_V8_proxyGetHandler0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong proxyRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_proxyGetHandler0");
    assert(isolateRef != 0L);
    assert(proxyRef != 0L);
    V8Scope scope(env, isolateRef, proxyRef);
    Local<Proxy> proxy = j2v_reference(scope, proxyRef).As<Proxy>();
    Local<Value> handler = proxy->GetHandler();
    if (scope.handledV8Exception() || handler.IsEmpty()) return v2j_undefined(scope);

    return v2j(scope, handler);
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_proxyIsRevoked0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong proxyRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_proxyIsRevoked0");
    assert(isolateRef != 0L);
    assert(proxyRef != 0L);
    V8Scope scope(env, isolateRef, proxyRef);
    Local<Proxy> proxy = j2v_reference(scope, proxyRef).As<Proxy>();
    return proxy->IsRevoked();
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_allowCodeGenerationFromStrings0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong globalRef, jboolean allow) {
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    scope.context->AllowCodeGenerationFromStrings(allow == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL Java_org_openjdk_engine_javascript_internal_V8_isCodeGenerationFromStringsAllowed0
(JNIEnv *env, jclass cls, jlong isolateRef, jlong globalRef) {
    assert(isolateRef != 0L);
    assert(globalRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    return scope.context->IsCodeGenerationFromStringsAllowed()? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_terminateExecution0
(JNIEnv *env, jclass v8Class, jlong isolateRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_terminateExecution0");
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    isolate->TerminateExecution();
}

// callback for V8 interrupts
static void interruptCallback(Isolate* isolate, void* data) {
    if (data != nullptr) {
        JNIEnv* env = JVMV8IsolateData::getEnv(isolate);
        JNI jni(env);

        /*
         * Do not allow JS execution from the interrupt callback!
         * Isolate::RequestInterrupt documentation specifically says
         * "Registered |callback| must not reenter interrupted Isolate"
         */
        Isolate::DisallowJavascriptExecutionScope noJS(isolate,
            Isolate::DisallowJavascriptExecutionScope::OnFailure::THROW_ON_FAILURE);

        // call run method of the interrupt callback Runnable
        jobject runnable = reinterpret_cast<jobject>(data);

        // Callback only if the weak object is not cleared
        jobject localRunnable = jni.NewLocalRef(runnable);
        if (! jni.IsSameObject(localRunnable, nullptr)) {
            jni.CallVoidMethod(localRunnable, runnableRunMethodID);

            // throw away the local handle
            jni.DeleteLocalRef(localRunnable);

            // throw away the runnable weak ref
            jni.DeleteWeakGlobalRef(runnable);
        }
    }
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_requestInterrupt0
(JNIEnv *env, jclass v8Class, jlong isolateRef, jobject runnable) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_requestInterrupt0");
    assert(isolateRef != 0L);
    assert(runnable != nullptr);
    JNI jni(env);

    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    isolate->RequestInterrupt(interruptCallback, jni.NewWeakGlobalRef(runnable));
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_runMicrotasks0
(JNIEnv *env, jclass v8Class, jlong isolateRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_runMicrotasks0");
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    isolate->PerformMicrotaskCheckpoint();
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_enqueueMicrotask0
(JNIEnv *env, jclass v8Class, jlong isolateRef, jlong globalRef, jlong functionRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_enqueueMicrotask0");
    assert(isolateRef != 0L);
    assert(functionRef != 0L);
    V8Scope scope(env, isolateRef, globalRef);
    Local<Function> microtask = j2v_reference(scope, functionRef).As<Function>();
    scope.isolate->EnqueueMicrotask(microtask);
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_throwException0
(JNIEnv *env, jclass v8Class, jlong isolateRef, jobject exception) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_throwException0");
    assert(isolateRef != 0L);
    assert(exception != nullptr);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    scope.throwV8Exception(j2v(scope, exception));
    scope.rethrowV8Exception();
}

// callback for V8 logger events
static void logEventCallback(const char* msg, int event) {
    fprintf(stderr, "V8 LOG: %s [%d]\n", msg, event);
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_setEventLogger0
(JNIEnv *env, jclass v8Class, jlong isolateRef) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_setEventLogger0");
    assert(isolateRef != 0L);
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    isolate->SetEventLogger(logEventCallback);
}

JNIEXPORT jlong JNICALL Java_org_openjdk_engine_javascript_internal_V8_getMethodID0
(JNIEnv *env, jclass v8Class, jclass cls, jstring name, jstring signature) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getMethodID0");
    assert(name != nullptr);
    assert(signature != nullptr);
    JNI jni(env);
    const char* nameUTF = jni.GetStringUTFChars(name);
    const char* signatureUTF = jni.GetStringUTFChars(signature);

    jlong ID = (jlong)jni.GetMethodID(cls, nameUTF, signatureUTF);

    jni.ReleaseStringUTFChars(name, nameUTF);
    jni.ReleaseStringUTFChars(signature, signatureUTF);

    return ID;
}

JNIEXPORT jlong JNICALL Java_org_openjdk_engine_javascript_internal_V8_getStaticMethodID0
(JNIEnv *env, jclass v8Class, jclass cls, jstring name, jstring signature) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getStaticMethodID0");
    assert(name != nullptr);
    assert(signature != nullptr);
    JNI jni(env);
    const char* nameUTF = jni.GetStringUTFChars(name);
    const char* signatureUTF = jni.GetStringUTFChars(signature);

    jlong ID = (jlong)jni.GetStaticMethodID(cls, nameUTF, signatureUTF);

    jni.ReleaseStringUTFChars(name, nameUTF);
    jni.ReleaseStringUTFChars(signature, signatureUTF);

    return ID;
}

JNIEXPORT jlong JNICALL Java_org_openjdk_engine_javascript_internal_V8_getFieldID0
(JNIEnv *env, jclass v8Class, jclass cls, jstring name, jstring signature) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getFieldID0");
    assert(name != nullptr);
    assert(signature != nullptr);
    JNI jni(env);
    const char* nameUTF = jni.GetStringUTFChars(name);
    const char* signatureUTF = jni.GetStringUTFChars(signature);

    jlong ID = (jlong)jni.GetFieldID(cls, nameUTF, signatureUTF);

    jni.ReleaseStringUTFChars(name, nameUTF);
    jni.ReleaseStringUTFChars(signature, signatureUTF);

    return ID;
}

JNIEXPORT jlong JNICALL Java_org_openjdk_engine_javascript_internal_V8_getStaticFieldID0
(JNIEnv *env, jclass v8Class, jclass cls, jstring name, jstring signature) {
    TRACE("Java_org_openjdk_engine_javascript_internal_V8_getStaticFieldID0");
    assert(name != nullptr);
    assert(signature != nullptr);
    JNI jni(env);
    const char* nameUTF = jni.GetStringUTFChars(name);
    const char* signatureUTF = jni.GetStringUTFChars(signature);

    jlong ID = (jlong)jni.GetStaticFieldID(cls, nameUTF, signatureUTF);

    jni.ReleaseStringUTFChars(name, nameUTF);
    jni.ReleaseStringUTFChars(signature, signatureUTF);

    return ID;
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_inspectorDispatchProtocolMessage0
(JNIEnv *env, jclass v8Class, jlong isolateRef, jstring msg) {
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    JVMV8IsolateData::inspectorDispatchProtocolMessage(isolate, j2v_string(scope, msg));
}

JNIEXPORT void JNICALL Java_org_openjdk_engine_javascript_internal_V8_registerIsolate0
(JNIEnv *env, jclass v8Class, jlong isolateRef, jobject v8Isolate ) {
    Isolate* isolate = fromReference<Isolate*>(isolateRef);
    V8Scope scope(env, isolate);
    JNIForJava jni(scope);
    JVMV8IsolateData::setV8Isolate(isolate, jni.NewWeakGlobalRef(v8Isolate));
}
