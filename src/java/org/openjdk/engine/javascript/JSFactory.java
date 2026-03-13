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

package org.openjdk.engine.javascript;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * A factory to create various JavaScript objects from java.
 *
 * Unless otherwise noted, passing a {@code null} argument to any method
 * in this class will cause a {@link java.lang.NullPointerException NullPointerException}
 * to be thrown.
 */
public interface JSFactory {
    /**
     * Create a new empty script object.
     *
     * @return new empty script object.
     */
    public JSObject newObject();

    /**
     * Create a new script array object.
     *
     * @param length of the newly created array
     * @return new script array object.
     */
    public JSArray newArray(int length);

    /**
     * Create a new script ArrayBuffer object.
     *
     * @param length of the newly created ArrayBuffer
     * @return new script ArrayBuffer object.
     */
    public JSObject newArrayBuffer(int length);

    /*
     * Create a ArrayBuffer script object whose storage is backed by the given nio ByteBuffer.
     *
     * @param buf Nio direct ByteBuffer that is wrapped as an ArrayBuffer object.
     * @return ArrayBuffer script object whose data is backed by the given nio ByteBuffer object.
     * @throws IllegalArgumentException if the buffer is not a direct buffer.
     */
    public JSObject newArrayBuffer(ByteBuffer buf);

    /**
     * Create a new nio ByteBuffer whose storage is backed by an ArrayBuffer object.
     *
     * @param arrayBuf An ArrayBuffer object that is wrapped as nio ByteBuffer object.
     * @return nio direct ByteBuffer whose byte buffer is backed by this script object.
     * @throws IllegalArgumentException if the arrayBuf is not an ArrayBuffer object.
     */
    public ByteBuffer newByteBuffer(JSObject arrayBuf);

    /**
     * Create a new script Date object.
     *
     * @param time time to be used to create new Date.
     * @return new script Date object.
     */
    public JSObject newDate(double time);

    /**
     * Create a new script Proxy object.
     *
     * @param target the target object for the proxy object.
     * @param handler the handler callback for the proxy.
     * @return new script Proxy object.
     */
    public JSProxy newProxy(JSObject target, JSObject handler);

    // should match enum constants of v8::RegExp::Flags
    /**
     * A flag passed when creating a new script RegExp object.
     */
    public static enum RegExpFlag {
        None, Global, IgnoreCase, Multiline, Sticky, Unicode
    }

    /**
     * Create a new script RegExp object.
     *
     * @param pattern pattern matched by the RegExp object
     * @param flags A set of RegExp customization flags
     * @return new script RegExp object.
     */
    public JSObject newRegExp(String pattern, EnumSet<RegExpFlag> flags);

    /**
     * Create a new script JSResolver (V8 Promise::Resolver) object.
     *
     * @return new script Resolver object.
     */
    public JSResolver newResolver();

    /**
     * Create a new script Promise that is resolved with the given value.
     * This is equivalent to <code>Promise.resolve(value)</code> in javascript code.
     *
     * @param value the resolved value of the promise.
     * @return new script Promise object or null if promise cannot be created.
     */
    public JSPromise newResolvedPromise(Object value);

    /**
     * Create a new script Promise that is rejected with the given reason.
     * This is equivalent to <code>Promise.reject(reason)</code> in javascript code.
     *
     * @param value the reason for the promise rejection.
     * @return new script Promise object or null if promise cannot be created.
     */
    public JSPromise newRejectedPromise(Object value);

    /**
     * Create a new script Promise that executes the given supplier with a CompletableFuture.
     * The Promise is resolved or rejected based on completion value of the CompletableFuture.
     *
     * @param <T> the type of the argument to the supplier
     * @param supplier A function that fetches the value to be used for resolving the Promise.
     * @return new Promise object.
     */
    public <T> JSPromise newPromise(Supplier<T> supplier);

    /**
     * Parses the JSON String and returns the parsed object as result.
     *
     * @param jsonString JSON string input.
     * @return parsed Object representation of the given JSON String.
     */
    public Object parseJSON(String jsonString);

    /**
     * JSON stringify the given script object.
     *
     * @param jsObj script object to JSON stringify.
     * @param gap gap String to be used in JSON generated String (can be null)
     * @return JSON String for the given script object.
     */
    public String toJSON(JSObject jsObj, String gap);

    /**
     * JSON stringify the given script object.
     *
     * @param jsObj script object to JSON stringify.
     * @return JSON String for the given script object.
     */
    public default String toJSON(JSObject jsObj) {
        return toJSON(jsObj, null);
    }

    /**
     * Create a new (private) Symbol initialized with the supplied key.
     *
     * @param key name of the symbol
     * @return the new JS Symbol
     */
    public JSSymbol newSymbol(String key);

    /**
     * Searches for existing symbols with the given key and returns it if found.
     * Otherwise a new symbol gets created in the global symbol registry with this key.
     *
     * @param key name of the symbol
     * @return the new JS Symbol or an existing Symbol with the given key
     */
    public JSSymbol symbolFor(String key);

    /**
     * Access well-known Symbol.iterator symbol.
     *
     * @return JSSymbol object
     */
    public JSSymbol getIteratorSymbol();

    /**
     * Access well-known Symbol.iterator symbol.
     * @return JSSymbol object
     */
    public JSSymbol getUnscopablesSymbol();

    /**
     * Access well-known Symbol.toStringTag symbol.
     * @return JSSymbol object
     */
    public JSSymbol getToStringTagSymbol();

    /**
     * Access well-known Symbol.isConcatSpreadable symbol.
     * @return JSSymbol object
     */
    public JSSymbol getIsConcatSpreadableSymbol();

    /**
     * Create a new JS Error object with the given error message.
     *
     * @param message error message
     * @return New JS Error object.
     */
    public JSObject newError(String message);

    /**
     * Create a new JS RangeError object with the given error message.
     *
     * @param message error message
     * @return New JS RangeError object.
     */
    public JSObject newRangeError(String message);

    /**
     * Create a new JS ReferenceError object with the given error message.
     *
     * @param message error message
     * @return New JS ReferenceError object.
     */
    public JSObject newReferenceError(String message);

    /**
     * Create a new JS SyntaxError object with the given error message.
     *
     * @param message error message
     * @return New JS SyntaxError object.
     */
    public JSObject newSyntaxError(String message);

    /**
     * Create a new JS TypeError object with the given error message.
     *
     * @param message error message
     * @return New JS TypeError object.
     */
    public JSObject newTypeError(String message);
}
