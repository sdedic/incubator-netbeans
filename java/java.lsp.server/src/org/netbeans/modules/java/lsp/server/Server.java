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
package org.netbeans.modules.java.lsp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.modules.java.lsp.server.debugging.Debugger;
import org.netbeans.modules.java.lsp.server.text.TextDocumentServiceImpl;
import org.netbeans.modules.java.lsp.server.workspace.WorkspaceServiceImpl;
import org.netbeans.spi.sendopts.Arg;
import org.netbeans.spi.sendopts.ArgsProcessor;
import org.netbeans.spi.sendopts.Description;
import org.netbeans.spi.sendopts.Env;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author lahvac
 */
public class Server implements ArgsProcessor {

    @Arg(longName="start-java-language-server", defaultValue = "-1")
    @Description(shortDescription="#DESC_StartJavaLanguageServer")
    @Messages("DESC_StartJavaLanguageServer=Starts the Java Language Server")
    public String lsPort;

    @Arg(longName="start-java-debug-adapter-server", defaultValue = "-1")
    @Description(shortDescription="#DESC_StartJavaDebugAdapterServer")
    @Messages("DESC_StartJavaDebugAdapterServer=Starts the Java Debug Adapter Server")
    public String debugPort;

    @Override
    public void process(Env env) throws CommandException {
        try {
            int connectTo = Integer.parseInt(lsPort);
            int debugConnectTo = Integer.parseInt(debugPort);
            if (debugConnectTo == -1) {
                debugConnectTo = 10001;
            }
            if (connectTo == -1) {
                run(env.getInputStream(), env.getOutputStream(), debugConnectTo);
            } else {
                Socket socket = new Socket("127.0.0.1", connectTo);
                run(socket.getInputStream(), socket.getOutputStream(), debugConnectTo);
            }
        } catch (Exception ex) {
            throw (CommandException) new CommandException(1).initCause(ex);
        }
    }
    
    private static void run(InputStream in, OutputStream out, int debugPort) throws Exception {
        LanguageServerImpl server = new LanguageServerImpl();
        Launcher<LanguageClient> serverLauncher = LSPLauncher.createServerLauncher(server, in, out);
        ((LanguageClientAware) server).connect(serverLauncher.getRemoteProxy());
        Future<Void> runningServer = serverLauncher.startListening();
        Debugger.startDebugger(debugPort);
        runningServer.get();
    }

    private static class LanguageServerImpl implements LanguageServer, LanguageClientAware {

        private static final Logger LOG = Logger.getLogger(LanguageServerImpl.class.getName());
        private LanguageClient client;
        private final TextDocumentService textDocumentService = new TextDocumentServiceImpl();

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams init) {
            List<FileObject> projectCandidates = new ArrayList<>();
            List<WorkspaceFolder> folders = init.getWorkspaceFolders();
            if (folders != null) {
                for (WorkspaceFolder w : folders) {
                    try {
                        projectCandidates.add(TextDocumentServiceImpl.fromUri(w.getUri()));
                    } catch (MalformedURLException ex) {
                        LOG.log(Level.FINE, null, ex);
                    }
                }
            } else {
                String root = init.getRootUri();

                if (root != null) {
                    try {
                        projectCandidates.add(TextDocumentServiceImpl.fromUri(root));
                    } catch (MalformedURLException ex) {
                        LOG.log(Level.FINE, null, ex);
                    }
                } else {
                    //TODO: use getRootPath()?
                }
            }
            List<Project> projects = new ArrayList<>();
            for (FileObject candidate : projectCandidates) {
                Project prj = FileOwnerQuery.getOwner(candidate);
                if (prj != null) {
                    projects.add(prj);
                }
            }
            //XXX: ensure project opened:
            try {
                Class.forName("org.netbeans.modules.project.ui.OpenProjectList", false, Lookup.getDefault().lookup(ClassLoader.class)).getDeclaredMethod("waitProjectsFullyOpen").invoke(null);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            OpenProjects.getDefault().open(projects.toArray(new Project[0]), false);
            try {
                OpenProjects.getDefault().openProjects().get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
            for (Project prj : projects) {
                //init source groups/FileOwnerQuery:
                ProjectUtils.getSources(prj).getSourceGroups(Sources.TYPE_GENERIC);
            }
            try {
                JavaSource.create(ClasspathInfo.create(ClassPath.EMPTY, ClassPath.EMPTY, ClassPath.EMPTY))
                          .runWhenScanFinished(cc -> {
                  client.showMessage(new MessageParams(MessageType.Info, INDEXING_COMPLETED));
                  //todo: refresh diagnostics all open editor?
                }, true);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            ServerCapabilities capabilities = new ServerCapabilities();
            capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
            CompletionOptions completionOptions = new CompletionOptions();
            completionOptions.setResolveProvider(true);
            capabilities.setCompletionProvider(completionOptions);
            capabilities.setCodeActionProvider(true);
            capabilities.setDocumentSymbolProvider(true);
            capabilities.setDefinitionProvider(true);
            capabilities.setDocumentHighlightProvider(true);
            capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(Arrays.asList(JAVA_BUILD_WORKSPACE)));
            return CompletableFuture.completedFuture(new InitializeResult(capabilities));
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void exit() {
            System.exit(1);
        }

        @Override
        public TextDocumentService getTextDocumentService() {
            return textDocumentService;
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return new WorkspaceServiceImpl();
        }

        @Override
        public void connect(LanguageClient client) {
            this.client = client;
            ((LanguageClientAware) getTextDocumentService()).connect(client);
        }
    }

    public static final String JAVA_BUILD_WORKSPACE =  "java.build.workspace";
    static final String INDEXING_COMPLETED = "Indexing completed.";
}
