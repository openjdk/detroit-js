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

import java.util.Map;

/**
 * A module resolver represents the callback that v8 uses to find the source
 * of a module imported by an
 * <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/import">import statement</a>.
 * <p>
 * A resolver may be registered with a {@link javax.script.ScriptContext} by
 * {@linkplain javax.script.ScriptContext#setAttribute(String, Object, int) setting it as an attribute}
 * using the {@link V8ScriptEngine#MODULE_RESOLVER} key.
 * <p>>
 * Setting a module resolver is required for {@code import} statements
 * to work. If no resolver is set, a {@link javax.script.ScriptException} will be thrown.
 */
@FunctionalInterface
public interface V8ModuleResolver {
    /**
     * Resolves a module given a specifier and attribute set.
     * <p>
     * This will be called by a script engine once for every,
     * specifier and attribute combination. Therefor the user
     * does not need to worry about caching.
     *
     * @param specifier the import specifier
     * @param importAttributes the import attributes
     * @return the source of the resolved module
     */
    String resolve(String specifier, Map<String, String> importAttributes);
}
