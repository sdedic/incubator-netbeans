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
package org.netbeans.modules.project.dependency;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A request to resolve a dependency within the project. A Dependency represents an
 * item that is present or should be added to the project, and is potentially remote. 
 * The dependency may have version unset, or set inexact as a range, as the project 
 * system permits. The resolution should precise the version, check if the requested
 * version satisfies other project requirements and make the artifact available with
 * its possible dependencies.
 * 
 * @author sdedic
 */
public final class ResolveDependencyRequest {
    /**
     * Dependencies to resolve.
     */
    private final List<Dependency> dependencies;
    
    /**
     * Permit online operations to fetch data and artifact indexes.
     */
    private final boolean online;
    
    /**
     * Recursively resolve dependent artifacts.
     */
    private final boolean recursive;
    
    ResolveDependencyRequest(List<Dependency> dependencies, boolean online, boolean recursive) {
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.online = online;
        this.recursive = recursive;
    }
    
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public boolean isOnline() {
        return online;
    }
    
    /**
     * Convenience helper method. Creates a Request to resolve a single artifact for the given scope, recursively and online.
     * @param spec artifact specification.
     * @param scope the desired scope. {@code null} is interpreted as {@link Scopes#COMPILE}.
     * @return constructed request.
     */
    public static ResolveDependencyRequest buildArtifactRequest(ArtifactSpec spec, Scope scope) {
        return new ResolveDependencyRequest(List.of(Dependency.make(spec, scope)), true, true);
    }
}
