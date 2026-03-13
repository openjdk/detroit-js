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
import org.openjdk.engine.javascript.internal.V8Object;

import javax.script.ScriptException;

/**
 * Represents a V8 Exception on Java side.
 */
public final class V8ScriptException extends ScriptException {
    private static long serialVersionUID = 1L;

    // source line from the script (if available)
    private final String sourceLine;
    // underlying ECMA Error object (if available)
    private Object ecmaError;


    /**
     * Creates a <code>V8ScriptException</code> with a String to be used in its message.
     * Filename, and line and column numbers are unspecified.
     *
     * @param message The String to use in the message.
     */
    public V8ScriptException(String message) {
        super(message);
        this.sourceLine = null;
        this.ecmaError = null;
    }

    /**
     * <code>V8ScriptException</code> constructor specifying message, filename, line number
     * and column number.
     * @param message The message.
     * @param fileName The filename
     * @param lineNumber the line number.
     * @param columnNumber the column number.
     */
    public V8ScriptException(String message, String fileName, int lineNumber, int columnNumber) {
        this(message, fileName, lineNumber, columnNumber, null, null);
    }

    // called from native
    /**
     * <code>V8ScriptException</code> constructor specifying message, filename, line number
     * column number and ECMAScript exception object.
     * @param message The message.
     * @param fileName The filename
     * @param lineNumber the line number.
     * @param columnNumber the column number.
     * @param sourceLine the source line from script.
     * @param ecmaError the underlying ECMAScript error object (can be null).
     */
    public V8ScriptException(String message, String fileName, int lineNumber, int columnNumber,
            String sourceLine, Object ecmaError) {
        super(message, fileName, lineNumber, columnNumber);
        this.sourceLine = sourceLine;
        this.ecmaError = ecmaError;
    }

    /**
     * Returns the source script line (if available).
     *
     * @return the source script line or null if not available.
     */
    public String getSourceLine() {
        return sourceLine;
    }

    /**
     * Returns the underlying ECMAScript error object (if available).
     *
     * @return the underlying ECMAScript error object or null.
     */
    public Object getEcmaError() {
        return ecmaError;
    }

    @Override
    public String getMessage() {
        String msg = super.getMessage();

        // if we have source line, print it with caret!
        if (sourceLine != null) {
            String eoln = System.lineSeparator();
            StringBuilder sb = new StringBuilder(msg);
            sb.append(eoln).append(sourceLine);

            int column = getColumnNumber();
            if (column > -1) {
                sb.append(eoln);
                // Pointer to column.
                for (int i = 0; i < column; i++) {
                    if (i < sourceLine.length() && sourceLine.charAt(i) == '\t') {
                        sb.append('\t');
                    } else {
                        sb.append(' ');
                    }
                }

                sb.append('^');
                sb.append(eoln);
            }
            return sb.toString();
        }

        return msg;
    }

    /**
     * Returns array javascript stack frames from this exception object.
     *
     * @return array of javascript stack frames, null if not available.
     */
    public StackTraceElement[] getScriptFrames() {
        if (ecmaError instanceof V8Object) {
            return V8.getStackTrace((V8Object)ecmaError);
        }
        return null;
    }

    /**
     * Returns array javascript stack frames from the given JS error object.
     *
     * @param error JS error object for which script frames are retrieved.
     * @return array of javascript stack frames, null if not available.
     */
    public static StackTraceElement[] getScriptFrames(JSObject error) {
        if (error instanceof V8Object) {
            return V8.getStackTrace((V8Object)error);
        }
        return null;
    }
}
