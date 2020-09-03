/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.java.lsp.server.debugging;

import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Responses;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.debugger.jpda.InvalidExpressionException;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.api.debugger.jpda.ObjectVariable;
import org.netbeans.api.debugger.jpda.Variable;
import org.netbeans.modules.debugger.jpda.truffle.vars.TruffleVariable;

/**
 *
 * @author martin
 */
final class NbEvaluateRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Requests.Command> getTargetCommands() {
        return Arrays.asList(Requests.Command.EVALUATE);
    }

    @Override
    public CompletableFuture<Messages.Response> handle(Requests.Command command, Requests.Arguments arguments, Messages.Response response, IDebugAdapterContext context) {
        Requests.EvaluateArguments evalArguments = (Requests.EvaluateArguments) arguments;
        Map<String, Object> options = context.getVariableFormatter().getDefaultOptions();
        VariableUtils.applyFormatterOptions(options, evalArguments.format != null && evalArguments.format.hex);
        String expression = evalArguments.expression;

        if (StringUtils.isBlank(expression)) {
            throw new CompletionException(AdapterUtils.createUserErrorDebugException(
                "Failed to evaluate. Reason: Empty expression cannot be evaluated.",
                ErrorCode.EVALUATION_COMPILE_ERROR));
        }
        JPDADebugger dbg = Debugger.findJPDADebugger(context.getDebugSession());
        JPDAThread currentThread = dbg.getCurrentThread();
        if (currentThread == null) {
            throw new CompletionException(AdapterUtils.createUserErrorDebugException(
                "Failed to evaluate. Reason: No current thread.",
                ErrorCode.EVALUATION_COMPILE_ERROR));
        }
        return CompletableFuture.supplyAsync(() -> {
            Variable variable;
            try {
                variable = dbg.evaluate(expression);
            } catch (InvalidExpressionException ex) {
            throw new CompletionException(AdapterUtils.createUserErrorDebugException(
                "Failed to evaluate. Reason: " + ex.getLocalizedMessage(),
                ErrorCode.EVALUATION_COMPILE_ERROR));
            }
            Responses.EvaluateResponseBody responseBody;
            TruffleVariable truffleVariable = TruffleVariable.get(variable);
            if (truffleVariable != null) {
                int referenceId = context.getRecyclableIdPool().addObject(currentThread.getID(), truffleVariable);
                responseBody = new Responses.EvaluateResponseBody(truffleVariable.getDisplayValue(),
                        referenceId, truffleVariable.getType(), truffleVariable.isLeaf() ? 0 : Integer.MAX_VALUE);
            } else {
                if (variable instanceof ObjectVariable) {
                    int referenceId = context.getRecyclableIdPool().addObject(currentThread.getID(), variable);
                    int indexedVariables = ((ObjectVariable) variable).getFieldsCount();
                    String toString;
                    try {
                        toString = ((ObjectVariable) variable).getToStringValue();
                    } catch (InvalidExpressionException ex) {
                        toString = variable.getValue();
                    }
                    responseBody = new Responses.EvaluateResponseBody(toString,
                            referenceId, variable.getType(), Math.max(indexedVariables, 0));
                } else {
                    responseBody = new Responses.EvaluateResponseBody(variable.getValue(),
                            0, variable.getType(), 0);
                }
            }
            response.body = responseBody;
            return response;
        });
    }
}
