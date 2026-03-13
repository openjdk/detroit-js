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
import java.io.IOException;
import org.openjdk.engine.javascript.*;

public class ExecutionControl {
   public static void main(String[] a) throws Exception {
       final ScriptEngineManager m = new ScriptEngineManager();
       final ScriptEngine e = m.getEngineByName("v8");
       final V8ExecutionControl control = (V8ExecutionControl)e;

       new Thread(() -> {
           System.out.println("Press enter to interrupt long running script");
           // wait for user inout
           try { System.in.read(); } catch(Exception ex) {}

           Runnable callback = () -> {
              System.out.println("interrupted! Press 'q' to terminate!");

               int c = -1;
               try {
                   c = System.in.read();
               } catch (IOException ignored) {}

               if (c == 'q') {
                   // terminate it!
                   control.terminateExecution();
               }
           };

           // schedule interrupt on long running script!
           control.requestInterrupt(callback);
       }).start();


       try {
           e.eval("while(true);");
       } catch(Exception ex) {
           // Script terminated exception
           ex.printStackTrace();
       }

       // another execution
       e.eval("print('hello')");
   }
}
