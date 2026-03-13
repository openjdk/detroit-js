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
package scriptrunner;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.engine.javascript.V8ScriptException;

/**
 * Run all javascript tests under jtreg "test.src" directory. If any script
 * throws exception, that is considered failed. Total count of failed script
 * tests should be zero for this jtreg pass to pass.
 *
 * @test
 * @library ../.
 * @build UnnamedPackageTestCallback models.*
 * @build FieldHolder
 * @run junit scriptrunner.ScriptRunnerTest
 */
public class ScriptRunnerTest {
    private static final Path TEST_SRC = Path.of(System.getProperty("test.src", "."));
    private static final Path SCRIPTS_DIR = TEST_SRC.resolve("scripts");
    private static final Path ASSERTJS = TEST_SRC.resolve("assert.js");

    @ParameterizedTest
    @MethodSource("scripts")
    public void runScript(Path script) throws IOException, ScriptException {
        final ScriptEngineManager m = new ScriptEngineManager();
        final ScriptEngine e = m.getEngineByName("v8");

        try (InputStream is = Files.newInputStream(SCRIPTS_DIR.resolve(script));
             InputStream ais = Files.newInputStream(ASSERTJS)) {
            Bindings bindings = e.createBindings();
            bindings.put(ScriptEngine.FILENAME, ASSERTJS.toString());
            e.eval(new InputStreamReader(ais), bindings);
            bindings.put(ScriptEngine.FILENAME, script.toString());
            e.eval(new InputStreamReader(is), bindings);
        } catch (V8ScriptException se) {
            System.err.println("ECMA error frames");
            Arrays.asList(se.getScriptFrames()).forEach(System.err::println);
            throw se;
        }
    }

    public static Object[][] scripts() throws IOException {
        try (Stream<Path> files = Files.walk(SCRIPTS_DIR).skip(1)) { // skip directory itself
            return files
                    .map(SCRIPTS_DIR::relativize) // get shorter paths
                    .map(f -> new Object[]{f}).toArray(Object[][]::new);
        }
    }
}
