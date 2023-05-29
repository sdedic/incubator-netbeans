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
package org.netbeans.network.api;

import java.net.InetSocketAddress;
import java.net.Proxy;
import static java.net.Proxy.Type.HTTP;
import static java.net.Proxy.Type.SOCKS;
import java.net.SocketAddress;
import java.net.URL;
import org.netbeans.core.network.utils.HostnameUtils;
import org.netbeans.core.network.utils.NativeException;

/**
 *
 * @author sdedic
 */
public class NetworkUtils {
    /**
     * Gets the name which is likely to be the local host's primary
     * name on the network.
     * 
     * <p>
     * IMPLEMENTATION: 
     * <ul>
     *   <li>On Unix-like OSes (incl Mac OS X) this is the value as returned from
     *       the {@code gethostname()} function from the standard C Library. </li>
     *   <li>On Windows it is the value as returned from the
     *       {@code gethostname()} function from {@code Ws2_32} library.
     *       (without domain name). Note that this Windows function will do a 
     *       name service lookup and the method is therefore potentially blocking, 
     *       although it is more than likely that Windows has cached this result 
     *       on computer startup in its DNS Client Cache and therefore the 
     *       result will be returned very fast.</li>
     * </ul>
     * 
     * @return host name
     * @throws NetworkException if there was an error executing the system calls.
     */
    public static String getNetworkHostname() throws NetworkException {
        try {
            return HostnameUtils.getNetworkHostname();
        } catch (NativeException ex) {
            throw new NetworkException("Could not obtain hostname", ex);
        }
    }
    
    /**
     * Default SOCKS port
     */
    public static final int PORT_DEFAULT_SOCKS = 1080;
    
    /**
     * Default HTTP port
     */
    public static final int PORT_DEFAULT_HTTP = 80;
    
    /**
     * Default HTTPS port
     */
    public static final int PORT_DEFAULT_HTTPS = 443;
    
    
    /**
     * Determines if the specified port is the default one for the protocol in the URI. If
     * the protocol is not recognized, returns false. Use the method to check, if a port needs
     * to be specified explicitly, or if it is sufficient to use just the protocol.
     * @param port the port number
     * @param url protocol specification
     * @return true, if the port value is the default one and can be omitted.
     */
    public static boolean isDefaultPort(int port, URL url) {
        switch (url.getProtocol()) {
            case "https":
                return port == PORT_DEFAULT_HTTPS;
            case "http":
                return port == PORT_DEFAULT_HTTP;
        }
        return false;
    }
    
    /**
     * Simple wrapper around {@link Proxy} that provides textual information
     */
    public static final class ProxySpec {
        private final Proxy  orig;
        private final String proxyHost;
        private final int proxyPort;

        ProxySpec(String proxyHost, int proxyPort, Proxy p) {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.orig = p;
        }
        
        /**
         * @return the original Proxy object
         */
        public Proxy getProxy() {
            return orig;
        }

        /**
         * @return The proxy host name.
         */
        public String getProxyHost() {
            return proxyHost;
        }

        /**
         * Returns the proxy port. For supported protocols (http, socks) return a valid port number even though
         * the Proxy object does not specify one
         * @return proxy port
         */
        public int getProxyPort() {
            return proxyPort;
        }
    }
    
    /**
     * Extracts hostname and port from the proxy, substitutes default port if not specified.
     * @param p the proxy
     * @return the parsed out wrapper.
     */
    public static ProxySpec toProxySpec(Proxy p) {
        if (p == null) {
            return null;
        }
        if (p.type() == Proxy.Type.DIRECT) {
            return null;
        }
        SocketAddress sa = p.address();
        if (sa == null) {
            return null;
        }
        if (sa instanceof InetSocketAddress) {
            InetSocketAddress iaddr = (InetSocketAddress)sa;
            int port = iaddr.getPort();
            int defPort = -1;

            switch(p.type()) {
                case HTTP:
                    defPort = PORT_DEFAULT_HTTP; 
                    break;
                case SOCKS:
                    defPort = PORT_DEFAULT_SOCKS; 
                    break;
            }
            if (port > 0) {
                return new ProxySpec(iaddr.getHostString(), port, p);
            } else {
                return new ProxySpec(iaddr.getHostString(), defPort, p);
            }
        } else {
            // return new ProxySpec(null, -1, p);
            return null;
        }
    }
}
