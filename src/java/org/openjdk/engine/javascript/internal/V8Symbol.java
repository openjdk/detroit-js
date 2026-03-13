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

import org.openjdk.engine.javascript.JSSymbol;

final class V8Symbol extends V8Value implements JSSymbol {
    private final V8Isolate isolate;
    private final String str;

    V8Symbol(V8Isolate isolate, long symbolRef, String str) {
        super(isolate, symbolRef);
        this.isolate = isolate;
        this.str = str;
    }

    V8Isolate getIsolate() {
        return isolate;
    }

    // called from native
    static V8Symbol create(long symbolRef, String str) {
        if (V8.DEBUG) debug("Symbol", symbolRef);
        return symbolRef != 0L? new V8Symbol(V8.getCurrentIsolate(), symbolRef, str) : null;
    }

    // called from native
    // if 'this' V8 Symbol belongs to the given isolate, then return the reference or else return 0L.
    // This method is called from the native code to make sure that we do *not* pass Symbol from
    // a V8 Isolate I1 to another V8 Isolate I2 "as is".
    final long checkAndGetReference(long otherIsolateRef) {
        return isolate.getReference() == otherIsolateRef? getReference() : 0L;
    }

    @Override
    public String toString() {
        return str;
    }


    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof V8Symbol) {
            return V8.strictEquals(this, ((V8Symbol)other));
        }

        return false;
    }

    boolean isFromIsolate(V8Isolate otherIsolate) {
         return isolate.equals(otherIsolate);
    }
}
