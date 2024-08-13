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
package org.netbeans.modules.project.dependency.impl;

import javax.swing.event.ChangeListener;
import org.netbeans.modules.project.dependency.DependencyChangeRequest;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.spi.DependencyModifierContext;
import org.netbeans.modules.project.dependency.spi.ProjectDependenciesImplementation;
import org.netbeans.modules.project.dependency.spi.ProjectReloadImplementation.ProjectStateData;

/**
 *
 * @author sdedic
 */
public abstract class DependencySpiAccessor {
    private static volatile DependencySpiAccessor INSTANCE;
    
    static {
        Class c = ProjectDependenciesImplementation.Context.class;
        try {
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        assert INSTANCE != null;
    }

    public static void set(DependencySpiAccessor a) {
        assert INSTANCE == null;
        INSTANCE = a;
    }
    
    public static DependencySpiAccessor get() {
        return INSTANCE;
    }
    
    public abstract void addProjectStateListener(ProjectStateData data, ChangeListener l);
    
    public abstract DependencyModifierContext createModifierContext(DependencyChangeRequest req, ProjectModificationResultImpl impl);
    
    public abstract ProjectDependenciesImplementation.Context createContextImpl(ProjectDependencies.DependencyQuery query, DependencyResultContextImpl impl);
}
