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

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.engine.javascript.JSFactory;
import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.V8ExecutionControl;
import org.openjdk.engine.javascript.V8ScriptException;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for V8ExecutionControl interface.
 *
 * @test
 * @run testng ExecutionControlTest
 */
@SuppressWarnings("javadoc")
public class ExecutionControlTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @Test
    public void terminationTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        boolean terminated = false;
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch(InterruptedException ie) {}
            ((V8ExecutionControl)e).terminateExecution();
        }).start();
        try {
            e.eval("while(true);");
        } catch (ScriptException ex) {
            terminated = ex.getMessage().indexOf("Script Terminated") != -1;
        }
        assertTrue(terminated);
    }

    @Test
    public void interruptRequestTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        boolean terminated = false;
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch(InterruptedException ie) {}

           // schedule interrupt on long running script!
           ((V8ExecutionControl)e).requestInterrupt(() -> {
                interrupted.set(true);
                ((V8ExecutionControl)e).terminateExecution();
           });
        }).start();
        try {
            e.eval("while(true);");
        } catch (ScriptException ex) {
            terminated = ex.getMessage().indexOf("Script Terminated") != -1;
        }
        assertTrue(interrupted.get());
        assertTrue(terminated);
    }

    @Test
    public void microtaskTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        V8ExecutionControl control = (V8ExecutionControl)e;
        e.put("x", 10);
        JSFunction func = (JSFunction)e.eval("(function() { x *= 3; })");
        control.enqueueMicrotask(func);
        e.eval("const x = 10");

        // microtask should have run at the end of eval and multiplied x by 3!
        assertEquals(((Number)e.get("x")).intValue(), 30);
    }

    @Test
    public void throwExceptionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        V8ExecutionControl control = (V8ExecutionControl)e;

        b.put("f", JSFunction.supplier(()-> control.throwException("hello")));
        boolean caughtException = false;
        try {
            e.eval("f()");
        } catch (V8ScriptException se) {
            caughtException = true;
            String str = (String)se.getEcmaError();
            assertTrue(str.contains("hello"));
        }
        assertTrue(caughtException);

        b.put("fun", JSFunction.supplier(() ->
            control.throwException(fac.newTypeError("wrong type!"))));
        caughtException = false;
        try {
            e.eval("fun()");
        } catch (V8ScriptException se) {
            caughtException = true;
            JSObject exp = (JSObject) se.getEcmaError();
            assertEquals(exp.getMember("name"), "TypeError");
            assertEquals(exp.getMember("message"), "wrong type!");
        }
        assertTrue(caughtException);

        JSObject rangeError = fac.newRangeError("too big");
        assertEquals(rangeError.getMember("name"), "RangeError");
        assertEquals(rangeError.getMember("message"), "too big");

        // define a function that throws that rangeError
        b.put("func", JSFunction.consumer(x -> control.throwException(rangeError)));

        caughtException = false;
        try {
            e.eval("func()");
        } catch (V8ScriptException se) {
            caughtException = true;
            JSObject exp = (JSObject) se.getEcmaError();
            assertEquals(exp.getMember("name"), "RangeError");
            assertEquals(exp.getMember("message"), "too big");
        }
        assertTrue(caughtException);
    }
}
