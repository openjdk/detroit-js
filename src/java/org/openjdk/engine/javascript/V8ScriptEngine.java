/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.engine.javascript.internal.V8ScriptEngineImpl;

import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * This interface contains additional API points unique to the V8ScriptEngine implementation
 */
public sealed interface V8ScriptEngine extends ScriptEngine, Compilable, Invocable,
        AutoCloseable, V8ExecutionControl, V8ScriptCompiler permits V8ScriptEngineImpl {

    /** The ScriptContext attribute key for the module resolver */
    String MODULE_RESOLVER = "org.openjdk.engine.javascript.moduleResolver";

    /**
     * Load a javascript module from source
     *
     * @param moduleSource the source of the module
     * @return the namespace object of the module
     */
    JSObject loadModule(String moduleSource) throws ScriptException;

    /**
     * Load a javascript module from source
     *
     * @param moduleSource the source of the module
     * @param sc the script context to use when loading the module
     * @return the namespace object of the module
     */
    JSObject loadModule(String moduleSource, ScriptContext sc) throws ScriptException;

    /**
     * Get the V8Inspector object to interact with the inspector.
     *
     * @return V8Inspector object.
     * @throws IllegalStateException if the engine is not initialized with V8 inspector enabled
     */
    V8Inspector getInspector();
}
