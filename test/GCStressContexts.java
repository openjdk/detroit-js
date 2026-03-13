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

public class GCStressContexts {
    // Usage: GCStressContexts <num contexts>

    public static void main(String[] args) throws Exception {
        int numContexts = args.length == 0? 100 : Integer.parseInt(args[0]);
        boolean closeEngine = args.length < 2? false : Boolean.valueOf(args[1]);
        System.out.printf("Num contexts: %d\n", numContexts);

        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine engine = m.getEngineByName("v8");
        for (int i = 0; i < numContexts; i++) {
            System.out.println(i);
            engine.eval("print", engine.createBindings());
        }
    }
}
