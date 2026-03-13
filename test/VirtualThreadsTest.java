/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static org.testng.Assert.assertEquals;

/**
 * @test
 * @run testng VirtualThreadsTest
 */

public class VirtualThreadsTest {

    static final String ENGINE = "v8-no-java";
    // OOME with 100_000 threads
    // #
    // # Fatal process out of memory: JSDispatchTable::AllocateAndInitializeEntry
    // #
    static final int NUM_THREADS = 10_000;

    @Test
    public void testManyEngines() throws Throwable {
        CountDownLatch threadLatch = new CountDownLatch(NUM_THREADS);
        CountDownLatch mainLatch = new CountDownLatch(1);

        List<Thread> threads = new ArrayList<>();
        Map<Thread, Throwable> exceptions = new ConcurrentHashMap<>();
        for (int count = NUM_THREADS; count > 0; count--) {
            threads.add(Thread.ofVirtual()
                    .name(String.valueOf(count))
                    .uncaughtExceptionHandler(exceptions::put)
                    .start(() -> {
                ScriptEngineManager m = new ScriptEngineManager();
                ScriptEngine e = m.getEngineByName(ENGINE);
                try {
                    int x = (int) e.eval("const x = Math.floor(Math.random() * 100000); x");
                    threadLatch.countDown();
                    mainLatch.await(); // VThread unmounts here
                    // Likely a different carrier thread when resuming
                    // make sure things still work
                    assertEquals(e.eval("x"), x);
                } catch (ScriptException | InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }));
        }

        threadLatch.await();
        mainLatch.countDown();

        for (Thread thread : threads) {
            thread.join();
            Throwable ex = exceptions.get(thread);
            if (ex != null) {
                throw ex;
            }
        }
    }

    @Test
    public void testSingleEngine() throws Throwable {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName(ENGINE);

        CountDownLatch threadLatch = new CountDownLatch(NUM_THREADS);
        CountDownLatch mainLatch = new CountDownLatch(1);

        List<Thread> threads = new ArrayList<>();
        Map<Thread, Throwable> exceptions = new ConcurrentHashMap<>();
        for (int count = NUM_THREADS; count > 0; count--) {
            final long id = count;
            threads.add(Thread.ofVirtual()
                    .name(String.valueOf(id))
                    .uncaughtExceptionHandler(exceptions::put)
                    .start(() -> {
                try {
                    String varName = "x" + id;
                    int x = (int) e.eval("const " + varName + " = Math.floor(Math.random() * 100000); " + varName);
                    threadLatch.countDown();
                    mainLatch.await(); // VThread unmounts here
                    // Likely a different carrier thread when resuming
                    // make sure things still work
                    assertEquals(e.eval(varName), x);
                } catch (ScriptException | InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }));
        }

        threadLatch.await();
        mainLatch.countDown();

        for (Thread thread : threads) {
            thread.join();
            Throwable ex = exceptions.get(thread);
            if (ex != null) {
                throw ex;
            }
        }
    }

}
