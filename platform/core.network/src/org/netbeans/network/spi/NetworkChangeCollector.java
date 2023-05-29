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
package org.netbeans.network.spi;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author sdedic
 */
public final class NetworkChangeCollector {
    private Map<String, C>   proxyMap = new HashMap<>();
    
    private static class C {
        private URL selector;
        private Proxy proxy;
        private InetAddress outgoing;
    }
    
    private C c(URL u) {
        String s = u == null ? "" : u.toExternalForm();
        return proxyMap.computeIfAbsent(s, x -> new C());
    }
    
    public void proxyFor(URL u, Proxy p) {
        c(u).proxy = p;
    }
    
    public void outgoingAddress(URL u, InetAddress addr) {
        c(u).outgoing = addr;
    }
    
    /**
     * @return Returns the active selectors.
     */
    public Collection<URL> selectors(String service) {
        if (service == null) {
            return proxyMap.values().stream().map(c -> c.selector).collect(Collectors.toList());
        }
        return null;
    }
}
