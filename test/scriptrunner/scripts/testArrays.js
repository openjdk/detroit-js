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


function testJavaArray(ArrayCtor, converter) {
    let array = new ArrayCtor(10);

    assertEquals(array.length, 10);
    assertEquals(typeof array.hashCode, 'function');
    assertEquals(typeof array.hashCode(), 'number');

    for (let i in array) {
        assertTrue(array.hasOwnProperty(i));
        array[i] = converter ? converter(i) : i;
    }

    assertEquals(Array.prototype.join.call(array, ' '), '0 1 2 3 4 5 6 7 8 9');
    assertEqualArrays(Object.keys(array), ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9']);
}

testJavaArray(Java.type('int[]'));
testJavaArray(Java.type('byte[]'));
testJavaArray(Java.type('short[]'));
testJavaArray(Java.type('float[]'));
testJavaArray(Java.type('double[]'));
testJavaArray(Java.type('long[]'));
testJavaArray(Java.type('java.lang.Object[]'));
testJavaArray(Java.type('java.lang.String[]'));
testJavaArray(Java.type('java.lang.Integer[]'), Number);
testJavaArray(Java.type('java.lang.Double[]'), (x) => new java.lang.Double(x));

