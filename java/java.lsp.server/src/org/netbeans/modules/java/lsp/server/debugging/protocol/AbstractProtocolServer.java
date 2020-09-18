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

package org.netbeans.modules.java.lsp.server.debugging.protocol;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.openide.util.Pair;

public abstract class AbstractProtocolServer implements IProtocolServer {
    private static final Logger logger = Logger.getLogger("java-debug");
    private static final int BUFFER_SIZE = 8192;
    private static final String TWO_CRLF = "\r\n\r\n";
    private static final Pattern CONTENT_LENGTH_MATCHER = Pattern.compile("Content-Length: (\\d+)");
    private static final Charset PROTOCOL_ENCODING = StandardCharsets.UTF_8; // vscode protocol uses UTF-8 as encoding format.

    protected boolean terminateSession = false;

    private final InputStream input;
    private final Writer writer;

    private final ByteBuffer rawData;
    private int contentLength = -1;
    private final AtomicLong sequenceNumber = new AtomicLong(1);

    private final RequestResponses requestResponses = new RequestResponses();

    /**
     * Constructs a protocol server instance based on the given input stream and
     * output stream.
     *
     * @param input
     *            the input stream
     * @param output
     *            the output stream
     */
    public AbstractProtocolServer(InputStream input, OutputStream output) {
        this.input = input;
        this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output, PROTOCOL_ENCODING)));
        this.contentLength = -1;
        this.rawData = new ByteBuffer();
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            while (!this.terminateSession) {
                int read = this.input.read(buffer, 0, BUFFER_SIZE);
                if (read == -1) {
                    break;
                }

                this.rawData.append(buffer, read);
                this.processData();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("Read data from io exception: %s", e.toString()), e);
        }
    }

    /**
     * Sets terminateSession flag to true. And the dispatcher loop will be
     * terminated after current dispatching operation finishes.
     */
    public void stop() {
        this.terminateSession = true;
    }

    /**
     * Send a request/response/event to the DA.
     *
     * @param message
     *            the message.
     */
    private void sendMessage(Messages.ProtocolMessage message) {
        message.seq = this.sequenceNumber.getAndIncrement();

        String jsonMessage = JsonUtils.toJson(message);
        byte[] jsonBytes = jsonMessage.getBytes(PROTOCOL_ENCODING);

        String header = String.format("Content-Length: %d%s", jsonBytes.length, TWO_CRLF);
        byte[] headerBytes = header.getBytes(PROTOCOL_ENCODING);

        ByteBuffer data = new ByteBuffer();
        data.append(headerBytes);
        data.append(jsonBytes);

        String utf8Data = data.getString(PROTOCOL_ENCODING);

        try {
            if (message instanceof Messages.Request) {
                logger.fine("\n[[REQUEST]]\n" + utf8Data);
            } else if (message instanceof Messages.Event) {
                logger.fine("\n[[EVENT]]\n" + utf8Data);
            } else {
                logger.fine("\n[[RESPONSE]]\n" + utf8Data);
            }
            this.writer.write(utf8Data);
            this.writer.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("Write data to io exception: %s", e.toString()), e);
        }
    }

    @Override
    public void sendEvent(Events.DebugEvent event) {
        sendMessage(new Messages.Event(event.type, event));
    }

    @Override
    public void sendResponse(Messages.Response response) {
        sendMessage(response);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request) {
        return sendRequest(request, 0);
    }

    @Override
    public CompletableFuture<Messages.Response> sendRequest(Messages.Request request, long timeout) {
        CompletableFuture<Messages.Response> future = requestResponses.add(request.seq, timeout);
        sendMessage(request);
        return future;
    }

    private void processData() {
        while (true) {
            /**
             * In vscode debug protocol, the content length represents the
             * message's byte length with utf8 format.
             */
            if (this.contentLength >= 0) {
                if (this.rawData.length() >= this.contentLength) {
                    byte[] buf = this.rawData.removeFirst(this.contentLength);
                    this.contentLength = -1;
                    String messageData = new String(buf, PROTOCOL_ENCODING);
                    try {
                        Messages.ProtocolMessage message = JsonUtils.fromJson(messageData, Messages.ProtocolMessage.class);

                        logger.fine(String.format("\n[%s]\n%s", message.type, messageData));

                        if (message.type.equals("request")) {
                            Messages.Request request = JsonUtils.fromJson(messageData, Messages.Request.class);
                            try {
                                this.dispatchRequest(request);
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, String.format("Dispatch debug protocol error: %s", e.toString()), e);
                            }
                        } else if (message.type.equals("response")) {
                            Messages.Response response = JsonUtils.fromJson(messageData, Messages.Response.class);
                            requestResponses.response(response);
                        }
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, String.format("Error parsing message: %s", ex.toString()), ex);
                    }

                    continue;
                }
            }

            String rawMessage = this.rawData.getString(PROTOCOL_ENCODING);
            int idx = rawMessage.indexOf(TWO_CRLF);
            if (idx != -1) {
                Matcher matcher = CONTENT_LENGTH_MATCHER.matcher(rawMessage);
                if (matcher.find()) {
                    this.contentLength = Integer.parseInt(matcher.group(1));
                    int headerByteLength = rawMessage.substring(0, idx + TWO_CRLF.length())
                            .getBytes(PROTOCOL_ENCODING).length;
                    this.rawData.removeFirst(headerByteLength); // Remove the header from the raw message.
                    continue;
                }
            }

            break;
        }
    }

    protected abstract void dispatchRequest(Messages.Request request);

    private class RequestResponses {

        private final Map<Long, Pair<CompletableFuture<Messages.Response>, Timer>> pendingResponses = new ConcurrentHashMap<>();

        public CompletableFuture<Messages.Response> add(long seq, long timeout) {
            Timer timer;
            if (timeout != 0) {
                timer = new Timer(Long.toString(seq));
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Pair<CompletableFuture<Messages.Response>, Timer> completable = pendingResponses.remove(seq);
                        if (completable != null) {
                            completable.first().completeExceptionally(new TimeoutException("timeout"));
                        }
                    }
                }, timeout);
            } else {
                timer = null;
            }
            CompletableFuture<Messages.Response> completable = new CompletableFuture<>();
            pendingResponses.put(seq, Pair.of(completable, timer));
            return completable;
        }

        public void response(Messages.Response response) {
            Pair<CompletableFuture<Messages.Response>, Timer> completable = pendingResponses.remove(response.request_seq);
            if (completable != null) {
                Timer timer = completable.second();
                if (timer != null) {
                    timer.cancel();
                }
                completable.first().complete(response);
            } else {
                // Timed out already
            }
        }
    }

    private class ByteBuffer {
        private byte[] buffer;

        public ByteBuffer() {
            this.buffer = new byte[0];
        }

        public int length() {
            return this.buffer.length;
        }

        public String getString(Charset cs) {
            return new String(this.buffer, cs);
        }

        public void append(byte[] b) {
            append(b, b.length);
        }

        public void append(byte[] b, int length) {
            byte[] newBuffer = new byte[this.buffer.length + length];
            System.arraycopy(buffer, 0, newBuffer, 0, this.buffer.length);
            System.arraycopy(b, 0, newBuffer, this.buffer.length, length);
            this.buffer = newBuffer;
        }

        public byte[] removeFirst(int n) {
            byte[] b = new byte[n];
            System.arraycopy(this.buffer, 0, b, 0, n);
            byte[] newBuffer = new byte[this.buffer.length - n];
            System.arraycopy(this.buffer, n, newBuffer, 0, this.buffer.length - n);
            this.buffer = newBuffer;
            return b;
        }
    }
}
