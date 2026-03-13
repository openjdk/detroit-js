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

import java.nio.IntBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.testng.annotations.Test;
import org.openjdk.engine.javascript.JSFactory;
import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSSymbol;

/**
 * Tests for pluggable external impls. of org.openjdk.engine.javascript.JSObject.
 *
 * @test
 * @run testng PluggableJSObjectTest
 */
@SuppressWarnings("javadoc")
public class PluggableJSObjectTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    public static class MapWrapperObject implements JSObject {
        private final HashMap<String, Object> map = new LinkedHashMap<>();

        public HashMap<String, Object> getMap() {
            return map;
        }

        @Override
        public Object getMember(final String name) {
            return map.get(name);
        }

        @Override
        public boolean setMember(final String name, final Object value) {
            map.put(name, value);
            return true;
        }

        @Override
        public boolean hasMember(final String name) {
            return map.containsKey(name);
        }

        @Override
        public boolean removeMember(final String name) {
            map.remove(name);
            return true;
        }

        @Override
        public String[] getNamedProperties() {
            return map.keySet().stream().
                filter(k -> k instanceof String).
                map(String.class::cast).
                collect(Collectors.toList()).
                toArray(new String[0]);
        }
    }

    @Test
    // Named property access on a JSObject
    public void namedAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            final MapWrapperObject obj = new MapWrapperObject();
            e.put("obj", obj);
            assertEquals(e.eval("'foo' in obj"), Boolean.FALSE);
            obj.getMap().put("foo", "bar");

            // property-like access on MapWrapperObject objects
            assertEquals(e.eval("obj.foo"), "bar");
            e.eval("obj.foo = 'hello'");
            assertEquals(e.eval("'foo' in obj"), Boolean.TRUE);
            assertEquals(e.eval("obj.foo"), "hello");
            assertEquals(obj.getMap().get("foo"), "hello");
            e.eval("delete obj.foo");
            assertFalse(obj.getMap().containsKey("foo"));
            assertEquals(e.eval("'foo' in obj"), Boolean.FALSE);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    // concatenated string attribute access on a JSObject
    public void consStringTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            final MapWrapperObject obj = new MapWrapperObject();
            e.put("obj", obj);
            e.put("f", "f");
            e.eval("obj[f + 'oo'] = 'bar';");

            assertEquals(obj.getMap().get("foo"), "bar");
            assertEquals(e.eval("obj[f + 'oo']"), "bar");
            assertEquals(e.eval("obj['foo']"), "bar");
            assertEquals(e.eval("f + 'oo' in obj"), Boolean.TRUE);
            assertEquals(e.eval("'foo' in obj"), Boolean.TRUE);
            e.eval("delete obj[f + 'oo']");
            assertFalse(obj.getMap().containsKey("foo"));
            assertEquals(e.eval("obj[f + 'oo']"), null);
            assertEquals(e.eval("obj['foo']"), null);
            assertEquals(e.eval("f + 'oo' in obj"), Boolean.FALSE);
            assertEquals(e.eval("'foo' in obj"), Boolean.FALSE);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    public static class BufferObject implements JSObject {
        private final IntBuffer buf;

        public BufferObject(final int size) {
            buf = IntBuffer.allocate(size);
        }

        public IntBuffer getBuffer() {
            return buf;
        }

        @Override
        public Object getMember(final String name) {
            return name.equals("length")? buf.capacity() : null;
        }

        @Override
        public boolean hasSlot(final int i) {
            return i > -1 && i < buf.capacity();
        }

        @Override
        public Object getSlot(final int i) {
            return buf.get(i);
        }

        @Override
        public boolean setSlot(final int i, final Object value) {
            buf.put(i, ((Number)value).intValue());
            return true;
        }
    }

    @Test
    // array-like indexed access for a JSObject
    public void indexedAccessTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            final BufferObject buf = new BufferObject(2);
            e.put("buf", buf);

            // array-like access on BufferObject objects
            assertEquals(e.eval("buf.length"), buf.getBuffer().capacity());
            e.eval("buf[0] = 23");
            assertEquals(buf.getBuffer().get(0), 23);
            assertEquals(e.eval("buf[0]"), 23);
            assertEquals(e.eval("buf[1]"), 0);
            buf.getBuffer().put(1, 42);
            assertEquals(e.eval("buf[1]"), 42);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    public static class Adder implements JSFunction {
        @Override
        public Object call(final Object thiz, final Object... args) {
            double res = 0.0;
            for (final Object arg : args) {
                res += ((Number)arg).doubleValue();
            }
            return res;
        }
    }

    @Test
    // a callable JSObject
    public void callableJSObjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            e.put("sum", new Adder());
            // check callability of Adder objects
            assertEquals(e.eval("typeof sum"), "function");
            assertEquals(((Number)e.eval("sum(1, 2, 3, 4, 5)")).intValue(), 15);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    public static class Factory implements JSFunction {
        @SuppressWarnings("unused")
        @Override
        public Object newObject(final Object... args) {
            return new HashMap<Object, Object>();
        }
    }

    @Test
    // a factory JSObject
    public void factoryJSObjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");
        try {
            e.put("Factory", new Factory());

            // check new on Factory
            assertEquals(e.eval("typeof Factory"), "function");
            assertEquals(e.eval("typeof new Factory()"), "object");
            assertEquals(e.eval("(new Factory()) instanceof java.util.Map"), Boolean.TRUE);
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    // iteration tests
    public void iteratingJSObjectTest() {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName(ENGINE);
        try {
            final MapWrapperObject obj = new MapWrapperObject();
            obj.setMember("foo", "hello");
            obj.setMember("bar", "world");
            e.put("obj", obj);

            // check for..in
            Object val = e.eval("var str = ''; for (i in obj) str += i; str");
            assertEquals(val.toString(), "foobar");

            // check for..each..in
            val = e.eval("var str = ''; for (i in obj) str += obj[i]; str");
            assertEquals(val.toString(), "helloworld");
        } catch (final Exception exp) {
            exp.printStackTrace();
            fail(exp.getMessage());
        }
    }

    @Test
    public void hidingInternalObjectsForJSObjectTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);

        final String code = "function func(obj) { obj.foo = [5, 5]; obj.bar = {} }";
        e.eval(code);

        // call the exposed function but pass user defined JSObject impl as argument
        ((Invocable)e).invokeFunction("func", new JSObject() {
            @Override
            public boolean setMember(final String name, final Object value) {
                assertTrue(value instanceof JSObject);
                return true;
            }
        });
    }

    @Test
    public void jsObjectDefaultToStringTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        final String STRING_VALUE = "This is a JSObject";
        JSObject jsobj = new JSObject() {
            @Override
            public String toString() {
                return STRING_VALUE;
            }
        };

        e.put("obj", jsobj);
        assertEquals(e.eval("obj.toString()"), STRING_VALUE);
    }

    @Test
    public void jsObjectReadableObjectToString() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        Bindings b = e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSFactory fac = (JSFactory)b;
        final JSSymbol toStringTag = fac.getToStringTagSymbol();
        JSObject jsobj = new JSObject() {
            private String toStringValue = "MyObject";

            @Override
            public boolean hasMember(JSSymbol sym) {
                return sym.equals(toStringTag);
            }

            @Override
            public String getMember(JSSymbol sym) {
                if (sym.equals(toStringTag)) {
                    return toStringValue;
                }
                return null;
            }

            @Override
            public boolean setMember(JSSymbol sym, Object value) {
                if (sym.equals(toStringTag)) {
                    toStringValue = value.toString();
                    return true;
                }
                return false;
            }
        };
        e.put("obj", jsobj);
        assertEquals(e.eval("Object.prototype.toString.call(obj)"), "[object MyObject]");
        e.eval("obj[Symbol.toStringTag] = 'SuperObject'");
        assertEquals(e.eval("Object.prototype.toString.call(obj)"), "[object SuperObject]");
    }

    @Test
    public void jsObjectOverriddenToStringTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        final String STRING_VALUE = "This is a JSObject";
        final String TOSTRING_OVERRIDE = "toString overridden value!";

        JSObject jsobj = new JSObject() {
            @Override
            public String toString() {
                return STRING_VALUE;
            }

            @Override
            public Object getMember(String name) {
                if (name.equals("toString")) {
                    return new JSFunction() {
                        @Override
                        public Object call(Object thiz, Object... args) {
                            return TOSTRING_OVERRIDE;
                        }
                    };
                }
                return null;
            }
        };

        e.put("obj", jsobj);
        // The JSObject provides it's own toString - that should be called!
        assertEquals(e.eval("obj.toString()"), TOSTRING_OVERRIDE);
    }

    @Test
    public void functionalInterfaceWrapperTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);

        e.put("giveme", JSFunction.supplier(() -> "hello"));
        assertTrue((Boolean)e.eval("giveme() === 'hello'"));

        // consumer that accepts and sets value to variable "foo"
        e.put("func", JSFunction.consumer(x -> {
            e.put("foo", x);
        }));

        Number num = (Number)e.eval("func(42); foo");
        assertEquals(num.intValue(), 42);

        e.put("square", JSFunction.apply(x -> (Integer)x*(Integer)x));
        num = (Number)e.eval("square(445)");
        assertEquals(num.intValue(), 445*445);

        e.put("isEmpty", JSFunction.predicate(x -> x.toString().isEmpty()));
        assertTrue((Boolean)e.eval("isEmpty('')"));
    }

    @Test
    public void toJSONTest() throws Exception {
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine e = engineManager.getEngineByName(ENGINE);
        JSFactory fac = (JSFactory)e.getBindings(ScriptContext.ENGINE_SCOPE);
        JSObject jsObj = new MapWrapperObject();
        jsObj.setMember("x", 44);
        jsObj.setMember("y", "hello");
        assertEquals(fac.toJSON(jsObj), "{\"x\":44,\"y\":\"hello\"}");
    }
}
