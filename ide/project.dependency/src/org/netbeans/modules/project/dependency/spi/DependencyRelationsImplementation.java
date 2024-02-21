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
package org.netbeans.modules.project.dependency.spi;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.netbeans.api.project.Project;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.Relation;

/**
 * Optional interface. Service that searches for related dependencies. A dependency indicate it is
 * a duplicate to another one, or that it is a resolved form of some incomplete or range declaration,
 * or that it is constrained by some other declaration. 
 * <p>
 * It is a service that can be provided on {@link DependencyResult#getLookup()} to augment dependencies
 * provided by the Dependency API. Alternatively, it can be provided through a factory in the
 * project's Lookup.
 * 
 * @author sdedic
 */
public interface DependencyRelationsImplementation {
    /**
     * Attempts to find a dependency related to this one. Relation kind may be one of the predefined
     * constants, or implementation-specific values.
     * @param kind type of relation
     * @param origin the dependency whose relations should be returned.
     * @return list of relations, or {@code null}, if relation is not supported.
     */
    public List<Relation> getRelated(Set<String> kind, DependencyResult result, Dependency origin);

    /**
     * Enumerates all relations supported by the implementation.
     * @return supported relation tokens.
     */
    public Collection<String> supportedRelations();
    
    /**
     * A factory in project Lookup that provides factory for relations. The factory will
     * be asked to create {@link Relations} implementation for the DependencyResult instance,
     * the instance will be remembered and kept alive as long as the DependencyResult. The
     * returned instance may cache data.
     * <p>
     * Instances of this interface should be registered in project's Lookup. 
     */
    public interface Factory {
        public DependencyRelationsImplementation create(Project project, ProjectDependencies.DependencyQuery query);
    }
}
