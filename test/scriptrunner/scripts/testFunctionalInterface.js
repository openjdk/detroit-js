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



function testCallable(c) {
    assertTrue(c instanceof java.util.concurrent.Callable);
    assertTrue(typeof c === 'function');
    assertEquals(c(), 'ok');
    assertEquals(c.call(), 'ok');
}


testCallable(new java.util.concurrent.Callable(function() { return 'ok' }));
testCallable(new java.util.concurrent.Callable({ call: function() { return 'ok' }}));

let cmp = new java.util.Comparator(function(a, b) { return a < b ? -1 : a > b ? 1 : 0 });

assertEquals(cmp('a', 'b'), -1);
assertEquals(cmp('b', 'a'), 1);
assertEquals(cmp('a', 'a'), 0);
assertEquals(cmp.compare('a', 'b'), -1);
assertEquals(cmp.compare('b', 'a'), 1);
assertEquals(cmp.compare('a', 'a'), 0);

let set = new java.util.TreeSet(cmp);
set.add('b');
set.add('z');
set.add('x');
set.add('y');
set.add('c');
set.add('a');

assertEquals(set.toString(), '[a, b, c, x, y, z]');

