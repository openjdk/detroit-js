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

/**
 * Interface to V8 Inspector.
 */
public interface V8Inspector {
    /**
     * Listener interface to receive messages/calls from V8 inspector.
     */
    public interface Listener {
        /**
         * Called to send a response message from V8 inspector to the front-end.
         *
         * @param msg the JSON String messsage.
         */
        public void onResponse(String msg);

        /**
         * Called to send a notification message from V8 inspector to the front-end.
         *
         * @param msg the JSON String messsage.
         */
        public void onNotification(String msg);

        /**
         * Run nested message loop when V8 is paused on a breakpoint.
         */
        public void runMessageLoopOnPause();

        /**
         * Quit nested message loop entered when V8 is paused on a breakpoint.
         */
        public void quitMessageLoopOnPause();
    }

    /**
     * Set the V8 Inspector listener.
     *
     * @param listener listener to process messages from V8 inspector.
     */
    public void setListener(Listener listener);

    /**
     * Pass JSON protocol message from the front-end to the V8 inspector.
     *
     * @param msg JSON protocol message from the front-end.
     */
    public void dispatchProtocolMessage(String msg);
}
