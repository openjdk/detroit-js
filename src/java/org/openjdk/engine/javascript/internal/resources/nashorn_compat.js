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

'use strict';

// nashorn compatibility script for detroit

// minimal JSAdapter using ES6 Proxy
Object.defineProperty(this, "JSAdapter", {
    value: function(obj) {
        return new Proxy(obj, {
            get: function(target, name) {
                return target.__get__(name);
            },

            has: function(target, name) {
                return target.__has__(name);
            },

            set: function(target, name, value) {
                return target.__put__(name, value);
            },

            deleteProperty: function(target, name) {
                return target.__delete__(name);
            }
        });
    },
    enumerable: false,
    configurable: true,
    writable: true
});

// Object.bindProperties function
Object.defineProperty(Object, "bindProperties", {
    value: function(target, source) {
        for (var p in source) {
            Object.defineProperty(target, p, {
                get: function() {
                    return source[p];
                },
                set: function(val) {
                    source[p] = val;
                },
                enumerable: true,
                configurable: true,
            });
        }

        return target;
    },
    enumerable: false,
    configurable: true,
    writable: true
});

Object.defineProperty(this, "readLine", {
    value: function(prompt) {
        var p = prompt? String(prompt) : "";
        java.lang.System.out.print(p);
        var reader = new java.io.BufferedReader(new java.io.InputStreamReader(java.lang.System.in));
        return reader.readLine();
    },
    enumerable: false,
    configurable: true,
    writable: true
});

Object.defineProperty(this, "readFully", {
    value: function(file) {
        var File = Java.type("java.io.File");
        var Files = Java.type("java.nio.file.Files");
        var JString = Java.type("java.lang.String");

        if (typeof file == 'string') {
            file = new File(file);
        }

        if (file instanceof File) {
            return new JString(Files.readAllBytes(file.toPath())).toString();
        } else {
            throw new TypeError("File expected, got " + file);
        }
    },
    enumerable: false,
    configurable: true,
    writable: true
});

String.prototype.isEmpty = function() {
    return this == "";
}

String.prototype.compareTo = function(other) {
    let JString = java.lang.String;
    return new JString(this).compareTo(String(other));
}

String.prototype.equals = function(other) {
    return this == other;
}
