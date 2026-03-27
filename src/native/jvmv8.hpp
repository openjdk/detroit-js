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

#ifndef __jvmv8_h__
#define __jvmv8_h__

using namespace v8;

class TRACER {
private:
    const char* name;
    static thread_local int indent;

public:
    TRACER(const char* name) : name(name) {
        fprintf(stdout, "%*s%s enter\n", indent, "", name); fflush(stdout);
        indent += 2;
    }

    ~TRACER() {
        indent -= 2;
        fprintf(stdout, "%*s%s exit\n", indent, "", name); fflush(stdout);
    }
};

#if 1
#define TRACE(name) (void)0
#else
#define TRACE(name) TRACER _TRACER(name)
#endif

// Platform information used by V8
extern Platform* v8platform;

// Initialize platform information used by V8
extern void init_platform();
// Clean up platform information used by V8
extern void term_platform();

// jvmv8 package in Java, keep in sync with java code
#define V8_PACKAGE_PREFIX "org/openjdk/engine/javascript/"
#define V8_INTERNAL_PACKAGE_PREFIX V8_PACKAGE_PREFIX "internal/"

// V8 isolate slot used to store jvmv8 data
#define V8_ISOLATE_JVMV8_DATA 0

// V8 context slot used to store jvmv8 JavaWrap function
#define V8_CONTEXT_JAVA_WRAP 1

// Convert a V8 reference to a long that can be handled by Java
inline jlong toReference(void *ptr) {
    return reinterpret_cast<jlong>(ptr);
}

// Convert a Java long back to a V8 reference
template<typename T> T fromReference(jlong ref) {
    return  reinterpret_cast<T>(ref);
}

class JVMV8InspectorClient;

// Manage data associated with isolate
class JVMV8IsolateData {
private:
    JavaVM* jvm;                                 // JVM using isolate
    bool javaSupport;                            // If java support is added for JavaScript
    Global<UnboundScript> bootScript;        // Script executed initially for Java support
    Global<Symbol> javaObject;               // javaObject symbol used for unwrapping wrapped Java objects
    Global<ObjectTemplate> globalTemplate;   // Global template used for all contexts
    Global<ObjectTemplate> jvmTemplate;      // Template with JVM support
    Global<ObjectTemplate> jsObjectTemplate; // Template with JSObject support
    Global<ObjectTemplate> jsObjectCallableTemplate; // Template with callable JSObject support
    Global<Value> securityToken;             // Shared security token (if all Contexts use the same token)

    JVMV8InspectorClient* inspectorClient;
    jobject v8Isolate; // weak reference!

    // Constructor for jvmv8 isolate data
    JVMV8IsolateData(JNIEnv* env, Isolate* isolate, JavaVM* jvm, bool javaSupport, bool inspector);

    // Set data in isolate
    static void setData(Isolate* isolate, JVMV8IsolateData* data) {
        isolate->SetData(V8_ISOLATE_JVMV8_DATA, data);
    }

    // Retrieve data from isolate
    static JVMV8IsolateData* getData(Isolate* isolate) {
        return reinterpret_cast<JVMV8IsolateData*>(isolate->GetData(V8_ISOLATE_JVMV8_DATA));
    }

public:

    // Create a new instance of isolate data and store in isolate
    static void create(JNIEnv* env, Isolate* isolate, JavaVM* jvm, bool javaSupport, bool inspector) {
        setData(isolate, new JVMV8IsolateData(env, isolate, jvm, javaSupport, inspector));
    }

    // Destroy data in isolate
    static void destroy(Isolate* isolate) {
        delete getData(isolate);
        isolate->SetData(V8_ISOLATE_JVMV8_DATA, nullptr);
    }

    // Get JVM instance from isolate
    static JavaVM* getJVM(Isolate* isolate) {
        return getData(isolate)->jvm;
    }

    static bool hasJavaSupport(Isolate* isolate) {
        return getData(isolate)->javaSupport;
    }

    static void setBootScript(Isolate* isolate, Local<UnboundScript>& script) {
        getData(isolate)->bootScript.Reset(isolate, script);
    }

    static Local<UnboundScript> getBootScript(Isolate* isolate) {
        return getData(isolate)->bootScript.Get(isolate);
    }

    static Local<Symbol> getJavaObjectSymbol(Isolate* isolate) {
        return getData(isolate)->javaObject.Get(isolate);
    }

    // Get JNI env indirectly from isolate
    static JNIEnv* getEnv(Isolate* isolate);

    static void setGlobalTemplate(Isolate* isolate, Local<ObjectTemplate>& globalTemplate) {
        getData(isolate)->globalTemplate.Reset(isolate, globalTemplate);
    }

    static Local<ObjectTemplate> getGlobalTemplate(Isolate* isolate) {
        return getData(isolate)->globalTemplate.Get(isolate);
    }

    static void setJVMTemplate(Isolate* isolate, Local<ObjectTemplate>& jvmTemplate) {
        getData(isolate)->jvmTemplate.Reset(isolate, jvmTemplate);
    }

    static Local<ObjectTemplate> getJVMTemplate(Isolate* isolate) {
        return getData(isolate)->jvmTemplate.Get(isolate);
    }

    static Local<ObjectTemplate> getJSObjectTemplate(Isolate* isolate) {
        return getData(isolate)->jsObjectTemplate.Get(isolate);
    }

    static Local<ObjectTemplate> getJSObjectCallableTemplate(Isolate* isolate) {
        return getData(isolate)->jsObjectCallableTemplate.Get(isolate);
    }

    // Get security token (non-Empty only if all Contexts share token).
    static Global<Value>& getSecurityToken(Isolate* isolate) {
        return getData(isolate)->securityToken;
    }

    static void setV8Isolate(Isolate* isolate, jobject obj) {
         getData(isolate)->v8Isolate = obj;
    }

    static jobject getV8Isolate(Isolate* isolate) {
         return getData(isolate)->v8Isolate;
    }

    static void inspectorSendResponse(Isolate* isolate, int callId, Local<String> str);
    static void inspectorSendNotification(Isolate* isolate, Local<String> str);
    static void inspectorContextCreated(Isolate* isolate, Local<Context> context, Local<String> contextName);
    static void inspectorDispatchProtocolMessage(Isolate* isolate, Local<String> str);
    static void inspectorRunMessageLoopOnPause(Isolate* isolate);
    static void inspectorQuitMessageLoopOnPause(Isolate* isolate);
};

// Manage data associated with context
class JVMV8ContextData {
public:
    // Set Java wrap function in context
    static void setJavaWrap(Local<Context>& context, Local<Function> wrapFunc) {
        context->SetEmbedderData(V8_CONTEXT_JAVA_WRAP, wrapFunc);
    }

    // Retrieve JavaWrap function from context
    static Local<Function> getJavaWrap(Local<Context>& context) {
        return context->GetEmbedderData(V8_CONTEXT_JAVA_WRAP).As<Function>();
    }
};

// Handles context scope if present
class ContextScope {
private:
    Local<Context> context;

public:
    ContextScope(Local<Context> context) : context(context) {
        if (!context.IsEmpty()) {
            context->Enter();
        }
    }

    ~ContextScope() {
        if (!context.IsEmpty()) {
            context->Exit();
        }
    }
};

// Scope used to simplify tracking V8 in support code
class V8Scope {
private:
friend class JNI;
friend class JNIForJS;
friend class JNIForJava;
    JNIEnv* env;                  // Current Java env

public:
    Isolate* isolate;             // Current V8 isolate

protected:
    Locker locker;                // V8 locker
    Isolate::Scope isolateScope;  // Current V8 scope
    HandleScope handleScope;      // Current V8 local scope
    TryCatch tryCatch;            // Current V8 try catch handler

public:
    Local<Context> context;       // Current V8 context local (may be empty)
                                  // Relies on handle scope

protected:
    ContextScope contextScope;    // Current V8 Context scope

    // Throw a V8 exception as a Java exception (and clear V8 exception)
    void throwV8AsJavaException();

public:
    // Constructor when Java env, V8 isolate and context are known
    V8Scope(JNIEnv* env, Isolate* isolate, Local<Context> context);
    // Constructor when Java env, Java isolate 'long reference' and a Java V8Object 'long reference' are known
    V8Scope(JNIEnv* env, jlong isolateRef, jlong objectRef);
    // Constructor when Java env and V8 isolate are known
    V8Scope(JNIEnv* env, Isolate* isolate);
    // Constructor when only the isolate is known
    V8Scope(Isolate* isolate);

    jlong isolateRef() {
        return toReference(isolate);
    }

    // Easy V8 string factory
    Local<String> string(const char* string);
    // Easy V8 string factory when size is known (faster)
    Local<String> string(const char* string, int size);
    // Easy V8 symbol factory
    Local<Symbol> symbol();
    // Easy V8 symbol factory
    Local<Symbol> symbol(Local<String> name);
    // Easy V8 symbol factory with string
    Local<Symbol> symbol(const char* string);
    // Easy V8 symbol factory with string and size
    Local<Symbol> symbol(const char* string, int size);
    // Easy V8 integer factory
    Local<Integer> integer(int value);
    // Easy V8 double factory
    Local<Number> number(double value);
    // Easy V8 boolean true factory
    Local<Boolean> True();
    // Easy V8 boolean false factory
    Local<Boolean> False();
    // Easy V8 boolean factory
    Local<Boolean> Boolean(bool value);
    // Easy V8 null factory
    Local<Primitive> Null();
    // Easy V8 undefined factory
    Local<Primitive> Undefined();

    // This is used as (thrown away) return value after an exception
    Local<Value> emptyValue() {
        return Local<Value>();
    }

    // V8 exception helpers

    // is there a V8 exception pending?
    bool checkV8Exception() {
        return tryCatch.HasCaught();
    }

    // checked already that there is a pending V8 exception! Rethrow it!
    void rethrowV8Exception() {
        TRACE("rethrowV8Exception");
        tryCatch.ReThrow();
    }

    // if a V8 exception is pending, rethrow it and return true. else false
    bool checkAndRethrowV8Exception() {
        TRACE("checkAndRethrowV8Exception");
        return checkV8Exception()? (rethrowV8Exception(), true) : false;
    }

    void clearV8Exception(bool printStack) {
        if (printStack) {
            Message::PrintCurrentStackTrace(isolate, std::cerr);
        }
        tryCatch.Reset();
    }

    void clearV8Exception() {
        tryCatch.Reset();
    }

    void throwV8Exception(Local<Value> v8exp) {
        isolate->ThrowException(v8exp);
    }

    void throwV8Exception(const char* message) {
        throwV8Exception(string(message));
    }

    /*
     * If there is a pending V8 exception, convert to Java exception & throw it.
     * Also, clear the V8 exception. This is called when returning to java code
     * after a call to V8 functionality that could potentially throw a V8 exception.
     */
    bool handledV8Exception() {
        TRACE("V8Scope::handledV8Exception");
        return checkV8Exception()? (throwV8AsJavaException(), true) : false;
    }

    // Display details of the V8 exception.
    void printV8Exception() const;

    Local<Value> getV8Exception() {
        return tryCatch.Exception();
    }

    JNIEnv* getEnv() { return env; }
};

// Evaluate the Java supporting boot script
void run_boot_script(V8Scope& scope);

// Java to V8 and V8 to Java value conversion utilities.
// These are not expected to throw any Java exception or translate
// java exception as V8 exception and throw! Any such unlikely
// Java exception is stack trace dumped and cleared!

// Register a Java object with V8
extern Local<External> j2v_object(V8Scope& scope, jobject object);

// Register a generic pointer with V8
extern Local<External> j2v_pointer(V8Scope& scope, void* object);

// Convert registered object back to Java objectl
extern jobject v2j_object(Local<Value>& external);

// Convert registered object back to generic pointer
extern void* v2j_pointer(Local<Value>& external);

// Get the Java V8Undefined singleton
extern jobject v2j_undefined(V8Scope& scope);

// Return a Java Byte
extern jobject v2j_Byte(V8Scope& scope, jbyte value);

// Return a Java Short
extern jobject v2j_Short(V8Scope& scope, jshort value);

// Return a Java Integer
extern jobject v2j_Integer(V8Scope& scope, jint value);

// Return a Java Long
extern jobject v2j_Long(V8Scope& scope, jlong value);

// Return a Java Float
extern jobject v2j_Float(V8Scope& scope, jfloat value);

// Return a Java Double
extern jobject v2j_Double(V8Scope& scope, jdouble value);

// Return a Java Boolean
extern jobject v2j_Boolean(V8Scope& scope, jboolean value);

// Return a Java Character
extern jobject v2j_Character(V8Scope& scope, jchar value);

// Convert V8 Script to V8 UnboundScript java long reference
extern jlong v2j_unboundscript(V8Scope& scope, MaybeLocal<Script> local);

// Convert unbound script java long reference to V8 UnboundScript
extern Local<UnboundScript> j2v_unboundscript(V8Scope& scope, jlong unboundScriptRef);

// Convert Java Integer to V8 integer
extern Local<Value> j2v_integer(V8Scope& scope, jobject object);

// Convert Java Double to V8 number
extern Local<Value> j2v_number(V8Scope& scope, jobject object);

// Convert Java Boolean to V8 boolean
extern Local<Value> j2v_boolean(V8Scope& scope, jobject object);

// Convert Java Character to V8 String
extern Local<String> j2v_char(V8Scope& scope, jchar value);

// Convert Java String to V8 String
extern Local<String> j2v_string(V8Scope& scope, jstring javastring);

// Convert a Java object to an appropriate V8 object
extern Local<Value> j2v(V8Scope& scope, jobject object);

// Convert a Java 'long reference' to V8 object
extern Local<Value> j2v_reference(V8Scope& scope, jlong objectRef);

// Convert a Java 'long reference' to V8 object
extern Local<Value> j2v_reference(Isolate* isolate, jlong objectRef);

// Convert a Java 'long reference' to V8 context
extern Local<Context> j2v_context(Isolate* isolate, jlong objectRef);

// Convert a V8 boolean to a Java boolean
extern jboolean v2j_boolean(V8Scope& scope, Local<Value> value);

// Convert a V8 number to a Java char
extern jchar v2j_char(V8Scope& scope, Local<Value> value);

// Convert a V8 number to a Java char, possibly throwing an error
extern jchar v2j_char_throws(V8Scope& scope, Local<Value> value);

// Convert a V8 number to a Java integer
extern jint v2j_integer(V8Scope& scope, Local<Value> value);

// Convert a V8 number to a Java long
extern jlong v2j_long(V8Scope& scope, Local<Value> value);

// Convert a V8 number to a Java float
extern jfloat v2j_float(V8Scope& scope, Local<Value> value);

// Convert a V8 number to a Java double
extern jdouble v2j_double(V8Scope& scope, Local<Value> value);

// Convert a V8 value to a Java String
extern jstring v2j_string(V8Scope& scope, Local<Value> value);

// Convert a V8 symbol to a Java object
extern jobject v2j_symbol(V8Scope& scope, Local<Symbol> value);

// Rethrow a V8 exception as a Java exception
extern void handle_script_exception(V8Scope& scope);

// Stop the show
extern void fatal_error(const char* message);

// Track usage of js object from java
template<typename T>
jlong track_js_value(Isolate* isolate, Local<T> local) {
    return toReference(new Global<T>(isolate, local));
}

// Create a Java V8Reference object
inline jobject v2j_reference(V8Scope& scope, Local<Object> value, jclass clazz, jmethodID methodID) {
    TRACE("v2j_reference");
    JNI jni(scope);
    jlong objectRef = track_js_value(scope.isolate, value);
    return jni.CallStaticObjectMethod(clazz, methodID, objectRef);
}

// Create a Java V8TypedArray reference object
inline jobject v2j_typed_array_object(V8Scope& scope, Local<Object> value) {
    TRACE("v2j_typed_array_object");
    return v2j_reference(scope, value, v8TypedArrayClass, v8TypedArrayCreateMethodID);
}

// Create a Java V8Array reference object
inline jobject v2j_array_object(V8Scope& scope, Local<Object> value) {
    TRACE("v2j_array_object");
    return v2j_reference(scope, value, v8ArrayClass, v8ArrayCreateMethodID);
}

// Create a Java V8Function reference object
inline jobject v2j_function_object(V8Scope& scope, Local<Object> value) {
    TRACE("v2j_function_object");
    return v2j_reference(scope, value, v8FunctionClass, v8FunctionCreateMethodID);
}

// Create a Java V8Resolver reference object
inline jobject v2j_resolver_object(V8Scope& scope, Local<Object> value) {
    TRACE("v2j_resolver_object");
    return v2j_reference(scope, value, v8ResolverClass, v8ResolverCreateMethodID);
}

// Create a Java V8Promise reference object
inline jobject v2j_promise_object(V8Scope& scope, Local<Object> value) {
    TRACE("v2j_promise_object");
    return v2j_reference(scope, value, v8PromiseClass, v8PromiseCreateMethodID);
}

// Create a Java V8Proxy reference object
inline jobject v2j_proxy_object(V8Scope& scope, Local<Object> value) {
    TRACE("v2j_proxy_object");
    return v2j_reference(scope, value, v8ProxyClass, v8ProxyCreateMethodID);
}

// Create a Java V8Object reference object
inline jobject v2j_object_object(V8Scope& scope, Local<Object> value) {
    TRACE("v2j_object_object");
    return v2j_reference(scope, value, v8ObjectClass, v8ObjectCreateMethodID);
}

// Convert a V8 value to a Java object
template <typename T> jobject v2j(V8Scope& scope, Local<T>& value) {
    TRACE("v2j");
    if (value->IsInt32()) {
        return v2j_Integer(scope, (jint)value->IntegerValue(scope.context).ToChecked());
    } else if (value->IsNumber()) {
        return v2j_Double(scope, value->NumberValue(scope.context).ToChecked());
    } else if (value->IsBoolean()) {
        return v2j_Boolean(scope, value->BooleanValue(scope.isolate));
    } else if (value->IsUndefined()) {
        return v2j_undefined(scope);
    } else if (value->IsNull()) {
        return nullptr;
    } else if (value->IsString()) {
        return v2j_string(scope, value);
    } else if (value->IsSymbol()) {
        return v2j_symbol(scope, value.template As<Symbol>());
    } else if (value->IsFunction()) {
        return v2j_function_object(scope, value.template As<Object>());
    } else if (value->IsArray()) {
        return v2j_array_object(scope, value.template As<Object>());
    } else if (value->IsExternal()) {
        return v2j_object(value);
    } else if (value->IsTypedArray()) {
        return v2j_typed_array_object(scope, value.template As<Object>());
    } else if (value->IsPromise()) {
        return v2j_promise_object(scope, value.template As<Object>());
    } else if (value->IsProxy()) {
        return v2j_proxy_object(scope, value.template As<Object>());
    } else if (value->IsObject()) {
        return v2j_object_object(scope, value.template As<Object>());
    }

    return v2j_string(scope, value);
}

// Convert a V8 maybe value to a Java object
template <class T> jobject v2j(V8Scope& scope, MaybeLocal<T> local) {
    if (!local.IsEmpty()) {
        Local<T> value = local.ToLocalChecked();
        return v2j(scope, value);
    } else {
        return nullptr;
    }
}

// Wrap Java object to appropriate V8 script object (when passed from java code).
// Throws Java Exception based on bool flag "throwJava"
extern Local<Value> j2v_java_wrap(V8Scope& scope, jobject object, bool throwJava);

// Convert a Java object to an appropriate V8 script wrapper object (when value is passed from java code)
// Throws Java Exception on failure.
extern Local<Value> j2v_java_wrap(V8Scope& scope, jobject object);

// Convert a V8 value to a Java object (when value is returned to Java code)
extern jobject v2j_java_unwrap(V8Scope& scope, Local<Value>& value);

// Convert a V8 value to a Java object (when value is returned to Java code)
inline jobject v2j_java_unwrap(V8Scope& scope, MaybeLocal<Value> local) {
    if (!local.IsEmpty()) {
        Local<Value> value = local.ToLocalChecked();
        return v2j_java_unwrap(scope, value);
    } else {
        return nullptr;
    }
}

#endif // __jvmv8_h__
