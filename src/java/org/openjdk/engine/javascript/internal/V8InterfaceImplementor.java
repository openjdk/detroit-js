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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/*
 * java.lang.reflect.Proxy based interface implementor. This is meant
 * to be used to implement Invocable.getInterface.
 */
@SuppressWarnings("removal")
abstract class V8InterfaceImplementor {
    V8InterfaceImplementor() {
    }

    private final class V8InterfaceImplementorInvocationHandler
                implements InvocationHandler {
        private final V8Object thiz;

        public V8InterfaceImplementorInvocationHandler(V8Object thiz) {
            this.thiz = thiz;
        }

        @Override
        public Object invoke(Object proxy , final Method method, final Object[] args)
                throws Throwable {
            return V8InterfaceImplementor.this.invoke(thiz, method, args);
        }
    }

    public final <T> T getInterface(Object thiz, Class<T> iface) {
        if (iface == null || !iface.isInterface()) {
            throw new IllegalArgumentException("interface Class expected");
        }

        V8.checkPackageAccess(iface);

        checkThis(thiz);

        if (! isImplemented((V8Object)thiz, iface)) {
            return null;
        }

        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(),
            new Class[] { iface },
            new V8InterfaceImplementorInvocationHandler((V8Object)thiz)));
    }

    protected abstract Object invoke(V8Object thiz, Method method, Object[] args) throws Exception;

    protected void checkThis(Object thiz) {
        if (thiz == null || !(thiz instanceof V8Object)) {
            throw new IllegalArgumentException("'this' must be a V8 object");
        }
    }

    protected boolean isImplemented(V8Object self, Class<?> iface) {
        // Allow functional interface to be implemented by JS function
        if (V8.isFunctionalInterface(iface) && self instanceof V8Function) {
            return true;
        }

        for (final Method method : iface.getMethods()) {
            // ignore methods of java.lang.Object class
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            // skip check for default methods - non-abstract, interface methods
            if (! Modifier.isAbstract(method.getModifiers())) {
                continue;
            }

            final Object obj = self.getMember(method.getName());
            if (!(obj instanceof V8Function)) {
                return false;
            }
        }

        return true;
    }
}
