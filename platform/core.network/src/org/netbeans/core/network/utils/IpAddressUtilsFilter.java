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
package org.netbeans.core.network.utils;

import org.netbeans.network.api.IpTypePreference;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.annotations.common.NonNull;

/**
 * Package private methods for choosing IP addresses from a
 * list based on a stated preference.
 * 
 * @author lbruun
 */
class IpAddressUtilsFilter {

    private static final boolean JDK_PREFER_IPV6_ADDRESS;
    static {
        JDK_PREFER_IPV6_ADDRESS = java.security.AccessController.doPrivileged(
                new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                return Boolean.getBoolean("java.net.preferIPv6Addresses");

            }
        });
    }
    private IpAddressUtilsFilter() {}
    
    protected static InetAddress pickInetAddress(Iterable<InetAddress> sortedList, IpTypePreference ipTypePref) {
        IpTypePreference pref = getIpTypePreferenceResolved(ipTypePref);
        for (InetAddress ipAddress : sortedList) {
            if (pref == IpTypePreference.ANY_IPV4_PREF  || pref == IpTypePreference.ANY_IPV6_PREF) {
                return ipAddress;
            }
            if (ipAddress instanceof Inet4Address) {
                if (pref == IpTypePreference.IPV4_ONLY) {
                    return ipAddress;
                }
            }
            if (ipAddress instanceof Inet6Address) {
                if (pref == IpTypePreference.IPV6_ONLY) {
                    return ipAddress;
                }
            }
        }
        return null;
    }
    
    protected static @NonNull List<InetAddress> filterInetAddresses(Iterable<InetAddress> list, IpTypePreference ipTypePref) {
        IpTypePreference pref = getIpTypePreferenceResolved(ipTypePref);
        List<InetAddress> newList = new ArrayList<>();
        if (list != null) {
            for (InetAddress ipAddress : list) {
                if (pref == IpTypePreference.ANY_IPV4_PREF || pref == IpTypePreference.ANY_IPV6_PREF) {
                    newList.add(ipAddress);
                } else {
                    if ((ipAddress instanceof Inet4Address) && (pref == IpTypePreference.IPV4_ONLY)) {
                        newList.add(ipAddress);
                    }
                    if ((ipAddress instanceof Inet6Address) && (pref == IpTypePreference.IPV6_ONLY)) {
                        newList.add(ipAddress);
                    }
                }
            }
        }
        if (pref == IpTypePreference.ANY_IPV4_PREF) {
            IpAddressUtils.sortIpAddressesShallow(newList,true);
        }
        if (pref == IpTypePreference.ANY_IPV6_PREF) {
            IpAddressUtils.sortIpAddressesShallow(newList,false);
        }
        return newList;
    }

    private static IpTypePreference getIpTypePreferenceResolved(IpTypePreference ipTypePref) {
        if (ipTypePref == IpTypePreference.ANY_JDK_PREF) {
            if (JDK_PREFER_IPV6_ADDRESS) {
                return IpTypePreference.ANY_IPV6_PREF;
            } else {
                return IpTypePreference.ANY_IPV4_PREF;
            }
        } else {
            return ipTypePref;
        }
    }    
}
