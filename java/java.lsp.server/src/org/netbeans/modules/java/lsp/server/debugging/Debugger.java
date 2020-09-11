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

import com.microsoft.java.debug.core.IEvaluatableBreakpoint;
import com.microsoft.java.debug.core.adapter.HotCodeReplaceEvent;
import com.microsoft.java.debug.core.adapter.ICompletionsProvider;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IExecuteProvider;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.adapter.ProviderContext;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import io.reactivex.Observable;
import io.reactivex.Observer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author lahvac
 */
public class Debugger {

    private static final Logger LOG = Logger.getLogger(Debugger.class.getName());

    public static void startDebugger(InputStream in, OutputStream out) {
        LOG.info("debugging requestaccepted....");
        NbSourceProvider sourceProvider = new NbSourceProvider();
        ProviderContext context = new ProviderContext();
        context.registerProvider(IVirtualMachineManagerProvider.class, new IVirtualMachineManagerProvider() {
            @Override
            public VirtualMachineManager getVirtualMachineManager() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

        });
        context.registerProvider(IHotCodeReplaceProvider.class, new IHotCodeReplaceProvider() {
            @Override
            public void onClassRedefined(Consumer<List<String>> arg0) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public CompletableFuture<List<String>> redefineClasses() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Observable<HotCodeReplaceEvent> getEventHub() {
                return new Observable<HotCodeReplaceEvent>() {
                    @Override
                    protected void subscribeActual(Observer<? super HotCodeReplaceEvent> arg0) {
                        LOG.log(Level.INFO, "arg0= {0}", arg0);
                    }
                };
            }
        });
        context.registerProvider(ISourceLookUpProvider.class, sourceProvider);
        context.registerProvider(IEvaluationProvider.class, new IEvaluationProvider() {
            @Override
            public boolean isInEvaluation(ThreadReference thread) {
                return false;
            }

            @Override
            public CompletableFuture<Value> evaluate(String arg0, ThreadReference arg1, int arg2) {
                return CompletableFuture.completedFuture(new Value() {
                    @Override
                    public Type type() {
                        return null;
                    }

                    @Override
                    public VirtualMachine virtualMachine() {
                        return null;
                    }
                });
            }

            @Override
            public CompletableFuture<Value> evaluate(String arg0, ObjectReference arg1, ThreadReference arg2) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public CompletableFuture<Value> evaluateForBreakpoint(IEvaluatableBreakpoint arg0, ThreadReference arg1) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public CompletableFuture<Value> invokeMethod(ObjectReference arg0, String arg1, String arg2, Value[] arg3, ThreadReference arg4, boolean arg5) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void clearState(ThreadReference arg0) {
            }
        });
        context.registerProvider(ICompletionsProvider.class, new ICompletionsProvider() {
            @Override
            public List<Types.CompletionItem> codeComplete(StackFrame arg0, String arg1, int arg2, int arg3) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        context.registerProvider(IExecuteProvider.class, new IExecuteProvider() {
            @Override
            public Process launch(Requests.LaunchArguments la) throws IOException {
                                throw new IOException();
                                /*
                String filePath = la.mainClass;
                FileObject file = filePath != null ? FileUtil.toFileObject(new File(filePath)) : null;
                if (file == null) {
                    throw new IOException("Missing file: " + la.mainClass);
                }
                return new LaunchingVirtualMachine(file, sourceProvider).runWithoutDebugger();*/
            }
        });
        NbProtocolServer server = new NbProtocolServer(in, out, context);
        server.run();
    }
}
