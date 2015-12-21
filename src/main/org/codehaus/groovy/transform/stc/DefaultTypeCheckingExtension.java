/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.codehaus.groovy.transform.stc;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ReturnStatement;

import java.lang.reflect.Type;
import java.util.*;

/**
 * The default type checking handler is used by the standard type checker and doesn't handle
 * any of the type checking errors by default. This just means that whenever a type checking
 * error is detected, there's no additional information available to the type checker that
 * could help it.
 *
 * The default handler is also capable of handling a collection of delegate handlers. If a list
 * of delegates is set, then the type checker will try all the delegates until one is capable
 * of handling an error.
 *
 * @author Cedric Champeau
 * @since 2.1.0
 */
public class DefaultTypeCheckingExtension extends TypeCheckingExtension {
    protected final List<TypeCheckingExtension> handlers = new LinkedList<TypeCheckingExtension>();
    protected final Deque<List<TypeCheckingExtension>> localHandlersStack = new ArrayDeque<List<TypeCheckingExtension>>();

    public DefaultTypeCheckingExtension(final StaticTypeCheckingVisitor typeCheckingVisitor) {
        super(typeCheckingVisitor);
    }

    public void addHandler(TypeCheckingExtension handler) {
        handlers.add(handler);
    }

    public void removeHandler(TypeCheckingExtension handler) {
        handlers.remove(handler);
    }

    public void pushLocalHandlers(List<TypeCheckingExtension> localHandlers) {
        localHandlersStack.push(localHandlers);
    }

    public void popLocalHandlers() {
        localHandlersStack.pop();
    }

    private class HandlerIterator implements Iterator<TypeCheckingExtension> {
        Iterator<TypeCheckingExtension> defaultHandlers;
        Iterator<TypeCheckingExtension> localHandlers;
        boolean exhaustedDefaults;

        HandlerIterator(){
            this.defaultHandlers = handlers.iterator();
            if (!localHandlersStack.isEmpty()) {
                this.localHandlers = localHandlersStack.peek().iterator();
            } else {
                this.localHandlers = Collections.<TypeCheckingExtension>emptyIterator();
            }
        }

        @Override
        public boolean hasNext() {
            return exhaustedDefaults ? localHandlers.hasNext() : defaultHandlers.hasNext();
        }

        @Override
        public TypeCheckingExtension next() {
            if (exhaustedDefaults) {
                return localHandlers.next();
            } else {
                TypeCheckingExtension tce = defaultHandlers.next();
                if (!defaultHandlers.hasNext()) {
                    exhaustedDefaults = true;
                }
                return tce;
            }
        }
    }

    public boolean handleUnresolvedVariableExpression(VariableExpression vexp) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.handleUnresolvedVariableExpression(vexp)) return true;
        }
        return false;
    }

    public boolean handleUnresolvedProperty(final PropertyExpression pexp) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.handleUnresolvedProperty(pexp)) return true;
        }
        return false;
    }

    public boolean handleUnresolvedAttribute(final AttributeExpression aexp) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.handleUnresolvedAttribute(aexp)) return true;
        }
        return false;
    }

    @Override
    public boolean handleIncompatibleAssignment(final ClassNode lhsType, final ClassNode rhsType, final Expression assignmentExpression) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.handleIncompatibleAssignment(lhsType, rhsType, assignmentExpression)) return true;
        }
        return false;
    }

    @Override
    public boolean handleIncompatibleReturnType(ReturnStatement returnStatement, ClassNode inferredReturnType) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.handleIncompatibleReturnType(returnStatement, inferredReturnType)) return true;
        }
        return false;
    }

    @Override
    public List<MethodNode> handleAmbiguousMethods(final List<MethodNode> nodes, final Expression origin) {
        List<MethodNode> result = nodes;
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (result.size()>1 && it.hasNext()) {
            result = it.next().handleAmbiguousMethods(result, origin);
        }
        return result;
    }

    public List<MethodNode> handleMissingMethod(final ClassNode receiver, final String name, final ArgumentListExpression argumentList, final ClassNode[] argumentTypes, final MethodCall call) {
        List<MethodNode> result = new LinkedList<MethodNode>();
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            List<MethodNode> handlerResult = handler.handleMissingMethod(receiver, name, argumentList, argumentTypes, call);
            for (MethodNode mn : handlerResult) {
                if (mn.getDeclaringClass()==null) {
                    mn.setDeclaringClass(ClassHelper.OBJECT_TYPE);
                }
            }
            result.addAll(handlerResult);
        }
        return result;
    }

    @Override
    public void afterVisitMethod(final MethodNode node) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            handler.afterVisitMethod(node);
        }
    }

    @Override
    public boolean beforeVisitMethod(final MethodNode node) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.beforeVisitMethod(node)) return true;
        }
        return false;
    }

    @Override
    public void afterVisitClass(final ClassNode node) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            handler.afterVisitClass(node);
        }
    }

    @Override
    public boolean beforeVisitClass(final ClassNode node) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.beforeVisitClass(node)) return true;
        }
        return false;
    }

    @Override
    public void afterMethodCall(final MethodCall call) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            handler.afterMethodCall(call);
        }

    }

    @Override
    public boolean beforeMethodCall(final MethodCall call) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            if (handler.beforeMethodCall(call)) return true;
        }
        return false;
    }

    @Override
    public void onMethodSelection(final Expression expression, final MethodNode target) {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            handler.onMethodSelection(expression, target);
        }
    }

    @Override
    public void setup() {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        // note that new extensions can be added during the "setup" phase
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            handler.setup();
        }
    }

    @Override
    public void finish() {
        Iterator<TypeCheckingExtension> it = new HandlerIterator();
        while (it.hasNext()) {
            TypeCheckingExtension handler = it.next();
            handler.finish();
        }
    }
}