/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.engine.javascript.micro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.openjdk.engine.javascript.*;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import javax.script.ScriptContext;

import org.openjdk.jmh.annotations.Setup;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
public class JavaAccess {

    static final ScriptEngineManager SEM = new ScriptEngineManager();
    static final ScriptEngine E = SEM.getEngineByName("v8-no-java");

    List<String> list = Arrays.asList(new String[1]);
    JSFunction CALLBACK;

    @Setup
    public void setup() {
        try {
            E.getBindings(ScriptContext.ENGINE_SCOPE).put("listAdd", JSFunction.consumer(o -> list.set(0, (String) o)));
            CALLBACK = (JSFunction) E.eval("function callback() { listAdd(\"asdf\"); }; callback");
        } catch (ScriptException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Benchmark
    public void java_addList() throws ScriptException {
        list.set(0, "asdf");
    }

    @Benchmark
    public void js_addList() throws ScriptException {
        CALLBACK.call(CALLBACK);
    }

    private static void dummy() {}
}
