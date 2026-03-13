/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.engine.javascript;

import java.util.EnumSet;
import java.util.Objects;

import javax.script.ScriptException;

/**
 * JSObject is the public interface to V8 script objects. This same interface
 * is also used to support pluggable script-friendly objects implemented in Java.
 */
public interface JSObject extends JSValue {
    /**
     * Attribute of a JSObject named member or a slot.
     */
    public enum PropertyAttribute {
        /** None **/
        None(),

        /** ReadOnly, i.e., not writable. **/
        ReadOnly(),

        /** DontEnum, i.e., not enumerable. **/
        DontEnum(),

        /** DontDelete, i.e., not configurable. **/
        DontDelete()
    }

    /**
     * Call this object as a JavaScript function. This is equivalent to
     * 'func.apply(thiz, args)' in JavaScript.
     *
     * @param thiz 'this' object to be passed to the function. This may be null.
     * @param args arguments to method
     * @throws ScriptException if there is any script error was thrown from called script function
     * @return result of call
     */
    public default Object call(final Object thiz, final Object... args) throws ScriptException {
        throw new UnsupportedOperationException("call");
    }

    /**
     * Call this 'constructor' JavaScript function to create a new object.
     * This is equivalent to 'new func(arg1, arg2...)' in JavaScript.
     *
     * @param args arguments to method
     * @throws ScriptException if there is any script error was thrown from called script function
     * @return result of constructor call
     */
    public default Object newObject(final Object... args) throws ScriptException {
        throw new UnsupportedOperationException("newObject");
    }

    /**
     * Call a member function
     * @param functionName function name
     * @param args         arguments
     * @return the return value of the function
     * @throws ScriptException if there is any script error was thrown from called script function
     * @throws NoSuchMethodException if there is no script function of matching name
     */
    public default Object callMember(final String functionName, final Object... args)
            throws NoSuchMethodException, ScriptException {
        Object value = getMember(functionName);
        if (value instanceof JSFunction) {
            return ((JSFunction)value).call(this, args);
        } else if (value instanceof JSObject && ((JSObject)value).isCallable()) {
            return ((JSObject)value).call(this, args);
        }
        throw new NoSuchMethodException(functionName);
    }

    /**
     * Retrieves a named member of this JavaScript object.
     *
     * @param name of member
     * @return member
     * @throws NullPointerException if name is null
     */
    public default Object getMember(final String name) {
        Objects.requireNonNull(name);
        return V8Undefined.INSTANCE;
    }

    /**
     * Retrieves a named member of this JavaScript object.
     *
     * @param name of member
     * @return member
     * @throws NullPointerException if name is null
     */
    public default Object getMember(final JSSymbol name) {
        Objects.requireNonNull(name);
        return V8Undefined.INSTANCE;
    }

    /**
     * Retrieves an indexed member of this JavaScript object.
     *
     * @param index index slot to retrieve
     * @return member
     */
    public default Object getSlot(final int index) {
        return V8Undefined.INSTANCE;
    }

    /**
     * Does this object have a named member?
     *
     * @param name name of member
     * @return true if this object has a member of the given name
     */
    public default boolean hasMember(final String name) {
        return false;
    }

    /**
     * Does this object have a named member?
     *
     * @param name name of member
     * @return true if this object has a member of the given name
     */
    public default boolean hasMember(final JSSymbol name) {
        return false;
    }

    /**
     * Does this object have a indexed property?
     *
     * @param slot index to check
     * @return true if this object has a slot
     */
    public default boolean hasSlot(final int slot) {
        return false;
    }

    /**
     * Remove a named member from this JavaScript object
     *
     * @param name name of the member
     * @return true if removal was successful, false otherwise.
     * @throws NullPointerException if name is null
     */
    public default boolean removeMember(final String name) {
        // ignore!
        Objects.requireNonNull(name);
        return false;
    }

    /**
     * Remove a named member from this JavaScript object
     *
     * @param name name of the member
     * @return true if removal was successful, false otherwise.
     * @throws NullPointerException if name is null
     */
    public default boolean removeMember(final JSSymbol name) {
        // ignore!
        Objects.requireNonNull(name);
        return false;
    }

    /**
     * Remove an indexed member from this JavaScript object
     *
     * @param index index of the member
     * @return true if removal was successful, false otherwise.
     */
    public default boolean removeSlot(final int index) {
        // ignore
        return false;
    }

    /**
     * Set a named member in this JavaScript object
     *
     * @param name  name of the member
     * @param value value of the member
     * @return true if the set was successful, false otherwise
     * @throws NullPointerException if name is null
     */
    public default boolean setMember(final String name, final Object value) {
        // ignore
        Objects.requireNonNull(name);
        return false;
    }

    /**
     * Set a named member in this JavaScript object
     *
     * @param name  name of the member
     * @param value value of the member
     * @return true if the set was successful, false otherwise
     * @throws NullPointerException if name is null
     */
    public default boolean setMember(final JSSymbol name, final Object value) {
        // ignore
        Objects.requireNonNull(name);
        return false;
    }

    /**
     * Set an indexed member in this JavaScript object
     *
     * @param index index of the member slot
     * @param value value of the member
     * @return true if the set was successful, false otherwise
     */
    public default boolean setSlot(final int index, final Object value) {
        // ignore
        return false;
    }

    /**
     * Define a own property with the given name, value and property attributes.
     *
     * @param name  name of the member
     * @param value value of the member
     * @param attrs property attributes, this can be null
     * @return true if the define was successful, false otherwise
     * @throws NullPointerException if name is null
     */
    public default boolean defineOwnProperty(String name, Object value, EnumSet<PropertyAttribute> attrs) {
        // ignore
        return false;
    }

    /**
     * Define a own property with the given name, value and property attributes.
     *
     * @param name  name of the member
     * @param value value of the member
     * @param attrs property attributes, this can be null
     * @return true if the define was successful, false otherwise
     * @throws NullPointerException if name is null
     */
    public default boolean defineOwnProperty(JSSymbol name, Object value, EnumSet<PropertyAttribute> attrs) {
        // ignore
        return false;
    }

    /**
     * Define an accessor property with the given name, getter, setter and property attributes.
     *
     * @param name  name of the member
     * @param getter getter function for the property
     * @param setter setter function for the property - this may be null
     * @param attrs property attributes, this can be null
     * @return true if the define was successful, false otherwise
     * @throws NullPointerException if name is null
     */
    public default boolean setAccessorProperty(String name, JSFunction getter, JSFunction setter,
            EnumSet<PropertyAttribute> attrs) {
        // ignore
        return false;
    }

    /**
     * Define an accessor property with the given name, getter, setter and property attributes.
     *
     * @param name  name of the member
     * @param getter getter function for the property
     * @param setter setter function for the property - this may be null
     * @param attrs property attributes, this can be null
     * @return true if the define was successful, false otherwise
     * @throws NullPointerException if name is null
     */
    public default boolean setAccessorProperty(JSSymbol name, JSFunction getter, JSFunction setter,
            EnumSet<PropertyAttribute> attrs) {
        // ignore
        return false;
    }

    // property attribute support
    /**
     * Return the set of PropertyAttributes of the given property name.
     *
     * @param name name of the property
     * @return PropertyAttribute enum set
     */
    public default EnumSet<PropertyAttribute> getMemberAttributes(String name) {
        return EnumSet.of(PropertyAttribute.None);
    }

    /**
     * Return the set of PropertyAttributes of the given property name.
     *
     * @param name name of the property
     * @return PropertyAttribute enum set
     */
    public default EnumSet<PropertyAttribute> getMemberAttributes(JSSymbol name) {
        return EnumSet.of(PropertyAttribute.None);
    }

    /**
     * Return the set of PropertyAttributes of the given index slot.

    /**
     * Return the set of PropertyAttributes of the given index slot.
     *
     * @param index index of the property
     * @return PropertyAttribute enum set
     */
    public default EnumSet<PropertyAttribute> getSlotAttributes(int index) {
        return EnumSet.of(PropertyAttribute.None);
    }

    // property iteration support

    /**
     * Return a String array for supported named properties of this object.
     *
     * @return named properties array
     */
    public default String[] getNamedProperties() {
        return new String[0];
    }

    /**
     * Return a JSSymbol array for supported symbol properties of this object.
     *
     * @return named properties array
     */
    public default JSSymbol[] getSymbolProperties() {
        return new JSSymbol[0];
    }

    /**
     * Return an int array for supported indexed properties of this object.
     *

    /**
     * Return an int array for supported indexed properties of this object.
     *
     * @return indexed properties array
     */
    public default int[] getIndexedProperties() {
        return new int[0];
    }

    /**
     * ECMA [[Class]] property
     *
     * @return ECMA [[Class]] property value of this object
     */
    public default String getClassName() {
        return getClass().getName();
    }

    /**
     * Is this a callable object?
     *
     * @return if true if this is a callable object
     */
    public default boolean isCallable() {
        return false;
    }

    /**
     * Convert this JSObject to JSON String representation.
     *
     * @param gap gap String to be used in generated JSON String.
     * @return JSON representation of this object.
     */
    public default String toJSON(String gap) {
        throw new UnsupportedOperationException("toJSON");
    }

    /**
     * Convert this JSObject to JSON String representation.
     *
     * @return JSON representation of this object.
     */
    public default String toJSON() {
        return toJSON(null);
    }

    /**
     * Returns an implementation of an interface using member functions of this scripting object.
     *
     * @param <T> the type of the interface to return
     * @param iface The Class object of the interface to return.
     * @return An instance of requested interface - null if the requested interface is unavailable.
     */
    public default <T> T getInterface(Class<T> iface) {
        throw new UnsupportedOperationException("getInterface");
    }

    // type queries

    /**
     * Is this a JS Array?
     *
     * @return true if this is an array, else false.
     */
    public default boolean isArray() {
        return this instanceof JSArray;
    }

    /**
     * Is this a JS Function?
     *
     * @return true if this is a function, else false.
     */
    public default boolean isFunction() {
        return this instanceof JSFunction;
    }

    /**
     * Is this a strict JS Function?
     *
     * @return true if this is a strict function, else false.
     */
    public default boolean isStrictFunction() {
        // FIXME: can we do the strict check better?
        return isFunction() && toString().contains("'use strict';");
    }

    /**
     * Is this a JS Promise?
     *
     * @return true if this is a promise, else false.
     */
    public default boolean isPromise() {
        return this instanceof JSPromise;
    }

    /**
     * Is this a JS Proxy?
     *
     * @return true if this is a proxy, else false.
     */
    public default boolean isProxy() {
        return this instanceof JSProxy;
    }
}
