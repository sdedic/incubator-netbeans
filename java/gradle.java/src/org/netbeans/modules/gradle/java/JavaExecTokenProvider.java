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
package org.netbeans.modules.gradle.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.netbeans.api.extexecution.base.ExplicitProcessParameters;
import org.netbeans.api.extexecution.startup.StartupExtender;
import org.netbeans.api.project.Project;
import org.netbeans.modules.gradle.api.GradleBaseProject;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.spi.actions.ReplaceTokenProvider;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author sdedic
 */
@ProjectServiceProvider(
        service = ReplaceTokenProvider.class, 
        projectType = NbGradleProject.GRADLE_PROJECT_TYPE
)
public class JavaExecTokenProvider implements ReplaceTokenProvider {
    public static String TOKEN_JAVAEXEC_JVMARGS = "javaExec.jvmArgs";
    public static String TOKEN_JAVAEXEC_ARGS = "javaExec.args";
    public static String TOKEN_JAVA_ARGS = "java.args";
    public static String TOKEN_JAVA_JVMARGS = "java.jvmArgs";
    
    private static final Set<String> TOKENS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            TOKEN_JAVAEXEC_ARGS, TOKEN_JAVAEXEC_JVMARGS,
            TOKEN_JAVA_ARGS, TOKEN_JAVA_JVMARGS
    )));
    
    private final Project project;
    
    public JavaExecTokenProvider(Project project) {
        this.project = project;
    }

    @Override
    public Set<String> getSupportedTokens() {
        return isEnabled() ? TOKENS : Collections.emptySet();
    }
    
    private boolean isEnabled() {
        Set<String> plugins = GradleBaseProject.get(project).getPlugins();
        return plugins.contains("java"); // NOI18N
    }

    @Override
    public Map<String, String> createReplacements(String action, Lookup context) {
        if (!isEnabled()) {
            return Collections.emptyMap();
        }
        StartupExtender.StartMode mode;
        
        switch (action) {
            case ActionProvider.COMMAND_RUN:
            case ActionProvider.COMMAND_RUN_SINGLE:
                mode = StartupExtender.StartMode.NORMAL;
                break;
            case ActionProvider.COMMAND_DEBUG:
            case ActionProvider.COMMAND_DEBUG_SINGLE:
                mode = StartupExtender.StartMode.DEBUG;
                break;
            case ActionProvider.COMMAND_PROFILE:
            case ActionProvider.COMMAND_PROFILE_SINGLE:
                mode = StartupExtender.StartMode.PROFILE;
                break;
            case ActionProvider.COMMAND_TEST:
            case ActionProvider.COMMAND_TEST_SINGLE:
                mode = StartupExtender.StartMode.TEST_NORMAL;
                break;
            case ActionProvider.COMMAND_DEBUG_TEST_SINGLE:
                mode = StartupExtender.StartMode.TEST_DEBUG;
                break;
            case ActionProvider.COMMAND_PROFILE_TEST_SINGLE:
                mode = StartupExtender.StartMode.TEST_PROFILE;
                break;
            default:
                mode = null;
        }
        InstanceContent ic = new InstanceContent();
        ic.add(project);
        if (project != null) {
            ic.add(project);
        }
        
        List<String> extraArgs = new ArrayList<>();
        if (mode != null) {
            for (StartupExtender group : StartupExtender.getExtenders(new AbstractLookup(ic), mode)) {
                extraArgs.addAll(group.getArguments());
            }
        }
        
        ExplicitProcessParameters contextParams = ExplicitProcessParameters.buildExplicitParameters(context);
        Map<String, String> result = new HashMap<>();
        result.put(TOKEN_JAVAEXEC_ARGS, ""); // NOI18N
        result.put(TOKEN_JAVAEXEC_JVMARGS, ""); // NOI18N
        result.put(TOKEN_JAVA_ARGS, ""); // NOI18N
        result.put(TOKEN_JAVA_ARGS, ""); // NOI18N
        if (extraArgs.isEmpty() && contextParams.isEmpty()) {
            return result;
        }
        ExplicitProcessParameters changedParams = ExplicitProcessParameters.builder().
                // Cannot read the rest of the commandline; custom args
                // are unsupported at the moment.
                // args(args).
                combine(contextParams).
                build();

        // need to pass JVM args and program args separately
        if (changedParams.getLauncherArguments() != null) {
            String jvmArgs = Utilities.escapeParameters(new String[] {
                String.join(" ", changedParams.getLauncherArguments())
            });
            result.put(TOKEN_JAVA_ARGS, jvmArgs);
            result.put(TOKEN_JAVAEXEC_JVMARGS, "-PrunJvmArgs=" + jvmArgs);
        }
        if (changedParams.getArguments() != null && !changedParams.getArguments().isEmpty()) {
            String args = Utilities.escapeParameters(new String[] {
                String.join(" ", changedParams.getArguments())
            });
            result.put(TOKEN_JAVA_ARGS, args);
            result.put(TOKEN_JAVAEXEC_ARGS, "--args " + args);
        }
        return result;
    }
}
