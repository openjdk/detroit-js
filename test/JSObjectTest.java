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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.openjdk.engine.javascript.V8Undefined;
import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSFactory;
import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSSymbol;
import org.openjdk.engine.javascript.JSObject.PropertyAttribute;
import org.openjdk.engine.javascript.V8ScriptException;

/**
 * Tests to check org.openjdk.engine.javascript.JSObject API.
 *
 * @test
 * @run testng JSObjectTest
 */
@SuppressWarnings("javadoc")
public class JSObjectTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @SuppressWarnings("unchecked")
    @Test
    public void reflectionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        e.eval("var obj = { x: 344, y: 'v8' }");

        int count = 0;
        Map<Object, Object> map = (Map<Object, Object>) e.get("obj");
        assertFalse(map.isEmpty());
        assertTrue(map.keySet().contains("x"));
        assertTrue(map.containsKey("x"));
        assertTrue(map.values().contains("v8"));
        assertTrue(map.containsValue("v8"));
        for (final Map.Entry<?, ?> ex : map.entrySet()) {
            final Object key = ex.getKey();
            if (key.equals("x")) {
                assertTrue(344 == ((Number) ex.getValue()).doubleValue());
                count++;
            } else if (key.equals("y")) {
                assertEquals(ex.getValue(), "v8");
                count++;
            }
        }
        assertEquals(2, count);
        assertEquals(2, map.size());

        // add property
        map.put("z", "hello");
        assertEquals(e.eval("obj.z"), "hello");
        assertEquals(map.get("z"), "hello");
        assertTrue(map.keySet().contains("z"));
        assertTrue(map.containsKey("z"));
        assertTrue(map.values().contains("hello"));
        assertTrue(map.containsValue("hello"));
        assertEquals(map.size(), 3);

        final Map<Object, Object> newMap = new HashMap<>();
        newMap.put("foo", 23.0);
        newMap.put("bar", true);
        map.putAll(newMap);

        assertEquals(e.eval("obj.foo"), 23);
        assertEquals(e.eval("obj.bar"), true);

        // remove using map method
        map.remove("foo");
        assertEquals(e.eval("typeof obj.foo"), "undefined");

        count = 0;
        e.eval("var arr = [ true, 'hello' ]");
        map = (Map<Object, Object>) e.get("arr");
        assertFalse(map.isEmpty());
        assertTrue(map.containsKey("length"));
        assertTrue(map.containsValue("hello"));
        for (final Map.Entry<?, ?> ex : map.entrySet()) {
            final Object key = ex.getKey();
            if (key.toString().equals("0")) {
                assertEquals(ex.getValue(), Boolean.TRUE);
                count++;
            } else if (key.toString().equals("1")) {
                assertEquals(ex.getValue(), "hello");
                count++;
            }
        }
        assertEquals(count, 2);
        assertEquals(map.size(), 2);

        // add element
        map.put("2", "world");
        assertEquals(map.get("2"), "world");
        assertEquals(map.size(), 3);

        // remove all
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(e.eval("typeof arr[0]"), "undefined");
        assertEquals(e.eval("typeof arr[1]"), "undefined");
        assertEquals(e.eval("typeof arr[2]"), "undefined");
    }

    @Test
    public void jsobjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            e.eval("var obj = { '1': 'world', func: function() { return this.bar; }, bar: 'hello' }");
            final JSObject obj = (JSObject) e.get("obj");

            // try basic get on existing properties
            if (!obj.getMember("bar").equals("hello")) {
                fail("obj.bar != 'hello'");
            }

            if (!obj.getSlot(1).equals("world")) {
                fail("obj[1] != 'world'");
            }

            if (!obj.callMember("func", new Object[0]).equals("hello")) {
                fail("obj.func() != 'hello'");
            }

            // try setting properties
            obj.setMember("bar", "new-bar");
            obj.setSlot(1, "new-element-1");
            if (!obj.getMember("bar").equals("new-bar")) {
                fail("obj.bar != 'new-bar'");
            }

            if (!obj.getSlot(1).equals("new-element-1")) {
                fail("obj[1] != 'new-element-1'");
            }

            // try adding properties
            obj.setMember("prop", "prop-value");
            obj.setSlot(12, "element-12");
            if (!obj.getMember("prop").equals("prop-value")) {
                fail("obj.prop != 'prop-value'");
            }

            if (!obj.getSlot(12).equals("element-12")) {
                fail("obj[12] != 'element-12'");
            }

            // delete properties
            obj.removeMember("prop");
            if ("prop-value".equals(obj.getMember("prop"))) {
                fail("obj.prop is not deleted!");
            }
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void jsObjectToStringTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            final Object obj = e.eval("new TypeError('wrong type')");
            assertEquals(obj.toString(), "TypeError: wrong type", "toString returns wrong value");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }

        try {
            final Object obj = e.eval("(function func() { print('hello'); })");
            assertEquals(obj.toString(), "function func() { print('hello'); }", "toString returns wrong value");
        } catch (final Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }
    }

    @Test
    public void mirrorNewObjectGlobalFunctionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final ScriptEngine e2 = m.getEngineByName(ENGINE);

        e.eval("function func() {}");
        e2.put("foo", e.get("func"));
        final JSObject e2global = (JSObject)e2.eval("this");
        final Object newObj = ((JSObject)e2global.getMember("foo")).newObject();
        assertTrue(newObj instanceof JSObject);
    }

    @Test
    public void mirrorNewObjectInstanceFunctionTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        final ScriptEngine e2 = m.getEngineByName(ENGINE);

        e.eval("function func() {}");
        e2.put("func", e.get("func"));
        final JSObject e2obj = (JSObject)e2.eval("({ foo: func })");
        final Object newObj = ((JSObject)e2obj.getMember("foo")).newObject();
        assertTrue(newObj instanceof JSObject);
    }

    @Test
    public void mapJSObjectCallsiteTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine engine = m.getEngineByName("v8");
        final String TEST_SCRIPT = "typeof obj.foo";

        final Bindings global = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        engine.eval("var obj = java.util.Collections.emptyMap()");
        engine.eval(TEST_SCRIPT, global);
        // redefine 'obj' to be a script object
        engine.eval("obj = {}");

        final Bindings newGlobal = engine.createBindings();
        // transfer 'obj' from default global to new global
        // new global will get a JSObject wrapping 'obj'
        newGlobal.put("obj", global.get("obj"));

        assertEquals("undefined", engine.eval(TEST_SCRIPT, newGlobal));
    }

    public interface MirrorCheckExample {
        Object test1(Object arg);
        Object test2(Object arg);
        boolean compare(Object o1, Object o2);
    }

    @Test
    public void checkMirrorToObject() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine engine = engineManager.getEngineByName(ENGINE);
        final Invocable invocable = (Invocable)engine;

        engine.eval("function test1(arg) { return { arg: arg }; }");
        engine.eval("function test2(arg) { return arg; }");
        engine.eval("function compare(arg1, arg2) { return arg1 == arg2; }");

        final Map<String, Object> map = new HashMap<>();
        map.put("option", true);

        final MirrorCheckExample example = invocable.getInterface(MirrorCheckExample.class);

        final Object value1 = invocable.invokeFunction("test1", map);
        final Object value2 = example.test1(map);
        final Object value3 = invocable.invokeFunction("test2", value2);
        final Object value4 = example.test2(value2);

        // check that Object type argument receives a JSObject
        // when ScriptObject is passed
        assertTrue(value1 instanceof JSObject);
        assertTrue(value2 instanceof JSObject);
        assertTrue(value3 instanceof JSObject);
        assertTrue(value4 instanceof JSObject);
        assertTrue((boolean)invocable.invokeFunction("compare", value1, value1));
        assertTrue(example.compare(value1, value1));
        assertTrue((boolean)invocable.invokeFunction("compare", value3, value4));
        assertTrue(example.compare(value3, value4));
    }

    @Test
    public void mirrorUnwrapInterfaceMethod() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine engine = engineManager.getEngineByName("v8");
        final Invocable invocable = (Invocable)engine;
        engine.eval("function apply(obj) { " +
            " return obj instanceof Packages.org.openjdk.engine.javascript.JSObject; " +
            "}");
        @SuppressWarnings("unchecked")
        final Function<Object,Object> func = invocable.getInterface(Function.class);
        assertFalse((boolean)func.apply(engine.eval("({ x: 2 })")));
    }

    @Test
    public void topLevelAnonFuncExpression() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        final JSObject func = (JSObject)e.eval("(function(x) { return x + ' world' })");
        assertTrue(func instanceof JSFunction);
        assertEquals(func.call(e.eval("this"), "hello"), "hello world");
    }

    @Test
    public void userDefinedJavaFunctionTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        e.put("sysProp", new JSFunction() {
            @Override
            public Object call(Object thiz, Object... args) throws ScriptException {
                if (args.length > 0) {
                    return System.getProperty(Objects.toString(args[0]));
                }
                return V8Undefined.INSTANCE;
            }
        });

        assertEquals(e.eval("sysProp('java.home')"), System.getProperty("java.home"));
    }

    @Test
    public void jsObjectNamedPropetyAccess() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        Object obj = e2.eval("(src = { x: 555 })");

        // read named property
        e1.put("dest", obj);
        assertTrue((boolean) e1.eval("dest.x === 555"));

        // write named property
        e1.eval("dest.y = 'hello'");
        // read it from source to verify
        assertTrue((boolean)e2.eval("src.y === 'hello'"));

        // delete named property
        assertTrue((boolean)e1.eval("delete dest.y"));
        // check that it is deleted!
        assertTrue((boolean)e2.eval("typeof src.y === 'undefined'"));
    }

    @Test
    public void jsObjectIndexedPropetyAccess() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        Object obj = e2.eval("(src = [ 111 ])");

        // read indexed property
        e1.put("dest", obj);
        assertTrue((boolean) e1.eval("dest[0] === 111"));

        // write indexed property
        e1.eval("dest[0] = -345");
        // read it from source to verify
        assertTrue((boolean)e2.eval("src[0] === -345"));

        // delete indexed property
        assertTrue((boolean)e1.eval("delete dest[0]"));
        // check that it is deleted!
        assertTrue((boolean)e2.eval("typeof src[0] === 'undefined'"));
    }

    @Test
    public void jsObjectFunctionCall() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        // define a lambda in second engine
        Object func = e2.eval("(x, y) => x * y");
        // expose that lambda as 'externFunc' in first engine
        e1.put("externFunc", func);
        assertTrue((boolean)e1.eval("externFunc(34, 45) === 34*45"));
    }

    @Test
    public void jsObjectFunctionCallNegativeTest() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        // define a non-function object in e2
        Object obj = e2.eval("({})");
        // expose that obj to e1
        e1.put("exposedFunc", obj);

        // make sure it is exposed as "object" and not "function"
        assertEquals(e1.eval("typeof exposedFunc"), "object");

        // attempt to call it anyway and check for TypeError
        boolean caughtException = false;
        try {
            e1.eval("exposedFunc()");
        } catch (V8ScriptException se) {
            assertTrue(se.getEcmaError().toString().contains("TypeError"));
            caughtException = true;
        }
        assertTrue(caughtException);
    }

    @Test
    public void jsObjectFunctionNew() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        // expose Object constructor of e2 to e1 as "externObject" var
        e1.put("externObject", e2.get("Object"));
        // use 'new' from first engine
        Object newObj = e1.eval("new externObject()");
        // expose newObj back as var in second engine
        e2.put("obj", newObj);
        // make sure 'obj' is local instance in second engine
        assertTrue((boolean)e2.eval("obj instanceof Object"));
    }

    @Test
    public void jsObjectFunctionNewNegativeTest() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        // expose a non-function of e2 to e1 as "externObject" var
        e1.put("externObject", e2.eval("({})"));

        // make sure it is exposed as "object" and not "function"
        assertEquals(e1.eval("typeof externObject"), "object");

        // use 'new' from first engine
        boolean caughtException = false;
        try {
            e1.eval("new externObject()");
        } catch (V8ScriptException se) {
            assertTrue(se.getEcmaError().toString().contains("TypeError"));
            caughtException = true;
        }
        assertTrue(caughtException);
    }

    @Test
    public void jsObjectNamedPropertiesEnumeration() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);

        // make an object with e2
        Object obj = e2.eval("({ x: 34, y: 'hello' })");

        // expose to e1
        e1.put("obj", obj);

        // iterator properties in e1 and return concatenated value
        Object res = e1.eval("var res = ''; for (i in obj) res += obj[i]; res");
        assertEquals(res.toString(), "34hello");
    }

    @Test
    public void jsObjectIndexedPropertiesEnumeration() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);

        // make an array with e2
        Object arr = e2.eval("[ 1, 7, 2, 9 ]");

        // expose to e1
        e1.put("arr", arr);

        // iterator properties in e1 and return concatenated value
        Object res = e1.eval("var res = ''; for (i in arr) res += arr[i]; res");

        // resulting string should be concatenated values (script uses string concat)
        assertEquals(res.toString(), "1729");
    }

    @Test
    public void jsObjectNamedPropertyAttributesTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);

        JSObject jsobj = (JSObject) e.eval("Object.prototype");
        EnumSet<PropertyAttribute> attrs = jsobj.getMemberAttributes("toString");
        assertTrue(attrs.contains(PropertyAttribute.DontEnum));
        assertFalse(attrs.contains(PropertyAttribute.ReadOnly));
        assertFalse(attrs.contains(PropertyAttribute.DontDelete));

        jsobj = (JSObject) e.eval("Math");
        attrs = jsobj.getMemberAttributes("PI");
        assertTrue(attrs.contains(PropertyAttribute.DontEnum));
        assertTrue(attrs.contains(PropertyAttribute.ReadOnly));
        assertTrue(attrs.contains(PropertyAttribute.DontDelete));

        // attribute of non-existent property
        attrs = jsobj.getMemberAttributes("foobar");
        assertTrue(attrs.contains(PropertyAttribute.None));
        assertFalse(attrs.contains(PropertyAttribute.DontEnum));
        assertFalse(attrs.contains(PropertyAttribute.ReadOnly));
        assertFalse(attrs.contains(PropertyAttribute.DontDelete));

        // attribute of a normal property
        jsobj = (JSObject) e.eval("({ x: 42 })");
        attrs = jsobj.getMemberAttributes("x");
        assertTrue(attrs.contains(PropertyAttribute.None));
        assertFalse(attrs.contains(PropertyAttribute.DontEnum));
        assertFalse(attrs.contains(PropertyAttribute.ReadOnly));
        assertFalse(attrs.contains(PropertyAttribute.DontDelete));
    }

    @Test
    public void jsObjectIndexedPropertyAttributesTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);

        JSObject jsobj = (JSObject) e.eval("arr = [34]");
        EnumSet<PropertyAttribute> attrs = jsobj.getSlotAttributes(0);
        assertTrue(attrs.contains(PropertyAttribute.None));
        assertFalse(attrs.contains(PropertyAttribute.DontEnum));
        assertFalse(attrs.contains(PropertyAttribute.ReadOnly));
        assertFalse(attrs.contains(PropertyAttribute.DontDelete));

        e.eval("Object.defineProperty(arr, 0, { enumerable: false, configurable: false })");
        attrs = jsobj.getSlotAttributes(0);
        assertTrue(attrs.contains(PropertyAttribute.DontEnum));
        assertFalse(attrs.contains(PropertyAttribute.ReadOnly));
        assertTrue(attrs.contains(PropertyAttribute.DontDelete));
    }

    @Test
    public void jsObjectPropInTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e1 = engineManager.getEngineByName(ENGINE);
        final ScriptEngine e2 = engineManager.getEngineByName(ENGINE);

        JSObject jsobj = (JSObject)e1.eval("obj = ({})");
        assertFalse((Boolean)e1.eval("'foo' in obj"));

        // expose object to second engine
        e2.put("foreignObj", jsobj);
        // "prop" in object should work here too!
        assertFalse((Boolean)e2.eval("'foo' in foreignObj"));
    }

    @Test
    public void jsObjectThisTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        e.put("func", new JSFunction() {
            @Override
            public Object call(Object thiz, Object...args) {
                return thiz;
            }
        });

        assertTrue((boolean)e.eval("func() === this"));
        assertTrue((boolean)e.eval("func.call(undefined) == this"));
    }

    @Test
    public void testFunctionPrototypeCall() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);

        // define a function in e2
        JSFunction func  = (JSFunction)e2.eval("(function(x, y) { return x * y })");

        // expose it in e1
        e1.put("ff", func);

        // direct call
        Number num = (Number) e1.eval("ff(45, 56)");
        assertEquals(num.intValue(), 45*56);

        // Function.prototype.call call
        num = (Number) e1.eval("ff.call(this, 45, 56)");
        assertEquals(num.intValue(), 45*56);

        // call Function.prototype.call from java
        JSFunction call = (JSFunction)((JSObject)func).getMember("call");
        num = (Number) call.call(func, e2.eval("this"), 45, 89);
        assertEquals(num.intValue(), 45*89);

        // define an object in e2
        JSObject obj = (JSObject)e2.eval("({ x: 33, f: function() { return this.x; } })");

        // expose to e1
        e1.put("o", obj);
        // make sure correct 'this' is passed
        assertEquals(((Number)e1.eval("o.f()")).intValue(), 33);
    }

    @Test
    public void testFunctionPrototypeCall2() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);
        ScriptEngine e2 = m.getEngineByName(ENGINE);

        // define an identity function in e2
        JSFunction identity  = (JSFunction)e2.eval("x => x");

        // expose it in e1
        e1.put("id", identity);
        assertEquals(e1.eval("id.call(this, 'hello')"), "hello");
    }

    @Test
    public void testSymbolProperties() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;

        // create an empty object
        JSObject obj = (JSObject)e.eval("obj = {}");

        // create a new private symbol
        String symName = "**++--";
        JSSymbol sym = fac.newSymbol(symName);

        // expose it a var
        e.put("sym", sym);
        // symbol key'ed property
        e.eval("obj[sym] = 455");

        // get symbol member
        assertEquals(((Number)obj.getMember(sym)).intValue(), 455);

        // set symbol member
        obj.setMember(sym, "hello");
        assertEquals(e.eval("obj[sym]"), "hello");

        // iteration
        JSSymbol[] syms = obj.getSymbolProperties();
        assertEquals(syms.length, 1);
        assertEquals(syms[0].toString(), symName);

        // deletion
        obj.removeMember(sym);
        assertTrue((Boolean)e.eval("typeof obj[sym] === 'undefined'"));
        syms = obj.getSymbolProperties();
        assertEquals(syms.length, 0);
    }

    @Test
    public void testDefineOwnProperty() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);
        JSObject global = (JSObject)e.eval("this");

        // define a read-only property
        global.defineOwnProperty("foo", "hello", EnumSet.of(PropertyAttribute.ReadOnly));

        // try to modify it in script
        assertTrue((Boolean)e.eval("'use strict';\n" +
            " var caught = false;\n" +
            " try { foo = 55; } catch (e) {" +
            "   caught = e instanceof TypeError }\n" +
            " caught"));

        // we should still be able to delete the read-only property
        assertTrue((Boolean)e.eval("delete foo"));
        assertEquals(global.getMember("foo"), V8Undefined.INSTANCE);

        // define it as non-enumerable property
        global.defineOwnProperty("foo", "hello", EnumSet.of(PropertyAttribute.DontEnum));
        assertFalse((Boolean)e.eval("var found = false;\n" +
             " for (var prop in this) {\n" +
             "     if (prop == 'foo') found = true;\n" +
             " }\n" +
             "found"));

        // make it non-deletable
        global.defineOwnProperty("foo", "world", EnumSet.of(PropertyAttribute.DontDelete));
        assertFalse((Boolean)e.eval("delete foo"));
        assertEquals(global.getMember("foo"), "world");
    }

    @Test
    public void testDefineOwnPropertyWithSymbolKey() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);
        JSObject global = (JSObject)e.eval("this");
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSSymbol foo = ((JSFactory)b).newSymbol("foo");

        global.setMember("foo", foo);
        // define a read-only property
        global.defineOwnProperty(foo, "hello", EnumSet.of(PropertyAttribute.ReadOnly));

        // try to modify it in script
        assertTrue((Boolean)e.eval("'use strict';\n" +
            " var caught = false;\n" +
            " try { this[foo] = 55; } catch (e) {" +
            "   caught = e instanceof TypeError }\n" +
            " caught"));

        // we should still be able to delete the read-only property
        assertTrue((Boolean)e.eval("delete this[foo]"));
        assertEquals(global.getMember(foo), V8Undefined.INSTANCE);

        // define it as non-enumerable property
        global.defineOwnProperty(foo, "hello", EnumSet.of(PropertyAttribute.DontEnum));
        assertFalse((Boolean)e.eval("var found = false;\n" +
             " for (var prop in this) {\n" +
             "     if (prop == foo) found = true;\n" +
             " }\n" +
             "found"));

        // make it non-deletable
        global.defineOwnProperty(foo, "world", EnumSet.of(PropertyAttribute.DontDelete));
        assertFalse((Boolean)e.eval("delete this[foo]"));
        assertEquals(global.getMember(foo), "world");
    }

    @Test
    public void testSetAccessorProperty() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);
        JSObject obj = (JSObject)e.eval("({})");
        final boolean[] reachedGetter = new boolean[1];
        final boolean[] reachedSetter = new boolean[1];
        obj.setAccessorProperty("foo", new JSFunction() {
                  @Override
                  public Object call(Object thiz, Object... args) {
                       reachedGetter[0] = true;
                       return "hello-foo";
                  }
            },
            new JSFunction() {
                @Override
                public Object call(Object thiz, Object... args) {
                     reachedSetter[0] = true;
                     return "hello-foo";
                }
            },
            null);
        e.put("obj", obj);

        assertFalse(reachedGetter[0]);
        assertFalse(reachedSetter[0]);
        assertEquals(e.eval("obj.foo"), "hello-foo");
        assertTrue(reachedGetter[0]);
        e.eval("obj.foo = 34");
        assertTrue(reachedSetter[0]);
    }

    @Test
    public void testSetAccessorPropertyWithSymbolKey() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);
        JSObject obj = (JSObject)e.eval("({})");
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSSymbol foo = ((JSFactory)b).newSymbol("foo");
        final boolean[] reachedGetter = new boolean[1];
        final boolean[] reachedSetter = new boolean[1];

        obj.setAccessorProperty(foo, new JSFunction() {
                  @Override
                  public Object call(Object thiz, Object... args) {
                       reachedGetter[0] = true;
                       return "hello-foo-sym";
                  }
            },
            new JSFunction() {
                @Override
                public Object call(Object thiz, Object... args) {
                     reachedSetter[0] = true;
                     return "hello-foo";
                }
            },
            null);

        e.put("obj", obj);
        e.put("foo", foo);

        assertFalse(reachedGetter[0]);
        assertFalse(reachedSetter[0]);
        assertEquals(e.eval("obj[foo]"), "hello-foo-sym");
        assertTrue(reachedGetter[0]);
        e.eval("obj[foo] = 34");
        assertTrue(reachedSetter[0]);
    }

    @Test
    public void toJSONTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        JSObject jsObj = (JSObject)e.eval("({ x: 44, y: 'hello' })");
        assertEquals(jsObj.toJSON(), "{\"x\":44,\"y\":\"hello\"}");
    }

    @Test
    public void toJSONWithGapTest() throws ScriptException {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        JSObject jsObj = (JSObject)e.eval("({ x: 44, y: 'hello' })");
        String json = jsObj.toJSON("  ");
        assertEquals(json, "{\n" +
                           "  \"x\": 44,\n" +
                           "  \"y\": \"hello\"\n" +
                           "}");
    }

    @Test
    public void toJSONForAnotherEngineTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        final ScriptEngine e2 = engineManager.getEngineByName(ENGINE);
        JSObject jsObj = (JSObject)e2.eval("({ x: 44, y: 'hello' })");
        assertEquals(jsObj.toJSON(), "{\"x\":44,\"y\":\"hello\"}");
    }

    @Test
    public void typeQueriesTest() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);

        assertTrue(((JSObject)e.eval("[]")).isArray());
        assertTrue(((JSObject)e.eval("x => x + 1")).isFunction());
        assertTrue(((JSObject)e.eval("(function() { 'use strict'; })")).isStrictFunction());
        assertTrue(((JSObject)e.eval("new Proxy({}, {})")).isProxy());
        assertTrue(((JSObject)e.eval("Promise.resolve(2)")).isPromise());
    }
}
