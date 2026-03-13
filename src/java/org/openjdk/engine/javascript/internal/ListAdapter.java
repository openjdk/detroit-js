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

package org.openjdk.engine.javascript.internal;

import java.util.AbstractList;
import java.util.Deque;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import javax.script.ScriptException;
import org.openjdk.engine.javascript.JSObject;

/**
 * An adapter that can wrap any ECMAScript Array-like object (that adheres to the array rules for the property
 * {@code length} and having conforming {@code push}, {@code pop}, {@code shift}, {@code unshift}, and {@code splice}
 * methods) and expose it as both a Java list and double-ended queue. While script arrays aren't necessarily efficient
 * as dequeues, it's still slightly more efficient to be able to translate dequeue operations into pushes, pops, shifts,
 * and unshifts, than to blindly translate all list's add/remove operations into splices. Also, it is conceivable that a
 * custom script object that implements an Array-like API can have a background data representation that is optimized
 * for dequeue-like access. Note that with ECMAScript arrays, {@code push} and {@code pop} operate at the end of the
 * array, while in Java {@code Deque} they operate on the front of the queue and as such the Java dequeue
 * {@link #push(Object)} and {@link #pop()} operations will translate to {@code unshift} and {@code shift} script
 * operations respectively, while {@link #addLast(Object)} and {@link #removeLast()} will translate to {@code push} and
 * {@code pop}.
 */
final class ListAdapter extends AbstractList<Object> implements RandomAccess, Deque<Object> {
    /** wrapped object */
    private final JSObject obj;

    // allow subclasses only in this package
    private ListAdapter(final JSObject obj) {
        this.obj = obj;
    }

    /**
     * Factory to create a ListAdapter for a given script object.
     *
     * @param obj script object to wrap as a ListAdapter
     * @return A ListAdapter wrapper object
     */
    static ListAdapter create(final JSObject obj) {
        return new ListAdapter(obj);
    }

    @Override
    public final Object get(final int index) {
        checkRange(index);
        return getAt(index);
    }

    private Object getAt(final int index) {
        return obj.getSlot(index);
    }

    @Override
    public Object set(final int index, final Object element) {
        checkRange(index);
        final Object prevValue = getAt(index);
        obj.setSlot(index, element);
        return prevValue;
    }

    private void checkRange(final int index) {
        if(index < 0 || index >= size()) {
            throw invalidIndex(index);
        }
    }

    @Override
    public int size() {
        return ((Number)obj.getMember("length")).intValue();
    }

    @Override
    public final void push(final Object e) {
        addFirst(e);
    }

    @Override
    public final boolean add(final Object e) {
        addLast(e);
        return true;
    }

    @Override
    public final void addFirst(final Object e) {
        JSObject unshift = getFunction("unshift");
        try {
            unshift.call(obj, e);
        } catch(RuntimeException ex) {
            throw ex;
        } catch(ScriptException se) {
            throw new RuntimeException(se);
        }
    }

    @Override
    public final void addLast(final Object e) {
        JSObject push = getFunction("push");
        try {
            push.call(obj, e);
        } catch(RuntimeException ex) {
            throw ex;
        } catch(ScriptException se) {
            throw new RuntimeException(se);
        }
    }

    @Override
    public final void add(final int index, final Object e) {
        try {
            if(index < 0) {
                throw invalidIndex(index);
            } else if(index == 0) {
                addFirst(e);
            } else {
                final int size = size();
                if(index < size) {
                    JSObject splice = getFunction("splice");
                    splice.call(obj, index, 0, e);
                } else if(index == size) {
                    addLast(e);
                } else {
                    throw invalidIndex(index);
                }
            }
        } catch(final RuntimeException ex) {
            throw ex;
        } catch(final ScriptException se) {
            throw new RuntimeException(se);
        }
    }

    private JSObject getFunction(final String name) {
        final Object fn = obj.getMember(name);
        if(!(fn instanceof JSObject) || !((JSObject)fn).isFunction()) {
            throw new UnsupportedOperationException("The script object doesn't have a function named " + name);
        }
        return (JSObject)fn;
    }

    private static IndexOutOfBoundsException invalidIndex(final int index) {
        return new IndexOutOfBoundsException(String.valueOf(index));
    }

    @Override
    public final boolean offer(final Object e) {
        return offerLast(e);
    }

    @Override
    public final boolean offerFirst(final Object e) {
        addFirst(e);
        return true;
    }

    @Override
    public final boolean offerLast(final Object e) {
        addLast(e);
        return true;
    }

    @Override
    public final Object pop() {
        return removeFirst();
    }

    @Override
    public final Object remove() {
        return removeFirst();
    }

    @Override
    public final Object removeFirst() {
        checkNonEmpty();
        return invokeShift();
    }

    @Override
    public final Object removeLast() {
        checkNonEmpty();
        return invokePop();
    }

    private void checkNonEmpty() {
        if(isEmpty()) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public final Object remove(final int index) {
        if(index < 0) {
            throw invalidIndex(index);
        } else if (index == 0) {
            return invokeShift();
        } else {
            final int maxIndex = size() - 1;
            if(index < maxIndex) {
                final Object prevValue = get(index);
                invokeSpliceRemove(index, 1);
                return prevValue;
            } else if(index == maxIndex) {
                return invokePop();
            } else {
                throw invalidIndex(index);
            }
        }
    }

    private Object invokeShift() {
        JSObject shift = getFunction("shift");
        try {
            return shift.call(obj);
        } catch(RuntimeException ex) {
            throw ex;
        } catch(ScriptException se) {
            throw new RuntimeException(se);
        }
    }

    private Object invokePop() {
        JSObject pop = getFunction("pop");
        try {
            return pop.call(obj);
        } catch(RuntimeException ex) {
            throw ex;
        } catch(ScriptException se) {
            throw new RuntimeException(se);
        }
    }

    @Override
    protected final void removeRange(final int fromIndex, final int toIndex) {
        invokeSpliceRemove(fromIndex, toIndex - fromIndex);
    }

    private void invokeSpliceRemove(final int fromIndex, final int count) {
        JSObject splice = getFunction("splice");
        try {
            splice.call(obj, fromIndex, count);
        } catch(RuntimeException ex) {
            throw ex;
        } catch(ScriptException se) {
            throw new RuntimeException(se);
        }
    }

    @Override
    public final Object poll() {
        return pollFirst();
    }

    @Override
    public final Object pollFirst() {
        return isEmpty() ? null : invokeShift();
    }

    @Override
    public final Object pollLast() {
        return isEmpty() ? null : invokePop();
    }

    @Override
    public final Object peek() {
        return peekFirst();
    }

    @Override
    public final Object peekFirst() {
        return isEmpty() ? null : get(0);
    }

    @Override
    public final Object peekLast() {
        return isEmpty() ? null : get(size() - 1);
    }

    @Override
    public final Object element() {
        return getFirst();
    }

    @Override
    public final Object getFirst() {
        checkNonEmpty();
        return get(0);
    }

    @Override
    public final Object getLast() {
        checkNonEmpty();
        return get(size() - 1);
    }

    @Override
    public final Iterator<Object> descendingIterator() {
        final ListIterator<Object> it = listIterator(size());
        return new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return it.hasPrevious();
            }

            @Override
            public Object next() {
                return it.previous();
            }

            @Override
            public void remove() {
                it.remove();
            }
        };
    }

    @Override
    public final boolean removeFirstOccurrence(final Object o) {
        return removeOccurrence(o, iterator());
    }

    @Override
    public final boolean removeLastOccurrence(final Object o) {
        return removeOccurrence(o, descendingIterator());
    }

    private static boolean removeOccurrence(final Object o, final Iterator<Object> it) {
        while(it.hasNext()) {
            if(Objects.equals(o, it.next())) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public ListAdapter reversed() {
        return this;
    }

    static boolean canAdaptTo(final Class<?> cls) {
        // Must be an Iterable or Collection but not outside the List and Deque hierarchies.
        return Iterable.class.isAssignableFrom(cls) &&
                (cls.isAssignableFrom(AbstractList.class) || cls.isAssignableFrom(Deque.class));
    }
}
