/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.netbeans.modules.java.lsp.server.debugging;

import com.sun.jdi.VMDisconnectedException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.java.lsp.server.debugging.protocol.AbstractProtocolServer;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Events.DebugEvent;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Events.StoppedEvent;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.modules.java.lsp.server.debugging.utils.UsageDataSession;

public class NbProtocolServer extends AbstractProtocolServer {

    private static final Logger LOG = Logger.getLogger(NbProtocolServer.class.getName());

    private final IDebugAdapter debugAdapter;
    private final UsageDataSession usageDataSession = new UsageDataSession();

    private final Object lock = new Object();
    private boolean isDispatchingRequest = false;
    private final ConcurrentLinkedQueue<DebugEvent> eventQueue = new ConcurrentLinkedQueue<>();

    /**
     * Constructs a protocol server instance based on the given input stream and output stream.
     * @param input
     *              the input stream
     * @param output
     *              the output stream
     * @param context
     *              provider context for a series of provider implementation
     */
    public NbProtocolServer(InputStream input, OutputStream output, IProviderContext context) {
        super(input, output);
        debugAdapter = new NbDebugAdapter(this, context);
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    @Override
    public void run() {
        usageDataSession.reportStart();
        super.run();
        usageDataSession.reportStop();
        usageDataSession.submitUsageData();
    }

    @Override
    public void sendResponse(Messages.Response response) {
        usageDataSession.recordResponse(response);
        super.sendResponse(response);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request) {
        LOG.log(Level.FINE, "SEND REQUEST: {0} {1}", new Object[]{request.command, request.arguments});
        usageDataSession.recordRequest(request);
        return super.sendRequest(request);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request, long timeout) {
        LOG.log(Level.FINE, "SEND REQUEST: {0} {1}", new Object[]{request.command, request.arguments});
        usageDataSession.recordRequest(request);
        return super.sendRequest(request, timeout);
    }

    @Override
    public void sendEvent(DebugEvent event) {
        LOG.log(Level.FINE, "SEND Event: {0} {1}", new Object[]{event.type, event});
        // See the two bugs https://github.com/Microsoft/java-debug/issues/134 and https://github.com/Microsoft/vscode/issues/58327,
        // it requires the java-debug to send the StoppedEvent after ContinueResponse/StepResponse is received by DA.
        if (event instanceof StoppedEvent) {
            sendEventLater(event);
        } else {
            super.sendEvent(event);
        }

    }

    /**
     * If the the dispatcher is idle, then send the event to the DA immediately.
     * Else add the new event to an eventQueue first and send them when dispatcher becomes idle again.
     */
    private void sendEventLater(DebugEvent event) {
        synchronized (lock) {
            if (this.isDispatchingRequest) {
                this.eventQueue.offer(event);
            } else {
                super.sendEvent(event);
            }
        }
    }

    @Override
    protected void dispatchRequest(Messages.Request request) {
        usageDataSession.recordRequest(request);
        try {
            synchronized (lock) {
                this.isDispatchingRequest = true;
            }

            debugAdapter.dispatchRequest(request).thenCompose((response) -> {
                CompletableFuture<Void> future = new CompletableFuture<>();
                if (response != null) {
                    LOG.log(Level.FINE, "Response: {0} {1} body = {2}", new Object[]{response.command, response.message, response.body});
                    sendResponse(response);
                    future.complete(null);
                } else {
                    future.completeExceptionally(new DebugException("The request dispatcher should not return null response.",
                            ErrorCode.UNKNOWN_FAILURE.getId()));
                }
                return future;
            }).exceptionally((error) -> {
                Messages.Response response = new Messages.Response(request.seq, request.command);
                Throwable ex;
                if (error instanceof CompletionException && error.getCause() != null) {
                    ex = error.getCause();
                } else {
                    ex = error;
                }
                assert ex != null;

                if (ex instanceof VMDisconnectedException) {
                    // mark it success to avoid reporting error on VSCode.
                    response.success = true;
                    sendResponse(response);
                } else {
                    String exceptionMessage = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                    ErrorCode errorCode = ex instanceof DebugException ? ErrorCode.parse(((DebugException) ex).getErrorCode()) : ErrorCode.UNKNOWN_FAILURE;
                    boolean isUserError = ex instanceof DebugException && ((DebugException) ex).isUserError();
                    if (isUserError) {
                        usageDataSession.recordUserError(errorCode);
                    } else {
                        LOG.log(Level.SEVERE, String.format("[error response][%s]: %s", request.command, exceptionMessage), ex);
                    }

                    sendResponse(AdapterUtils.setErrorResponse(response,
                            errorCode,
                            exceptionMessage));
                }
                return null;
            }).join();
        } finally {
            synchronized (lock) {
                this.isDispatchingRequest = false;
            }

            while (this.eventQueue.peek() != null) {
                super.sendEvent(this.eventQueue.poll());
            }
        }
    }
}
