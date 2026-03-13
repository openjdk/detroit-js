/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


var map = new java.util.HashMap();

map["put(java.lang.Object,java.lang.Object)"]("py", "python");
map["put(Object,Object)"]("js", "javascript");

// call without signature
map["put"]("pl", "perl");
assertEquals(map.get("py"), "python");
assertEquals(map.get("js"), "javascript");
assertEquals(map.get("pl"), "perl");

map["clear()"]();
assertTrue(map.isEmpty());

assertEquals(java.lang.Integer["valueOf(java.lang.String,int)"]("FF", 16), 255);
assertEquals(java.lang.Integer["valueOf(String,int)"]("FF", 16), 255);

var JStringCtor = Java.type("java.lang.String")["(char[],int,int)"];
var jstr = new JStringCtor(Java.to(['a', 'b', 'c'], 'char[]'), 0, 3);

assertTrue(jstr instanceof java.lang.String);
assertTrue(jstr instanceof JStringCtor);
assertEquals(jstr.toString(), 'abc');

// explicit constructor call - but with automatic JS array to Java array conversion
var jstr = new JStringCtor(['a', 'b', 'c'], 0, 3);

assertTrue(jstr instanceof java.lang.String);
assertTrue(jstr instanceof JStringCtor);
assertEquals(jstr.toString(), 'abc');
