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



let FieldHolder = Java.type('FieldHolder');
let jobj = new java.lang.Object();

FieldHolder.aStaticObject = "foo";
assertEquals(FieldHolder.aStaticObject, "foo");
FieldHolder.aStaticObject = 3;
assertEquals(FieldHolder.aStaticObject, 3);
FieldHolder.aStaticObject = jobj;
assertTrue(FieldHolder.aStaticObject.equals(jobj));

FieldHolder.aStaticBoolean = true;
assertEquals(FieldHolder.aStaticBoolean, true);

FieldHolder.aStaticByte = 3;
assertEquals(FieldHolder.aStaticByte, 3);

FieldHolder.aStaticShort = 3;
assertEquals(FieldHolder.aStaticShort, 3);

FieldHolder.aStaticChar = 'a';
assertEquals(FieldHolder.aStaticChar, 'a');

FieldHolder.aStaticInt = 3;
assertEquals(FieldHolder.aStaticInt,  3);

FieldHolder.aStaticLong = 3;
assertEquals(FieldHolder.aStaticLong, 3);

FieldHolder.aStaticFloat = 3.5;
assertEquals(FieldHolder.aStaticFloat, 3.5);

FieldHolder.aStaticDouble = 3.5;
assertEquals(FieldHolder.aStaticDouble, 3.5);

let fh = new FieldHolder();

fh.anObject = "foo";
assertEquals(fh.anObject, "foo");
fh.anObject = 3;
assertEquals(fh.anObject, 3);
fh.anObject = jobj;
assertTrue(fh.anObject.equals(jobj));

fh.aBoolean = true;
assertEquals(fh.aBoolean, true);

fh.aByte = 3;
assertEquals(fh.aByte, 3);

fh.aShort = 3;
assertEquals(fh.aShort, 3);

fh.aChar = 'a';
assertEquals(fh.aChar, 'a');

fh.anInt = 3;
assertEquals(fh.anInt, 3);

fh.aLong = 3;
assertEquals(fh.aLong, 3);

fh.aFloat = 3.5;
assertEquals(fh.aFloat, 3.5);

fh.aDouble = 3.5;
assertEquals(fh.aDouble, 3.5);






