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

abstract class V8Generator {
    private static final String SEPARATOR = System.lineSeparator();
    private static final int INDENT = 4;
    private final boolean debug;

    private final StringBuilder code;
    private int indent;
    private boolean newline;

    V8Generator() {
        this(false);
    }

    V8Generator(boolean debug) {
        this.debug = debug;
        this.code = new StringBuilder();
        this.indent = 0;
        this.newline = true;
    }

    private void checkNewline() {
        if (debug && newline) {
            for (int i = 0; i < indent; i++) {
                code.append(' ');
            }

            newline = false;
        }
    }

    protected void p(String string) {
        checkNewline();
        code.append(string);
    }

    protected void p(char ch) {
        checkNewline();
        code.append(ch);
    }

    protected void p(int i) {
        checkNewline();
        code.append(i);
    }

    protected void fill(String string, int count) {
        for (int i = 0; i < count; i++) {
            code.append(string);
        }
    }

    protected void fill(char ch, int count) {
        for (int i = 0; i < count; i++) {
            code.append(ch);
        }
    }

    protected void l(String string, int width) {
        l(string, width, ' ');
    }

    protected void l(String string, int width, char fill) {
        if (string.length() > width) {
            string = string.substring(0, width);
        }

        p(string);
        fill(fill, width - string.length());
    }

    protected void r(String string, int width) {
        l(string, width, ' ');
    }

    protected void r(String string, int width, char fill) {
        if (string.length() > width) {
            string = string.substring(0, width);
        }

        fill(fill, width - string.length());
        p(string);
    }

    protected void hex(int i) {
        p("0x");
        r(Integer.toHexString(i), 8, '0');
    }

    protected void hex(long i) {
        p("0x");
        r(Long.toHexString(i), 8, '0');
    }

    protected void q(String string) {
        p('\'');
        p(string);
        p('\'');
    }

    protected void nl() {
        if (debug) {
            p(SEPARATOR);
            newline = true;
        }
    }

    protected void begin() {
        indent += INDENT;
    }

    protected void end() {
        indent -= INDENT;
    }

    protected String code() {
        return code.toString();
    }

    abstract String generateConstructor();
    abstract String generateProperty(String prop, boolean isStatic);
}
