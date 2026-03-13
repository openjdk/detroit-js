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


var Int3 = Java.type('int[][][]');
var Int2 = Java.type('int[][]');
var Int1 = Java.type('int[]');

var x = new Int3(3);
for (var i = 0; i < x.length; i++) {
    x[i] = new Int2(3);
    for (var j = 0; j < x[i].length; j++) {
        x[i][j] = new Int1(3);
        for (var k = 0; k < x[i][j].length; k++) {
            x[i][j][k] = i * 9 + j * 3 + k;
        }
    }
}

assertEqualArrays(Object.keys(x), ['0', '1', '2']);
assertEqualArrays(Object.keys(x[1]), ['0', '1', '2']);
assertEqualArrays(Object.keys(x[1][1]), ['0', '1', '2']);

let counter = 0;

for (var i = 0; i < x.length; i++) {
    for (var j = 0; j < x[i].length; j++) {
        for (var k = 0; k < x[i][j].length; k++) {
            assertEquals(x[i][j][k], counter++);
        }
    }
}

assertEquals(counter, 27);

var Integer3 = Java.type('java.lang.Integer[][][]');
var Integer2 = Java.type('java.lang.Integer[][]');
var Integer1 = Java.type('java.lang.Integer[]');

x = new Integer3(3);
for (var i = 0; i < x.length; i++) {
    x[i] = new Integer2(3);
    for (var j = 0; j < x[i].length; j++) {
        x[i][j] = new Integer1(3);
        for (var k = 0; k < x[i][j].length; k++) {
            x[i][j][k] = i * 9 + j * 3 + k;
        }
    }
}

assertEqualArrays(Object.keys(x), ['0', '1', '2']);
assertEqualArrays(Object.keys(x[1]), ['0', '1', '2']);
assertEqualArrays(Object.keys(x[1][1]), ['0', '1', '2']);

counter = 0;

for (var i = 0; i < x.length; i++) {
    for (var j = 0; j < x[i].length; j++) {
        for (var k = 0; k < x[i][j].length; k++) {
            assertEquals(x[i][j][k], counter++);
        }
    }
}

assertEquals(counter, 27);
