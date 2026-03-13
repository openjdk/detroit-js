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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSFactory;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for Java nio ByteBuffer <-> ECMAScript ArrayBuffer conversion support.
 *
 * @test
 * @run testng NioSupportTest
 */
@SuppressWarnings("javadoc")
public class NioSupportTest {
    private static final ScriptEngineManager manager = new ScriptEngineManager();
    private static final ScriptEngine engine = manager.getEngineByName("v8");

    @Test
    public void newByteBufferTest() throws ScriptException {
        // fresh global scope for new test
        engine.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)engine.getBindings(ScriptContext.ENGINE_SCOPE);

        // create an ArrayBuffer and populate values in script
        JSObject obj = (JSObject) engine.eval(
            "var b = new ArrayBuffer(8); \n" +
            "var view = new Int32Array(b); \n" +
            "view[0] = 34; view[1] = 44; \n" +
            "b");

        // access ArrayBuffer as nio Buffer
        ByteBuffer buf = fac.newByteBuffer(obj);
        IntBuffer ib = buf.asIntBuffer();
        assertEquals(ib.get(), 34);
        assertEquals(ib.get(), 44);

        ib.rewind();

        // write into ByteBuffer
        ib.put(0, 445);
        ib.put(1, 555);

        // see it from ArrayBuffer!
        assertEquals((int)engine.eval("view[0]"), 445);
        assertEquals((int)engine.eval("view[1]"), 555);
    }

    @Test
    public void newArrayBufferTest() throws ScriptException {
        // fresh global scope for new test
        Bindings bindings = engine.createBindings();
        JSFactory fac = (JSFactory)bindings;
        engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        // create a java ByteBuffer and populate values
        ByteBuffer bb = ByteBuffer.allocateDirect(12);
        bb.order(ByteOrder.nativeOrder());
        IntBuffer ib = bb.asIntBuffer();
        ib.put(1);
        ib.put(12);
        ib.put(123);

        // create ArrayBuffer fom given ByteBuffer and expose as var
        engine.put("ab", fac.newArrayBuffer(bb));

        assertTrue((boolean)engine.eval("ab instanceof ArrayBuffer"));
        engine.eval("var ia = new Int32Array(ab);");
        assertEquals((int)engine.eval("ia[0]"), 1);
        assertEquals((int)engine.eval("ia[1]"), 12);
        assertEquals((int)engine.eval("ia[2]"), 123);

        // write into array buffer from script
        engine.eval("ia[0] = 32; ia[1] = -126; ia[2] = 793;");

        // read script updated values from Java ByteBuffer
        ib.rewind();
        assertEquals(ib.get(), 32);
        assertEquals(ib.get(), -126);
        assertEquals(ib.get(), 793);
    }
}
