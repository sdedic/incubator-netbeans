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
import java.util.Collections;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.project.dependency.DependencyChangeException;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Computes dependency modifications to project files. Must be registered in the project's
 * Lookup. More implementation can be registered for a project; they take precedence
 * in the order of the project's Lookup. Modifiers can exclude other modifier's work, even though
 * they come later in the chain. 
 * <p>
 * The Modifier may request that some or all modified files are saved after the operation. It must 
 * either specify FileObjects (can use virtual ones for files that do not exist yet), or 
 * use {@link Result#SAVE_ALL} special instance to indicate that all files should be saved.
 * <p>
 * The implementation <b>must call</b> {@link DependencyModifierContext#consume} for each {@link DependencyChannge}
 * it accepts and processes. If a DependencyChange is not marked as consumed, the modify operation fails with
 * {@link DependencyChangeException} with reason {@link DependencyChangeException.Reason#UNSUPPORTED}.
 * @since 1.7
 * @author sdedic
 */
public interface ProjectDependencyModifier<T> {
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

    /**
     * Computes changes to project files that apply the dependency change. The implementation may
     * consume some of the {@link DependencyChange}s, so that lower-priority Modifiers will not process it.
     * 
     * @param context context of the modification
     * @return result of the operation
     * @throws DependencyChangeException if the dependencies cannot be changed
     * @throws ProjectOperationException on general project system error conditions
     */
    @NonNull
    public Result   computeChange(DependencyModifierContext context) throws DependencyChangeException;
    
    /**
     * Prepares the modifier context for modifications. The returned value will be merged with other Lookups
     * into {@link DependencyModifierContext#getLookup}.
     * 
     * @param context the modification context
     * @return Lookup or {@code null}. The default implementation returns {@code null}.
     * @throws DependencyChangeException to abort the operation.
     */
    @CheckForNull
    public default Lookup prepareChange(DependencyModifierContext context) throws DependencyChangeException {
        return null;
    }
    
    /**
     * Result of dependency modification change. The Result must describe all edit / create / delete
     * operations that result from the modification request. 
     */
    public interface Result {
        /**
         * A special collection instance that indicates that all resources changed by the result's
         * {@link #getWorkspaceEdit()} must be saved to update the project infrastructure.
         */
        public static final Collection<FileObject> SAVE_ALL = Collections.singleton(null);
        
        /**
         * Returns list of files that require save. Until these modified files are saved,
         * the project infrastructure does not reflect the changes.
         * 
         * @return files to save.
         */
        @CheckForNull
        public default Collection<FileObject> requiresSave() {
            return SAVE_ALL;
        }
        
        /**
         * Computes edits that make the change. 
         * @return edits that implement the requested dependency change.
         */
        @NonNull
        public WorkspaceEdit getWorkspaceEdit();
    }
}
