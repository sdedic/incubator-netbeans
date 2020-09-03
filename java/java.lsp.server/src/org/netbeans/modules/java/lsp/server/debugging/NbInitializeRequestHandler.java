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

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Requests;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.DebuggerManagerAdapter;
import org.netbeans.api.debugger.Session;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.modules.debugger.jpda.models.JPDAThreadImpl;

/**
 *
 * @author martin
 */
public class NbInitializeRequestHandler implements IDebugRequestHandler {

    public NbInitializeRequestHandler() {
    }

    private boolean registered = false;

    @Override
    public List<Requests.Command> getTargetCommands() {
        return Arrays.asList(Requests.Command.INITIALIZE);
    }

    @Override
    public CompletableFuture<Messages.Response> handle(Requests.Command command, Requests.Arguments arguments, Messages.Response response, IDebugAdapterContext context) {
        if (!registered) {
            registered = true;
            registerBreakpointHandler(context);
        }
        return CompletableFuture.completedFuture(response);
    }

    private void registerBreakpointHandler(IDebugAdapterContext context) {
        DebuggerManager.getDebuggerManager().addDebuggerListener(DebuggerManager.PROP_SESSIONS, new DebuggerManagerAdapter() {
            @Override
            public void sessionAdded(Session session) {
                DebuggerManager.getDebuggerManager().removeDebuggerListener(DebuggerManager.PROP_SESSIONS, this);
                JPDADebugger dbg = session.lookupFirst(null, JPDADebugger.class);
                dbg.addPropertyChangeListener(new DebuggerPropertyChangeListener(context));
            }
        });
    }
    private final class DebuggerPropertyChangeListener implements PropertyChangeListener {

        private final IDebugAdapterContext context;

        DebuggerPropertyChangeListener(IDebugAdapterContext context) {
            this.context = context;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String propertyName = evt.getPropertyName();
            switch (propertyName) {
                case JPDADebugger.PROP_STATE:
                    int newState = (int) evt.getNewValue();
                    JPDADebugger debugger = (JPDADebugger) evt.getSource();
                    switch (newState) {
                        case JPDADebugger.STATE_STOPPED:
                            JPDAThread currentThread = debugger.getCurrentThread();
                            if (((JPDAThreadImpl) currentThread).isInStep()) {
                                context.getProtocolServer().sendEvent(new Events.StoppedEvent("step", currentThread.getID()));
                            } else if (currentThread.getCurrentBreakpoint() != null) {
                                context.getProtocolServer().sendEvent(new Events.StoppedEvent("breakpoint", currentThread.getID()));
                            } else {
                                context.getProtocolServer().sendEvent(new Events.StoppedEvent("pause", currentThread.getID()));
                            }
                            break;
                        case JPDADebugger.STATE_DISCONNECTED:
                            debugger.removePropertyChangeListener(this);
                            context.setVmTerminated();
                            context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
                            // Terminate eventHub thread.
                            try {
                                context.getDebugSession().getEventHub().close();
                            } catch (Exception e) {
                                // do nothing.
                            }
                            break;
                    }
                    break;
                case JPDADebugger.PROP_THREAD_STARTED:
                    JPDAThread thread = (JPDAThread) evt.getNewValue();
                    Events.ThreadEvent threadStartEvent = new Events.ThreadEvent("started", thread.getID());
                    context.getProtocolServer().sendEvent(threadStartEvent);
                    break;
                case JPDADebugger.PROP_THREAD_DIED:
                    thread = (JPDAThread) evt.getOldValue();
                    Events.ThreadEvent threadDeathEvent = new Events.ThreadEvent("exited", thread.getID());
                    context.getProtocolServer().sendEvent(threadDeathEvent);
                    break;
            }
        }

    }
}
