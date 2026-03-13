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

import org.openjdk.engine.javascript.JSArray;
import org.openjdk.engine.javascript.JSFactory;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSPromise;
import org.openjdk.engine.javascript.JSProxy;
import org.openjdk.engine.javascript.JSResolver;
import org.openjdk.engine.javascript.JSSymbol;
import org.openjdk.engine.javascript.V8Context;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.script.Bindings;

public final class V8Bindings implements Bindings, JSFactory, V8Context {
    private static final AtomicInteger GLOBAL_ID = new AtomicInteger();
    private final V8Object v8Global;

    private V8Bindings(V8Object v8Global) {
        this.v8Global = v8Global;
    }

    private static V8Bindings of(V8Isolate isolate, V8Object v8Global) {
        if (isolate.isJavaSupported()) {
            v8Global.setMember("context", V8ScriptContextWrapper.instance);
        }
        return new V8Bindings(v8Global);
    }

    static V8Bindings of(V8Isolate isolate, String name) {
        return V8Bindings.of(isolate, isolate.createGlobal(name));
    }

    static V8Bindings of(V8Isolate isolate) {
        return V8Bindings.of(isolate, defaultName(isolate));
    }

    @Override
    public Object put(String name, Object value) {
        checkKey(name);
        return v8Global.put(name, value);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> toMerge) {
        checkKeys(toMerge.keySet());
        v8Global.putAll(toMerge);
    }

    @Override
    public boolean containsKey(Object key) {
        checkKey(key);
        return v8Global.containsKey(key);
    }

    @Override
    public Object get(Object key) {
        checkKey(key);
        return v8Global.get(key);
    }

    @Override
    public Object remove(Object key) {
        checkKey(key);
        return v8Global.remove(key);
    }

    @Override
    public int size() {
        return v8Global.size();
    }

    @Override
    public boolean isEmpty() {
        return v8Global.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        return v8Global.containsValue(value);
    }

    @Override
    public void clear() {
        v8Global.clear();
    }

    @Override
    public Set<String> keySet() {
        Set<Object> objectkeySet = v8Global.keySet();
        Set<String> stringKeySet = new HashSet<>();

        objectkeySet.stream().filter((key) -> (key instanceof String)).forEach((key) -> {
            stringKeySet.add((String)key);
        });

        return stringKeySet;
    }

    @Override
    public Collection<Object> values() {
        return v8Global.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<Object, Object>> objectEntrySet = v8Global.entrySet();
        Set<Entry<String, Object>> stringEntrySet = new HashSet<>();

        objectEntrySet.stream().forEach((entry) -> {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key instanceof String) {
                stringEntrySet.add(new AbstractMap.SimpleImmutableEntry<>((String)key, value));
            }
        });

        return stringEntrySet;
    }

    // JSFactory methods

    @Override
    public JSObject newObject() {
        return V8.newObject(v8Global);
    }

    @Override
    public JSArray newArray(int length) {
        return V8.newArray(v8Global, length);
    }

    @Override
    public JSObject newArrayBuffer(int length) {
        return V8.newArrayBuffer(v8Global, length);
    }

    @Override
    public JSObject newArrayBuffer(ByteBuffer buf) {
        return V8.newArrayBuffer(v8Global, Objects.requireNonNull(buf));
    }

    @Override
    public ByteBuffer newByteBuffer(JSObject arrayBuf) {
        Objects.requireNonNull(arrayBuf);
        if (arrayBuf instanceof V8Object) {
            return V8.newByteBuffer((V8Object)arrayBuf);
        }
        throw new IllegalArgumentException("expected an ArrayBuffer object");
    }

    @Override
    public JSObject newDate(double time) {
        return V8.newDate(v8Global, time);
    }

    @Override
    public JSProxy newProxy(JSObject target, JSObject handler) {
        return V8.newProxy(v8Global, Objects.requireNonNull(target), Objects.requireNonNull(handler));
    }

    @Override
    public JSObject newRegExp(String pattern, EnumSet<RegExpFlag> flags) {
        return V8.newRegExp(v8Global, Objects.requireNonNull(pattern), regExpFlags(flags));
    }

    @Override
    public JSResolver newResolver() {
        return V8.newResolver(v8Global);
    }

    @Override
    public JSPromise newResolvedPromise(Object value) {
        V8Resolver resolver = V8.newResolver(v8Global);
        return resolver.resolve(value)? resolver.getPromise() : null;
    }

    @Override
    public JSPromise newRejectedPromise(Object value) {
        V8Resolver resolver = V8.newResolver(v8Global);
        return resolver.reject(value)? resolver.getPromise() : null;
    }

    @Override
    public <T> JSPromise newPromise(Supplier<T> supplier) {
        JSResolver resolver = newResolver();
        Objects.requireNonNull(supplier);
        CompletableFuture.supplyAsync(supplier).whenComplete((value, throwable) -> {
            if (value != null) {
                resolver.resolve(value);
            } else if (throwable != null) {
                resolver.reject(throwable);
            } else {
                throw new AssertionError("should not reach here");
            }
        });
        return resolver.getPromise();
    }

    @Override
    public Object parseJSON(String jsonString) {
        return V8.parseJSON(v8Global, Objects.requireNonNull(jsonString));
    }

    @Override
    public String toJSON(JSObject jsObj, String gap) {
        return V8.toJSON(v8Global, Objects.requireNonNull(jsObj), gap);
    }

    @Override
    public JSSymbol newSymbol(String key) {
        return V8.newSymbol(v8Global.getIsolate(), Objects.requireNonNull(key));
    }

    @Override
    public JSSymbol symbolFor(String key) {
        return V8.symbolFor(v8Global.getIsolate(), Objects.requireNonNull(key));
    }

    @Override
    public JSSymbol getIteratorSymbol() {
        return V8.getIteratorSymbol(v8Global.getIsolate());
    }

    @Override
    public JSSymbol getUnscopablesSymbol() {
        return V8.getUnscopablesSymbol(v8Global.getIsolate());
    }

    @Override
    public JSSymbol getToStringTagSymbol() {
        return V8.getToStringTagSymbol(v8Global.getIsolate());
    }

    @Override
    public JSSymbol getIsConcatSpreadableSymbol() {
        return V8.getIsConcatSpreadableSymbol(v8Global.getIsolate());
    }

    @Override
    public JSObject newError(String message) {
        return V8.newError(v8Global, Objects.requireNonNull(message));
    }

    @Override
    public JSObject newRangeError(String message) {
        return V8.newRangeError(v8Global, Objects.requireNonNull(message));
    }

    @Override
    public JSObject newReferenceError(String message) {
        return V8.newReferenceError(v8Global, Objects.requireNonNull(message));
    }

    @Override
    public JSObject newSyntaxError(String message) {
        return V8.newSyntaxError(v8Global, Objects.requireNonNull(message));
    }

    @Override
    public JSObject newTypeError(String message) {
        return V8.newTypeError(v8Global, Objects.requireNonNull(message));
    }

    @Override
    public void allowCodeGenerationFromStrings(boolean allow) {
        V8.allowCodeGenerationFromStrings(v8Global, allow);
    }

    @Override
    public boolean isCodeGenerationFromStringsAllowed() {
        return V8.isCodeGenerationFromStringsAllowed(v8Global);
    }

    @Override
    public JSObject getGlobal() {
        return v8Global;
    }

    // package-private helpers
    V8Object getV8Global() {
        return v8Global;
    }

    // Internals below this point
    private static int regExpFlags(EnumSet<RegExpFlag> flags) {
        Objects.requireNonNull(flags);
        int res = 0;
        // should match enum values of v8::RegExp::Flags
        for (RegExpFlag f : flags) {
            switch (f) {
                case None:       res |= 0;  break;
                case Global:     res |= 1;  break;
                case IgnoreCase: res |= 2;  break;
                case Multiline:  res |= 4;  break;
                case Sticky:     res |= 8;  break;
                case Unicode:    res |= 16; break;
            }
        }
        return res;
    }

    private void checkKey(Object key) {
        Objects.requireNonNull(key);
        if (! (key instanceof String)) {
            throw new ClassCastException("String key expected");
        }
        if ("".equals(key)) {
            throw new IllegalArgumentException("empty String as key");
        }
    }

    private void checkKey(String key) {
        Objects.requireNonNull(key);
        if ("".equals(key)) {
            throw new IllegalArgumentException("empty String as key");
        }
    }

    private void checkKeys(Set<? extends String> keys) {
        for (String key : keys) {
            checkKey(key);
        }
    }

    private static String defaultName(V8Isolate isolate) {
        return isolate.isInspectorEnabled()?
            "bindings: " + Integer.toHexString(GLOBAL_ID.getAndIncrement()) : null;
    }
}
