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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

public abstract class V8AbstractScriptEngineFactory implements ScriptEngineFactory {
    static {
        try {
            V8.initialize();
        } catch (Throwable th) {
            // javax.script.ScriptEngineManager eats exceptions!
            // Show any initialization issue unconditionally!
            System.err.println("FAILED: V8 script engine factory initialization failed");
            th.printStackTrace();
            if (th instanceof RuntimeException) {
                throw (RuntimeException)th;
            } else {
                throw new RuntimeException(th);
            }
        }
    }

    @Override
    public String getEngineName() {
        return (String)getParameter(ScriptEngine.ENGINE);
    }

    @Override
    public String getEngineVersion() {
        return (String)getParameter(ScriptEngine.ENGINE_VERSION);
    }

    @Override
    public List<String> getExtensions() {
       return Collections.unmodifiableList(EXTENSIONS);
    }

    @Override
    public List<String> getMimeTypes() {
        return Collections.unmodifiableList(MIME_TYPES);
    }

    @Override
    public String getLanguageName() {
        return (String)getParameter(ScriptEngine.LANGUAGE);
    }

    @Override
    public String getLanguageVersion() {
        return (String)getParameter(ScriptEngine.LANGUAGE_VERSION);
    }

    @Override
    public Object getParameter(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must non-null");
        }

        switch (key) {
        case ScriptEngine.ENGINE_VERSION:
            return "14.3";
        case ScriptEngine.LANGUAGE:
            return "ECMAScript";
        case ScriptEngine.LANGUAGE_VERSION:
            return "ECMAScript 2025";
        case "THREADING":
            // The engine implementation is not thread-safe. Can't be
            // used to execute scripts concurrently on multiple threads.
            return null;
        default:
            return null;
        }
    }

    @Override
    public String getMethodCallSyntax(String thiz, String method, String... args) {
        if (thiz == null) {
            throw new IllegalArgumentException("Object must non-null");
        }

        if (method == null) {
            throw new IllegalArgumentException("Method name must non-null");
        }

        if (args == null) {
            throw new IllegalArgumentException("Arguments name must non-null");
        }

        final StringBuilder sb = new StringBuilder().append(thiz).append('.').append(method).append('(');
        final int len = args.length;

        if (len > 0) {
            sb.append(args[0]);
        }
        for (int i = 1; i < len; i++) {
            sb.append(',').append(args[i]);
        }
        sb.append(')');

        return sb.toString();
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        if (toDisplay == null) {
            throw new IllegalArgumentException("Output must non-null");
        }

        return "print(" + toDisplay + ")";
    }

    @Override
    public String getProgram(String... statements) {
        if (statements == null) {
            throw new IllegalArgumentException("Statements must non-null");
        }

        final StringBuilder sb = new StringBuilder();

        for (final String statement : statements) {
            sb.append(statement).append(';');
        }

        return sb.toString();
    }

    private static final List<String> MIME_TYPES;
    private static final List<String> EXTENSIONS;

    static {
        MIME_TYPES = immutableList(
                        "application/javascript",
                        "application/ecmascript",
                        "text/javascript",
                        "text/ecmascript"
                    );

        EXTENSIONS = immutableList("js");
    }

    private static List<String> immutableList(final String... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
}
