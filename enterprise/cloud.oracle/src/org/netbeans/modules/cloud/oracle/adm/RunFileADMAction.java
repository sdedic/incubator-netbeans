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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 *
 * @author Petr Pisl
 */

@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.cloud.oracle.adm.RunFileADMAction"
)
@ActionRegistration(
        displayName = "#CTL_RunFileAudit",
        asynchronous = true
)

@ActionReferences(value = {
    @ActionReference(position = 251, path = "Loaders/text/x-maven-pom+xml/Actions"),
    @ActionReference(position = 1800, path = "Projects/org-netbeans-modules-maven/Actions")
})

@NbBundle.Messages({
    "CTL_RunFileAudit=Run ADM",})
public class RunFileADMAction implements ActionListener{

    private final FileObject file;

    public RunFileADMAction(FileObject file) {
        this.file = file;
    }
    
    
    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = FileOwnerQuery.getOwner(file);
        System.out.println("Running adm action:");
        System.out.println("  Project: " + project.toString());
        System.out.println("  File: " + file.getPath());
        System.out.println("  Knowledge Base: " + DefaultKnowledgeBaseStorage.getInstance().getDefaultKnowledgeBaseId());
    }
    
}
