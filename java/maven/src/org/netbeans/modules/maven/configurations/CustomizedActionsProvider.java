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
package org.netbeans.modules.maven.configurations;

import java.util.Collections;
import java.util.Set;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.api.execute.RunConfig;
import org.netbeans.modules.maven.execute.model.NetbeansActionMapping;
import org.netbeans.modules.maven.spi.actions.MavenActionsProvider;
import org.netbeans.spi.project.LookupProvider.Registration.ProjectType;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.Lookup;

/**
 * Serves actions from customized configurations only. Ignores all defaulted configurations,
 * to give a chance to other action providers to step in and provide their definitions.
 * 
 * @author sdedic
 */
@ProjectServiceProvider(
        service = MavenActionsProvider.class,
        projectTypes = @ProjectType(
                id = NbMavenProject.TYPE, 
                // attempt to precede most of the providers
                position = -100000
        )
)
public final class CustomizedActionsProvider implements MavenActionsProvider {
    private final Project prj;
    
    /**
     * Lazily initialized from {@link #configs}. Not synchronized - M2ConfigProvider
     * should be a singleton.
     */
    private M2ConfigProvider cfgProvider;
    
    public CustomizedActionsProvider(Project project) {
        prj = project;
    }
    
    private M2ConfigProvider configs() {
        if (cfgProvider == null) {
            // should be a singleton
            cfgProvider = prj.getLookup().lookup(M2ConfigProvider.class);
        }
        return cfgProvider;
    }
    
    @Override
    public RunConfig createConfigForDefaultAction(String actionName, Project project, Lookup lookup) {
        M2Configuration cfg = configs().getActiveConfiguration();
        if (cfg.isCustomized()) {
            return cfg.createConfigForDefaultAction(actionName, project, lookup);
        } else {
            return null;
        }
    }

    @Override
    public NetbeansActionMapping getMappingForAction(String actionName, Project project) {
        M2Configuration cfg = configs().getActiveConfiguration();
        if (cfg.isCustomized()) {
            return cfg.getMappingForAction(actionName, project);
        } else {
            return null;
        }
    }

    @Override
    public boolean isActionEnable(String action, Project project, Lookup lookup) {
        M2Configuration cfg = configs().getActiveConfiguration();
        if (cfg.isCustomized()) {
            return cfg.isActionEnable(action, project, lookup);
        }
        return false;
    }

    @Override
    public Set<String> getSupportedDefaultActions() {
        M2Configuration cfg = configs().getActiveConfiguration();
        if (cfg.isCustomized()) {
            return cfg.getSupportedDefaultActions();
        }
        return Collections.emptySet();
    }
}
