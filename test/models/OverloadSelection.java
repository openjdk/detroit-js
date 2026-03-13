/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package models;

import java.io.File;
import java.util.Arrays;

@SuppressWarnings("javadoc")
public class OverloadSelection {

    public static String overloadedMethod(final boolean b) {
        return "boolean: " + b;
    }

    public static String overloadedMethod(final byte b) {
        return "byte: " + b;
    }

    public static String overloadedMethod(final short s) {
        return "short: " + s;
    }

    public static String overloadedMethod(final int i) {
        return "int: " + i;
    }

    public static String overloadedMethod(final long l) {
        return "long: " + l;
    }

    public static String overloadedMethod(final char c) {
        return "char: " + c;
    }

    public static String overloadedMethod(final float f) {
        return "float: " + f;
    }

    public static String overloadedMethod(final double d) {
        return "double: " + d;
    }

    public static String overloadedMethod(final Object o) {
        return "Object: " + o;
    }

    public static String overloadedMethod(final String s) {
        return "String: " + s;
    }

    public static String overloadedMethod(final Object[] a) {
        return "Object[]: " + Arrays.toString(a);
    }

    public static String overloadedVarArgsMethod(final String[] s) {
        return "String[]: " + Arrays.toString(s);
    }

    public static String overloadedVarArgsMethod(final File[] f) {
        return "File[]: " + Arrays.toString(f);
    }
}
