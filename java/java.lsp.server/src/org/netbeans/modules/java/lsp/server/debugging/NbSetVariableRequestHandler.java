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
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Responses;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbDebugSession;
import org.netbeans.spi.viewmodel.Models;
import org.netbeans.spi.viewmodel.UnknownTypeException;

final class NbSetVariableRequestHandler implements IDebugRequestHandler {

    private static final String LOCALS_VIEW_NAME = "LocalsView";
    private static final String LOCALS_VALUE_COLUMN_ID = "LocalsValue";
    private static final String LOCALS_TYPE_COLUMN_ID = "LocalsType";
    private static final String LOCALS_TO_STRING_COLUMN_ID = "LocalsToString";

    private final ViewModel.Provider localsModelProvider;

    NbSetVariableRequestHandler() {
        this.localsModelProvider = new ViewModel.Provider(LOCALS_VIEW_NAME);
    }

    @Override
    public List<Requests.Command> getTargetCommands() {
        return Collections.singletonList(Requests.Command.SETVARIABLE);
    }

    @Override
    public CompletableFuture<Messages.Response> handle(Requests.Command command, Requests.Arguments arguments, Messages.Response response, IDebugAdapterContext context) {
        Requests.SetVariableArguments setVarArguments = (Requests.SetVariableArguments) arguments;
        if (setVarArguments.value == null) {
            // Just exit out of editing if we're given an empty expression.
            return CompletableFuture.completedFuture(response);
        } else if (setVarArguments.variablesReference == -1) {
            throw AdapterUtils.createCompletionException(
                "SetVariablesRequest: property 'variablesReference' is missing, null, or empty",
                ErrorCode.ARGUMENT_MISSING);
        } else if (StringUtils.isBlank(setVarArguments.name)) {
            throw AdapterUtils.createCompletionException(
                "SetVariablesRequest: property 'name' is missing, null, or empty",
                ErrorCode.ARGUMENT_MISSING);
        }

        Object container = context.getRecyclableIdPool().getObjectById(setVarArguments.variablesReference);
        // container is null means the stack frame is continued by user manually.
        if (container == null) {
            throw AdapterUtils.createCompletionException(
                "Failed to set variable. Reason: Cannot set value because the thread is resumed.",
                ErrorCode.SET_VARIABLE_FAILURE);
        }

        JPDADebugger debugger = ((NbDebugSession) context.getDebugSession()).getDebugger();
        Models.CompoundModel localsModel = localsModelProvider.getModel(debugger.getSession());

        if (container instanceof NbScope) {
            container = localsModel.getRoot();
        }
        String varName = setVarArguments.name;
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
                localsModel.setValueAt(varChild, LOCALS_VALUE_COLUMN_ID, setVarArguments.value);
                String value = String.valueOf(localsModel.getValueAt(varChild, LOCALS_TO_STRING_COLUMN_ID));
                String type = String.valueOf(localsModel.getValueAt(varChild, LOCALS_TYPE_COLUMN_ID));
                int id = context.getRecyclableIdPool().addObject(1L, varChild);
                response.body = new Responses.SetVariablesResponseBody(type, value, id, 0);
            } else {
                throw AdapterUtils.createCompletionException(
                    String.format("SetVariableRequest: Variable %s cannot be found.", varName),
                    ErrorCode.SET_VARIABLE_FAILURE);
            }
        } catch (UnknownTypeException e) {
            throw AdapterUtils.createCompletionException(e.getLocalizedMessage(), ErrorCode.SET_VARIABLE_FAILURE, e);
        }
        return CompletableFuture.completedFuture(response);
    }

}
