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
package org.netbeans.modules.gradle.spi.actions;

import java.util.HashMap;
import java.util.Map;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.api.execute.GradleProjectConfiguration;
import org.netbeans.spi.project.ProjectConfigurationProvider;

/**
 *
 * @author sdedic
 */
public final class ProjectConfigurationSupport {
    
    private static final ThreadLocal<Map<NbGradleProject, GradleProjectConfiguration>> selectedConfigs = new ThreadLocal<Map<NbGradleProject, GradleProjectConfiguration>>() {
        @Override
        protected Map<NbGradleProject, GradleProjectConfiguration> initialValue() {
            return new HashMap<>();
        }
    };
    
    public static void executeWithConfiguration(NbGradleProject gp, GradleProjectConfiguration c, Runnable task) {
        ProjectConfigurationProvider<GradleProjectConfiguration> pcp = gp.projectLookup(ProjectConfigurationProvider.class);
        if (pcp == null) {
            // the project does not support configurations.
            task.run();
            return;
        }
        Map<NbGradleProject, GradleProjectConfiguration> m = selectedConfigs.get();
        try {
            Map<NbGradleProject, GradleProjectConfiguration> n = new HashMap<>(m);
            m.put(gp, c);
            selectedConfigs.set(n);
            task.run();
        } finally {
            selectedConfigs.set(m);
        }
    }
    
    public static GradleProjectConfiguration getEffectiveConfiguration(NbGradleProject p) {
        GradleProjectConfiguration c = selectedConfigs.get().get(p);
        if (c != null) {
            return c;
        }
        ProjectConfigurationProvider<GradleProjectConfiguration> pcp = p.projectLookup(ProjectConfigurationProvider.class);
        if (pcp == null) {
            return null;
        }
        return pcp.getActiveConfiguration();
    }
}
