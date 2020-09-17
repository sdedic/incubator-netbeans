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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Consumer;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Events.OutputEvent.Category;
import org.openide.util.Pair;

public final class NbProcessConsole {

    private final Consumer<ConsoleMessage> messageConsumer;
    private final MessagesProvider outputProvider;
    private final MessagesProvider errorProvider;

    /**
     * Constructor.
     * @param process
     *              the process
     * @param name
     *              the process name
     * @param encoding
     *              the process encoding format
     */
    NbProcessConsole(Pair<InputStream, InputStream> outErrStreams, String name, Charset encoding, Consumer<ConsoleMessage> messageConsumer) {
        this.messageConsumer = messageConsumer;
        outputProvider = new MessagesProvider(name + " OUT", outErrStreams.first(), encoding, Category.stdout);
        errorProvider = new MessagesProvider(name + " ERR", outErrStreams.second(), encoding, Category.stderr);
        outputProvider.start();
        errorProvider.start();
    }

    /**
     * Stop monitoring the process console.
     */
    public void stop() {
        outputProvider.interrupt();
        errorProvider.interrupt();
    }

    private final class MessagesProvider extends Thread {

        private static final int BUFFER_LENGTH = 2048;

        private final Reader reader;
        private final Category category;
        private char[] buffer = new char[BUFFER_LENGTH];

        MessagesProvider(String name, InputStream inputStream, Charset encoding, Category category) {
            super(name);
            setDaemon(true);
            this.reader = new InputStreamReader(inputStream, encoding);
            this.category = category;
        }

        @Override
        public void run() {
            int length;
            try {
                while ((length = reader.read(buffer, 0, BUFFER_LENGTH)) != -1) {
                    String text = new String(buffer, 0, length);
                    String[] lines = text.split("(?<=\n)");
                    for (String line : lines) {
                        messageConsumer.accept(new ConsoleMessage(line, category));
                    }
                    if (Thread.interrupted()) {
                        break;
                    }
                }
            } catch (IOException ex) {
                // EOF
            } finally {
                messageConsumer.accept(null); // EOF
            }
        }
    }

    public static final class ConsoleMessage {
        public String output;
        public Category category;

        public ConsoleMessage(String message, Category category) {
            this.output = message;
            this.category = category;
        }
    }

}
