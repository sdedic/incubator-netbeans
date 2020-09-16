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
package org.netbeans.modules.java.lsp.server.debugging.requests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;

import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.NbScope;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbDebugSession;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages.Response;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Arguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Command;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.VariablesArguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Responses;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Types;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.spi.viewmodel.Models;
import org.netbeans.spi.viewmodel.UnknownTypeException;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;

/**
 *
 * @author martin
 */
final class NbVariablesRequestHandler implements DebuggerRequestHandler {

    private static final String LOCALS_VIEW_NAME = "LocalsView";
    private static final String LOCALS_TO_STRING_COLUMN_ID = "LocalsToString";
    private static final String LOCALS_TYPE_COLUMN_ID = "LocalsType";

    private final ViewModel.Provider localsModelProvider;

    NbVariablesRequestHandler() {
        this.localsModelProvider = new ViewModel.Provider(LOCALS_VIEW_NAME);
    }

    @Override
    public List<Command> getTargetCommands() {
        return Collections.singletonList(Command.VARIABLES);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        //IVariableFormatter variableFormatter = context.getVariableFormatter();
        VariablesArguments varArgs = (VariablesArguments) arguments;

        // boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;

        List<Types.Variable> list = new ArrayList<>();
        Object container = context.getRecyclableIdPool().getObjectById(varArgs.variablesReference);
        // vscode will always send variables request to a staled scope, return the empty list is ok since the next
        // variable request will contain the right variablesReference.
        if (container == null) {
            response.body = new Responses.VariablesResponseBody(list);
            return CompletableFuture.completedFuture(response);
        }

        JPDADebugger debugger = ((NbDebugSession) context.getDebugSession()).getDebugger();
        Models.CompoundModel localsModel = localsModelProvider.getModel(debugger.getSession());
        if (container instanceof NbScope) {
            container = localsModel.getRoot();
        }

        try {
            Object[] children;
            if (varArgs.count > 0) {
                children = localsModel.getChildren(container, varArgs.start, varArgs.start + varArgs.count);
            } else {
                children = localsModel.getChildren(container, 0, Integer.MAX_VALUE);
            }
            for (Object child : children) {
                String name = localsModel.getDisplayName(child);
                String value = String.valueOf(localsModel.getValueAt(child, LOCALS_TO_STRING_COLUMN_ID));
                String type = String.valueOf(localsModel.getValueAt(child, LOCALS_TYPE_COLUMN_ID));
                int id = context.getRecyclableIdPool().addObject(1L, child);
                Types.Variable variable = new Types.Variable(name, value, type, id, null);
                list.add(variable);
            }
        } catch (UnknownTypeException e) {
            throw AdapterUtils.createCompletionException(e.getLocalizedMessage(), ErrorCode.GET_VARIABLE_FAILURE, e);
        }

        response.body = new Responses.VariablesResponseBody(list);

        return CompletableFuture.completedFuture(response);
    }

}
