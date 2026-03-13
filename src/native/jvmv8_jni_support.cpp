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

JNI::JNI(V8Scope& scope) : JNI(scope.env) {
}

bool JNI::handledException() {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();

        return true;
    }
    return false;
}

bool JNI::checkException() {
    return env->ExceptionCheck();
}

jboolean JNI::CallBooleanMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallBooleanMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jboolean result = env->CallBooleanMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return false;
    }

    return result;
}

jbyte JNI::CallByteMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallByteMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jbyte result = env->CallByteMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jchar JNI::CallCharMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallCharMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jchar result = env->CallCharMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jshort JNI::CallShortMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallShortMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jshort result = env->CallShortMethod(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jdouble JNI::CallDoubleMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallDoubleMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jdouble result = env->CallDoubleMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0.0;
    }

    return result;
}

jfloat JNI::CallFloatMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallFloatMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jfloat result = env->CallFloatMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0.0F;
    }

    return result;
}

jint JNI::CallIntMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallIntMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jint result = env->CallIntMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jlong JNI::CallLongMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallLongMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jlong result = env->CallLongMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0L;
    }

    return result;
}

jobject JNI::CallObjectMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallObjectMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jobject result = env->CallObjectMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

void JNI::CallVoidMethod(jobject obj, jmethodID methodID, ...) {
    TRACE("CallVoidMethod");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    env->CallVoidMethodV(obj, methodID, args);
    va_end(args);

    if (handledException()) {
        return;
    }
}

jboolean JNI::CallStaticBooleanMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticBooleanMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jboolean result = env->CallStaticBooleanMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return false;
    }

    return result;
}

jbyte JNI::CallStaticByteMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticByteMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jbyte result = env->CallStaticByteMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return false;
    }

    return result;
}

jchar JNI::CallStaticCharMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticCharMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jchar result = env->CallStaticCharMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jshort JNI::CallStaticShortMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticShortMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jshort result = env->CallStaticShortMethod(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jdouble JNI::CallStaticDoubleMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticDoubleMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jdouble result = env->CallStaticDoubleMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0.0;
    }

    return result;
}

jfloat JNI::CallStaticFloatMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticFloatMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jfloat result = env->CallStaticFloatMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0.0F;
    }

    return result;
}

jint JNI::CallStaticIntMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticIntMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jint result = env->CallStaticIntMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jlong JNI::CallStaticLongMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticLongMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jlong result = env->CallStaticLongMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return 0L;
    }

    return result;
}

jobject JNI::CallStaticObjectMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticObjectMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jobject result = env->CallStaticObjectMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

void JNI::CallStaticVoidMethod(jclass clazz, jmethodID methodID, ...) {
    TRACE("CallStaticVoidMethod");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    env->CallStaticVoidMethodV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return;
    }
}

jboolean JNI::CallBooleanMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallBooleanMethodA");
    assert(!env->ExceptionCheck());
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jboolean result = env->CallBooleanMethodA(obj, methodID, args);

    if (handledException()) {
        return false;
    }

    return result;
}

jbyte JNI::CallByteMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallByteMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jbyte result = env->CallByteMethodA(obj, methodID, args);

    if (handledException()) {
        return false;
    }

    return result;
}

jchar JNI::CallCharMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallCharMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jchar result = env->CallCharMethodA(obj, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jshort JNI::CallShortMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallShortMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jshort result = env->CallShortMethodA(obj, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jdouble JNI::CallDoubleMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallDoubleMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jdouble result = env->CallDoubleMethodA(obj, methodID, args);

    if (handledException()) {
        return 0.0;
    }

    return result;
}

jfloat JNI::CallFloatMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallFloatMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jfloat result = env->CallFloatMethodA(obj, methodID, args);

    if (handledException()) {
        return 0.0F;
    }

    return result;
}

jint JNI::CallIntMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallIntMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jint result = env->CallIntMethodA(obj, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jlong JNI::CallLongMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallLongMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jlong result = env->CallLongMethodA(obj, methodID, args);

    if (handledException()) {
        return 0L;
    }

    return result;
}

jobject JNI::CallObjectMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallObjectMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jobject result = env->CallObjectMethodA(obj, methodID, args);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

void JNI::CallVoidMethodA(jobject obj, jmethodID methodID, jvalue* args) {
    TRACE("CallVoidMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    env->CallVoidMethodA(obj, methodID, args);

    if (handledException()) {
        return;
    }
}

jboolean JNI::CallStaticBooleanMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticBooleanMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jboolean result = env->CallStaticBooleanMethodA(clazz, methodID, args);

    if (handledException()) {
        return false;
    }

    return result;
}

jbyte JNI::CallStaticByteMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticByteMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jbyte result = env->CallStaticByteMethodA(clazz, methodID, args);

    if (handledException()) {
        return false;
    }

    return result;
}

jchar JNI::CallStaticCharMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticCharMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jchar result = env->CallStaticCharMethodA(clazz, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jshort JNI::CallStaticShortMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticShortMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jshort result = env->CallStaticShortMethodA(clazz, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jdouble JNI::CallStaticDoubleMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticDoubleMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jdouble result = env->CallStaticDoubleMethodA(clazz, methodID, args);

    if (handledException()) {
        return 0.0;
    }

    return result;
}

jfloat JNI::CallStaticFloatMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticFloatMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jfloat result = env->CallStaticFloatMethodA(clazz, methodID, args);

    if (handledException()) {
        return 0.0F;
    }

    return result;
}

jint JNI::CallStaticIntMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticIntMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jint result = env->CallStaticIntMethodA(clazz, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jlong JNI::CallStaticLongMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticLongMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jlong result = env->CallStaticLongMethodA(clazz, methodID, args);

    if (handledException()) {
        return 0L;
    }

    return result;
}

jobject JNI::CallStaticObjectMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticObjectMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jobject result = env->CallStaticObjectMethodA(clazz, methodID, args);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

void JNI::CallStaticVoidMethodA(jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallStaticVoidMethodA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    env->CallStaticVoidMethodA(clazz, methodID, args);

    if (handledException()) {
        return;
    }
}

jboolean JNI::CallNonvirtualBooleanMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualBooleanMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jboolean result = env->CallNonvirtualBooleanMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return false;
    }

    return result;
}

jbyte JNI::CallNonvirtualByteMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualByteMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jbyte result = env->CallNonvirtualByteMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return false;
    }

    return result;
}

jchar JNI::CallNonvirtualCharMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualCharMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jchar result = env->CallNonvirtualCharMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jshort JNI::CallNonvirtualShortMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualShortMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jshort result = env->CallNonvirtualShortMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jdouble JNI::CallNonvirtualDoubleMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualDoubleMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jdouble result = env->CallNonvirtualDoubleMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return 0.0;
    }

    return result;
}

jfloat JNI::CallNonvirtualFloatMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualFloatMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jfloat result = env->CallNonvirtualFloatMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return 0.0F;
    }

    return result;
}

jint JNI::CallNonvirtualIntMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualIntMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jint result = env->CallNonvirtualIntMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return 0;
    }

    return result;
}

jlong JNI::CallNonvirtualLongMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualLongMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jlong result = env->CallNonvirtualLongMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return 0L;
    }

    return result;
}

jobject JNI::CallNonvirtualObjectMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualObjectMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jobject result = env->CallNonvirtualObjectMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

void JNI::CallNonvirtualVoidMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args) {
    TRACE("CallNonvirtualVoidMethodA");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    env->CallNonvirtualVoidMethodA(obj, clazz, methodID, args);

    if (handledException()) {
        return;
    }
}

jobject JNI::GetObjectField(jobject obj, jfieldID fieldID) {
    TRACE("GetObjectField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jobject result = env->GetObjectField(obj, fieldID);

    return result;
}

jboolean JNI::GetBooleanField(jobject obj, jfieldID fieldID) {
    TRACE("GetBooleanField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jboolean result = env->GetBooleanField(obj, fieldID);

    return result;
}

jbyte JNI::GetByteField(jobject obj, jfieldID fieldID) {
    TRACE("GetByteField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jbyte result = env->GetByteField(obj, fieldID);

    return result;
}

jchar JNI::GetCharField(jobject obj, jfieldID fieldID) {
    TRACE("GetCharField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jchar result = env->GetCharField(obj, fieldID);

    return result;
}

jshort JNI::GetShortField(jobject obj, jfieldID fieldID) {
    TRACE("GetShortField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jshort result = env->GetShortField(obj, fieldID);

    return result;
}

jint JNI::GetIntField(jobject obj, jfieldID fieldID) {
    TRACE("GetIntField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jint result = env->GetIntField(obj, fieldID);

    return result;
}

jlong JNI::GetLongField(jobject obj, jfieldID fieldID) {
    TRACE("GetLongField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jlong result = env->GetLongField(obj, fieldID);

    return result;
}

jfloat JNI::GetFloatField(jobject obj, jfieldID fieldID) {
    TRACE("GetFloatField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jfloat result = env->GetFloatField(obj, fieldID);

    return result;
}

jdouble JNI::GetDoubleField(jobject obj, jfieldID fieldID) {
    TRACE("GetDoubleField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    jdouble result = env->GetDoubleField(obj, fieldID);

    return result;
}

void JNI::SetObjectField(jobject obj, jfieldID fieldID, jobject value) {
    TRACE("SetObjectField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetObjectField(obj, fieldID, value);
}

void JNI::SetBooleanField(jobject obj, jfieldID fieldID, jboolean value) {
    TRACE("SetBooleanField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetBooleanField(obj, fieldID, value);
}

void JNI::SetByteField(jobject obj, jfieldID fieldID, jbyte value) {
    TRACE("SetByteField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetByteField(obj, fieldID, value);
}

void JNI::SetCharField(jobject obj, jfieldID fieldID, jchar value) {
    TRACE("SetCharField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetCharField(obj, fieldID, value);
}

void JNI::SetShortField(jobject obj, jfieldID fieldID, jshort value) {
    TRACE("SetShortField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetShortField(obj, fieldID, value);
}

void JNI::SetIntField(jobject obj, jfieldID fieldID, jint value) {
    TRACE("SetIntField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetIntField(obj, fieldID, value);
}

void JNI::SetLongField(jobject obj, jfieldID fieldID, jlong value) {
    TRACE("SetLongField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetLongField(obj, fieldID, value);
}

void JNI::SetFloatField(jobject obj, jfieldID fieldID, jfloat value) {
    TRACE("SetFloatField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetFloatField(obj, fieldID, value);
}

void JNI::SetDoubleField(jobject obj, jfieldID fieldID, jdouble value) {
    TRACE("SetDoubleField");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    assert(fieldID != 0);
    env->SetDoubleField(obj, fieldID, value);
}

void JNI::DeleteGlobalRef(jobject gref) {
    TRACE("DeleteGlobalRef");
    assert(!env->ExceptionCheck());
    env->DeleteGlobalRef(gref);
}

void JNI::DeleteLocalRef(jobject obj) {
    TRACE("DeleteLocalRef");
    assert(!env->ExceptionCheck());
    env->DeleteLocalRef(obj);
}

void JNI::DeleteWeakGlobalRef(jweak ref) {
    TRACE("DeleteWeakGlobalRef");
    assert(!env->ExceptionCheck());
    env->DeleteWeakGlobalRef(ref);
}

jclass JNI::FindClass(const char* name) {
    TRACE("FindClass");
    assert(!env->ExceptionCheck());
    assert(name != nullptr && name[0] != '\0');
    jclass result = env->FindClass(name);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jobject JNI::GetStaticObjectField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticObjectField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jobject result = env->GetStaticObjectField(clazz, fieldID);

    return result;
}

jboolean JNI::GetStaticBooleanField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticBooleanField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jboolean result = env->GetStaticBooleanField(clazz, fieldID);

    return result;
}

jbyte JNI::GetStaticByteField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticByteField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jbyte result = env->GetStaticByteField(clazz, fieldID);

    return result;
}

jchar JNI::GetStaticCharField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticCharField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jchar result = env->GetStaticCharField(clazz, fieldID);

    return result;
}

jshort JNI::GetStaticShortField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticShortField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jshort result = env->GetStaticShortField(clazz, fieldID);

    return result;
}

jint JNI::GetStaticIntField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticIntField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jint result = env->GetStaticIntField(clazz, fieldID);

    return result;
}

jlong JNI::GetStaticLongField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticLongField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jlong result = env->GetStaticLongField(clazz, fieldID);

    return result;
}

jfloat JNI::GetStaticFloatField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticFloatField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jfloat result = env->GetStaticFloatField(clazz, fieldID);

    return result;
}

jdouble JNI::GetStaticDoubleField(jclass clazz, jfieldID fieldID) {
    TRACE("GetStaticDoubleField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    jdouble result = env->GetStaticDoubleField(clazz, fieldID);

    return result;
}

void JNI::SetStaticObjectField(jclass clazz, jfieldID fieldID, jobject value) {
    TRACE("SetStaticObjectField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticObjectField(clazz, fieldID, value);
}

void JNI::SetStaticBooleanField(jclass clazz, jfieldID fieldID, jboolean value) {
    TRACE("SetStaticBooleanField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticBooleanField(clazz, fieldID, value);
}

void JNI::SetStaticByteField(jclass clazz, jfieldID fieldID, jbyte value) {
    TRACE("SetStaticByteField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticByteField(clazz, fieldID, value);
}

void JNI::SetStaticCharField(jclass clazz, jfieldID fieldID, jchar value) {
    TRACE("SetStaticCharField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticCharField(clazz, fieldID, value);
}

void JNI::SetStaticShortField(jclass clazz, jfieldID fieldID, jshort value) {
    TRACE("SetStaticShortField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticShortField(clazz, fieldID, value);
}

void JNI::SetStaticIntField(jclass clazz, jfieldID fieldID, jint value) {
    TRACE("SetStaticIntField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticIntField(clazz, fieldID, value);
}

void JNI::SetStaticLongField(jclass clazz, jfieldID fieldID, jlong value) {
    TRACE("SetStaticLongField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticLongField(clazz, fieldID, value);
}

void JNI::SetStaticFloatField(jclass clazz, jfieldID fieldID, jfloat value) {
    TRACE("SetStaticFloatField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticFloatField(clazz, fieldID, value);
}

void JNI::SetStaticDoubleField(jclass clazz, jfieldID fieldID, jdouble value) {
    TRACE("SetStaticDoubleField");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(fieldID != 0);
    env->SetStaticDoubleField(clazz, fieldID, value);
}

jsize JNI::GetArrayLength(jarray array) {
    TRACE("GetArrayLength");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    return env->GetArrayLength(array);
}

jboolean* JNI::GetBooleanArrayElements(jbooleanArray array) {
    TRACE("GetBooleanArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetBooleanArrayElements(array, &isCopy);
}

jbyte* JNI::GetByteArrayElements(jbyteArray array) {
    TRACE("GetByteArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetByteArrayElements(array, &isCopy);
}

jchar* JNI::GetCharArrayElements(jcharArray array) {
    TRACE("GetCharArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetCharArrayElements(array, &isCopy);
}

jshort* JNI::GetShortArrayElements(jshortArray array) {
    TRACE("GetShortArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetShortArrayElements(array, &isCopy);
}

jint* JNI::GetIntArrayElements(jintArray array) {
    TRACE("GetIntArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetIntArrayElements(array, &isCopy);
}

jlong* JNI::GetLongArrayElements(jlongArray array) {
    TRACE("GetLongArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetLongArrayElements(array, &isCopy);
}

jfloat* JNI::GetFloatArrayElements(jfloatArray array) {
    TRACE("GetFloatArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetFloatArrayElements(array, &isCopy);
}

jdouble* JNI::GetDoubleArrayElements(jdoubleArray array) {
    TRACE("GetDoubleArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    jboolean isCopy = false;

    return env->GetDoubleArrayElements(array, &isCopy);
}

void JNI::ReleaseBooleanArrayElements(jbooleanArray array, jboolean* elems) {
    TRACE("ReleaseBooleanArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseBooleanArrayElements(array, elems, 0);
}

void JNI::ReleaseByteArrayElements(jbyteArray array, jbyte* elems) {
    TRACE("ReleaseByteArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseByteArrayElements(array, elems, 0);
}

void JNI::ReleaseCharArrayElements(jcharArray array, jchar* elems) {
    TRACE("ReleaseCharArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseCharArrayElements(array, elems, 0);
}

void JNI::ReleaseShortArrayElements(jshortArray array,  jshort* elems) {
    TRACE("ReleaseShortArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseShortArrayElements(array, elems, 0);
}

void JNI::ReleaseIntArrayElements(jintArray array, jint* elems) {
    TRACE("ReleaseIntArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseIntArrayElements(array, elems, 0);
}

void JNI::ReleaseLongArrayElements(jlongArray array,  jlong* elems) {
    TRACE("ReleaseLongArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseLongArrayElements(array, elems, 0);
}

void JNI::ReleaseFloatArrayElements(jfloatArray array, jfloat* elems) {
    TRACE("ReleaseFloatArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseFloatArrayElements(array, elems, 0);
}

void JNI::ReleaseDoubleArrayElements(jdoubleArray array, jdouble* elems) {
    TRACE("ReleaseDoubleArrayElements");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(elems != nullptr);
    env->ReleaseDoubleArrayElements(array, elems, 0);
}


jobject JNI::NewDirectByteBuffer(void* address, jlong capacity) {
    TRACE("NewDirectByteBuffer");
    assert(!env->ExceptionCheck());
    assert(address != nullptr);
    assert(0 <= capacity);
    jobject result = env->NewDirectByteBuffer(address, capacity);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

void* JNI::GetDirectBufferAddress(jobject buf) {
    TRACE("GetDirectBufferAddress");
    assert(!env->ExceptionCheck());
    assert(buf != nullptr);
    void* result = env->GetDirectBufferAddress(buf);

    return result;
}

jlong JNI::GetDirectBufferCapacity(jobject buf) {
    TRACE("GetDirectBufferCapacity");
    assert(!env->ExceptionCheck());
    assert(buf != nullptr);
    jlong result = env->GetDirectBufferCapacity(buf);

    return result;
}

jobject JNI::GetObjectArrayElement(jobjectArray array, jsize index) {
    TRACE("GetObjectArrayElement");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(0 <= index);
    jobject result = env->GetObjectArrayElement(array, index);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

void JNI::SetObjectArrayElement(jobjectArray array, jsize index, jobject val) {
    TRACE("SetObjectArrayElement");
    assert(!env->ExceptionCheck());
    assert(array != nullptr);
    assert(0 <= index);
    env->SetObjectArrayElement(array, index, val);

    if (handledException()) {
        return;
    }
}

jmethodID JNI::GetMethodID(jclass clazz, const char* name, const char* sig) {
    TRACE("GetMethodID");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(name != nullptr && name[0] != '\0');
    assert(sig != nullptr && sig[0] != '\0');
    jmethodID result = env->GetMethodID(clazz, name, sig);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jclass JNI::GetObjectClass(jobject obj) {
    TRACE("GetObjectClass");
    assert(!env->ExceptionCheck());
    assert(obj!= nullptr);
    jclass result = env->GetObjectClass(obj);

    return result;
}

jfieldID JNI::GetFieldID(jclass clazz, const char* name, const char* sig) {
    TRACE("GetFieldID");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(name != nullptr && name[0] != '\0');
    assert(sig != nullptr && sig[0] != '\0');
    jfieldID result = env->GetFieldID(clazz, name, sig);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jfieldID JNI::GetStaticFieldID(jclass clazz, const char* name, const char* sig) {
    TRACE("GetStaticFieldID");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(name != nullptr && name[0] != '\0');
    assert(sig != nullptr && sig[0] != '\0');
    jfieldID result = env->GetStaticFieldID(clazz, name, sig);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jmethodID JNI::GetStaticMethodID(jclass clazz, const char* name, const char* sig) {
    TRACE("GetStaticMethodID");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(name != nullptr && name[0] != '\0');
    assert(sig != nullptr && sig[0] != '\0');
    jmethodID result = env->GetStaticMethodID(clazz, name, sig);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

const jchar* JNI::GetStringChars(jstring str) {
    TRACE("GetStringChars");
    assert(!env->ExceptionCheck());
    assert(str != nullptr);
    jboolean isCopy = false;

    return env->GetStringChars(str, &isCopy);
}

void JNI::ReleaseStringChars(jstring str, const jchar* chars) {
    TRACE("ReleaseStringChars");
    assert(!env->ExceptionCheck());
    assert(str != nullptr);
    assert(chars != nullptr);
    env->ReleaseStringChars(str, chars);
}

jsize JNI::GetStringLength(jstring str) {
    TRACE("GetStringLength");
    assert(!env->ExceptionCheck());
    assert(str != nullptr);
    return env->GetStringLength(str);
}

const char* JNI::GetStringUTFChars(jstring str) {
    TRACE("GetStringUTFChars");
    assert(!env->ExceptionCheck());
    assert(str != nullptr);
    jboolean isCopy = true;

    return env->GetStringUTFChars(str, &isCopy);
}

void JNI::ReleaseStringUTFChars(jstring str, const char* chars) {
    TRACE("ReleaseStringUTFChars");
    assert(!env->ExceptionCheck());
    assert(str != nullptr);
    assert(chars != nullptr);
    env->ReleaseStringUTFChars(str, chars);
}

jclass JNI::GetSuperclass(jclass sub) {
    TRACE("GetSuperclass");
    assert(!env->ExceptionCheck());
    assert(sub != nullptr);
    jclass result = env->GetSuperclass(sub);

    return result;
}

jboolean JNI::IsAssignableFrom(jclass sub, jclass sup) {
    TRACE("IsAssignableFrom");
    assert(!env->ExceptionCheck());
    assert(sub != nullptr);
    assert(sup != nullptr);

    return env->IsAssignableFrom(sub, sup);
}

jboolean JNI::IsInstanceOf(jobject obj, jclass clazz) {
    TRACE("IsInstanceOf");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);

    return env->IsInstanceOf(obj, clazz);
}

jboolean JNI::IsSameObject(jobject obj1, jobject obj2) {
    TRACE("IsSameObject");
    assert(!env->ExceptionCheck());

    return env->IsSameObject(obj1, obj2);
}

jbooleanArray JNI::NewBooleanArray(jsize len) {
    TRACE("NewBooleanArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jbooleanArray result = env->NewBooleanArray(len);

    return result;
}

jbyteArray JNI::NewByteArray(jsize len) {
    TRACE("NewByteArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jbyteArray result = env->NewByteArray(len);

    return result;
}

jcharArray JNI::NewCharArray(jsize len) {
    TRACE("NewCharArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jcharArray result = env->NewCharArray(len);

    return result;
}

jshortArray JNI::NewShortArray(jsize len) {
    TRACE("NewShortArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jshortArray result = env->NewShortArray(len);

    return result;
}

jintArray JNI::NewIntArray(jsize len) {
    TRACE("NewIntArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jintArray result = env->NewIntArray(len);

    return result;
}

jlongArray JNI::NewLongArray(jsize len) {
    TRACE("NewLongArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jlongArray result = env->NewLongArray(len);

    return result;
}

jfloatArray JNI::NewFloatArray(jsize len) {
    TRACE("NewFloatArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jfloatArray result = env->NewFloatArray(len);

    return result;
}

jdoubleArray JNI::NewDoubleArray(jsize len) {
    TRACE("NewDoubleArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    jdoubleArray result = env->NewDoubleArray(len);

    return result;
}

jobjectArray JNI::NewObjectArray(jsize len, jclass clazz, jobject init) {
    TRACE("NewObjectArray");
    assert(!env->ExceptionCheck());
    assert(0 <= len);
    assert(clazz != nullptr);
    jobjectArray result = env->NewObjectArray(len, clazz, init);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jobject JNI::NewObject(jclass clazz, jmethodID methodID, ...) {
    TRACE("NewObject");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    jobject result = env->NewObjectV(clazz, methodID, args);
    va_end(args);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jobject JNI::NewObjectA(jclass clazz, jmethodID methodID, const jvalue *args) {
    TRACE("NewObjectA");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    assert(args != nullptr);
    jobject result = env->NewObjectA(clazz, methodID, args);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jstring JNI::NewString(const jchar *unicode, jsize len) {
    TRACE("NewString");
    assert(!env->ExceptionCheck());
    assert(unicode != nullptr);
    assert(0 <= len);
    jstring result = env->NewString(unicode, len);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jstring JNI::NewStringUTF(const char *utf) {
    TRACE("NewStringUTF");
    assert(!env->ExceptionCheck());
    assert(utf != nullptr);
    jstring result = env->NewStringUTF(utf);

    if (handledException()) {
        return nullptr;
    }

    return result;
}

jobject JNI::NewLocalRef(jobject obj) {
    TRACE("NewLocalRef");
    assert(!env->ExceptionCheck());
    return env->NewLocalRef(obj);
}

jobject JNI::NewGlobalRef(jobject obj) {
    TRACE("NewGlobalRef");
    assert(!env->ExceptionCheck());
    return env->NewGlobalRef(obj);
}

jweak JNI::NewWeakGlobalRef(jobject obj) {
    TRACE("NewWeakGlobalRef");
    assert(!env->ExceptionCheck());
    return env->NewWeakGlobalRef(obj);
}

jobject JNI::PopLocalFrame(jobject result) {
    TRACE("PopLocalFrame");
    return env->PopLocalFrame(result);
}

jint JNI::PushLocalFrame(jint capacity) {
    TRACE("PushLocalFrame");
    assert(!env->ExceptionCheck());
    printAndClearException();
    return env->PushLocalFrame(capacity);
}

jint JNI::Throw(jthrowable obj) {
    TRACE("Throw");
    assert(!env->ExceptionCheck());
    assert(obj != nullptr);
    printAndClearException();
    return env->Throw(obj);
}

jint JNI::ThrowNew(jclass clazz, const char *msg) {
    TRACE("ThrowNew");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(msg != nullptr);
    printAndClearException();
    return env->ThrowNew(clazz, msg);
}


jint JNI::ThrowNew(jclass clazz, jmethodID methodID,...) {
    TRACE("ThrowNew");
    assert(!env->ExceptionCheck());
    assert(clazz != nullptr);
    assert(methodID != 0);
    va_list args;
    va_start(args, methodID);
    va_end(args);

    printAndClearException();
    jthrowable jexp = (jthrowable)env->NewObjectV(clazz, methodID, args);
    return jexp != nullptr? env->Throw(jexp) : -1; // exception while creating an exception!
}

jint JNI::GetJavaVM(JavaVM** vm) {
    TRACE("GetJavaVM");
    assert(vm != nullptr);
    return env->GetJavaVM(vm);
}

JNIForJS::JNIForJS(V8Scope& scope) : JNI(scope.env), scope(scope) {
    assert(!scope.context.IsEmpty());
}

bool JNIForJS::handledException() {
    TRACE("JNIForJS::handledException");

    if (!env->ExceptionCheck()) {
        return false;
    }

    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();

    if (exception) {
        jstring message = (jstring)env->CallObjectMethod(exception, throwableClassGetMessageMethodID, exception);

        if (env->ExceptionOccurred()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            message = nullptr;
        }

        if (!message) {
            message = (jstring)env->CallObjectMethod(exception, objectClassToStringMethodID);

            if (env->ExceptionOccurred()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                message = nullptr;
            }
        }

        // j2v_string does not raise further exceptions
        Local<String> jsMsg = message? j2v_string(scope, message) : scope.string("Unknown Java exception");
        Local<Value> jsExp = Exception::Error(jsMsg);
        assert(jsExp->IsObject());
        if (jsExp->IsObject()) {
            // expose the java exception as "javaException" property
            Local<Object> jsExpObj = jsExp.As<Object>();
            Local<Value> wrappedJavaExp = j2v_java_wrap(scope, exception);

            // clear possible Java exception from j2v_java_wrap!
            if (env->ExceptionOccurred()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            } else {
                // wrapping went well! Expose as "javaException" property
                Maybe<bool> ignored = jsExpObj->Set(scope.context, scope.string("javaException"), wrappedJavaExp);
            }
        }

        if (jsExp.IsEmpty()) {
            scope.isolate->ThrowException(jsMsg);
        } else {
            scope.isolate->ThrowException(jsExp);
        }
    }

    return true;
}

bool JNIForJS::checkException() {
    // we should have translated Java exception as V8 exception earlier!!
    assert(!env->ExceptionCheck());
    return scope.checkV8Exception();
}

JNIForJava::JNIForJava(V8Scope& scope) : JNI(scope.env), scope(scope) {
}

bool JNIForJava::handledException() {
    return env->ExceptionCheck();
}
