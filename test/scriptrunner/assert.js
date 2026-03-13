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


function shouldNotReachHere() {
    var e = new Error("Should not reach here!");
    print(e.stack);
    throw e;
}

function assertTrue(a) {
    if (a !== true) {
        throw new Error('Not true: ' + a);
    }
}

function assertFalse(a) {
    if (a) {
        throw new Error('Not false: ' + a);
    }
}

function assertEquals(a, b) {
    if (a !== b && !(a instanceof java.lang.Object && a.equals(b))) {
        throw new Error('Not equal: ' + a + ', ' + b);
    }
}

function assertEqualArrays(a, b) {
    if (a.length !== b.length) {
        throw new Error('Array length not equal: ' + a.length + ', ' + b.length);
    }
    for (let i = 0; i < a.length; i++) {
        if (a[i] !== b[i] && !(a[i] instanceof java.lang.Object && a[i].equals(b[i]))) {
            throw new Error('Elements at index ' + i + ' not equal: ' + a[i] + ', ' + b[i]);
        }
    }
}

function assertThrows(f) {
    try {
        f();
    } catch (e) {
        return;
    }
    throw new Error('Function did not throw');
}

function fail(msg) {
    throw new Error(msg);
}

