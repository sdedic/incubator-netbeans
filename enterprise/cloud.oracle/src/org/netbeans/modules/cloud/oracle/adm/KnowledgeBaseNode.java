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
package org.netbeans.modules.cloud.oracle.adm;

import com.oracle.bmc.adm.ApplicationDependencyManagementClient;
import com.oracle.bmc.adm.model.KnowledgeBaseSummary;
import com.oracle.bmc.adm.requests.ListKnowledgeBasesRequest;
import com.oracle.bmc.adm.responses.ListKnowledgeBasesResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.netbeans.modules.cloud.common.explorer.ChildrenProvider;
import org.netbeans.modules.cloud.common.explorer.CloudNode;
import org.netbeans.modules.cloud.common.explorer.NodeProvider;
import org.netbeans.modules.cloud.oracle.OCIManager;
import org.netbeans.modules.cloud.oracle.compartment.CompartmentItem;
import org.netbeans.modules.cloud.oracle.items.OCID;
import org.netbeans.modules.cloud.oracle.items.OCIItem;
import org.openide.nodes.Children;

/**
 *
 * @author Jan Horvath
 */
public class KnowledgeBaseNode extends CloudNode {

    private static final String ICON = "org/netbeans/modules/cloud/oracle/resources/knowledge_base.svg"; // NOI18N

    public KnowledgeBaseNode(OCIItem item) {
        super(item, Children.LEAF);
        setIconBaseWithExtension(ICON);
    }

     @NodeProvider.Registration(path = "Oracle/KnowledgeBase")
    public static NodeProvider<KnowledgeBaseItem> createNode() {
        return KnowledgeBaseNode::new;
    }

    @ChildrenProvider.Registration(parentPath = "Oracle/Compartment")
    public static ChildrenProvider<CompartmentItem, KnowledgeBaseItem> listKnowledgeBases() {
        return compartment -> {
            try ( ApplicationDependencyManagementClient client 
                    = new ApplicationDependencyManagementClient(OCIManager.getDefault().getConfigProvider())) {
                
                ListKnowledgeBasesRequest request = ListKnowledgeBasesRequest.builder()
                        .compartmentId(compartment.getKey().getValue()).build();
                ListKnowledgeBasesResponse response = client.listKnowledgeBases(request);
                List<KnowledgeBaseSummary> projects = response.getKnowledgeBaseCollection().getItems();
                return projects.stream().map(p -> new KnowledgeBaseItem(OCID.of(p.getId(), "Oracle/KnowledgeBase"), // NOI18N 
                        p.getDisplayName())).collect(Collectors.toList());
            }
        };
    }
    
}
