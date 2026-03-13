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


const ArrayList = Java.type("java.util.ArrayList");

const list = new ArrayList();

list.add("a");
list.add("b");
list.add("c");

print(list.toString());

const HM = Java.type("java.util.HashMap");
const m = new HM();
m.put(null, null);
m.put("x", null);
print(m.toString());


var Class = java.lang.Class;
try {
    Class.forName("java.lang.Object");
    throw new Error("FAILED: Class.forName(String) should have been hidden!");
} catch(e) {
}

print(Object.getOwnPropertyNames(Class));

const IAE = Java.type("java.lang.IllegalArgumentException");

var caughtException = false;
try {
    const l = new ArrayList(-1);
} catch (e) {
    caughtException = true;
    print(e);
    assertTrue(e.javaException instanceof IAE);
    e.javaException.printStackTrace();
}

if (!caughtException) {
    throw new Error("should have caught exception!");
}

print("Successful test");
