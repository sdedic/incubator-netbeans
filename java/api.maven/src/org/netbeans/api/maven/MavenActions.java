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
package org.netbeans.api.maven;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import static org.netbeans.modules.maven.spi.actions.AbstractMavenActionsProvider.fromResource;
import org.netbeans.spi.project.LookupProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author sdedic
 */
public final class MavenActions {
    
    static LookupProvider forProjectLayer(FileObject fromLayer) {
        Object o = fromLayer.getAttribute("resource");
        URL resourceURL = null;
        
        if (o instanceof URL) {
            resourceURL = (URL)o;
        } else if (o instanceof String) {
            try {
                resourceURL = new URL(URLMapper.findURL(fromLayer, URLMapper.INTERNAL), (String)o);
            } catch (MalformedURLException ex) {
                Logger.getLogger(MavenActions.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        final URL finURL = resourceURL;
        return new LookupProvider() {
            @Override
            public Lookup createAdditionalLookup(Lookup baseContext) {
                Project p = baseContext.lookup(Project.class);
                if (p == null || finURL == null) {
                    return Lookup.EMPTY;
                }
                return Lookups.fixed(
                        fromResource(p, finURL)
                );
            }
        };
    }
    
}
