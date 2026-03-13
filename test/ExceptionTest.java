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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.V8ScriptException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for ECMAScript exception throw and Java exception translation.
 *
 * @test
 * @run testng ExceptionTest
 */
@SuppressWarnings("javadoc")
public class ExceptionTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @Test
    public void ecmaErrorObjectTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        boolean caughtException = false;
        try {
            e.eval("throw new Error('this is an error')");
        } catch (V8ScriptException ex) {
            caughtException = true;
            JSObject jsObj = (JSObject)ex.getEcmaError();
            assertEquals(jsObj.getClassName(), "Error");
            assertEquals(jsObj.getMember("message").toString(), "this is an error");
        }
        assertTrue(caughtException);
    }

    @Test
    public void errorStackPropertyTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        e.put(ScriptEngine.FILENAME, "test.js");

        boolean caughtException = false;
        try {
            e.eval("throw new Error('this is an error')");
        } catch (V8ScriptException ex) {
            caughtException = true;
            JSObject jsObj = (JSObject)ex.getEcmaError();
            String stack = (String)jsObj.getMember("stack");
            assertTrue(stack.contains("Error: this is an error"));
            assertTrue(stack.contains("at test.js:1:7"));
        }
        assertTrue(caughtException);
    }

    @Test
    public void primitiveThrowTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        boolean caughtException = false;
        try {
            e.eval("throw 2");
        } catch (V8ScriptException ex) {
            caughtException = true;
            assertEquals(2, ((Number)ex.getEcmaError()).intValue());
        }
        assertTrue(caughtException);

        caughtException = false;
        try {
            e.eval("throw 'hello'");
        } catch (V8ScriptException ex) {
            caughtException = true;
            assertEquals("hello", ex.getEcmaError());
        }
        assertTrue(caughtException);

        caughtException = false;
        try {
            e.eval("throw null");
        } catch (V8ScriptException ex) {
            caughtException = true;
            assertNull(ex.getEcmaError());
        }
    }

    @Test
    public void evalSyntaxErrorTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        boolean caughtException = false;
        try {
            e.eval("eval('x***')");
        } catch (V8ScriptException ex) {
            caughtException = true;
            JSObject jsObj = (JSObject)ex.getEcmaError();
            assertEquals("SyntaxError", jsObj.getMember("name"));
            String msg = (String)jsObj.getMember("message");
            assertTrue(msg.contains("Unexpected token '*'"));
        }
        assertTrue(caughtException);
    }

    @Test
    public void exceptionLocationTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        e.put(ScriptEngine.FILENAME, "test.js");
        boolean caughtException = false;
        try {
            // Do *not* remove/modify newlines and spaces!
            // Line number and column number asserts depend on these!
            e.eval("\n\n  func()");
        } catch (V8ScriptException ex) {
            caughtException = true;
            assertEquals(ex.getFileName(), "test.js");
            assertEquals(ex.getLineNumber(), 3);
            assertEquals(ex.getColumnNumber(), 2);
            assertEquals(ex.getSourceLine(), "  func()");
        }
        assertTrue(caughtException);
    }

    @Test
    public void javaExceptionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        boolean caughtException = false;
        try {
            e.eval("java.lang.System.getProperty('')");
        } catch (V8ScriptException ex) {
            Object errorObj = ex.getEcmaError();
            assert(errorObj instanceof JSObject);
            Object jExp = ((JSObject)errorObj).getMember("javaException");
            assert(jExp instanceof IllegalArgumentException);
            caughtException = true;
        }

        assertTrue(caughtException);
    }

    @Test
    public void getScriptFramesTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        boolean caughtException = false;
        e.put(ScriptEngine.FILENAME, "myscript.js");
        try {
            // Do *not* remove/modify newlines and spaces!
            // Line number and column number asserts depend on these!
            e.eval("function func() { foo() }\n\n;func();");
        } catch (V8ScriptException se) {
            StackTraceElement[] frames = se.getScriptFrames();
            assertEquals(frames.length, 2);
            assertEquals(frames[0].getFileName(), "myscript.js");
            assertEquals(frames[1].getFileName(), "myscript.js");
            assertEquals(frames[0].getLineNumber(), 1);
            assertEquals(frames[1].getLineNumber(), 3);
            assertEquals(frames[0].getMethodName(), "func");
            assertEquals(frames[1].getMethodName(), "");

            System.out.println("Got stack trace using getScriptFrames API:");
            System.out.println(frames[0]);
            System.out.println(frames[1]);
            caughtException = true;
        }
        assertTrue(caughtException);
    }

}
