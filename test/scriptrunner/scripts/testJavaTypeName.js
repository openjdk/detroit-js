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


var F = Java.type("java.io.File");
var URL = java.net.URL;
var java_lang = java.lang;

assertTrue(Java.typeName(F) == "java.io.File");
assertTrue(Java.typeName(URL) == "java.net.URL");

assertTrue(Java.typeName(java_lang) === undefined);
assertTrue(Java.typeName(333) === undefined);
assertTrue(Java.typeName(false) === undefined);
assertTrue(Java.typeName("hello") === undefined);
assertTrue(Java.typeName({}) === undefined);
assertTrue(Java.typeName(null) === undefined);
assertTrue(Java.typeName(undefined) === undefined);
assertTrue(Java.typeName(function() {}) === undefined);
