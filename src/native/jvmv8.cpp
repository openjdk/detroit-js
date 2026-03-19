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
#include <chrono>
#include <iostream>
#include <memory.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <string>
#include <thread>
#include <sys/stat.h>
#include <unistd.h>

#include "jvmv8_jni.hpp"
#include "jvmv8_jni_support.hpp"
#include "jvmv8_java_classes.hpp"
#include "jvmv8.hpp"
#include "jvmv8_primitives.hpp"
#include "jvmv8_jsobject.hpp"
#include "jvmv8_inspector.hpp"

using namespace v8;

#include "libplatform/libplatform.h"
#include "v8-context.h"
#include "v8-initialization.h"
#include "v8-isolate.h"
#include "v8-local-handle.h"
#include "v8-primitive.h"
#include "v8-script.h"


// Fake process name
const char* processName = "jvmv8";
thread_local int TRACER::indent = 0;

// Platform information used by V8
std::unique_ptr<v8::Platform> jvmv8platform;

// Initialize platform information used by V8
void init_platform() {
    TRACE("init_platform");
    if (jvmv8platform == nullptr) {
        // v8::V8::SetFlagsFromString("--min-semi-space-size=128");
        // v8::V8::SetFlagsFromString("--max-semi-space-size=128");
        // v8::V8::SetFlagsFromString("--trace-gc-verbose");
        v8::V8::InitializeICUDefaultLocation(processName);
        v8::V8::InitializeExternalStartupData(processName);
        jvmv8platform = v8::platform::NewDefaultPlatform();
        v8::V8::InitializePlatform(jvmv8platform.get());
        v8::V8::Initialize();
    }
}

// Clean up platform information used by V8
void term_platform() {
    TRACE("term_platform");
    if (jvmv8platform != nullptr) {
        v8::V8::Dispose();
        v8::V8::DisposePlatform();
        jvmv8platform.reset();
    }
}

// used for inspector client
int contextGroupId = 1;

// Constructor for jvmv8 isolate data
JVMV8IsolateData::JVMV8IsolateData(JNIEnv* env, Isolate* isolate, JavaVM* jvm,  bool javaSupport, bool inspector) :
    jvm(jvm),
    javaSupport(javaSupport),
    persistentsPool(new V8PersistentPoolManager(isolate)),
    globalTemplate() {
    TRACE("JVMV8IsolateData::JVMV8IsolateData");

    V8Scope scope(env, isolate);
    Local<ObjectTemplate> globalTemplate = ObjectTemplate::New(isolate);
    if (javaSupport) {
        add_primitives(isolate, env, globalTemplate);
    }
    this->globalTemplate.Reset(isolate, globalTemplate);

    Local<ObjectTemplate> jsObjectTemplate = createJSObjectTemplate(scope);
    this->jsObjectTemplate.Reset(isolate, jsObjectTemplate);

    Local<ObjectTemplate> jsObjectCallableTemplate = createJSObjectCallableTemplate(scope);
    this->jsObjectCallableTemplate.Reset(isolate, jsObjectCallableTemplate);

    if (javaSupport) {
        Local<ObjectTemplate> jvmTemplate = createJVMTemplate(scope);
        this->jvmTemplate.Reset(isolate, jvmTemplate);

        // Symbol used as property key in script friendly wrappers for Java objects
        Local<Symbol> javaObject = Symbol::New(isolate);
        this->javaObject.Reset(isolate, javaObject);
    }

    inspectorClient = inspector? new JVMV8InspectorClient(isolate, contextGroupId++) : nullptr;
    v8Isolate = nullptr;
}

// Destructor for jvmv8 isolate data
JVMV8IsolateData::~JVMV8IsolateData() {
    TRACE("JVMV8IsolateData::~JVMV8IsolateData");
    this->globalTemplate.Reset();
    this->jsObjectTemplate.Reset();
    this->jsObjectCallableTemplate.Reset();
    this->jvmTemplate.Reset();
    this->javaObject.Reset();
    delete persistentsPool;
}

// Get JNI env indirectly from isolate
JNIEnv* JVMV8IsolateData::getEnv(Isolate* isolate) {
    TRACE("JVMV8IsolateData::getEnv");
    return GetJVMEnv(getData(isolate)->jvm);
}

void JVMV8IsolateData::inspectorSendResponse(Isolate* isolate, int callId, Local<String> msg) {
    TRACE("JVMV8IsolateData::inspectorSendResponse");
    V8Scope scope(getEnv(isolate), isolate);
    JNIForJava jni(scope);
    jni.CallStaticVoidMethod(v8Class, v8InspectorSendResponseMethodID, getV8Isolate(isolate), callId, v2j_string(scope, msg));
}

void JVMV8IsolateData::inspectorSendNotification(Isolate* isolate, Local<String> msg) {
    TRACE("JVMV8IsolateData::inspectorSendNotification");
    V8Scope scope(getEnv(isolate), isolate);
    JNIForJava jni(scope);
    jni.CallStaticVoidMethod(v8Class, v8InspectorSendNotificationMethodID, getV8Isolate(isolate), v2j_string(scope, msg));
}

void JVMV8IsolateData::inspectorContextCreated(Isolate* isolate, Local<Context> context, Local<String> contextName) {
    TRACE("JVMV8IsolateData::inspectorContextCreated");
    JVMV8InspectorClient* client = JVMV8IsolateData::getData(isolate)->inspectorClient;
    if (client != nullptr) {
        client->contextCreated(context, contextName);
    }
}

void JVMV8IsolateData::inspectorDispatchProtocolMessage(Isolate* isolate, Local<String> msg) {
    TRACE("JVMV8IsolateData::inspectorDispatchProtocolMessage");
    JVMV8InspectorClient* client = JVMV8IsolateData::getData(isolate)->inspectorClient;
    if (client != nullptr) {
        client->dispatchProtocolMessage(msg);
    }
}

void JVMV8IsolateData::inspectorRunMessageLoopOnPause(v8::Isolate* isolate) {
    TRACE("JVMV8IsolateData::inspectorRunMessageLoopOnPause");
    V8Scope scope(getEnv(isolate), isolate);
    JNIForJava jni(scope);
    jni.CallStaticVoidMethod(v8Class, v8InspectorRunMessageLoopOnPauseMethodID, getV8Isolate(isolate));
}

void JVMV8IsolateData::inspectorQuitMessageLoopOnPause(v8::Isolate* isolate) {
    TRACE("JVMV8IsolateData::inspectorQuitMessageLoopOnPause");
    V8Scope scope(getEnv(isolate), isolate);
    JNIForJava jni(scope);
    jni.CallStaticVoidMethod(v8Class, v8InspectorQuitMessageLoopOnPauseMethodID, getV8Isolate(isolate));
}

// Constructor when Java env, V8 isolate and context are known
V8Scope::V8Scope(JNIEnv* env, Isolate* isolate, Local<Context> context) :
    env(env),
    isolate(isolate),
    locker(isolate),
    isolateScope(isolate),
    handleScope(isolate),
    tryCatch(isolate),
    context(context),
    contextScope(context) {
    TRACE("V8Scope::V8Scope 1");
}

// Constructor when Java env, Java isolate 'long reference' and Java V8Object 'long reference' are known
V8Scope::V8Scope(JNIEnv* env, jlong isolateRef, jlong objectRef) :
    env(env),
    isolate(fromReference<Isolate*>(isolateRef)),
    locker(this->isolate),
    isolateScope(this->isolate),
    handleScope(this->isolate),
    tryCatch(this->isolate),
    context(j2v_context(isolate, objectRef)),
    contextScope(context) {
    TRACE("V8Scope::V8Scope 2");
}

// Constructor when Java env and V8 isolate are known
V8Scope::V8Scope(JNIEnv* env, Isolate* isolate) :
    env(env),
    isolate(isolate),
    locker(this->isolate),
    isolateScope(this->isolate),
    handleScope(this->isolate),
    tryCatch(this->isolate),
    // MUST NOT call GetCurrentContext before locking and
    // entering the isolate by creating locker and isolateScope above
    context(isolate->GetCurrentContext()),
    contextScope(context) {
    TRACE("V8Scope::V8Scope 3");
}

// Constructor when only the isolate is known
V8Scope::V8Scope(Isolate* isolate) :
    V8Scope(JVMV8IsolateData::getEnv(isolate), isolate) {
    TRACE("V8Scope::V8Scope 4");
}

// Easy V8 string factory
Local<String> V8Scope::string(const char* string) {
    TRACE("V8Scope::string");
    MaybeLocal<String> maybeString = String::NewFromUtf8(isolate, string, NewStringType::kNormal);
    return maybeString.IsEmpty() ? String::Empty(isolate) : maybeString.ToLocalChecked();
}

// Easy V8 string factory when size is known (faster)
Local<String> V8Scope::string(const char* string, int size) {
    TRACE("V8Scope::string");
    MaybeLocal<String> maybeString = String::NewFromUtf8(isolate, string, NewStringType::kNormal, size);
    return maybeString.IsEmpty() ? String::Empty(isolate) : maybeString.ToLocalChecked();
}

// Easy V8 symbol factory
Local<Symbol> V8Scope::symbol() {
    TRACE("V8Scope::symbol");
    return Symbol::New(isolate);
}

// Easy V8 symbol factory
Local<Symbol> V8Scope::symbol(Local<String> name) {
    TRACE("V8Scope::symbol");
    return Symbol::New(isolate, name);
}

// Easy V8 symbol factory with string
Local<Symbol> V8Scope::symbol(const char* name) {
    TRACE("V8Scope::symbol");
    return Symbol::New(isolate, string(name));
}

// Easy V8 symbol factory with string and size
Local<Symbol> V8Scope::symbol(const char* name, int size) {
    TRACE("V8Scope::symbol");
    return Symbol::New(isolate, string(name, size));
}

// Easy V8 integer factory
Local<Integer> V8Scope::integer(int value) {
    TRACE("V8Scope::integer");
    return Integer::New(isolate, value);
}

// Easy V8 double factory
Local<Number> V8Scope::number(double value) {
    TRACE("V8Scope::number");
    return Number::New(isolate, value);
}

// Easy V8 boolean true factory
Local<Boolean> V8Scope::True() {
    TRACE("V8Scope::True");
    return v8::True(isolate);
}

// Easy V8 boolean false factory
Local<Boolean> V8Scope::False() {
    TRACE("V8Scope::False");
    return v8::False(isolate);
}

// Easy V8 boolean factory
Local<Boolean> V8Scope::Boolean(bool value) {
    TRACE("V8Scope::Boolean");
    return value ? v8::True(isolate) : v8::False(isolate);
}

// Easy V8 null factory
Local<Primitive> V8Scope::Null() {
    TRACE("V8Scope::Null");
    return v8::Null(isolate);
}

// Easy V8 undefined factory
Local<Primitive> V8Scope::Undefined() {
    TRACE("V8Scope::Undefined");
    return v8::Undefined(isolate);
}

// Evaluate the Java supporting boot javascript.
// This function potentially leaves a V8 Exception in Isolate. Caller has to handle.
// This is not expected to throw any Java exception as this is called from Java.
// Caller has to take care of exception translation/clear.
void run_boot_script(V8Scope& scope) {
    TRACE("run_boot_script");
    Local<Script> script;
    JNIForJS jni(scope);
    JNILocalFrame locals(jni);
    Local<UnboundScript> unboundScript = JVMV8IsolateData::getBootScript(scope.isolate);

    if (unboundScript.IsEmpty()) {
        jstring jstr = (jstring)jni.CallStaticObjectMethod(v8Class, v8GetBootstrapMethodID);
        if (jni.checkException()) return;

        Local<String> scriptString = j2v_string(scope, jstr);
        ScriptOrigin origin(scope.string("<boot>"));

        MaybeLocal<Script> maybeScript = Script::Compile(scope.context, scriptString, &origin);
        if (scope.checkV8Exception()) return;

        if (maybeScript.IsEmpty()) {
            scope.throwV8Exception("Cannot compile bootscript");
            return;
        }

        script = maybeScript.ToLocalChecked();
        unboundScript = script->GetUnboundScript();
        JVMV8IsolateData::setBootScript(scope.isolate, unboundScript);
    } else {
        ContextScope contextScope(scope.context);
        script = unboundScript->BindToCurrentContext();
    }

    Local<Value> debug = scope.Boolean(getenv("JVMV8_BOOT_DEBUG") != nullptr);
    Local<Object> global = scope.context->Global();
    Local<ObjectTemplate> jvmTemplate = JVMV8IsolateData::getJVMTemplate(scope.isolate);
    MaybeLocal<Object> maybeJVM = jvmTemplate->NewInstance(scope.context);

    if (debug.IsEmpty()) {
        scope.throwV8Exception("Cannot allocate jvm instance : debug");
        return;
    }

    if (global.IsEmpty()) {
        scope.throwV8Exception("Cannot allocate jvm instance : global");
        return;
    }

    if (jvmTemplate.IsEmpty()) {
        scope.throwV8Exception("Cannot allocate jvm instance : jvmTemplate");
        return;
    }

    if (maybeJVM.IsEmpty()) {
        scope.throwV8Exception("Cannot allocate jvm instance : JVM");
        return;
    }

    MaybeLocal<Value> maybeBootstrap = script->Run(scope.context);
    if (scope.checkV8Exception()) return;

    if (maybeBootstrap.IsEmpty()) {
        scope.throwV8Exception("No boot function from bootscript");
        return;
    }

    Local<Function> bootstrap = maybeBootstrap.ToLocalChecked().As<Function>();

    if (bootstrap.IsEmpty()) {
        scope.throwV8Exception("Cannot allocate jvm instance : bootstrap");
        return;
    }

    Local<Object> jvm = maybeJVM.ToLocalChecked();
    Local<Symbol> javaObject = JVMV8IsolateData::getJavaObjectSymbol(scope.isolate);

    Local<Value> args[] = { debug, global, jvm, javaObject };

    MaybeLocal<Value> javaWrapFunc = bootstrap->Call(scope.context, global, 4, args);
    if (scope.checkV8Exception()) return;

    if (!javaWrapFunc.IsEmpty()) {
        Local<Value> func = javaWrapFunc.ToLocalChecked();
        if (func->IsFunction()) {
            JVMV8ContextData::setJavaWrap(scope.context, func.As<Function>());
            return;
        }
    }

    scope.throwV8Exception("Bootscript did not return a java wrap function");
}

// Allocate a clump of persistents for storing Java objects
V8PersistentPool::V8PersistentPool(Isolate* isolate, V8PersistentPool *next) :
    isolate(isolate), next(next), cursor(0) {
    TRACE("V8PersistentPool::V8PersistentPool");
}

// Deallocate remaining references to Java objects
V8PersistentPool::~V8PersistentPool() {
    TRACE("V8PersistentPool::~V8PersistentPool");
    // Java env (could be store in pool, but thread local is handy enough)
    JNIEnv* env = JVMV8IsolateData::getEnv(isolate);
    JNI jni(env);

    // For each persistent
    for (int i = 0; i < MAXIMUM; i++) {
        // Next persistent
        Persistent<void*>* persistent = persistents + i;
        if (!persistent->IsEmpty()) {
            Persistent<Value>* valuePersistent = reinterpret_cast<Persistent<Value>*>(persistent);
            Local<Value> value = valuePersistent->Get(isolate);

            if (value->IsExternal()) {
                // Fetch V8 external reference to java object
                Local<External> external = value.As<External>();
                // Fetch Java object reference
                jobject globalRef = reinterpret_cast<jobject>(external->Value());
                // Dispose of Java reference
                jni.DeleteGlobalRef(globalRef);
            }
        }

        persistent->Reset();
    }
}

// Scan for next available V8 persistent
Persistent<void*>* V8PersistentPool::scan() {
    TRACE("V8PersistentPool::scan");
    while (cursor != MAXIMUM) {
        Persistent<void*>* persistent = persistents + cursor++;

        // If persistent is unused
        if (persistent->IsEmpty()) {
            return persistent;
        }
    }

    // No V8 persistents available in this pool
    return nullptr;
}

// Scan from first to last (exclusive, can be nullptr for end of list) pool.
Persistent<void*>* V8PersistentPoolManager::scan(V8PersistentPool *first, V8PersistentPool *last) {
    TRACE("V8PersistentPoolManager::~V8PersistentPoolManager");
    for (V8PersistentPool *p = first; p != last; p = p->nextPool()) {
        Persistent<void*>* persistent = p->scan();

        // If found, return V8 persistent
        if (persistent) {
            // Update searches start
            current = p;

            return persistent;
        }
    }

    // No V8 persistents found
    return nullptr;
}

// Make sure all Java references are disposed of properly
V8PersistentPoolManager::~V8PersistentPoolManager() {
    TRACE("V8PersistentPoolManager::~V8PersistentPoolManager");
    while (pools) {
        current = pools->nextPool();
        delete pools;
        pools = current;
    }
}

// Search for a free V8 persistent
Persistent<void*>* V8PersistentPoolManager::nextPersistent() {
    TRACE("V8PersistentPoolManager::nextPersistent");
    Persistent<void*>* persistent;

    // Scan from last search pool to end of list
    if ((persistent = scan(current, nullptr))) {
        return persistent;
    }

    // Scan from start of list to last search pool
    if ((persistent = scan(pools, current))) {
        return persistent;
    }

    // Force gc
    isolate->LowMemoryNotification();

    // Restart all pools to start at beginning
    for (V8PersistentPool *p = pools; p; p = p->nextPool()) {
        p->Restart();
    }

    // Try again after gc
    if ((persistent = scan(pools, nullptr))) {
        return persistent;
    }

    // Allocate a new pool
    pools = new V8PersistentPool(isolate, pools);
    // Search again with new pool
    persistent = scan(pools, nullptr);
    assert(persistent != nullptr);

    return persistent;
}

struct track_value_callback_info {
    Persistent<External>* persistent;
    jobject object;

    track_value_callback_info(Persistent<External>* persistent, jobject object)
        : persistent(persistent), object(object) {
    }
};

// Used to clean up jobjects used by V8
static void track_value_callback(const WeakCallbackInfo<track_value_callback_info>& data) {
    TRACE("track_value_callback");
    track_value_callback_info* info = data.GetParameter();

    if (info->object) {
        JNIEnv* env = JVMV8IsolateData::getEnv(data.GetIsolate());
        JNI jni(env);

        /*
        jstring javaString = (jstring)jni.CallObjectMethod(info->object, objectClassToStringMethodID);
        const char *nativeString = jni.GetStringUTFChars(javaString);
        printf("RELEASING %s\n", nativeString); fflush(stdout);
        jni.ReleaseStringUTFChars(javaString, nativeString);
        */

        jni.DeleteGlobalRef(info->object);
        info->persistent->Reset();
        delete info;
    }
}

// Track usage of jobject value
Local<External> V8PersistentPoolManager::trackValue(V8Scope& scope, jobject object) {
    TRACE("V8PersistentPoolManager::trackValue");
    if (object == nullptr) return Local<External>();
    JNI jni(scope);
    // Allocate a V8 persistent for the object
    Persistent<External>* persistent = reinterpret_cast<Persistent<External>*>(nextPersistent());
    // Get a JNI global reference for the object
    jobject globalRef = jni.NewGlobalRef(object);
    // Create a V8 external for the Java reference
    Local<External> external = External::New(scope.isolate, globalRef);
    // Have persistent track external
    persistent->Reset(scope.isolate, external);
    // Set callback to handle gc of external
    track_value_callback_info* info = new track_value_callback_info(persistent, globalRef);
    persistent->SetWeak(info, track_value_callback, WeakCallbackType::kParameter);

    return external;
}

// Register a Java object with V8
Local<External> j2v_object(V8Scope& scope, jobject object) {
    TRACE("j2v_object");
    V8PersistentPoolManager* persistents = JVMV8IsolateData::getPersistentsPool(scope.isolate);

    return persistents->trackValue(scope, object);
}

// Register a generic pointer with V8
Local<External> j2v_pointer(V8Scope& scope, void* object) {
    TRACE("j2v_pointer");
    // Wrap object in V8 external
    Local<External> external = External::New(scope.isolate, object);

    return external;
}

// Convert registered object back to Java object
jobject v2j_object(Local<Value>& external) {
    TRACE("v2j_object");
    return reinterpret_cast<jobject>(external.As<External>()->Value());
}

// Convert registered object back to generic pointer
void* v2j_pointer(Local<Value>& external) {
    TRACE("v2j_pointer");
    return external.As<External>()->Value();
}

// Get the Java V8Undefined singleton
jobject v2j_undefined(V8Scope& scope) {
    TRACE("v2j_undefined");
    JNI jni(scope);
    jobject result = jni.GetStaticObjectField(v8UndefinedClass, v8UndefinedInstanceFieldID);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Byte
jobject v2j_Byte(V8Scope& scope, jbyte value) {
    TRACE("v2j_Byte");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(byteClass, byteValueOfMethodID, value);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Short
jobject v2j_Short(V8Scope& scope, jshort value) {
    TRACE("v2j_Short");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(shortClass, shortValueOfMethodID, value);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Integer
jobject v2j_Integer(V8Scope& scope, jint value) {
    TRACE("v2j_Integer");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(integerClass, integerValueOfMethodID, value);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Long
jobject v2j_Long(V8Scope& scope, jlong value) {
    TRACE("v2j_Long");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(longClass, longValueOfMethodID, value);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Float
jobject v2j_Float(V8Scope& scope, jfloat value) {
    TRACE("v2j_Float");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(floatClass, floatValueOfMethodID, value);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Double
jobject v2j_Double(V8Scope& scope, jdouble value) {
    TRACE("v2j_Double");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(doubleClass, doubleValueOfMethodID, value);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Boolean
jobject v2j_Boolean(V8Scope& scope, jboolean value) {
    TRACE("v2j_Boolean");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(booleanClass, booleanValueOfMethodID, value != 0);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Return a Java Character
jobject v2j_Character(V8Scope& scope, jchar value) {
    TRACE("v2j_Character");
    JNI jni(scope);
    jobject result = jni.CallStaticObjectMethod(characterClass, characterValueOfMethodID, value != 0);
    assert(!jni.checkException() && result != nullptr);
    return result;
}

// Convert V8 Script to a Java long reference to V8 UnboundScript
jlong v2j_unboundscript(V8Scope& scope, MaybeLocal<Script> local) {
    TRACE("v2j_unboundscript");
    if (!local.IsEmpty()) {
        Local<Script> script = local.ToLocalChecked();
        Local<UnboundScript> unboundScript = script->GetUnboundScript();
        V8PersistentPoolManager* persistents = JVMV8IsolateData::getPersistentsPool(scope.isolate);
        return persistents->persist(unboundScript);
    } else {
        return 0L;
    }
}

// Convert unbound script reference to V8 UnboundScript
Local<UnboundScript> j2v_unboundscript(V8Scope& scope, jlong unboundScriptRef) {
    TRACE("j2v_unboundscript");
    const Persistent<UnboundScript>* script = fromReference<const Persistent<UnboundScript>*>(unboundScriptRef);
    return script->Get(scope.isolate);
}

// Convert Java Integer to V8 integer
Local<Value> j2v_integer(V8Scope& scope, jobject object) {
    TRACE("j2v_integer");
    JNI jni(scope);
    jint value = jni.CallIntMethod(object, numberIntValueMethodID);
    assert(!jni.checkException());
    return scope.integer(value);
}

// Convert Java Double to V8 number
Local<Value> j2v_number(V8Scope& scope, jobject object) {
    TRACE("j2v_number");
    JNI jni(scope);
    jdouble value = jni.CallDoubleMethod(object, numberDoubleValueMethodID);
    assert(!jni.checkException());
    return scope.number(value);
}

// Convert Java Boolean to V8 boolean
Local<Value> j2v_boolean(V8Scope& scope, jobject object) {
    TRACE("j2v_boolean");
    JNI jni(scope);
    jboolean value = jni.CallIntMethod(object, booleanBooleanValueMethodID);
    assert(!jni.checkException());
    return scope.Boolean(value);
}

// Convert Java char char to V8 String
Local<String> j2v_char(V8Scope& scope, jchar value) {
    TRACE("j2v_char");
    // TODO: create wrapped java.lang.Character instead
    MaybeLocal<String> maybeString = String::NewFromTwoByte(scope.isolate, &value, NewStringType::kNormal, 1);
    return maybeString.IsEmpty() ? String::Empty(scope.isolate) : maybeString.ToLocalChecked();
}

// Convert Java String to V8 String
Local<String> j2v_string(V8Scope& scope, jstring javaString) {
    TRACE("j2v_string");
    JNI jni(scope);
    jsize length = jni.GetStringLength(javaString);
    const jchar* chars = jni.GetStringChars(javaString);
    MaybeLocal<String> maybeString = String::NewFromTwoByte(scope.isolate, chars, NewStringType::kNormal, length);
    jni.ReleaseStringChars(javaString, chars);

    return maybeString.IsEmpty() ? String::Empty(scope.isolate) : maybeString.ToLocalChecked();
}

// Wrap Java object to appropriate V8 script object (when passed from java code).
// Throws Java Exception based on bool flag "throwJava"
Local<Value> j2v_java_wrap(V8Scope& scope, jobject object, bool throwJava) {
    TRACE("j2v_java_wrap");
    assert(!scope.context.IsEmpty());
    Local<Value> value = j2v(scope, object);
    if (scope.handledV8Exception() || value.IsEmpty()) return Local<Value>();

    // wrap Java object to appropriate script wrapper object using boot code's JavaWrap function
    if (value->IsExternal() && JVMV8IsolateData::hasJavaSupport(scope.isolate)) {
        Local<Function> javaWrapFunc = JVMV8ContextData::getJavaWrap(scope.context);
        assert(!javaWrapFunc.IsEmpty());

        Local<Object> global = scope.context->Global();
        Local<Value> args[1] = { value };

        MaybeLocal<Value> result = javaWrapFunc->Call(scope.context, global, 1, args);
        if (throwJava && scope.handledV8Exception()) {
            return value;
        }

        return result.IsEmpty()? value : result.ToLocalChecked();
    }

    return value;
}

// Wrap Java object to appropriate V8 script object (when passed from java code).
// Throws Java Exception on failure.
Local<Value> j2v_java_wrap(V8Scope& scope, jobject object) {
    TRACE("j2v_java_wrap");
    return j2v_java_wrap(scope, object, true);
}

// Unwrap a V8 script to underlying Java object (if it is returned to Java code)
jobject v2j_java_unwrap(V8Scope& scope, Local<Value>& value) {
    TRACE("v2j_java_unwrap");
    assert(!scope.context.IsEmpty());
    assert(!scope.checkV8Exception());
    if (value->IsObject() && JVMV8IsolateData::hasJavaSupport(scope.isolate)) {
        // get "javaObject" symbol
        Local<Symbol> javaObject = JVMV8IsolateData::getJavaObjectSymbol(scope.isolate);
        // check if the script object has a property with the key being "javaObject" symbol
        MaybeLocal<Value> maybeResult = value.As<Object>()->Get(scope.context, javaObject);

        // Don't translate any V8 exception as Java exception from here!! throwV8AsJavaException
        // itself uses this method! We'll end up in infinite recursion!
        if (scope.checkV8Exception()) {
            // clear that V8 exception, but dump stack trace so that we can debug!
            scope.clearV8Exception(true);
        }

        if (!maybeResult.IsEmpty()) {
            Local<Value> result = maybeResult.ToLocalChecked();
            if (result->IsExternal()) {
                return v2j_object(result);
            }
        }
    }

    return v2j(scope, value);
}

static Local<Value> jsObjectWrapper(V8Scope& scope, jobject object, bool callable) {
    TRACE("jsObjectWrapper");
    // different ObjectTemplate depending on whether the JSObject is callable or not
    Local<ObjectTemplate> objTemplate = callable?
        JVMV8IsolateData::getJSObjectCallableTemplate(scope.isolate) :
        JVMV8IsolateData::getJSObjectTemplate(scope.isolate);
    MaybeLocal<Object> maybeJSObject = objTemplate->NewInstance(scope.context);
    if (maybeJSObject.IsEmpty()) return Undefined(scope.isolate);
    Local<Object> jsObject = maybeJSObject.ToLocalChecked();
    // set java object external reference as an internal field
    jsObject->SetInternalField(0, j2v_object(scope, object));
    return jsObject;
}

// Convert a Java object to an appropriate V8 object
Local<Value> j2v(V8Scope& scope, jobject object) {
    TRACE("j2v");
    JNI jni(scope);

    if (object == nullptr) {
        return scope.Null();
    } else if (jni.IsInstanceOf(object, stringClass)) {
        return j2v_string(scope, (jstring)object);
    } else if (jni.IsInstanceOf(object, numberClass)) {
        if (jni.IsInstanceOf(object, doubleClass) ||
            jni.IsInstanceOf(object, floatClass)) {
            return j2v_number(scope, object);
        } else if (jni.IsInstanceOf(object, integerClass) ||
                   jni.IsInstanceOf(object, byteClass) ||
                   jni.IsInstanceOf(object, shortClass)) {
            return j2v_integer(scope, object);
        }
    } else if (jni.IsInstanceOf(object, booleanClass)) {
        return j2v_boolean(scope, object);
    } else if (jni.IsInstanceOf(object, v8UndefinedClass)) {
        return Undefined(scope.isolate);
    } else if (jni.IsInstanceOf(object, v8ObjectClass)) {
        jlong reference = jni.CallLongMethod(object, v8ObjectCheckAndGetReferenceMethodID, scope.isolateRef());
        assert(!jni.checkException());
        if (reference != 0L) {
            const Persistent<Object>* obj = fromReference<const Persistent<Object>*>(reference);
            return obj->Get(scope.isolate);
        }
        // Not the same isolate - needs wrapping
    } else if (jni.IsInstanceOf(object, v8SymbolClass)) {
        jlong reference = jni.CallLongMethod(object, v8SymbolCheckAndGetReferenceMethodID, scope.isolateRef());
        assert(!jni.checkException());
        if (reference != 0L) {
            const Persistent<Symbol>* symbol = fromReference<const Persistent<Symbol>*>(reference);
            return symbol->Get(scope.isolate);
        }
        // Not the same isolate - needs wrapping
    }

    // wrap JSFunction and JSObject instances with a convenient wrapper
    if (jni.IsInstanceOf(object, jsFunctionClass)) {
        // JSFunction wrapper using createFunction
        MaybeLocal<Function> maybeFunction = createFunction(scope, object);
        if (! maybeFunction.IsEmpty()) {
            return maybeFunction.ToLocalChecked();
        } // else fallthru
    } else if (jni.IsInstanceOf(object, jsObjectClass)) {
        // JSObject.isCallable method call
        bool callable = jni.CallBooleanMethod(object, jsObjectIsCallableMethodID);
        return jsObjectWrapper(scope, object, callable);
    }

    return j2v_object(scope, object);
}

// Convert a Java 'long reference' to V8 object
Local<Value> j2v_reference(V8Scope& scope, jlong objectRef) {
    TRACE("j2v_reference");
    return j2v_reference(scope.isolate, objectRef);
}

Local<Value> j2v_reference(Isolate* isolate, jlong objectRef) {
    TRACE("j2v_reference");
    Persistent<Value>* persistent = fromReference<Persistent<Value>*>(objectRef);
    return persistent->Get(isolate);
}

Local<Context> j2v_context(Isolate* isolate, jlong objectRef) {
    TRACE("j2v_context");
    MaybeLocal<Context> maybeContext = j2v_reference(isolate, objectRef).As<Object>()->GetCreationContext();
    return maybeContext.IsEmpty() ? isolate->GetCurrentContext() : maybeContext.ToLocalChecked();
}

// Convert a V8 value to a Java boolean
jboolean v2j_boolean(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_boolean");
    if (value->IsBoolean()) {
        return value->BooleanValue(scope.isolate);
    } else if (value->IsInt32()) {
        return value->Int32Value(scope.context).ToChecked() != 0;
    } else if (value->IsNumber()) {
        return value->IntegerValue(scope.context).ToChecked() != 0;
    } else if (value->IsString()) {
        String::Utf8Value utf8(scope.isolate, value);
        return utf8.length() > 0;
    } else if (value->IsExternal()) {
        JNI jni(scope);
        jobject object = v2j_object(value);

        if (jni.IsInstanceOf(object, booleanClass)) {
            jboolean result = jni.CallBooleanMethod(object, booleanBooleanValueMethodID);
            assert(!jni.checkException());
            return result;
        }
    }

    return ! (value->IsUndefined() || value->IsNull());
}

// Convert a V8 value to a Java char
jchar v2j_char(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_char");
    JNI jni(scope);

    if (value->IsInt32()) {
        return (jchar)value->Int32Value(scope.context).ToChecked();
    } else if (value->IsNumber()) {
        return (jchar)value->IntegerValue(scope.context).ToChecked();
    } else if (value->IsBoolean()) {
        return value->BooleanValue(scope.isolate) ? 1 : 0;
    } else if (value->IsString()) {
        String::Utf8Value utf8(scope.isolate, value);
        if (utf8.length() == 1) {
            return (jchar)**utf8;
        }
    } else if (value->IsExternal()) {
        jobject object = v2j_object(value);

        if (jni.IsInstanceOf(object, characterClass)) {
            jchar result = jni.CallCharMethod(object, characterCharValueMethodID);
            assert(!jni.checkException());
            return result;
        }
    }

    return 0;
}

// Convert a V8 value to a Java char, throwing a V8 error if value can't be converted
jchar v2j_char_throws(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_char_throws");
    JNI jni(scope);
    if (value->IsString()) {
        MaybeLocal<String> maybeString = value->ToString(scope.context);

        if (!maybeString.IsEmpty() && maybeString.ToLocalChecked()->Length() != 1) {
            scope.throwV8Exception("Cannot convert string to character; its length must be exactly 1");
        }
    }

    return v2j_char(scope, value);
}

// Convert a V8 value to a Java integer
jint v2j_integer(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_integer");
    if (value->IsInt32()) {
        return value->Int32Value(scope.context).ToChecked();
    } else if (value->IsNumber()) {
        return (int32_t)value->IntegerValue(scope.context).ToChecked();
    } else if (value->IsBoolean()) {
        return value->BooleanValue(scope.isolate) ? 1 : 0;
    } else if (value->IsString()) {
        String::Utf8Value utf8(scope.isolate, value);
        return strtod(*utf8, nullptr);
    } else if (value->IsExternal()) {
        JNI jni(scope);
        jobject object = v2j_object(value);

        if (jni.IsInstanceOf(object, numberClass)) {
            jint result = jni.CallIntMethod(object, numberIntValueMethodID);
            assert(!jni.checkException());
            return result;
        }
    }

    return 0;
}

// Convert a V8 value to a Java long
jlong v2j_long(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_long");
    if (value->IsInt32()) {
        return value->Int32Value(scope.context).ToChecked();
    } else if (value->IsNumber()) {
        return (int64_t)value->IntegerValue(scope.context).ToChecked();
    } else if (value->IsBoolean()) {
        return value->BooleanValue(scope.isolate) ? 1L : 0L;
    } else if (value->IsString()) {
        String::Utf8Value utf8(scope.isolate, value);
        return strtoll(*utf8, nullptr, 10);
    } else if (value->IsExternal()) {
        JNI jni(scope);
        jobject object = v2j_object(value);

        if (jni.IsInstanceOf(object, numberClass)) {
            jlong result = jni.CallLongMethod(object, numberLongValueMethodID);
            assert(!jni.checkException());
            return result;
        }
    }

    return 0L;
}

// Convert a V8 value to a Java float
jfloat v2j_float(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_float");
    if (value->IsInt32()) {
        return value->Int32Value(scope.context).ToChecked();
    } else if (value->IsNumber()) {
        return value->NumberValue(scope.context).ToChecked();
    } else if (value->IsBoolean()) {
        return value->BooleanValue(scope.isolate) ? 1.0F : 0.0F;
    } else if (value->IsString()) {
        String::Utf8Value utf8(scope.isolate, value);
        return strtod(*utf8, nullptr);
    } else if (value->IsExternal()) {
        JNI jni(scope);
        jobject object = v2j_object(value);

        if (jni.IsInstanceOf(object, numberClass)) {
            jfloat result = jni.CallFloatMethod(object, numberFloatValueMethodID);
            assert(!jni.checkException());
            return result;
        }
    } else if (value->IsUndefined()) {
        return 0.0F/0.0F;
    }

    return 0.0F;
}

// Convert a V8 value to a Java double
jdouble v2j_double(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_double");
    if (value->IsInt32()) {
        return value->Int32Value(scope.context).ToChecked();
    } else if (value->IsNumber()) {
        return value->NumberValue(scope.context).ToChecked();
    } else if (value->IsBoolean()) {
        return value->BooleanValue(scope.isolate) ? 1.0 : 0.0;
    } else if (value->IsString()) {
        String::Utf8Value utf8(scope.isolate, value);
        return strtod(*utf8, nullptr);
    } else if (value->IsExternal()) {
        JNI jni(scope);
        jobject object = v2j_object(value);

        if (jni.IsInstanceOf(object, numberClass)) {
            jdouble result = jni.CallDoubleMethod(object, numberDoubleValueMethodID);
            assert(!jni.checkException());
            return result;
        }
    } else if (value->IsUndefined()) {
        return 0.0/0.0;
    }

    return 0.0;
}

// Convert a V8 value to a Java String
jstring v2j_string(V8Scope& scope, Local<Value> value) {
    TRACE("v2j_string");
    assert(!scope.checkV8Exception());
    JNI jni(scope);
    if (value.IsEmpty()) {
        return jni.NewStringUTF("null");
    } else if (value->IsString()) {
        Local<String> v8String = value.As<String>();
        int length = v8String->Length();
        std::unique_ptr<jchar[]> chars(new jchar[length]);
        v8String->WriteV2(scope.isolate, 0, length, chars.get(), 0);
        jstring javaString = jni.NewString(chars.get(), length);

        return javaString;
    } else if (value->IsSymbol()) {
        return v2j_string(scope, value.As<Symbol>()->Description(scope.isolate));
    }

    String::Utf8Value utf8(scope.isolate, value);
    const char* msg = *utf8;
    if (scope.checkV8Exception()) {
        scope.clearV8Exception(true);
    }
    return msg == nullptr? nullptr : jni.NewStringUTF(msg);
}

// Convert a V8 Symbol to a Java V8Symbol
jobject v2j_symbol(V8Scope& scope, Local<Symbol> value) {
    TRACE("v2j_symbol");
    JNI jni(scope);
    jstring jstr = v2j_string(scope, value);
    V8PersistentPoolManager* persistents = JVMV8IsolateData::getPersistentsPool(scope.isolate);
    jlong ref = persistents->persist(value);
    return jni.CallStaticObjectMethod(v8SymbolClass, v8SymbolCreateMethodID, ref, jstr);
}

// Convert a V8 context to a Java 'long reference'
jlong v2j_context(Isolate* isolate, Local<Context> context) {
    TRACE("v2j_context");
    V8PersistentPoolManager* persistents = JVMV8IsolateData::getPersistentsPool(isolate);
    return persistents->persist(context);
}

// Throw a V8 exception as a Java exception and clear the V8 exception.
void V8Scope::throwV8AsJavaException() {
    TRACE("V8Scope::throwV8AsJavaException");
    JNIForJava jni(*this);

    Local<Message> message = tryCatch.Message();
    if (message.IsEmpty()) {
        if (tryCatch.HasTerminated()) {
            // Isolate::TerminateExecution was called from another thread
            isolate->CancelTerminateExecution();
            jni.ThrowNew(v8ScriptExceptionClass, "Script Terminated");
        } else {
            // something wrong! we didn't get Message to fill in details.
            jni.ThrowNew(v8ScriptExceptionClass, "Unknown Exception");
        }
        // clear the original V8 exception
        clearV8Exception();
    } else {
        // get values from Message object
        Local<String> text = message->Get();
        Local<Value> resourceName = message->GetScriptResourceName();
        int lineNumber = message->GetLineNumber(context).FromMaybe(-1);
        int columnNumber = message->GetStartColumn(context).FromMaybe(-1);
        MaybeLocal<String> sourceLine = message->GetSourceLine(context);
        Local<Value> exception = tryCatch.Exception();

        // clear the original V8 exception now, after we've retrieved
        // the necessary information, so we don't accidentally think
        // an exception was thrown in the below v2j calls
        clearV8Exception();

        // convert values
        jstring jMessage = v2j_string(*this, text);
        jstring jResourceName = resourceName->IsUndefined() ? jni.NewStringUTF("<eval>") : v2j_string(*this, resourceName);
        jstring jSourceLine = sourceLine.IsEmpty() ? jni.NewStringUTF("") : v2j_string(*this, sourceLine.ToLocalChecked());
        jobject ecmaError = v2j_java_unwrap(*this, exception);

        // clear any exception from v2j_java_unwrap itself!
        if (env->ExceptionOccurred()) {
            // but dump stack trace, so that we can debug!
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jint success = jni.ThrowNew(v8ScriptExceptionClass,
             v8ScriptExceptionInitMethodID,
             jMessage, jResourceName, lineNumber, columnNumber, jSourceLine,
             ecmaError);

        if (success < 0) {
            // something wrong! we couldn't fill Message details.
            jni.ThrowNew(v8ScriptExceptionClass, "Unknown Script Exception");
        }
    }
}

// Display details of the V8 exception.
void V8Scope::printV8Exception() const {
    Local<Message> message = tryCatch.Message();
    if (message.IsEmpty()) {
        if (tryCatch.HasTerminated()) {
            printf("Script Terminated\n");
        } else {
            printf("Unknown Exception\n");
        }
    } else {
        String::Utf8Value text(isolate, message->Get());
        String::Utf8Value resourceName(isolate, message->GetScriptResourceName());
        int lineNumber = message->GetLineNumber(context).FromMaybe(-1);
        int columnNumber = message->GetStartColumn(context).FromMaybe(-1);
        MaybeLocal<String> maybeSource =  message->GetSourceLine(context);
        String::Utf8Value sourceLine(isolate, maybeSource.IsEmpty() ? String::Empty(isolate) : maybeSource.ToLocalChecked());

        printf("%s\n", *text);
        printf("%s:%d:%d\n", *resourceName, lineNumber, columnNumber);
        printf("%s\n", *sourceLine);
    }
}

