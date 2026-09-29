/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jsimport;

import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSPromise;
import org.openjdk.engine.javascript.V8ExecutionControl;
import org.openjdk.engine.javascript.V8ModuleResolver;
import org.openjdk.engine.javascript.V8ScriptEngine;
import org.openjdk.engine.javascript.V8ScriptException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Test JS import statement
 *
 * @test
 * @run testng jsimport.JSImportTest
 */

public class JSImportTest {
    static final String ENGINE_NAME = System.getProperty("jvmv8.engine.type", "v8-no-java");

    @BeforeClass
    public void setup() throws Throwable {
        Files.writeString(Path.of("mymod.js"), """
            export function add(a, b) {
                return a + b;
            }
            """);
        Files.writeString(Path.of("circA.js"), """
            import { valueB } from './circB.js';
            export const valueA = "Hello from A";
            export function testA() {
                return valueB;
            }
            """);
        Files.writeString(Path.of("circB.js"), """
            import { valueA } from './circA.js';
            export const valueB = "Hello from B";
            export function testB() {
                return valueA;
            }
            """);
    }

    @AfterClass
    public void tearDown() throws Throwable {
        Files.deleteIfExists(Path.of("mymod.js"));
        Files.deleteIfExists(Path.of("circA.js"));
        Files.deleteIfExists(Path.of("circB.js"));
    }

    @Test
    public void testEvalModuleWithImport() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        AtomicBoolean called = new AtomicBoolean();
        V8ModuleResolver resolver = (spec, attr) -> {
            called.set(true);
            return resolveModule(spec, attr);
        };
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSObject obj = ((V8ScriptEngine) e).loadModule("""
            import { add } from './mymod.js';
            export function func() {
              return add(2, 3);
            }
            """);
        assertTrue(called.get());
        assertEquals(obj.callMember("func"), 5);
    }

    @Test
    public void testImportWithAttributes() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        AtomicReference<Map<String, String>> attribsRef = new AtomicReference<>();
        V8ModuleResolver resolver = (spec, attr) -> {
            attribsRef.set(attr);
            return resolveModule(spec, attr);
        };
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSObject obj = ((V8ScriptEngine) e).loadModule("""
            import { add } from './mymod.js' with { key: "data", key2: "data2" };
            export function func() {
              return add(2, 3);
            }
            """);
        assertEquals(obj.callMember("func"), 5);

        Map<String, String> attribs = attribsRef.get();
        assertEquals(attribs.get("key"), "data");
        assertEquals(attribs.get("key2"), "data2");
    }

    @Test
    public void testImportException() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        V8ModuleResolver resolver = (_, _) -> {
            throw new RuntimeException("Testing Exception");
        };
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        try {
            ((V8ScriptEngine) e).loadModule("""
                    import { add } from './mymod.js';
                    export function func() {
                      return add(2, 3);
                    }
                    """);
        } catch (V8ScriptException ex) {
            assertTrue(ex.getEcmaError() instanceof RuntimeException);
            assertEquals(((RuntimeException) ex.getEcmaError()).getMessage(), "Testing Exception");
        }
    }

    @Test
    public void testImportResolverNullReturn() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        V8ModuleResolver resolver = (_, _) -> {
            return null;
        };
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        try {
            ((V8ScriptEngine) e).loadModule("""
                    import { add } from './mymod.js';
                    export function func() {
                      return add(2, 3);
                    }
                    """);
            throw new AssertionError("should have thrown exception");
        } catch (V8ScriptException ex) {
            assertTrue(ex.getEcmaError().toString().contains("Can not import module. Module resolver returned null."));
        }
    }

    @Test
    public void testImportDynamic() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        V8ModuleResolver resolver = JSImportTest::resolveModule;
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSObject obj = ((V8ScriptEngine) e).loadModule("""
            export async function func() {
                let mod = await import('./mymod.js');
                return 5;
            }
            """);

        CompletableFuture<Object> cf = toCompletableFuture(((JSPromise) obj.callMember("func")));
        ((V8ExecutionControl)e).runMicrotasks();
        assertEquals(cf.get(), 5);
    }

    @Test
    public void testImportDynamicAttribs() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        AtomicReference<Map<String, String>> attribsRef = new AtomicReference<>();
        V8ModuleResolver resolver = (spec, attr) -> {
            attribsRef.set(attr);
            return resolveModule(spec, attr);
        };
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSObject obj = ((V8ScriptEngine) e).loadModule("""
            export async function func() {
                let mod = await import('./mymod.js', {
                    with: { key: "data", key2: "data2" }
                });
                return 5;
            }
            """);

        CompletableFuture<Object> cf = toCompletableFuture(((JSPromise) obj.callMember("func")));
        ((V8ExecutionControl)e).runMicrotasks();
        assertEquals(cf.get(), 5);

        Map<String, String> attribs = attribsRef.get();
        assertEquals(attribs.get("key"), "data");
        assertEquals(attribs.get("key2"), "data2");
    }

    @Test
    public void testImportDynamicEval() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        V8ModuleResolver resolver = JSImportTest::resolveModule;
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSPromise promise = (JSPromise) e.eval("""
            async function func() {
                let mod = await import('./mymod.js');
                return 5;
            }

            func();
            """);

        CompletableFuture<Object> cf = toCompletableFuture(promise);
        ((V8ExecutionControl)e).runMicrotasks();
        assertEquals(cf.get(), 5);
    }

    @Test
    public void testImportDynamicException() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        V8ModuleResolver resolver = (_, _) -> {
            throw new RuntimeException("Testing Exception");
        };
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSObject obj = ((V8ScriptEngine) e).loadModule("""
            export async function func() {
                let mod = await import('./mymod.js');
                return 5;
            }
            """);

        CompletableFuture<Object> cf = toCompletableFuture(((JSPromise) obj.callMember("func")));
        ((V8ExecutionControl)e).runMicrotasks();
        try {
            cf.get();
        } catch (ExecutionException ex) {
            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof RuntimeException);
            assertEquals(ex.getCause().getMessage(), "Testing Exception");
        }
    }

    @Test
    public void testCircularImport() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        V8ModuleResolver resolver = JSImportTest::resolveModule;
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSObject obj = ((V8ScriptEngine) e).loadModule("""
            import { testA } from './circA.js';
            export function func() {
                return testA();
            }
            """);
        assertEquals(obj.callMember("func"), "Hello from B");

        JSObject objB = ((V8ScriptEngine) e).loadModule("""
            import { testB } from './circB.js';
            export function func() {
                return testB();
            }
            """);
        assertEquals(objB.callMember("func"), "Hello from A");
    }

    @Test
    public void testModuleResolverCaching() throws Throwable {
        ScriptEngineManager sem = new ScriptEngineManager();
        ScriptEngine e = sem.getEngineByName(ENGINE_NAME);

        java.util.concurrent.atomic.AtomicInteger callCountA = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger callCountB = new java.util.concurrent.atomic.AtomicInteger();

        V8ModuleResolver resolver = (spec, attr) -> {
            if ("./circA.js".equals(spec)) {
                callCountA.incrementAndGet();
            } else if ("./circB.js".equals(spec)) {
                callCountB.incrementAndGet();
            }
            return resolveModule(spec, attr);
        };
        e.getContext().setAttribute(V8ScriptEngine.MODULE_RESOLVER, resolver, ScriptContext.ENGINE_SCOPE);

        JSObject obj = ((V8ScriptEngine) e).loadModule("""
            import { testA } from './circA.js';
            export function func() {
                return testA();
            }
            """);
        assertEquals(obj.callMember("func"), "Hello from B");
        assertEquals(callCountA.get(), 1);
        assertEquals(callCountB.get(), 1);

        // Subsequent loadModule in the same engine should reuse cached modules without re-resolving
        JSObject obj2 = ((V8ScriptEngine) e).loadModule("""
            import { valueA } from './circA.js';
            export function func2() {
                return valueA;
            }
            """);
        assertEquals(obj2.callMember("func2"), "Hello from A");
        assertEquals(callCountA.get(), 1);
    }

    private static String resolveModule(String specifier, Map<String, String> importAttributes) {
        try {
            return Files.readString(Path.of(specifier));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static CompletableFuture<Object> toCompletableFuture(JSPromise promise) {
        CompletableFuture<Object> cf = new CompletableFuture<>();
        promise
            .then(JSFunction.consumer(cf::complete))
            ._catch(JSFunction.consumer(ex -> {
                if (ex instanceof Throwable t) {
                    cf.completeExceptionally(t);
                } else {
                    cf.completeExceptionally(new RuntimeException(ex.toString()));
                }
            }));
        return cf;
    }
}
