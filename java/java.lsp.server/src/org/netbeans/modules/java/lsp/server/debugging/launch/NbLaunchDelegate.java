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
package org.netbeans.modules.java.lsp.server.debugging.launch;

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.handler.ILaunchDelegate;
import com.sun.jdi.VMDisconnectedException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.DebuggerManagerAdapter;
import org.netbeans.api.debugger.Session;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.queries.UnitTestForSourceQuery;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.debugger.jpda.JPDADebuggerImpl;
import org.netbeans.modules.java.lsp.server.debugging.IConfigurationSemaphore;
import org.netbeans.modules.java.lsp.server.debugging.NbSourceProvider;
import org.netbeans.modules.java.lsp.server.utils.IOProviderImpl;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.IOProvider;

/**
 *
 * @author martin
 */
public abstract class NbLaunchDelegate implements ILaunchDelegate {

    private static final Logger LOG = Logger.getLogger(NbLaunchDelegate.class.getName());

    public final CompletableFuture<NbProcessConsole> nbLaunch(FileObject toRun, IDebugAdapterContext context, boolean debug) {
            Pair<ActionProvider, String> providerAndCommand = findTarget(toRun, debug);
            if (providerAndCommand == null) {
                throw new VMDisconnectedException("Cannot find debug action!"); //TODO: message, locations
            }
            Pair<InputStream, OutputStream> outPair = IOProviderImpl.createCopyingStreams();
            InputStream out = outPair.first();
            Pair<InputStream, OutputStream> errPair = IOProviderImpl.createCopyingStreams();
            InputStream err = errPair.first();
            IOProvider ioProvider = new IOProviderImpl(outPair.second(), errPair.second());
            ActionProgress progress = new ActionProgress() {
                @Override
                protected void started() {}
                @Override
                public void finished(boolean success) {
                    try {
                        outPair.second().close();
                    } catch (IOException ex) {
                        LOG.log(Level.FINE, null, ex);
                    }
                    try {
                        errPair.second().close();
                    } catch (IOException ex) {
                        LOG.log(Level.FINE, null, ex);
                    }
                    /*
                    synchronized (LaunchingVirtualMachine.this) {
                        exitCode = success ? 0 : 1;
                        finished = true;
                        LaunchingVirtualMachine.this.notifyAll();
                    }*/
                }
            };
            CompletableFuture<NbProcessConsole> launchFuture = new CompletableFuture<>();
            if (debug) {
                DebuggerManager.getDebuggerManager().addDebuggerListener(new DebuggerManagerAdapter() {
                    @Override
                    public void sessionAdded(Session session) {
                        JPDADebugger debugger = session.lookupFirst(null, JPDADebugger.class);
                        if (debugger != null) {
                            Map properties = session.lookupFirst(null, Map.class);
                            NbSourceProvider sourceProvider = (NbSourceProvider) context.getProvider(ISourceLookUpProvider.class);
                            sourceProvider.setSourcePath(properties != null ? (ClassPath) properties.getOrDefault("sourcepath", ClassPath.EMPTY) : ClassPath.EMPTY);
                            debugger.addPropertyChangeListener(JPDADebugger.PROP_STATE, new PropertyChangeListener() {
                                @Override
                                public void propertyChange(PropertyChangeEvent evt) {
                                    int newState = (int) evt.getNewValue();
                                    if (newState == JPDADebugger.STATE_RUNNING) {
                                        debugger.removePropertyChangeListener(JPDADebugger.PROP_STATE, this);
                                        IDebugSession debugSession = new NbDebugSession(debugger);
                                        context.setDebugSession(debugSession);
                                        NbProcessConsole console = new NbProcessConsole(Pair.of(out, err), "Debuggee", context.getDebuggeeEncoding());
                                        launchFuture.complete(console);
                                        context.getProvider(IConfigurationSemaphore.class).waitForConfigutaionDone();
                                    }
                                }
                            });
                        }
                    }
                });
            } else {
                launchFuture.complete(new NbProcessConsole(Pair.of(out, err), "Run", context.getDebuggeeEncoding()));
            }

            Lookups.executeWith(new ProxyLookup(Lookups.fixed(ioProvider), Lookup.getDefault()), () -> {
                providerAndCommand.first().invokeAction(providerAndCommand.second(), Lookups.fixed(toRun, ioProvider, progress));
            });
            return launchFuture;
    }

    
    protected static @CheckForNull Pair<ActionProvider, String> findTarget(FileObject toRun, boolean debug) {
        ClassPath sourceCP = ClassPath.getClassPath(toRun, ClassPath.SOURCE);
        FileObject fileRoot = sourceCP != null ? sourceCP.findOwnerRoot(toRun) : null;
        boolean mainSource;
        if (fileRoot != null) {
            mainSource = UnitTestForSourceQuery.findUnitTests(fileRoot).length > 0;
        } else {
            mainSource = true;
        }
        ActionProvider provider = null;
        String command = null;
        Collection<ActionProvider> actionProviders = new ArrayList<>();
        Project prj = FileOwnerQuery.getOwner(toRun);
        if (prj != null) {
            ActionProvider ap = prj.getLookup().lookup(ActionProvider.class);
            actionProviders.add(ap);
        }
        actionProviders.addAll(Lookup.getDefault().lookupAll(ActionProvider.class));
        Lookup testLookup = Lookups.singleton(toRun);
        String[] actions = debug ? mainSource ? new String[] {ActionProvider.COMMAND_DEBUG_SINGLE}
                                              : new String[] {ActionProvider.COMMAND_DEBUG_TEST_SINGLE, ActionProvider.COMMAND_DEBUG_SINGLE}
                                 : mainSource ? new String[] {ActionProvider.COMMAND_RUN_SINGLE}
                                              : new String[] {ActionProvider.COMMAND_TEST_SINGLE, ActionProvider.COMMAND_RUN_SINGLE};

        OUTER: for (String commandCandidate : actions) {
            for (ActionProvider ap : actionProviders) {
                if (new HashSet<>(Arrays.asList(ap.getSupportedActions())).contains(commandCandidate) &&
                    ap.isActionEnabled(commandCandidate, testLookup)) {
                    provider = ap;
                    command = commandCandidate;
                    break OUTER;
                }
            }
        }

        if (provider == null)
            return null;

        return Pair.of(provider, command);
    }

}
