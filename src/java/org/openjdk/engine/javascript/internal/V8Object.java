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

import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSSymbol;
import org.openjdk.engine.javascript.V8Undefined;

import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.script.ScriptException;

public class V8Object extends V8Value implements Map<Object, Object>, JSObject {
    private final V8Isolate isolate;

    private V8Object(V8Isolate isolate, long reference) {
        super(isolate, reference);
        this.isolate = isolate;
    }

    V8Object(long reference) {
        this(V8.getCurrentIsolate(), reference);
    }

    // called from native
    static V8Object create(long objectRef) {
        if (V8.DEBUG) debug("Object", objectRef);
        return objectRef != 0L? new V8Object(objectRef) : null;
    }

    // called from native
    // if 'this' V8 Object belongs to the given isolate, then return the reference or else return 0L.
    // This method is called from the native code to make sure that we do *not* pass script object from
    // a V8 Isolate I1 to another V8 Isolate I2 "as is".
    final long checkAndGetReference(long isolateRef) {
        return isolate.getReference() == isolateRef? getReference() : 0L;
    }

    V8Isolate getIsolate() {
        return isolate;
    }

    // java.util.Map methods
    @Override
    public Object put(Object key, Object value) {
        return V8.put(this, key, value);
    }

    @Override
    public void putAll(Map<? extends Object, ? extends Object> toMerge) {
        toMerge.entrySet().stream().forEach((entry) -> {
            put(entry.getKey(), entry.getValue());
        });
    }

    @Override
    public boolean containsKey(Object key) {
        return V8.contains(this, key);
    }

    @Override
    public Object get(Object key) {
        return translateUndefined(V8.get(this, key));
    }

    @Override
    public Object remove(Object key) {
        return translateUndefined(V8.remove(this, key));
    }

    @Override
    public int size() {
        return V8.keys(this).length;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsValue(Object value) {
        Object[] keys = V8.keys(this);

        if (value == null) {
            for (Object key : keys) {
                if (get(key) == null) {
                    return true;
                }
            }
        } else {
            for (Object key : keys) {
                if (value.equals(get(key))) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void clear() {
        for (Object key : V8.keys(this)) {
            remove(key);
        }
    }

    @Override
    public Set<Object> keySet() {
        return new HashSet<Object>(Arrays.asList(V8.keys(this)));
    }

    @Override
    public Collection<Object> values() {
        Object[] keys = V8.keys(this);
        Collection<Object> values = new ArrayList<>();

        for (Object key : keys) {
            values.add(get(key));
        }

        return values;
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        Object[] keys = V8.keys(this);
        Set<Entry<Object, Object>> set = new HashSet<>();

        for (Object key : keys) {
            set.add(new SimpleImmutableEntry<>(key, get(key)));
        }

        return set;
    }

    // JSObject methods
    @Override
    public Object call(final Object thiz, final Object... args) throws ScriptException {
        return V8.invoke(this, thiz, args);
    }

    @Override
    public Object callMember(final String name, final Object... args) throws NoSuchMethodException, ScriptException {
        return V8.invokeMethod(this, name, args);
    }

    @Override
    public Object newObject(final Object... args) throws ScriptException {
        return V8.newObject(this, args);
    }

    @Override
    public Object getMember(String key) {
        return V8.get(this, Objects.requireNonNull(key));
    }

    @Override
    public Object getMember(JSSymbol key) {
        V8Symbol sym = checkSymbol(key);
        if (sym.isFromIsolate(isolate)) {
            return V8.get(this, sym);
        } else {
            return V8Undefined.INSTANCE;
        }
    }

    @Override
    public Object getSlot(int key) {
        return V8.get(this, key);
    }

    @Override
    public boolean hasMember(String key) {
        return V8.contains(this, Objects.requireNonNull(key));
    }

    @Override
    public boolean hasMember(JSSymbol key) {
        V8Symbol sym = checkSymbol(key);
        return sym.isFromIsolate(isolate)? V8.contains(this, sym) : false;
    }

    @Override
    public boolean hasSlot(int key) {
        return V8.contains(this, key);
    }

    @Override
    public boolean removeMember(String key) {
        return V8.delete(this, Objects.requireNonNull(key));
    }

    @Override
    public boolean removeMember(JSSymbol key) {
        V8Symbol sym = checkSymbol(key);
        return sym.isFromIsolate(isolate)? V8.delete(this, sym) : false;
    }

    @Override
    public boolean removeSlot(int key) {
        return V8.delete(this, key);
    }

    @Override
    public boolean setMember(String name, Object value) {
        return V8.set(this, Objects.requireNonNull(name), value);
    }

    @Override
    public boolean setMember(JSSymbol name, Object value) {
        V8Symbol sym = checkSymbol(name);
        if (sym.isFromIsolate(isolate)) {
            return V8.set(this, sym, value);
        } else {
            return false;
        }
    }

    @Override
    public boolean setSlot(int index, Object value) {
        return V8.set(this, index, value);
    }

    @Override
    public boolean defineOwnProperty(String name, Object value, EnumSet<PropertyAttribute> attrs) {
        return V8.defineOwnProperty(this, Objects.requireNonNull(name), value, attrs);
    }

    @Override
    public boolean defineOwnProperty(JSSymbol name, Object value, EnumSet<PropertyAttribute> attrs) {
        V8Symbol sym = checkSymbol(name);
        if (sym.isFromIsolate(isolate)) {
            return V8.defineOwnProperty(this, sym, value, attrs);
        } else {
            return false;
        }
    }

    @Override
    public boolean setAccessorProperty(String name, JSFunction getter, JSFunction setter,
            EnumSet<PropertyAttribute> attrs) {
        return V8.setAccessorProperty(this, Objects.requireNonNull(name),
                Objects.requireNonNull(getter), setter, attrs);
    }

    @Override
    public boolean setAccessorProperty(JSSymbol name, JSFunction getter, JSFunction setter,
            EnumSet<PropertyAttribute> attrs) {
        V8Symbol sym = checkSymbol(name);
        if (sym.isFromIsolate(isolate)) {
            return V8.setAccessorProperty(this, sym, Objects.requireNonNull(getter), setter, attrs);
        } else {
            return false;
        }
    }

    @Override
    public EnumSet<PropertyAttribute> getMemberAttributes(String name) {
        return V8.propertyAttributes(this, Objects.requireNonNull(name));
    }

    @Override
    public EnumSet<PropertyAttribute> getMemberAttributes(JSSymbol name) {
        V8Symbol sym = checkSymbol(name);
        if (sym.isFromIsolate(isolate)) {
            return V8.propertyAttributes(this, sym);
        } else {
            return EnumSet.noneOf(PropertyAttribute.class);
        }
    }

    @Override
    public EnumSet<PropertyAttribute> getSlotAttributes(int index) {
        return V8.propertyAttributes(this, index);
    }

    @Override
    public String[] getNamedProperties() {
        return V8.namedKeys(this);
    }

    @Override
    public JSSymbol[] getSymbolProperties() {
        return V8.symbolKeys(this);
    }

    @Override
    public int[] getIndexedProperties() {
        return V8.indexedKeys(this);
    }

    @Override
    public String getClassName() {
        return V8.getConstructorName(this);
    }

    @Override
    public boolean isCallable() {
        return V8.isCallable(this);
    }

    @Override
    public String toJSON(String gap) {
        return V8.toJSON(this, gap);
    }

    @Override
    public <T> T getInterface(Class<T> iface) {
        return (new V8InterfaceImplementor() {
            @Override
            protected Object invoke(V8Object self, Method method, Object[] args)
                    throws Exception {
                return V8.invokeMethod(self, method.getName(), args);
            }
        }).getInterface(this, iface);
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof V8Object) {
            return V8.strictEquals(this, ((V8Object)other));
        }

        return false;
    }

    @Override
    public final String toString() {
        return V8.toString(this);
    }

    final boolean isFromIsolate(V8Isolate otherIsolate) {
        return isolate.equals(otherIsolate);
    }

    private static V8Symbol checkSymbol(JSSymbol sym) {
        Objects.requireNonNull(sym);
        if (! (sym instanceof V8Symbol)) {
            throw new IllegalArgumentException("Expected V8 Symbol");
        }
        return (V8Symbol)sym;
    }

    private static Object translateUndefined(Object obj) {
        return obj == V8Undefined.INSTANCE ? null : obj;
    }
}
