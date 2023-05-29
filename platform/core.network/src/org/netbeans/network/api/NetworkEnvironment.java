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

import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URL;
import java.util.EventObject;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.netbeans.core.network.utils.LocalAddressUtils;
import org.netbeans.network.impl.NetworkApiAccessor;
import org.openide.util.RequestProcessor;

/**
 * Provides basic access to the network environment data. Various services in the IDE may need to create local or network-accessible
 * servers, or connect to outside network. Those services may need to reconnect or adapt when the network environment changes, i.e. the 
 * user switches from public to private network or vice versa. JDK offers the {@link ProxySelector} service, but does not offer 
 * a trigger that could initiate a reconfiguration.
 * <p>
 * It is expected that the network client wants to work with some service, or generally with the global network services. The client 
 * can register get notified when the network conditions for the service change so it may need to reset and reestablish a connection
 * or change its data or configuration based on the newly discovered network configuration.
 * <p>
 * 
 * @author sdedic
 */
public final class NetworkEnvironment {
    
    private static final NetworkEnvironment INSTANCE = new NetworkEnvironment();
    
    private static final RequestProcessor   REFRESH_RP = new RequestProcessor(NetworkEnvironment.class);
    
    static {
        new AccessorImpl().init();
    }
    
    private InetAddress outgoingAddress;
    
    public static NetworkEnvironment get() {
        return INSTANCE;
    }
    
    /**
     * Returns the most likely address used to communicate with the global network. May return {@code null}
     * if such address cannot be found among the available network interfaces.
     * 
     * @return local outgoing address for communication, or {@code null}.
     */
    public InetAddress getDefaultOutgoingAddress() {
        InetAddress a = outgoingAddress;
        if (a == null) {
             a = LocalAddressUtils.getLocalAddress(null);
        }
        synchronized (this) {
            if (outgoingAddress == null) {
                outgoingAddress = a;
            }
        }
        return a;
    }
    
    private RequestProcessor.Task pendingTask;
    
    /**
     * Performs a check of the network environment. If a change is detected, or is pending, and "wait" is
     * true, the call returns only after all {@link NetworkListeners} for the change have been called.
     * This ensures that any changes or adjustments that may be necessary for the detected network changes 
     * have been performed. The method may {@link NetworkException.UserAborted} indicating that the operation
     * should abort because of user decision, or {@link NetworkException} to indicate a network operation
     * failure.
     * 
     * @param wait true, if the method should wait for the listeners to complete.
     * @return true, if the network conditions changed.
     */
    public boolean checkNetwork(boolean wait) throws NetworkException {
        boolean change = LocalAddressUtils.checkNetworkConnection();
        synchronized (this) {
            if (change) {
                outgoingAddress = null;
            }
        }
        if (!wait) {
            return change;
        }
        return false;
    }
    
    /**
     * Checks the network information on behalf of an executing operation of a service. Similar to
     * {@link #checkNetwork(boolean)}, if the network changes, network listeners
     * 
     * @param operation
     * @return
     * @throws NetworkException 
     */
    public boolean checkOperationNetworkSetup(String service, String operation, String detail) throws NetworkException {
        
    }
    
    /**
     * Flushes cached network data. Optionally reloads the network data and waits for the
     * data to be reloaded.
     * @param wait true, if the data should be reloaded.
     */
    public void flushNetworkData(boolean wait) {
        LocalAddressUtils.refreshNetworkInfo(wait);
    }
    
    /**
     * Adds a network listener to be informed when the network reachability of the selector URL changes. {@code null}
     * selector can be passed, which registers to watch a generic public service (usually Google DNS address, but may change).
     * 
     * @param selector the probe external URL
     * @param listener the change callback
     */
    public void addNetworkListener(String serviceId, URL selector, NetworkListener listener) {
        
    }
    
    /**
     * Removes a previously registered listener.
     * @param listener the listener
     */
    public void removeNetworkListener(NetworkListener listener) {
        
    }
    
    /**
     * Listener that is informed when the network condition may have changed. 
     */
    public interface NetworkListener {
        /**
         * Informs the listener that the preferred network went down. This means the computer
         * does not currently have a connection to the Internet.
         * <p>
         * The machine can still have network connectivity, but the global network may not be reachable.
         * This may be important for services that require public artifact repositories, or cloud search
         * engines.
         * 
         * @param ev event that describes the change
         */
        public default void networkDown(NetworkChangeEvent ev) {}
        
        /**
         * Informs the listener that the preferred network may have changed. This means that listening sockets
         * may need to be rebound, and active connection to external services reconnected.
         * @param ev event that describes the change
         */
        public void networkChangeDetected(NetworkChangeEvent ev);
    }
    
    /**
     * Event that describes the network change. 
     */
    public final class NetworkChangeEvent extends EventObject {
        private final Set<String>  excludes;
        private final InetAddress  outgoingAddress;
        private final InetAddress  previousOutgoingAddress;
        
        NetworkChangeEvent(NetworkEnvironment source, InetAddress out, InetAddress prev, Set<String> excludes) {
            super(source);
            this.excludes = excludes;
            this.outgoingAddress = out;
            this.previousOutgoingAddress = prev;
        }

        /**
         * Returns a set of tokens representing services excluded from the change. This typically indicates the service was already handled.
         * @return services excluded from the change
         */
        public Set<String> getExcludes() {
            return excludes;
        }

        /**
         * 
         * @return The current outgoing address.
         */
        public InetAddress getOutgoingAddress() {
            return outgoingAddress;
        }

        /**
         * 
         * @return the previously active outgoing address.
         */
        public InetAddress getPreviousOutgoingAddress() {
            return previousOutgoingAddress;
        }
    }
    
    private static class AccessorImpl extends NetworkApiAccessor {
        void init() {
            init(this);
        }
    }
}
