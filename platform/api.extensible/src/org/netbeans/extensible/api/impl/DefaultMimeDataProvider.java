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

package org.netbeans.extensible.api.impl;

import java.util.Map;
import java.util.WeakHashMap;
import org.netbeans.extensible.api.Composition;
import org.netbeans.extensible.spi.PluginDataProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 *
 * @author vita
 */
@org.openide.util.lookup.ServiceProvider(service=org.netbeans.extensible.spi.PluginDataProvider.class, position=0)
public class DefaultMimeDataProvider implements PluginDataProvider  {
    
    private Map<Composition, SwitchLookup>  lookupCache = new WeakHashMap<>();

    /** Creates a new instance of DefaultMimeDataProvider */
    public DefaultMimeDataProvider() {
        // no-op
    }

    @Override
    public Lookup getLookup(Composition composition) {
        return new SwitchLookup(composition);
    }

    @Override
    public FileObject[] getContent(Composition composition) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
