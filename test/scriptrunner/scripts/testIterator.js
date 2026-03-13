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



const jobject = new java.lang.Object();

let list = new java.util.ArrayList();
list.add(3);
list.add('foo');
list.add(true);
list.add(jobject);

assertEquals(list.size(), 4);
assertEquals(list.get(0), 3);
assertEquals(list.get(1), 'foo');
assertEquals(list.get(2), true);
assertEquals(list.get(3), jobject);

assertEquals(list[0], 3);
assertEquals(list[1], 'foo');
assertEquals(list[2], true);
assertEquals(list[3], jobject);

let array = [];
for (let e of list) {
    array.push(e);
}
assertEqualArrays(array, [3, 'foo', true, jobject]);

// test for..of on java array
array = [];
for (let e of list.toArray()) {
    array.push(e);
}
assertEqualArrays(array, [3, 'foo', true, jobject]);


let map = new java.util.LinkedHashMap();
map.put(3, 4);
map.put('foo', 'bar');
map.put(true, 1);
map.put(jobject, jobject);

assertEquals(map.size(), 4);
assertEquals(map.get(3), 4);
assertEquals(map.get('foo'), 'bar');
assertEquals(map.get(true), 1);
assertEquals(map.get(jobject), jobject);

array = [];
for (let e of map) {
    array.push(e.key, e.value);
}
assertEqualArrays(array, [3, 4, 'foo', 'bar', true, 1, jobject, jobject]);


let set = new java.util.LinkedHashSet();
set.add(3);
set.add('foo');
set.add(true);
set.add(jobject);

assertEquals(set.size(), 4);
assertTrue(set.contains(3));
assertTrue(set.contains('foo'));
assertTrue(set.contains(true));
assertTrue(set.contains(jobject));

array = [];
for (let e of set) {
    array.push(e);
}
assertEqualArrays(array, [3, 'foo', true, jobject]);

// for..of on java byte array
let jstring = new java.lang.String('abc');
let bytes = jstring.bytes;

array = [];
for (let i of bytes) {
    array.push(i);
}
assertEqualArrays(array, [97, 98, 99]);

