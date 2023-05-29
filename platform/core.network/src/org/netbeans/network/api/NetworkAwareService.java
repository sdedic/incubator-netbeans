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

/**
 * Initializes a network-aware service with a network environment instance. The 
 * implementation can hook a {@link NetworkEnvironment.NetworkListener} to the
 * service.
 * 
 * @author sdedic
 */

public interface NetworkAwareService {
    /**
     * Initializes the service, so the service can start watching out for changes.
     * @param environment the network environment
     */
    public void attach(NetworkEnvironment environment);
    
    /**
     * Called when the service should stop monitoring the network changes.
     * @param environment the environment to detach from.
     */
    public void detach(NetworkEnvironment environment);
}
