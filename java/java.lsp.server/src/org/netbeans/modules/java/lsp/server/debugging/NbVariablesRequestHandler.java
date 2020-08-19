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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IStackFrameManager;
import com.microsoft.java.debug.core.adapter.variables.IVariableFormatter;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalStructureExpression;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructure.LogicalVariable;
import com.microsoft.java.debug.core.adapter.variables.JavaLogicalStructureManager;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.Variable;
import com.microsoft.java.debug.core.adapter.variables.VariableDetailUtils;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.adapter.variables.VariableUtils;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.VariablesArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InternalException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.netbeans.spi.viewmodel.ColumnModel;
import org.netbeans.spi.viewmodel.Model;
import org.netbeans.spi.viewmodel.Models;
import org.netbeans.spi.viewmodel.UnknownTypeException;

/**
 *
 * @author martin
 */
public class NbVariablesRequestHandler implements IDebugRequestHandler {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private static final String LOCALS_VIEW_NAME = "LocalsView";
    private static final String LOCALS_TO_STRING_COLUMN_ID = "LocalsToString";
    private static final String LOCALS_TYPE_COLUMN_ID = "LocalsType";
    private static final String LOCALS_VALUE_COLUMN_ID = "LocalsValue";

    private final ViewModel localsView = new ViewModel(LOCALS_VIEW_NAME);;

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.VARIABLES);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        //IVariableFormatter variableFormatter = context.getVariableFormatter();
        VariablesArguments varArgs = (VariablesArguments) arguments;

        boolean showStaticVariables = DebugSettings.getCurrent().showStaticVariables;

        //Map<String, Object> options = variableFormatter.getDefaultOptions();
        //VariableUtils.applyFormatterOptions(options, varArgs.format != null && varArgs.format.hex);
        IEvaluationProvider evaluationEngine = context.getProvider(IEvaluationProvider.class);

        List<Types.Variable> list = new ArrayList<>();
        Object container = context.getRecyclableIdPool().getObjectById(varArgs.variablesReference);
        // vscode will always send variables request to a staled scope, return the empty list is ok since the next
        // variable request will contain the right variablesReference.
        if (container == null) {
            response.body = new Responses.VariablesResponseBody(list);
            return CompletableFuture.completedFuture(response);
        }

        Models.CompoundModel localsModel = localsView.createModel();

        if (container instanceof VariableProxy) {
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
