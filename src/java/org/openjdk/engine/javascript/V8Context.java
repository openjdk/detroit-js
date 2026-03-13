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

/**
 * Control aspects of a V8 Context.
 */
public interface V8Context {
    /**
     * Control whether code generation from strings is allowed. Calling this method with false
     * will disable 'eval' and the 'Function' constructor for code running in this context.
     * If 'eval' or the 'Function' constructor are used an exception will be thrown.
     *
     * @param allow flag to set whether to allow eval and Function or not.
     */
    void allowCodeGenerationFromStrings(boolean allow);

    /**
     * Returns true if code generation from strings is allowed for the context.
     *
     * @return true if code generation from strings is allowed, false otherwise.
     */
    boolean isCodeGenerationFromStringsAllowed();

    /**
     * Return the global scope object associated this Context.
     *
     * @return the global scope object
     */
    JSObject getGlobal();
}
