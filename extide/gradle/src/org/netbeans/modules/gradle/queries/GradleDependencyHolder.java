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
package org.netbeans.modules.gradle.queries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.netbeans.modules.gradle.api.GradleDependency;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.Scope;

/**
 * Simple implementation of a tree node. Captures the dependency path up to the root, which is not (yet)
 * exposed in the {@link Dependency} API objects.
 * 
 * @author sdedic
 */
public final class GradleDependencyHolder {
    private final Scope scope;
    /**
     * The project-specific data
     */
    private final GradleDependency dependency;
    
    /**
     * The parent node, for path construction.
     */
    private final GradleDependencyHolder parent;
    
    public GradleDependencyHolder(GradleDependency dependency, Scope scope, GradleDependencyHolder parent) {
        this.dependency = dependency;
        this.parent = parent;
        this.scope = scope;
    }
    
    public Scope getScope() {
        return scope;
    }

    public GradleDependency getDependency() {
        return dependency;
    }
    
    public GradleDependencyHolder getParent() {
        return parent;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + Objects.hashCode(this.dependency);
        hash = 23 * hash + Objects.hashCode(this.parent);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GradleDependencyHolder other = (GradleDependencyHolder) obj;
        if (!Objects.equals(this.dependency, other.dependency)) {
            return false;
        }
        return Objects.equals(this.parent, other.parent);
    }
    
}
