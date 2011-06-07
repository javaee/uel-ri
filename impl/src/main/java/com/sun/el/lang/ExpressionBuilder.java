/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.el.lang;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import com.sun.el.MethodExpressionImpl;
import com.sun.el.MethodExpressionLiteral;
import com.sun.el.ValueExpressionImpl;
import com.sun.el.parser.AstCompositeExpression;
import com.sun.el.parser.AstDeferredExpression;
import com.sun.el.parser.AstDynamicExpression;
import com.sun.el.parser.AstFunction;
import com.sun.el.parser.AstIdentifier;
import com.sun.el.parser.AstLiteralExpression;
import com.sun.el.parser.AstValue;
import com.sun.el.parser.ELParser;
import com.sun.el.parser.Node;
import com.sun.el.parser.NodeVisitor;
import com.sun.el.parser.ParseException;
import com.sun.el.util.MessageFactory;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public final class ExpressionBuilder implements NodeVisitor {

    static private class StringSoftReference extends SoftReference<String> {

        StringSoftReference(String referent, ReferenceQueue<String> refQ) {
            super(referent, refQ);
        }

        @Override
        public boolean equals(Object obj) {
            String thisString = this.get();
            if (thisString == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            SoftReference<String> sr = (SoftReference<String>) obj;
            return thisString.equals(sr.get());
        }

        @Override
        public int hashCode() {
            String thisString = this.get();
            return (thisString == null)? 0: thisString.hashCode();
        }
    }

    static private class SoftConcurrentHashMap extends
                ConcurrentHashMap<String, Node> {

        private static final int CACHE_INIT_SIZE = 256;
        private ConcurrentHashMap<StringSoftReference,SoftReference<Node>> map =                 new ConcurrentHashMap<StringSoftReference, SoftReference<Node>>(CACHE_INIT_SIZE);
        private ReferenceQueue<String> refQ = new ReferenceQueue<String>();

        // Remove map entries that have been placed on the queue by GC.
        private void cleanup() {
            Reference<? extends String> keyRef;
            while ((keyRef = refQ.poll()) != null) {
                map.remove(keyRef);
            }
        }

        @Override
        public Node put(String key, Node value) {
            cleanup();
            SoftReference<Node> valueRef = new SoftReference<Node>(value);
            StringSoftReference keyRef = new StringSoftReference(key, refQ);
            SoftReference<Node> prev = map.put(keyRef, valueRef);
            return prev == null? null: prev.get();
        }

        @Override
        public Node putIfAbsent(String key, Node value) {
            cleanup();
            SoftReference<Node> valueRef = new SoftReference<Node>(value);
            StringSoftReference keyRef = new StringSoftReference(key, refQ);
            SoftReference<Node> prev = map.putIfAbsent(keyRef, valueRef);
            return prev == null? null: prev.get();
        }
        @Override
        public Node get(Object key) {
            if (!(key instanceof String)) {
                return null;
            }
            StringSoftReference keyRef =
                        new StringSoftReference((String)key, refQ);
            SoftReference<Node> valueRef = map.get(keyRef);
            if (valueRef == null) {
                return null;
            }
            if (valueRef.get() == null) {
                // value has been garbage collected, remove entry in map
                map.remove(keyRef);
                return null;
            }
            return valueRef.get();
        }
    }

    private static final SoftConcurrentHashMap cache =
                new SoftConcurrentHashMap();

    private FunctionMapper fnMapper;

    private VariableMapper varMapper;

    private String expression;

    /**
     * 
     */
    public ExpressionBuilder(String expression, ELContext ctx)
            throws ELException {
        this.expression = expression;

        FunctionMapper ctxFn = ctx.getFunctionMapper();
        VariableMapper ctxVar = ctx.getVariableMapper();

        if (ctxFn != null) {
            this.fnMapper = new FunctionMapperFactory(ctxFn);
        }
        if (ctxVar != null) {
            this.varMapper = new VariableMapperFactory(ctxVar);
        }
    }

    public final static Node createNode(String expr) throws ELException {
        Node n = createNodeInternal(expr);
        return n;
    }

    private final static Node createNodeInternal(String expr)
            throws ELException {
        if (expr == null) {
            throw new ELException(MessageFactory.get("error.null"));
        }

        Node n = cache.get(expr);
        if (n == null) {
            try {
                n = (new ELParser(
                        new com.sun.el.parser.ELParserTokenManager(
                            new com.sun.el.parser.SimpleCharStream(
                                new StringReader(expr),1, 1, expr.length()+1))))
                        .CompositeExpression();

                // validate composite expression
                if (n instanceof AstCompositeExpression) {
                    int numChildren = n.jjtGetNumChildren();
                    if (numChildren == 1) {
                        n = n.jjtGetChild(0);
                    } else {
                        Class type = null;
                        Node child = null;
                        for (int i = 0; i < numChildren; i++) {
                            child = n.jjtGetChild(i);
                            if (child instanceof AstLiteralExpression)
                                continue;
                            if (type == null)
                                type = child.getClass();
                            else {
                                if (!type.equals(child.getClass())) {
                                    throw new ELException(MessageFactory.get(
                                            "error.mixed", expr));
                                }
                            }
                        }
                    }
                }
                if (n instanceof AstDeferredExpression
                        || n instanceof AstDynamicExpression) {
                    n = n.jjtGetChild(0);
                }
                cache.putIfAbsent(expr, n);
            } catch (ParseException pe) {
                throw new ELException("Error Parsing: " + expr, pe);
            }
        }
        return n;
    }

    private void prepare(Node node) throws ELException {
        node.accept(this);
        if (this.fnMapper instanceof FunctionMapperFactory) {
            this.fnMapper = ((FunctionMapperFactory) this.fnMapper).create();
        }
        if (this.varMapper instanceof VariableMapperFactory) {
            this.varMapper = ((VariableMapperFactory) this.varMapper).create();
        }
    }

    private Node build() throws ELException {
        Node n = createNodeInternal(this.expression);
        this.prepare(n);
        if (n instanceof AstDeferredExpression
                || n instanceof AstDynamicExpression) {
            n = n.jjtGetChild(0);
        }
        return n;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.el.parser.NodeVisitor#visit(com.sun.el.parser.Node)
     */
    public void visit(Node node) throws ELException {
        if (node instanceof AstFunction) {

            AstFunction funcNode = (AstFunction) node;

            if (this.fnMapper == null) {
                throw new ELException(MessageFactory.get("error.fnMapper.null"));
            }
            Method m = fnMapper.resolveFunction(funcNode.getPrefix(), funcNode
                    .getLocalName());
            if (m == null) {
                throw new ELException(MessageFactory.get(
                        "error.fnMapper.method", funcNode.getOutputName()));
            }
            int pcnt = m.getParameterTypes().length;
            if (node.jjtGetNumChildren() != pcnt) {
                throw new ELException(MessageFactory.get(
                        "error.fnMapper.paramcount", funcNode.getOutputName(),
                        "" + pcnt, "" + node.jjtGetNumChildren()));
            }
        } else if (node instanceof AstIdentifier && this.varMapper != null) {
            String variable = ((AstIdentifier) node).getImage();

            // simply capture it
            this.varMapper.resolveVariable(variable);
        }
    }

    public ValueExpression createValueExpression(Class expectedType)
            throws ELException {
        Node n = this.build();
        return new ValueExpressionImpl(this.expression, n, this.fnMapper,
                this.varMapper, expectedType);
    }

    public MethodExpression createMethodExpression(Class expectedReturnType,
            Class[] expectedParamTypes) throws ELException {
        Node n = this.build();
        if (n instanceof AstValue || n instanceof AstIdentifier) {
            return new MethodExpressionImpl(expression, n,
                    this.fnMapper, this.varMapper, expectedReturnType,
                    expectedParamTypes);
        } else if (n instanceof AstLiteralExpression) {
            return new MethodExpressionLiteral(expression, expectedReturnType,
                    expectedParamTypes);
        } else {
            throw new ELException("Not a Valid Method Expression: "
                    + expression);
        }
    }
}
