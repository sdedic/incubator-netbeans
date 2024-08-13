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
package org.netbeans.modules.project.dependency.spi;

import java.util.EnumSet;
import org.netbeans.modules.project.dependency.ProjectReload.ProjectTrust;

/**
 * This interface should be present for project systems that execute scripts without proper
 * sandboxing and with access to the filesystem. Untrusted projects should block executing
 * project operations and requests that require script execution. 
 * 
 * Project implementations should expose this interface to better integrate with the project 
 * system and project open actions.
 * 
 * @author sdedic
 */
public interface ProjectTrustImplementation {
    /**
     * Marks the project as trusted or removes the trust. If {@code permamnent} is true,
     * the implementation should mark the project as permanently trusted. If trust is
     * revoked from the project, the permanent trust mark must be removed as well.
     * 
     * @param trust desired trust level.
     */
    public void makeTrusted(ProjectTrust trust);
    
    /**
     * Returns the supported trust levels.
     */
    public EnumSet<ProjectTrust> supportedOptions();
    
    /**
     * Check if the project is trusted. If {@code permanent} is true, the method must
     * only return true, if the project is permanently marked.
     * @return project trust level.
     */
    public ProjectTrust checkTrusted();
}
