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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.netbeans.extensible.api.Composition;
import org.netbeans.extensible.spi.InstanceProvider;
import org.netbeans.extensible.spi.PluginLocation;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author vita
 */
public class SwitchLookup extends Lookup {

    private static final Logger LOG = Logger.getLogger(SwitchLookup.class.getName());
    
    /* package */ static final String ROOT_FOLDER = "Editors"; //NOI18N

    private final Composition mimePath;

    private final String LOCK = new String("SwitchLookup.LOCK"); //NOI18N
    
    private Map<Class<?>,Lookup> classLookups = new HashMap<Class<?>, Lookup>();
    private Map<List<String>,Lookup> pathsLookups = new HashMap<List<String>,Lookup>();

    public SwitchLookup(Composition mimePath) {
        super();
        this.mimePath = mimePath;
    }

    public <T> Lookup.Result<T> lookup(Lookup.Template<T> template) {
        return findLookup(template.getType()).lookup(template);
    }

    public <T> T lookup(Class<T> clazz) {
        return findLookup(clazz).lookup(clazz);
    }

    private Lookup findLookup(Class<?> clazz) {
        synchronized (LOCK) {
            Lookup lookup = classLookups.get(clazz);
            if (lookup == null) {
                // Create lookup
                lookup = createLookup(clazz);
                classLookups.put(clazz, lookup);
            }
            return lookup;
        }
    }

    private Lookup createLookup(Class<?> forClass) {
        PluginLocation loc = forClass.getAnnotation(PluginLocation.class);

        if (loc == null) {
            loc = new PluginLocation() {
                @Override
                public String subfolderName() {
                    return null;
                }
                @Override
                public Class<? extends InstanceProvider> instanceProviderClass() {
                    return null;
                }

                @Override
                public Class<? extends Annotation> annotationType() {
                    return PluginLocation.class;
                }
            };
        }
        List<String> paths = computePaths(mimePath, loc.subfolderName());
        Lookup lookup;
        
        if (loc.instanceProviderClass() != null && loc.instanceProviderClass() != InstanceProvider.class) {
            try {
                // Get a lookup for the new instance provider
                lookup = getLookupForProvider(paths, loc.instanceProviderClass().newInstance());
            } catch (InstantiationException ex) {
                Exceptions.printStackTrace(ex);
                lookup = Lookup.EMPTY;
            } catch (IllegalAccessException ex) {
                Exceptions.printStackTrace(ex);
                lookup = Lookup.EMPTY;
            }
        } else {
            // Get a lookup for the new paths
            lookup = getLookupForPaths(paths);
        }
        
        return lookup;
    }
    
    private Lookup getLookupForPaths(List<String> paths) {
        Lookup lookup = pathsLookups.get(paths);
        if (lookup == null) {
            lookup = new FolderPathLookup(paths.toArray(new String[paths.size()]));
            pathsLookups.put(paths, lookup);
        }
        
        return lookup;
    }

    private Lookup getLookupForProvider(List<String> paths, InstanceProvider instanceProvider) {
        return new InstanceProviderLookup(mimePath.getBaseContext(), paths.toArray(new String[paths.size()]), instanceProvider);
    }
    
    private static List<String> computePaths(Composition mimePath, String suffixPath) {
        if (suffixPath == null) {
            return mimePath.getComponents();
        } else {
            return mimePath.getComponents().stream().map(c -> c + "/" + suffixPath).collect(Collectors.toList());
        }
    }
    
}
