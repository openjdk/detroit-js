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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static javax.script.ScriptContext.ENGINE_SCOPE;

import org.openjdk.engine.javascript.JSObject;
import javax.script.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for JSR-223 script engine for V8.
 *
 * @test
 * @run testng BasicTest
 */

public class BasicTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    static String script1 =
"var a = 10;\n" +
"var b = 20;\n" +
"var c = a + b;\n" +
"c;\n";
    static String script2 =
"var a = 'abd';\n" +
"var b = 'def';\n" +
"var c = a + b;\n" +
"c;\n";
    static String script3 =
"var c = {a:10, b:20}\n" +
"c;\n";
    static String script4 =
"var c = [1, 2, 3, 4];\n" +
"c;\n";
    static String script5 =
"var c = function a() {};\n" +
"c;\n";
    static String script6 =
"var c = undefined;\n" +
"c;\n";
    static String script7 =
"var c = {};\n" +
"c.a;\n";
    static String script8 =
"var a = 10.1;\n" +
"var b = 20.1;\n" +
"var c = a + b;\n" +
"c;\n";
    static String script9 =
"var a = 10.1;\n" +
"var b = 20.1;\n" +
"var c = a == b;\n" +
"c;\n";

    static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    static final ScriptEngine engine = scriptEngineManager.getEngineByName(ENGINE);

    @DataProvider(name = "scripts")
    public static Object[][] scriptTests() {
      return new Object[][] {
          {  script1, "Integer" }, { script2, "String" }, { script3, "V8Object" },
          { script4, "V8Array" }, {  script5, "V8Function" }, { script6, "V8Undefined" },
          { script7, "V8Undefined" }, { script8, "Double" }, {  script9, "Boolean" }
      };
    }

    @Test(dataProvider = "scripts")
    public void test1(String script, String resultClassName) throws ScriptException {
        Object result = engine.eval(script);
        assertNotNull(result);
        System.out.println(result.getClass() + ": " + result);
        assertEquals(result.getClass().getSimpleName(), resultClassName);
    }

    @Test
    public void test2() throws ScriptException {
        Object object = engine.eval("var x = {a: 10, b: 20, c: 30, f: function() { } };\nx;\n");
        assertNotNull(object);
        System.out.println(object.getClass() + ": " + object);

        Object result;
        result = ((JSObject)object).getMember("b");
        JSObject func = (JSObject) ((JSObject)object).getMember("f");
        func.call(object);

        JSObject jsObject = (JSObject)engine.eval("Object");
        System.out.println(jsObject.newObject());

        JSObject myConstructor = (JSObject)engine.eval("function func(val) { this.foo = val; }; func");
        JSObject myObj = (JSObject) myConstructor.newObject(42);
        System.out.println("obj.foo is " + myObj.getMember("foo"));
        System.out.println("myObj's constructor: " + myObj.getClassName());
        if (result != null) {
            System.out.println(result.getClass() + ": " + result);
        } else {
            System.out.println("null");
        }
    }

    @Test
    public void test3() {
        boolean caughtException = false;
        try {
            engine.eval("var x = {a: 10, b: 20, c: 30;\nx;\n");
        } catch (ScriptException se) {
            caughtException = true;
            System.out.println("Got expected exception: " + se.getMessage());
        }
        assertTrue(caughtException);
    }

    @Test
    public void test4() {
        boolean caughtException = false;
        try {
            engine.eval("var x = {a: 10, b: 20, c: 30};\nx.f();\n");
        } catch (ScriptException se) {
            caughtException = true;
            System.out.println("Got expected exception: " + se.getMessage());
        }
        assertTrue(caughtException);
    }

    @Test
    public void test5() throws ScriptException {
        Object object = engine.eval("var obj = {}; obj");

        Object result;
        JSObject obj = (JSObject)object;
        obj.setMember("x", "1234");
        assertEquals(obj.getMember("x"), "1234");
    }

    @Test
    public void test11() throws ScriptException {
        Bindings bindings = engine.getBindings(ENGINE_SCOPE);
        bindings.put("x", 1234);
        bindings.put("y", 5678);

        engine.eval("var z = x + y;");
        System.out.println(bindings.get("z"));
        assertEquals(((Number)bindings.get("z")), 1234 + 5678);
    }

    @Test
    public void test12() throws ScriptException {
        ScriptContext context = engine.getContext();
        Bindings bindings = context.getBindings(ENGINE_SCOPE);
        bindings.put("x", 1234);
        bindings.put("y", 5678);

        engine.eval("var z = x + y;", context);
        System.out.println(bindings.get("z"));
        assertEquals(((Number)bindings.get("z")), 1234 + 5678);
    }

    @Test
    public void test13() throws ScriptException {
        ScriptContext context = engine.getContext();
        Bindings bindings = engine.createBindings();
        context.setBindings(bindings, ENGINE_SCOPE);
        bindings.put("x", 1234);
        bindings.put("y", 5678);

        engine.eval("var z = x + y;", context);
        System.out.println(bindings.get("z"));
        assertEquals(((Number)bindings.get("z")), 1234 + 5678);
    }

    @Test
    public void test14() throws NoSuchMethodException, ScriptException {
        engine.eval("function test(a, b) { return a + b; }");
        Object result = ((Invocable)engine).invokeFunction("test", "abc", "def");
        System.out.println(result);
        assertEquals(result, "abcdef");
    }

    @Test
    public void test15() throws NoSuchMethodException, ScriptException {
        engine.eval("function test(a) { return a.x; }");
        boolean caughtException = false;
        try {
            ((Invocable)engine).invokeFunction("test", null, null);
        } catch (ScriptException se) {
            caughtException = true;
            System.out.println("Got expected exception: " + se.getMessage());
        }
        assertTrue(caughtException);
    }

    @Test
    public void test16() throws ScriptException, NoSuchMethodException {
        boolean caughtException = false;
        try {
            engine.eval("function test() { test(); }");
            ((Invocable)engine).invokeFunction("test");
        } catch (ScriptException se) {
            caughtException = true;
            System.out.println("Got expected exception: " + se.getMessage());
        }
        assertTrue(caughtException);
    }

    @Test
    public void test20() throws ScriptException {
        ScriptEngine engine = scriptEngineManager.getEngineByName("v8");
        String script = "var ArrayList = Java.type('java.util.ArrayList');\n" +
                            "var list = new ArrayList;\n" +
                            "list.add('a');\n" +
                            "list.add('b');\n" +
                            "list.add('c');\n" +
                            "list.toString();\n";
        Object result = engine.eval(script);
        System.out.println(result);
        assertEquals(result.toString(), "[a, b, c]");
    }
}
