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

import org.openjdk.engine.javascript.V8Undefined;
import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.V8ScriptCompiler;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for V8ScriptCompiler interface.
 *
 * @test
 * @run testng ScriptCompilerTest
 */
@SuppressWarnings("javadoc")
public class ScriptCompilerTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @Test
    public void compileFunctionArgumentsTest() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);
        V8ScriptCompiler compiler = (V8ScriptCompiler)e;

        JSObject function = compiler.compileFunction("return x*y", new String[] { "x", "y" }, null);
        assertEquals(((Number)function.call(V8Undefined.INSTANCE, 45, 34)).intValue(), 45*34);
    }

    @Test
    public void compileFunctionExtensionsTest() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");
        V8ScriptCompiler compiler = (V8ScriptCompiler)e;
        JSObject java = (JSObject)e.eval("Java");
        JSObject function = compiler.compileFunction(
            "return new (type('java.util.HashMap'))()",
            null, new JSObject[] { java });

        assertTrue(function.call(V8Undefined.INSTANCE) instanceof java.util.HashMap);
    }

    @Test
    public void compileFunctionArgumentsExtensionsTest() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);
        V8ScriptCompiler compiler = (V8ScriptCompiler)e;

        JSObject mathObj = (JSObject)e.eval("Math");
        JSObject function = compiler.compileFunction("return PI*r*r", new String[] { "r" }, new JSObject[] { mathObj });

        assertEquals(((Number)function.call(V8Undefined.INSTANCE, "2")).doubleValue(), Math.PI*2.0*2.0, Double.MIN_VALUE);
    }
}
