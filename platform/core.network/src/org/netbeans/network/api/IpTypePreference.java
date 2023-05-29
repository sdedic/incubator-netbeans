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
 * Filters the result of a method according to IP protocol preference.
 */
public enum IpTypePreference {
    /**
     * Only IPv4 address(es) in the returned value.
     */
    IPV4_ONLY, 
    
    /**
     * Only IPv6 address(es) in the returned value.
     */ 
    IPV6_ONLY, 
    
    /**
     * Any of IPv4 or IPv6 addresses are acceptable in the returned value,
     * but IPv4 address is preferred over IPv6. If the method returns
     * an array then IPv4 addresses will come before IPv6 addresses.
     */ 
    ANY_IPV4_PREF, 
    
    /**
     * Any of IPv4 or IPv6 addresses are acceptable in the returned value,
     * but IPv6 address is preferred over IPv4. If the method returns
     * an array then IPv6 addresses will come before IPv4 addresses.
     */ 
    ANY_IPV6_PREF, 
    
    /**
     * Any of IPv4 or IPv6 addresses are acceptable in the returned value,
     * but their internal preference is determined by the setting in the
     * JDK, namely the {@code java.net.preferIPv6Addresses} system property.
     * If this property is {@code true} then using this preference will be
     * exactly as {@link #ANY_IPV6_PREF}, if {@code false} it will be
     * exactly as {@link #ANY_IPV4_PREF}.
     */ 
    ANY_JDK_PREF
}
