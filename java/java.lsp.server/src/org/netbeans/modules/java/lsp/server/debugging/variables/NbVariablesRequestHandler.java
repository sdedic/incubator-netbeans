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
package org.netbeans.modules.java.lsp.server.debugging.variables;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;

import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.modules.java.lsp.server.debugging.DebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.NbScope;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbDebugSession;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.spi.viewmodel.Models;
import org.netbeans.spi.viewmodel.UnknownTypeException;

/**
 *
 * @author martin
 */
public final class NbVariablesRequestHandler {

    private static final String LOCALS_VIEW_NAME = "LocalsView";
    private static final String LOCALS_VALUE_COLUMN_ID = "LocalsValue";
    private static final String LOCALS_TO_STRING_COLUMN_ID = "LocalsToString";
    private static final String LOCALS_TYPE_COLUMN_ID = "LocalsType";

    private final ViewModel.Provider localsModelProvider;

    public NbVariablesRequestHandler() {
        this.localsModelProvider = new ViewModel.Provider(LOCALS_VIEW_NAME);
    }

    public CompletableFuture<VariablesResponse> variables(VariablesArguments arguments, DebugAdapterContext context) {
        CompletableFuture<VariablesResponse> future = new CompletableFuture<>();
        // IVariableFormatter variableFormatter = context.getVariableFormatter();
        // boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;

        VariablesResponse response = new VariablesResponse();
        Object container = context.getRecyclableIdPool().getObjectById(arguments.getVariablesReference());
        // vscode will always send variables request to a staled scope, return the empty list is ok since the next
        // variable request will contain the right variablesReference.
        if (container == null) {
            response.setVariables(new Variable[0]);
        } else {
            JPDADebugger debugger = ((NbDebugSession) context.getDebugSession()).getDebugger();
            Models.CompoundModel localsModel = localsModelProvider.getModel(debugger.getSession());
            if (container instanceof NbScope) {
                container = localsModel.getRoot();
            }
            List<Variable> list = new ArrayList<>();
            try {
                Object[] children;
                int count = arguments.getCount() != null ? arguments.getCount() : 0;
                if (count > 0) {
                    int start = arguments.getStart() != null ? arguments.getStart() : 0;
                    children = localsModel.getChildren(container, start, start + count);
                } else {
                    children = localsModel.getChildren(container, 0, Integer.MAX_VALUE);
                }
                for (Object child : children) {
                    String name = localsModel.getDisplayName(child);
                    String value = String.valueOf(localsModel.getValueAt(child, LOCALS_TO_STRING_COLUMN_ID));
                    String type = String.valueOf(localsModel.getValueAt(child, LOCALS_TYPE_COLUMN_ID));
                    int id = context.getRecyclableIdPool().addObject(1, child);
                    Variable variable = new Variable();
                    variable.setName(name);
                    variable.setValue(value);
                    variable.setType(type);
                    variable.setVariablesReference(id);
                    list.add(variable);
                }
            } catch (UnknownTypeException e) {
                future.completeExceptionally(AdapterUtils.createResponseErrorException(e.getMessage(), ErrorCode.GET_VARIABLE_FAILURE));
                return future;
            }
            response.setVariables(list.toArray(new Variable[list.size()]));
        }
        future.complete(response);
        return future;
    }

    public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args, DebugAdapterContext context) {
        CompletableFuture<SetVariableResponse> future = new CompletableFuture<>();
        if (StringUtils.isBlank(args.getValue())) {
            future.completeExceptionally(AdapterUtils.createResponseErrorException(
                "SetVariablesRequest: property 'value' is missing, null, or empty",
                ErrorCode.ARGUMENT_MISSING));
            return future;
        } else if (args.getVariablesReference() == -1) {
            future.completeExceptionally(AdapterUtils.createResponseErrorException(
                "SetVariablesRequest: property 'variablesReference' is missing, null, or empty",
                ErrorCode.ARGUMENT_MISSING));
            return future;
        } else if (StringUtils.isBlank(args.getName())) {
            future.completeExceptionally(AdapterUtils.createResponseErrorException(
                "SetVariablesRequest: property 'name' is missing, null, or empty",
                ErrorCode.ARGUMENT_MISSING));
            return future;
        }

        Object container = context.getRecyclableIdPool().getObjectById(args.getVariablesReference());
        // container is null means the stack frame is continued by user manually.
        if (container == null) {
            future.completeExceptionally(AdapterUtils.createResponseErrorException(
                "Failed to set variable. Reason: Cannot set value because the thread is resumed.",
                ErrorCode.SET_VARIABLE_FAILURE));
            return future;
        }

        JPDADebugger debugger = ((NbDebugSession) context.getDebugSession()).getDebugger();
        Models.CompoundModel localsModel = localsModelProvider.getModel(debugger.getSession());

        if (container instanceof NbScope) {
            container = localsModel.getRoot();
        }
        String varName = args.getName();
        // We need to search for varName in the container:
        try {
            Object[] children = localsModel.getChildren(container, 0, Integer.MAX_VALUE);
            Object varChild = null;
            for (Object child : children) {
                String name = localsModel.getDisplayName(child);
                if (varName.equals(name)) {
                    varChild = child;
                    break;
                }
            }
            if (varChild != null) {
                localsModel.setValueAt(varChild, LOCALS_VALUE_COLUMN_ID, args.getValue());
                String value = String.valueOf(localsModel.getValueAt(varChild, LOCALS_TO_STRING_COLUMN_ID));
                String type = String.valueOf(localsModel.getValueAt(varChild, LOCALS_TYPE_COLUMN_ID));
                int id = context.getRecyclableIdPool().addObject(1, varChild);
                SetVariableResponse response = new SetVariableResponse();
                response.setType(type);
                response.setValue(value);
                response.setVariablesReference(id);
                response.setIndexedVariables(0);
                future.complete(response);
            } else {
                future.completeExceptionally(AdapterUtils.createResponseErrorException(
                    String.format("SetVariableRequest: Variable %s cannot be found.", varName),
                    ErrorCode.SET_VARIABLE_FAILURE));
            }
        } catch (UnknownTypeException e) {
            future.completeExceptionally(AdapterUtils.createResponseErrorException(e.getMessage(), ErrorCode.SET_VARIABLE_FAILURE));
        }
        return future;
    }
}
