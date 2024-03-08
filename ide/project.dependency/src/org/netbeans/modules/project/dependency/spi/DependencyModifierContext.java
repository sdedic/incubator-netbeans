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
import java.util.Collections;
import java.util.List;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.project.dependency.DependencyChange;
import org.netbeans.modules.project.dependency.DependencyChangeRequest;
import org.netbeans.modules.project.dependency.impl.ProjectModificationResultImpl;

/**
 * Context that accumulates modification information. It can mark operations
 * as "consumed", so they will not be processed by further Modifier implementations.
 * The context also provides access for pending edits made by Modifiers already processed.
 * A Modifier may choose to reject or modify the operation if it cannot adapt its model.
 * 
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
     */
    public void consume(DependencyChange chg) {
        changes.remove(chg);
    }
}
