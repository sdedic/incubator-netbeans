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

import java.io.IOException;

/**
 * Indicates a network-level exception that prevents the query or configuration from completing.
 * A specific subcase is a {@link UserAborted} subclass that indicates that the user aborted the
 * whole operation.
 * 
 * @author sdedic
 */
public class NetworkException extends IOException {
    protected NetworkException(String message) {
        super(message);
    }
    
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }

    public NetworkException(Throwable cause) {
        super(cause);
    }
   
    /**
     * 
     */
    public class UserAborted extends NetworkException {
        public UserAborted(String message) {
            super(message);
        }
    }
}
