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

import javax.script.*;

public class GCStressIsolates {
    // Usage: GCStressIsolates <num engines> <explicit close>

    public static void main(String[] args) throws Exception {
        // Danger!! Enable only for stress testing!
        //  System.runFinalizersOnExit(true);

        int numEngines = args.length == 0? 100 : Integer.parseInt(args[0]);
        boolean closeEngine = args.length < 2? false : Boolean.valueOf(args[1]);
        // Optionally, uncomment explicit GC call.
        // System.gc();

        System.out.printf("Num engines: %d, explicit close: %b\n", numEngines, closeEngine);
        stress(numEngines, closeEngine);
   }

   private static void stress(int numEngines, boolean close) throws Exception {
       ScriptEngineManager m = new ScriptEngineManager();
       for (int i = 0; i < numEngines; i++) {
           System.out.println(i);
           ScriptEngine e = m.getEngineByName("v8");
           e.eval("var Sys = Java.type('java.lang.System')");
           if (close) {
               ((AutoCloseable)e).close();
           }
       }
   }
}
