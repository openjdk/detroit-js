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
#include <string.h>
#include <string>

#include "jvmv8_jni.hpp"
#include "jvmv8_jni_support.hpp"
#include "jvmv8_java_classes.hpp"
#include "jvmv8.hpp"
#include "jvmv8_primitives.hpp"

// Character codes for Java type sigantures
#define TYPE_VOID        'V'
#define TYPE_BOOLEAN     'Z'
#define TYPE_BYTE        'B'
#define TYPE_CHAR        'C'
#define TYPE_SHORT       'S'
#define TYPE_INT         'I'
#define TYPE_LONG        'J'
#define TYPE_FLOAT       'F'
#define TYPE_DOUBLE      'D'
#define TYPE_OBJECT      'L'
#define TYPE_OBJECT_END  ';'
#define TYPE_ARRAY       '['
#define TYPE_ARG_BEGIN   '('
#define TYPE_ARG_END     ')'

// Support routine to scan to the next type in a signature c string
static char* signature_next(char* signature) {
    assert(signature != nullptr);
    while (*signature == TYPE_ARRAY) {
        signature++;
    }

    if (*signature == TYPE_OBJECT) {
        while (*signature && *signature != TYPE_OBJECT_END) {
            signature++;
        }
    }

    if (*signature) {
        signature++;
    }

    return signature;
}

// Support routine to determine the arity of a Java method
static int signature_count(char* signature) {
    assert(signature != nullptr);
    char ch;
    int count = 0;

    if (*signature == TYPE_ARG_BEGIN) {
        signature++;
    }

    while ((ch = *signature) && ch != TYPE_ARG_END) {
        count++;
        signature = signature_next(signature);
    }

    return count;
}

// Support routine to determine the return type of a Java method
static const char* signature_return(const char* signature) {
    assert(signature != nullptr);
    const char* where = strchr(signature, TYPE_ARG_END);

    return where ? where + 1 : "V";
}

// Support routine to marshall supplied JavaScript arguments as Java arguments.
// The resulting jvalue array is gc'd by the caller (see class Arg.)
static jvalue* marshall(V8Scope& scope, char* signature, Local<Array> jsArgs) {
    assert(signature != nullptr);
    int count = signature_count(signature);
    jvalue* args = new jvalue[count];

    char ch;
    uint32_t length = jsArgs->Length();

    if (*signature == TYPE_ARG_BEGIN) {
        signature++;
    }

    for (int i = 0; (ch = *signature) && ch != TYPE_ARG_END; i++) {
        Local<Value> value;

        if (i < length) {
            MaybeLocal<Value> maybeValue = jsArgs->Get(scope.context, i);

            if (!maybeValue.IsEmpty()) {
                value = maybeValue.ToLocalChecked();
            }
        }

        switch (ch) {
            case TYPE_BOOLEAN:
                args[i].z = v2j_boolean(scope, value);
                break;
            case TYPE_BYTE:
                args[i].b = v2j_integer(scope, value);
                break;
            case TYPE_CHAR:
                args[i].c = v2j_char(scope, value);
                break;
            case TYPE_SHORT:
                args[i].s = v2j_integer(scope, value);
                break;
            case TYPE_INT:
                args[i].i = v2j_integer(scope, value);
                break;
            case TYPE_LONG:
                args[i].j = v2j_long(scope, value);
                break;
            case TYPE_FLOAT:
                args[i].f = v2j_float(scope, value);
                break;
            case TYPE_DOUBLE:
                args[i].d = v2j_double(scope, value);
                break;
            case TYPE_OBJECT:
            case TYPE_ARRAY:
                args[i].l = v2j(scope, value);
                break;
            default:
                break;
        }

        signature = signature_next(signature);
    }

    return args;
}

// Support class for managing an array of bytes.  The array is gc'd by
// the destructor.
class Buffer {
private:
    char* data;
    size_t length;

public:
    Buffer() : data(nullptr), length(0) {
    }

    Buffer(Local<Value> value) {
        Local<Uint8Array> array = value.As<Uint8Array>();
        this->length = array->ByteLength();
        this->data = new char[length];
        array->CopyContents(this->data, length);
    }

    ~Buffer() {
        if (data) {
            delete[] data;
            data = nullptr;
        }
    }

    char* operator()() {
        return data;
    }

    size_t operator*() {
        return length;
    }
};

// Support class for managing an array of UTF16 characters.  The array is
// gc'd by the destructor.
class UTF8String {
private:
    Isolate* isolate;
    char* data;
    size_t length;

public:
    UTF8String() : isolate(nullptr), data(nullptr), length(0) {
    }

    UTF8String(Isolate* isolate, Local<Value> value) {
        Local<String> string = value.As<String>();
        size_t length = string->Utf8LengthV2(isolate);
        char* data = new char[length + 1];
        string->WriteUtf8V2(isolate, data, length);

        this->isolate = isolate;
        this->data = data;
        this->length = length;
    }

    ~UTF8String() {
        if (data) {
            delete[] data;
            data = nullptr;
        }
    }

    char* operator()() {
        return data;
    }

    size_t operator*() {
        return length;
    }
};

// Support class for managing an array of jvalues. The array is gc'd
// by the destructor.
class Args {
private:
    jvalue* args;

public:
    Args() : args(nullptr) {
    }

    Args(jvalue* args) : args(args) {

    }

    ~Args() {
        if (args) {
            delete[] args;
            args = nullptr;
        }
    }

    jvalue* operator()() {
        return args;
    }
};

// The following templates are used by class InfoHelper to test and convert
// JavaScript arguments to equivalent for JNI calls.

template<typename T> bool testValue(Local<Value>& value) {
    return value->IsExternal();
}
template<typename T> T nullValue() {
    return nullptr;
}
template<typename T> T getValue(V8Scope& scope, Local<Value>& value) {
    return reinterpret_cast<T>(value.As<External>()->Value());
}

template<> bool testValue<jobject>(Local<Value>& value) {
    return true;
}
template<> jobject nullValue<jobject>() {
    return nullptr;
}
template<> jobject getValue<jobject>(V8Scope& scope, Local<Value>& value) {
    return v2j(scope, value);
}

template<> bool testValue<jbyte>(Local<Value>& value) {
    return value->IsNumber();
}
template<> jbyte nullValue<jbyte>() {
    return 0;
}
template<> jbyte getValue<jbyte>(V8Scope& scope, Local<Value>& value) {
    return (jbyte)value->IntegerValue(scope.context).ToChecked();
}

template<> bool testValue<jchar>(Local<Value>& value) {
    return value->IsNumber() || value->IsString();
}
template<> jchar nullValue<jchar>() {
    return 0;
}
template<> jchar getValue<jchar>(V8Scope& scope, Local<Value>& value) {
    return v2j_char_throws(scope, value);
}

template<> bool testValue<jshort>(Local<Value>& value) {
    return value->IsNumber();
}
template<> jshort nullValue<jshort>() {
    return 0;
}
template<> jshort getValue<jshort>(V8Scope& scope, Local<Value>& value) {
    return (jshort)value->IntegerValue(scope.context).ToChecked();
}

template<> bool testValue<jlong>(Local<Value>& value) {
    // TODO - handle longs
    return value->IsNumber() || value->IsExternal();
}
template<> jlong nullValue<jlong>() {
    return 0;
}
template<> jlong getValue<jlong>(V8Scope& scope, Local<Value>& value) {
    return v2j_long(scope, value);
}

template<> bool testValue<jfloat>(Local<Value>& value) {
    return value->IsNumber();
}
template<> jfloat nullValue<jfloat>() {
    return 0;
}
template<> jfloat getValue<jfloat>(V8Scope& scope, Local<Value>& value) {
    return value->NumberValue(scope.context).ToChecked();
}

template<> bool testValue<jdouble>(Local<Value>& value) {
    return value->IsNumber();
}
template<> jdouble nullValue<jdouble>() {
    return 0;
}
template<> jdouble getValue<jdouble>(V8Scope& scope, Local<Value>& value) {
    return value->NumberValue(scope.context).ToChecked();
}

template<> bool testValue<int>(Local<Value>& value) {
    return value->IsNumber();
}
template<> int nullValue<int>() {
    return 0;
}
template<> int getValue<int>(V8Scope& scope, Local<Value>& value) {
    return (int)value->IntegerValue(scope.context).ToChecked();
}

template<> bool testValue<bool>(Local<Value>& value) {
    return value->IsBoolean();
}
template<> bool nullValue<bool>() {
    return false;
}
template<> bool getValue<bool>(V8Scope& scope, Local<Value>& value) {
    return value->BooleanValue(scope.isolate);
}

template<> bool testValue<jboolean>(Local<Value>& value) {
    return value->IsBoolean();
}
template<> jboolean nullValue<jboolean>() {
    return false;
}
template<> jboolean getValue<jboolean>(V8Scope& scope, Local<Value>& value) {
    return value->BooleanValue(scope.isolate);
}

template<> bool testValue<Buffer>(Local<Value>& value) {
    return value->IsUint8Array();
}
template<> Buffer nullValue<Buffer>() {
    return Buffer();
}
template<> Buffer getValue<Buffer>(V8Scope& scope, Local<Value>& value) {
    return Buffer(value);
}

template<> bool testValue<UTF8String>(Local<Value>& value) {
    return value->IsString();
}
template<> UTF8String nullValue<UTF8String>() {
    return UTF8String();
}
template<> UTF8String getValue<UTF8String>(V8Scope& scope, Local<Value>& value) {
    return UTF8String(scope.isolate, value);
}

// The following templates are used by class InfoHelper to convert results from
// a JNI call to JavaScript equivalent.

template<typename T> void setReturn(V8Scope& scope, const FunctionCallbackInfo<Value>& info, T value) {
    info.GetReturnValue().Set(j2v(scope, value));
}

template<> void setReturn(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jmethodID value) {
    if (!value) {
        info.GetReturnValue().Set(scope.Null());

        return;
    }

    info.GetReturnValue().Set(j2v_pointer(scope, value));
}

template<> void setReturn(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jfieldID value) {
    if (!value) {
        info.GetReturnValue().Set(scope.Null());

        return;
    }

    info.GetReturnValue().Set(j2v_pointer(scope, value));
}

template<> void setReturn(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jstring value) {
    info.GetReturnValue().Set(j2v_string(scope, value));
}

template<> void setReturn<jbyte>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jbyte value) {
    info.GetReturnValue().Set(scope.integer(value));
}

template<> void setReturn<jchar>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jchar value) {
    info.GetReturnValue().Set(j2v_char(scope, value));
}

template<> void setReturn<jshort>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jshort value) {
    info.GetReturnValue().Set(scope.integer(value));
}

template<> void setReturn<int>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, int value) {
    info.GetReturnValue().Set(scope.integer(value));
}

template<> void setReturn<jlong>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jlong value) {
    // TODO - handle longs
    info.GetReturnValue().Set(scope.integer((int)value));
}

template<> void setReturn<jboolean>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, jboolean value) {
    info.GetReturnValue().Set(scope.Boolean(value));
}

template<> void setReturn<bool>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, bool value) {
    info.GetReturnValue().Set(scope.Boolean(value));
}

template<> void setReturn<float>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, float value) {
    info.GetReturnValue().Set(scope.number(value));
}

template<> void setReturn<double>(V8Scope& scope, const FunctionCallbackInfo<Value>& info, double value) {
    info.GetReturnValue().Set(scope.number(value));
}

// Support class to simplify conversion of arguments/results to JNI calls.
class InfoHelper {
private:
    V8Scope& scope;                          // JavaScript function scope
    const FunctionCallbackInfo<Value>& info; // JavaScript function info
    int length;                              // JavaScript function argument count
    int i;                                   // Current argument index

public:
    InfoHelper(V8Scope& scope, const FunctionCallbackInfo<Value>& info) : scope(scope), info(info), length(info.Length()), i(0) {
    }

    inline bool isMore() {
        return i < length;
    }

    inline void next() {
        i++;
    }

    // Raise a JavaScript exception, primarily used to indicate problems with
    // arguments. Note the exception message length is limited.
    void exception(V8Scope& scope, const char* message, int value) {
        char buffer[128];
        snprintf(buffer, sizeof(buffer), message, value);
        scope.throwV8Exception(buffer);
    }

    // Get the next argument as a raw JavaScript value.
    Local<Value> getRaw() {
        if (scope.checkV8Exception()) {
             return Local<Value>();
        }

        if (!isMore()) {
            exception(scope, "JVM wrong arity, should be greater than %d", length);

             return Local<Value>();
        }

        Local<Value> value = info[i];
        next();

        return value;
    }

    // Generic get next argument, uses testValue, getValue, nullValue templates defined above.
    template<typename T> T get() {
        if (scope.checkV8Exception()) {
            return nullValue<T>();
        }

        if (!isMore()) {
            exception(scope, "JVM wrong arity, should be greater than %d", length);

            return nullValue<T>();
        }

        Local<Value> value = info[i];

        if (!testValue<T>(value)) {
            exception(scope, "JVM arg %d not correct type", i);

            return nullValue<T>();
        }

        next();

        return getValue<T>(scope, value);
    }

    // Get a 'long reference' value from an external or from two separate integer arguments.
    template<typename T> T getID() {
        if (scope.checkV8Exception()) {
            return nullptr;
        }

        if (!isMore()) {
            exception(scope, "JVM wrong arity, should be greater than %d", length);

            return nullptr;
        }

        Local<Value> value1 = info[i];

        if (value1->IsExternal()) {
            next();

            return getValue<T>(scope, value1);
        } else if (value1->IsNumber()) {
            next();

            if (!isMore()) {
                exception(scope, "JVM wrong arity, should be greater than %d", length);

                return nullptr;
            }

            Local<Value> value2 = info[i];

            if (value2->IsNumber()) {
                int64_t hi = value1.As<Number>()->IntegerValue(scope.context).ToChecked();
                int64_t lo = value2.As<Number>()->IntegerValue(scope.context).ToChecked();
                uint64_t id = (hi << 32) | (lo & 0xFFFFFFFFL);
                next();

                return reinterpret_cast<T>(id);
            }
        }

        exception(scope, "JVM arg %d not correct type", i);

        return nullptr;
    }

    // Get marshalled arguments (gc managed) for method call.
    Args getArgs(char* signature){
        if (scope.checkV8Exception()) {
            return Args();
        }

        if (!isMore()) {
            exception(scope, "JVM wrong arity, should be greater than %d", length);

            return Args();
        }

        Local<Value> value = info[i];

        if (!value->IsArray()) {
            exception(scope, "JVM arg %d not an array", i);

            return Args();
        }

        Local<Array> array = value.As<Array>();

        return Args(marshall(scope, signature, array));
    }

    void result() {
        scope.checkAndRethrowV8Exception();
    }

    // Set the return type for JNI call, uses setReturn templates defined above.
    template<typename T> void result(T value) {
        if (!scope.checkV8Exception()) {
            setReturn(scope, info, value);
        } else {
            scope.rethrowV8Exception();
        }
    }

    // Return as wrapped Java object, never converting to JS primitives. Used for Java constructor return values.
    void rawResult(jobject object) {
        if (!scope.checkV8Exception()) {
            if (!object) {
                info.GetReturnValue().Set(scope.Null());

                return;
            }

            info.GetReturnValue().Set(j2v_object(scope, object));
        } else {
            scope.rethrowV8Exception();
        }
    }

    void rawResult(Local<Value> val) {
        if (!scope.checkV8Exception()) {
            info.GetReturnValue().Set(val);
        } else {
            scope.rethrowV8Exception();
        }
    }
};

static void jvmIsEvalAllowed(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmIsEvalAllowed");
    V8Scope scope(info.GetIsolate());
    Local<Context> context = scope.isolate->GetCurrentContext();
    info.GetReturnValue().Set(context->IsCodeGenerationFromStringsAllowed());
}

static void jvmSetEvalAllowed(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetEvalAllowed");
    V8Scope scope(info.GetIsolate());
    Local<Context> context = scope.isolate->GetCurrentContext();
    bool allow = info.Length() > 0? info[0]->IsTrue() : false;
    context->AllowCodeGenerationFromStrings(allow);
}

// The following is a subset of JNI calls that are practical for bridging support.

static void jvmCallMethod(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmCallMethod");
    // jobject CallObjectMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jboolean CallBooleanMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jbyte CallByteMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jchar CallCharMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jshort CallShortMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jint CallIntMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jlong CallLongMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jfloat CallFloatMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // jdouble CallDoubleMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    // void CallVoidMethodA(jobject obj, jmethodID methodID, const jvalue * args) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jmethodID methodID = helper.getID<jmethodID>();
    UTF8String signature = helper.get<UTF8String>();
    Args args = helper.getArgs(signature());

    if (scope.checkAndRethrowV8Exception()) return;

    switch (signature_return(signature())[0]) {
        case TYPE_BOOLEAN: {
            jboolean result = jni.CallBooleanMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_BYTE: {
            jbyte result = jni.CallByteMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_CHAR: {
            jchar result = jni.CallCharMethodA(object, methodID, args());

            info.GetReturnValue().Set(scope.integer(result));
            break;
        }
        case TYPE_SHORT: {
            jshort result = jni.CallShortMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_INT: {
            jint result = jni.CallIntMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_LONG: {
            jlong result = jni.CallLongMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_FLOAT: {
            jfloat result = jni.CallFloatMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_DOUBLE: {
            jdouble result = jni.CallDoubleMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_OBJECT:
        case TYPE_ARRAY: {
            jobject result = jni.CallObjectMethodA(object, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_VOID:
        default:
            jni.CallVoidMethodA(object, methodID, args());

            helper.result();
            break;
    }
}

static void jvmCallNonvirtualMethod(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmCallNonvirtualMethod");
    // jobject CallNonvirtualObjectMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jboolean CallNonvirtualBooleanMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jbyte CallNonvirtualByteMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jchar CallNonvirtualCharMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jshort CallNonvirtualShortMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jint CallNonvirtualIntMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jlong CallNonvirtualLongMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jfloat CallNonvirtualFloatMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // jdouble CallNonvirtualDoubleMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    // void CallNonvirtualVoidMethodA(jobject obj, jclass cls, jmethodID methodID, const jvalue * args) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jclass cls = helper.get<jclass>();
    jmethodID methodID = helper.getID<jmethodID>();
    UTF8String signature = helper.get<UTF8String>();
    Args args = helper.getArgs(signature());

    if (scope.checkAndRethrowV8Exception()) return;

    switch (signature_return(signature())[0]) {
        case TYPE_BOOLEAN: {
            jboolean result = jni.CallNonvirtualBooleanMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_BYTE: {
            jbyte result = jni.CallNonvirtualByteMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_CHAR: {
            jchar result = jni.CallNonvirtualCharMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_SHORT: {
            jshort result = jni.CallNonvirtualShortMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_INT: {
            jint result = jni.CallNonvirtualIntMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_LONG: {
            jlong result = jni.CallNonvirtualLongMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_FLOAT: {
            jfloat result = jni.CallNonvirtualFloatMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_DOUBLE: {
            jdouble result = jni.CallNonvirtualDoubleMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_OBJECT:
        case TYPE_ARRAY: {
            jobject result = jni.CallNonvirtualObjectMethodA(object, cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_VOID:
        default:
            jni.CallNonvirtualVoidMethodA(object, cls, methodID, args());

            helper.result();
            break;
    }
}

static void jvmGetObjectField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetObjectField");
    // jobject GetObjectField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject result = jni.GetObjectField(object, fieldID);

    helper.result(result);
}

static void jvmGetBooleanField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetBooleanField");
    // jboolean GetBooleanField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jboolean result = jni.GetBooleanField(object, fieldID);

    helper.result(result);
}

static void jvmGetByteField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetByteField");
    // jbyte GetByteField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jbyte result = jni.GetByteField(object, fieldID);

    helper.result(result);
}

static void jvmGetCharField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetCharField");
    // jchar GetCharField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jchar result = jni.GetCharField(object, fieldID);

    helper.result(result);
}

static void jvmGetShortField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetShortField");
    // jshort GetShortField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jshort result = jni.GetShortField(object, fieldID);

    helper.result(result);
}

static void jvmGetIntField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetIntField");
    // jint GetIntField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jint result = jni.GetIntField(object, fieldID);

    helper.result(result);
}

static void jvmGetLongField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetLongField");
    // jlong GetLongField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jlong result = jni.GetLongField(object, fieldID);

    helper.result(result);
}

static void jvmGetFloatField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetFloatField");
    // jfloat GetFloatField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jfloat result = jni.GetFloatField(object, fieldID);

    helper.result(result);
}

static void jvmGetDoubleField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetDoubleField");
    // jdouble GetDoubleField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jdouble result = jni.GetDoubleField(object, fieldID);

    helper.result(result);
}

static void jvmSetObjectField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetObjectField");
    // void SetObjectField(jobject obj, jfieldID fieldID, jobject val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jobject value = helper.get<jobject>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetObjectField(object, fieldID, value);

    helper.result();
}

static void jvmSetBooleanField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetBooleanField");
    // void SetBooleanField(jobject obj, jfieldID fieldID, jboolean val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jboolean value = helper.get<jboolean>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetBooleanField(object, fieldID, value);

    helper.result();
}

static void jvmSetByteField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetByteField");
    // void SetByteField(jobject obj, jfieldID fieldID, jbyte val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jbyte value = helper.get<jbyte>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetByteField(object, fieldID, value);

    helper.result();
}

static void jvmSetCharField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetCharField");
    // void SetCharField(jobject obj, jfieldID fieldID, jchar val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jchar value = helper.get<jchar>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetCharField(object, fieldID, value);

    helper.result();
}

static void jvmSetShortField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetShortField");
    // void SetShortField(jobject obj, jfieldID fieldID, jshort val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jshort value = helper.get<jshort>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetShortField(object, fieldID, value);

    helper.result();
}

static void jvmSetIntField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetIntField");
    // void SetIntField(jobject obj, jfieldID fieldID, jint val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jint value = helper.get<jint>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetIntField(object, fieldID, value);

    helper.result();
}

static void jvmSetLongField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetLongField");
    // void SetLongField(jobject obj, jfieldID fieldID, jlong val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jlong value = helper.get<jlong>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetLongField(object, fieldID, value);

    helper.result();
}

static void jvmSetFloatField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetFloatField");
    // void SetFloatField(jobject obj, jfieldID fieldID, jfloat val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jfloat value = helper.get<jfloat>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetFloatField(object, fieldID, value);

    helper.result();
}

static void jvmSetDoubleField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetDoubleField");
    // void SetDoubleField(jobject obj, jfieldID fieldID, jdouble val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jdouble value = helper.get<jdouble>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetDoubleField(object, fieldID, value);

    helper.result();
}

static void jvmCallStaticMethod(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmCallStaticMethod");
    // jobject CallStaticObjectMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // jboolean CallStaticBooleanMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // jbyte CallStaticByteMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // jchar CallStaticCharMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // jshort CallStaticShortMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // jint CallStaticIntMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // jlong CallStaticLongMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // jfloat CallStaticFloatMethod(jclass cls, jmethodID methodID, const jvalue * args) {
    // jdouble CallStaticDoubleMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    // void CallStaticVoidMethodA(jclass cls, jmethodID methodID, const jvalue * args) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jmethodID methodID = helper.getID<jmethodID>();
    UTF8String signature = helper.get<UTF8String>();
    Args args = helper.getArgs(signature());

    if (scope.checkAndRethrowV8Exception()) return;

    switch (signature_return(signature())[0]) {
        case TYPE_BOOLEAN: {
            jboolean result = jni.CallStaticBooleanMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_BYTE: {
            jbyte result = jni.CallStaticByteMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_CHAR: {
            jchar result = jni.CallStaticCharMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_SHORT: {
            jshort result = jni.CallStaticShortMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_INT: {
            jint result = jni.CallStaticIntMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_LONG: {
            jlong result = jni.CallStaticLongMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_FLOAT: {
            jfloat result = jni.CallStaticFloatMethod(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_DOUBLE: {
            jdouble result = jni.CallStaticDoubleMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_OBJECT:
        case TYPE_ARRAY: {
            jobject result = jni.CallStaticObjectMethodA(cls, methodID, args());

            helper.result(result);
            break;
        }
        case TYPE_VOID:
        default:
            jni.CallStaticVoidMethodA(cls, methodID, args());

            helper.result();
            break;
    }
}

static void jvmFindClass(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmFindClass");
    // jclass FindClass(const char *name) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    Local<Value> name = helper.getRaw();

    if (scope.checkAndRethrowV8Exception()) return;

    jclass cls = (jclass)jni.CallStaticObjectMethod(v8Class, v8FindClassMethodID, v2j_string(scope, name));

    helper.result(cls);
}

static void jvmFindClassPrivate(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmFindClassPrivate");
    // jclass FindClass(const char *name) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    Local<Value> name = helper.getRaw();

    if (scope.checkAndRethrowV8Exception()) return;

    jclass cls = (jclass)jni.CallStaticObjectMethod(v8Class, v8FindClassPrivateMethodID, v2j_string(scope, name));

    helper.result(cls);
}

static void jvmCreateListAdapter(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmCreateListAdapter");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    Local<Value> val = helper.getRaw();
    jobject obj = v2j_object_object(scope, val.As<Object>());
    jclass cls = helper.get<jclass>();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject listAdapter = (jobject)jni.CallStaticObjectMethod(v8Class, v8CreateListAdapterMethodID, obj, cls);

    helper.result(listAdapter);
}

static void jvmGetClassName(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetClassName");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();

    if (scope.checkAndRethrowV8Exception()) return;

    jstring name = (jstring)jni.CallObjectMethod(cls, classGetNameMethodID);

    helper.result(name);
}

static void jvmGetHashCode(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetHashCode");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject cls = helper.get<jobject>();

    if (scope.checkAndRethrowV8Exception()) return;

    jint hashCode = jni.CallIntMethod(cls, objectClassHashCodeMethodID);

    helper.result(hashCode);
}

static void jvmGetStaticBooleanField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticBooleanField");
    // jboolean GetStaticBooleanField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jboolean result = jni.GetStaticBooleanField(cls, fieldID);

    helper.result(result);
}

static void jvmGetStaticByteField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticByteField");
    // jbyte GetStaticByteField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jbyte result = jni.GetStaticByteField(cls, fieldID);

    helper.result(result);
}

static void jvmGetStaticCharField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticCharField");
    // jchar GetStaticCharField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jchar result = jni.GetStaticCharField(cls, fieldID);

    helper.result(result);
}

static void jvmGetStaticShortField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticShortField");
    // jshort GetStaticShortField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jshort result = jni.GetStaticShortField(cls, fieldID);

    helper.result(result);
}

static void jvmGetStaticIntField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticIntField");
    // jint GetStaticIntField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jint result = jni.GetStaticIntField(cls, fieldID);

    helper.result(result);
}

static void jvmGetStaticLongField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticLongField");
    // jlong GetStaticLongField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jlong result = jni.GetStaticLongField(cls, fieldID);

    helper.result(result);
}

static void jvmGetStaticFloatField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticFloatField");
    // jfloat GetStaticFloatField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jfloat result = jni.GetStaticFloatField(cls, fieldID);

    helper.result(result);
}

static void jvmGetStaticDoubleField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticDoubleField");
    // jdouble GetStaticDoubleField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jdouble result = jni.GetStaticDoubleField(cls, fieldID);

    helper.result(result);
}

static void jvmSetStaticObjectField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticObjectField");
    // void SetStaticObjectField(jobject obj, jfieldID fieldID, jobject val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jobject value = helper.get<jobject>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticObjectField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticBooleanField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticBooleanField");
    // void SetStaticBooleanField(jobject obj, jfieldID fieldID, jboolean val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jboolean value = helper.get<jboolean>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticBooleanField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticByteField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticByteField");
    // void SetStaticByteField(jobject obj, jfieldID fieldID, jbyte val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jbyte value = helper.get<jbyte>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticByteField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticCharField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticCharField");
    // void SetStaticCharField(jobject obj, jfieldID fieldID, jchar val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jchar value = helper.get<jchar>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticCharField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticShortField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticShortField");
    // void SetStaticShortField(jobject obj, jfieldID fieldID, jshort val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jshort value = helper.get<jshort>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticShortField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticIntField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticIntField");
    // void SetStaticIntField(jobject obj, jfieldID fieldID, jint val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jint value = helper.get<jint>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticIntField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticLongField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticLongField");
    // void SetStaticLongField(jobject obj, jfieldID fieldID, jlong val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jlong value = helper.get<jlong>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticLongField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticFloatField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticFloatField");
    // void SetStaticFloatField(jobject obj, jfieldID fieldID, jfloat val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jfloat value = helper.get<jfloat>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticFloatField(cls, fieldID, value);

    helper.result();
}

static void jvmSetStaticDoubleField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetStaticDoubleField");
    // void SetStaticDoubleField(jobject obj, jfieldID fieldID, jdouble val) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();
    jdouble value = helper.get<jdouble>();

    if (scope.checkAndRethrowV8Exception()) return;

    jni.SetStaticDoubleField(cls, fieldID, value);

    helper.result();
}

// These type codes are known to boot script. If you modify, synchronize with boot script!

#define J_BOOLEAN         0
#define J_CHAR            1
#define J_BYTE            2
#define J_SHORT           3
#define J_INT             4
#define J_LONG            5
#define J_FLOAT           6
#define J_DOUBLE          7
#define J_BOOLEAN_WRAPPER 8
#define J_CHAR_WRAPPER    9
#define J_BYTE_WRAPPER    10
#define J_SHORT_WRAPPER   11
#define J_INT_WRAPPER     12
#define J_LONG_WRAPPER    13
#define J_FLOAT_WRAPPER   14
#define J_DOUBLE_WRAPPER  15
#define J_STRING          16
#define J_OBJECT          17

static void jvmGetArrayElementTypeCode(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetArrayElementTypeCode");
    // jint GetArrayElementTypeCode(jarray array) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jarray array = helper.get<jarray>();

    if (scope.checkAndRethrowV8Exception()) return;
    jclass cls = (jclass)jni.CallObjectMethod(array, objectClassGetClassMethodID, "()Ljava/lang/Class;");

    if (jni.IsSameObject(cls, booleanPrimitiveArrayClass)) {
        helper.result(J_BOOLEAN);
    } else if (jni.IsSameObject(cls, characterPrimitiveArrayClass)) {
        helper.result(J_CHAR);
    } else if (jni.IsSameObject(cls, bytePrimitiveArrayClass)) {
        helper.result(J_BYTE);
    } else if (jni.IsSameObject(cls, shortPrimitiveArrayClass)) {
        helper.result(J_SHORT);
    } else if (jni.IsSameObject(cls, integerPrimitiveArrayClass)) {
        helper.result(J_INT);
    } else if (jni.IsSameObject(cls, longPrimitiveArrayClass)) {
        helper.result(J_LONG);
    } else if (jni.IsSameObject(cls, floatPrimitiveArrayClass)) {
        helper.result(J_FLOAT);
    } else if (jni.IsSameObject(cls, doublePrimitiveArrayClass)) {
        helper.result(J_DOUBLE);
    } else if (jni.IsSameObject(cls, booleanWrapperArrayClass)) {
        helper.result(J_BOOLEAN_WRAPPER);
    } else if (jni.IsSameObject(cls, characterWrapperArrayClass)) {
        helper.result(J_CHAR_WRAPPER);
    } else if (jni.IsSameObject(cls, byteWrapperArrayClass)) {
        helper.result(J_BYTE_WRAPPER);
    } else if (jni.IsSameObject(cls, shortWrapperArrayClass)) {
        helper.result(J_SHORT_WRAPPER);
    } else if (jni.IsSameObject(cls, integerWrapperArrayClass)) {
        helper.result(J_INT_WRAPPER);
    } else if (jni.IsSameObject(cls, longWrapperArrayClass)) {
        helper.result(J_LONG_WRAPPER);
    } else if (jni.IsSameObject(cls, floatWrapperArrayClass)) {
        helper.result(J_FLOAT_WRAPPER);
    } else if (jni.IsSameObject(cls, doubleWrapperArrayClass)) {
        helper.result(J_DOUBLE_WRAPPER);
    } else if (jni.IsSameObject(cls, stringArrayClass)) {
        helper.result(J_STRING);
    } else {
        helper.result(J_OBJECT);
    }
}

static void jvmGetArrayElement(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetArrayElement");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jarray array = helper.get<jarray>();
    jint index = helper.get<jint>();
    jint typeCode = helper.get<jint>();

    if (scope.checkAndRethrowV8Exception()) return;

    switch (typeCode) {
        case J_BOOLEAN: {
            jbooleanArray booleanArray = reinterpret_cast<jbooleanArray>(array);
            jboolean* elements = jni.GetBooleanArrayElements(booleanArray);
            if (elements == nullptr) return;
            jboolean value = elements[index];
            jni.ReleaseBooleanArrayElements(booleanArray, elements);
            helper.result(value);

            return;
        }

        case J_BYTE: {
            jbyteArray byteArray = reinterpret_cast<jbyteArray>(array);
            jbyte* elements = jni.GetByteArrayElements(byteArray);
            if (elements == nullptr) return;
            jbyte value = elements[index];
            jni.ReleaseByteArrayElements(byteArray, elements);
            helper.result(value);

            return;
        }

        case J_SHORT: {
            jshortArray shortArray = reinterpret_cast<jshortArray>(array);
            jshort* elements = jni.GetShortArrayElements(shortArray);
            if (elements == nullptr) return;
            jshort value = elements[index];
            jni.ReleaseShortArrayElements(shortArray, elements);
            helper.result(value);

            return;
        }

        case J_CHAR: {
            jcharArray charArray = reinterpret_cast<jcharArray>(array);
            jchar* elements = jni.GetCharArrayElements(charArray);
            if (elements == nullptr) return;
            jchar value = elements[index];
            jni.ReleaseCharArrayElements(charArray, elements);
            helper.result(value);

            return;
        }

        case J_INT: {
            jintArray intArray = reinterpret_cast<jintArray>(array);
            jint* elements = jni.GetIntArrayElements(intArray);
            if (elements == nullptr) return;
            jint value = elements[index];
            jni.ReleaseIntArrayElements(intArray, elements);
            helper.result(value);

            return;
        }

        case J_LONG: {
            jlongArray longArray = reinterpret_cast<jlongArray>(array);
            jlong* elements = jni.GetLongArrayElements(longArray);
            if (elements == nullptr) return;
            jlong value = elements[index];
            jni.ReleaseLongArrayElements(longArray, elements);
            helper.result(value);

            return;
        }

        case J_FLOAT: {
            jfloatArray floatArray = reinterpret_cast<jfloatArray>(array);
            jfloat* elements = jni.GetFloatArrayElements(floatArray);
            if (elements == nullptr) return;
            jfloat value = elements[index];
            jni.ReleaseFloatArrayElements(floatArray, elements);
            helper.result(value);

            return;
        }

        case J_DOUBLE: {
            jdoubleArray doubleArray = reinterpret_cast<jdoubleArray>(array);
            jdouble* elements = jni.GetDoubleArrayElements(doubleArray);
            if (elements == nullptr) return;
            jdouble value = elements[index];
            jni.ReleaseDoubleArrayElements(doubleArray, elements);
            helper.result(value);

            return;
        }

        default: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            helper.result(jni.GetObjectArrayElement(objectArray, index));
        }
    }
}

static void jvmGetArrayLength(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetArrayLength");
    // jsize GetArrayLength(jarray array) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jarray array = helper.get<jarray>();

    if (scope.checkAndRethrowV8Exception()) return;

    jsize length = jni.GetArrayLength(array);

    helper.result(length);
}

static void jvmGetMethodID(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetMethodID");
    // jmethodID GetMethodID(jclass cls, const char *name, const char *sig) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    UTF8String name = helper.get<UTF8String>();
    UTF8String signature = helper.get<UTF8String>();

    if (scope.checkAndRethrowV8Exception()) return;

    jmethodID methodID = jni.GetMethodID(cls, name(), signature());

    helper.result(methodID);
}

static void jvmGetObjectClass(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetObjectClass");
    // jclass GetObjectClass(jobject obj) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();

    if (scope.checkAndRethrowV8Exception()) return;

    jclass cls = jni.GetObjectClass(object);

    helper.result(cls);
}

static void jvmGetStaticFieldID(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticFieldID");
    // jfieldID GetStaticFieldID(jclass cls, const char *name, const char *sig) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    UTF8String name = helper.get<UTF8String>();
    UTF8String signature = helper.get<UTF8String>();

    if (scope.checkAndRethrowV8Exception()) return;

    jfieldID fieldID = jni.GetStaticFieldID(cls, name(), signature());

    helper.result(fieldID);
}

static void jvmGetStaticMethodID(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticMethodID");
    // jmethodID GetStaticMethodID(jclass cls, const char *name, const char *sig) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    UTF8String name = helper.get<UTF8String>();
    UTF8String signature = helper.get<UTF8String>();

    if (scope.checkAndRethrowV8Exception()) return;

    jmethodID methodID = jni.GetStaticMethodID(cls, name(), signature());

    helper.result(methodID);

}

static void jvmGetStaticObjectField(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetStaticObjectField");
    // jobject GetStaticObjectField(jobject obj, jfieldID fieldID) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jfieldID fieldID = helper.getID<jfieldID>();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject result = jni.GetStaticObjectField(cls, fieldID);

    helper.result(result);
}

static void jvmGetSuperclass(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmGetSuperclass");
    // jclass GetSuperclass(jclass sub) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();

    if (scope.checkAndRethrowV8Exception()) return;

    jclass super = jni.GetSuperclass(cls);

    helper.result(super);
}

static void jvmIsAssignableFrom(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmIsAssignableFrom");
    // jboolean IsAssignableFrom(jclass sub, jclass sup) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass subClass = helper.get<jclass>();
    jclass superClass = helper.get<jclass>();

    if (scope.checkAndRethrowV8Exception()) return;

    jboolean isAssignable = jni.IsAssignableFrom(subClass, superClass);

    helper.result(isAssignable);
}

static void jvmIsInstanceOf(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmIsInstanceOf");
    // jboolean IsInstanceOf(jobject obj, jclass cls) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object = helper.get<jobject>();
    jclass cls = helper.get<jclass>();

    if (scope.checkAndRethrowV8Exception()) return;

    jboolean is = jni.IsInstanceOf(object, cls);

    helper.result(is);
}

static void jvmIsSameObject(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmIsSameObject");
    // jboolean IsSameObject(jobject obj1, jobject obj2) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject object1 = helper.get<jobject>();
    jobject object2 = helper.get<jobject>();

    if (scope.checkAndRethrowV8Exception()) return;

    jboolean same = jni.IsSameObject(object1, object2);

    helper.result(same);
}

static void jvmNewArray(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmNewArray");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jint len = helper.get<jint>();

    if (scope.checkAndRethrowV8Exception()) return;

    if (jni.IsSameObject(cls, booleanPrimitiveArrayClass)) {
        helper.result(jni.NewBooleanArray(len));

        return;
    }

    if (jni.IsSameObject(cls, bytePrimitiveArrayClass)) {
        helper.result(jni.NewByteArray(len));

        return;
    }

    if (jni.IsSameObject(cls, shortPrimitiveArrayClass)) {
        helper.result(jni.NewShortArray(len));

        return;
    }

    if (jni.IsSameObject(cls, characterPrimitiveArrayClass)) {
        helper.result(jni.NewCharArray(len));

        return;
    }

    if (jni.IsSameObject(cls, integerPrimitiveArrayClass)) {
        helper.result(jni.NewIntArray(len));

        return;
    }

    if (jni.IsSameObject(cls, longPrimitiveArrayClass)) {
        helper.result(jni.NewLongArray(len));

        return;
    }

    if (jni.IsSameObject(cls, floatPrimitiveArrayClass)) {
        helper.result(jni.NewFloatArray(len));

        return;
    }

    if (jni.IsSameObject(cls, doublePrimitiveArrayClass)) {
        helper.result(jni.NewDoubleArray(len));

        return;
    }

    jclass component = (jclass)jni.CallObjectMethod(cls, classGetComponentTypeMethodID, "()Ljava/lang/Class;");
    if (jni.checkException()) {
        helper.result();
        return;
    }

    helper.result(jni.NewObjectArray(len, component, nullptr));
}

static void jvmNewObject(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmNewObject");
    // jobject NewObjectA(jclass cls, jmethodID methodID, const jvalue *args) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jmethodID methodID = helper.getID<jmethodID>();
    UTF8String signature = helper.get<UTF8String>();
    Args args = helper.getArgs(signature());

    if (scope.checkAndRethrowV8Exception()) return;

    jobject object = jni.NewObjectA(cls, methodID, args());

    helper.rawResult(object);
}

static void jvmSetArrayElement(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmSetArrayElement");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jarray array = helper.get<jarray>();
    jint index = helper.get<jint>();
    Local<Value> value = helper.getRaw();
    jint typeCode = helper.get<jint>();

    if (scope.checkAndRethrowV8Exception()) return;

    switch (typeCode) {
        case J_BOOLEAN: {
            jbooleanArray booleanArray = reinterpret_cast<jbooleanArray>(array);
            jboolean* elements = jni.GetBooleanArrayElements(booleanArray);
            elements[index] = v2j_boolean(scope, value);
            jni.ReleaseBooleanArrayElements(booleanArray, elements);
            helper.result();

            return;
        }

        case J_BYTE: {
            jbyteArray byteArray = reinterpret_cast<jbyteArray>(array);
            jbyte* elements = jni.GetByteArrayElements(byteArray);
            elements[index] = v2j_integer(scope, value);
            jni.ReleaseByteArrayElements(byteArray, elements);
            helper.result();

            return;
        }

        case J_SHORT: {
            jshortArray shortArray = reinterpret_cast<jshortArray>(array);
            jshort* elements = jni.GetShortArrayElements(shortArray);
            elements[index] = v2j_integer(scope, value);
            jni.ReleaseShortArrayElements(shortArray, elements);
            helper.result();

            return;
        }

        case J_CHAR: {
            jcharArray charArray = reinterpret_cast<jcharArray>(array);
            jchar* elements = jni.GetCharArrayElements(charArray);
            jchar c = v2j_char_throws(scope, value);
            elements[index] = c;
            jni.ReleaseCharArrayElements(charArray, elements);
            helper.result();

            return;
        }

        case J_INT: {
            jintArray intArray = reinterpret_cast<jintArray>(array);
            jint* elements = jni.GetIntArrayElements(intArray);
            elements[index] = v2j_integer(scope, value);
            jni.ReleaseIntArrayElements(intArray, elements);
            helper.result();

            return;
        }

        case J_LONG: {
            jlongArray longArray = reinterpret_cast<jlongArray>(array);
            jlong* elements = jni.GetLongArrayElements(longArray);
            elements[index] = v2j_long(scope, value);
            jni.ReleaseLongArrayElements(longArray, elements);
            helper.result();

            return;
        }

        case J_FLOAT: {
            jfloatArray floatArray = reinterpret_cast<jfloatArray>(array);
            jfloat* elements = jni.GetFloatArrayElements(floatArray);
            elements[index] = v2j_float(scope, value);
            jni.ReleaseFloatArrayElements(floatArray, elements);
            helper.result();

            return;
        }

        case J_DOUBLE: {
            jdoubleArray doubleArray = reinterpret_cast<jdoubleArray>(array);
            jdouble* elements = jni.GetDoubleArrayElements(doubleArray);
            elements[index] =  v2j_double(scope, value);
            jni.ReleaseDoubleArrayElements(doubleArray, elements);
            helper.result();

            return;
        }

        case J_BOOLEAN_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Boolean(scope, v2j_boolean(scope, value)));
            helper.result();

            return;
        }

        case J_CHAR_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Character(scope, v2j_char_throws(scope, value)));
            helper.result();

            return;
        }

        case J_BYTE_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Byte(scope, v2j_integer(scope, value)));
            helper.result();

            return;
        }

        case J_SHORT_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Short(scope, v2j_integer(scope, value)));
            helper.result();

            return;
        }

        case J_INT_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Integer(scope, v2j_integer(scope, value)));
            helper.result();

            return;
        }

        case J_LONG_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Long(scope, v2j_long(scope, value)));
            helper.result();

            return;
        }

        case J_FLOAT_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Float(scope, v2j_double(scope, value)));
            helper.result();

            return;
        }

        case J_DOUBLE_WRAPPER: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jni.SetObjectArrayElement(objectArray, index, value->IsNull()? nullptr : v2j_Double(scope, v2j_double(scope, value)));
            helper.result();

            return;
        }

        default: {
            jobjectArray objectArray = reinterpret_cast<jobjectArray>(array);
            jobject jvalue = value->IsNull()? nullptr : (typeCode == J_STRING? v2j_string(scope, value) : v2j(scope, value));
            jni.SetObjectArrayElement(objectArray, index, jvalue);
            helper.result();

            return;
        }
    }
}

static void jvmAsArrayBuffer(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmAsArrayBuffer");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject bbuf = helper.get<jobject>();

    if (!(jni.IsInstanceOf(bbuf, byteBufferClass))) {
        helper.result();
        return;
    }

    void* data = jni.GetDirectBufferAddress(bbuf);
    if (scope.checkAndRethrowV8Exception()) return;
    jlong len = jni.GetDirectBufferCapacity(bbuf);
    if (scope.checkAndRethrowV8Exception()) return;

    if (data && len != -1) {
        std::shared_ptr<BackingStore> bs = ArrayBuffer::NewBackingStore(data, len, v8::BackingStore::EmptyDeleter, nullptr);
        Local<ArrayBuffer> abuf = ArrayBuffer::New(scope.isolate, bs);
        if (scope.checkAndRethrowV8Exception()) return;
        memcpy(abuf->Data(), data, len);

        // make sure nio Buffer is alive till V8 ArrayBuffer is alive!
        // We save (an external ref to) java nio Buffer object as a private property.
        abuf->SetPrivate(scope.context, Private::New(scope.isolate), j2v_object(scope, bbuf));
        if (scope.checkAndRethrowV8Exception()) return;

        helper.rawResult(abuf);
    } else {
        helper.result();
    }
}

static void jvmAsByteBuffer(const FunctionCallbackInfo<Value>& info) {
    TRACE("jvmAsByteBuffer");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    Local<Value> value = helper.getRaw();
    if (value->IsArrayBuffer()) {
        jobject bbuf = jni.CallStaticObjectMethod(v8Class, v8NewByteBufferMethodID, v2j_object_object(scope, value.As<Object>()));
        helper.rawResult(bbuf);
    }
}

// The following are routines to assist the support of the bridging of the jvm

static void v8ExecutableSignature(const FunctionCallbackInfo<Value>& info) {
    TRACE("v8ExecutableSignature");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject method = helper.get<jobject>();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject signature = jni.CallStaticObjectMethod(v8Class, v8ClassExecutableSignatureMethodID, method);

    helper.result(signature);
}

static void v8FieldSignature(const FunctionCallbackInfo<Value>& info) {
    TRACE("v8FieldSignature");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jobject field = helper.get<jobject>();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject signature = jni.CallStaticObjectMethod(v8Class, v8ClassFieldSignatureMethodID, field);

    helper.result(signature);
}

static void v8GenerateClass(const FunctionCallbackInfo<Value>& info) {
    TRACE("v8GenerateClass");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject code = jni.CallStaticObjectMethod(v8Class, v8GenerateClassMethodID, cls);

    helper.result(code);
}

static void v8LookupProperty(const FunctionCallbackInfo<Value>& info) {
    TRACE("v8LookupProperty");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    jstring prop = v2j_string(scope, helper.getRaw());
    jboolean isStatic = helper.get<jboolean>();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject code = jni.CallStaticObjectMethod(v8Class, v8LookupPropertyMethodID, cls, prop, isStatic);

    helper.result(code);
}


static void v8GetInterface(const FunctionCallbackInfo<Value>& info) {
    TRACE("v8GetInterface");
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);
    InfoHelper helper(scope, info);
    jclass cls = helper.get<jclass>();
    Local<Value> value = helper.getRaw();

    if (scope.checkAndRethrowV8Exception()) return;

    jobject obj = jni.CallStaticObjectMethod(v8Class, v8GetInterfaceMethodID, cls, v2j(scope, value));

    helper.rawResult(obj);
}

// Support routine for defining a V8 object template for all JNI calls.
Local<ObjectTemplate> createJVMTemplate(V8Scope &scope) {
    Local<ObjectTemplate> jvmTemplate = ObjectTemplate::New(scope.isolate);

    jvmTemplate->Set(scope.string("isEvalAllowed"), FunctionTemplate::New(scope.isolate, jvmIsEvalAllowed));
    jvmTemplate->Set(scope.string("setEvalAllowed"), FunctionTemplate::New(scope.isolate, jvmSetEvalAllowed));

    jvmTemplate->Set(scope.string("callMethod"), FunctionTemplate::New(scope.isolate, jvmCallMethod));
    jvmTemplate->Set(scope.string("callNonvirtualMethod"), FunctionTemplate::New(scope.isolate, jvmCallNonvirtualMethod));
    jvmTemplate->Set(scope.string("callStaticMethod"), FunctionTemplate::New(scope.isolate, jvmCallStaticMethod));
    jvmTemplate->Set(scope.string("findClass"), FunctionTemplate::New(scope.isolate, jvmFindClass));
    jvmTemplate->Set(scope.string("findClassPrivate"), FunctionTemplate::New(scope.isolate, jvmFindClassPrivate));
    jvmTemplate->Set(scope.string("createListAdapter"), FunctionTemplate::New(scope.isolate, jvmCreateListAdapter));
    jvmTemplate->Set(scope.string("getClassName"), FunctionTemplate::New(scope.isolate, jvmGetClassName));
    jvmTemplate->Set(scope.string("getHashCode"), FunctionTemplate::New(scope.isolate, jvmGetHashCode));
    jvmTemplate->Set(scope.string("getArrayElement"), FunctionTemplate::New(scope.isolate, jvmGetArrayElement));
    jvmTemplate->Set(scope.string("getArrayElementTypeCode"), FunctionTemplate::New(scope.isolate, jvmGetArrayElementTypeCode));
    jvmTemplate->Set(scope.string("getArrayLength"), FunctionTemplate::New(scope.isolate, jvmGetArrayLength));
    jvmTemplate->Set(scope.string("getMethodID"), FunctionTemplate::New(scope.isolate, jvmGetMethodID));
    jvmTemplate->Set(scope.string("getObjectClass"), FunctionTemplate::New(scope.isolate, jvmGetObjectClass));
    jvmTemplate->Set(scope.string("getObjectField"), FunctionTemplate::New(scope.isolate, jvmGetObjectField));
    jvmTemplate->Set(scope.string("getBooleanField"), FunctionTemplate::New(scope.isolate, jvmGetBooleanField));
    jvmTemplate->Set(scope.string("getByteField"), FunctionTemplate::New(scope.isolate, jvmGetByteField));
    jvmTemplate->Set(scope.string("getCharField"), FunctionTemplate::New(scope.isolate, jvmGetCharField));
    jvmTemplate->Set(scope.string("getShortField"), FunctionTemplate::New(scope.isolate, jvmGetShortField));
    jvmTemplate->Set(scope.string("getIntField"), FunctionTemplate::New(scope.isolate, jvmGetIntField));
    jvmTemplate->Set(scope.string("getLongField"), FunctionTemplate::New(scope.isolate, jvmGetLongField));
    jvmTemplate->Set(scope.string("getFloatField"), FunctionTemplate::New(scope.isolate, jvmGetFloatField));
    jvmTemplate->Set(scope.string("getDoubleField"), FunctionTemplate::New(scope.isolate, jvmGetDoubleField));
    jvmTemplate->Set(scope.string("setObjectField"), FunctionTemplate::New(scope.isolate, jvmSetObjectField));
    jvmTemplate->Set(scope.string("setBooleanField"), FunctionTemplate::New(scope.isolate, jvmSetBooleanField));
    jvmTemplate->Set(scope.string("setByteField"), FunctionTemplate::New(scope.isolate, jvmSetByteField));
    jvmTemplate->Set(scope.string("setCharField"), FunctionTemplate::New(scope.isolate, jvmSetCharField));
    jvmTemplate->Set(scope.string("setShortField"), FunctionTemplate::New(scope.isolate, jvmSetShortField));
    jvmTemplate->Set(scope.string("setIntField"), FunctionTemplate::New(scope.isolate, jvmSetIntField));
    jvmTemplate->Set(scope.string("setLongField"), FunctionTemplate::New(scope.isolate, jvmSetLongField));
    jvmTemplate->Set(scope.string("setFloatField"), FunctionTemplate::New(scope.isolate, jvmSetFloatField));
    jvmTemplate->Set(scope.string("setDoubleField"), FunctionTemplate::New(scope.isolate, jvmSetDoubleField));
    jvmTemplate->Set(scope.string("getStaticFieldID"), FunctionTemplate::New(scope.isolate, jvmGetStaticFieldID));
    jvmTemplate->Set(scope.string("getStaticMethodID"), FunctionTemplate::New(scope.isolate, jvmGetStaticMethodID));
    jvmTemplate->Set(scope.string("getStaticObjectField"), FunctionTemplate::New(scope.isolate, jvmGetStaticObjectField));
    jvmTemplate->Set(scope.string("getStaticBooleanField"), FunctionTemplate::New(scope.isolate, jvmGetStaticBooleanField));
    jvmTemplate->Set(scope.string("getStaticByteField"), FunctionTemplate::New(scope.isolate, jvmGetStaticByteField));
    jvmTemplate->Set(scope.string("getStaticCharField"), FunctionTemplate::New(scope.isolate, jvmGetStaticCharField));
    jvmTemplate->Set(scope.string("getStaticShortField"), FunctionTemplate::New(scope.isolate, jvmGetStaticShortField));
    jvmTemplate->Set(scope.string("getStaticIntField"), FunctionTemplate::New(scope.isolate, jvmGetStaticIntField));
    jvmTemplate->Set(scope.string("getStaticLongField"), FunctionTemplate::New(scope.isolate, jvmGetStaticLongField));
    jvmTemplate->Set(scope.string("getStaticFloatField"), FunctionTemplate::New(scope.isolate, jvmGetStaticFloatField));
    jvmTemplate->Set(scope.string("getStaticDoubleField"), FunctionTemplate::New(scope.isolate, jvmGetStaticDoubleField));
    jvmTemplate->Set(scope.string("setStaticObjectField"), FunctionTemplate::New(scope.isolate, jvmSetStaticObjectField));
    jvmTemplate->Set(scope.string("setStaticBooleanField"), FunctionTemplate::New(scope.isolate, jvmSetStaticBooleanField));
    jvmTemplate->Set(scope.string("setStaticByteField"), FunctionTemplate::New(scope.isolate, jvmSetStaticByteField));
    jvmTemplate->Set(scope.string("setStaticCharField"), FunctionTemplate::New(scope.isolate, jvmSetStaticCharField));
    jvmTemplate->Set(scope.string("setStaticShortField"), FunctionTemplate::New(scope.isolate, jvmSetStaticShortField));
    jvmTemplate->Set(scope.string("setStaticIntField"), FunctionTemplate::New(scope.isolate, jvmSetStaticIntField));
    jvmTemplate->Set(scope.string("setStaticLongField"), FunctionTemplate::New(scope.isolate, jvmSetStaticLongField));
    jvmTemplate->Set(scope.string("setStaticFloatField"), FunctionTemplate::New(scope.isolate, jvmSetStaticFloatField));
    jvmTemplate->Set(scope.string("setStaticDoubleField"), FunctionTemplate::New(scope.isolate, jvmSetStaticDoubleField));

    jvmTemplate->Set(scope.string("getSuperclass"), FunctionTemplate::New(scope.isolate, jvmGetSuperclass));
    jvmTemplate->Set(scope.string("isAssignableFrom"), FunctionTemplate::New(scope.isolate, jvmIsAssignableFrom));
    jvmTemplate->Set(scope.string("isInstanceOf"), FunctionTemplate::New(scope.isolate, jvmIsInstanceOf));
    jvmTemplate->Set(scope.string("isSameObject"), FunctionTemplate::New(scope.isolate, jvmIsSameObject));
    jvmTemplate->Set(scope.string("newArray"), FunctionTemplate::New(scope.isolate, jvmNewArray));
    jvmTemplate->Set(scope.string("newObject"), FunctionTemplate::New(scope.isolate, jvmNewObject));
    jvmTemplate->Set(scope.string("setArrayElement"), FunctionTemplate::New(scope.isolate, jvmSetArrayElement));

    jvmTemplate->Set(scope.string("executableSignature"), FunctionTemplate::New(scope.isolate, v8ExecutableSignature));
    jvmTemplate->Set(scope.string("fieldSignature"), FunctionTemplate::New(scope.isolate, v8FieldSignature));
    jvmTemplate->Set(scope.string("generateClass"), FunctionTemplate::New(scope.isolate, v8GenerateClass));
    jvmTemplate->Set(scope.string("lookupProperty"), FunctionTemplate::New(scope.isolate, v8LookupProperty));
    jvmTemplate->Set(scope.string("getInterface"), FunctionTemplate::New(scope.isolate, v8GetInterface));

    // nio support
    jvmTemplate->Set(scope.string("asArrayBuffer"), FunctionTemplate::New(scope.isolate, jvmAsArrayBuffer));
    jvmTemplate->Set(scope.string("asByteBuffer"), FunctionTemplate::New(scope.isolate, jvmAsByteBuffer));

    return jvmTemplate;
}

#define CODEGEN_FROM_STRING_ERROR_MSG "Code generation from strings disallowed for this context"

// shared code for load
static void globalLoad(const FunctionCallbackInfo<Value>& info) {
    TRACE("globalLoad");    // void V8GlobalFunctions.load(Object, Object)

    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    if (!scope.context->IsCodeGenerationFromStringsAllowed()) {
        scope.throwV8Exception(Exception::Error(scope.string(CODEGEN_FROM_STRING_ERROR_MSG)));
        scope.rethrowV8Exception();
        return;
    }

    Local<Object> self = info.This();
    jobject thiz = v2j_object_object(scope, self);

    jobject arg = nullptr;
    int length = info.Length();
    if (length > 0) {
        Local<Value> value = info[0];
        arg = v2j_java_unwrap(scope, value);
    }

    jobject result = jni.CallStaticObjectMethod(v8GlobalFunctionsClass,
        v8GlobalFunctionsLoadMethodID,
        thiz, arg);

    if (! scope.checkAndRethrowV8Exception()) {
        if (result) {
            Local<Value> resultVal = j2v_java_wrap(scope, result, false);
            if (scope.checkAndRethrowV8Exception()) return;
            info.GetReturnValue().Set(resultVal);
        } else {
            info.GetReturnValue().Set(scope.Undefined());
        }
    }
}

// shared code for fork and forkOnExecutor
static void globalForkImpl(bool executorArg, const FunctionCallbackInfo<Value>& info) {
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    if (!scope.context->IsCodeGenerationFromStringsAllowed()) {
        scope.throwV8Exception(Exception::Error(scope.string(CODEGEN_FROM_STRING_ERROR_MSG)));
        scope.rethrowV8Exception();
        return;
    }

    Local<Object> self = info.This();
    jobject thiz = v2j_object_object(scope, self);

    jobject src = nullptr;
    jobject executor = nullptr;
    jobjectArray args = nullptr;

    // get & convert arguments
    int length = info.Length();
    if (length > 0) {
        Local<Value> value = info[0];
        src = v2j_java_unwrap(scope, value);
    }

    if (executorArg && length > 1) {
        Local<Value> value = info[1];
        executor = v2j_java_unwrap(scope, value);
    }

    // if executor arg is present, then arguments start at 2 or else arguments start at 1
    int argsOffset = executorArg? 2 : 1;
    if (length > argsOffset) {
        args = jni.NewObjectArray(length - argsOffset, objectClass, nullptr);
        if (scope.checkAndRethrowV8Exception()) return;

        for (int index = argsOffset; index < length; index++) {
            Local<Value> rawVal = info[index];
            jni.SetObjectArrayElement(args, index - argsOffset, v2j_java_unwrap(scope, rawVal));
            if (scope.checkAndRethrowV8Exception()) return;
        }
    }

    jobject result = jni.CallStaticObjectMethod(v8GlobalFunctionsClass,
        v8GlobalFunctionsForkOnExecutorMethodID, thiz, src, executor, args);

    if (! scope.checkAndRethrowV8Exception()) {
        if (result) {
            Local<Value> resultVal = j2v_java_wrap(scope, result, false);
            if (scope.checkAndRethrowV8Exception()) return;
            info.GetReturnValue().Set(resultVal);
        } else {
            info.GetReturnValue().Set(scope.Undefined());
        }
    }
}

static void globalFork(const FunctionCallbackInfo<Value>& info) {
    TRACE("globalFork");
    // void V8GlobalFunctions.fork(Object thiz, Object src, Object[] args)
    globalForkImpl(false /* no executor arg */, info);
}

static void globalForkOnExecutor(const FunctionCallbackInfo<Value>& info) {
    TRACE("globalForkOnExecutor");
    // void V8GlobalFunctions.forkOnExecutor(Object thiz, Object src, Object executor, Object[] args)
    globalForkImpl(true /* executor arg */, info);
}

static void globalPrint(const FunctionCallbackInfo<Value>& info) {
    TRACE("globalPrint");
    // void V8GlobalFunctions.print(Object, Object...)
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    Local<Object> self = info.This();
    jobject thiz = v2j_object_object(scope, self);

    // get total arguments passed to the call
    int length = info.Length();

    jobjectArray args = jni.NewObjectArray(length, objectClass, nullptr);
    if (scope.checkAndRethrowV8Exception()) return;

    for (int index = 0; index < length; index++) {
        Local<Value> val = info[index];
        jni.SetObjectArrayElement(args, index, v2j_java_unwrap(scope, val));
        if (scope.checkAndRethrowV8Exception()) return;
    }

    jni.CallStaticVoidMethod(v8GlobalFunctionsClass, v8GlobalFunctionsPrintMethodID, thiz, args);
    if (scope.checkAndRethrowV8Exception()) return;
}

static Local<FunctionTemplate> newNonConstructor(Isolate* isolate, FunctionCallback callback) {
    return FunctionTemplate::New(isolate, callback, Local<Value>(), Local<Signature>(),
        0, ConstructorBehavior::kThrow);
}

// Add all the primitives to the supplied global template
void add_primitives(Isolate* isolate, JNIEnv* env, Local<ObjectTemplate>& globalTemplate) {
    V8Scope scope(env, isolate);
    Local<Value> undefined = scope.Undefined();

    globalTemplate->Set(scope.string("load"), newNonConstructor(scope.isolate, globalLoad), DontEnum);
    globalTemplate->Set(scope.string("fork"), newNonConstructor(scope.isolate, globalFork), DontEnum);
    globalTemplate->Set(scope.string("forkOnExecutor"), newNonConstructor(scope.isolate, globalForkOnExecutor), DontEnum);
    globalTemplate->Set(scope.string("print"), newNonConstructor(scope.isolate, globalPrint), DontEnum);

    // These are initialized here so that we could use DontEnum
    globalTemplate->Set(scope.string("arguments"), undefined, DontEnum);
    globalTemplate->Set(scope.string("context"), undefined, DontEnum);
    globalTemplate->Set(scope.string("engine"), undefined, DontEnum);
    globalTemplate->Set(scope.string("javax.script.filename"), undefined, DontEnum);
    globalTemplate->Set(scope.string("javax.script.argv"), undefined, DontEnum);
}
