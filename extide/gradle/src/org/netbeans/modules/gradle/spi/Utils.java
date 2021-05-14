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

package org.netbeans.modules.gradle.spi;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Laszlo Kishalmi
 */
public final class Utils {

    private Utils() {}

    public static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder(s.length());
        String prepend = "";
        for (String part : parts) {
            sb.append(prepend);
            prepend = " ";
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }

    public static String camelCaseToTitle(String s) {
        if (s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.replaceAll("\\p{Upper}", " $0")); //NOI18N
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }
    
    public InputStream  concatStreams(InputStream... delegates) {
        return new MultiStream(Arrays.asList(delegates));
    }
    
    static final class MultiStream extends FilterInputStream {
        private final List<InputStream> delegates;
        private final Iterator<InputStream> nextDelegate;
        
        MultiStream(List<InputStream> delegates) {
            super(delegates.get(0));
            this.delegates = new ArrayList<>(delegates);
            
            Iterator<InputStream> i = delegates.iterator();
            i.next();
            nextDelegate = i;
        }
        
        public List<InputStream> getParts() {
            return delegates;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (in == null) {
                return -1;
            }
            while (true) {
                int l = super.read(b, off, len);
                if (l != -1) {
                    return l;
                }
                if (nextDelegate.hasNext()) {
                    in = nextDelegate.next();
                } else {
                    break;
                }
            }
            in = null;
            return -1;
        }

        @Override
        public int read() throws IOException {
            if (in == null) {
                return -1;
            }
            while (true) {
                int r = super.read();
                if (r != -1) {
                    return r;
                }
                if (nextDelegate.hasNext()) {
                    in = nextDelegate.next();
                } else {
                    break;
                }
            }
            in = null;
            return -1;
        }
    }
}
