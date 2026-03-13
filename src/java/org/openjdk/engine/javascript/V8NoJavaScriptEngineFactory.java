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
package org.openjdk.engine.javascript;

import org.openjdk.engine.javascript.internal.V8;
import org.openjdk.engine.javascript.internal.V8AbstractScriptEngineFactory;
import org.openjdk.engine.javascript.internal.V8ScriptEngineImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.script.ScriptEngine;

public final class V8NoJavaScriptEngineFactory extends V8AbstractScriptEngineFactory {

    // Called by service loader implementation
    public V8NoJavaScriptEngineFactory() {
    }

    @Override
    public Object getParameter(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must non-null");
        }

        switch (key) {
        case ScriptEngine.NAME:
            return "v8-no-java";
        case ScriptEngine.ENGINE:
            return "Oracle V8 No Java";
        }
        return super.getParameter(key);
    }

    @Override
    public V8ScriptEngine getScriptEngine() {
        return getScriptEngine(false);
    }

    public V8ScriptEngine getScriptEngine(boolean inspector) {
        try {
            return new V8ScriptEngineImpl(this, null, inspector);
        } catch (RuntimeException re) {
            if (V8.DEBUG) {
                V8.debugPrintf("Failed to create a new V8 sript engine: %s", re.toString());
                re.printStackTrace();
            }
            throw re;
        }
    }

    @Override
    public List<String> getNames() {
        return Collections.unmodifiableList(NAMES);
    }

    private static final List<String> NAMES;
    static {
        NAMES = immutableList(
                    "v8-no-java", "V8-no-java"
                );
    }

    private static List<String> immutableList(final String... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
}
