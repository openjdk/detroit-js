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

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSObject;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for V8 ScriptEngine's AutoCloseable.close method.
 *
 * @test
 * @run testng CloseTest
 */
@SuppressWarnings("javadoc")
public class CloseTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @Test
    public void evalAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        ((AutoCloseable)e).close();
        boolean gotISE = false;
        try {
            e.eval("print('hello)");
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }

        assertTrue(gotISE);
    }

    @Test
    public void compileAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        ((AutoCloseable)e).close();
        boolean gotISE = false;
        try {
            ((Compilable)e).compile("print('hello)");
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }

        assertTrue(gotISE);
    }

    @Test
    public void invokeFunctionAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        ((AutoCloseable)e).close();
        boolean gotISE = false;
        try {
            System.out.println(((Invocable)e).invokeFunction("parseInt", "42"));
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }

        assertTrue(gotISE);
    }

    @Test
    public void invokeMethodAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Object obj = e.eval("({ func: function(x) { print('func:' + x) } })");
        ((AutoCloseable)e).close();
        boolean gotISE = false;
        try {
            ((Invocable)e).invokeMethod(obj, "func", "hello");
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }

        assertTrue(gotISE);
    }

    @Test
    public void getInterfaceAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Object obj = e.eval("({ run: function(x) { print('run method') } })");
        ((AutoCloseable)e).close();
        boolean gotISE = false;
        try {
            Runnable r = ((Invocable)e).getInterface(obj, Runnable.class);
            r.run();
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }

        assertTrue(gotISE);
    }

    @Test
    public void evalCompiledScriptAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        CompiledScript cs = ((Compilable)e).compile("print('from compiled script')");
        ((AutoCloseable)e).close();
        boolean gotISE = false;
        try {
            cs.eval();
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }

        assertTrue(gotISE);
    }

    @Test
    public void invokeInterfaceAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Object obj = e.eval("({ run: function(x) { print('run method') } })");
        Runnable r = ((Invocable)e).getInterface(obj, Runnable.class);
        ((AutoCloseable)e).close();
        boolean gotISE = false;
        try {
            r.run();
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }

        assertTrue(gotISE);
    }

    @Test
    public void accessJSObjectAfterClose() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        JSObject obj = (JSObject)e.eval("({ x: 42, run: function(x) { print('run method:' + x) } })");
        ((AutoCloseable)e).close();

        boolean gotISE = false;
        try {
            System.out.println("obj.x = " + obj.getMember("x"));
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }
        assertTrue(gotISE, "JSObject.getMember succeeded");

        gotISE = false;
        try {
            obj.setMember("x", 54);
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }
        assertTrue(gotISE, "JSObject.setMember succeeded!");

        gotISE = false;
        try {
            obj.callMember("run", "hello");
        } catch (IllegalStateException ise) {
            System.err.println("Got expected exception: " + ise);
            gotISE = true;
        }
        assertTrue(gotISE, "JSObject.callMember succeeded!");
    }
}
