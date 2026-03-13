/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


import javax.script.*;
import java.io.*;

public class ConsoleExample {
    public static class Console {
        private final PrintWriter out;
        private final PrintWriter err;

        private Console(ScriptContext sc) {
            this.out = new PrintWriter(sc.getWriter(), true);
            this.err = new PrintWriter(sc.getErrorWriter(), true);
        }

        public void log(String message) {
            out.println("[LOG] " + message);
        }

        public void warn(String message) {
            out.println("[WARN] " + message);
        }

        public void error(String message) {
            err.println("[ERROR] " + message);
        }

        public void info(String message) {
            out.println("[INFO] " + message);
        }

        public void log(Object... args) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                builder.append(args[i] != null ? args[i].toString() : "null");
                if (i < args.length - 1) {
                    builder.append(" ");
                }
            }
            log(builder.toString());
        }
    }

    public static void main(String[] args) throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine e = m.getEngineByName("v8");

        e.put("console", new Console(e.getContext()));
        e.eval("console.log('hello')");
    }
}
