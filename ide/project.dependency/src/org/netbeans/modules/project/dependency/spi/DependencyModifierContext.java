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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyChange;
import org.netbeans.modules.project.dependency.DependencyChangeRequest;
import org.netbeans.modules.project.dependency.impl.ProjectModificationResultImpl;

/**
 * Context that accumulates modification information. It can mark operations
 * as "consumed", so they will not be processed by further Modifier implementations.
 * The context also provides access for pending edits made by Modifiers already processed.
 * A Modifier may choose to reject or modify the operation if it cannot adapt its model.
 * <p/>
 * @author sdedic
 */
public final class DependencyModifierContext {
    private final ProjectModificationResultImpl impl;
    private final DependencyChangeRequest request;
    private final List<DependencyChange> changes;
    
    DependencyModifierContext(DependencyChangeRequest request, ProjectModificationResultImpl impl) {
        this.request = request;
        this.impl = impl;
        this.changes = new ArrayList<>(request.getOperations());
    }

    /**
     * Returns the original operation request. 
     * The request contains the complete list of changes originally requested, some of them may be
     * already {@link #consume}d.
     * 
     * @return original modification request.
     */
    public DependencyChangeRequest getRequest() {
        return request;
    }
    
    /**
     * Returns the currently pending edits for the specified file. These are modifications that
     * are proposed for the given file by other Modifiers. The implementation may choose to abort the operation
     * in case it can not adapt to the proposed changes.
     * <p>
     * The set of changes is represented as {@link WorkspaceEdit} as this structure can potentially contain
     * some metadata in the future.
     * 
     * TODO: represent resources using URI, URL or String -- resources may not exist (yet).
     * 
     * @return currently pending edits.
     */
    public WorkspaceEdit getPendingEdits(URI uri) {
        return impl.getWorkspaceEdit(uri);
    }
    
    /**
     * Returns the current list of pending operations. Some of the requested original operations may
     * be already consumed. See {@link DependencyChangeRequest#getOperations()} for the requested list.
     * @return pending operations
     */
    public List<DependencyChange> getPendingOperations() {
        return Collections.unmodifiableList(changes);
    }
    
    /**
     * Consumes a requested change. No further Modifier will get the change to handle. The change must
     * be among the currently {@link #getPendingOperations}. 
     * @param chg the change.
     * @param dep the dependency that is consumed
     */
    public void consume(DependencyChange chg, Dependency... consumed) {
        int index = this.changes.indexOf(chg);
        if (index == -1) {
            throw new IllegalArgumentException("Dependency change not part of operation: " + chg);
        }
        if (consumed != null && consumed.length > 0) {
            Set<Dependency> chgDeps = new HashSet<>(Arrays.asList(consumed));
            chgDeps.removeAll(chg.getDependencies());
            if (!chgDeps.isEmpty()) {
                throw new IllegalArgumentException("Dependencies not part of the change: " + chgDeps);
            }
            this.changes.set(index, DependencyChange.clone(chg, consumed).create());
        } else {
            changes.remove(chg);
        }
    }
    
    /**
     * Adds, removes or replaces a Dependency change. Can be called in {@link ProjectDependencyModifier#prepareChange} and will
     * be visible for <b>all modifier implementations</b>. If the method is called during {@link ProjectDependencyModifier#computeChanges}, 
     * it will only affect the rest of Modifiers.
     * <p>
     * The call can remove, add or replace operations. If {@code remove} is {@code null}, nothing is removed. Similarly, if {@code add}
     * is empty or null, nothing is added. If items are added, they are added at the specified {@code position} in the <b>original</b>
     * operation order. 
     * <p>
     * If {@code position} is -1, new items are added instead of the removed DependencyChange. If no DependencyChange is removed, they
     * are added at the end of the operation list.
     * 
     * @param remove removes a change from the {@link #getPendingOperations()}. Pass {@code null} for no removal
     * @param add zero or more changes to add
     * @param position position where the changes should be added. -1 means add *in place* of the removed item, or at the end of the list.
     */
    public void change(@NullAllowed DependencyChange remove, int position, DependencyChange... add) {
        int index = -1;
        
        if (remove != null) {
            index = changes.indexOf(remove);
            if (index == 1) {
                throw new IllegalArgumentException("Change not present: " + remove);
            }
            changes.remove(index);
            if (position > index) {
                position--;
            }
        }
        if (add == null || add.length == 0) {
            return;
        }
        if (index == -1) {
            index = position;
        }
        if (index == -1) {
            index = changes.size();
        }
        changes.addAll(index, Arrays.asList(add));
    }
}
