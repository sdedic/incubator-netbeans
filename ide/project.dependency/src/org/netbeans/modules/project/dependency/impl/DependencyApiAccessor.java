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

import java.util.List;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyChange;
import org.netbeans.modules.project.dependency.ProjectScopes;
import org.netbeans.modules.project.dependency.spi.ProjectDependencyScopes;

/**
 *
 * @author sdedic
 */
public abstract class DependencyApiAccessor {
    private static volatile DependencyApiAccessor INSTANCE;
    
    public static void set(DependencyApiAccessor a) {
        assert INSTANCE == null;
        INSTANCE = a;
    }
    
    public static DependencyApiAccessor get() {
        return INSTANCE;
    }
    
    public abstract ProjectScopes createScopes(List<ProjectDependencyScopes> implementations);
    public abstract void remove(DependencyChange chg, Dependency d);
}
