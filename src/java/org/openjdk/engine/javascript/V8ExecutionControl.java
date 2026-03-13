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
 * Interface to control V8 script engine's execution. For example, long running
 * "runaway" scripts can be interrupted and/or terminated using this interface.
 */
public interface V8ExecutionControl {
    /**
     * Forcefully terminate the current thread of JavaScript execution in the given engine.
     */
    public void terminateExecution();

    /**
     * Request V8 to interrupt long running JavaScript code and invoke the given callback.
     * After callback returns, the control will be returned to the JavaScript code.
     * Registered callback must not reenter interrupted engine for JavaScript evaluation.
     *
     * @param callback callback that is invoked on interruption.
     * @throws NullPointerException if callback is null.
     */
    public void requestInterrupt(Runnable callback);

    /**
     * Runs the Microtask Work Queue until empty Any exceptions thrown by microtask
     * callbacks are swallowed.
     */
    public void runMicrotasks();

    /**
     * Enqueues the callback to the Microtask Work Queue.
     *
     * @param microtask callback function for the microtask.
     * @throws IllegalArgumentException if microtask is not function from this engine.
     * @throws NullPointerException if microtask is null.
     */
    public void enqueueMicrotask(JSFunction microtask);

    /**
     * Schedules a JavaScript exception to be thrown when returning to JavaScript. When an exception
     * has been scheduled it is illegal to invoke any JavaScript operation; the caller must
     * return immediately. The Void return type is used so that the caller can immediately return.
     *
     * @param exception V8 exception to be thrown.
     * @return null always. Return type provided so that caller immediately return.
     */
    public Object throwException(Object exception);

    /**
     * Returns array javascript stack frames from the current javascript stack.
     *
     * @return array of javascript stack frames, null if not available.
     */
    public StackTraceElement[] getCurrentStackTrace();
}
