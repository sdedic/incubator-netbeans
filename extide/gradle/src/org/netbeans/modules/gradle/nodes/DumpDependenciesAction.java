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
package org.netbeans.modules.gradle.nodes;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.netbeans.api.project.Project;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.Scopes;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;

/**
 *
 * @author sdedic
 */
@ActionRegistration(displayName = "Dump dependencies", asynchronous = true)
@ActionID(category = "Gradle", id = DumpDependenciesAction.ID)
@ActionReferences({
    @ActionReference(path = "Loaders/text/x-gradle+x-groovy/Actions"),
    @ActionReference(path = "Projects/org-netbeans-modules-gradle/Actions")
})
public class DumpDependenciesAction implements ActionListener {
    static final String ID = "org.netbeans.modules.gradle.nodes.DumpDependenciesAction";
    final Project project;

    public DumpDependenciesAction(Project project) {
        this.project = project;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        ProjectDependencies.DependencyQuery q = ProjectDependencies.newQuery(
                Scopes.RUNTIME
        );
        DependencyResult r = ProjectDependencies.findDependencies(project, q);
        System.err.println(r);
    }
}
