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


var s = new java.lang.String('abc');
assertEquals(s[0], 97);
assertEquals(s[1], 98);
assertEquals(s[2], 99);
assertEquals(s[3], undefined);
assertEquals(s[-1], undefined);


var l = new java.util.ArrayList();
l.add('a');
l.add('b');
l.add('c');
assertEquals(l[0], 'a');
assertEquals(l[1], 'b');
assertEquals(l[2], 'c');
l[0] = 'x';
l[1] = 'y';
l[2] = 'z';
assertEquals(l[0], 'x');
assertEquals(l[1], 'y');
assertEquals(l[2], 'z');

