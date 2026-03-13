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

import org.openjdk.engine.javascript.V8Context;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

class V8ClassGenerator extends V8Generator {

    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

    private final static String CONSTRUCTOR_NAME = "CONSTRUCTOR";
    private final static String PROTOTYPE_NAME = "PROTOTYPE";

    private final Class<?> cls;
    private final String className;

    private final int WEIGHT_INT = 0;
    private final int WEIGHT_OTHER_PRIMITIVE = 1;
    private final int WEIGHT_DOUBLE = 2;
    private final int WEIGHT_FLOAT = 3;
    private final int WEIGHT_OTHER_OBJECT = 4;
    private final int WEIGHT_OBJECT_OBJECT = 5;
    private final int WEIGHT_OTHER_ARRAY = 6;
    private final int WEIGHT_OBJECT_ARRAY = 7;

    private static final ClassValue<String> FUNCTIONAL_INTERFACE_METHOD_NAMES = new ClassValue<String>() {
        @Override
        protected String computeValue(final Class<?> type) {
            if (V8.isAccessibleClass(type) && type.isInterface() && type.isAnnotationPresent(FunctionalInterface.class)) {
                // return the first abstract method
                for (final Method m : getMethods(type, false)) {
                    if (Modifier.isAbstract(m.getModifiers()) && ! isOverridableObjectMethod(m)) {
                        return m.getName();
                    }
                }
            }

            for (final Class<?> iface : type.getInterfaces()) {
                String name = FUNCTIONAL_INTERFACE_METHOD_NAMES.get(iface);
                if (name != null) {
                    return name;
                }
            }

            return null;
        }
    };

    V8ClassGenerator(Class<?> cls) {
        this(cls, false);
    }

    V8ClassGenerator(Class<?> cls, boolean debug) {
        super(debug);
        this.cls = cls;
        this.className = cls.getSimpleName();
    }

    private void id(long id) {
        hex((int)(id >>> 32));
        p(", ");
        hex((int)(id & 0xFFFFFFFF));
    }

    private static void typeSignature(StringBuilder sb, Class<?> type) {
        if (type == void.class) {
            sb.append('V');
        } else if (type == boolean.class) {
            sb.append('Z');
        } else if (type == byte.class) {
            sb.append('B');
        } else if (type == char.class) {
            sb.append('C');
        } else if (type == short.class) {
            sb.append('S');
        } else if (type == int.class) {
            sb.append('I');
        } else if (type == long.class) {
            sb.append('J');
        } else if (type == float.class) {
            sb.append('F');
        } else if (type == double.class) {
            sb.append('D');
        } else if (type.isArray()) {
            sb.append('[');
            typeSignature(sb, type.getComponentType());
        } else {
            sb.append('L');
            sb.append(type.getName().replace('.', '/'));
            sb.append(';');
        }
    }

    private static String fieldSignature(final Field field) {
        final StringBuilder sb = new StringBuilder();
        typeSignature(sb, field.getType());

        return sb.toString();
    }

    private static String executableSignature(final Executable executable) {
        final StringBuilder sb = new StringBuilder();
        sb.append('(');

        for (Class<?> type : executable.getParameterTypes()) {
            typeSignature(sb, type);
        }

        sb.append(')');

        if (executable instanceof Method) {
            Method method = (Method)executable;
            typeSignature(sb, method.getReturnType());
        } else {
            sb.append('V');
        }

        return sb.toString();
    }

    // is this an overridable java.lang.Object method?
    private static boolean isOverridableObjectMethod(final Method m) {
        switch (m.getName()) {
            case "equals":
                if (m.getReturnType() == boolean.class) {
                    final Class<?>[] params = m.getParameterTypes();
                    return params.length == 1 && params[0] == Object.class;
                }
                return false;
            case "hashCode":
                return m.getReturnType() == int.class && m.getParameterCount() == 0;
            case "toString":
                return m.getReturnType() == String.class && m.getParameterCount() == 0;
        }
        return false;
    }

    private void sourceURL(String src) {
        p("//# sourceURL="); p(src); p(".js"); nl();
    }

    private void constructor() {
        p(CONSTRUCTOR_NAME);
    }

    private void prototype(boolean isStatic) {
        p(isStatic ? CONSTRUCTOR_NAME : PROTOTYPE_NAME);
    }

    private String upperName(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private String lowerName(String name) {
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private void beginProperty(boolean isStatic, String name) {
        p("Object.defineProperty("); prototype(isStatic); p(", "); q(name); p(", {"); nl();
        begin();
    }

    private void beginSymbolProperty(boolean isStatic, String name) {
        p("Object.defineProperty("); prototype(isStatic); p(", "); p(name); p(", {"); nl();
        begin();
    }

    private void endProperty() {
        end();
        p("});"); nl();
    }

    private void applyArg(String name, int i) {
         p(name); p("(arguments["); p(i); p("])");
    }

    private void typeArgument(Class<?> type, int i, boolean isVarArgs) {

        if (isVarArgs) {
            p("toVarArgs(Array.prototype.slice.call(arguments, "); p(i); p("), "); q(type.getName()); p(")");
        } else if (type == long.class) {
            applyArg("toLong", i);
        } else if (type == String.class) {
            p("toString(arguments["); p(i); p("])");
        } else if (type.isPrimitive()) {
            // Other primitive types need no conversion if test was positive
            p("arguments["); p(i); p("]");
        } else {
            if (V8.isFunctionalInterface(type)) {
                p("toInterface(arguments["); p(i); p("], "); q(type.getName()); p(")");
            } else if (ListAdapter.canAdaptTo(type)) {
                p("toListObject(arguments["); p(i); p("], "); q(type.getName()); p(")");
            } else if (type.isArray()) {
                p("toJavaArray(arguments["); p(i); p("], "); q(type.getName()); p(")");
            } else if (type != Object.class) {
                p("toNonUndefinedObject(arguments["); p(i); p("], "); q(type.getName()); p(")");
            } else {
                applyArg("toObject", i);
            }
        }
    }

    private void typeCondition(Class<?> type, int i, boolean isVarArgs) {

        if (isVarArgs) {
            p("&& isVarArgs(Array.prototype.slice.call(arguments, "); p(i); p("), "); q(type.getName()) ;
            p(", "); q(type.getComponentType().getName()) ; p(")");
        } else if (type == boolean.class) {
            applyArg("&& isBoolean", i);
        } else if (type == byte.class || type == short.class || type == int.class) {
            applyArg("&& isInteger", i);
        } else if (type == char.class) {
            applyArg("&& isChar", i);
        } else if (type == long.class) {
            applyArg("&& isLong", i);
        } else if (type == float.class || type == double.class) {
            applyArg("&& isNumber", i);
        } else if (type == String.class) {
            applyArg("&& isString", i);
        } else if (type != Object.class) {
            assert !type.isPrimitive();
            p("&& (arguments["); p(i); p("] == null || isInstance(arguments["); p(i); p("], "); q(type.getName()); p(")");
            if (V8.isFunctionalInterface(type)) {
                p(" || typeof arguments["); p(i); p("] === 'function'");
            } else if (ListAdapter.canAdaptTo(type) || type.isArray()) {
                p(" || isArrayOrArguments(arguments["); p(i); p("])");
            }
            p(") ");
        }
    }

    private void parameterCountCondition(int parameterCount, boolean isVarArgs) {
        if (isVarArgs) {
            p("count >= "); p(parameterCount - 1);
        } else {
            p("count === "); p(parameterCount);
        }
        nl();
    }

    private void typeReturn(Class<?> type) {
        if (!type.isPrimitive() && type != String.class) {
            p("return JavaWrap(result);"); nl();
        } else {
            p("return result;"); nl();
        }
    }

    // Try ordering signatures from more specific to more generic, e.g. put (String) before (Object)
    private void sortByParameterWeight(Executable[] methods) {
        Arrays.sort(methods, (m1, m2) -> {
            Class<?>[] param1 = m1.getParameterTypes();
            Class<?>[] param2 = m2.getParameterTypes();
            if (param1.length != param2.length) {
                return param1.length - param2.length;
            }
            for (int i = 0; i < param1.length; i++) {
                if (param1[i] != param2[i]) {
                    int diff = typeWeight(param1[i]) - typeWeight(param2[i]);
                    if (diff != 0) {
                        return diff;
                    }
                    if (param1[i].isAssignableFrom(param2[i])) {
                        return 1;
                    } else if (param2[i].isAssignableFrom(param1[i])) {
                        return -1;
                    }
                }

            }
            return 0;
        });
    }

    private int typeWeight(final Class<?> cls) {
        if (cls == int.class) {
            return WEIGHT_INT;
        } else if (cls == float.class) {
            return WEIGHT_FLOAT;
        } else if (cls == double.class) {
            return WEIGHT_DOUBLE;
        } else if (cls.isPrimitive()) {
            return WEIGHT_OTHER_PRIMITIVE;
        } else if (cls.isArray()) {
            return cls.getComponentType() == Object.class ? WEIGHT_OBJECT_ARRAY : WEIGHT_OTHER_ARRAY;
        } else {
            return cls == Object.class ? WEIGHT_OBJECT_OBJECT : WEIGHT_OTHER_OBJECT;
        }
    }

    private static boolean isVarArgs(Class<?>[] parameterTypes) {
        return parameterTypes.length > 0
                && parameterTypes[parameterTypes.length - 1].isArray()
                && !parameterTypes[parameterTypes.length - 1].getComponentType().isPrimitive();
    }

    private void instanceConstructor(Constructor<?>[] constructors) {
        sortByParameterWeight(constructors);
        final int constructorsLength = constructors.length;
        p("if (!(this instanceof CONSTRUCTOR)) {"); nl();
        p("    throw new TypeError('Constructor must be called with new keyword');"); nl();
        p("}"); nl();
        p("const count = arguments.length;"); nl();

        for (int c = 0; c < constructorsLength; c++) {
            Constructor<?> constructor = constructors[c];
            if (shouldFilter(constructor)) {
                continue;
            }

            String signature = executableSignature(constructor);
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            int parameterCount = parameterTypes.length;
            boolean isVarArgs = isVarArgs(parameterTypes);

            p("if ("); nl();
            begin();
            parameterCountCondition(parameterCount, isVarArgs);

            for (int i = 0; i < parameterCount; i++) {
                typeCondition(parameterTypes[i], i, isVarArgs && i == parameterCount - 1);
            }
            end();
            p(") {"); nl();
            begin();
            long methodID = V8.getMethodID0(cls, "<init>", signature);

            p("return ");
            p("makeJavaWrapper(CONSTRUCTOR, JVM.newObject(this.constructor[javaObject], "); id(methodID); p(", "); q(signature); p(", [");
            if (parameterCount > 0) { nl(); }
            begin();
            for (int i = 0; i < parameterCount; i++) {
                Class<?> type = parameterTypes[i];
                typeArgument(parameterTypes[i], i, isVarArgs && i == parameterCount - 1);
                p(","); nl();
            }
            end();
            p("]));"); nl();
            end();
            p("}");
            p(" else ");
        }
        if (constructorsLength > 0) {
            p("{"); nl();
            begin();
            p("throw new TypeError('Cannot create Java object with the passed arguments: no matching constructor');"); nl();
            end();
            p("}"); nl();
        } else {
            p("throw new TypeError('Cannot create Java object: no matching constructor');"); nl();
        }
    }

    private void generateConstructor(Constructor<?>[] constructors) {
        p("function "); constructor(); p("() {"); nl();
        begin();
            if (cls.isInterface()) {
                interfaceConstructor();
            } else if (cls.isPrimitive()) {
                p("throw new TypeError('Can not extend final class "); p(className); p("');");
            } else if (cls.isArray()) {
                p("Object.defineProperty(this, 'length', { value: +arguments[0] | 0 });"); nl();
                p("Object.defineProperty(this, javaObject, { value: JVM.newArray(cls, this.length) });"); nl();
                p("return JavaArrayProxy(this);"); nl();
            } else {
                instanceConstructor(constructors);
            }
        end();
        p("}"); nl();
    }

    private void interfaceConstructor() {
        p("const count = arguments.length;"); nl();
        p("if ("); nl();
            begin();
            parameterCountCondition(1, true);
            p(" && arguments[0]"); nl();
            p(" && (typeof arguments[0] === 'object' || typeof arguments[0] === 'function')"); nl();
            end();
        p(") {"); nl();
            begin();
                p("return makeJavaWrapper(CONSTRUCTOR, JVM.getInterface(cls, arguments[0]))"); nl();
            end();
        p("} else {"); nl();
            begin();
                p("throw new TypeError('cannot create an interface instance "); p(className); p("');"); nl();
            end();
        p("}"); nl();
    }

    private void callMethod(Method method, boolean isStatic) {
        String name = method.getName();
        String signature = executableSignature(method);
        Class<?>[] parameterTypes = method.getParameterTypes();
        int parameterCount = parameterTypes.length;
        Class<?> returnType = method.getReturnType();
        boolean isVarArgs = isVarArgs(parameterTypes);

        p("if ("); nl();
        begin();
            parameterCountCondition(parameterCount, isVarArgs);

            for (int i = 0; i < parameterCount; i++) {
                typeCondition(parameterTypes[i], i, isVarArgs && i == parameterCount - 1);
            }
        end();
        p(") {"); nl();
        begin();
            long methodID = isStatic ? V8.getStaticMethodID0(cls, name, signature) : V8.getMethodID0(cls, name, signature);

            p("const result = JVM.call"); p(isStatic ? "Static" : ""); p("Method(this[javaObject], ");
            id(methodID); p(", "); q(signature); p(", [");
            if (parameterCount > 0) { nl(); }
            begin();
                for (int i = 0; i < parameterCount; i++) {
                    Class<?> type = parameterTypes[i];
                    typeArgument(parameterTypes[i], i, isVarArgs && i == parameterCount - 1);
                    p(","); nl();
                }
            end();
            p("]);"); nl();

            typeReturn(returnType);
        end();
        p("}"); nl();
    }

    private void generatePropertyInvoker(String property, List<Method> methodList, boolean isStatic) {
        Method[] methods = methodList.toArray(new Method[0]);
        sortByParameterWeight(methods);

        p(property); p(": function() {"); nl();
        begin();
            p("const count = arguments.length;"); nl();
            for (Method method : methods) {
                callMethod(method, isStatic);
            }
            p("throw new TypeError('Cannot invoke method with the passed arguments: no matching signature');"); nl();
            end();
        p("},"); nl();
    }

    private void generatePropertyDescriptor(String name, boolean isStatic, List<Method> methods,
                                             List<Method> getters, List<Method> setters, Field field) {
        p("({"); nl();
        if (methods != null && !methods.isEmpty()) {
            generatePropertyInvoker("value", methods, isStatic);
        } else {

            if (getters != null && !getters.isEmpty()) {
                generatePropertyInvoker("get", getters, isStatic);
            } else if (field != null) {
                generateFieldGetter(field, isStatic);
            }

            if (setters != null && !setters.isEmpty()) {
                generatePropertyInvoker("set", setters, isStatic);
            } else if (field != null) {
                generateFieldSetter(field, isStatic);
            }
        }
        p("})");
    }

    private void generateFieldGetter(Field field, boolean isStatic) {
        String name = field.getName();
        String signature = fieldSignature(field);
        Class<?> type = field.getType();
        String typeName = type.getName();
        String typeString = type.isPrimitive() ? upperName(typeName): "Object";
        long fieldID = isStatic ? V8.getStaticFieldID0(cls, name, signature) : V8.getFieldID0(cls, name, signature);

        p("get : function() {"); nl();
        begin();
            p("const result = JVM.get"); p(isStatic ? "Static" : ""); p(typeString); p("Field(this[javaObject], "); id(fieldID); p(");"); nl();
            typeReturn(type);
        end();
        p("},"); nl();
    }


    private void generateFieldSetter(Field field, boolean isStatic) {
        String name = field.getName();
        String signature = fieldSignature(field);
        int modifiers = field.getModifiers();
        boolean isFinal = (modifiers & Modifier.FINAL) != 0;
        Class<?> type = field.getType();
        String typeString = type.isPrimitive() ? upperName(type.getName()): "Object";
        long fieldID = isStatic ? V8.getStaticFieldID0(cls, name, signature) : V8.getFieldID0(cls, name, signature);


        if (!isFinal) {
            p("set : function(value) {"); nl();
            begin();
            p("JVM.set"); p(isStatic ? "Static" : ""); p(typeString); p("Field(this[javaObject], ");
            id(fieldID);  p(", "); typeArgument(type, 0, false); p(");"); nl();
            end();
            p("},"); nl();
        }
    }

    private String generateExplicitSignatureMethod(final String prop, final boolean isStatic) {

        final int lastChar = prop.length() - 1;
        if (prop.charAt(lastChar) != ')') {
            return null;
        }
        final int openBrace = prop.indexOf('(');
        if( openBrace == -1) {
            return null;
        }

        final String name = prop.substring(0, openBrace);
        final String params = prop.substring(openBrace + 1, lastChar);

        final List<String> paramList = new ArrayList<>();
        final StringTokenizer tok = new StringTokenizer(params, ", ");
        while (tok.hasMoreTokens()) {
            paramList.add(tok.nextToken());
        }
        String[] mparams = paramList.toArray(new String[0]);

        if (name.isEmpty()) {
            return generateExplicitSignatureConstructor(mparams);
        }

        methodloop: for (Method method : getMethods(cls, isStatic)) {
            if (!name.equals(method.getName()) || shouldFilter(method)) {
                continue;
            }

            Class<?>[] methodParams = method.getParameterTypes();
            if (methodParams.length != mparams.length) {
                continue;
            }
            for (int i = 0; i < methodParams.length; i++) {
                if (!typeNameMatches(mparams[i], methodParams[i])) {
                    continue methodloop;
                }
            }

            p("({");
            generatePropertyInvoker("value", Collections.singletonList(method), isStatic);
            p("})");
            break;
        }
        return code();
    }

    private String generateExplicitSignatureConstructor(String[] paramTypes) {
        ctorloop: for (Constructor ctor : cls.getConstructors()) {
            if (shouldFilter(ctor)) {
                continue;
            }

            Class<?>[] ctorParams = ctor.getParameterTypes();

            if (ctorParams.length != paramTypes.length) {
                continue;
            }
            for (int i = 0; i < ctorParams.length; i++) {
                if (!typeNameMatches(paramTypes[i], ctorParams[i])) {
                    continue ctorloop;
                }
            }

            p("({ value: ");
            generateConstructor(new Constructor[] { ctor });
            p("})");
            break;

        }
        return code();
    }

    private static boolean typeNameMatches(final String typeName, final Class<?> type) {
        return  typeName.equals(typeName.indexOf('.') == -1 ? type.getSimpleName() : type.getCanonicalName());
    }

    @Override
    String generateProperty(final String prop, final boolean isStatic) {
        if (prop.indexOf('(') > -1) {
            sourceURL(cls.getName() + "-" + prop);
            return generateExplicitSignatureMethod(prop, isStatic);
        }
        List<Method> methods = new ArrayList<>();
        List<Method> getters = new ArrayList<>();
        List<Method> setters = new ArrayList<>();
        String upperName = upperName(prop);
        String setterName = "set" + upperName;
        String getterName = "get" + upperName;
        Set<MethodSignature> signatures = isStatic ? null : new HashSet<>();

        for (Method method : getMethods(cls, isStatic)) {
            if (shouldFilter(method)) {
                continue;
            }

            if (!isStatic) {
                MethodSignature signature = new MethodSignature(method);
                if (signatures.contains(signature)) {
                    continue;
                }
                signatures.add(signature);
            }

            final String methodName = method.getName();
            if (methodName.equals(prop)) {
                methods.add(method);
            } else if (methodName.equals(setterName) && method.getParameterCount() == 1) {
                setters.add(method);
            } else if (methodName.equals(getterName) && method.getParameterCount() == 0) {
                getters.add(method);
            }
        }

        Field field = null;
        for (Field f : cls.getFields()) {
            if (shouldFilter(f)) {
                continue;
            }
            if (prop.equals(f.getName())) {
                field = f;
                break;
            }
        }

        if (!methods.isEmpty() || !getters.isEmpty() || !setters.isEmpty() || field != null) {
            sourceURL(cls.getName() + "-" + prop);
            generatePropertyDescriptor(prop, isStatic, methods, getters, setters, field);
        }

        return code();
    }

    @Override
    String generateConstructor() {
        Set<String> instanceProperties = new HashSet<>();
        Set<String> staticProperties = new HashSet<>();

        for (Method method : getMethods(cls, true)) {
            if (shouldFilter(method)) {
                continue;
            }

            String name = method.getName();
            Set<String> set = staticProperties;
            set.add(name);

            if (isGetterOrSetter(method)) {
                set.add(lowerName(name.substring(3)));
            }
        }

        for (Method method : getMethods(cls, false)) {
            if (shouldFilter(method)) {
                continue;
            }

            String name = method.getName();
            Set<String> set = instanceProperties;
            set.add(name);

            if (isGetterOrSetter(method)) {
                set.add(lowerName(name.substring(3)));
            }
        }

        Field[] fields = cls.getFields();
        for (Field field : fields) {
            if (shouldFilter(field)) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            Set<String> set = isStatic ? staticProperties : instanceProperties;
            set.add(field.getName());
        }

        p("\"use strict\";");
        nl();
        sourceURL(cls.getName());
        p("(function() {");
        nl();
        begin();
        p("const "); constructor(); p(" = ");
        generateConstructor(cls.getConstructors());

        p("const ");
        prototype(false);
        p(" = Object.create(");
        getPrototypeForClass(cls, instanceProperties);
        p(");");
        nl();

        beginProperty(true, "prototype");
        p("value: ");
        prototype(false); nl();
        endProperty();
        nl();

        beginProperty(false, "constructor");
        p("value: ");
        prototype(true); nl();
        endProperty();
        nl();

        beginSymbolProperty(false, "Symbol.toStringTag");
        p("value: '");
        p(cls.getName());
        p("'"); nl();
        endProperty();
        nl();

        beginSymbolProperty(true, "Symbol.toStringTag");
        p("value: '");
        if (cls.isInterface()) {
            p("interface:");
        } else {
            p("class:");
        }
        p(cls.getName());
        p("'"); nl();
        endProperty();
        nl();

        beginSymbolProperty(true, "staticProperties");
        p("value: ");
        printPropertyMap(staticProperties);
        nl();
        endProperty();
        nl();

        if (java.util.List.class.isAssignableFrom(cls)) {
            beginSymbolProperty(true, "isList");
            p("value: true"); nl();
            endProperty();
            nl();
        }

        if (java.util.Map.class.isAssignableFrom(cls)) {
            beginSymbolProperty(false, "Symbol.iterator");
            p("value: javaMapIterator"); nl();
            endProperty();
            nl();
        }

        if (java.lang.Iterable.class.isAssignableFrom(cls)) {
            beginSymbolProperty(false, "Symbol.iterator");
            p("value: javaIterableIterator"); nl();
            endProperty();
            nl();
        }

        String functionalInterfaceMethodName = FUNCTIONAL_INTERFACE_METHOD_NAMES.get(cls);
        if (functionalInterfaceMethodName != null) {
            beginSymbolProperty(true, "functionalInterfaceMethod");
            p("value: '");
            p(functionalInterfaceMethodName); p("'"); nl();
            endProperty();
            nl();
        }

        // instanceof support
        p("Object.defineProperty(CONSTRUCTOR, Symbol.hasInstance, {"); nl();
        begin();
            p("value : javaHasInstance"); nl();
        end();
        p("});"); nl();

        nl();
        p("return ");
        constructor();
        p(";"); nl();
        end();
        p("})()"); nl();

        return code();
    }

    private boolean isGetterOrSetter(Method method) {
        final String name = method.getName();

        if (name.length() > 3) {
            return (name.startsWith("get") && method.getParameterCount() == 0) ||
                    (name.startsWith("set") && method.getParameterCount() == 1);
        }

        return false;
    }

    private void getPrototypeForClass(Class<?> cls, Set<String> properties) {
        if (cls == Object.class || cls.isInterface()) {
            p("JavaBottomProxy(");
            printPropertyMap(properties);
            p(')');
        } else if (cls.isArray()) {
            // Set proto to java array prototype
            p("JavaArrayProtoProxy");
        } else if (cls.isPrimitive()) {
            p("null");
        } else {
            // Set proto to proto of Java superclass
            p("JavaProtoProxy('");
            p(cls.getSuperclass().getName());
            p("', cls, ");
            printPropertyMap(properties);
            p(')');
        }
    }

    private void printPropertyMap(Set<String> properties) {
        p("{");
        for (String s : properties) {
            p(s);
            p(": 1, ");
        }
        p("}");
    }

    @SuppressWarnings("unchecked")
    private boolean shouldFilter(Constructor ctr) {
        try {
            PUBLIC_LOOKUP.unreflectConstructor(ctr);
        } catch (IllegalAccessException iae) {
            return true;
        }
        return V8.isRestrictedClass(ctr.getDeclaringClass());
    }

    private boolean isAllowCodeGenerationFromStrings(Method method) {
        return method.getName().equals("allowCodeGenerationFromStrings")
            && (V8Context.class.isAssignableFrom(method.getDeclaringClass()));
    }

    private boolean shouldFilter(Method method) {
        try {
            PUBLIC_LOOKUP.unreflect(method);
        } catch (IllegalAccessException iae) {
            return true;
        }
        return isAllowCodeGenerationFromStrings(method);
    }

    private boolean shouldFilter(Field field) {
        try {
            PUBLIC_LOOKUP.unreflectGetter(field);
        } catch (IllegalAccessException iae) {
            return true;
        }
        return V8.isRestrictedClass(field.getDeclaringClass());
    }

    static private class MethodSignature {
        final String name;
        final Class<?>[] types;

        MethodSignature(Executable executable) {
            this.name = executable.getName();
            this.types = executable.getParameterTypes();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (! (o instanceof MethodSignature)) {
                return false;
            }

            MethodSignature other = (MethodSignature) o;
            return name.equals(other.name) && Arrays.equals(types, other.types);
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ Arrays.hashCode(types);
        }
    }


    private static Collection<Method> getMethods(Class<?> cls, boolean isStatic) {
        return new AccessibleMembersLookup(cls, !isStatic).getMethods();
    }
}
