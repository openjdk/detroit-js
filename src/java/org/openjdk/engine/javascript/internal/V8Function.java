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
import org.openjdk.engine.javascript.V8Undefined;

import java.lang.reflect.Method;

final class V8Function extends V8Object implements JSFunction {
    private V8Function(long reference) {
        super(reference);
    }

    // called from native
    static V8Function create(long objectRef) {
        if (V8.DEBUG) debug("Function", objectRef);
        return objectRef != 0L? new V8Function(objectRef) : null;
    }

    @Override
    public String getName() {
        return V8.getFunctionName(this);
    }

    @Override
    public <T> T getInterface(Class<T> iface) {
        return (new V8InterfaceImplementor() {
            @Override
            protected Object invoke(V8Object self, Method method, Object[] args)
                    throws Exception {
                if (method.getDeclaringClass() == Object.class) {
                    String name = method.getName();
                    switch (name) {
                        case "toString":
                            return "[Object " + iface.getSimpleName() + "]";
                        case "hashCode":
                            return self.hashCode();
                        case "equals":
                            assert args.length == 1;
                            return self == args[0];
                        default:
                            throw new IllegalArgumentException("Unhandled method: " + method);
                    }
                }
                return V8.invoke(self, V8Undefined.INSTANCE, args);
            }
        }).getInterface(this, iface);
    }
}
