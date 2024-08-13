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


import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyChangeRequest;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.netbeans.modules.project.dependency.ProjectScopes;
import org.netbeans.modules.project.dependency.ProjectSpec;
import org.netbeans.modules.project.dependency.impl.DependencyResultContextImpl;
import org.netbeans.modules.project.dependency.impl.DependencySpiAccessor;
import org.netbeans.modules.project.dependency.impl.ProjectModificationResultImpl;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 *
 * @author sdedic
 */
public interface ProjectDependenciesImplementation {
    
    /**
     * Returns evaluation order of this Implementation. The default implementation
     * for the project system should use the default, 10000. Other implementations may choose
     * its relative position before/after to intercept or augment the requests.
     * 
     * @return order of the implementation.
     */
    public default int getOrder() {
        return 10000;
    }

    // TODO: change to CompletionStage<>, as the implementation is likely to use some dedicated
    // thread to evaluate the project.
    @NonNull
    public Result findDependencies(@NonNull ProjectDependencies.DependencyQuery query, Context context)
            throws ProjectOperationException;
    
    /**
     * Returns {@link Scope scopes} or their relationships this implementation defines. The returned
     * information will be merged with all other providers to create {@link ProjectScopes}
     * @return project scopes, or {@code null}
     */
    @CheckForNull 
    public ProjectDependencyScopes findProjectScopes();
    
    /**
     * Response object that will accumulate data from {@link #findDependencies} implementations.
     * The {@link ProjectDependenciesImplementation} must use this object to collect children, problems
     * and files for the {@link DependencyResult} that will merge the info from multiple providers.
     */
    public static final class Context {
        private final DependencyResultContextImpl impl;
        private final ProjectDependencies.DependencyQuery query;
        
        private Context(ProjectDependencies.DependencyQuery query, DependencyResultContextImpl impl) {
            this.query = query;
            this.impl = impl;
        }
        
        /**
         * Returns the Lookup constructed so far from the partial results.
         * @return composed lookup snapshot
         */
        public Lookup getLookup() {
            return impl.getLookup();
        }
        
        public ProjectScopes getScopes() {
            return impl.getScopes();
        }
        
        public ProjectDependencies.DependencyQuery getQuery() {
            return query;
        }
        
        /**
         * Reports a problem related to an artifact.
         * @param spec artifact specification
         */
        public void reportArtifactProblem(ArtifactSpec spec) {
            impl.addProblemArtifact(spec);
        }
        
        /**
         * Reports a file in the project contents, that affects dependencies.
         * @param f the file.
         */
        public void addDependencyFile(@NonNull FileObject f) {
            impl.addDependencyFile(f);
        }
        
        /**
         * Sets the project artifact. The artifact can be used to depend on the project
         * in other projects, or to identify project using project system coordinates.
         * @param artifact project description artifact.
         */
        public void setProjectArtifact(@NonNull ArtifactSpec artifact) {  
            impl.setProjectArtifact(artifact);
        }
        
        /**
         * Sets the project specification. The specification may be used to depend on
         * the project from other projects in the workspace or within the same root project.
         * @param spec project specification.
         */
        public void setProjectSpec(@NonNull ProjectSpec spec) {
            impl.setProjectSpec(spec);
        }
        
        /**
         * Adds children that will be reported directly under project root node.
         * @param rootChildren list of children.
         */
        public void addRootChildren(@NonNull List<Dependency> rootChildren) {
            impl.addRootChildren(rootChildren);
        }
        
        /**
         * Registers a location provider. Additional {@link DependencyLocationProvider}s can
         * be provided from factories in the project Lookup.
         * @param provider location provider
         */
        public void addLocationProvider(@NonNull DependencyLocationProvider provider) {
            impl.addLocationProvider(provider);
        }
        
        static {
            DependencySpiAccessor.set(new DependencySpiAccessor() {
                @Override
                public void addProjectStateListener(ProjectReloadImplementation.ProjectStateData data, ChangeListener l) {
                    data.addListener(l);
                }
                
                @Override
                public Context createContextImpl(ProjectDependencies.DependencyQuery query, DependencyResultContextImpl impl) {
                    return new Context(query, impl);
                }

                @Override
                public DependencyModifierContext createModifierContext(DependencyChangeRequest req, ProjectModificationResultImpl impl) {
                    return new DependencyModifierContext(req, impl);
                }
            });
        }
    }
    
    /**
     * Data holder and service accessor returned from {@link #findDependencies}. The implementation
     * must implement listener management. Additional services may be provided from {@link Lookup.Provider},
     * to interface with other possible plug-ins.
     * <p/>
     * If the status or other property of the Result changes, a {@link ChangeEvent} should be broadcasted to
     * registered {@link ChangeListener}s by the implementation.
     */
    public interface Result extends Lookup.Provider {
        /**
         * @return True, if the result is still valid. 
         */
        public boolean isValid();

        /**
         * Adds a listener that receives dependency change notification.
         * @param l listener instance
         */
        public void addChangeListener(ChangeListener l);

        /**
         * Removes a previously registered listener.
         * @param l listener instance
         */
        public void removeChangeListener(ChangeListener l);
    }
}
