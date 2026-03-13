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

import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.V8ScriptException;
import org.openjdk.engine.javascript.V8Undefined;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import javax.script.ScriptContext;
import javax.script.ScriptException;

/**
 * Function valued properties added to the V8 global object.
 */
@SuppressWarnings("removal")
final class V8GlobalFunctions {
    // All static utility
    private V8GlobalFunctions() {}

    // called from native
    private static void print(Object thiz, Object... args) {
        PrintWriter pw = null;

        // try to get jsr-223 ScriptContext's current Writer
        ScriptContext ctx = V8.getCurrentIsolate().getScriptContext();
        if (ctx != null) {
            Writer w = ctx.getWriter();
            pw = new PrintWriter(w);
        } else  if (pw == null) {
            // no luck! use a System.out based PrintWriter.
            pw = new PrintWriter(System.out);
        }

        // whitespace separated printing of toString of each argument
        for (int i = 0; i < args.length; i++) {
            pw.print(args[i]);
            if (i < args.length - 1) {
                pw.print(' ');
            }
        }

        // newline at the end & flush
        pw.println();
        pw.flush();
    }

    // load helper

    // pseudo URL prefix to load scripts from java CLASSPATH
    private static final String LOAD_CLASSPATH = "classpath:";

    // pseudo URL prefix to load scripts from jvmv8 resources
    private static final String LOAD_JVMV8 = "jvmv8:";

    // Simple script source with name and actual script string
    private static class Source {
        final String name;
        final String script;

        Source(String name, String script) {
            this.name = name;
            this.script = script;
        }
    }

    private static Source loadInternal(final String srcStr, final String prefix, final String resourcePath) {
        if (srcStr.startsWith(prefix)) {
            final String resource = resourcePath + srcStr.substring(prefix.length());
            try {
                final InputStream resStream = V8.class.getResourceAsStream(resource);
                final Reader reader = new InputStreamReader(resStream);
                return resStream != null ? new Source(srcStr, V8.readAll(reader)) : null;
            } catch (final IOException exp) {
                return null;
            }
        }

        return null;
    }

    // various sources for script supported using this utility (file, URL, string, JSObject, Map)
    private static Source readScript(V8Isolate isolate, Object src) throws IOException {
        Objects.requireNonNull(src);

        Reader reader = null;
        String name = src.toString();

        // load accepts a String (which could be a URL or a file name), a File, a URL
        // or a JSObject that has "name" and "source" (string valued) properties.
        if (src instanceof String) {
            final String srcStr = (String)src;
            if (srcStr.startsWith(LOAD_CLASSPATH)) {
                final URL url = isolate.getResource(srcStr.substring(LOAD_CLASSPATH.length()));
                reader = url != null? new InputStreamReader(url.openStream()) : null;
            } else {
                final File file = new File(srcStr);
                if (srcStr.indexOf(':') != -1) {
                    Source source = null;
                    if ((source = loadInternal(srcStr, LOAD_JVMV8, "resources/")) == null) {
                        URL url;
                        try {
                            //check for malformed url. if malformed, it may still be a valid file
                            url = URI.create(srcStr).toURL();
                        } catch (final MalformedURLException e) {
                            url = file.toURI().toURL();
                        }
                        reader = new InputStreamReader(url.openStream());
                    } else {
                        return source;
                    }
                } else if (file.isFile()) {
                    if (srcStr.indexOf(':') != -1) {
                        reader = new InputStreamReader(file.toURI().toURL().openStream());
                    } else if (file.isFile()) {
                        reader = new FileReader(file);
                    }
                }
            }
        } else if (src instanceof File && ((File)src).isFile()) {
            final File file = (File)src;
            reader = new FileReader(file);
        } else if (src instanceof URL) {
            final URL url = (URL)src;
            reader = new InputStreamReader(url.openStream());
        } else if (src instanceof JSObject) {
            final JSObject sobj = (JSObject)src;
            if (sobj.hasMember("script") && sobj.hasMember("name")) {
                final String script = sobj.getMember("script").toString();
                name   = sobj.getMember("name").toString();
                return new Source(name, script);
            }
        } else if (src instanceof Map) {
            final Map<?,?> map = (Map<?,?>)src;
            if (map.containsKey("script") && map.containsKey("name")) {
                final String script = map.get("script").toString();
                name   = map.get("name").toString();
                return new Source(name, script);
            }
        }

        if (reader == null) {
            return null;
        }

        return new Source(name, V8.readAll(reader));
    }

    private static V8Object asV8Object(Object thiz) {
        return (thiz instanceof V8Object)? (V8Object)thiz : null;
    }

    private static Source asSource(V8Object global, Object src) {
        V8Isolate isolate = global.getIsolate();
        try {
            Source source = readScript(isolate, src);
            if (source == null) {
                String msg = "Cannot read script from: " + src.toString();
                return (Source) V8.throwException(isolate, V8.newTypeError(global, msg));
            }
            return source;
        } catch (IOException exp) {
            JSObject jsExp = V8.newTypeError(global, "IOException while reading " + src.toString());
            jsExp.setMember("javaException", exp);
            return (Source) V8.throwException(isolate, jsExp);
        }
    }

    // called from native
    static Object load(Object thiz, Object src) throws ScriptException {
        V8Object global = asV8Object(thiz);
        if (global == null) {
            return V8.throwException(V8.getCurrentIsolate(), "'this' is not a script object to load");
        }

        if (src == null || src == V8Undefined.INSTANCE) {
            return V8.throwException(global.getIsolate(), V8.newTypeError(global, "load expects script file or URL"));
        }

        Source source = asSource(global, src);
        return source != null? V8.eval(global, source.name, source.script) : null;
    }

    // pool containing java enabled V8Isolates
    private static final ConcurrentLinkedQueue<V8Isolate> theJavaPool = new ConcurrentLinkedQueue<V8Isolate>();
    // pool containing java disabled V8Isolates
    private static final ConcurrentLinkedQueue<V8Isolate> theNoJavaPool = new ConcurrentLinkedQueue<V8Isolate>();
    // default Executor for fork
    private static final Executor defaultExecutor = new ForkJoinPool();

    // barrow a free V8Isolate
    private static V8Isolate barrowIsolate(V8Isolate curIsolate) {
        boolean javaSupport = curIsolate.isJavaSupported();
        ConcurrentLinkedQueue<V8Isolate> pool = javaSupport? theJavaPool : theNoJavaPool;
        V8Isolate isolate;
        if ((isolate = pool.poll()) == null) {
            isolate = V8.createIsolate(javaSupport, false);
        }

        if (V8.DEBUG) {
            V8.debugPrintf("Isolate 0x%x barrowed from pool", isolate.getReference());
        }
        return isolate;
    }

    // return back the V8Isolate
    private static void returnBackIsolate(V8Isolate isolate) {
        if (V8.DEBUG) {
             V8.debugPrintf("Isolate 0x%x freed to pool", isolate.getReference());
        }
        if (isolate.isJavaSupported()) {
            theJavaPool.offer(isolate);
        } else {
            theNoJavaPool.offer(isolate);
        }
    }

    // is the given value a JS primitive?
    private static boolean isJSPrimitive(Object obj) {
         return obj instanceof String || obj instanceof Integer ||
             obj instanceof Double || obj instanceof Boolean ||
             obj == null || obj == V8Undefined.INSTANCE;
    }

    // translate non-JS primitive values as strings
    private static Object translateJSNonPrimitive(Object obj) {
        return isJSPrimitive(obj)? obj : obj.toString();
    }

    // called from native
    /**
     * forkOnExecutor executes the given script concurrently with the calling script. To support concurrent execution,
     * we maintain pool(s) of V8Isolate objects. A fresh global object is created in a free V8Isolate and script
     * is evaluated in it. User can optionally pass an Executor on which the concurrent execution will occur.
     * By default, a ForkJoinPool executor is used for concurrent execution. forkOnExecutor caller can also optionally
     * pass arguments to the script. Only JS primitive values can be passed as arguments. Also, only JS primitive
     * values are allowed as script eval result. Non-primitive values are converted to Strings.
     *
     * Arguments are passed by creating an array named "arguments" in the fresh global scope object created.
     * All non-JS primitive values are converted to strings.
     *
     * @param thiz The global of the caller
     * @param src source of the script that is evaluated asynchronously
     * @param executor (optional) Executor to run the script concurrently
     * @param args (optional) arguments passed to the asynchornous script
     * @return A CompletableFuture for the result of the asynchoronous execution
     */
    static Object forkOnExecutor(Object thiz, Object src, Object executor, Object[] args) throws ScriptException {
        // make sure we've got a script object for 'this'
        V8Object global = asV8Object(thiz);
        if (global == null) {
            return V8.throwException(V8.getCurrentIsolate(), "'this' is not a script object to fork");
        }

        // check script src
        V8Isolate isolate = global.getIsolate();
        if (src == null || src == V8Undefined.INSTANCE) {
            return V8.throwException(global.getIsolate(), V8.newTypeError(global, "fork expects script file or URL"));
        }

        // load and check script source
        Source source;
        if (src instanceof V8Function) {
            // syntax sugar. User can pass a script function. We derive script source from it!
            String script = "(" + src.toString() + ").apply(this, arguments)";
            source = new Source(((V8Function)src).getName(), script);
        } else {
            source = asSource(global, src);
            if (source == null) {
                return null;
            }
        }

        // second argument, if present, should be an Executor object
        if (executor != null && !(executor instanceof Executor)) {
            return V8.throwException(global.getIsolate(), V8.newTypeError(global, "fork expects Executor or null"));
        }

        Executor exec = executor instanceof Executor? (Executor)executor : defaultExecutor;

        // get a free V8Isolate and copy the right state from current V8Isolate
        final V8Isolate otherIsolate = barrowIsolate(isolate);
        otherIsolate.setClassLoader(isolate.getClassLoader());

        // create a new global object in the free V8Isolate
        V8Object newGlobal = otherIsolate.createGlobal(
            otherIsolate.isInspectorEnabled()?  "fork: " + source.name : null);

        // set "arguments" array in the new global
        JSObject jsArgs;
        if (args != null) {
            jsArgs = V8.newArray(newGlobal, args.length);
            for (int i = 0; i < args.length; i++) {
                // make sure that non-JS primitive values are translated
                jsArgs.setSlot(i, translateJSNonPrimitive(args[i]));
            }
        } else {
            // no args passed. create an empty "arguments" array for convenience
            jsArgs = V8.newArray(newGlobal, 0);
        }
        newGlobal.setMember("arguments", jsArgs);

        // asynchronous eval of the script
        return CompletableFuture.supplyAsync(() -> {
            try {
                // make sure that non-JS primitive values are translated
                return translateJSNonPrimitive(V8.eval(newGlobal, source.name, source.script));
            } catch (ScriptException se) {
                if (se instanceof V8ScriptException) {
                    // make sure that exception does not leak non-JS primitive value!
                    V8ScriptException vse = (V8ScriptException)se;
                    se = new V8ScriptException(vse.getMessage(), vse.getFileName(), vse.getLineNumber(),
                            vse.getColumnNumber(), vse.getSourceLine(), translateJSNonPrimitive(vse.getEcmaError()));
                }
                if (V8.DEBUG) {
                    V8.debugPrintf("Exception from fork: %s", se.getMessage());
                    se.printStackTrace();
                }
                throw new RuntimeException(se);
            } finally {
                // restore Java state before releasing the V8Isolate
                otherIsolate.setClassLoader(null);
                returnBackIsolate(otherIsolate);
            }
        }, exec);
    }
}
