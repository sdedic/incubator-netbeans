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
package org.netbeans.modules.java.lsp.server.workspace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.netbeans.api.debugger.ActionsManager;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.java.lsp.server.Server;
import org.netbeans.modules.java.lsp.server.utils.IOProviderImpl;
import org.netbeans.spi.project.ActionProvider;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.IOProvider;

/**
 *
 * @author lahvac
 */
public class WorkspaceServiceImpl implements WorkspaceService, LanguageClientAware {

    private final IOProvider ioProvider;
    private LanguageClient client;

    public WorkspaceServiceImpl() {
        final Pair<InputStream, OutputStream> out = IOProviderImpl.createCopyingStreams();
        final Pair<InputStream, OutputStream> err = IOProviderImpl.createCopyingStreams();
        this.ioProvider = new IOProviderImpl(out.second(), err.second());
        StreamPrinter sp = new StreamPrinter(out.first());
        sp.start();
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        switch (params.getCommand()) {
            case Server.GRAALVM_PAUSE_SCRIPT:
                ActionsManager am = DebuggerManager.getDebuggerManager().getCurrentEngine().getActionsManager();
                am.doAction("pauseInGraalScript");
                return CompletableFuture.completedFuture(true);
            case Server.JAVA_BUILD_WORKSPACE:
                for (Project prj : OpenProjects.getDefault().getOpenProjects()) {
                    ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);
                    if (ap != null && ap.isActionEnabled(ActionProvider.COMMAND_BUILD, Lookups.fixed())) {
                        Lookups.executeWith(new ProxyLookup(Lookups.fixed(ioProvider), Lookup.getDefault()), () -> {
                            ap.invokeAction(ActionProvider.COMMAND_BUILD, Lookups.fixed());
                        });
                    }
                }
                return CompletableFuture.completedFuture(true);
            default:
                throw new UnsupportedOperationException("Command not supported: " + params.getCommand());
        }
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams arg0) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams arg0) {
        //TODO: no real configuration right now
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams arg0) {
        //TODO: not watching files for now
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    private class StreamPrinter extends Thread {

        InputStream is;

        public StreamPrinter(InputStream stream) {
            is = stream;
        }

        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line = br.readLine();
                while (line != null) {
                    if (client != null) {
                        client.logMessage(new MessageParams(MessageType.Info, line));
                    }
                    line = br.readLine();
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }
}
