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
package org.netbeans.modules.java.lsp.server.explorer.api;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.netbeans.modules.java.lsp.server.explorer.TreeItem;

/**
 * 
 * @author sdedic
 */
@JsonSegment("nodes")
public interface TreeViewService {
    @JsonNotification(value = "collapsed")
    public void nodesCollapsed(int parentId);

    @JsonRequest(value = "delete")
    public CompletableFuture<Boolean> nodesDelete(int nodeId);

    @JsonRequest(value = "info")
    public CompletableFuture<TreeItem> nodesInfo(int nodeId);

    @JsonRequest(value = "explorermanager")
    public CompletableFuture<TreeItem> explorerManager(String id);

    @JsonRequest(value = "children")
    public CompletableFuture<int[]> getChildren(int id);
    
    @JsonRequest
    public CompletableFuture<Void> configure(ConfigureExplorerParams par);
}
