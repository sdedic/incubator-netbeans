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
package org.netbeans.modules.cloud.oracle.devops;


import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.Project;
import com.oracle.bmc.devops.model.ProjectSummary;
import com.oracle.bmc.devops.requests.GetProjectRequest;
import com.oracle.bmc.devops.requests.ListProjectsRequest;
import com.oracle.bmc.devops.responses.GetProjectResponse;
import com.oracle.bmc.devops.responses.ListProjectsResponse;
import com.oracle.bmc.model.BmcException;
import java.util.List;
import java.util.stream.Collectors;
import org.netbeans.modules.cloud.common.explorer.NodeProvider;
import org.netbeans.modules.cloud.oracle.OCIManager;
import org.netbeans.modules.cloud.oracle.items.OCID;
import org.netbeans.modules.cloud.oracle.items.OCIItem;
import org.netbeans.modules.cloud.common.explorer.ChildrenProvider;
import org.netbeans.modules.cloud.common.explorer.CloudItem;
import org.netbeans.modules.cloud.common.explorer.ItemLoader;
import org.netbeans.modules.cloud.oracle.OCINode;
import org.netbeans.modules.cloud.oracle.compartment.CompartmentItem;
import org.openide.util.Exceptions;

/**
 *
 * @author Jan Horvath
 */

public class DevopsProjectNode extends OCINode {
    
    private static final String DB_ICON = "org/netbeans/modules/cloud/oracle/resources/devops_project.svg"; // NOI18N
    
    public DevopsProjectNode(OCIItem item) {
        super(item);
        setName(item.getName()); 
        setDisplayName(item.getName());
        setIconBaseWithExtension(DB_ICON);
        setShortDescription(item.getDescription());
    }
    
    @NodeProvider.Registration(path = "Oracle/DevopsProject")
    public static NodeProvider<OCIItem> createNode() {
        return DevopsProjectNode::new;
    }
   
    @ChildrenProvider.Registration(parentPath = "Oracle/Compartment")
    public static ChildrenProvider<CompartmentItem, DevopsProjectItem> listDevopsProjects() {
        return compartmentId -> {
            try (
                DevopsClient client = new DevopsClient(OCIManager.getDefault().getConfigProvider());
            ) {
                ListProjectsRequest request = ListProjectsRequest.builder().compartmentId(compartmentId.getKey().getValue()).build();
                ListProjectsResponse response = client.listProjects(request);

                List<ProjectSummary> projects = response.getProjectCollection().getItems();
                for (ProjectSummary project : projects) {
                    project.getNotificationConfig().getTopicId();
                    
                }
                return projects.stream().map(p -> new DevopsProjectItem(OCID.of(p.getId(), "Oracle/DevopsProject"), 
                        p.getName())).collect(Collectors.toList());
            }
        };
    }
    
    @ItemLoader.Registration(path = "Oracle/DevopsProject")
    public static class DevopsLoader implements ItemLoader<OCID> {

        @Override
        public CloudItem loadItem(OCID key) {
            try (DevopsClient client = new DevopsClient(OCIManager.getDefault().getConfigProvider())) {
                GetProjectRequest request = GetProjectRequest.builder().projectId(key.getValue()).build();
                GetProjectResponse response = client.getProject(request);
                Project project = response.getProject();
                return new DevopsProjectItem(key, project.getName());
            } catch(BmcException e) {
                Exceptions.printStackTrace(e);
            }
            return null;
        }

        @Override
        public OCID fromPersistentForm(String persistedKey) {
            return OCID.of(persistedKey, "Oracle/DevopsProject");
        }

    }
    
}