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

package org.openjdk.engine.javascript.internal;

import org.openjdk.engine.javascript.V8Inspector;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.WeakHashMap;
import javax.script.ScriptContext;

public final class V8Isolate implements V8Inspector {
    private volatile long reference;
    // java supported in this isolate or not?
    private final boolean javaSupport;
    // lazily initialized
    private WeakHashMap<ByteBuffer, V8Object> arrayBufferCache;

    private ClassLoader classLoader;
    // Current ScriptContext instance.
    private ScriptContext context;

    // inspector support
    // inspector enabled?
    private final boolean inspector;
    // set only when inspector is true
    private V8Inspector.Listener inspectorListener;

    private V8Isolate(long ref, boolean javaSupport, boolean inspector) {
        this.reference = ref;
        this.javaSupport = javaSupport;
        this.inspector = inspector;
    }

    V8Object createGlobal(String name) {
        return V8.createGlobal(this, inspector? name : null);
    }

    ClassLoader getClassLoader() {
        return classLoader;
    }

    void setClassLoader(ClassLoader cl) {
        this.classLoader = cl;
    }

    boolean isJavaSupported() {
        return javaSupport;
    }

    boolean isInspectorEnabled() {
        return inspector;
    }

    public ScriptContext getScriptContext() {
        return context;
    }

    void setScriptContext(ScriptContext context) {
        this.context = context;
    }

    // get resource URL using the Isolate specific class loader (if not null)
    // or else returns system resource URL.
    URL getResource(String resName) {
        ClassLoader cl = getClassLoader();
        if (cl != null) {
            return cl.getResource(resName);
        }
        return ClassLoader.getSystemResource(resName);
    }

    boolean isDisposed() {
        return reference == 0L;
    }

    long getReference() {
        if (isDisposed()) {
            throw new IllegalStateException("V8 Isolate already closed");
        }
        return reference;
    }

    void cacheArrayBuffer(ByteBuffer byteBuf, V8Object arrayBuf) {
        if (arrayBufferCache == null) {
            arrayBufferCache = new WeakHashMap<>();
        }
        arrayBufferCache.put(byteBuf, arrayBuf);
    }

    static V8Isolate create(long ref, boolean javaSupport, boolean inspector) {
        return ref != 0L? new V8Isolate(ref, javaSupport, inspector) : null;
    }

    // This method is used as cleaner thunk for script engine
    synchronized void cleanerThunk() {
        try {
            long ref = reference;
            // Notify early!
            this.reference = 0L;
            if (ref != 0L) {
                V8.disposeIsolate(ref);
            }
        } catch (Throwable th) {
            // Any Throwable from cleaner thunk results from System.exit!
            // If DEBUG mode, print stack trace. Or else swallow it!
            if (V8.DEBUG) {
                th.printStackTrace();
            }
        }
    }

    // V8Inspector methods
    @Override
    public void setListener(V8Inspector.Listener listener) {
        checkInspector();
        this.inspectorListener = Objects.requireNonNull(listener);
    }

    @Override
    public void dispatchProtocolMessage(String msg) {
        checkInspector();
        V8.inspectorDispatchProtocolMessage(this, Objects.requireNonNull(msg));
    }

    V8Inspector.Listener getListener() {
        checkInspector();
        return this.inspectorListener;
    }

    private void checkInspector() {
        if (!inspector) {
            throw new IllegalStateException("V8Inspector not enabled");
        }
    }
}
