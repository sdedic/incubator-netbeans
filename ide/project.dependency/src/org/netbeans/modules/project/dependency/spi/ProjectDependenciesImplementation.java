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
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.project.Project;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.openide.util.Lookup;

/**
 *
 * @author sdedic
 */
public interface ProjectDependenciesImplementation {
    // TODO: change to CompletionStage<>, as the implementation is likely to use some dedicated
    // thread to evaluate the project.
    @NonNull
    public DependencyResult findDependencies(@NonNull ProjectDependencies.DependencyQuery query)
            throws ProjectOperationException;
    
    /**
     * If a Dependency has not loaded its children, the ChildProvider is asked to load them.
     * The Provider may be asked more than once to find children for a dependency if concurrent
     * readers attempt to access the children.
     * <p/>
     * Note that calls to Dependency methods may recursively execute {@link ChildProvider} for the
     * instance. Recursive executions on the same instance are not allowed.
     * <p>
     * Instances of this interface should be registered in project's Lookup. The set of instances
     * active during creation of the DependencyResult will be used for all queries on that result.
     */
    public interface ChildProvider {
        /**
         * Returns the set of children for the Dependency instance. The list of children may be cached,
         * and cannot be modified after returning from the method.
         * @param d the dependency
         * @return list of child dependencies, possibly empty list.
         */
        List<Dependency>    findChildren(DependencyResult result, Dependency.Path d);
    }

    /**
     * A factory in project Lookup that provides factory for dependency children. The factory will
     * be asked to create {@link Relations} implementation for the DependencyResult instance,
     * the instance will be remembered and kept alive as long as the DependencyResult. The
     * returned instance may cache data.
     * <p>
     * Instances of this interface should be registered in project's Lookup. 
     */
    public interface ChildProviderFactory {
        public ChildProvider create(Project project, ProjectDependencies.DependencyQuery query);
    }
    
    public interface Result extends Lookup.Provider {
        /**
         * @return True, if the result is still valid.
         */
        public boolean isValid();
        
        /**
         * @return node representing the project. Can be {@code null}.
         */
        @CheckForNull
        public Dependency getRoot();
        
        /**
         * Provider that resolves children of the nodes reported by this implementation.
         * @return 
         */
        public ChildProvider getChildProvider();
        
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
