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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.Objects;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.openjdk.engine.javascript.JSFunction;
import org.openjdk.engine.javascript.JSObject;
import org.openjdk.engine.javascript.JSObject.PropertyAttribute;
import org.openjdk.engine.javascript.JSSymbol;
import org.openjdk.engine.javascript.V8Inspector;
import org.openjdk.engine.javascript.V8ModuleResolver;

@SuppressWarnings("removal")
public final class V8 {
    public static final boolean DEBUG = Options.getBooleanProperty("jvmv8.debug", false);

    static final boolean LOGGING = Options.getBooleanProperty("jvmv8.v8.logging", false);

    static final boolean DUMP_GENERATED_SCRIPT = Options.getBooleanProperty("jvmv8.dump.generated.script", false);

    static final boolean SECURITY_TOKEN_PER_CONTEXT = Options.getBooleanProperty("jvmv8.security.token.per.context", false);

    private static final String MESSAGES_RESOURCE = "org.openjdk.engine.javascript.internal.resources.messages";

    private static final ResourceBundle MESSAGES_BUNDLE;
    static {
        MESSAGES_BUNDLE = ResourceBundle.getBundle(MESSAGES_RESOURCE, Locale.getDefault());
    }

    private static volatile boolean initialized = false;

    public static void debugPrintf(String format, Object... args) {
        System.err.printf("JVMV8 DEBUG: " + format + "\n", args);
    }

   private static void initializeImpl() {
        if (!initialized) {
            V8.initialized = true;
            try {
                Path tmpDir = Path.of(System.getProperty("org.openjdk.engine.javascript.TMP_DIR",
                        System.getProperty("java.io.tmpdir")));
                if (!Files.isWritable(tmpDir) && Files.isExecutable(tmpDir)) {
                    throw new IllegalStateException("Temp dir is not writable or executable." +
                            " Set the system property 'org.openjdk.engine.javascript.TMP_DIR'" +
                            " to a writeable and executable directory");
                }
                String libName = System.mapLibraryName("jvmv8");
                Path libFile = Files.createTempFile(tmpDir, "jvmv8", ".so");
                try (InputStream in = V8.class.getResourceAsStream(libName);
                     OutputStream out = Files.newOutputStream(libFile)) {
                    in.transferTo(out);
                }
                System.load(libFile.toAbsolutePath().toString());
                libFile.toFile().deleteOnExit(); // don't create too much garbage
            } catch (SecurityException | UnsatisfiedLinkError | IOException ex) {
                if (DEBUG) {
                    ex.printStackTrace();
                }
                throw new RuntimeException(ex);
            }
        }
    }

    // called from native
    public static void initialize() {
        initializeImpl();
    }

    private V8() {
    }

    private static final ThreadLocal<V8Isolate> currentIsolate = new ThreadLocal<>();
    public static V8Isolate getCurrentIsolate() {
        V8Isolate isolate = currentIsolate.get();
        if (isolate == null) {
            throw new IllegalStateException("no current V8 Isolate");
        }
        return isolate;
    }

    private static <T> T runInIsolate(final V8Isolate newIsolate, final Callable<T> callable) throws ScriptException {
        final V8Isolate oldIsolate = currentIsolate.get();
        try {
            currentIsolate.set(newIsolate);
            return callable.call();
        } catch (ScriptException se) {
            throw se;
        } catch (RuntimeException re) {
            throw (RuntimeException)re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            currentIsolate.set(oldIsolate);
        }
    }

    private static <T> T runInIsolate(final V8Isolate newIsolate, final Supplier<T> func) {
        final V8Isolate oldIsolate = currentIsolate.get();
        try {
            currentIsolate.set(newIsolate);
            return func.get();
        } finally {
            currentIsolate.set(oldIsolate);
        }
    }

    private native static long createIsolate0(boolean javaSupport, boolean inspector);
    static V8Isolate createIsolate(boolean javaSupport, boolean inspector) {
        long isolateRef = createIsolate0(javaSupport, inspector);
        if (V8.DEBUG) {
            if (isolateRef != 0L) {
                debugPrintf("New V8 Isolate created: 0x%x", isolateRef);
            } else {
                debugPrintf("Failed to create a new V8 Isolate!", isolateRef);
            }
        }

        // Fail early!
        if (isolateRef == 0L) {
            throw new RuntimeException("Failed to create a new V8 Isolate!");
        }

        if (V8.LOGGING) {
            V8.setEventLogger0(isolateRef);
        }

        V8Isolate isolate = V8Isolate.create(isolateRef, javaSupport, inspector);
        if (inspector) {
            registerIsolate0(isolateRef, isolate);
        }
        return isolate;
    }

    private native static void disposeIsolate0(long isolateRef);
    static void disposeIsolate(long isolateRef) {
        if (V8.DEBUG) {
            debugPrintf("Disposing V8 Isolate: 0x%x", isolateRef);
        }
        disposeIsolate0(isolateRef);
    }

    private native static V8Object createGlobal0(long isolateRef, boolean setSecurityToken, String contextName);
    static V8Object createGlobal(V8Isolate isolate, String contextName) {
        long isolateRef = isolate.getReference();
        V8Object global = runInIsolate(isolate, (Supplier<V8Object>)() -> {
            return createGlobal0(isolateRef, !(SECURITY_TOKEN_PER_CONTEXT), contextName);
        });
        if (V8.DEBUG) {
            if (global != null) {
                debugPrintf("New V8 Global created: 0x%x (%s)", global.getReference(), contextName);
            } else {
                debugPrintf("Failed to create a New V8 Global!");
            }
        }

        // Fail early!
        if (global == null) {
            throw new RuntimeException("Failed to create a new V8 Global");
        }

        return global;
    }

    private native static void releaseReference0(long isolateRef, long ref);
    static void releaseReference(long isolateRef, String className, long ref) {
        if (V8.DEBUG) {
            debugPrintf("Releasing %s: 0x%x", className, ref);
        }
        releaseReference0(isolateRef, ref);
    }

    // stack trace helper
    private native static StackTraceElement[] getStackTrace0(long isolateRef, long exceptionRef);
    public static StackTraceElement[] getStackTrace(V8Object exception) {
        final V8Isolate isolate = exception.getIsolate();
        return runInIsolate(isolate, (Supplier<StackTraceElement[]>)() -> {
            synchronized(isolate) {
                StackTraceElement[] frames = getStackTrace0(isolate.getReference(), exception.getReference());
                return frames != null? frames : new StackTraceElement[0];
            }
        });
    }

    private native static StackTraceElement[] getStackTrace1(long isolateRef, long globalRef);
    static StackTraceElement[] getCurrentStackTrace(V8Object global) {
        V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<StackTraceElement[]>)() -> {
            synchronized(isolate) {
                StackTraceElement[] frames = getStackTrace1(isolate.getReference(), global.getReference());
                return frames != null? frames : new StackTraceElement[0];
            }
        });
    }

    // Script compile, eval helpers
    private native static long compile0(long isolateRef, long globalRef, String name, String script) throws ScriptException;
    static V8UnboundScript compile(V8Object global, String name, String script) throws ScriptException {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Callable<V8UnboundScript>)() -> {
            synchronized(isolate) {
                if (V8.DEBUG) {
                    debugPrintf("Compiling %s in global 0x%x", name, global.getReference());
                }
                long ref = compile0(isolate.getReference(), global.getReference(), name, script);
                if (ref != 0 && V8.DEBUG) {
                    debugPrintf("New compiled script 0x%x (global 0x%x)", ref, global.getReference());
                }
                return V8UnboundScript.create(isolate, ref);
            }
        });
    }

    private native static V8Function compileFunctionInContext0(long isolateRef, long globalRef,
            String name, String script, String[] arguments, V8Object[] extensions) throws ScriptException;
    static V8Function compileFunctionInContext(V8Object global, String name, String script,
            String[] arguments, V8Object[] extensions) throws ScriptException {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Callable<V8Function>)() -> {
            synchronized(isolate) {
                if (V8.DEBUG) {
                    debugPrintf("Compiling %s in global 0x%x", name, global.getReference());
                }
                return compileFunctionInContext0(isolate.getReference(), global.getReference(),
                        name, script, arguments, extensions);
            }
        });
    }

    private native static Object eval0(long isolateRef, long globalRef, String name, String script) throws ScriptException;
    static Object eval(V8Object global, String name, String script) throws ScriptException {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Callable<Object>)() -> {
            synchronized(isolate) {
                if (V8.DEBUG) {
                    debugPrintf("Evaluating %s in global 0x%x", name, global.getReference());
                }
                return eval0(isolate.getReference(), global.getReference(), name, script);
            }
        });
    }

    static Object eval(V8Object global, String name, String script, ScriptContext sc) throws ScriptException {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Callable<Object>)() -> {
            synchronized(isolate) {
                if (V8.DEBUG) {
                    debugPrintf("Evaluating %s in global 0x%x", name, global.getReference());
                }
                ScriptContext oldCtx = isolate.getScriptContext();
                isolate.setScriptContext(sc);
                try {
                    return eval0(isolate.getReference(), global.getReference(), name, script);
                } finally {
                    isolate.setScriptContext(oldCtx);
                }
            }
        });
    }

    private native static Object eval1(long isolateRef, long objectRef, long unboundScriptRef) throws ScriptException;
    static Object eval(V8Object global, V8UnboundScript script, ScriptContext sc) throws ScriptException {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Callable<Object>)() -> {
            synchronized(isolate) {
                if (V8.DEBUG) {
                    debugPrintf("Evaluating compiled script 0x%x in global 0x%x", script.getReference(), global.getReference());
                }
                ScriptContext oldCtx = isolate.getScriptContext();
                isolate.setScriptContext(sc);
                try {
                    return eval1(isolate.getReference(), global.getReference(), script.getReference());
                } finally {
                    isolate.setScriptContext(oldCtx);
                }
            }
        });
    }

    // V8Object helpers
    private native static String toString0(long isolateRef, long ref);
    static String toString(V8Object obj) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<String>)() -> {
            synchronized(isolate) {
                return toString0(isolate.getReference(), obj.getReference());
            }
        });
    }

    private native static boolean strictEquals0(long isolateRef, long ref1, long ref2);
    static boolean strictEquals(V8Object obj1, V8Object obj2) {
        V8Isolate isolate1 = obj1.getIsolate(), isolate2 = obj2.getIsolate();
        if (! isolate1.equals(isolate2)) {
            return false;
        }
        return runInIsolate(isolate1, (Supplier<Boolean>)() -> {
            synchronized(isolate1) {
                return strictEquals0(isolate1.getReference(), obj1.getReference(), obj2.getReference());
            }
        });
    }

    static boolean strictEquals(V8Symbol sym1, V8Symbol sym2) {
        V8Isolate isolate1 = sym1.getIsolate(), isolate2 = sym2.getIsolate();
        if (! isolate1.equals(isolate2)) {
            return false;
        }
        return runInIsolate(isolate1, (Supplier<Boolean>)() -> {
            synchronized(isolate1) {
                return strictEquals0(isolate1.getReference(), sym1.getReference(), sym2.getReference());
            }
        });
    }

    private native static Object invoke0(long isolateRef, long objectRef, Object thiz, Object[] args);
    static Object invoke(V8Object object, Object thiz, Object[] args) throws ScriptException {
        final V8Isolate isolate = object.getIsolate();
        return runInIsolate(isolate, (Callable<Object>)() -> {
            synchronized(isolate) {
                return invoke0(isolate.getReference(), object.getReference(), thiz, args);
            }
        });
    }

    static Object invokeMethod(V8Object thiz, String name, Object[] args) throws NoSuchMethodException, ScriptException {
        Object value = thiz.get(name);
        if (value instanceof V8Function) {
            return invoke((V8Function)value, thiz, args);
        } else if (value instanceof V8Object) {
            V8Object func = (V8Object)value;
            if (func.isCallable()) {
                return invoke(func, thiz, args);
            } // else fallthru
        }

        throw new NoSuchMethodException(name);
    }

    static Object invoke(V8Object object, Object thiz, Object[] args, ScriptContext sc) throws ScriptException {
        final V8Isolate isolate = object.getIsolate();
        return runInIsolate(isolate, (Callable<Object>)() -> {
            synchronized(isolate) {
                ScriptContext oldCtx = isolate.getScriptContext();
                isolate.setScriptContext(sc);
                try {
                    return invoke0(isolate.getReference(), object.getReference(), thiz, args);
                } finally {
                    isolate.setScriptContext(oldCtx);
                }
            }
        });
    }

    static Object invokeMethod(V8Object thiz, String name, Object[] args, ScriptContext sc)
            throws NoSuchMethodException, ScriptException {
        Object value = thiz.get(name);
        if (value instanceof V8Function) {
            return invoke((V8Function)value, thiz, args, sc);
        } else if (value instanceof V8Object) {
            V8Object func = (V8Object)value;
            if (func.isCallable()) {
                return invoke(func, thiz, args, sc);
            } // else fallthru
        }

        throw new NoSuchMethodException(name);
    }

    private native static String getFunctionName0(long isolateRef, long functionRef);
    static String getFunctionName(V8Function function) {
        final V8Isolate isolate = function.getIsolate();
        return runInIsolate(isolate, (Supplier<String>)()-> {
            return getFunctionName0(isolate.getReference(), function.getReference());
        });
    }

    private native static Object newObject0(long isolateRef, long objectRef, Object[] args);
    static Object newObject(V8Object object, Object[] args) throws ScriptException {
        final V8Isolate isolate = object.getIsolate();
        return runInIsolate(isolate, (Callable<Object>)() -> {
            synchronized(isolate) {
                return newObject0(isolate.getReference(), object.getReference(), args);
            }
        });
    }

    private native static String getConstructorName0(long isolateRef, long objectRef);
    static String getConstructorName(V8Object obj) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<String>)() -> {
            synchronized(isolate) {
                return getConstructorName0(isolate.getReference(), obj.getReference());
            }
        });
    }

    private native static boolean isCallable0(long isolateRef, long objectRef);
    static boolean isCallable(V8Object obj) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return isCallable0(isolate.getReference(), obj.getReference());
            }
        });
    }

    private native static Object get0(long isolateRef, long objectRef, String name);
    static Object get(V8Object obj, String name) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return get0(isolate.getReference(), obj.getReference(), name);
            }
        });
    }

    private native static Object get1(long isolateRef, long objectRef, int index);
    static Object get(V8Object obj, int index) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return get1(isolate.getReference(), obj.getReference(), index);
            }
        });
    }

    private native static Object get2(long isolateRef, long objectRef, Object key);
    static Object get(V8Object obj, Object key) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return get2(isolate.getReference(), obj.getReference(), key);
            }
        });
    }

    private native static Object put0(long isolateRef, long objectRef, String name, Object value);
    static Object put(V8Object obj, String name, Object value) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return put0(isolate.getReference(), obj.getReference(), name, value);
            }
        });
    }

    private native static Object put1(long isolateRef, long objectRef, int index, Object value);
    static Object put(V8Object obj, int index, Object value) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return put1(isolate.getReference(), obj.getReference(), index, value);
            }
        });
    }

    private native static Object put2(long isolateRef, long objectRef, Object key, Object value);
    static Object put(V8Object obj, Object key, Object value) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return put2(isolate.getReference(), obj.getReference(), key, value);
            }
        });
    }

    private native static boolean set0(long isolateRef, long objectRef, String name, Object value);
    static boolean set(V8Object obj, String name, Object value) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return set0(isolate.getReference(), obj.getReference(), name, value);
            }
        });
    }

    private native static boolean set1(long isolateRef, long objectRef, int index, Object value);
    static boolean set(V8Object obj, int index, Object value) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return set1(isolate.getReference(), obj.getReference(), index, value);
            }
        });
    }

    private native static boolean set2(long isolateRef, long objectRef, Object key, Object value);
    static boolean set(V8Object obj, Object key, Object value) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return set2(isolate.getReference(), obj.getReference(), key, value);
            }
        });
    }

    private native static boolean contains0(long isolateRef, long objectRef, String name);
    static boolean contains(V8Object obj, String name) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return contains0(isolate.getReference(), obj.getReference(), name);
            }
        });
    }

    private native static boolean contains1(long isolateRef, long objectRef, int index);
    static boolean contains(V8Object obj, int index) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return contains1(isolate.getReference(), obj.getReference(), index);
            }
        });
    }

    private native static boolean contains2(long isolateRef, long objectRef, Object key);
    static boolean contains(V8Object obj, Object key) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return contains2(isolate.getReference(), obj.getReference(), key);
            }
        });
    }

    private native static Object remove0(long isolateRef, long objectRef, String name);
    static Object remove(V8Object obj, String name) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return remove0(isolate.getReference(), obj.getReference(), name);
            }
        });
    }

    private native static Object remove1(long isolateRef, long objectRef, int index);
    static Object remove(V8Object obj, int index) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return remove1(isolate.getReference(), obj.getReference(), index);
            }
        });
    }

    private native static Object remove2(long isolateRef, long objectRef, Object key);
    static Object remove(V8Object obj, Object key) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return remove2(isolate.getReference(), obj.getReference(), key);
            }
        });
    }

    private native static boolean delete0(long isolateRef, long objectRef, String name);
    static boolean delete(V8Object obj, String name) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return delete0(isolate.getReference(), obj.getReference(), name);
            }
        });
    }

    private native static boolean delete1(long isolateRef, long objectRef, int index);
    static boolean delete(V8Object obj, int index) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return delete1(isolate.getReference(), obj.getReference(), index);
            }
        });
    }

    private native static boolean delete2(long isolateRef, long objectRef, Object key);
    static boolean delete(V8Object obj, Object key) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return delete2(isolate.getReference(), obj.getReference(), key);
            }
        });
    }

    private native static int length0(long isolateRef, long objectRef);
    static int length(V8Object array) {
        final V8Isolate isolate = array.getIsolate();
        return runInIsolate(isolate, (Supplier<Integer>)() -> {
            synchronized(isolate) {
                return length0(isolate.getReference(), array.getReference());
            }
        });
    }

    // These values should match the corresponding v8::PropertyAttribute enum values
    private static final int PROPERTY_ATTRIBUTE_NONE        = 0;
    private static final int PROPERTY_ATTRIBUTE_READ_ONLY   = 1 << 0;
    private static final int PROPERTY_ATTRIBUTE_DONT_ENUM   = 1 << 1;
    private static final int PROPERTY_ATTRIBUTE_DONT_DELETE = 1 << 2;

    private static EnumSet<PropertyAttribute> toPropertyAttributes(int flags) {
        if (flags == 0) {
            return EnumSet.of(PropertyAttribute.None);
        }

        List<PropertyAttribute> attrs = new ArrayList<>();
        if ((flags & PROPERTY_ATTRIBUTE_READ_ONLY) != 0) {
            attrs.add(PropertyAttribute.ReadOnly);
        }

        if ((flags & PROPERTY_ATTRIBUTE_DONT_ENUM) != 0) {
            attrs.add(PropertyAttribute.DontEnum);
        }

        if ((flags & PROPERTY_ATTRIBUTE_DONT_DELETE) != 0) {
            attrs.add(PropertyAttribute.DontDelete);
        }

        return EnumSet.copyOf(attrs);
    }

    private static int fromPropertyAttributes(EnumSet<PropertyAttribute> attrs) {
        int flags = 0;
        if (attrs == null) {
            return flags;
        }
        for (PropertyAttribute attr : attrs) {
            switch (attr) {
                case ReadOnly:
                    flags |= PROPERTY_ATTRIBUTE_READ_ONLY;
                    break;
                case DontEnum:
                    flags |= PROPERTY_ATTRIBUTE_DONT_ENUM;
                    break;
                case DontDelete:
                    flags |= PROPERTY_ATTRIBUTE_DONT_DELETE;
                    break;
            }
        }
        return flags;
    }

    private native static int propertyAttributes0(long isolateRef, long objectRef, String name);
    static EnumSet<PropertyAttribute> propertyAttributes(V8Object obj, String name) {
        final V8Isolate isolate = obj.getIsolate();
        int props = runInIsolate(isolate, (Supplier<Integer>)() -> {
            synchronized(isolate) {
                return propertyAttributes0(isolate.getReference(), obj.getReference(), name);
            }
        });
        return toPropertyAttributes(props);
    }

    private native static int propertyAttributes1(long isolateRef, long objectRef, int index);
    static EnumSet<PropertyAttribute> propertyAttributes(V8Object obj, int index) {
        final V8Isolate isolate = obj.getIsolate();
        int props = runInIsolate(isolate, (Supplier<Integer>)() -> {
            synchronized(isolate) {
                return propertyAttributes1(isolate.getReference(), obj.getReference(), index);
            }
        });
        return toPropertyAttributes(props);
    }

    private native static int propertyAttributes2(long isolateRef, long objectRef, Object key);
    static EnumSet<PropertyAttribute> propertyAttributes(V8Object obj, Object key) {
        final V8Isolate isolate = obj.getIsolate();
        int props = runInIsolate(isolate, (Supplier<Integer>)() -> {
            synchronized(isolate) {
                return propertyAttributes2(isolate.getReference(), obj.getReference(), key);
            }
        });
        return toPropertyAttributes(props);
    }

    // called from native
    static int propertyAttributeFlags(JSObject jsObj, String name) {
        // if it is a V8Object don't translate to EnumSet and back. Short-circuit that mapping!
        if (jsObj instanceof V8Object) {
            final V8Object obj = (V8Object)jsObj;
            final V8Isolate isolate = obj.getIsolate();
            return runInIsolate(isolate, (Supplier<Integer>)() -> {
                synchronized(isolate) {
                    return propertyAttributes0(isolate.getReference(), obj.getReference(), name);
                }
            });
        } else {
            // go via JSObject API route
            return fromPropertyAttributes(jsObj.getMemberAttributes(name));
        }
    }

    // called from native
    static int propertyAttributeFlags(JSObject jsObj, JSSymbol name) {
        // if it is a V8Object don't translate to EnumSet and back. Short-circuit that mapping!
        if (jsObj instanceof V8Object) {
            final V8Object obj = (V8Object)jsObj;
            final V8Isolate isolate = obj.getIsolate();
            return runInIsolate(isolate, (Supplier<Integer>)() -> {
                synchronized(isolate) {
                    return propertyAttributes2(isolate.getReference(), obj.getReference(), name);
                }
            });
        } else {
            // go via JSObject API route
            return fromPropertyAttributes(jsObj.getMemberAttributes(name));
        }
    }

    // called from native
    static int propertyAttributeFlags(JSObject jsObj, int index) {
        // if it is a V8Object don't translate to EnumSet and back. Short-circuit that mapping!
        if (jsObj instanceof V8Object) {
            final V8Object obj = (V8Object)jsObj;
            final V8Isolate isolate = obj.getIsolate();
            return runInIsolate(isolate, (Supplier<Integer>)() -> {
                synchronized(isolate) {
                    return propertyAttributes1(isolate.getReference(), obj.getReference(), index);
                }
            });
        } else {
            // go via JSObject API route
            return fromPropertyAttributes(jsObj.getSlotAttributes(index));
        }
    }

    private native static boolean defineOwnProperty0(long isolateRef, long objectRef, String name, Object value, int flags);
    static boolean defineOwnProperty(V8Object obj, String name, Object value, EnumSet<PropertyAttribute> attrs) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return defineOwnProperty0(isolate.getReference(), obj.getReference(), name, value,
                    fromPropertyAttributes(attrs));
            }
        });
    }

    private native static boolean defineOwnProperty1(long isolateRef, long objectRef, long symbolRef, Object value, int flags);
    static boolean defineOwnProperty(V8Object obj, V8Symbol name, Object value, EnumSet<PropertyAttribute> attrs) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return defineOwnProperty1(isolate.getReference(), obj.getReference(), name.getReference(),
                    value, fromPropertyAttributes(attrs));
            }
        });
    }

    private native static boolean setAccessorProperty0(long isolateRef, long objectRef, String name,
            JSFunction getter, JSFunction setter, int flags);
    static boolean setAccessorProperty(V8Object obj, String name, JSFunction getter,
            JSFunction setter, EnumSet<PropertyAttribute> attrs) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return setAccessorProperty0(isolate.getReference(), obj.getReference(), name,
                    getter, setter, fromPropertyAttributes(attrs));
            }
        });
    }

    private native static boolean setAccessorProperty1(long isolateRef, long objectRef, long symbolRef,
            JSFunction getter, JSFunction setter, int flags);
    static boolean setAccessorProperty(V8Object obj, V8Symbol name, JSFunction getter,
            JSFunction setter, EnumSet<PropertyAttribute> attrs) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return setAccessorProperty1(isolate.getReference(), obj.getReference(), name.getReference(),
                    getter, setter, fromPropertyAttributes(attrs));
            }
        });
    }

    // called from native
    static Object jsObjectGetMember(JSObject jsObj, String name) {
        Object value = jsObj.getMember(name);

        // special case for "toString". If it is not a function type value,
        // return a function that would call toString on the JSObject.
        if (name.equals("toString") && !(value instanceof JSFunction)) {
            return new JSFunction() {
                @Override
                public Object call(Object thiz, Object... args) {
                    return jsObj.toString();
                }

                @Override
                public String toString() {
                    return "function toString() { [native code] }";
                }
            };
        }

        return value;
    }

    private native static Object[] keys0(long isolateRef, long objectRef);
    static Object[] keys(V8Object obj) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<Object[]>)() -> {
            synchronized(isolate) {
                Object[] keys = keys0(isolate.getReference(), obj.getReference());
                return keys != null? keys : new Object[0];
            }
        });
    }

    private native static String[] namedKeys0(long isolateRef, long objectRef);
    static String[] namedKeys(V8Object obj) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<String[]>)() -> {
            synchronized(isolate) {
                String[] names = namedKeys0(isolate.getReference(), obj.getReference());
                return names != null? names : new String[0];
            }
        });
    }

    private native static int[] indexedKeys0(long isolateRef, long objectRef);
    static int[] indexedKeys(V8Object obj) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<int[]>)() -> {
            synchronized(isolate) {
                int[] res = indexedKeys0(isolate.getReference(), obj.getReference());
                return res != null? res : new int[0];
            }
        });
    }

    private native static JSSymbol[] symbolKeys0(long isolateRef, long objectRef);
    static JSSymbol[] symbolKeys(V8Object obj) {
        final V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<JSSymbol[]>)() -> {
            synchronized(isolate) {
                JSSymbol[] names = symbolKeys0(isolate.getReference(), obj.getReference());
                return names != null? names : new JSSymbol[0];
            }
        });
    }

    // JSFactory support
    private native static V8Object newObject1(long isolateRef, long globalRef);
    static V8Object newObject(V8Object global) {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Object>)() -> {
            synchronized(isolate) {
                return newObject1(isolate.getReference(), global.getReference());
            }
        });
    }

    private native static V8Array newArray0(long isolateRef, long globalRef, int length);
    static V8Array newArray(V8Object global, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("negative size");
        }
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Array>)() -> {
            synchronized(isolate) {
                return newArray0(isolate.getReference(), global.getReference(), length);
            }
        });
    }

    private native static V8Object newArrayBuffer0(long isolateRef, long globalRef, int length);
    static V8Object newArrayBuffer(V8Object global, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("negative size");
        }
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Object>)() -> {
            synchronized(isolate) {
                return newArrayBuffer0(isolate.getReference(), global.getReference(), length);
            }
        });
    }

    private static native V8Object newArrayBuffer1(long isolateRef, long globalRef, ByteBuffer buf);
    // Create a new ArrayBuffer object whose memory is backed by the nio (direct) ByteBuffer.
    static V8Object newArrayBuffer(V8Object global, ByteBuffer buf) {
        Objects.requireNonNull(buf);
        if (!buf.isDirect()) {
            throw new IllegalArgumentException("direct nio Buffer expected");
        }
        if (buf.order() != ByteOrder.nativeOrder()) {
            throw new IllegalArgumentException("nio Buffer should use platform native order");
        }

        V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Object>)() -> {
            synchronized(isolate) {
                return newArrayBuffer1(isolate.getReference(), global.getReference(), buf);
            }
        });
    }

    private static native ByteBuffer newByteBuffer0(long isolateRef, long arrayBufRef);
    // Create a new nio direct ByteBuffer whose memory is backed by the given ArrayBuffer
    // called from native
    static ByteBuffer newByteBuffer(V8Object arrayBuf) {
        final V8Isolate isolate = arrayBuf.getIsolate();
        return runInIsolate(isolate, (Supplier<ByteBuffer>)() -> {
            synchronized(isolate) {
                ByteBuffer byteBuf = newByteBuffer0(isolate.getReference(), arrayBuf.getReference());
                // V8 ArrayBuffer uses platform native order. Make sure nio Buffer follows the same order.
                byteBuf.order(ByteOrder.nativeOrder());
                // make sure V8 Arraybuffer lives till nio ByteBuffer lives by weak caching
                isolate.cacheArrayBuffer(byteBuf, arrayBuf);
                return byteBuf;
            }
        });
    }

    private native static V8Object newDate0(long isolateRef, long globalRef, double time);
    static V8Object newDate(V8Object global, double time) {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Object>)() -> {
            synchronized(isolate) {
                return newDate0(isolate.getReference(), global.getReference(), time);
            }
        });
    }

    private native static V8Proxy newProxy0(long isolateRef, long globalRef, JSObject target, JSObject handler);
    static V8Proxy newProxy(V8Object global, JSObject target, JSObject handler) {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Proxy>)() -> {
            synchronized(isolate) {
                return newProxy0(isolate.getReference(), global.getReference(), target, handler);
            }
        });
    }

    private native static V8Resolver newResolver0(long isolateRef, long globalRef);
    static V8Resolver newResolver(V8Object global) {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Resolver>)() -> {
            synchronized(isolate) {
                return newResolver0(isolate.getReference(), global.getReference());
            }
        });
    }

    private native static V8Object newRegExp0(long isolateRef, long globalRef, String pattern, int flags);
    static V8Object newRegExp(V8Object global, String pattern, int flags) {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Object>)() -> {
            synchronized(isolate) {
                return newRegExp0(isolate.getReference(), global.getReference(), pattern, flags);
            }
        });
    }

    private native static Object parseJSON0(long isolateRef, long globalRef, String jsonString);
    static Object parseJSON(V8Object global, String jsonString) {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<Object>)() -> {
            synchronized(isolate) {
                return parseJSON0(isolate.getReference(), global.getReference(), jsonString);
            }
        });
    }

    private native static String toJSON0(long isolateRef, long objectRef, String gap);
    static String toJSON(V8Object obj, String gap) {
        V8Isolate isolate = obj.getIsolate();
        return runInIsolate(isolate, (Supplier<String>)() -> {
            synchronized(isolate) {
                return toJSON0(isolate.getReference(), obj.getReference(), gap);
            }
        });
    }

    private native static String toJSON1(long isolateRef, long globalRef, JSObject jsObj, String gap);
    static String toJSON(V8Object global, JSObject jsObj, String gap) {
        if (jsObj instanceof V8Object) {
            return toJSON((V8Object)jsObj, gap);
        } else {
            V8Isolate isolate = global.getIsolate();
            return runInIsolate(isolate, (Supplier<String>)() -> {
                synchronized(isolate) {
                    return toJSON1(isolate.getReference(), global.getReference(), jsObj, gap);
                }
            });
        }
    }

    private native static V8Symbol newSymbol0(long isolateRef, String name);
    static V8Symbol newSymbol(V8Isolate isolate, String name) {
        return runInIsolate(isolate, (Supplier<V8Symbol>)() -> {
            synchronized(isolate) {
                return newSymbol0(isolate.getReference(), name);
            }
        });
    }

    private native static V8Symbol symbolFor0(long isolateRef, String name);
    static V8Symbol symbolFor(V8Isolate isolate, String name) {
        return runInIsolate(isolate, (Supplier<V8Symbol>)() -> {
            synchronized(isolate) {
                return symbolFor0(isolate.getReference(), name);
            }
        });
    }

    private native static V8Symbol getIteratorSymbol0(long isolateRef);
    static V8Symbol getIteratorSymbol(V8Isolate isolate) {
        return runInIsolate(isolate, (Supplier<V8Symbol>)() -> {
            synchronized(isolate) {
                return getIteratorSymbol0(isolate.getReference());
            }
        });
    }

    private native static V8Symbol getUnscopablesSymbol0(long isolateRef);
    static V8Symbol getUnscopablesSymbol(V8Isolate isolate) {
        return runInIsolate(isolate, (Supplier<V8Symbol>)() -> {
            synchronized(isolate) {
                return getUnscopablesSymbol0(isolate.getReference());
            }
        });
    }

    private native static V8Symbol getToStringTagSymbol0(long isolateRef);
    static V8Symbol getToStringTagSymbol(V8Isolate isolate) {
        return runInIsolate(isolate, (Supplier<V8Symbol>)() -> {
            synchronized(isolate) {
                return getToStringTagSymbol0(isolate.getReference());
            }
        });
    }

    private native static V8Symbol getIsConcatSpreadableSymbol0(long isolateRef);
    static V8Symbol getIsConcatSpreadableSymbol(V8Isolate isolate) {
        return runInIsolate(isolate, (Supplier<V8Symbol>)() -> {
            synchronized(isolate) {
                return getIsConcatSpreadableSymbol0(isolate.getReference());
            }
        });
    }

    // V8 Error factory support
    private static final int ERROR           = 0;
    private static final int RANGE_ERROR     = 1;
    private static final int REFERENCE_ERROR = 2;
    private static final int SYNTAX_ERROR    = 3;
    private static final int TYPE_ERROR      = 4;

    private native static V8Object newError0(long isolateRef, long globalRef, String message, int type);
    private static V8Object newError(V8Object global, String message, int type) {
        Objects.requireNonNull(message);
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Object>)() -> {
            synchronized(isolate) {
                return newError0(isolate.getReference(), global.getReference(), message, type);
            }
        });
    }

    static V8Object newError(V8Object global, String message) {
        return newError(global, message, ERROR);
    }

    static V8Object newRangeError(V8Object global, String message) {
        return newError(global, message, RANGE_ERROR);
    }

    static V8Object newReferenceError(V8Object global, String message) {
        return newError(global, message, REFERENCE_ERROR);
    }

    static V8Object newSyntaxError(V8Object global, String message) {
        return newError(global, message, SYNTAX_ERROR);
    }

    static V8Object newTypeError(V8Object global, String message) {
        return newError(global, message, TYPE_ERROR);
    }

    // V8Resolver support
    private native static boolean resolverResolve0(long isolateRef, long resolverRef, Object result);
    static boolean resolverResolve(V8Resolver resolver, Object result) {
        final V8Isolate isolate = resolver.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return resolverResolve0(isolate.getReference(), resolver.getReference(), result);
            }
        });
    }

    private native static boolean resolverReject0(long isolateRef, long resolverRef, Object result);
    static boolean resolverReject(V8Resolver resolver, Object result) {
        final V8Isolate isolate = resolver.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return resolverReject0(isolate.getReference(), resolver.getReference(), result);
            }
        });
    }

    private native static V8Promise resolverGetPromise0(long isolateRef, long resolverRef);
    static V8Promise resolverGetPromise(V8Resolver resolver) {
        final V8Isolate isolate = resolver.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Promise>)() -> {
            synchronized(isolate) {
                return resolverGetPromise0(isolate.getReference(), resolver.getReference());
            }
        });
    }

    // V8Promise support
    private native static V8Promise promiseCatch0(long isolateRef, long promiseRef, JSFunction handler);
    static V8Promise promiseCatch(V8Promise promise, JSFunction handler) {
        Objects.requireNonNull(handler);
        final V8Isolate isolate = promise.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Promise>)() -> {
            synchronized(isolate) {
                return promiseCatch0(isolate.getReference(), promise.getReference(), handler);
            }
        });
    }

    private native static V8Promise promiseThen0(long isolateRef, long promiseRef, JSFunction handler);
    static V8Promise promiseThen(V8Promise promise, JSFunction handler) {
        Objects.requireNonNull(handler);
        final V8Isolate isolate = promise.getIsolate();
        return runInIsolate(isolate, (Supplier<V8Promise>)() -> {
            synchronized(isolate) {
                return promiseThen0(isolate.getReference(), promise.getReference(), handler);
            }
        });
    }

    private native static boolean promiseHasHandler0(long isolateRef, long promiseRef);
    static boolean promiseHasHandler(V8Promise promise) {
        final V8Isolate isolate = promise.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return promiseHasHandler0(isolate.getReference(), promise.getReference());
            }
        });
    }

    // V8Promise support
    private native static JSObject proxyGetTarget0(long isolateRef, long proxyRef);
    static JSObject proxyGetTarget(V8Proxy proxy) {
        final V8Isolate isolate = proxy.getIsolate();
        return runInIsolate(isolate, (Supplier<JSObject>)() -> {
            synchronized(isolate) {
                return proxyGetTarget0(isolate.getReference(), proxy.getReference());
            }
        });
    }

    private native static JSObject proxyGetHandler0(long isolateRef, long proxyRef);
    static JSObject proxyGetHandler(V8Proxy proxy) {
        final V8Isolate isolate = proxy.getIsolate();
        return runInIsolate(isolate, (Supplier<JSObject>)() -> {
            synchronized(isolate) {
                return proxyGetHandler0(isolate.getReference(), proxy.getReference());
            }
        });
    }

    private native static boolean proxyIsRevoked0(long isolateRef, long proxyRef);
    static boolean proxyIsRevoked(V8Proxy proxy) {
        final V8Isolate isolate = proxy.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return proxyIsRevoked0(isolate.getReference(), proxy.getReference());
            }
        });
    }

    // V8Context support
    private static native void allowCodeGenerationFromStrings0(long isolateRef, long globalRef, boolean allow);
    static void allowCodeGenerationFromStrings(V8Object global, boolean allow) {
        final V8Isolate isolate = global.getIsolate();
        runInIsolate(isolate, (Supplier<Void>)() -> {
            synchronized(isolate) {
                allowCodeGenerationFromStrings0(isolate.getReference(), global.getReference(), allow);
                return null;
            }
        });
    }

    private static native boolean isCodeGenerationFromStringsAllowed0(long isolateRef, long globalRef);
    static boolean isCodeGenerationFromStringsAllowed(V8Object global) {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Supplier<Boolean>)() -> {
            synchronized(isolate) {
                return isCodeGenerationFromStringsAllowed0(isolate.getReference(), global.getReference());
            }
        });
    }

    // V8ExecutionControl support
    private static native void terminateExecution0(long isolateRef);
    public static void terminateExecution(V8Isolate isolate) {
        terminateExecution0(isolate.getReference());
    }

    private static native void requestInterrupt0(long isolateRef, Runnable callback);
    public static void requestInterrupt(V8Isolate isolate, Runnable callback) {
        assert callback != null;
        requestInterrupt0(isolate.getReference(), callback);
    }

    private static native void runMicrotasks0(long isolateRef);
    public static void runMicrotasks(V8Isolate isolate) {
        runInIsolate(isolate, (Supplier<Void>)() -> {
            synchronized(isolate) {
                runMicrotasks0(isolate.getReference());
            }
            return null;
        });
    }

    private static native void enqueueMicrotask0(long isolateRef, long globalRef, long functionRef);
    public static void enqueueMicrotask(V8Isolate isolate, V8Object global, JSFunction microtask) {
        assert microtask != null;

        // Only V8Function objects are supported
        if (!(microtask instanceof V8Function)) {
            throw new IllegalArgumentException("Expected V8 script function!");
        }

        // Let the function better be defined by the current isolate!
        V8Function v8Func = (V8Function)microtask;
        if (! v8Func.isFromIsolate(isolate)) {
            throw new IllegalArgumentException("Expected a V8 script function from the current engine");
        }

        synchronized(isolate) {
            enqueueMicrotask0(isolate.getReference(), global.getReference(), v8Func.getReference());
        }
    }

    private static native void throwException0(long isolateRef, Object exception);
    public static Object throwException(V8Isolate isolate, Object exception) {
        Objects.requireNonNull(exception);
        // when throwing exception, we need to be already running JavaScript code
        // in the supplied V8Isolate! If we try to throw exception and then enter V8
        // for JS evaluation V8 crashes! We just make sure we are in right state by
        // making sure that the given V8Isolate is already entered in TLS.
        V8Isolate curIsolate = getCurrentIsolate();
        if (!isolate.equals(curIsolate)) {
            throw new IllegalStateException("Throwing exception to another V8Isolate!");
        }

        assert Thread.holdsLock(isolate);
        throwException0(isolate.getReference(), exception);
        return null;
    }

    // DEBUG mode propagation to native code
    private static native void setEventLogger0(long isolateRef);

    // Script generator helpers
    native static long getMethodID0(Class<?> cls, String name, String signature);
    native static long getStaticMethodID0(Class<?> cls, String name, String signature);
    native static long getFieldID0(Class<?> cls, String name, String signature);
    native static long getStaticFieldID0(Class<?> cls, String name, String signature);

    private static void typeSignature(StringBuilder sb, Class<?> type) {
        if (type == void.class) {
            sb.append('V');
        } else if (type == boolean.class) {
            sb.append('Z');
        } else if (type == byte.class) {
            sb.append('B');
        } else if (type == char.class) {
            sb.append('C');
        } else if (type == short.class) {
            sb.append('S');
        } else if (type == int.class) {
            sb.append('I');
        } else if (type == long.class) {
            sb.append('J');
        } else if (type == float.class) {
            sb.append('F');
        } else if (type == double.class) {
            sb.append('D');
        } else if (type.isArray()) {
            sb.append('[');
            typeSignature(sb, type.getComponentType());
        } else {
            sb.append('L');
            sb.append(type.getName().replace('.', '/'));
            sb.append(';');
        }
    }

    // called from native
    static String fieldSignature(final Field field) {
        final StringBuilder sb = new StringBuilder();
        typeSignature(sb, field.getType());

        return sb.toString();
    }

    // called from native
    static String executableSignature(final Executable executable) {
        final StringBuilder sb = new StringBuilder();
        sb.append('(');

        for (Class<?> type : executable.getParameterTypes()) {
            typeSignature(sb, type);
        }

        sb.append(')');

        if (executable instanceof Method) {
            Method method = (Method)executable;
            typeSignature(sb, method.getReturnType());
        } else {
            sb.append('V');
        }

        return sb.toString();
    }

    private static String generateClassImplCommon(Class<?> cls) {
        V8ClassGenerator generator = new V8ClassGenerator(cls, true);
        if (DEBUG) {
            debugPrintf("Generating script for Java class: %s", cls.getName());
        }
        String script = generator.generateConstructor();
        if (DUMP_GENERATED_SCRIPT) {
            System.err.println(script);
        }
        return script;
    }

    private static String generateClassImpl(Class<?> cls) {
        return generateClassImplCommon(cls);
    }

    // cache for generated scripts for bootstrap java classes
    private static final ClassValue<String> CLASS_TO_SCRIPT = new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> cls) {
            return generateClassImpl(cls);
        }
    };

    private static boolean isBootstrapClass(Class<?> cls) {
        try {
            return cls.getClassLoader() == null;
        } catch (SecurityException ignored) {
            return false;
        }
    }

    // called from native
    static String generateClass(final Class<?> cls) {
        if (isBootstrapClass(cls)) {
            return CLASS_TO_SCRIPT.get(cls);
        } else {
            // don't cache for other cases!
            // FIXME: per V8Isolate cache?
            return generateClassImpl(cls);
        }
    }

    private static String lookupPropertyImpl(Class<?> cls, String prop, boolean isStatic) {
        V8ClassGenerator generator = new V8ClassGenerator(cls, true);
        if (DEBUG) {
            debugPrintf("Generating script for property %s of class: %s", prop, cls.getName());
        }
        String script = generator.generateProperty(prop, isStatic);
        if (DUMP_GENERATED_SCRIPT) {
            System.err.println(script);
        }
        return script;
    }

    // called from native
    static String lookupProperty(final Class<?> cls, final String prop, final boolean isStatic) {
        return lookupPropertyImpl(cls, prop, isStatic);
    }

    // called from native
    static Object getInterface(final Class<?> cls, V8Object obj) {
        Object res = obj.getInterface(cls);
        if (res == null) {
            throw new IllegalArgumentException(cls + " cannot be implemented");
        }
        return res;
    }

    // called from native
    static String getBootstrap() {
        return V8BootstrapLoader.script;
    }

    /**
     * Checks that the given package name can be accessed from script or not.
     *
     * @param pkgName package name
     * @throws RuntimeException if not accessible
     */
    static void checkPackageAccess(String pkgName) {
        // make sure to convert possible internal package name.
        pkgName = pkgName.replace('/', '.');
        // package name based access checks.
        // FIXME: filter more sensitive packages here!
        if (pkgName.startsWith("sun.") || pkgName.startsWith("jdk.internal.")) {
            throw new RuntimeException("cannot access package: " + pkgName);
        }
    }

    /**
     * Checks that the given Class can be accessed from no permissions context.
     *
     * @param clazz Class object
     * @throws SecurityException if not accessible
     */
    static void checkPackageAccess(final Class<?> clazz) {
        Class<?> bottomClazz = clazz;
        while (bottomClazz.isArray()) {
            bottomClazz = bottomClazz.getComponentType();
        }

        // perform security access check as early as possible
        if (!Modifier.isPublic(bottomClazz.getModifiers()) ||
            !bottomClazz.getModule().isExported(bottomClazz.getPackageName())) {
            throw new RuntimeException(bottomClazz.getName() + " is not accessible");
        }
        checkPackageAccess(bottomClazz.getName());
    }

    /**
     * Checks that the given Class can be accessed from no permissions context.
     *
     * @param clazz Class object
     * @return true if package is accessible, false otherwise
     */
    static boolean isAccessiblePackage(final Class<?> clazz) {
        try {
            checkPackageAccess(clazz);
            return true;
        } catch (final RuntimeException re) {
            return false;
        }
    }

    /**
     * Returns true if the class is either not public, or it resides in a package with restricted access.
     * @param clazz the class to test
     * @return true if the class is either not public, or it resides in a package with restricted access.
     */
    static boolean isRestrictedClass(final Class<?> clazz) {
        return !isAccessibleClass(clazz);
    }

    /**
     * Checks that the given Class is public and it can be accessed from no permissions context.
     *
     * @param clazz Class object to check
     * @return true if Class is accessible, false otherwise
     */
    static boolean isAccessibleClass(final Class<?> clazz) {
        return Modifier.isPublic(clazz.getModifiers()) && V8.isAccessiblePackage(clazz);
    }

    private static Class findClassChecked(String className) throws ClassNotFoundException {
        // no internal name or array name here!
        assert className.indexOf('[') == -1 && className.indexOf('/') == -1;
        checkPackageAccess(className);
        // no reflection!
        if (className.startsWith("java.lang.reflect.") ||
            className.startsWith("java.lang.invoke.")) {
            throw new RuntimeException("no reflection from scripts");
        }

        V8Isolate isolate = currentIsolate.get();
        ClassLoader loader;
        if (isolate != null) {
            loader = isolate.getClassLoader();
        } else {
            // node case
            loader = ClassLoader.getSystemClassLoader();
        }

        return Class.forName(className, true, loader);
    }

    /**
     * Get a formatted, localized message with the given arguments.
     *
     * @param msgId the message id
     * @param args the arguments
     * @return the formatted message
     */
    public static String getMessage(final String msgId, final Object... args) {
        try {
            return MessageFormat.format(MESSAGES_BUNDLE.getString(msgId), args);
        } catch (final java.util.MissingResourceException e) {
            throw new RuntimeException("no message resource found for message id: "+ msgId);
        }
    }

    private static final Map<Character, Class> primitiveClasses = new HashMap<>();
    static {
        primitiveClasses.put('V', void.class);
        primitiveClasses.put('C', char.class);
        primitiveClasses.put('B', byte.class);
        primitiveClasses.put('S', short.class);
        primitiveClasses.put('I', int.class);
        primitiveClasses.put('J', long.class);
        primitiveClasses.put('F', float.class);
        primitiveClasses.put('D', double.class);
        primitiveClasses.put('Z', boolean.class);
    }

    private static Class findClassImpl(String internalName, boolean checked) throws ClassNotFoundException {
        int lastBracket = internalName.lastIndexOf('[');
        String elemClassName = internalName.substring(lastBracket + 1).replace('/', '.');
        Class elemCls = null;

        // array class case
        if (lastBracket != -1) {
            if (elemClassName.length() == 1) {
                // must be some primitive class
                elemCls = primitiveClasses.get(elemClassName.charAt(0));
            } else if (elemClassName.endsWith(";")) {
                // remove "L" and ";"
                elemClassName = elemClassName.substring(1, elemClassName.length() - 1);
            }
        }

        // non-primitive element class
        if (elemCls == null) {
            elemCls = checked? findClassChecked(elemClassName) : Class.forName(elemClassName);
        }

        // handle array class case
        return lastBracket == -1? elemCls : Array.newInstance(elemCls, new int[lastBracket + 1]).getClass();
    }

    // called from native
    static Class findClass(String internalName) throws ClassNotFoundException {
        try {
            return findClassImpl(internalName, true);
        } catch (ClassNotFoundException|SecurityException ex) {
            if (DEBUG) {
                ex.printStackTrace();
            }
            throw ex;
        }
    }

    // called from native - when bootscript has to access classes for its own use
    static Class findClassPrivate(String internalName) throws ClassNotFoundException {
        return findClassImpl(internalName, false);
    }

    // called from native
    static ListAdapter createListAdapter(JSObject obj, Class<?> type) {
        if (type == List.class || type == Queue.class || type == Deque.class || type == Collection.class) {
            return ListAdapter.create(obj);
        } else {
            throw new IllegalArgumentException("cannot JS Array to: " + type);
        }
    }

    // V8 inspector support methods
    private static native void inspectorDispatchProtocolMessage0(long isolateRef, String msg);
    static void inspectorDispatchProtocolMessage(V8Isolate isolate, String msg) {
        if (DEBUG) {
            debugPrintf("inspector protocol message: %s", msg);
        }
        runInIsolate(isolate, (Supplier<Void>)() -> {
            inspectorDispatchProtocolMessage0(isolate.getReference(), msg);
            return null;
        });
    }

    // called from native
    static void inspectorSendResponse(V8Isolate isolate, int callId, String msg) {
        if (DEBUG) {
            debugPrintf("inspector response: (%d) %s", callId, msg);
        }
        runInIsolate(isolate, (Supplier<Void>)() -> {
            V8Inspector.Listener listener = isolate.getListener();
            if (listener != null) {
                listener.onResponse(msg);
            }
            return null;
        });
    }

    // called from native
    static void inspectorSendNotification(V8Isolate isolate, String msg) {
        if (DEBUG) {
            debugPrintf("inspector notification: %s", msg);
        }
        runInIsolate(isolate, (Supplier<Void>)() -> {
            V8Inspector.Listener listener = isolate.getListener();
            if (listener != null) {
                listener.onNotification(msg);
            }
            return null;
        });
    }

    // called from native
    static void inspectorRunMessageLoopOnPause(V8Isolate isolate) {
        if (DEBUG) {
            debugPrintf("inspector message loop on pause");
        }
        runInIsolate(isolate, (Supplier<Void>)() -> {
            V8Inspector.Listener listener = isolate.getListener();
            if (listener != null) {
                listener.runMessageLoopOnPause();
            }
            return null;
        });
    }

    // called from native
    static void inspectorQuitMessageLoopOnPause(V8Isolate isolate) {
        if (DEBUG) {
            debugPrintf("inspector quit message loop on pause");
        }
        runInIsolate(isolate, (Supplier<Void>)() -> {
            V8Inspector.Listener listener = isolate.getListener();
            if (listener != null) {
                listener.quitMessageLoopOnPause();
            }
            return null;
        });
    }

    // called from native
    static String resolveModule(String specifier, String[] importAttributes) throws ScriptException {
        if (DEBUG) {
            debugPrintf("resolve module");
        }
        V8ModuleResolver resolver = V8ScriptEngineImpl.getModuleResolver(getCurrentIsolate().getScriptContext());
        if (resolver == null) {
            throw new ScriptException("Can not import module. No module resolver callback has been registered.");
        }
        Map<String, String> attribMap = readImportAttributes(importAttributes);
        return resolver.resolve(specifier, attribMap);
    }

    private static Map<String, String> readImportAttributes(String[] importAttributes) {
        Map<String, String> result = new HashMap<>();
        // attributes appear in pairs of 3: key, value, column
        for (int i = 0; i < importAttributes.length; i += 2) {
            String key = importAttributes[i];
            String value = importAttributes[i + 1];
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private native static JSObject loadModule0(long isolateRef, long globalRef, String name, String script) throws ScriptException;
    public static JSObject loadModule(V8Object global, String name, String moduleSource, ScriptContext sc) throws ScriptException {
        final V8Isolate isolate = global.getIsolate();
        return runInIsolate(isolate, (Callable<JSObject>)() -> {
            synchronized(isolate) {
                if (V8.DEBUG) {
                    debugPrintf("Evaluating %s in global 0x%x", name, global.getReference());
                }
                ScriptContext oldCtx = isolate.getScriptContext();
                isolate.setScriptContext(sc);
                try {
                    return loadModule0(isolate.getReference(), global.getReference(), name, moduleSource);
                } finally {
                    isolate.setScriptContext(oldCtx);
                }
            }
        });
    }

    // called to register a V8Isolate object
    private static native void registerIsolate0(long isolateRef, V8Isolate isolate);

    static String readAll(Reader reader) throws IOException {
        Objects.requireNonNull(reader);
        final int BUFFER_SIZE = 4096;
        final char[] buffer = new char[BUFFER_SIZE];
        final StringBuilder sb = new StringBuilder();

        try (Reader r = reader) {
            int numChars;
            while ((numChars = r.read(buffer, 0, BUFFER_SIZE)) > 0) {
                sb.append(buffer, 0, numChars);
            }
        }

        return sb.toString();
    }

    static boolean isFunctionalInterface(Class<?> iface) {
        return iface.isInterface() && hasSAM(iface);
    }

    private static boolean hasSAM(Class<?> iface) {
        String samName = null;
        for (Method m : iface.getMethods()) {
            if (isObjectMethod(m)) {
                // cases where interface redeclared e.g. equals
                continue;
            }

            if (Modifier.isAbstract(m.getModifiers())) {
                if (samName != null && !samName.equals(m.getName())) {
                    return false;
                }
                samName = m.getName();
            }
        }
        return samName != null;
    }

    private static boolean isObjectMethod(Method m) {
        return switch (m.getName()) {
            case "equals" ->
                    m.getReturnType() == boolean.class
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == Object.class;
            case "hashCode" ->
                    m.getReturnType() == int.class
                    && m.getParameterCount() == 0;
            case "toString" ->
                    m.getReturnType() == String.class
                    && m.getParameterCount() == 0;
            default ->  false;
        };
    }
}
