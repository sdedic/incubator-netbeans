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
package org.netbeans.modules.cloud.common.explorer;

import java.util.HashMap;
import java.util.Map;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Jan Horvath
 */
public class ItemLoaderFactory {
    
    private static ItemLoaderFactory instance = null;
    
    private Map<String, Lookup> lookups = new HashMap<> ();
    
    public static synchronized ItemLoaderFactory getDefault() {
        if (instance == null) {
            instance = new ItemLoaderFactory();
        }
        return instance;
    }

    private ItemLoaderFactory() {
    }
    
    private ItemLoader findLoaderForPath(String path) {
        Lookup lkp = lookups.get(path);
        if (lkp == null) {
            lkp = Lookups.forPath(String.format("Cloud/%s/Nodes", path));
            lookups.put(path, lkp);
        }
        return lkp.lookup(ItemLoader.class);
    }
    
    public CloudItemKey fromPersistent(String path, String persistedKey) {
        ItemLoader loader = findLoaderForPath(path);
        if (loader != null) {
            return loader.fromPersistentForm(persistedKey);
        }
        return null;
    }
    
    public CloudItem loadItem(CloudItemKey key) {
        ItemLoader loader = findLoaderForPath(key.getPath());
        if (loader != null) {
            return loader.loadItem(key);
        }
        return null;
    }
    
}
