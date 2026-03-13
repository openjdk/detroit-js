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


// basic tests for fork

function checkLoadAsync(src) {
    caughtError = false;
    try {
        fork(src);
    } catch (e) {
        print(e);
        assertTrue(e instanceof TypeError);
        caughtError = true;
    }
    assertTrue(caughtError);
}

checkLoadAsync(null);
checkLoadAsync(undefined);
checkLoadAsync("blah blah");

// check that the script runs on a different thread
var cf = fork({
    name: "x",
    script: "java.lang.Thread.currentThread()"
});

// thread names are different!
assertTrue(cf.get().toString() != java.lang.Thread.currentThread().toString());

// argument passing
cf = fork({
    name: 't',
    script: "var res =''; for(i in arguments) { res += arguments[i] }; res"
}, "hello", 54, true);

assertEquals(cf.get(), "hello54true");

// no object arguments!
cf = fork({
    name: 'y',
    script: "typeof arguments[0]"
}, new Object());

assertEquals(cf.get(), 'string');

// no object return value
cf = fork({
    name: 'z',
    script: "new Object()"
});

assertTrue(typeof cf.get() === 'string');

// check "Java" is defined
cf = fork({
    name: 'z1',
    script: "typeof Java"
});

assertTrue(cf.get() === 'object');

// fork with callback function argument
cf = fork(() => {
    return arguments[0] + arguments[1];
}, 45, 34);
assertEquals(cf.get(), 45 + 34);

var Executor = Java.type("java.util.concurrent.Executor");

// forkOnExecutor - same thread executor!
var cf = forkOnExecutor({
    name: "x",
    script: "java.lang.Thread.currentThread()"
}, new Executor({
    execute: function(r) { r.run() }
}));

assertTrue(cf.get().toString() == java.lang.Thread.currentThread().toString());

// forkOnExecutor - same thread executor, arguments
var cf = forkOnExecutor((x, y) => {
    return x + y
}, new Executor({
    execute: function(r) { r.run() }
}), 45, 37);

assertTrue(cf.get() == 45 + 37);
