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
package org.netbeans.core.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.RequestProcessor;

/**
 *
 * @author sdedic
 */
public class ValidateConnectivityTest extends NbTestCase {
    static {
        System.setProperty("java.awt.headless", "true");
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(NbModuleSuite.createConfiguration(ValidateConnectivityTest.class).
            clusters("platform").enableClasspathModules(false).
                enableModules("(?!.*netbinox$|.*html.*|.*jfx.*).*").
                gui(false).suite());
        return suite;
    }

    public ValidateConnectivityTest(String name) {
        super(name);
    }
    
    
    class ServerWorker implements Runnable {
        private final AtomicBoolean cancel = new AtomicBoolean();
        final ServerSocket socket;

        public ServerWorker(ServerSocket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            while (true) {
                try {
                    String msg = 
                            "HTTP/1.0 200 OK\n"  +
                            "Content-Type: text/plain\n" +
                            "Content-Length: 5\n\n" +
                            "Pong!";
                    Socket conn =  socket.accept();
                    // read at least one line:
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                        r.readLine();
                        
                        // and flush 200 OK there:
                        try (Writer wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                            wr.write(msg);
                            wr.flush();
                        }
                        Thread.sleep(1000);
                    }
                    conn.close();
                } catch (SocketException ex) {
                    if (socket.isClosed()) {
                        break;
                    }
                } catch (IOException ex) {
                    // expected
                } catch (InterruptedException ex) {
                }
            }
        }
        
    }
    
    volatile ServerWorker worker;

    @Override
    protected void tearDown() throws Exception {
        if (worker != null) {
            try {
                worker.socket.close();
            } catch (IOException ex) {
                // expected
            }
        }
        super.tearDown();
    }
    
    public void testHttpConnection() throws Exception {
        ServerSocket sock = new ServerSocket(0);
        worker = new ServerWorker(sock);
        RequestProcessor.getDefault().post(worker);
        
        URL u = new URL("http://localhost:" + worker.socket.getLocalPort() + "/hello");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream(), "UTF-8"))) {
            String l = r.readLine();
        }
    }
}
