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



//  Tests for Java nio ByteBuffer <-> ECMAScript ArrayBuffer conversion support.

const Assert = org.junit.Assert;

function asByteBufferTest() {
    // create an ArrayBuffer and populate values in script
    const ab = new ArrayBuffer(8);
    var view = new Int32Array(ab);
    view[0] = 34; view[1] = 44;

    // access ArrayBuffer as nio Buffer
    const bbuf = Java.asByteBuffer(ab);
    const ib = bbuf.asIntBuffer();
    Assert.assertTrue(ib.get() == 34);
    Assert.assertTrue(ib.get() == 44);

    ib.rewind();

    // write into ByteBuffer
    ib.put(0, 445);
    ib.put(1, 555);

    // see it from ArrayBuffer!
    Assert.assertTrue(view[0] == 445);
    Assert.assertTrue(view[1] == 555);
}

asByteBufferTest();

const ByteOrder = java.nio.ByteOrder;
const ByteBuffer = java.nio.ByteBuffer;

function asArrayBufferTest() {
    // create a java ByteBuffer and populate values
    const bb = ByteBuffer.allocateDirect(12);
    bb.order(ByteOrder.nativeOrder());
    const ib = bb.asIntBuffer();
    ib.put(1);
    ib.put(12);
    ib.put(123);

    // create ArrayBuffer fom given ByteBuffer and expose as var
    const ab = Java.asArrayBuffer(bb);

    Assert.assertTrue(ab instanceof ArrayBuffer);
    const ia = new Int32Array(ab);
    Assert.assertTrue(ia[0] == 1);
    Assert.assertTrue(ia[1] == 12);
    Assert.assertTrue(ia[2] == 123);

    // write into array buffer from script
    ia[0] = 32; ia[1] = -126; ia[2] = 793;

    // read script updated values from Java ByteBuffer
    ib.rewind();
    Assert.assertTrue(ib.get() == 32);
    Assert.assertTrue(ib.get() == -126);
    Assert.assertTrue(ib.get() == 793);
}

asArrayBufferTest();
