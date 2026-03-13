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
import org.openjdk.engine.javascript.V8Undefined;

import java.util.HashSet;
import java.util.Set;
import javax.script.Bindings;
import javax.script.ScriptContext;

/**
 * Instance of this class is exposed to V8 global objects as "context" property.
 * The context object exposes it's scope variables via JSObject hooks.
 * Script authors can either access variables using context.var_name or use a
 * 'with' statement:
 *
 * <code>
 * <pre>
 *    with(context) {
 *        // access context attributes by simple names
 *    }
 * </pre>
 * </code>
 * In addition, a special property "this" is supported on "context" object. This
 * can be used to access underlying ScriptContext instance that is being wrapped.
 */
final class V8ScriptContextWrapper implements JSObject {
    // sole instance
    public static final V8ScriptContextWrapper instance = new V8ScriptContextWrapper();

    private V8ScriptContextWrapper() {
    }

    private static ScriptContext getScriptContext() {
        return V8.getCurrentIsolate().getScriptContext();
    }

    // JSObject methods
    @Override
    public Object getMember(String name) {
        ScriptContext sc = getScriptContext();
        if ("this".equals(name)) {
            return sc;
        }

        return sc != null? sc.getAttribute(name) : V8Undefined.INSTANCE;
    }

    @Override
    public boolean setMember(String name, Object value) {
        if ("this".equals(name)) {
            return false;
        }
        ScriptContext sc = getScriptContext();
        if (sc != null) {
            int scope = sc.getAttributesScope(name);
            if (scope == -1) {
                scope = ScriptContext.ENGINE_SCOPE;
            }
            sc.setAttribute(name, value, scope);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasMember(String name) {
        if ("this".equals(name)) {
            return true;
        }
        ScriptContext sc = getScriptContext();
        return sc != null? sc.getAttributesScope(name) != -1 : false;
    }

    @Override
    public boolean removeMember(String name) {
        if ("this".equals(name)) {
            return false;
        }
        ScriptContext sc = getScriptContext();
        if (sc != null) {
            int scope = sc.getAttributesScope(name);
            if (scope != -1) {
                sc.removeAttribute(name, scope);
                return true;
            }
        }
        return false;
    }

    @Override
    public String[] getNamedProperties() {
        ScriptContext sc = getScriptContext();
        if (sc != null) {
            Set<String> names = new HashSet<>();
            for (int scope : sc.getScopes()) {
                Bindings b = sc.getBindings(scope);
                if (b != null) {
                    names.addAll(b.keySet());
                }
            }
            return names.toArray(new String[0]);
        } else {
            return new String[0];
        }
    }

    @Override
    public String toString() {
        return "[object context]";
    }
}
