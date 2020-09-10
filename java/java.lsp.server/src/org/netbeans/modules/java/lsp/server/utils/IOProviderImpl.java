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
package org.netbeans.modules.java.lsp.server.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.Writer;

import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputListener;
import org.openide.windows.OutputWriter;

public final class IOProviderImpl extends IOProvider {

    private final OutputStream out;
    private final OutputStream err;

    public IOProviderImpl(OutputStream out, OutputStream err) {
        this.out = out;
        this.err = err;
    }

    @Override
    public synchronized InputOutput getIO(String name, boolean newIO) {
        return new IO(new OutputWriterImpl(this, OutputWriterImpl.Kind.OUT),
                new OutputWriterImpl(this, OutputWriterImpl.Kind.ERR));
    }

    @Override
    public OutputWriter getStdOut() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static Pair<InputStream, OutputStream> createCopyingStreams() {
        PipedInputStream in = new PipedInputStream();
        OutputStream out;
        try {
            out = new PipedOutputStream(in);
        } catch (IOException ex) {
            throw new RuntimeException(ex); // Can not happen
        }
        return Pair.of(in, out);
    }

    private static final class IO implements InputOutput {

        private final OutputWriter out;
        private final OutputWriter err;

        public IO(OutputWriter out, OutputWriter err) {
            this.out = out;
            this.err = err;
        }

        @Override
        public OutputWriter getOut() {
            return out;
        }

        @Override
        public Reader getIn() {
            return new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    return -1;
                }

                @Override
                public void close() throws IOException {
                }
            };
        }

        @Override
        public OutputWriter getErr() {
            return err;
        }

        @Override
        public void closeInputOutput() {
        }

        @Override
        public boolean isClosed() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setOutputVisible(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setErrVisible(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setInputVisible(boolean value) {
            //ignore...
        }

        @Override
        public void select() {
        }

        @Override
        public boolean isErrSeparated() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setErrSeparated(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean isFocusTaken() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setFocusTaken(boolean value) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Reader flushReader() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }

    private static class OutputWriterImpl extends OutputWriter {

        private final Kind kind;

        public OutputWriterImpl(IOProviderImpl io, Kind kind) {
            super(createWriter(io, kind));
            this.kind = kind;
        }

        private static Writer createWriter(IOProviderImpl io, Kind kind) {
            return new OutputStreamWriter(kind == Kind.OUT ? io.out : io.err);
        }

        @Override
        public void println(String s, OutputListener l) throws IOException {
            out.write(s);
            out.write("\n");
            out.flush();
        }

        @Override
        public void write(String s, int off, int len) {
            super.write(s, off, len);
            flush();
        }

        @Override
        public void write(char[] buf, int off, int len) {
            super.write(buf, off, len);
            flush();
        }

        @Override
        public void write(int c) {
            super.write(c);
            flush();
        }

        @Override
        public void reset() throws IOException {
            IOProvider io = Lookup.getDefault().lookup(IOProvider.class);
            if (io instanceof IOProviderImpl) {
                out = createWriter((IOProviderImpl) io, kind);
            }
        }

        @Override
        public void close() {
        }

        public enum Kind {
            OUT, ERR;
        }
    }
}
