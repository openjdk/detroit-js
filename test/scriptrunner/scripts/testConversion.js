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


let System = Java.type('java.lang.System');
assertEquals(System.toString(), '[JavaClass java.lang.System]');
assertEquals(System.setProperty('jvmv8-test-property', 'ok'), null);
assertEquals(System.getProperty('jvmv8-test-property'), 'ok');
assertEquals(System.clearProperty('jvmv8-test-property'), 'ok');
assertEquals(System.getProperty('jvmv8-test-property'), null);
System.out.println('Printing to System.out');

let JavaString = Java.type('java.lang.String');
assertEquals(JavaString.valueOf(5), '5');
assertEquals(JavaString.valueOf(5.5), '5.5');
assertEquals(JavaString.valueOf([1, 2, 3]), '1,2,3');
assertEquals(JavaString.valueOf(1000000000000000000), '1000000000000000000');

let s = new JavaString('foo');
assertEquals(typeof s, 'object');
assertEquals(s.length(), 3);
assertEquals(s.toString(), 'foo');

let b = s.getBytes();
assertEquals(typeof b, 'object');
assertEquals(b.length, 3);
assertEquals(b[0], 'foo'.charCodeAt(0));
assertEquals(b[1], 'foo'.charCodeAt(1));
assertEquals(b[2], 'foo'.charCodeAt(2));

let s2 = new JavaString(b);
assertEquals(s2.equals(s), true);
assertEquals(s2.toString(), 'foo');
assertEquals(s2.subSequence(1, 3).charAt(1), 'o');

let CharArray = Java.type('char[]');
let chars = new CharArray(6);
s.getChars(0, 3, chars, 0);
new JavaString('foobar').getChars(3, 6, chars, 3);
assertEquals(Java.from(chars).join(''), 'foobar');
assertEquals(new JavaString(chars, 0, 6).toString(), 'foobar');

let StringBuilder = Java.type('java.lang.StringBuilder');
let sb = new StringBuilder();
sb.append(1);
sb.append(5.5);
sb.append(1000000000000000000);
sb.append('hello');
sb.append(s);
sb.append(sb);
assertEquals(sb.toString(), '15.51000000000000000000hellofoo15.51000000000000000000hellofoo');

// java packages
assertEquals(java.lang.String, JavaString);
assertEquals(Packages.java.lang.String, JavaString);
assertEquals(java.lang.System, System);
assertEquals(Packages.java.lang.System, System);

function testPackagePropertyDescriptor(desc) {
    assertEquals(desc.enumerable, false);
    assertEquals(desc.writable, true);
    assertEquals(desc.configurable, true);
}

testPackagePropertyDescriptor(Object.getOwnPropertyDescriptor(this, 'com'));
testPackagePropertyDescriptor(Object.getOwnPropertyDescriptor(this, 'edu'));
testPackagePropertyDescriptor(Object.getOwnPropertyDescriptor(this, 'java'));
testPackagePropertyDescriptor(Object.getOwnPropertyDescriptor(this, 'javafx'));
testPackagePropertyDescriptor(Object.getOwnPropertyDescriptor(this, 'javax'));
testPackagePropertyDescriptor(Object.getOwnPropertyDescriptor(this, 'org'));
testPackagePropertyDescriptor(Object.getOwnPropertyDescriptor(this, 'Packages'));

// varargs support
assertEquals(JavaString.format('foo'), 'foo');
assertEquals(JavaString.format('%1$d %2$d %3$d', 1, 2, 3), '1 2 3');
assertEquals(JavaString.format('%s %d', 'x', 10), 'x 10');
// same with JS array as last arg
assertEquals(JavaString.format('foo', []), 'foo');
assertEquals(JavaString.format('%1$d %2$d %3$d', [1, 2, 3]), '1 2 3');
assertEquals(JavaString.format('%s %d', ['x', 10]), 'x 10');

