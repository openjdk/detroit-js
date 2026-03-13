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

#include "jvmv8_jni_support.hpp"
#include "jvmv8_java_classes.hpp"
#include "jvmv8.hpp"
#include "jvmv8_jsobject.hpp"

static void jsObjectCall(const FunctionCallbackInfo<Value>& info) {
    // Object JSObject.call(Object, Object...) or
    // Object JSObject.newObject(Object...)

    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    // get total arguments passed to the call
    int length = info.Length();

    jobjectArray args = jni.NewObjectArray(length, objectClass, nullptr);
    if (scope.checkAndRethrowV8Exception()) return;

    for (int index = 0; index < length; index++) {
        Local<Value> rawVal = info[index];
        jni.SetObjectArrayElement(args, index, v2j_java_unwrap(scope, rawVal));
        if (scope.checkAndRethrowV8Exception()) return;
    }

    jobject result;
    if (info.IsConstructCall()) {
        result = jni.CallObjectMethod(jsobject, jsObjectNewObjectMethodID, args);
    } else {
        result = jni.CallObjectMethod(jsobject, jsObjectCallMethodID, jsobject, args);
    }

    if (! scope.checkAndRethrowV8Exception()) {
        if (result) {
            Local<Value> resultVal = j2v_java_wrap(scope, result, false);
            if (scope.checkAndRethrowV8Exception()) return;
            info.GetReturnValue().Set(resultVal);
        } else {
            info.GetReturnValue().Set(scope.Null());
        }
    }
}

static Intercepted jsObjectGetMember(Local<Name> property, const PropertyCallbackInfo<Value>& info) {
    // Object V8.jsObjectGetMember(JSObject, String)
    // Object JSObject.GetMember(JSSymbol, String)

    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jobject result;
    if (property->IsSymbol()) {
        Local<Symbol> sym = property.As<Symbol>();
        jobject propSym = v2j_symbol(scope, sym);
        result = jni.CallObjectMethod(jsobject, jsObjectGetMemberSymbolMethodID, propSym);
    } else {
        jstring propName = v2j_string(scope, property);

        // We avoid calling JSObject.getMember(String) directly, we call V8.jsObjectGetMember(JSObject, String)
        // instead. This is to make sure a function value is returned for "toString" name.
        result = jni.CallStaticObjectMethod(v8Class, v8jsObjectGetMemberMethodID, jsobject, propName);
    }

    if (! scope.checkAndRethrowV8Exception()) {
        if (result) {
            Local<Value> resultVal = j2v_java_wrap(scope, result, false);
            if (scope.checkAndRethrowV8Exception()) return Intercepted::kNo;
            info.GetReturnValue().Set(resultVal);
        } else {
            info.GetReturnValue().Set(scope.Null());
        }
    }

    return Intercepted::kYes;
}

static Intercepted jsObjectSetMember(Local<Name> property, Local<Value> value, const PropertyCallbackInfo<void>& info) {
    // Object JSObject.setMember(String, Object)
    // Object JSObject.setMember(JSSymbol, Object)

    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);
    jobject jvalue = v2j_java_unwrap(scope, value);
    jboolean result = false;

    if (property->IsSymbol()) {
        Local<Symbol> sym = property.As<Symbol>();
        jobject propSym = v2j_symbol(scope, sym);

        result = jni.CallBooleanMethod(jsobject, jsObjectSetMemberSymbolMethodID, propSym, jvalue);
        if (! scope.checkAndRethrowV8Exception()) {
            info.GetReturnValue().Set(result? scope.True() : scope.False());
        }
    } else {
        jstring propName = v2j_string(scope, property);
        result = jni.CallBooleanMethod(jsobject, jsObjectSetMemberMethodID, propName, jvalue);
    }

    if (! scope.checkAndRethrowV8Exception()) {
        info.GetReturnValue().Set(result? scope.True() : scope.False());
    }

    return Intercepted::kYes;
}

static Intercepted jsObjectQueryMember(Local< Name > property, const PropertyCallbackInfo<Integer> &info) {
    // int V8.proeprtyAttributeFlags(JSObject, String)
    // int V8.proeprtyAttributeFlags(JSObject, JSSymbol)

    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jboolean propExists;
    if (property->IsSymbol()) {
        Local<Symbol> sym = property.As<Symbol>();
        jobject propSym = v2j_symbol(scope, sym);

        propExists = jni.CallBooleanMethod(jsobject, jsObjectHasMemberSymbolMethodID, propSym);
        if (!scope.checkAndRethrowV8Exception() && propExists) {
            jint flags = jni.CallStaticIntMethod(v8Class, v8SymbolPropAttrFlagsMethodID, jsobject, propSym);
            if (!scope.checkAndRethrowV8Exception()) {
                info.GetReturnValue().Set(static_cast<v8::PropertyAttribute>(flags));
            }
        }
    } else {
        jstring propName = v2j_string(scope, property);

        propExists = jni.CallBooleanMethod(jsobject, jsObjectHasMemberMethodID, propName);
        if (!scope.checkAndRethrowV8Exception() && propExists) {
            jint flags = jni.CallStaticIntMethod(v8Class, v8StringPropAttrFlagsMethodID, jsobject, propName);
            if (!scope.checkAndRethrowV8Exception()) {
                info.GetReturnValue().Set(static_cast<v8::PropertyAttribute>(flags));
            }
        }
    }

    // FIXME This is a workaround.
    // V8 calls this query callback to check if a property exists.
    // If it doesn't exist, the implementation expects the return value
    // in 'info' to be set to v8::internal::PropertyAttributes::ABSENT.
    // But this value is not exposed in the public API.
    // For now, return 'kNo' instead which has a similar effect.
    //
    // See doc: v8-template.h NamedPropertyQueryCallback
    // See impl: v8/v8/src/objects/js-objects.cc JSReceiver::HasProperty
    return propExists ? Intercepted::kYes : Intercepted::kNo;
}

static Intercepted jsObjectRemoveMember(Local<Name> property, const PropertyCallbackInfo<Boolean>& info) {
    // boolean JSObject.removeMember(String)
    // boolean JSObject.removeMember(JSSymbol)

    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);
    jboolean result = false;
    if (property->IsSymbol()) {
        Local<Symbol> sym = property.As<Symbol>();
        jobject propSym = v2j_symbol(scope, sym);
        result = jni.CallBooleanMethod(jsobject, jsObjectRemoveMemberSymbolMethodID, propSym);
    } else {
        jstring propName = v2j_string(scope, property);
        result = jni.CallBooleanMethod(jsobject, jsObjectRemoveMemberMethodID, propName);
    }

    if (scope.checkAndRethrowV8Exception()) return Intercepted::kNo;
    info.GetReturnValue().Set(result? scope.True() : scope.False());

    return Intercepted::kYes;
}

static void jsObjectGetNamedProperties(const PropertyCallbackInfo<Array>& info) {
    // String[] JSObject.getNamedProperties();
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jobjectArray namesArray = (jobjectArray)jni.CallObjectMethod(jsobject, jsObjectGetNamedPropertiesMethodID);
    if (! scope.checkAndRethrowV8Exception()) {
        if (namesArray) {
            int length = jni.GetArrayLength(namesArray);
            Local<Array> result = Array::New(scope.isolate, length);
            if (scope.checkAndRethrowV8Exception()) return;

            for (int i = 0; i < length; i++) {
                // get element should be safe - we're within array lenth
                jstring name = (jstring)jni.GetObjectArrayElement(namesArray, i);
                if (name) {
                    result->Set(scope.context, i, j2v_string(scope, name)).FromJust();
                }
            }
            info.GetReturnValue().Set(result);
        }
    }
}

static Intercepted jsObjectGetSlot(uint32_t index, const PropertyCallbackInfo<Value>& info) {
    // Object JSObject.getSlot(int)
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jobject result = jni.CallObjectMethod(jsobject, jsObjectGetSlotMethodID, index);
    if (! scope.checkAndRethrowV8Exception()) {
        if (result) {
            Local<Value> resultVal = j2v_java_wrap(scope, result, false);
            if (scope.checkAndRethrowV8Exception()) return Intercepted::kNo;
            info.GetReturnValue().Set(resultVal);
        } else {
            info.GetReturnValue().Set(scope.Null());
        }
    }

    return Intercepted::kYes;
}

static Intercepted jsObjectSetSlot(uint32_t index, Local<Value> value, const PropertyCallbackInfo<void>& info) {
    // Object JSObject.setSlot(int, Object)
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jobject jvalue = v2j_java_unwrap(scope, value);
    jboolean result = jni.CallBooleanMethod(jsobject, jsObjectSetSlotMethodID, index, jvalue);
    if (! scope.checkAndRethrowV8Exception()) {
        info.GetReturnValue().Set(result? scope.True() : scope.False());
    }

    return Intercepted::kYes;
}

static Intercepted jsObjectQuerySlot(uint32_t index, const PropertyCallbackInfo< Integer > &info) {
    // int V8.proeprtyAttributeFlags(Object, String)
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jboolean slotExists = jni.CallBooleanMethod(jsobject, jsObjectHasSlotMethodID, index);
    if (!scope.checkAndRethrowV8Exception() && slotExists) {
        jint flags = jni.CallStaticIntMethod(v8Class, v8IntPropAttrFlagsMethodID, jsobject, index);
        if (!scope.checkAndRethrowV8Exception()) {
            info.GetReturnValue().Set(static_cast<v8::PropertyAttribute>(flags));
        }
    }

    return Intercepted::kYes;
}

static Intercepted jsObjectRemoveSlot(uint32_t index, const PropertyCallbackInfo<Boolean>& info) {
    // boolean JSObject.removeSlot(int)
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jboolean result = jni.CallBooleanMethod(jsobject, jsObjectRemoveSlotMethodID, index);
    if (scope.checkAndRethrowV8Exception()) return Intercepted::kNo;
    info.GetReturnValue().Set(result? scope.True() : scope.False());

    return Intercepted::kYes;
}

static void jsObjectGetIndexedProperties(const PropertyCallbackInfo<Array>& info) {
    // int[] JSObject.getIndexedProperties()
    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSObject instance from the script wrapper
    Local<Object> self = info.This();
    Local<Value> external = self->GetInternalField(0).As<v8::Value>();
    jobject jsobject = v2j_object(external);

    jintArray indicesArray = (jintArray)jni.CallObjectMethod(jsobject, jsObjectGetIndexedPropertiesMethodID);
    if (! scope.checkAndRethrowV8Exception()) {
        if (indicesArray) {
            int length = jni.GetArrayLength(indicesArray);
            Local<Array> result = Array::New(scope.isolate, length);
            if (scope.checkAndRethrowV8Exception()) return;

            jint* elements = jni.GetIntArrayElements(indicesArray);
            if (elements) {
                for (int i = 0; i < length; i++) {
                    result->Set(scope.context, i, scope.integer(elements[i])).FromJust();
                }
                jni.ReleaseIntArrayElements(indicesArray, elements);
                info.GetReturnValue().Set(result);
            }
        }
    }
}

static Local<ObjectTemplate> createJSObjectTemplateCommon(V8Scope& scope, bool callable) {
    Local<ObjectTemplate> jsObjectTemplate = ObjectTemplate::New(scope.isolate);
    // JSObject wrapper has to hold underlying JSObject instance as internal field
    jsObjectTemplate->SetInternalFieldCount(1);

    // named property access
    NamedPropertyHandlerConfiguration nameHandler(jsObjectGetMember, jsObjectSetMember,
        jsObjectQueryMember, jsObjectRemoveMember, jsObjectGetNamedProperties);
    jsObjectTemplate->SetHandler(nameHandler);

    // indexed property access
    IndexedPropertyHandlerConfiguration indexHandler(jsObjectGetSlot, jsObjectSetSlot,
        jsObjectQuerySlot, jsObjectRemoveSlot, jsObjectGetIndexedProperties);
    jsObjectTemplate->SetHandler(indexHandler);

    if (callable) {
        // support to call JSObejct or use it as constructor
        jsObjectTemplate->SetCallAsFunctionHandler(jsObjectCall);
    }

    return jsObjectTemplate;
}

Local<ObjectTemplate> createJSObjectTemplate(V8Scope &scope) {
    return createJSObjectTemplateCommon(scope, false /* not callable */);
}

Local<ObjectTemplate> createJSObjectCallableTemplate(V8Scope& scope) {
    return createJSObjectTemplateCommon(scope, true /* callable */);
}

// Function callback to handle call/new on JSFunction objects
static void jsFunctionCall(const FunctionCallbackInfo<Value>& info) {
    // Object JSFunction.call(Object, Object...) or
    // Object JSFunction.newObject(Object...)

    V8Scope scope(info.GetIsolate());
    JNIForJS jni(scope);

    // get the underlying JSFunction instance - we pass JSFunction
    // external reference as data when we create Function object
    Local<Value> external = info.Data();
    jobject jsobject = v2j_object(external);

    // get total arguments passed to the call
    int length = info.Length();

    jobjectArray args = jni.NewObjectArray(length, objectClass, nullptr);
    if (scope.checkAndRethrowV8Exception()) return;

    for (int index = 0; index < length; index++) {
        Local<Value> rawVal = info[index];
        jni.SetObjectArrayElement(args, index, v2j_java_unwrap(scope, rawVal));
        if (scope.checkAndRethrowV8Exception()) return;
    }

    jobject result;
    if (info.IsConstructCall()) {
        result = jni.CallObjectMethod(jsobject, jsFunctionNewObjectMethodID, args);
    } else {
        Local<Value> self = info.This();
        jobject thiz = v2j_java_unwrap(scope, self);
        result = jni.CallObjectMethod(jsobject, jsFunctionCallMethodID, thiz, args);
    }

    if (! scope.checkAndRethrowV8Exception()) {
        if (result) {
            Local<Value> resultVal = j2v_java_wrap(scope, result, false);
            if (scope.checkAndRethrowV8Exception()) return;
            info.GetReturnValue().Set(resultVal);
        } else {
            info.GetReturnValue().Set(scope.Null());
        }
    }
}

MaybeLocal<Function> createFunction(V8Scope& scope, jobject jsfunction) {
    return Function::New(scope.context, jsFunctionCall, j2v_object(scope, jsfunction));
}
