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
package org.netbeans.modules.java.lsp.server.ui;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Set;
import org.netbeans.api.io.Hyperlink;
import org.netbeans.api.io.OutputColor;
import org.netbeans.api.io.ShowOperation;
import org.netbeans.modules.java.lsp.server.ui.LspInputOutputProvider.LspIO;
import org.netbeans.spi.io.InputOutputProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = InputOutputProvider.class)
public final class LspInputOutputProvider implements InputOutputProvider<LspIO, PrintWriter, Void, Void> {
    @Override
    public String getId() {
        return "lspio";
    }

    @Override
    public LspIO getIO(String name, boolean newIO, Lookup lookup) {
        IOContext ioCtx = IOContext.find();
        return new LspIO(name, ioCtx, lookup);
    }

    @Override
    public Reader getIn(LspIO io) {
        return io.in;
    }

    @Override
    public PrintWriter getOut(LspIO io) {
        return io.out;
    }

    @Override
    public PrintWriter getErr(LspIO io) {
        return io.err;
    }

    @Override
    public void print(LspIO io, PrintWriter writer, String text, Hyperlink link, OutputColor color, boolean printLineEnd) {
        if (printLineEnd) {
            writer.println(text);
        } else {
            writer.print(text);
        }
    }

    @Override
    public Lookup getIOLookup(LspIO io) {
        return io.lookup;
    }

    @Override
    public void resetIO(LspIO io) {
    }

    @Override
    public void showIO(LspIO io, Set<ShowOperation> operations) {
    }

    @Override
    public void closeIO(LspIO io) {
    }

    @Override
    public boolean isIOClosed(LspIO io) {
        return false;
    }

    @Override
    public Void getCurrentPosition(LspIO io, PrintWriter writer) {
        return null;
    }

    @Override
    public void scrollTo(LspIO io, PrintWriter writer, Void position) {
    }

    @Override
    public Void startFold(LspIO io, PrintWriter writer, boolean expanded) {
        return null;
    }

    @Override
    public void endFold(LspIO io, PrintWriter writer, Void fold) {
    }

    @Override
    public void setFoldExpanded(LspIO io, PrintWriter writer, Void fold, boolean expanded) {
    }

    @Override
    public String getIODescription(LspIO io) {
        return "";
    }

    @Override
    public void setIODescription(LspIO io, String description) {
    }

    public static final class LspIO {
        private final String name;
        private final IOContext ctx;
        final Lookup lookup;
        final Reader in;
        final PrintWriter out;
        final PrintWriter err;

        LspIO(String name, IOContext ioCtx, Lookup lookup) {
            this.name = name;
            this.ctx = ioCtx;
            this.lookup = lookup;
            this.out = new PrintWriter(new LspWriter(true));
            this.err = new PrintWriter(new LspWriter(false));
            this.in = new CharArrayReader(new char[0]) {
                @Override
                public void close() {
                }
            };
        }

        private final class LspWriter extends Writer {
            private final boolean stdIO;

            LspWriter(boolean stdIO) {
                this.stdIO = stdIO;
            }

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                String chunk = new String(cbuf, off, len);
                if (stdIO) {
                    ctx.stdOut(chunk);
                } else {
                    ctx.stdErr(chunk);
                }
            }

            @Override
            public void flush() throws IOException {
            }

            @Override
            public void close() throws IOException {
            }
        }
    }

}
