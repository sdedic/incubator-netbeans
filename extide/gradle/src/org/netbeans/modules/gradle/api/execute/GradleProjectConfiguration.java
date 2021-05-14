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
package org.netbeans.modules.gradle.api.execute;

import java.util.Map;
import org.netbeans.spi.project.ProjectConfiguration;

/**
 *
 * @author sdedic
 */
public final class GradleProjectConfiguration implements ProjectConfiguration {
    public static final String DEFAULT = "%%DEFAULT%%";
    public static final String ACTIVE = "%%ACTIVE%%";
    
    private final String id;
    private String displayName;
    
    /**
     * Project properties effective in this configuration.
     */
    private Map<String, String> projectProperties;
    
    /**
     * Gradle commandline added in the configuration.
     */
    private String commandLineArgs;

    public GradleProjectConfiguration(String id) {
        this.id = id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public Map<String, String> getProjectProperties() {
        return projectProperties;
    }

    public String getCommandLineArgs() {
        return commandLineArgs;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setProjectProperties(Map<String, String> projectProperties) {
        this.projectProperties = projectProperties;
    }

    public void setCommandLineArgs(String commandLineArgs) {
        this.commandLineArgs = commandLineArgs;
    }
}
