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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.script.ScriptException;

/**
 * JSFunction is the public interface to V8 functions. This same interface
 * is also used to support pluggable script functions implemented in Java.
 */
public interface JSFunction extends JSObject {
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
        return V8Undefined.INSTANCE;
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
        return V8Undefined.INSTANCE;
    }

    /**
     * Return the name of this function.
     *
     * @return the name of this function.
     */
    public default String getName() {
        return "";
    }

    // helper factories that can create JSFunction objects wrapping the standard
    // functional interface types from java.util.function package.

    /**
     * Create a new JSFunction wrapping the given BiConsumer object.
     *
     * @param biCons BiConsumer that is wrapped.
     * @return a new JSFunction.
     */
    public static JSFunction consumer(BiConsumer<Object, Object> biCons) {
        Objects.requireNonNull(biCons);
        return new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) {
                Object arg1 = args.length > 0? args[0] : V8Undefined.INSTANCE;
                Object arg2 = args.length > 1? args[1] : V8Undefined.INSTANCE;
                biCons.accept(arg1, arg2);
                return V8Undefined.INSTANCE;
            }
        };
    }

    /**
     * Create a new JSFunction wrapping the given Consumer object.
     *
     * @param cons Consumer that is wrapped.
     * @return a new JSFunction.
     */
    public static JSFunction consumer(Consumer<Object> cons) {
        Objects.requireNonNull(cons);
        return new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) {
                Object arg = args.length > 0? args[0] : V8Undefined.INSTANCE;
                cons.accept(arg);
                return V8Undefined.INSTANCE;
            }
        };
    }

    /**
     * Create a new JSFunction wrapping the given BiFunction object.
     *
     * @param biFunc BiFunction that is wrapped.
     * @return a new JSFunction.
     */
    public static JSFunction apply(BiFunction<Object, Object, ?> biFunc) {
        Objects.requireNonNull(biFunc);
        return new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) {
                Object arg1 = args.length > 0? args[0] : V8Undefined.INSTANCE;
                Object arg2 = args.length > 1? args[1] : V8Undefined.INSTANCE;
                return biFunc.apply(arg1, arg2);
            }
        };
    }

    /**
     * Create a new JSFunction wrapping the given Function object.
     *
     * @param func Function that is wrapped.
     * @return a new JSFunction.
     */
    public static JSFunction apply(Function<Object, ?> func) {
        Objects.requireNonNull(func);
        return new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) {
                Object arg = args.length > 0? args[0] : V8Undefined.INSTANCE;
                return func.apply(arg);
            }
        };
    }

    /**
     * Create a new JSFunction wrapping the given BiPredicate object.
     *
     * @param biPred BiPredicate that is wrapped.
     * @return a new JSFunction.
     */
    public static JSFunction predicate(BiPredicate<Object, Object> biPred) {
        Objects.requireNonNull(biPred);
        return new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) {
                Object arg1 = args.length > 0? args[0] : V8Undefined.INSTANCE;
                Object arg2 = args.length > 1? args[1] : V8Undefined.INSTANCE;
                return biPred.test(arg1, arg2);
            }
        };
    }

    /**
     * Create a new JSFunction wrapping the given Predicate object.
     *
     * @param pred Predicate that is wrapped.
     * @return a new JSFunction.
     */
    public static JSFunction predicate(Predicate<Object> pred) {
        Objects.requireNonNull(pred);
        return new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) {
                Object arg = args.length > 0? args[0] : V8Undefined.INSTANCE;
                return pred.test(arg);
            }
        };
    }

    /**
     * Create a new JSFunction wrapping the given Supplier object.
     *
     * @param supplier Supplier that is wrapped.
     * @return a new JSFunction.
     */
    public static JSFunction supplier(Supplier<?> supplier) {
        Objects.requireNonNull(supplier);
        return new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) {
                return supplier.get();
            }
        };
    }
}
