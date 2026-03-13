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


function check(callback) {
    try {
        callback();
        throw new Error("should not reach here");
    } catch (e) {
        assertTrue(e instanceof TypeError);
        assertTrue(e.message.indexOf("Expected an array-like object as the first argument") != -1);
    }
}

check(() => Java.to(34, Java.type("int[]")));
check(() => Java.to(34, java.util.List));
check(() => Java.to("hello", Java.type("int[]")));
check(() => Java.to("world", java.util.List));
check(() => Java.to(true, Java.type("int[]")));
check(() => Java.to(false, java.util.List));
check(() => Java.to({}, Java.type("int[]")));
check(() => Java.to(null, java.util.List));
check(() => Java.to(undefined, java.util.List));
