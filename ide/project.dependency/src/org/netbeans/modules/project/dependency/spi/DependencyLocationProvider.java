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

import java.io.IOException;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.SourceLocation;

/**
 * Attempts to map a Dependency to the location in the project metadata files. Implementations
 * can be provided by {@link ProjectDependenciesImplementation.Result} itself,
 * {@link ProjectDependenciesImplementation.Result#getLookup()} or through {@link DependencyLocationProvider.ProjectFactory}.
 * An instance is always tied to the particular dependency result. It may cache data for it.
 * @author sdedic
 */
public interface DependencyLocationProvider {
    public SourceLocation getDeclarationRange(@NonNull DependencyResult result, @NonNull Dependency d, String part) throws IOException;
    /**
     * Adds a listener that receives dependency change notification.
     * @param l listener instance
     */
    public void addSourceChangeListener(ChangeListener l);

    /**
     * Removes a previously registered listener.
     * @param l listener instance
     */
    public void removeSourceChangeListener(ChangeListener l);
    
    
    /**
     * Factory for {@link DependencyLocationProvider}, that can be placed in the project Lookup.
     * These providers are collected in the lookup order and placed <b>after</b> any providers contributed
     * by {@link ProjectDependenciesImplementation} registered for the project.
     */
    public interface ProjectFactory {
        /**
         * Creates a provider, which maps DependencyResult information to the source locations.
         * The factory can return {@code null}.
         * @param result the constructed dependency result.
         * @return provider instance of {@code null}
         */
        public @CheckForNull DependencyLocationProvider createLocationProvider(@NonNull DependencyResult result);
    }
}
