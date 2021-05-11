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

package org.netbeans.modules.maven.spi.actions;

import java.util.List;
import java.util.Set;
import org.netbeans.modules.maven.api.execute.RunConfig;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.MavenConfiguration;
import org.netbeans.modules.maven.execute.model.NetbeansActionMapping;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Interface that allows to put additional items to project's popup plus to provide specific
 * implementations of ActionProvider actions.
 * Implementations should be registered in default lookup using {@link ServiceProvider},
 * or since 2.50 may also be registered using {@link ProjectServiceProvider} if applicable to just some packagings.
 * 
 * @author  Milos Kleint
 */
public interface MavenActionsProvider {

    
    /**
     * Create an instance of RunConfig configured for execution.
     * @param actionName one of the ActionProvider constants
     * @returns RunConfig or null, if action not supported
     */
    RunConfig createConfigForDefaultAction(String actionName, Project project, Lookup lookup);

    /**
     * get a action to maven mapping configuration for the given action. No context specific value replacements
     * happen.
     * @return
     */
    NetbeansActionMapping getMappingForAction(String actionName, Project project);

    /**
     * return is action is supported or not
     * @param action action name, see ActionProvider for details.
     * @param project project that the action is invoked on.
     * @param lookup context for the action
     * @return
     */
    boolean isActionEnable(String action, Project project, Lookup lookup);

    /**
     * returns a list of supported actions, see ActionProvider.getSupportedActions()
     * @return
     */
    Set<String> getSupportedDefaultActions();
    
    /**
     * Enhances the provider to work with project configurations. Adds the ability to 
     * provide different actions, or map actions differently for different configurations.
     */
    public interface ConfigurationAware extends MavenActionsProvider {
        
        /**
         * Enumerates configurations provided or extended by the Provider. May return
         * {@code null} if configurations are not supported. The set of configurations must be
         * static - must not change. The {@link #MavenActionsProvider} instance may be
         * replaced by a new instance, however.
         * @return list of configurations.
         */
        public List<MavenConfiguration> getConfigurations();
        
        /**
         * Returns a provider effective for the specific configuration. May provide 
         * specialized mappings or logic.
         * <p/>
         * This call is valid on the {@link MavenActionsProvider} instance registered in
         * the (project) Lookup. Results of calling on an instance obtained from {@link #forConfiguration}
         * call are not defined.
         * 
         * @param cfg the configuration.
         * @return action provider.
         */
        public MavenActionsProvider forConfiguration(MavenConfiguration cfg);
    }
}
