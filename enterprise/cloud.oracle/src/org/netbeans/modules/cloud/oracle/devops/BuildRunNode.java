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
import com.oracle.bmc.devops.model.BuildRunSummary;
import com.oracle.bmc.devops.requests.ListBuildRunsRequest;
import com.oracle.bmc.devops.responses.ListBuildRunsResponse;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.netbeans.modules.cloud.common.explorer.ChildrenProvider;
import org.netbeans.modules.cloud.common.explorer.CloudNode;
import org.netbeans.modules.cloud.common.explorer.NodeProvider;
import org.netbeans.modules.cloud.oracle.OCIManager;
import org.netbeans.modules.cloud.oracle.items.OCID;
import org.netbeans.modules.cloud.oracle.items.OCIItem;
import org.openide.nodes.Children;
import org.openide.util.NbBundle;

/**
 *
 * @author Jan Horvath
 */
@NbBundle.Messages({
    "BuildRuns=Builds",})
public class BuildRunNode extends CloudNode {

    private static final String ICON = "org/netbeans/modules/cloud/oracle/resources/build_run.svg"; // NOI18N

    public BuildRunNode(OCIItem item) {
        super(item, Children.LEAF);
        setIconBaseWithExtension(ICON);
    }

    @NodeProvider.Registration(path = "Oracle/BuildRun")
    public static NodeProvider<BuildRunItem> createNode() {
        return BuildRunNode::new;
    }

    @ChildrenProvider.Registration(parentPath = "Oracle/DevopsProject")
    public static ChildrenProvider<DevopsProjectItem, BuildRunItem.BuildRunFolder> listBuildRuns() {
        return project -> {
            try ( DevopsClient client = new DevopsClient(OCIManager.getDefault().getConfigProvider())) {
                ListBuildRunsRequest request = ListBuildRunsRequest.builder()
                        .projectId(project.getKey().getValue()).build();
                ListBuildRunsResponse response = client.listBuildRuns(request);
                List<BuildRunSummary> projects = response.getBuildRunSummaryCollection().getItems();
                return Collections.singletonList(
                        new BuildRunItem.BuildRunFolder(OCID.of(project.getKey().getValue(), "Oracle/BuildRunFolder"),
                                Bundle.BuildRuns(),
                                projects.stream()
                                        .map(p -> new BuildRunItem(OCID.of(p.getId(), "Oracle/BuildRun"), p.getDisplayName()))
                                        .collect(Collectors.toList()))
                );
            }
        };
    }

    public static class BuildRunFolderNode extends CloudNode {

        private static final String ICON = "org/netbeans/modules/cloud/oracle/resources/build_run_folder.svg"; // NOI18N

        public BuildRunFolderNode(BuildRunItem.BuildRunFolder folder) {
            super(folder);
            setIconBaseWithExtension(ICON);
        }
    }

    @NodeProvider.Registration(path = "Oracle/BuildRunFolder")
    public static NodeProvider<BuildRunItem.BuildRunFolder> createFolderNode() {
        return BuildRunFolderNode::new;
    }

    @ChildrenProvider.Registration(parentPath = "Oracle/BuildRunFolder")
    public static ChildrenProvider<BuildRunItem.BuildRunFolder, BuildRunItem> expandBuildRuns() {
        return repositories -> repositories.getBuildRuns();
    }
}
