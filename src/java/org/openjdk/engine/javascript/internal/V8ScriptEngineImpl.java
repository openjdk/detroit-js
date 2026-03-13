/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package org.openjdk.engine.javascript.internal;

import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.V8ExecutionControl;
import org.openjdk.engine.javascript.V8Inspector;
import org.openjdk.engine.javascript.V8ModuleResolver;
import org.openjdk.engine.javascript.V8ScriptCompiler;
import org.openjdk.engine.javascript.V8ScriptEngine;
import org.openjdk.engine.javascript.V8Undefined;

import java.io.IOException;
import java.io.Reader;
import java.lang.ref.Cleaner;
import java.lang.reflect.Method;
import java.util.Objects;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

public final class V8ScriptEngineImpl extends AbstractScriptEngine implements Compilable, Invocable,
        AutoCloseable, V8ExecutionControl, V8ScriptCompiler, V8ScriptEngine {

    private final ScriptEngineFactory factory;
    private final V8Isolate isolate;
    private final Cleaner.Cleanable engineCleaner;

    @SuppressWarnings({"OverridableMethodCallInConstructor"})
    public V8ScriptEngineImpl(ScriptEngineFactory factory, ClassLoader loader, boolean inspector) {
        this.factory = factory;

        try {
            Bindings engineScope;
            boolean javaSupport = loader != null;
            this.isolate = V8.createIsolate(javaSupport, inspector);
            engineScope = inspector? createBindings("default") : createBindings();
            engineCleaner = RefCleaner.register(this, isolate::cleanerThunk);

            isolate.setClassLoader(loader);
            getContext().setBindings(engineScope, ScriptContext.ENGINE_SCOPE);
            isolate.setScriptContext(getContext()); // set as default
        } catch (Throwable th) {
            // javax.script.ScriptEngineManager eats exceptions!
            // Show any initialization issue unconditionally!
            th.printStackTrace();
            if (th instanceof RuntimeException) {
                throw (RuntimeException)th;
            } else {
                throw new RuntimeException(th);
            }
        }
    }

    /**
     * Get the V8Inspector object to interact with the inspector.
     *
     * @return V8Inspector object.
     * @throws IllegalStateException if the engine is not initialized with V8 inspector enabled
     */
    @Override
    public V8Inspector getInspector() {
        if (isolate.isInspectorEnabled()) {
            return (V8Inspector)isolate;
        }

        throw new IllegalStateException("V8 Inspector not enabled!");
    }

    @Override
    public synchronized void close() {
        if (engineCleaner != null) {
            engineCleaner.clean();
        }
    }

    @Override
    public Object eval(String script, ScriptContext sc) throws ScriptException {
        Objects.requireNonNull(script);
        Objects.requireNonNull(sc);
        return V8.eval(getV8Global(sc), getFileName(sc), script, sc);
    }

    @Override
    public Object eval(Reader reader, ScriptContext sc) throws ScriptException {
        try {
            return eval(V8.readAll(Objects.requireNonNull(reader)), sc);
        } catch (IOException ex) {
            throw new ScriptException(ex);
        }
    }

    public JSObject loadModule(String moduleSource) throws ScriptException {
        return loadModule(moduleSource, context);
    }

    public JSObject loadModule(String moduleSource, ScriptContext sc) throws ScriptException {
        return V8.loadModule(getV8Global(sc), getFileName(sc), moduleSource, sc);
    }

    @Override
    public Bindings createBindings() {
        return V8Bindings.of(getV8Isolate());
    }

    /**
     * Create a new Bindings object that can be used as ENGINE_SCOPE with this engine.
     *
     * @param name readable name for the new Bindings to use for debugging.
     * @return a new Bindings object.
     * @throws NullPointerException if name is null.
     */
    public Bindings createBindings(String name) {
        return V8Bindings.of(getV8Isolate(), Objects.requireNonNull(name));
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    @Override
    public CompiledScript compile(String script) throws ScriptException {
        Objects.requireNonNull(script);
        V8UnboundScript compiledScript = V8.compile(getV8Global(getContext()), getFileName(getContext()), script);
        return new V8CompiledScript(this, compiledScript);
    }

    @Override
    public CompiledScript compile(Reader reader) throws ScriptException {
        try {
            return compile(V8.readAll(Objects.requireNonNull(reader)));
        } catch (IOException ex) {
            throw new ScriptException(ex);
        }
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
        Objects.requireNonNull(name);
        if (thiz == null || !(thiz instanceof V8Object)) {
            throw new IllegalArgumentException("'this' must be a V8 object");
        }

        V8Object self = (V8Object)thiz;
        V8Isolate isolate = getV8Isolate();
        if (! self.isFromIsolate(isolate)) {
            throw new IllegalArgumentException("script object belongs to another engine");
        }

        return V8.invokeMethod((V8Object)thiz, name, args, getContext());
    }

    @Override
    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
        return invokeMethod(getV8Global(getContext()), name, args);
    }

    @Override
    public <T> T getInterface(Class<T> iface) {
        return getInterface(getV8Global(getContext()), iface);
    }

    private final V8InterfaceImplementor ifaceImplementor =
        new V8InterfaceImplementor() {
            @Override
            public void checkThis(Object thiz) {
                super.checkThis(thiz);
                assert thiz instanceof V8Object;
                V8Object self = (V8Object)thiz;
                V8Isolate isolate = getV8Isolate();
                if (! self.isFromIsolate(isolate)) {
                    throw new IllegalArgumentException("script object belongs to another engine");
                }
            }

            @Override
            public Object invoke(V8Object thiz, Method method, Object[] args) throws Exception {
                return invokeMethod(thiz, method.getName(), args);
            }
        };

    @Override
    public <T> T getInterface(Object thiz, Class<T> iface) {
        return ifaceImplementor.getInterface(thiz, iface);
    }


    // V8ExecutionControl methods
    @Override
    public synchronized void terminateExecution() {
        V8.terminateExecution(isolate);
    }

    @Override
    public synchronized void requestInterrupt(Runnable callback) {
        V8.requestInterrupt(isolate, Objects.requireNonNull(callback));
    }

    @Override
    public void runMicrotasks() {
        V8.runMicrotasks(isolate);
    }

    @Override
    public void enqueueMicrotask(JSFunction microtask) {
        V8.enqueueMicrotask(isolate, getV8Global(getContext()), Objects.requireNonNull(microtask));
    }

    @Override
    public Object throwException(Object exception) {
        Objects.requireNonNull(exception);
        return V8.throwException(isolate, Objects.requireNonNull(exception));
    }

    @Override
    public StackTraceElement[] getCurrentStackTrace() {
         return V8.getCurrentStackTrace(getV8Global(getContext()));
    }

    // V8ScriptCompiler methods
    @Override
    public JSObject compileFunction(String script, String[] arguments,
                JSObject[] extensions, ScriptContext sc) throws ScriptException {
        Objects.requireNonNull(script);
        Objects.requireNonNull(sc);
        V8Object v8Global = getV8Global(sc);

        V8Object[] v8Exts = extensions == null? null : new V8Object[extensions.length];
        if (extensions != null) {
            System.arraycopy(extensions, 0, v8Exts, 0, v8Exts.length);
        }
        return V8.compileFunctionInContext(v8Global, getFileName(sc), script, arguments, v8Exts);
    }

    @Override
    public JSObject compileFunction(String script, String[] arguments,
                JSObject[] extensions) throws ScriptException {
        return compileFunction(script, arguments, extensions, getContext());
    }

    // package-private helpers
    V8Isolate getV8Isolate() {
        return isolate;
    }

    // get the V8 global object associated with the given ScriptContext
    static V8Object getV8Global(ScriptContext sc) {
        return getV8Bindings(sc).getV8Global();
    }

    // For now, we assume ENGINE_SCOPE of ScriptContexts are of V8Bindings type
    static V8Bindings getV8Bindings(ScriptContext sc) {
        Bindings bindings = sc.getBindings(ScriptContext.ENGINE_SCOPE);
        // FIXME: for now, ENGINE_SCOPE must a V8Bindings object.
        if (! (bindings instanceof V8Bindings)) {
            throw new IllegalArgumentException("ENGINE_SCOPE Bindings is not a V8Bindings");
        }
        return (V8Bindings)bindings;
    }

    static String getFileName(ScriptContext sc) {
        Object name = sc.getAttribute(FILENAME);
        if (name == null || name == V8Undefined.INSTANCE) {
            return "<eval>";
        }
        return name.toString();
    }

    static V8ModuleResolver getModuleResolver(ScriptContext sc) {
        return (V8ModuleResolver) sc.getAttribute(MODULE_RESOLVER);
    }
}
