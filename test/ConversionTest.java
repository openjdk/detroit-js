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

import org.openjdk.engine.javascript.V8ScriptEngineFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;


/**
 * Tests for automatic conversion of JS to Java arguments
 *
 * @test
 * @run testng ConversionTest
 */
@SuppressWarnings("javadoc")
public class ConversionTest {

    ScriptEngine scriptEngine;
    Object result;

    public interface JsTargetObject
    {

        void singleStringArg(String str);

        void twoStringArgs(String str, String str2);

        void threeStringArgs(String str, String str2, String str3);

        void singleStringArrayArg(String[] strings);

        void singleObjectArrayArg(Object[] objects);

        void singleStringListArg(List<String> strings);

        void singleObjectListArg(List<Object> objects);

        void singleStringSetArg(Set<String> strings);

    }


    JsTargetObject jsTargetObject = new JsTargetObject() {
        @Override
        public void singleStringArg(String str) {
            result = str;
        }

        @Override
        public void twoStringArgs(String str, String str2) {
            result = new String[] { str, str2 };
        }

        @Override
        public void threeStringArgs(String str, String str2, String str3) {
            result = new String[] { str, str2, str3 };
        }

        @Override
        public void singleStringArrayArg(String[] strings) {
            result = strings;
        }

        @Override
        public void singleObjectArrayArg(Object[] objects) {
            result = objects;
        }

        @Override
        public void singleStringListArg(List<String> strings) {
            result = strings;
        }

        @Override
        public void singleObjectListArg(List<Object> objects) {
            result = objects;
        }

        @Override
        public void singleStringSetArg(Set<String> strings) {
            result = strings;
        }
    };

    @BeforeTest
    public void setup() throws ScriptException
    {
        ScriptEngineFactory scriptEngineFactory = new V8ScriptEngineFactory();
        scriptEngine = scriptEngineFactory.getScriptEngine();
        scriptEngine.put("target", jsTargetObject);
    }

    @Test
    public void singleStringArgInvokedWithNull() throws ScriptException
    {
        scriptEngine.eval("target.singleStringArg(null)");
        Assert.assertEquals(result, null);
    }

    @Test
    public void singleStringArgInvokedWithUndefined() throws ScriptException
    {
        scriptEngine.eval("target.singleStringArg(undefined)");
        Assert.assertEquals(result, "undefined");
    }

    @Test
    public void twoStringArgInvokedWithStringNull() throws ScriptException
    {
        scriptEngine.eval("target.twoStringArgs('foo', null)");
        Assert.assertEquals(result, new Object[] { "foo", null });
    }

    @Test
    public void twoStringArgInvokedWithNullNull() throws ScriptException
    {
        scriptEngine.eval("target.twoStringArgs(null, null)");
        Assert.assertEquals(result, new Object[] { null, null });
    }

    @Test
    public void twoStringArgInvokedWithNullUndefined() throws ScriptException
    {
        scriptEngine.eval("target.twoStringArgs(null, undefined)");
        Assert.assertEquals(result, new Object[] { null, "undefined" });
    }

    @Test(expectedExceptions = ScriptException.class)
    public void threeStringArgInvokedWithString() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs('foo')");
        Assert.assertEquals(result, new String[] { "foo", null, null });
    }

    @Test(expectedExceptions = ScriptException.class)
    public void threeStringArgInvokedWithStringNull() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs('foo', null)");
        Assert.assertEquals(result, new String[] { "foo", null, null });
    }

    @Test(expectedExceptions = ScriptException.class)
    public void threeStringArgInvokedWithStringString() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs('foo', 'bar')");
        Assert.assertEquals(result, new String[] { "foo", "bar", null });
    }

    @Test
    public void threeStringArgInvokedWithStringStringString() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs('foo', 'bar', 'baz')");
        Assert.assertEquals(result, new String[] { "foo", "bar", "baz" });
    }

    @Test
    public void threeStringArgInvokedWithStringNullNull() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs('foo', null, null)");
        Assert.assertEquals(result, new String[] { "foo", null, null });
    }

    @Test
    public void threeStringArgInvokedWithNullNullNull() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs(null, null, null)");
        Assert.assertEquals(result, new String[] { null, null, null });
    }

    @Test
    public void threeStringArgInvokedWithStringUndefinedUndefined() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs('foo', undefined, undefined)");
        Assert.assertEquals(result, new String[] { "foo", "undefined", "undefined" });
    }

    @Test
    public void threeStringArgInvokedWithStringStringUndefined() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs('foo', 'bar', undefined)");
        Assert.assertEquals(result, new String[] { "foo", "bar", "undefined" });
    }

    @Test
    public void threeStringArgInvokedWithNullNullUndefined() throws ScriptException
    {
        scriptEngine.eval("target.threeStringArgs(null, null, undefined)");
        Assert.assertEquals(result, new String[] { null, null, "undefined" });
    }

    @Test
    public void singleStringArrayArgInvokedWithNull() throws ScriptException
    {
        scriptEngine.eval("target.singleStringArrayArg(null)");
        Assert.assertEquals(result, null);
    }

    @Test
    public void singleStringArrayArgInvokedWithEmptyJavaArray() throws ScriptException {
        String[] strings = new String[]{};
        scriptEngine.put("arg", strings);
        scriptEngine.eval("target.singleStringArrayArg(arg)");
        Assert.assertEquals(result, strings);
    }

    @Test
    public void singleStringArrayArgInvokedWithSingleStringElementJsArray() throws ScriptException
    {
        scriptEngine.eval("target.singleStringArrayArg(['foo'])");
        Assert.assertEquals(result, new String[] { "foo" });

    }

    @Test
    public void singleObjectArrayArgInvokedWithNull() throws ScriptException
    {
        scriptEngine.eval("target.singleObjectArrayArg(null)");
        Assert.assertEquals(result, null);
    }

    @Test
    public void singleObjectArrayArgInvokedWithEmptyJavaArray() throws ScriptException
    {
        String[] strings = new String[] {};
        scriptEngine.put("arg", strings);
        scriptEngine.eval("target.singleObjectArrayArg(arg)");
        Assert.assertEquals(result, strings);
    }

    @Test
    public void singleObjectArrayArgInvokedWithSingleStringElementJsArray() throws ScriptException
    {
        scriptEngine.eval("target.singleObjectArrayArg(['foo'])");
        Assert.assertEquals(result, new String[] { "foo" });

    }

    @Test
    public void singleStringListArgInvokedWithNull() throws ScriptException
    {
        scriptEngine.eval("target.singleStringListArg(null)");
        Assert.assertEquals(result, null);
    }

    @Test
    public void singleStringListArgInvokedWithEmptyJavaList() throws ScriptException
    {
        List<String> strings = new ArrayList<>();
        scriptEngine.put("arg", strings);
        scriptEngine.eval("target.singleStringListArg(arg)");
        Assert.assertEquals(result, strings);
    }

    @Test
    public void singleStringListArgInvokedWithSingleStringElementJsArray() throws ScriptException
    {
        List<String> strings = new ArrayList<>();
        strings.add("foo");
        scriptEngine.eval("target.singleStringListArg(['foo'])");
        Assert.assertEquals(result, strings);
    }

    @Test
    public void singleObjectListArgInvokedWithNull() throws ScriptException
    {
        scriptEngine.eval("target.singleObjectListArg(null)");
        Assert.assertEquals(result, null);
    }

    @Test
    public void singleObjectListArgInvokedWithEmptyJavaList() throws ScriptException
    {
        List<Object> strings = new ArrayList<>();
        scriptEngine.put("arg", strings);
        scriptEngine.eval("target.singleObjectListArg(arg)");
        Assert.assertEquals(result, strings);
    }

    @Test
    public void singleObjectListArgInvokedWithSingleStringElementJsArray() throws ScriptException
    {
        List<Object> strings = new ArrayList<>();
        strings.add("foo");
        scriptEngine.eval("target.singleObjectListArg(['foo'])");
        Assert.assertEquals(result, strings);
    }

    @Test
    public void singleStringSetArgInvokedWithNull() throws ScriptException
    {
        scriptEngine.eval("target.singleStringSetArg(null)");
        Assert.assertEquals(result, null);
    }

    @Test
    public void singleStringSetArgInvokedWithEmptyJavaSet() throws ScriptException
    {
        Set<String> strings = new HashSet<>();
        scriptEngine.put("arg", strings);
        scriptEngine.eval("target.singleStringSetArg(arg)");
        Assert.assertEquals(result, strings);
    }


}
