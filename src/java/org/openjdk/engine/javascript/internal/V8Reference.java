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

abstract class V8Reference {
    private final long reference;

    @SuppressWarnings("LeakingThisInConstructor")
    V8Reference(V8Isolate isolate, long reference) {
        this.reference = reference;
        RefCleaner.register(V8Reference.this, referenceCleaner(isolate, V8Reference.this.getClass().getSimpleName(), reference));
    }

    // Use this when you're not sure if obj could be null or not
    static long getReference(V8Reference obj) {
        return obj != null? obj.getReference() : 0L;
    }

    final long getReference() {
        return reference;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@0x" + Long.toHexString(reference);
    }

    private static Runnable referenceCleaner(V8Isolate isolate, String className, long reference) {
        return () -> {
            try {
                if (!isolate.isDisposed()) {
                    V8.releaseReference(isolate.getReference(), className, reference);
                }
            } catch (Throwable th) {
                // Any Throwable from cleaner thunk results from System.exit!
                // If DEBUG mode, print stack trace. Or else swallow it!
                if (V8.DEBUG) {
                    th.printStackTrace();
                }
            }
        };
    }
}
