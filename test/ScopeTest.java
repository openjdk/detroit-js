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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for jsr223 Bindings "scope" (engine, global scopes)
 * @test
 * @build ScopeTest
 * @run testng/othervm ScopeTest
 */
@SuppressWarnings("javadoc")
public class ScopeTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @Test
    public void createBindingsTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final Bindings b = e.createBindings();
        b.put("foo", 42.0);
        Object res = null;
        try {
            res = e.eval("foo == 42.0", b);
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        assertEquals(res, Boolean.TRUE);
    }

    @Test
    public void engineScopeTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final Bindings engineScope = e.getBindings(ScriptContext.ENGINE_SCOPE);

        // check few ECMA standard built-in global properties
        assertNotNull(engineScope.get("Object"));
        assertNotNull(engineScope.get("TypeError"));
        assertNotNull(engineScope.get("eval"));

        // can access via ScriptEngine.get as well
        assertNotNull(e.get("Object"));
        assertNotNull(e.get("TypeError"));
        assertNotNull(e.get("eval"));

        // Access by either way should return same object
        assertEquals(engineScope.get("Array"), e.get("Array"));
        assertEquals(engineScope.get("EvalError"), e.get("EvalError"));
        assertEquals(engineScope.get("undefined"), e.get("undefined"));

        // try exposing a new variable from scope
        engineScope.put("myVar", "foo");
        try {
            assertEquals(e.eval("myVar"), "foo");
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        // update "myVar" in script an check the value from scope
        try {
            e.eval("myVar = 'v8';");
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        // now check modified value from scope and engine
        assertEquals(engineScope.get("myVar"), "v8");
        assertEquals(e.get("myVar"), "v8");
    }

    @Test
    public void multiGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final Bindings b = e.createBindings();
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);

        try {
            final Object obj1 = e.eval("Object");
            final Object obj2 = e.eval("Object", newCtxt);
            Assert.assertNotEquals(obj1, obj2);
            Assert.assertNotNull(obj1);
            Assert.assertNotNull(obj2);
            Assert.assertEquals(obj1.toString(), obj2.toString());

            e.eval("x = 'hello'");
            e.eval("x = 'world'", newCtxt);
            Object x1 = e.getContext().getAttribute("x");
            Object x2 = newCtxt.getAttribute("x");
            Assert.assertNotEquals(x1, x2);
            Assert.assertEquals(x1, "hello");
            Assert.assertEquals(x2, "world");

            x1 = e.eval("x");
            x2 = e.eval("x", newCtxt);
            Assert.assertNotEquals(x1, x2);
            Assert.assertEquals(x1, "hello");
            Assert.assertEquals(x2, "world");

            final ScriptContext origCtxt = e.getContext();
            e.setContext(newCtxt);
            e.eval("y = new Object()");
            e.eval("y = new Object()", origCtxt);

            final Object y1 = origCtxt.getAttribute("y");
            final Object y2 = newCtxt.getAttribute("y");
            Assert.assertNotEquals(y1, y2);
            final Object yeval1 = e.eval("y");
            final Object yeval2 = e.eval("y", origCtxt);
            Assert.assertNotEquals(yeval1, yeval2);
            Assert.assertEquals("[object Object]", y1.toString());
            Assert.assertEquals("[object Object]", y2.toString());
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

    @Test
    public void userEngineScopeBindingsTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        e.eval("function func() {}");

        final ScriptContext newContext = new SimpleScriptContext();
        newContext.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
        // we are using a new bindings - so it should have 'func' defined
        final Object value = e.eval("typeof func", newContext);
        assertTrue(value.equals("undefined"));
    }

    @Test
    public void userEngineScopeBindingsNoLeakTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final ScriptContext newContext = new SimpleScriptContext();
        newContext.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
        e.eval("function foo() {}", newContext);

        // in the default context's ENGINE_SCOPE, 'foo' shouldn't exist
        assertTrue(e.eval("typeof foo").equals("undefined"));
    }

    @Test
    public void userEngineScopeBindingsRetentionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final ScriptContext newContext = new SimpleScriptContext();
        newContext.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
        e.eval("function foo() {}", newContext);

        // definition retained with user's ENGINE_SCOPE Binding
        assertTrue(e.eval("typeof foo", newContext).equals("function"));

        final Bindings oldBindings = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
        // but not in another ENGINE_SCOPE binding
        newContext.setBindings(e.createBindings(), ScriptContext.ENGINE_SCOPE);
        assertTrue(e.eval("typeof foo", newContext).equals("undefined"));

        // restore ENGINE_SCOPE and check again
        newContext.setBindings(oldBindings, ScriptContext.ENGINE_SCOPE);
        assertTrue(e.eval("typeof foo", newContext).equals("function"));
    }

    @Test
    public static void contextOverwriteTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final Bindings b = e.createBindings();
        b.put("context", "hello");
        b.put("foo", 32);
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        e.setContext(newCtxt);
        assertEquals(e.eval("context"), "hello");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    @Test
    public static void contextOverwriteInScriptTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        e.put("foo", 32);

        assertEquals(((Number)e.eval("foo")).intValue(), 32);
        assertEquals(e.eval("context = 'bar'"), "bar");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    @Test
    public static void engineOverwriteTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final Bindings b = e.createBindings();
        b.put("engine", "hello");
        b.put("foo", 32);
        final ScriptContext newCtxt = new SimpleScriptContext();
        newCtxt.setBindings(b, ScriptContext.ENGINE_SCOPE);
        e.setContext(newCtxt);
        assertEquals(e.eval("engine"), "hello");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    @Test
    public static void engineOverwriteInScriptTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        e.put("foo", 32);

        assertEquals(((Number)e.eval("foo")).intValue(), 32);
        assertEquals(e.eval("engine = 'bar'"), "bar");
        assertEquals(((Number)e.eval("foo")).intValue(), 32);
    }

    @Test
    public static void testGetInDifferentGlobals() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName(ENGINE);
        final String script = "foo";
        for (int index = 0; index < 25; index++) {
            final Bindings bindings = engine.createBindings();
            bindings.put("foo", index);
            final Number value = (Number)engine.eval(script, bindings);
            assertEquals(index, value.intValue());
        }
    }

    // with get/setAttribute methods insonsistent for GLOBAL_SCOPE
    @Test
    public void testGlobalScopeSearch() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final ScriptContext c = e.getContext();
        c.setAttribute("name1234", "value", ScriptContext.GLOBAL_SCOPE);
        assertEquals(c.getAttribute("name1234"), "value");
        assertEquals(c.getAttributesScope("name1234"),
            ScriptContext.GLOBAL_SCOPE);
    }

    // which doesn't completely conform to the spec regarding exceptions throwing
    @Test
    public void testScriptContext_NPE_IAE() throws Exception {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final ScriptContext c = e.getContext();
        try {
            c.getAttribute("");
            throw new AssertionError("should have thrown IAE");
        } catch (final IllegalArgumentException iae1) {}

        try {
            c.getAttribute(null);
            throw new AssertionError("should have thrown NPE");
        } catch (final NullPointerException npe1) {}

        try {
            c.getAttribute("", ScriptContext.ENGINE_SCOPE);
            throw new AssertionError("should have thrown IAE");
        } catch (final IllegalArgumentException iae2) {}

        try {
            c.getAttribute(null, ScriptContext.ENGINE_SCOPE);
            throw new AssertionError("should have thrown NPE");
        } catch (final NullPointerException npe2) {}

        try {
            c.removeAttribute("", ScriptContext.ENGINE_SCOPE);
            throw new AssertionError("should have thrown IAE");
        } catch (final IllegalArgumentException iae3) {}

        try {
            c.removeAttribute(null, ScriptContext.ENGINE_SCOPE);
            throw new AssertionError("should have thrown NPE");
        } catch (final NullPointerException npe3) {}

        try {
            c.setAttribute("", "value", ScriptContext.ENGINE_SCOPE);
            throw new AssertionError("should have thrown IAE");
        } catch (final IllegalArgumentException iae4) {}

        try {
            c.setAttribute(null, "value", ScriptContext.ENGINE_SCOPE);
            throw new AssertionError("should have thrown NPE");
        } catch (final NullPointerException npe4) {}

        try {
            c.getAttributesScope("");
            throw new AssertionError("should have thrown IAE");
        } catch (final IllegalArgumentException iae5) {}

        try {
            c.getAttributesScope(null);
            throw new AssertionError("should have thrown NPE");
        } catch (final NullPointerException npe5) {}
    }

    public static class RecursiveEval {
        private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("v8");
        private final Bindings engineBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

        public void program() throws ScriptException {
            final ScriptContext sc = new SimpleScriptContext();
            final Bindings global = new SimpleBindings();
            sc.setBindings(global, ScriptContext.GLOBAL_SCOPE);
            sc.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
            global.put("text", "programText");
            String value = engine.eval("with(context) text", sc).toString();
            Assert.assertEquals(value, "programText");
            engine.put("program", this);
            engine.eval("program.method()");
            // eval again from here!
            value = engine.eval("with(context) text", sc).toString();
            Assert.assertEquals(value, "programText");
        }

        public void method() throws ScriptException {
            // a context with a new global bindings, same engine bindings
            final ScriptContext sc = new SimpleScriptContext();
            final Bindings global = new SimpleBindings();
            sc.setBindings(global, ScriptContext.GLOBAL_SCOPE);
            sc.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
            global.put("text", "methodText");
            final String value = engine.eval("with(context) text", sc).toString();
            Assert.assertEquals(value, "methodText");
        }
    }

    // was called from a previous engine.eval results in wrong
    // ScriptContext being used.
    @Test
    public void recursiveEvalCallScriptContextTest() throws ScriptException {
        new RecursiveEval().program();
    }

    @Test
    public void invokeFunctionInGlobalScopeTest() throws Exception {
         final ScriptEngine engine = new ScriptEngineManager().getEngineByName("v8");
         final ScriptContext ctxt = engine.getContext();

         // define a function called "func"
         engine.eval("func = function() { return 42 }");

         // move ENGINE_SCOPE Bindings to GLOBAL_SCOPE
         ctxt.setBindings(ctxt.getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.GLOBAL_SCOPE);

         // create a new Bindings and set as ENGINE_SCOPE
         ctxt.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

         // define new function that calls "func" now in GLOBAL_SCOPE
         engine.eval("newfunc = function() { with(context) { return func() } }");

         // call "newfunc" and check the return value
         final Object value = ((Invocable)engine).invokeFunction("newfunc");
         assertTrue(((Number)value).intValue() == 42);
    }


    // variant of above that replaces default ScriptContext of the engine with a fresh instance!
    @Test
    public void invokeFunctionInGlobalScopeTest2() throws Exception {
         final ScriptEngine engine = new ScriptEngineManager().getEngineByName("v8");

         // create a new ScriptContext instance
         final ScriptContext ctxt = new SimpleScriptContext();
         // set it as 'default' ScriptContext
         engine.setContext(ctxt);

         // create a new Bindings and set as ENGINE_SCOPE
         ctxt.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

         // define a function called "func"
         engine.eval("func = function() { return 42 }");

         // move ENGINE_SCOPE Bindings to GLOBAL_SCOPE
         ctxt.setBindings(ctxt.getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.GLOBAL_SCOPE);

         // create a new Bindings and set as ENGINE_SCOPE
         ctxt.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

         // define new function that calls "func" now in GLOBAL_SCOPE
         engine.eval("newfunc = function() { with(context) { return func() } }");

         // call "newfunc" and check the return value
         final Object value = ((Invocable)engine).invokeFunction("newfunc");
         assertTrue(((Number)value).intValue() == 42);
    }

    // When we create a Global for a non-default ScriptContext that needs one keep the
    // ScriptContext associated with the Global so that invoke methods work as expected.
    @Test
    public void invokeFunctionWithCustomScriptContextTest() throws Exception {
        final ScriptEngine engine = new ScriptEngineManager().getEngineByName(ENGINE);

        // create an engine and a ScriptContext, but don't set it as default
        final ScriptContext scriptContext = new SimpleScriptContext();
        scriptContext.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        // Set some value in the context
        scriptContext.setAttribute("myString", "foo", ScriptContext.ENGINE_SCOPE);

        // Evaluate script with custom context and get back a function
        final String script = "(function (c) { return myString.indexOf(c); })";
        final CompiledScript compiledScript = ((Compilable)engine).compile(script);
        final Object func = compiledScript.eval(scriptContext);

        // Invoked function should be able to see context it was evaluated with
        final Object result = ((Invocable) engine).invokeMethod(func, "call", func, "o", null);
        assertTrue(((Number)result).intValue() == 1);
    }

    @Test
    public void globalScopeSimpleBindingsTest() throws Exception {
        final ScriptEngine engine = new ScriptEngineManager().getEngineByName("v8");
        ScriptContext context = engine.getContext();
        SimpleBindings bindings = new SimpleBindings();
        bindings.put("foo", 42);
        context.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
        // use context.<prop_name>
        assertEquals(((Number)engine.eval("context.foo")).intValue(), 42);
        // use 'with'
        assertEquals(((Number)engine.eval("context.foo")).intValue(), 42);

        // modify foo
        engine.eval("context.foo = 'hello'");
        assertEquals(bindings.get("foo"), "hello");

        // modify foo using 'with'
        engine.eval("with(context) foo = 'world'");
        assertEquals(bindings.get("foo"), "world");

        // delete foo
        assertTrue(bindings.containsKey("foo"));
        assertTrue((Boolean)engine.eval("with(context) delete foo"));
        // 'foo' should have been removed from bindings!
        assertFalse(bindings.containsKey("foo"));
    }
}
