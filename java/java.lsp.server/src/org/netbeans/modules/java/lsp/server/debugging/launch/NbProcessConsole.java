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

import com.microsoft.java.debug.core.protocol.Events.OutputEvent.Category;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.openide.util.Pair;

/**
 *
 * @author martin
 */
public final class NbProcessConsole {
    private InputStreamObservable stdoutStream;
    private InputStreamObservable stderrStream;
    private Observable<ConsoleMessage> observable = null;

    /**
     * Constructor.
     * @param process
     *              the process
     * @param name
     *              the process name
     * @param encoding
     *              the process encoding format
     */
    NbProcessConsole(Pair<InputStream, InputStream> outErrStreams, String name, Charset encoding) {
        this.stdoutStream = new InputStreamObservable(name + " Stdout Handler", outErrStreams.first(), encoding);
        this.stderrStream = new InputStreamObservable(name + " Stderr Handler", outErrStreams.second(), encoding);
        Observable<ConsoleMessage> stdout = this.stdoutStream.messages().map((message) -> new ConsoleMessage(message, Category.stdout));
        Observable<ConsoleMessage> stderr = this.stderrStream.messages().map((message) -> new ConsoleMessage(message, Category.stderr));
        this.observable = Observable.mergeArrayDelayError(stdout, stderr).observeOn(Schedulers.newThread());
    }

    /**
     * Start monitoring the stdout/stderr streams of the target process.
     */
    public void start() {
        stdoutStream.start();
        stderrStream.start();
    }

    /**
     * Stop monitoring the process console.
     */
    public void stop() {
        stdoutStream.stop();
        stderrStream.stop();
    }

    public Observable<ConsoleMessage> messages() {
        return observable;
    }

    public Observable<ConsoleMessage> stdoutMessages() {
        return this.messages().filter((message) -> message.category == Category.stdout);
    }

    public Observable<ConsoleMessage> stderrMessages() {
        return this.messages().filter((message) -> message.category == Category.stderr);
    }

    /**
     * Split the stdio message to lines, and return them as a new Observable.
     */
    public Observable<ConsoleMessage> lineMessages() {
        return this.messages().map((message) -> {
            String[] lines = message.output.split("(?<=\n)");
            return Stream.of(lines).map((line) -> new ConsoleMessage(line, message.category)).toArray(ConsoleMessage[]::new);
        }).concatMap((lines) -> Observable.fromArray(lines));
    }

    public static class InputStreamObservable {
        private PublishSubject<String> rxSubject = PublishSubject.<String>create();
        private String name;
        private InputStream inputStream;
        private Charset encoding;
        private Thread loopingThread;

        /**
         * Constructor.
         */
        public InputStreamObservable(String name, InputStream inputStream, Charset encoding) {
            this.name = name;
            this.inputStream = inputStream;
            this.encoding = encoding;
        }

        /**
         * Starts the stream.
         */
        public void start() {
            loopingThread = new Thread(name) {
                public void run() {
                    monitor(inputStream, rxSubject);
                }
            };
            loopingThread.setDaemon(true);
            loopingThread.start();
        }

        /**
         * Stops the stream.
         */
        public void stop() {
            if (loopingThread != null) {
                loopingThread.interrupt();
                loopingThread = null;
            }
        }

        private void monitor(InputStream input, PublishSubject<String> subject) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding));
            final int BUFFERSIZE = 4096;
            char[] buffer = new char[BUFFERSIZE];
            while (true) {
                try {
                    if (Thread.interrupted()) {
                        subject.onComplete();
                        return;
                    }
                    int read = reader.read(buffer, 0, BUFFERSIZE);
                    if (read == -1) {
                        subject.onComplete();
                        return;
                    }

                    subject.onNext(new String(buffer, 0, read));
                } catch (IOException e) {
                    subject.onError(e);
                    return;
                }
            }
        }

        public Observable<String> messages() {
            return rxSubject;
        }
    }

    public static class ConsoleMessage {
        public String output;
        public Category category;

        public ConsoleMessage(String message, Category category) {
            this.output = message;
            this.category = category;
        }
    }
}
