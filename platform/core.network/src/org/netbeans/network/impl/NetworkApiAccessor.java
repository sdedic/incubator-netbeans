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
package org.netbeans.network.impl;

import org.netbeans.network.api.NetworkEnvironment;

/**
 *
 * @author sdedic
 */
public abstract class NetworkApiAccessor {
    private static NetworkApiAccessor INSTANCE;
    
    static {
        NetworkEnvironment.get();
    }
    
    protected static void init(NetworkApiAccessor inst) {
        synchronized (NetworkApiAccessor.class) {
            if (INSTANCE != null) {
                throw new IllegalStateException();
            }
            INSTANCE = inst;
        }
    }
}
