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


var byteArray = Java.to([1, 2.5, '4', 'foo', false, true], 'byte[]');
assertEqualArrays(byteArray, [1, 2, 4, 0, 0, 1]);
assertEqualArrays(Java.from(byteArray), [1, 2, 4, 0, 0, 1]);

var shortArray = Java.to([1, 2.5, '4', 'foo', false, true], 'short[]');
assertEqualArrays(shortArray, [1, 2, 4, 0, 0, 1]);
assertEqualArrays(Java.from(shortArray), [1, 2, 4, 0, 0, 1]);

var intArray = Java.to([1, 2.5, '4', 'foo', false, true], 'int[]');
assertEqualArrays(intArray, [1, 2, 4, 0, 0, 1]);
assertEqualArrays(Java.from(intArray), [1, 2, 4, 0, 0, 1]);

var longArray = Java.to([1, 2.5, '4', 'foo', false, true], 'long[]');
assertEqualArrays(longArray, [1, 2, 4, 0, 0, 1]);
assertEqualArrays(Java.from(longArray), [1, 2, 4, 0, 0, 1]);

var floatArray = Java.to([1, 2.5, '4', false, true], 'float[]');
assertEqualArrays(floatArray, [1, 2.5, 4, 0, 1]);
assertEqualArrays(Java.from(floatArray), [1, 2.5, 4, 0, 1]);

floatArray = Java.to(["foo"], "float[]");
assertTrue(isNaN(floatArray[0]));

var doubleArray = Java.to([1, 2.5, '4', false, true], 'double[]');
assertEqualArrays(doubleArray, [1, 2.5, 4, 0, 1]);
assertEqualArrays(Java.from(doubleArray), [1, 2.5, 4, 0, 1]);

doubleArray = Java.to(["foo"], "double[]");
assertTrue(isNaN(doubleArray[0]));

var booleanArray = Java.to([1, 0, 'foo', '', true, false], 'boolean[]');
assertEqualArrays(booleanArray, [true, false, true, false, true, false]);
assertEqualArrays(Java.from(booleanArray), [true, false, true, false, true, false]);

var charArray = Java.to(['a', 97, 10], 'char[]');
assertEqualArrays(charArray, ['a', 'a', '\n']);  // TODO: should be instances of java.lang.Character, not strings
assertEqualArrays(Java.from(charArray), ['a', 'a', '\n']);

var objectArray = Java.to([1, 2.5, '4', 'foo', false, true], 'java.lang.Object[]');
assertEqualArrays(objectArray, [1, 2.5, '4', 'foo', false, true]);
assertEqualArrays(Java.from(objectArray), [1, 2.5, '4', 'foo', false, true]);

var jstringArray = Java.to(['a', 'b', 'c'], 'java.lang.String[]');
assertEqualArrays(jstringArray, ['a', 'b', 'c']);
assertEqualArrays(Java.from(jstringArray), ['a', 'b', 'c']);

// When feeding back a java.lang.Class instance into JS, it gets wrapped as a JavaClass instance
var jclassArray = Java.to([java.lang.Object.class, java.lang.System.class], 'java.lang.Class[]');
assertEqualArrays(Java.from(jclassArray), [java.lang.Object, java.lang.System]);

// strings must have length 1 to be converted to char
assertThrows(function() { Java.to([''], 'char[]'); });
assertThrows(function() { Java.to(['abc'], 'char[]'); });

var ArrayList = Java.type('java.util.ArrayList');
var list = new ArrayList();
var obj = {'p': 123, 'f': function() { return 'It\'s working!'; } };
list.add('foo');
list.add(13);
list.add(true);
list.add(5.2);
list.add(obj);
assertEqualArrays(Java.from(list), ['foo', 13, true, 5.2, obj]);
print(list.get(4).f());

