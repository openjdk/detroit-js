/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

"use strict";

/*
 * This bootstrap script file is used for Java support for scripts. Java objects
 * are wrapped as a script-friendly objects (see JavaWrap and JavaObject functions).
 * A script is dynamically generated for every Java class and eval'ed (refer to the
 * V8ClassGenerator class) to wrap Java objects of that class. JavaWrap function is
 * is also returned from this script and is known to native code. The native code
 * assumes that underlying Java object can be unwrapped from the script friendly object
 * wrapper by retrieving a property with the known key Symbol.
 *
 * This script file defines a named anonymous function! We get the name in stack traces
 * but still the function is not defined in global scope. This should be the last expression
 * in this script.  Caller saves the "eval" result of this file and uses the same as a
 * function to bootstrap Java support in every V8 Context.
 */

/**
 * @param DEBUG turn on DEBUG mode for this script
 * @param GLOBAL global object into which this scripts adds new properties.
 * @param JVM JNI interface to scripts
 * @param JAVA_OBJECT symbol to be used to as property key for Java object being wrapped
 */
(function BOOTSTRAP(DEBUG, GLOBAL, JVM, JAVA_OBJECT) {

    // Symbol used to attach native java objects to JS wrappers for Java objects, arrays, and classes
    const javaObject = JAVA_OBJECT;
    // Symbol for functional interface method name
    const functionalInterfaceMethod = Symbol('functionalInterfaceMethod');
    // Symbol to tell whether an object is a Java class
    const isJavaType = Symbol("isJavaType");
    // Symbol for classes implementing java.util.List
    const isList = Symbol('isList');
    // Symbol for classes for static properties
    const staticProperties = Symbol('staticProperties');

    // Minimum and maximum java long values
    const MIN_LONG = -0x8000000000000000;
    const MAX_LONG =  0x7fffffffffffffff;

    // This function is assigned as value for Symbol.hasInstance key for Java classes
    function javaHasInstance(obj) {
        const type = typeof obj;
        if (obj && (type == 'object' || type == 'function')) {
            if (javaObject in obj) {
                return JVM.isInstanceOf(obj[javaObject], this[javaObject]);
            } else {
                return obj.constructor === this;
            }
        }
        return false;
    }

    function javaMapIterator() {
        return javaIterator(this.entrySet().iterator());
    }

    function javaIterableIterator() {
        return javaIterator(this.iterator());
    }

    function javaIterator(iterator) {
        return {
            next() {
                if (iterator.hasNext()) return {
                    value: iterator.next(),
                    done: false
                };
                return { done: true };
            }
        };
    }

    // Java class wrapper constructor
    function JavaClass(cls, name) {
        // Java class
        this.class = cls;
        // Supplied name or name from the class
        this.name = name || JVM.getClassName(cls);

        // Get and evaluate script containing definitions of field accessors and methods
        const code = JVM.generateClass(cls);

        const evalOkay = JVM.isEvalAllowed();
        if (!evalOkay) JVM.setEvalAllowed(true);
        // The eval'ed script returns the constructor object
        let ctor;
        try {
            ctor = eval(code);
        } finally {
            if (!evalOkay) JVM.setEvalAllowed(false);
        }

        // save cls as private/internal property of the constructor object
        Object.defineProperty(ctor, javaObject, { value: cls });
        // to tell that this is a java type!
        Object.defineProperty(ctor, isJavaType, { value: true });
        ctor.__proto__ = JavaConstructorProxy(name, ctor[staticProperties]);
        // Java class constructor defined in script
        this.CONSTRUCTOR = ctor;
    }

    // Called from JavaObject below and Java interface constructor functions, returns a function proxy for SAMs
    function makeJavaWrapper(ctor, obj) {
        const methodName = ctor[functionalInterfaceMethod];
        let wrapper;
        if (methodName) {
            wrapper = FunctionalInterfaceProxy(ctor.prototype, methodName);
        } else if (ctor[isList]) {
            wrapper = JavaListProxy(ctor.prototype);
        } else {
            wrapper = JavaObjectProxy(ctor.prototype);
        }

        Object.defineProperty(wrapper, javaObject, { value: obj });

        return wrapper;
    }

    // Java object wrapper factory
    function JavaObject(obj) {
        const cls = JVM.getObjectClass(obj);
        const javaClass = findClassByMirror(cls);

        const isArray = javaClass.name.startsWith('[');
        if (isArray) {
            const wrapper = Object.create(javaClass.CONSTRUCTOR.prototype);
            Object.defineProperty(wrapper, javaObject, { value: obj });
            Object.defineProperty(wrapper, 'length', { value: JVM.getArrayLength(obj) });
            return JavaArrayProxy(wrapper);
        } else {
            return makeJavaWrapper(javaClass.CONSTRUCTOR, obj);
        }
    }

    // Java method result constructor
    function JavaWrap(obj) {
        // Is this already a native JS object coming back to us from Java land?
        if (obj instanceof Object) {
            return obj;
        }
        if (obj !== null && typeof obj === 'object') {
            if (isInstance(obj, 'java.lang.Class')) {
                return findClassByMirror(obj).CONSTRUCTOR;
            }
            return JavaObject(obj);
        } else {
            return obj;
        }
    }

    function toCanonicalClassName(name) {
        const primitiveMap = {
            'boolean': 'Z',
            'byte':    'B',
            'char':    'C',
            'short':   'S',
            'int':     'I',
            'long':    'J',
            'float':   'F',
            'double':  'D'
        };
        let internalName = '';
        while (name.endsWith('[]')) {
             internalName += '[';
             name = name.slice(0, -2);
        }
        if (name in primitiveMap) {
            internalName += primitiveMap[name];
        } else if (internalName) {
            internalName += 'L' + name + ';';
        } else {
            internalName = name;
        }

        return internalName;
    }

    // this cache is only for classes in the isolate's
    // own class loader
    const java_classes_by_name = new Map();

    // accepts a name as returned by Class::getName
    function findClassByName(name) {
        const canonicalName = toCanonicalClassName(name);
        const cachedClass = java_classes_by_name.get(canonicalName);
        if (cachedClass) {
            return cachedClass;
        }

        // Find a class with this name in the isolate's class loader
        let cls = undefined;
        try {
            cls = JVM.findClass(canonicalName);
        } catch (error) {
            return undefined;
        }
        const javaClass = new JavaClass(cls, canonicalName);
        java_classes_by_name.set(canonicalName, javaClass);
        return javaClass
    }

    class MirrorCache {
        constructor() {
            this.entries = new Map();
        }

        put(mirrorHash, javaClass) {
            let bucket = this.entries.get(mirrorHash);
            if (!bucket) {
                bucket = [];
                this.entries.set(mirrorHash, bucket);
            }
            bucket.push(javaClass);
        }

        get(mirrorHash, mirror) {
            const bucket = this.entries.get(mirrorHash);
            if (!bucket) {
                return undefined;
            }
            // linear search for classes with the same hash (should be very rare)
            for (const javaClass of bucket) {
                if (JVM.isSameObject(javaClass.class, mirror)) {
                    return javaClass;
                }
            }
            return undefined;
        }
    }

    const java_classes_by_mirror = new MirrorCache();

    // Takes java.lang.Class as input
    // will distinguish classes with the same name but a different class loader
    function findClassByMirror(mirror, knownName) {
        const mirrorHash = JVM.getHashCode(mirror);
        const cachedClass = java_classes_by_mirror.get(mirrorHash, mirror);
        if (cachedClass) {
            return cachedClass;
        }

        // Look in the isolate's class loader for a class with this name
        const name = knownName || JVM.getClassName(mirror);
        const javaClassByName = findClassByName(name);
        if (javaClassByName && JVM.isSameObject(javaClassByName.class, mirror)) {
            // Cached JavaClass was made from this mirror
            return javaClassByName;
        }

        const javaClass = new JavaClass(mirror, name);
        // Don't pollute the named cache with this class, since the name may not be unique
        java_classes_by_mirror.put(mirrorHash, javaClass);
        return javaClass;
    }

    // Helper to deal with primitive classes
    function primitiveClass(boxClass, name) {
        // Get TYPE field (from boxing class) containing primitive class
        const fieldID = JVM.getStaticFieldID(boxClass.class, 'TYPE', 'Ljava/lang/Class;');
        const cls = JVM.getStaticObjectField(boxClass.class, fieldID);
        // Define class
        java_classes_by_name.set(name, new JavaClass(cls, name));
    }

    function findClassPrivate(name) {
        return findClassByMirror(JVM.findClassPrivate(name), name);
    }

    const StringClass     = findClassPrivate('java.lang.String');
    const NumberClass     = findClassPrivate('java.lang.Number');
    const LongClass       = findClassPrivate('java.lang.Long');
    const CollectionClass = findClassPrivate('java.util.Collection');
    const ByteBufferClass = findClassPrivate('java.nio.ByteBuffer');

    function java_type(name) {
        const javaClass = findClassByName(name);

        return javaClass ? javaClass.CONSTRUCTOR : undefined;
    }

    function java_to(array, type) {
        if (!Array.isArray(array)) {
            if (isArguments(array) || isArrayLike(array)) {
                array = Array.from(array);
            } else {
                throw new TypeError('Expected an array-like object as the first argument');
            }
        }
        const length = array.length;
        const javaType = typeof(type) === 'function'? type : java_type(type || '[Ljava.lang.Object;');
        if (typeof javaType !== 'function') {
            throw new TypeError('Invalid java type: ' + type);
        }

        if (javaType.class.name.startsWith("[")) {
            const javaArray = new javaType(length);
            for (let i = 0; i < length; i++) {
                javaArray[i] = array[i];
            }
            return javaArray;
        } else {
            return new JavaObject(JVM.createListAdapter(array, type.class[javaObject]));
        }
    }

    function java_from(arg) {
        const javaArray = isInstance(arg, CollectionClass) ? arg.toArray() : arg;
        const length = javaArray.length;
        const array = [];
        for (let i = 0; i < length; i++) {
            array[i] = javaArray[i];
        }
        return array;
    }

    function java_asByteBuffer(abuf) {
        if (!(abuf instanceof ArrayBuffer)) {
            throw new TypeError("ArrayBuffer expected");
        }
        return JavaWrap(JVM.asByteBuffer(abuf));
    }

    function java_asArrayBuffer(bbuf) {
        if (!isInstance(bbuf, ByteBufferClass)) {
            throw new TypeError("Expected a nio ByteBuffer object");
        }
        return JVM.asArrayBuffer(bbuf[javaObject]);
    }

    // Java argument support.
    function isBoolean(arg) { return typeof arg === 'boolean'; }

    function isInteger(arg) { return isNumber(arg) && (arg | 0) == arg; }

    function isChar(arg) { return typeof arg === 'string' && arg.length === 1; }

    function isLong(arg) { return (isNumber(arg) && Math.floor(arg) == arg && arg >= MIN_LONG && arg <= MAX_LONG) || isInstance(arg, LongClass); }
    function toLong(arg) { return toObject(arg); }

    function isNumber(arg) { return typeof arg === 'number'; }

    function isString(arg) { return typeof arg === 'string' || arg == null; }
    function toString(arg) { return arg === null? arg : String(arg); }

    function isArguments(arg) { return typeof arg === 'object' && Object.prototype.toString.call(arg) === "[object Arguments]" }

    function isArrayLike(arg) { return arg instanceof Object && !(javaObject in arg) && 'length' in arg  }

    // is this a JS array or 'arguments' object or any array-like object?
    function isArrayOrArguments(arg) {
        return Array.isArray(arg) || isArguments(arg) || isArrayLike(arg);
    }

    function toObject(arg) { return isJavaObject(arg) ? arg[javaObject] : arg; }

    function toNonUndefinedObject(arg, type) {
        if (arg === undefined) {
            throw new TypeError("undefined cannnot be passed for: " + type);
        }
        return toObject(arg);
    }

    function toListObject(arg, className) {
        if (isArrayOrArguments(arg)) {
            return JVM.createListAdapter(arg, CollectionClass.class);
        } else {
            return toNonUndefinedObject(arg, className);
        }
    }

    function toJavaArray(arg, className) {
        if (isArrayOrArguments(arg)) {
            return toObject(Java.to(arg, className));
        } else {
            return toNonUndefinedObject(arg, className);
        }
    }

    function toInterface(arg, className) {
        if (typeof arg === 'function' && ! (javaObject in arg)) {
            const javaType = java_type(className);
            return toObject(new javaType(arg));
        } else {
            return toNonUndefinedObject(arg, className);
        }
    }

    function isVarArgs(args, arrayType, componentType) {
        if (componentType !== 'java.lang.Object') {
            const firstNonNullArg = args.find(a => a != null);
            if (firstNonNullArg != null && !isArrayOrArguments(firstNonNullArg)) {
                return isInstance(firstNonNullArg, componentType)
                    || (args.length === 1 && isInstance(firstNonNullArg, arrayType));
            }
        }
        return true;
    }

    function toVarArgs(args, type) {
        if (args.length == 1) {
            if (isArrayOrArguments(args[0])) {
                return java_to(args[0], type)[javaObject];
            } else if (isInstance(args[0], type)) {
                return toObject(args[0]);
            } else if (args[0] === null) {
                return null;
            }
        }
        return java_to(args, type)[javaObject];
    }

    function isInstance(arg, classOrName) {
        const jclass = typeof classOrName === 'string' ? findClassByName(classOrName) : classOrName;
        return arg != null && JVM.isInstanceOf(toObject(arg), jclass.class);
    }

    function isJavaObject(arg) {
        return arg instanceof Object && arg.hasOwnProperty(javaObject);
    }

    // Convert prop to a JS array index, or return -1 if not a valid index
    function toIndex(prop) {
        if (typeof prop !== 'symbol') {
            const index = Number(prop) | 0; // TODO: range checks
            return String(index) == prop ? index : -1
        }
        return -1;
    }

    // Lookup a java method or field and define an invocation stub for it
    function lookupProperty(constructor, prop, isStatic) {
        const code = JVM.lookupProperty(constructor[javaObject], prop, isStatic);
        if (code) {
            const evalOkay = JVM.isEvalAllowed();
            if (!evalOkay) JVM.setEvalAllowed(true);
            let desc;
            try {
                desc = eval(code);
            } finally {
                if (!evalOkay) JVM.setEvalAllowed(false);
            }

            if (isStatic && desc.value && desc.value.name === 'CONSTRUCTOR') {
                // Explicit signature constructor, copy prototype property from main  constructor
                Object.defineProperty(desc.value, 'prototype', { value: constructor.prototype });
            }
            return desc;
        }
        return false;
    }

    function JavaObjectProxy(proto) {
        return Object.create(proto);
    }

    function JavaListProxy(proto) {
        return new Proxy(Object.create(proto), {
            get(target, prop) {
                const index = toIndex(prop);
                if (0 <= index && index < target.size()) {
                    return target.get(index);
                }
                return target[prop];
            },

            set(target, prop, value) {
                const index = toIndex(prop);
                if (0 <= index && index < target.size()) {
                    target.set(index, value);
                }
                return true;
            },

            has(target, prop) {
                const index = toIndex(prop);
                return (0 <= index && index < target.size()) || prop in target;
            },

            deleteProperty(target, prop) {
                const index = toIndex(prop);
                if (0 <= index && index < target.size()) {
                    return target.remove(index);
                }
                return false;
            },

            ownKeys(target) {
                return Array.from(new Array(target.size()).keys()).map(String).concat(javaObject);
            },

            getOwnPropertyDescriptor(target, prop) {
                const index = toIndex(prop);

                if (0 <= index && index < target.size()) {
                    return {
                        value: target.get(index),
                        writable: true,
                        enumerable: true,
                        configurable: true
                    };
                }

                return Object.getOwnPropertyDescriptor(target, prop);
            }
        });
    }

    function JavaProtoProxy(superClassName, cls, properties) {
        const target = Object.create(findClassByMirror(JVM.getSuperclass(cls), superClassName).CONSTRUCTOR.prototype);
        return new Proxy(target, {
            has(target, prop) {
                return prop in target || properties.hasOwnProperty(prop);
            },
            ownKeys(target) {
                return Object.keys(properties);
            }
        });
    }

    function JavaBottomProxy(properties) {
        return new Proxy({
            [Symbol.toPrimitive](hint) {
                // Responsible for converting Java objects to JS primitives
                if (hint !== 'string' && JVM.isInstanceOf(this[javaObject], NumberClass.class)) {
                    return this.doubleValue();
                }
                return this.toString();
            }
        }, {
            get(target, prop, receiver) {
                if (typeof prop === 'string') {
                    // Handle array-index like properties for Java strings and lists
                    const index = toIndex(prop);
                    if (index > -1) {
                        if (JVM.isInstanceOf(receiver[javaObject], StringClass.class)) {
                            return index < receiver.length() ? receiver.charAt(index) : undefined;
                        }
                    }
                    // If this is a method/field/property in the Java object define it
                    if ((prop in receiver || prop.indexOf('(') > -1) && prop !== 'constructor') {
                        const def = lookupProperty(receiver.constructor, prop, false);
                        if (def) {
                            Object.defineProperty(receiver.constructor.prototype, prop, def);
                            return prop in receiver ? receiver[prop] : undefined;
                        }
                    }
                }

                return target[prop];
            },

            set(target, prop, value, receiver) {
                if (typeof prop === 'string') {
                    // If this is a method/field/property in the Java object make sure it is defined
                    if (prop !== 'constructor' && prop in receiver) {
                        const def = lookupProperty(receiver.constructor, prop, false);
                        if (def) {
                            Object.defineProperty(receiver.constructor.prototype, prop, def);
                            receiver[prop] = toObject(value);
                            return true;
                        }
                    }
                }

                return false;
            },

            has(target, prop) {
                return prop in properties;
            }
        });
    }

    function JavaConstructorProxy(name, properties) {
        const target = {
            toString: function() {
                return '[JavaClass ' + name + ']';
            }
        };
        Object.setPrototypeOf(target, JavaConstructorProxy.prototype);

        return new Proxy(target, {
            get(target, prop, receiver) {
                if (typeof prop === 'string' && javaObject in receiver) {
                    if ('class' === prop) {
                        const classObject = JavaObject(receiver[javaObject]);
                        Object.defineProperty(receiver, prop, { value: classObject });
                        return classObject;
                    }
                    const def = lookupProperty(receiver, prop, true);
                    if (def) {
                        Object.defineProperty(receiver, prop, def);
                        return prop in receiver ? receiver[prop] : undefined;
                    }
                }
                return target[prop];
            },
            set(target, prop, value, receiver) {
                // If this is a method/field/property in the Java object make sure it is defined
                if (typeof prop === 'string' && javaObject in receiver) {
                    const def = lookupProperty(receiver, prop, true);
                    if (def) {
                        Object.defineProperty(receiver, prop, def);
                        receiver[prop] = toObject(value);
                        return true;
                    }
                    return false;
                }
                // Allow one-time, read-only definition of the javaObject property
                if (prop === javaObject) {
                    Object.defineProperty(receiver, javaObject, { value: value });
                    return true;
                }
                return false;
            },
            ownKeys(target) {
                return Object.keys(properties);
            }
        });
    }

    // see also jvmv8_primitives.cpp. Synchronize changes!
    const J_BOOLEAN         = 0;
    const J_CHAR            = 1;
    const J_BYTE            = 2;
    const J_SHORT           = 3;
    const J_INT             = 4;
    const J_LONG            = 5;
    const J_FLOAT           = 6;
    const J_DOUBLE          = 7;
    const J_BOOLEAN_WRAPPER = 8;
    const J_CHAR_WRAPPER    = 9;
    const J_BYTE_WRAPPER    = 10;
    const J_SHORT_WRAPPER   = 11;
    const J_INT_WRAPPER     = 12;
    const J_LONG_WRAPPER    = 13;
    const J_FLOAT_WRAPPER   = 14;
    const J_DOUBLE_WRAPPER  = 15;
    const J_STRING          = 16;
    const J_OBJECT          = 17;

    // Wrapper for java array types
    function JavaArrayProxy(target) {
        const typeCode = JVM.getArrayElementTypeCode(target[javaObject]);
        return new Proxy(target, {
            get(target, prop) {
                const index = toIndex(prop);

                if (0 <= index && index < target.length) {
                    return JavaWrap(JVM.getArrayElement(target[javaObject], index, typeCode));
                }
                return target[prop];
            },

            set(target, prop, value) {
                const index = toIndex(prop);

                if (0 <= index && index < target.length) {

                    switch (typeCode) {
                        case J_BOOLEAN:
                            value = Boolean(value);
                            break;

                        case J_BYTE:
                        case J_SHORT:
                        case J_INT:
                        case J_LONG:
                            value = value === null || value === undefined? 0 : Number(value);
                            break;

                        case J_FLOAT:
                        case J_DOUBLE:
                            value = Number(value);
                            break;

                        case J_BYTE_WRAPPER:
                        case J_SHORT_WRAPPER:
                        case J_INT_WRAPPER:
                        case J_LONG_WRAPPER:
                            if (isJavaObject(value)) {
                                if (!isInstance(value, "java.lang.Number")) {
                                    throw new TypeError("Not a Java Number object");
                                }
                                value = value[javaObject];
                            } else {
                                value = value === null || value === undefined? null : Number(value);
                            }
                            break;

                        case J_FLOAT_WRAPPER:
                        case J_DOUBLE_WRAPPER:
                            if (isJavaObject(value)) {
                                if (!isInstance(value, "java.lang.Number")) {
                                    throw new TypeError("Not a Java Number object");
                                }
                                value = value[javaObject];
                            } else {
                                value = value === null? value: Number(value);
                            }
                            break;

                        case J_BOOLEAN_WRAPPER:
                            if (isJavaObject(value)) {
                                if (!isInstance(value, "java.lang.Boolean")) {
                                    throw new TypeError("Not a Java Boolean object");
                                }
                                value = value[javaObject];
                            } else {
                                value = value === null || value == undefined? null : Boolean(value);
                            }
                            break;

                        case J_CHAR_WRAPPER:
                            if (isJavaObject(value)) {
                                if (!isInstance(value, "java.lang.Character")) {
                                    throw new TypeError("Not a Java Character object");
                                }
                                value = value[javaObject];
                            } else {
                                value = value === undefined? null : value;
                            }
                            break;

                        case J_STRING:
                            value = value === null? null : String(value);
                            break;

                        default:
                            value = toObject(value);
                            break;
                    }

                    try {
                        JVM.setArrayElement(target[javaObject], index, value, typeCode);
                    } catch (e) {
                        const ne = new TypeError();
                        ne.javaException = e.javaException;
                        throw ne;
                    }
                }

                // See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy/handler/set
                // The set method should return a boolean value. Return true to indicate that
                // assignment succeeded. If the set method returns false, and the assignment
                // happened in strict-mode code, a TypeError will be thrown.

                return true;
            },

            has(target, prop) {
                const index = toIndex(prop);
                return (0 <= index && index < target.length) || prop in target;
            },

            ownKeys(target) {
                return Array.from(new Array(target.length).keys()).map(String).concat('length', javaObject);
            },

            getOwnPropertyDescriptor(target, prop) {
                const index = toIndex(prop);

                if (0 <= index && index < target.length) {
                    return {
                        value: target[prop],
                        writable: true,
                        enumerable: true,
                        configurable: true
                    };
                }

                return Object.getOwnPropertyDescriptor(target, prop);
            }
        });
    }

    const JavaArrayProtoProxy = Object.create(findClassByName('java.lang.Object').CONSTRUCTOR.prototype, {
        // Implement iterator protocol for java arrays
        [Symbol.iterator]: {
            value: function () {
                const array = this;
                const length = this.length;
                let index = 0;
                return {
                    next() {
                        if (index < length) return {
                            value: array[index++],
                            done: false
                        };
                        return {done: true};
                    }
                };
            }
        }
    });

    function FunctionalInterfaceProxy(proto, methodName) {
        const target = function() {};
        Object.setPrototypeOf(target, proto);

        const proxy = new Proxy(target, {
            apply(target, self, args) {
                return proxy[methodName].apply(proxy, args);
            },

            construct(target, prop, args) {
                throw new TypeError(proxy + ' is not a constructor');
            }
        });

        return proxy;
    }

    // Java package implementation

    const JavaPackageHandler = {
        get(target, prop) {
            if (prop === Symbol.toPrimitive || prop === 'toString') {
                return function toString() { return target.toString(); };
            }
            if (isString(prop)) {
                return target.getChild(prop);
            }
            return undefined;
        },

        has(target, prop) {
            return isString(prop);
        },

        apply(target, self, args) {
            target.notAFunction();
        },

        construct(target, prop, args) {
            target.classNotFound();
        },

        ownKeys(target) {
            return []; // we don't know the sub-packages!
        }
    };

    function JavaPackage(name) {
        const jpackage = function jpackage() {}; // Must be a function in order to make proxy callable
        Object.setPrototypeOf(jpackage, JavaPackage.prototype);
        const cachedChildren = new Map();

        jpackage.ownName = function ownName() {
            const dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : name;
        };

        jpackage.getChild = function getChild(childName) {
            const newName = name ? name + '.' + childName : childName;
            let child = cachedChildren.get(newName);
            if (!child) {
                child = java_type(newName) || JavaPackage(newName);
                cachedChildren.set(newName, child);
            }
            return child;
        };

        jpackage.toString = function toString() {
            return '[JavaPackage ' + name + ']';
        };

        jpackage.notAFunction = function notAFunction() {
            throw new TypeError(this + ' is not a function');
        };

        jpackage.classNotFound = function classNotFound() {
            throw new TypeError('Java class ' + name + ' not found');
        };

        return new Proxy(jpackage, JavaPackageHandler);
    }

    const JavaImporterHandler = {
        get(target, prop) {
            return target.find(prop);
        },

        has(target, prop) {
            return typeof target.find(prop) !== 'undefined'
        },

        apply(target, self, args) {
            throw new TypeError(this + ' is not a function');
        },

        construct(target, prop, args) {
            throw new TypeError(this + ' is not a function');
        }
    };

    function JavaImporter() {
        // do some validation on arguments to make sure we have got
        // package and/or class objects!

        for (const i in arguments) {
            const arg = arguments[i];
            if (typeof arg !== 'function') {
                throw TypeError("expected import to be a package or class: arg" + i);
            }
            if (!(arg instanceof JavaPackage) && !(arg instanceof JavaConstructorProxy)) {
                throw TypeError("expected import to be a package or class: arg" + i);
            }
        }

        const importer = {
            imports: Array.prototype.reverse.call(arguments), // latter import overrides former one!

            find(name) {
                for (const i in this.imports) {
                    const pkgOrClass = this.imports[i];
                    if (pkgOrClass instanceof JavaConstructorProxy) {
                        // already a class in import. check if the name ends with simple name!
                        const clsName = pkgOrClass.class.getSimpleName();
                        if (clsName === String(name)) {
                            return pkgOrClass;
                        }
                    } else {
                        // it is a package. check if that package has a class of the given name
                        const item = pkgOrClass[name];
                        if (typeof item === 'function' && javaObject in item) {
                            // class found in that package
                            return item;
                        }
                    }
                }

                return undefined;
            }
        };

        return new Proxy(importer, JavaImporterHandler);
    }

    Object.defineProperties(GLOBAL, {
        Java: {
            value: {},
            configurable: true,
            writable: true
        },
        com: {
            value: JavaPackage('com'),
            configurable: true,
            writable: true
        },
        edu: {
            value: JavaPackage('edu'),
            configurable: true,
            writable: true
        },
        java: {
            value: JavaPackage('java'),
            configurable: true,
            writable: true
        },
        javafx: {
            value: JavaPackage('javafx'),
            configurable: true,
            writable: true
        },
        javax: {
            value: JavaPackage('javax'),
            configurable: true,
            writable: true
        },
        org: {
            value: JavaPackage('org'),
            configurable: true,
            writable: true
        },
        Packages: {
            value: JavaPackage(''),
            configurable: true,
            writable: true
        },
        JavaImporter: {
            value: JavaImporter,
            configurable: true,
            writable: true
        }
    });

    Object.defineProperties(GLOBAL.Java, {
        from: {
            value: java_from,
            configurable: true,
            writable: true
        },
        to: {
            value: java_to,
            configurable: true,
            writable: true
        },
        type: {
            value: function type(name) {
                const cls = java_type(name);
                if (cls) {
                    return cls;
                }
                throw new TypeError('Java class ' + name + ' not found');
            },
            configurable: true,
            writable: true
        },
        isType: {
            value: obj => obj instanceof Object && isJavaType in obj,
            configurable: true,
            writable: true
        },
        typeName: {
            value: obj => Java.isType(obj)? obj.class.getName() : undefined,
            configurable: true,
            writable: true
        },
        asArrayBuffer: {
            value: java_asArrayBuffer,
            configurable: true,
            writable: true
        },
        asByteBuffer: {
            value: java_asByteBuffer,
            configurable: true,
            writable: true
        },
        isJavaObject: {
            value: isJavaObject,
            configurable: true,
            writable: true
        }
    });

    // native code saves the returned function and calls it whenever it
    // has to wrap a Java object with a script friendly object
    return JavaWrap;
});
