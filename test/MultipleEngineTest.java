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
import static org.testng.Assert.assertTrue;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.testng.annotations.Test;

/**
 * Test that we can create multiple, independent script engines and use those
 * independently.
 *
 * @test
 * @run testng MultipleEngineTest
 */
@SuppressWarnings("javadoc")
public class MultipleEngineTest {
    public static final String ENGINE = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @Test
    public void createAndUseManyEngine() throws ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();

        final ScriptEngine e1 = m.getEngineByName(ENGINE);
        e1.eval("var x = 33");

        final ScriptEngine e2 = m.getEngineByName(ENGINE);
        e2.eval("var x = 42");
    }

    @Test
    public void sharingSymbolsBetweenEngines() throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e1 = m.getEngineByName(ENGINE);

        // make sure that Symbol round trip from JS-Java-JS is fine.
        // In particular, identity of symbol is fine.
        e1.eval("var foo = Symbol.for('foo')");
        Object obj = e1.get("foo");
        assertEquals(obj.toString(), "foo");
        e1.put("bar", obj);
        assertTrue((boolean)e1.eval("bar === foo"));

        // expose that symbol to another engine. It should not crash of V8!
        ScriptEngine e2 = m.getEngineByName(ENGINE);
        e2.put("foo", obj);

        assertEquals(e2.eval("typeof foo"), "object");
    }
}
