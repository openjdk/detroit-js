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

#ifndef __jvmv8_jni_support_hpp__
#define __jvmv8_jni_support_hpp__

class V8Scope;

extern thread_local int indent;

class JNI {
private:
    // When we could potentially overwrite an existing exception
    // atleast print stack trace and clear it so that we'll know!
    void printAndClearException() {
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

protected:
    JNIEnv* env;

    virtual bool handledException();

public:
    JNI(JNIEnv* env) : env(env) {
    }

    JNI(V8Scope& scope);

    virtual bool checkException();

    jboolean CallBooleanMethod(jobject obj, jmethodID methodID, ...);

    jbyte CallByteMethod(jobject obj, jmethodID methodID, ...);

    jchar CallCharMethod(jobject obj, jmethodID methodID, ...);

    jshort CallShortMethod(jobject obj, jmethodID methodID, ...);

    jdouble CallDoubleMethod(jobject obj, jmethodID methodID, ...);

    jfloat CallFloatMethod(jobject obj, jmethodID methodID, ...);

    jint CallIntMethod(jobject obj, jmethodID methodID, ...);

    jlong CallLongMethod(jobject obj, jmethodID methodID, ...);

    jobject CallObjectMethod(jobject obj, jmethodID methodID, ...);

    void CallVoidMethod(jobject obj, jmethodID methodID, ...);

    jboolean CallStaticBooleanMethod(jclass clazz, jmethodID methodID, ...);

    jbyte CallStaticByteMethod(jclass clazz, jmethodID methodID, ...);

    jchar CallStaticCharMethod(jclass clazz, jmethodID methodID, ...);

    jshort CallStaticShortMethod(jclass clazz, jmethodID methodID, ...);

    jdouble CallStaticDoubleMethod(jclass clazz, jmethodID methodID, ...);

    jfloat CallStaticFloatMethod(jclass clazz, jmethodID methodID, ...);

    jint CallStaticIntMethod(jclass clazz, jmethodID methodID, ...);

    jlong CallStaticLongMethod(jclass clazz, jmethodID methodID, ...);

    jobject CallStaticObjectMethod(jclass clazz, jmethodID methodID, ...);

    void CallStaticVoidMethod(jclass clazz, jmethodID methodID, ...);

    jboolean CallBooleanMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jbyte CallByteMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jchar CallCharMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jshort CallShortMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jdouble CallDoubleMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jfloat CallFloatMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jint CallIntMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jlong CallLongMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jobject CallObjectMethodA(jobject obj, jmethodID methodID, jvalue* args);

    void CallVoidMethodA(jobject obj, jmethodID methodID, jvalue* args);

    jboolean CallStaticBooleanMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jbyte CallStaticByteMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jchar CallStaticCharMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jshort CallStaticShortMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jdouble CallStaticDoubleMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jfloat CallStaticFloatMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jint CallStaticIntMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jlong CallStaticLongMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jobject CallStaticObjectMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    void CallStaticVoidMethodA(jclass clazz, jmethodID methodID, jvalue* args);

    jboolean CallNonvirtualBooleanMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jbyte CallNonvirtualByteMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jchar CallNonvirtualCharMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jshort CallNonvirtualShortMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jdouble CallNonvirtualDoubleMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jfloat CallNonvirtualFloatMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jint CallNonvirtualIntMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jlong CallNonvirtualLongMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jobject CallNonvirtualObjectMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    void CallNonvirtualVoidMethodA(jobject obj, jclass clazz, jmethodID methodID, jvalue* args);

    jobject GetObjectField(jobject obj, jfieldID fieldID);

    jboolean GetBooleanField(jobject obj, jfieldID fieldID);

    jbyte GetByteField(jobject obj, jfieldID fieldID);

    jchar GetCharField(jobject obj, jfieldID fieldID);

    jshort GetShortField(jobject obj, jfieldID fieldID);

    jint GetIntField(jobject obj, jfieldID fieldID);

    jlong GetLongField(jobject obj, jfieldID fieldID);

    jfloat GetFloatField(jobject obj, jfieldID fieldID);

    jdouble GetDoubleField(jobject obj, jfieldID fieldID);

    void SetObjectField(jobject obj, jfieldID fieldID, jobject value);

    void SetBooleanField(jobject obj, jfieldID fieldID, jboolean value);

    void SetByteField(jobject obj, jfieldID fieldID, jbyte value);

    void SetCharField(jobject obj, jfieldID fieldID, jchar value);

    void SetShortField(jobject obj, jfieldID fieldID, jshort value);

    void SetIntField(jobject obj, jfieldID fieldID, jint value);

    void SetLongField(jobject obj, jfieldID fieldID, jlong value);

    void SetFloatField(jobject obj, jfieldID fieldID, jfloat value);

    void SetDoubleField(jobject obj, jfieldID fieldID, jdouble value);

    void DeleteGlobalRef(jobject gref);

    void DeleteLocalRef(jobject obj);

    void DeleteWeakGlobalRef(jweak ref);

    jclass FindClass(const char* name);

    jobject GetStaticObjectField(jclass clazz, jfieldID fieldID);

    jboolean GetStaticBooleanField(jclass clazz, jfieldID fieldID);

    jbyte GetStaticByteField(jclass clazz, jfieldID fieldID);

    jchar GetStaticCharField(jclass clazz, jfieldID fieldID);

    jshort GetStaticShortField(jclass clazz, jfieldID fieldID);

    jint GetStaticIntField(jclass clazz, jfieldID fieldID);

    jlong GetStaticLongField(jclass clazz, jfieldID fieldID);

    jfloat GetStaticFloatField(jclass clazz, jfieldID fieldID);

    jdouble GetStaticDoubleField(jclass clazz, jfieldID fieldID);

    void SetStaticObjectField(jclass clazz, jfieldID fieldID, jobject value);

    void SetStaticBooleanField(jclass clazz, jfieldID fieldID, jboolean value);

    void SetStaticByteField(jclass clazz, jfieldID fieldID, jbyte value);

    void SetStaticCharField(jclass clazz, jfieldID fieldID, jchar value);

    void SetStaticShortField(jclass clazz, jfieldID fieldID, jshort value);

    void SetStaticIntField(jclass clazz, jfieldID fieldID, jint value);

    void SetStaticLongField(jclass clazz, jfieldID fieldID, jlong value);

    void SetStaticFloatField(jclass clazz, jfieldID fieldID, jfloat value);

    void SetStaticDoubleField(jclass clazz, jfieldID fieldID, jdouble value);

    jsize GetArrayLength(jarray array);

    jboolean* GetBooleanArrayElements(jbooleanArray array);

    jbyte* GetByteArrayElements(jbyteArray array);

    jchar* GetCharArrayElements(jcharArray array);

    jshort* GetShortArrayElements(jshortArray array);

    jint* GetIntArrayElements(jintArray array);

    jlong* GetLongArrayElements(jlongArray array);

    jfloat* GetFloatArrayElements(jfloatArray array);

    jdouble* GetDoubleArrayElements(jdoubleArray array);

    void ReleaseBooleanArrayElements(jbooleanArray array, jboolean* elems);

    void ReleaseByteArrayElements(jbyteArray array, jbyte* elems);

    void ReleaseCharArrayElements(jcharArray array, jchar* elems);

    void ReleaseShortArrayElements(jshortArray array,  jshort* elems);

    void ReleaseIntArrayElements(jintArray array, jint* elems);

    void ReleaseLongArrayElements(jlongArray array,  jlong* elems);

    void ReleaseFloatArrayElements(jfloatArray array, jfloat* elems);

    void ReleaseDoubleArrayElements(jdoubleArray array, jdouble* elems);

    jobject NewDirectByteBuffer(void* address, jlong capacity);

    void* GetDirectBufferAddress(jobject buf);

    jlong GetDirectBufferCapacity(jobject buf);

    jobject GetObjectArrayElement(jobjectArray array, jsize index);

    void SetObjectArrayElement(jobjectArray array, jsize index, jobject val);

    jmethodID GetMethodID(jclass clazz, const char* name, const char* sig);

    jclass GetObjectClass(jobject obj);

    jfieldID GetFieldID(jclass clazz, const char* name, const char* sig);

    jfieldID GetStaticFieldID(jclass clazz, const char* name, const char* sig);

    jmethodID GetStaticMethodID(jclass clazz, const char* name, const char* sig);

    const jchar *GetStringChars(jstring str);

    void ReleaseStringChars(jstring str, const jchar* chars);

    jsize GetStringLength(jstring str);

    const char* GetStringUTFChars(jstring str);

    void ReleaseStringUTFChars(jstring str, const char* chars);

    jclass GetSuperclass(jclass sub);

    jboolean IsAssignableFrom(jclass sub, jclass sup);

    jboolean IsInstanceOf(jobject obj, jclass clazz);

    jboolean IsSameObject(jobject obj1, jobject obj2);

    jbooleanArray NewBooleanArray(jsize len);

    jbyteArray NewByteArray(jsize len);

    jcharArray NewCharArray(jsize len);

    jshortArray NewShortArray(jsize len);

    jintArray NewIntArray(jsize len);

    jlongArray NewLongArray(jsize len);

    jfloatArray NewFloatArray(jsize len);

    jdoubleArray NewDoubleArray(jsize len);

    jobjectArray NewObjectArray(jsize len, jclass clazz, jobject init);

    jobject NewObject(jclass clazz, jmethodID methodID, ...);

    jobject NewObjectA(jclass clazz, jmethodID methodID, const jvalue *args);

    jstring NewString(const jchar *unicode, jsize len);

    jstring NewStringUTF(const char *utf);

    jobject NewLocalRef(jobject obj);

    jobject NewGlobalRef(jobject obj);

    jweak NewWeakGlobalRef(jobject obj);

    jobject PopLocalFrame(jobject result);

    jint PushLocalFrame(jint capacity);

    jint Throw(jthrowable obj);

    jint ThrowNew(jclass clazz, const char *msg);

    jint ThrowNew(jclass clazz, jmethodID methodID, ...);

    int GetJavaVM(JavaVM** vm);
};

class JNIForJS : public JNI {
private:
    V8Scope& scope;

protected:
    virtual bool handledException();

public:
    JNIForJS(V8Scope& scope);
    virtual bool checkException();
};

class JNIForJava : public JNI {
private:
    V8Scope& scope;

protected:
    virtual bool handledException();

public:
    JNIForJava(V8Scope& scope);
};

class JNILocalFrame {
private:
    JNI& jni;
    bool popped;
    const int LOCALS = 256;

public:
    JNILocalFrame(JNI& jni) : jni(jni), popped(false) {
        int result = jni.PushLocalFrame(LOCALS);
        assert(result == 0);
    }

    ~JNILocalFrame() {
        if (!popped) {
            jni.PopLocalFrame(nullptr);
            popped = true;
        }
    }

    jobject pop(jobject result) {
        if (!popped) {
            result = jni.PopLocalFrame(result);
            popped = true;
        }

        return result;
    }
};

#endif // __jvmv8_jni_support_hpp__
