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


var a = ['a', 'b', 'c', 'd']

var l = Java.to(a, java.util.List)
assertTrue(l instanceof java.util.List)
assertTrue(l instanceof java.util.Deque)

assertEquals(l[0], 'a')
assertEquals(l[1], 'b')
assertEquals(l[2], 'c')
assertEquals(l[3], 'd')

assertEquals(l.size(), 4)

l.push('x')
assertEquals(a.toString(), "x,a,b,c,d")

l.addLast('y')
assertEquals(a.toString(), "x,a,b,c,d,y")

assertEquals(l.pop().toString(), "x")
assertEquals(l.removeLast().toString(), "y")
assertEquals(a.toString(), "a,b,c,d")

l.add('e')
l.add(5, 'f')
assertEquals(a.toString(), "a,b,c,d,e,f");

l.add(0, 'z')
assertEquals(a.toString(), "z,a,b,c,d,e,f");

l.add(2, 'x')
assertEquals(a.toString(), "z,a,x,b,c,d,e,f");

l[7] = 'g'
assertEquals(a.toString(), "z,a,x,b,c,d,e,g");

const IOBE = java.lang.IndexOutOfBoundsException;

try { l.add(15, '') } catch(e) { assertTrue(e.javaException instanceof IOBE) }
try { l.remove(15) } catch(e) { assertTrue(e.javaException instanceof IOBE) }
try { l.add(-1, '') } catch(e) { assertTrue(e.javaException instanceof IOBE) }
try { l.remove(-1) } catch(e) { assertTrue(e.javaException instanceof IOBE) }

l.remove(7)
l.remove(2)
l.remove(0)
assertEquals(a.toString(), "a,b,c,d,e");

assertEquals(l.peek().toString(), "a")
assertEquals(l.peekFirst().toString(), "a")
assertEquals(l.peekLast().toString(), "e")

assertEquals(l.element().toString(), "a")
assertEquals(l.getFirst().toString(), "a")
assertEquals(l.getLast().toString(), "e")

l.offer('1')
l.offerFirst('2')
l.offerLast('3')
assertEquals(a.toString(), "2,a,b,c,d,e,1,3")

a = ['1', '2', 'x', '3', '4', 'x', '5', '6', 'x', '7', '8']
assertEquals(a.toString(), "1,2,x,3,4,x,5,6,x,7,8")

var l = Java.to(a, java.util.List)
l.removeFirstOccurrence('x')
assertEquals(a.toString(), "1,2,3,4,x,5,6,x,7,8")
l.removeLastOccurrence('x')
assertEquals(a.toString(), "1,2,3,4,x,5,6,7,8")

var empty = Java.to([], java.util.List)
const NSEE = java.util.NoSuchElementException;

try { empty.pop() } catch(e) { assertTrue(e.javaException instanceof NSEE) }
try { empty.removeFirst() } catch(e) { assertTrue(e.javaException instanceof NSEE) }
try { empty.removeLast() } catch(e) { assertTrue(e.javaException instanceof NSEE) }

try { empty.element() } catch(e) { assertTrue(e.javaException instanceof NSEE) }
try { empty.getFirst() } catch(e) { assertTrue(e.javaException instanceof NSEE) }
try { empty.getLast() } catch(e) { assertTrue(e.javaException instanceof NSEE) }

assertTrue(empty.peek() === null)
assertTrue(empty.peekFirst() === null)
assertTrue(empty.peekLast() === null)

// arguments object to Java List
function func() {
   return Java.to(arguments, java.util.List);
}

var l = func("hello", "world");
assertTrue(l instanceof java.util.List);
assertTrue(l.size() == 2);
assertEquals(l.get(0), "hello");
assertEquals(l.get(1), "world");
