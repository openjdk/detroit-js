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

import java.util.EnumSet;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.openjdk.engine.javascript.V8Undefined;
import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSFactory;
import org.openjdk.engine.javascript.JSFactory.RegExpFlag;
import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSPromise;
import org.openjdk.engine.javascript.JSProxy;
import org.openjdk.engine.javascript.JSResolver;
import org.openjdk.engine.javascript.JSSymbol;
import org.openjdk.engine.javascript.V8ExecutionControl;
import org.openjdk.engine.javascript.V8ScriptException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.expectThrows;

/**
 * Tests for JSFactory API.
 *
 * @test
 * @run testng JSFactoryTest
 */
@SuppressWarnings("javadoc")
public class JSFactoryTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @Test
    public void jsFactoryBasicsTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        b.put("obj", fac.newObject());
        assertTrue((Boolean)e.eval("obj instanceof Object"));

        b.put("arr", fac.newArray(10));
        assertTrue((Boolean)e.eval("Array.isArray(arr)"));
        assertTrue((Boolean)e.eval("arr.length == 10"));

        b.put("abuf", fac.newArrayBuffer(15));
        assertTrue((Boolean)e.eval("abuf instanceof ArrayBuffer"));

        b.put("d", fac.newDate(0.0));
        assertTrue((Boolean)e.eval("d instanceof Date"));

        b.put("r", fac.newRegExp("foo", EnumSet.of(RegExpFlag.Global, RegExpFlag.IgnoreCase)));
        assertTrue((Boolean)e.eval("r instanceof RegExp"));
        assertEquals(e.eval("r.toString()"), "/foo/gi");
    }

    @Test
    public void jsFactoryPromiseResolveTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        JSResolver resolver = fac.newResolver();
        JSPromise promise = resolver.getPromise();
        String RESOLVE_VALUE = "hello-resolved";
        boolean[] thenReached = new boolean[1];
        promise.then(new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) throws ScriptException {
                thenReached[0] = true;
                assertEquals(args[0], RESOLVE_VALUE);
                return null;
            }
        });

        resolver.resolve(RESOLVE_VALUE);
        e.eval("const x = 10"); // V8 will handle pending promise resolves, rejects at the end of eval
        assertTrue(thenReached[0]);
    }

    @Test
    public void jsFactoryPromiseResolveTest2() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        JSResolver resolver = fac.newResolver();
        JSPromise promise = resolver.getPromise();
        String RESOLVE_VALUE = "hello-resolved";
        boolean[] thenReached = new boolean[1];
        promise.then(new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) throws ScriptException {
                thenReached[0] = true;
                assertEquals(args[0], RESOLVE_VALUE);
                return null;
            }
        });

        resolver.resolve(RESOLVE_VALUE);
        // running microtasks also causes resolved/rejected promises to be handled!
        ((V8ExecutionControl)e).runMicrotasks();
        assertTrue(thenReached[0]);
    }

    @Test
    public void jsFactoryPromiseRejectTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        JSResolver resolver = fac.newResolver();
        JSPromise promise = resolver.getPromise();
        String ERROR_VALUE = "promise-error";
        boolean[] catchReached = new boolean[1];
        promise._catch(new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) throws ScriptException {
                catchReached[0] = true;
                assertEquals(args[0], ERROR_VALUE);
                return null;
            }
        });

        resolver.reject(ERROR_VALUE);
        e.eval("const x = 10"); // V8 will handle pending promise resolves, rejects at the end of eval
        assertTrue(catchReached[0]);
    }

    @Test
    public void jsFactoryPromiseRejectTest2() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        JSResolver resolver = fac.newResolver();
        JSPromise promise = resolver.getPromise();
        String ERROR_VALUE = "promise-error";
        boolean[] catchReached = new boolean[1];
        promise._catch(new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) throws ScriptException {
                catchReached[0] = true;
                assertEquals(args[0], ERROR_VALUE);
                return null;
            }
        });

        resolver.reject(ERROR_VALUE);
        // running microtasks also causes resolved/rejected promises to be handled!
        ((V8ExecutionControl)e).runMicrotasks();
        assertTrue(catchReached[0]);
    }

    @Test
    public void jsFactoryProxyTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        JSObject target = new JSObject() {};
        JSObject handler = (JSObject)e.eval("({ get: function(target, name) { return name.toUpperCase() } })");
        JSProxy proxy = fac.newProxy(target, handler);
        b.put("obj", proxy);

        assertEquals(e.eval("obj.foo"), "FOO");
        assertEquals(e.eval("obj.bar"), "BAR");
    }

    @Test
    public void jsFactoryProxyTest2() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        JSObject target = new JSObject() {};

        // double Proxy! Proxy whose handler is a JSObject implemented in Java!
        JSObject handler = new JSObject() {
            @Override
            public Object getMember(String name) {
                if (name.equals("get")) {
                    return new JSFunction() {
                        @Override
                        public Object call(Object thiz, Object... args) {
                            // first arg is target, second arg is name of the property
                            return args[1].toString().toLowerCase();
                        }
                    };
                }
                return V8Undefined.INSTANCE;
            }
        };

        JSProxy proxy = fac.newProxy(target, handler);
        b.put("obj", proxy);

        assertEquals(e.eval("obj.Foo"), "foo");
        assertEquals(e.eval("obj.bAr"), "bar");
    }

    @Test
    public void jsFactoryProxyRevokeTest() throws ScriptException, NoSuchMethodException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        JSObject revocable = (JSObject)e.eval("r = Proxy.revocable({}, {}); p = r.proxy; r");
        e.eval("p.x = 44");
        JSProxy proxy = (JSProxy) revocable.getMember("proxy");
        assertFalse(proxy.isRevoked());
        revocable.callMember("revoke");
        assertTrue(proxy.isRevoked());
        V8ScriptException ex = expectThrows(V8ScriptException.class, () -> e.eval("p.x"));
        assertTrue(ex.getEcmaError().toString().contains("TypeError"));
    }

    @Test
    public void jsFactoryProxyTargetHandlerTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        JSProxy proxy = (JSProxy)e.eval("target = {}; handler = {}; new Proxy(target, handler)");
        assertEquals(proxy.getTarget(), e.eval("target"));
        assertEquals(proxy.getHandler(), e.eval("handler"));
    }

    @Test
    public void jsFactoryErrorsTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        String errorMessage = "JS Error from Java";

        Object exp = fac.newError(errorMessage);
        b.put("exp", exp);
        assertTrue((Boolean)e.eval("exp instanceof Error"));
        assertEquals((String)e.eval("exp.message"), errorMessage);

        exp = fac.newRangeError(errorMessage);
        b.put("exp", exp);
        assertTrue((Boolean)e.eval("exp instanceof RangeError"));
        assertEquals((String)e.eval("exp.message"), errorMessage);

        exp = fac.newReferenceError(errorMessage);
        b.put("exp", exp);
        assertTrue((Boolean)e.eval("exp instanceof ReferenceError"));
        assertEquals((String)e.eval("exp.message"), errorMessage);

        exp = fac.newSyntaxError(errorMessage);
        b.put("exp", exp);
        assertTrue((Boolean)e.eval("exp instanceof SyntaxError"));
        assertEquals((String)e.eval("exp.message"), errorMessage);

        exp = fac.newTypeError(errorMessage);
        b.put("exp", exp);
        assertTrue((Boolean)e.eval("exp instanceof TypeError"));
        assertEquals((String)e.eval("exp.message"), errorMessage);
    }

    @Test
    public void jsFactorySymbols() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        String symName = "****";

        JSSymbol sym = fac.newSymbol(symName);
        e.put("sym", sym);
        assertTrue((Boolean)e.eval("typeof sym === 'symbol'"));
        assertEquals(sym.toString(), symName);

        JSSymbol sym1 = fac.symbolFor(symName);
        e.put("sym1", sym1);
        assertTrue((Boolean)e.eval("typeof sym1 === 'symbol'"));
        assertEquals(sym1.toString(), symName);

        JSSymbol sym2 = fac.symbolFor(symName);
        e.put("sym2", sym2);
        assertTrue((Boolean)e.eval("typeof sym2 === 'symbol'"));
        assertEquals(sym2.toString(), symName);

        assertTrue((Boolean)e.eval("sym1 === sym2"));
    }

    @Test
    public void toJSONTest() throws ScriptException {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        JSFactory fac = (JSFactory)e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSObject jsObj = (JSObject)e.eval("({ x: 44, y:'hello'})");
        assertEquals(fac.toJSON(jsObj), "{\"x\":44,\"y\":\"hello\"}");
    }

    @Test
    public void toJSONWithGapTest() throws ScriptException {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        JSFactory fac = (JSFactory)e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSObject jsObj = (JSObject)e.eval("({ x: 44, y:'hello'})");
        String json = fac.toJSON(jsObj, "  ");
        assertEquals(json, "{\n" +
                           "  \"x\": 44,\n" +
                           "  \"y\": \"hello\"\n" +
                           "}");
    }

    @Test
    public void parseJSONTest() throws ScriptException {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        JSFactory fac = (JSFactory)e.getBindings(ScriptContext.ENGINE_SCOPE);
        Object obj = fac.parseJSON("{\"x\":55,\"y\":\"world\"}");

        assertTrue(obj instanceof JSObject);
        JSObject jsObj = (JSObject)obj;
        assertEquals(((Number)jsObj.getMember("x")).intValue(), 55);
        assertEquals(jsObj.getMember("y"), "world");
    }
}
