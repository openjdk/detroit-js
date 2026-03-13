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

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.V8Context;
import org.openjdk.engine.javascript.V8ScriptException;

/**
 * Tests for JSR-223 script engine for V8.
 *
 * @test
 * @build Window WindowEventHandler VariableArityTestInterface ScriptEngineTest
 * @run testng/othervm ScriptEngineTest
 */
@SuppressWarnings("javadoc")
public class ScriptEngineTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    private static void log(final String msg) {
        org.testng.Reporter.log(msg, true);
    }

    @Test
    public void argumentsTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        final String[] args = new String[] { "hello", "world" };
        try {
            e.put("arguments", args);
            final Object arg0 = e.eval("arguments[0]");
            final Object arg1 = e.eval("arguments[1]");
            assertEquals(args[0], arg0);
            assertEquals(args[1], arg1);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void argumentsWithTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        final String[] args = new String[] { "hello", "world" };
        try {
            e.put("arguments", args);
            final Object arg0 = e.eval("var imports = new JavaImporter(java.io); " +
                    " with(imports) { arguments[0] }");
            final Object arg1 = e.eval("var imports = new JavaImporter(java.util, java.io); " +
                    " with(imports) { arguments[1] }");
            assertEquals(args[0], arg0);
            assertEquals(args[1], arg1);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    // FIXME: @Test
    public void argumentsEmptyTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        try {
            assertEquals(e.eval("arguments instanceof Array"), true);
            assertEquals(e.eval("arguments.length == 0"), true);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void factoryTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        assertNotNull(e);

        final ScriptEngineFactory fac = e.getFactory();

        assertEquals(fac.getLanguageName(), "ECMAScript");
        assertEquals(fac.getParameter(ScriptEngine.NAME), ENGINE);
        assertEquals(fac.getLanguageVersion(), "ECMAScript 2025");
        assertEquals(fac.getEngineName(), "Oracle V8 No Java");
        assertEquals(fac.getOutputStatement("context"), "print(context)");
        assertEquals(fac.getProgram("print('hello')", "print('world')"), "print('hello');print('world');");
        assertEquals(fac.getParameter(ScriptEngine.NAME), ENGINE);

        boolean seenJS = false;
        for (final String ext : fac.getExtensions()) {
            if (ext.equals("js")) {
                seenJS = true;
            }
        }

        assertEquals(seenJS, true);
        final String str = fac.getMethodCallSyntax("obj", "foo", "x");
        assertEquals(str, "obj.foo(x)");

        boolean seenV8 = false;
        for (final String name : fac.getNames()) {
            switch (name) {
                case "v8": seenV8 = true; break;
                case "v8-no-java": seenV8 = true; break;
            default:
                break;
            }
        }
        assertTrue(seenV8);

        boolean seenAppJS = false, seenAppECMA = false, seenTextJS = false, seenTextECMA = false;
        for (final String mime : fac.getMimeTypes()) {
            switch (mime) {
                case "application/javascript": seenAppJS = true; break;
                case "application/ecmascript": seenAppECMA = true; break;
                case "text/javascript": seenTextJS = true; break;
                case "text/ecmascript": seenTextECMA = true; break;
            default:
                break;
            }
        }

        assertTrue(seenAppJS);
        assertTrue(seenAppECMA);
        assertTrue(seenTextJS);
        assertTrue(seenTextECMA);
    }

    @Test
    public void evalTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        e.put(ScriptEngine.FILENAME, "myfile.js");

        try {
            e.eval("const x = 42");
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }
        try {
            e.eval("const y = 'hello");
            fail("script exception expected");
        } catch (final ScriptException se) {
            assertEquals(se.getLineNumber(), 1);
            assertEquals(se.getColumnNumber(), 10);
            assertEquals(se.getFileName(), "myfile.js");
            // se.printStackTrace();
        }

        try {
            Object obj = e.eval("34 + 41");
            assertTrue(34.0 + 41.0 == ((Number)obj).doubleValue());
            obj = e.eval("z = 5");
            assertTrue(5.0 == ((Number)obj).doubleValue());
        } catch (final ScriptException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

    @Test
    public void compileTests() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        CompiledScript script = null;

        try {
            script = ((Compilable)e).compile("const x = 42");
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }

        try {
            script.eval();
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }

        // try to compile from a Reader
        try {
            script = ((Compilable)e).compile(new StringReader("const y = 42"));
        } catch (final ScriptException se) {
            fail(se.getMessage());
        }

        try {
            script.eval();
        } catch (final ScriptException | NullPointerException se) {
            se.printStackTrace();
            fail(se.getMessage());
        }
    }

    @Test
    public void compiledScriptFromDiffEngineTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName(ENGINE);
        final Compilable compilable = (Compilable) engine;
        final CompiledScript compiledScript = compilable.compile("foo");
        final ScriptEngine engine2 = m.getEngineByName(ENGINE);
        boolean seenException = false;
        try {
            compiledScript.eval(engine2.getContext());
        } catch (IllegalArgumentException iae) {
            seenException = true;
            System.err.println("Got Exception as expected");
            iae.printStackTrace();
        }
        assertTrue(seenException);
    }

    @Test
    public void compileAndEvalInDiffContextTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName(ENGINE);
        final Compilable compilable = (Compilable) engine;
        final CompiledScript compiledScript = compilable.compile("foo");
        engine.put("foo", 42);
        assertEquals(((Number)compiledScript.eval()).intValue(), 42);

        // eval CompiledScript with a passed Bindings
        Bindings binds = engine.createBindings();
        binds.put("foo", true);
        assertEquals(true, compiledScript.eval(binds));

        // eval CompiledScript with a passed ScriptContext
        final ScriptContext ctxt = new SimpleScriptContext();
        ctxt.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        ctxt.setAttribute("foo", "hello", ScriptContext.ENGINE_SCOPE);
        assertEquals(compiledScript.eval(ctxt), "hello");
    }

    @Test
    public void accessGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        try {
            e.eval("var x = 'hello'");
            assertEquals(e.get("x"), "hello");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void exposeGlobalTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        try {
            e.put("y", "foo");
            e.eval("const x = y");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void putGlobalFunctionTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        e.put("callable", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "callable was called";
            }
        });

        try {
            e.eval("print(callable.call())");
        } catch (final ScriptException exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowAlertTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("print(window.alert)");
            e.eval("window.alert('calling window.alert...')");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowLocationTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("print(window.location)");
            final Object locationValue = e.eval("window.getLocation()");
            assertEquals(locationValue, "http://localhost:8080/window");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowItemTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final Window window = new Window();

        try {
            e.put("window", window);
            final String item1 = (String)e.eval("window.item(65535)");
            assertEquals(item1, "ffff");
            final String item2 = (String)e.eval("window.item(255)");
            assertEquals(item2, "ff");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void windowEventTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final Window window = new Window();

        try {
            e.put("window", window);
            e.eval("window.onload = function() { print('window load event fired'); return true }");
            assertTrue((Boolean)e.eval("window.onload.loaded()"));
            final WindowEventHandler handler = window.getOnload();
            assertNotNull(handler);
            assertTrue(handler.loaded());
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void throwTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        e.put(ScriptEngine.FILENAME, "throwtest.js");

        try {
            e.eval("throw 'foo'");
        } catch (final ScriptException exp) {
            log(exp.getMessage());
            assertTrue(exp.getMessage().contains("foo in throwtest.js at line number 1 at column number 0"));
            assertEquals(exp.getFileName(), "throwtest.js");
            assertEquals(exp.getLineNumber(), 1);
        }
    }

    @Test
    public void setTimeoutTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final Window window = new Window();

        try {
            final Class<?> setTimeoutParamTypes[] = { Window.class, String.class, int.class };
            final Method setTimeout = Window.class.getDeclaredMethod("setTimeout", setTimeoutParamTypes);
            assertNotNull(setTimeout);
            e.put("window", window);
            e.eval("window.setTimeout('foo()', 100)");

            // try to make setTimeout global
            e.put("setTimeout", setTimeout);
            // TODO: java.lang.ClassCastException: required class
            // java.lang.Integer but encountered class java.lang.Double
            // e.eval("setTimeout('foo2()', 200)");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void setWriterTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);

        try {
            e.eval("print('hello world')");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
        assertEquals(sw.toString(), println("hello world"));
    }

    @Test
    public void redefineEchoTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        try {
            e.eval("var echo = {}; if (typeof echo !== 'object') { throw 'echo is a '+typeof echo; }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void noEnumerablePropertiesTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            e.eval("for (i in this) { throw 'found property: ' + i }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void noRefErrorForGlobalThisAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            e.eval("this.foo");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void refErrorForUndeclaredAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            e.eval("try { print(foo); throw 'no ref error' } catch (e) { if (!(e instanceof ReferenceError)) throw e; }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void typeErrorForGlobalThisCallTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            e.eval("try { this.foo() } catch(e) { if (! (e instanceof TypeError)) throw 'no type error' }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void refErrorForUndeclaredCallTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            e.eval("try { foo() } catch(e) { if (! (e instanceof ReferenceError)) throw 'no ref error' }");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    // check that print function prints arg followed by newline char
    public void printTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);
        try {
            e.eval("print('hello')");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        assertEquals(sw.toString(), println("hello"));
    }

    @Test
    // check that print prints all arguments (more than one)
    public void printManyTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final StringWriter sw = new StringWriter();
        e.getContext().setWriter(sw);
        try {
            e.eval("print(34, true, 'hello')");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        assertEquals(sw.toString(), println("34 true hello"));
    }

    @Test
    public void scriptObjectAutoConversionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        e.eval("obj = { foo: 'hello' }");
        e.put("Window", e.eval("Packages.Window"));
        assertEquals(e.eval("Window.funcJSObject(obj)"), "hello");
        assertEquals(e.eval("Window.funcMap(obj)"), "hello");
        assertEquals(e.eval("Window.funcJSObject(obj)"), "hello");
    }

    @Test
    public void mirrorCache() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        e.put("c1", String.class);
        e.put("c2", String.class);
        assertTrue((boolean) e.eval("c1 === c2"));
        assertTrue((boolean) e.eval("c1.class === c2.class"));
    }

    @Test
    public void mirrorCacheExtend() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        String className = "org.test.MyClass";
        e.put("c1", loadTestClass(className, "method1"));
        e.put("c2", loadTestClass(className, "method1"));
        // names are equal, instances are distinct
        assertTrue((boolean) e.eval("c1 !== c2"));
        assertTrue((boolean) e.eval("c1.class !== c2.class"));
        assertTrue((boolean) e.eval("c1.class.name === c2.class.name"));

        Class<?> c1Class = (Class<?>) e.eval("c1.class");
        Class<?> c2Class = (Class<?>) e.eval("c2.class");
        assertNotSame(c1Class, c2Class);

        // make sure the same class that is not
        // available through a named lookup
        // is also de-duplicated
        e.put("c1Class1", c1Class);
        e.put("c1Class2", c1Class);
        assertTrue((boolean) e.eval("c1Class1 === c1Class2"));
    }

    @Test
    public void staticCallDifferentClassesSameName() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        String className = "org.test.MyClass";
        Class<?> c1 = loadTestClass(className, "method1");
        Class<?> c2 = loadTestClass(className, "method2");

        e.put("c1", c1);
        e.put("c2", c2);

        e.eval("c1.method1()");
        e.eval("c2.method2()");
    }

    private static class TestClassLoader extends ClassLoader {
        private final String className;
        private final byte[] classBytes;

        public TestClassLoader(String className, byte[] classBytes) {
            this.className = className;
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!Objects.equals(name, className)) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, classBytes, 0, classBytes.length);
        }
    }

    private static Class<?> loadTestClass(String className, String methodName) {
        ClassFile cf = of();
        byte[] bytes = cf.build(ClassDesc.of(className), cb ->
            cb.withFlags(ACC_PUBLIC)
              .withMethodBody(methodName, MethodTypeDesc.of(CD_void), ACC_PUBLIC | ACC_STATIC, mb ->
                  mb.return_()));

        ClassLoader cl = new TestClassLoader(className, bytes);
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void checkProxyAccess() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        final boolean[] reached = new boolean[1];
        final Runnable r = (Runnable)Proxy.newProxyInstance(
            ScriptEngineTest.class.getClassLoader(),
            new Class[] { Runnable.class },
            new InvocationHandler() {
                @Override
                public Object invoke(final Object p, final Method mtd, final Object[] a) {
                    reached[0] = true;
                    return null;
                }
            });

        e.put("r", r);
        e.eval("r.run()");

        assertTrue(reached[0]);
    }

    // properties that can be read by any code
    private static final String[] PROP_NAMES = {
        "java.version",
        "java.vendor",
        "java.vendor.url",
        "java.class.version",
        "os.name",
        "os.version",
        "os.arch",
        "file.separator",
        "path.separator",
        "line.separator",
        "java.specification.version",
        "java.specification.vendor",
        "java.specification.name",
        "java.vm.specification.version",
        "java.vm.specification.vendor",
        "java.vm.specification.name",
        "java.vm.version",
        "java.vm.vendor",
        "java.vm.name"
    };

    @Test
    public void checkPropertyReadPermissions() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        for (final String name : PROP_NAMES) {
            checkProperty(e, name);
        }
    }

    @Test
    public void withOnMirrorTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        final Object obj = e.eval("({ foo: 'hello'})");
        final Object[] arr = new Object[1];
        arr[0] = obj;
        e.put("arr", arr);
        final Object res = e.eval("var res; with(arr[0]) { res = foo; }; res");
        assertEquals(res, "hello");
    }

    // with v8 engine's ENGINE_SCOPE bindings
    @Test
    public void enumerableGlobalsTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        e.put(ScriptEngine.FILENAME, "test");
        final Object enumerable = e.eval(
            "Object.getOwnPropertyDescriptor(this, " +
            " 'javax.script.filename').enumerable");
        assertEquals(enumerable, Boolean.FALSE);
    }

    public static class Context {
        private Object myobj;

        public void set(final Object o) {
            myobj = o;
        }

        public Object get() {
            return myobj;
        }
    }

    @Test
    public void currentGlobalMissingTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName("v8");

        final Context ctx = new Context();
        e.put("ctx", ctx);
        e.eval("var obj = { foo: function(str) { return str.toUpperCase() } }");
        e.eval("ctx.set(obj)");
        final Invocable inv = (Invocable)e;
        assertEquals("HELLO", inv.invokeMethod(ctx.get(), "foo", "hello"));
        // try object literal
        e.eval("ctx.set({ bar: function(str) { return str.toLowerCase() } })");
        assertEquals("hello", inv.invokeMethod(ctx.get(), "bar", "HELLO"));
        // try array literal
        e.eval("var arr = [ 'hello', 'world' ]");
        e.eval("ctx.set(arr)");
        assertEquals("helloworld", inv.invokeMethod(ctx.get(), "join", ""));
    }

    // ScriptEngineFactory.getParameter() throws IAE
    // for an unknown key, doesn't conform to the general spec
    @Test
    public void getParameterInvalidKeyTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName(ENGINE);
        // no exception expected here!
        final Object value = e.getFactory().getParameter("no value assigned to this key");
        assertNull(value);
    }

    @Test
    public void functionalInterfaceStringTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName(ENGINE);
        final AtomicBoolean invoked = new AtomicBoolean(false);
        e.put("f", JSFunction.apply(t -> {
                invoked.set(true);
                return t;
            }));
        assertEquals(e.eval("var x = 'a'; x += 'b'; f(x)"), "ab");
        assertTrue(invoked.get());
    }

    @Test
    public void functionalInterfaceObjectTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName(ENGINE);
        final AtomicBoolean invoked = new AtomicBoolean(false);
        e.put("c", JSFunction.consumer(t -> {
                assertTrue(t instanceof JSObject);
                assertEquals(((JSObject)t).getMember("a"), "xyz");
                invoked.set(true);
            }));
        e.eval("var x = 'xy'; x += 'z';c({a:x})");
        assertTrue(invoked.get());
    }

    @Test
    public void testLengthOnArrayLikeObjects() throws Exception {
        final ScriptEngine e = new ScriptEngineManager().getEngineByName(ENGINE);
        final Object val = e.eval("var arr = { length: 1, 0: 1}; arr.length");

        assertTrue(Number.class.isAssignableFrom(val.getClass()));
        assertTrue(((Number)val).intValue() == 1);
    }

    @Test
    public void illegalBindingsValuesTest() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName(ENGINE);

        try {
            e.put(null, "null-value");
            fail();
        } catch (final NullPointerException x) {
            // expected
        }

        try {
            e.put("", "empty-value");
            fail();
        } catch (final IllegalArgumentException x) {
            // expected
        }

        final Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);

        try {
            b.put(null, "null-value");
            fail();
        } catch (final NullPointerException x) {
            // expected
        }

        try {
            b.put("", "empty-value");
            fail();
        } catch (final IllegalArgumentException x) {
            // expected
        }

        try {
            b.get(null);
            fail();
        } catch (final NullPointerException x) {
            // expected
        }

        try {
            b.get("");
            fail();
        } catch (final IllegalArgumentException x) {
            // expected
        }

        try {
            b.get(1);
            fail();
        } catch (final ClassCastException x) {
            // expected
        }

        try {
            b.remove(null);
            fail();
        } catch (final NullPointerException x) {
            // expected
        }

        try {
            b.remove("");
            fail();
        } catch (final IllegalArgumentException x) {
            // expected
        }

        try {
            b.remove(1);
            fail();
        } catch (final ClassCastException x) {
            // expected
        }

        try {
            b.containsKey(null);
            fail();
        } catch (final NullPointerException x) {
            // expected
        }

        try {
            b.containsKey("");
            fail();
        } catch (final IllegalArgumentException x) {
            // expected
        }

        try {
            b.containsKey(1);
            fail();
        } catch (final ClassCastException x) {
            // expected
        }

        try {
            b.putAll(null);
            fail();
        } catch (final NullPointerException x) {
            // expected
        }

        try {
            b.putAll(Collections.singletonMap((String)null, "null-value"));
            fail();
        } catch (final NullPointerException x) {
            // expected
        }

        try {
            b.putAll(Collections.singletonMap("", "empty-value"));
            fail();
        } catch (final IllegalArgumentException x) {
            // expected
        }
    }

    // with insonsistent get/remove methods behavior for undefined attributes
    @Test
    public void testScriptContextGetRemoveUndefined() throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine e = manager.getEngineByName(ENGINE);
        final ScriptContext ctx = e.getContext();
        assertNull(ctx.getAttribute("undefinedname", ScriptContext.ENGINE_SCOPE));
        assertNull(ctx.removeAttribute("undefinedname", ScriptContext.ENGINE_SCOPE));
    }

    @Test
    public void testCrossEngineObjectPassing() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        e2.put("obj", e1.eval("({ foo: 333})"));
        Object obj = e2.eval("obj");
        assertNotNull(obj);
        assertTrue(obj instanceof JSObject);
    }

    @Test
    public void testHideCallerSensitiveMethods() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");
        Object val = e.eval("(function() { " +
                " const C = java.lang.Class; " +
                " try { C.forName('java.lang.Object'); } catch (e) { return true; } " +
                " return false; })()");
        assertTrue((Boolean)val);
    }

    @Test
    public void testInstanceofForJavaObjects() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");
        e.eval("var M = java.util.Map;\n" +
               "var HM = java.util.HashMap;\n" +
               "var O = java.lang.Object;\n" +
               "var F = java.io.File;");

        e.eval("var m = new HM()");

        assertTrue((Boolean)e.eval("m instanceof HM"));
        assertTrue((Boolean)e.eval("m instanceof M"));
        assertTrue((Boolean)e.eval("m instanceof O"));
        assertFalse((Boolean)e.eval("m instanceof F"));
    }

    @Test
    public void testJavaObjectFromEval() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");
        Object obj = e.eval("new java.io.File('xx')");
        assertTrue(obj instanceof java.io.File);

        obj = e.eval("new java.lang.Runnable({ run: function() {} })");
        assertTrue(obj instanceof Runnable);

        obj = e.eval("new (Java.type('java.util.HashMap'))()");
        assertTrue(obj instanceof java.util.HashMap);

        obj = e.eval("Java.type('java.util.Collections')");
        assertTrue(obj instanceof Class && obj == Collections.class);
    }

    @Test
    public void testJavaExceptionFromEval() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");
        boolean caughtException = false;
        try {
            e.eval("throw new java.lang.NullPointerException('NULL')");
        } catch (V8ScriptException se) {
            Object exp = se.getEcmaError();
            assertTrue(exp instanceof NullPointerException);
            assertEquals(((Throwable)exp).getMessage(), "NULL");
            caughtException = true;
        }
        assertTrue(caughtException);
    }

    @Test
    public void testScriptContextVariableAccess() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");
        e.put("x", 42);
        // context.this gets back underlying Java ScriptContext object!
        Object value = e.eval("context.this.getAttribute('x')");
        assertEquals(((Number)value).intValue(), 42);
        // easier access of 'vars' in context
        value = e.eval("context.x");
        assertEquals(((Number)value).intValue(), 42);
    }

    @Test
    public void testLoadOperatesOnRightBindings() throws ScriptException {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");

        // two Bindings - one default and one created
        Bindings b1 = e.getBindings(ScriptContext.ENGINE_SCOPE);
        Bindings b2 = e.createBindings();

        // both have foo but different type values
        b1.put("foo", "hello");
        b2.put("foo", 42);

        // define a non-function in b1 that calls 'load'
        Object obj = e.eval("(function() { return load({ name: 'x', script: 'var bar = 4; typeof foo' }) })", b1);

        // expose that function to "b2"
        b2.put("func", obj);

        // call "func" with b2 as ENGINE_SCOPE
        Object result = e.eval("func()", b2);
        // because b1's foo is type String - it shouldn't matter b2 has a number 'foo'
        assertEquals(result, "string");

        // loaded code defines 'bar' - that should go into b1 and not b2
        assertEquals(e.eval("typeof bar", b1), "number");
        assertEquals(((Number)e.eval("bar", b1)).intValue(), 4);

        assertEquals(e.eval("typeof bar", b2), "undefined");
    }

    /*
    @Test
    public void testForkJavaDefinition() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine javaEngine = m.getEngineByName("v8");
        ScriptEngine noJavaEngine = m.getEngineByName("v8-no-java");

        CompletableFuture res = (CompletableFuture)javaEngine.eval("fork({ name: 'x', script: 'typeof Java'})");
        assertEquals(res.get(), "function");
        res = (CompletableFuture)noJavaEngine.eval("fork({ name: 'x', script: 'typeof Java'})");
        assertEquals(res.get(), "undefined");

    }
    */

    private static final String NO_EVAL_MSG = "Code generation from strings disallowed for this context";

    private static void checkEvals(ScriptEngine e) throws ScriptException {
        // eval, Function, fork, load - all should fail!
        assertTrue((Boolean)e.eval("var caught = false;\n" +
            "try { eval('print') } catch (e) { caught = e.message.indexOf(msg) != -1; };\n" +
            "caught"));

        assertTrue((Boolean)e.eval("var caught = false;\n" +
            "try { new Function('x', 'x * x') } catch (e) { caught = e.message.indexOf(msg) != -1; };\n" +
            "caught"));

        assertTrue((Boolean)e.eval("var caught = false;\n" +
            "try { fork('print') } catch (e) { caught = e.message.indexOf(msg) != -1; };\n" +
            "caught"));

        assertTrue((Boolean)e.eval("var caught = false;\n" +
            "try { load({ name: 'x', script: 'print'}) } catch (e) { caught = e.message.indexOf(msg) != -1; };\n" +
            "caught"));
    }

    @Test
    public void testNoEvals() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName("v8");
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        b.put("msg", NO_EVAL_MSG);

        // don't allow eval from strings
        V8Context ctx = (V8Context)b;
        ctx.allowCodeGenerationFromStrings(false);
        assertFalse(ctx.isCodeGenerationFromStringsAllowed());

        // engine eval should be okay!
        assertEquals(e.eval("'hello'"), "hello");
        assertEquals(((Number)e.eval("34 + 59")).intValue(), 34 + 59);
        assertTrue(e.eval("print") instanceof JSFunction);
        assertTrue(e.eval("this") instanceof JSObject);

        checkEvals(e);
    }

    @Test
    public void testNoEvalsWithJava() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName("v8");
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        b.put("msg", NO_EVAL_MSG);

        // don't allow eval from strings
        V8Context ctx = (V8Context)b;
        ctx.allowCodeGenerationFromStrings(false);
        assertFalse(ctx.isCodeGenerationFromStringsAllowed());

        // engine eval should be okay!
        assertEquals(e.eval("'hello'"), "hello");
        assertEquals(((Number)e.eval("34 + 59")).intValue(), 34 + 59);
        assertTrue(e.eval("print") instanceof JSFunction);
        assertTrue(e.eval("this") instanceof JSObject);

        // accessing Java also results in eval (from boot code). That should still work!
        Object res = e.eval("m = new java.util.HashMap(); m.put('x', 'world'); m");
        assertTrue(res instanceof Map);
        assertEquals(((Map)res).get("x"), "world");

        checkEvals(e);
    }

    @Test
    public void testEvalsCannotBeTurnedOnFromScript() throws ScriptException {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName("v8");
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);

        // don't allow eval from strings
        V8Context ctx = (V8Context)b;
        ctx.allowCodeGenerationFromStrings(false);
        assertFalse(ctx.isCodeGenerationFromStringsAllowed());

        // expose bindings as a var
        e.put("b", b);

        // try to turn on evals from script!
        Object caught = e.eval("var caught = false;\n" +
            "try { b.allowCodeGenerationFromStrings(true); } " +
            "catch (ex) { print(ex.stack); caught = (ex instanceof TypeError); }\n" +
            "caught");

        assertTrue((Boolean)caught);
    }

    private static void checkProperty(final ScriptEngine e, final String name)
        throws ScriptException {
        final String value = System.getProperty(name);
        e.put("name", name);
        assertEquals(value, e.eval("java.lang.System.getProperty(name)"));
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    // Returns String that would be the result of calling PrintWriter.println
    // of the given String. (This is to handle platform specific newline).
    private static String println(final String str) {
        return str + LINE_SEPARATOR;
    }
}
