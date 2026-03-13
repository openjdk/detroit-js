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



let Model = Packages.models.OverloadSelection;

assertEquals(Model.overloadedMethod(true), 'boolean: true');
assertEquals(Model.overloadedMethod(false), 'boolean: false');
assertEquals(Model.overloadedMethod(0), 'int: 0');
assertEquals(Model.overloadedMethod(1), 'int: 1');
assertEquals(Model.overloadedMethod(-0x80000000), 'int: -2147483648');
assertEquals(Model.overloadedMethod(0x7fffffff), 'int: 2147483647');
assertEquals(Model.overloadedMethod(-0x80000001), 'long: -2147483649');
assertEquals(Model.overloadedMethod(0x80000000), 'long: 2147483648');
assertEquals(Model.overloadedMethod(3000000000), 'long: 3000000000');
assertEquals(Model.overloadedMethod('a'), 'char: a');
assertEquals(Model.overloadedMethod(2.1), 'double: 2.1');
assertEquals(Model.overloadedMethod(1e30), 'double: 1.0E30');
assertEquals(Model.overloadedMethod('foo'), 'String: foo');
// TODO: should be 'Object: null'
assertEquals(Model.overloadedMethod(null), 'String: null');
assertEquals(Model["overloadedMethod(java.lang.Object)"](null), 'Object: null');
assertEquals(Model["overloadedMethod(java.lang.String)"](null), 'String: null');
// TODO: should be 'Object: undefined'
assertEquals(Model.overloadedMethod(undefined), 'String: undefined');
assertEquals(Model.overloadedMethod(new java.util.HashMap()), 'Object: {}');
assertEquals(Model.overloadedMethod({}), 'Object: [object Object]');
assertEquals(Model.overloadedMethod(1, 2, 3), 'Object[]: [1, 2, 3]');


assertEquals(Model.overloadedVarArgsMethod('foo'), 'String[]: [foo]');
assertEquals(Model.overloadedVarArgsMethod(new java.io.File('bar')), 'File[]: [bar]');
assertEquals(Model.overloadedVarArgsMethod('foo', 'bar'), 'String[]: [foo, bar]');
assertEquals(Model.overloadedVarArgsMethod(new java.io.File('bar'), new java.io.File('foo')), 'File[]: [bar, foo]');
assertEquals(Model.overloadedVarArgsMethod(Java.to(['foo', 'bar'], Java.type('java.lang.String[]'))), 'String[]: [foo, bar]');
assertEquals(Model.overloadedVarArgsMethod(Java.to([new java.io.File('bar'), new java.io.File('foo')], Java.type('java.io.File[]'))), 'File[]: [bar, foo]');
