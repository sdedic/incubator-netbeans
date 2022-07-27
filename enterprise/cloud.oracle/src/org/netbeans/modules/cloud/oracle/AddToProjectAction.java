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
package org.netbeans.modules.cloud.oracle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.cloud.common.project.CloudResourcesStorage;
import org.netbeans.modules.cloud.oracle.items.OCIItem;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.QuickPick.Item;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.NbBundle;

/**
 *
 * @author Jan Horvath
 */
@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.cloud.oracle.actions.AddToProject"
)
@ActionRegistration( 
        displayName = "#AddToProject", 
        asynchronous = true
)

@ActionReferences(value = {
    @ActionReference(path = "Cloud/Oracle/Common/Actions", position = 250)
})
@NbBundle.Messages({
    "AddToProject=Add To Project",
    "Select=Select Project(s)",
    "SelectProject=Select Project(s) where \"{0}\" will be added"
})
public class AddToProjectAction implements ActionListener {

    private final OCIItem context;

    public AddToProjectAction(OCIItem context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project[] projects = OpenProjects.getDefault().getOpenProjects();
        if (projects.length == 1) {
            CloudResourcesStorage storage = projects[0].getLookup().lookup(CloudResourcesStorage.class);
            if (storage != null) {
                storage.addToProject(context);
            }
            return;
        }
        Map<Item, Project> m = new HashMap<> ();
        for (int i = 0; i < projects.length; i++) {
            final ProjectInformation info = ProjectUtils.getInformation(projects[i]);
            Item item = new Item(info.getName(), info.getDisplayName());
            m.put(item, projects[i]);
        }
        NotifyDescriptor.QuickPick qp = new NotifyDescriptor.QuickPick(Bundle.SelectProject(context.getName()),
                Bundle.Select(), new ArrayList(m.keySet()), true);
        
        if (DialogDescriptor.OK_OPTION == DialogDisplayer.getDefault().notify(qp)) {
            for (Item item : qp.getItems()) {
                Project p = m.get(item);
                CloudResourcesStorage storage = p.getLookup().lookup(CloudResourcesStorage.class);
                if (storage != null) {
                    storage.addToProject(context);
                }
            }
        }
    }
    
}
