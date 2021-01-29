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

package org.netbeans.modules.maven.runjar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.maven.wagon.Streams;
import org.netbeans.api.extexecution.base.ExplicitProcessParameters;
import org.netbeans.api.extexecution.startup.StartupExtender;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.ActionProviderImpl;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.api.execute.ActiveJ2SEPlatformProvider;
import org.netbeans.modules.maven.api.execute.ExecutionContext;
import org.netbeans.modules.maven.api.execute.LateBoundPrerequisitesChecker;
import org.netbeans.modules.maven.api.execute.RunConfig;
import org.netbeans.modules.maven.execute.BeanRunConfig;
import org.netbeans.modules.maven.execute.MavenExecuteUtils;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

@ProjectServiceProvider(service=LateBoundPrerequisitesChecker.class, projectType="org-netbeans-modules-maven/" + NbMavenProject.TYPE_JAR)
public class RunJarStartupArgs implements LateBoundPrerequisitesChecker {
    /**
     * Marker that separates VM args main class name from 'user specified arguments', whatever that
     * means for a selected goal.
     * If not present, then any injected arguments will be appended at the end of 'exec.args' property. If
     * present, the action context can <b>replace</b> the arguments.
     * @since 2.143
     */
    public static final String USER_PROGRAM_ARGS_MARKER = "%args"; // NOI18N
    
    /**
     * Splits a command line, pays respect to quoting and newlines.
     * @param line original line
     * @return line split into individual arguments.
     */
    private static String[] splitCommandLine(String line) {
        if (line == null) {
            return new String[0];
        }
        String l = line.trim();
        if (l.isEmpty()) {
            return new String[0];
        }
        List<String> result = new ArrayList<>();
        for (String part : MavenExecuteUtils.propertySplitter(l, true)) {
            result.add(part);
        }
        return result.toArray(new String[result.size()]);
    }
    
    @Override public boolean checkRunConfig(RunConfig config, ExecutionContext con) {
        String actionName = config.getActionName();
        StartupExtender.StartMode mode;
        if (ActionProvider.COMMAND_RUN.equals(actionName) || ActionProviderImpl.COMMAND_RUN_MAIN.equals(actionName)) {
            mode = StartupExtender.StartMode.NORMAL;
        } else if (ActionProvider.COMMAND_DEBUG.equals(actionName) || ActionProviderImpl.COMMAND_DEBUG_MAIN.equals(actionName)) {
            mode = StartupExtender.StartMode.DEBUG;
        } else if (ActionProvider.COMMAND_PROFILE.equals(actionName) || ActionProvider.COMMAND_PROFILE_SINGLE.equals(actionName) || ActionProviderImpl.COMMAND_PROFILE_MAIN.equals(actionName)) {
            mode = StartupExtender.StartMode.PROFILE;
        } else if (ActionProvider.COMMAND_PROFILE_TEST_SINGLE.equals(actionName)) {
            mode = StartupExtender.StartMode.TEST_PROFILE;
        } else {
            // XXX could also set argLine for COMMAND_TEST and relatives (StartMode.TEST_*); need not be specific to TYPE_JAR
            return true;
        }
        boolean isTestScope = false;
        for (Map.Entry<? extends String, ? extends String> entry : config.getProperties().entrySet()) {
            if (entry.getKey().equals("exec.args")) { // NOI18N
                List<String> fixedArgs = new ArrayList<String>();
                InstanceContent ic = new InstanceContent();
                Project p = config.getProject();
                if (p != null) {
                    ic.add(p);
                    ActiveJ2SEPlatformProvider pp = p.getLookup().lookup(ActiveJ2SEPlatformProvider.class);
                    if (pp != null) {
                        ic.add(pp.getJavaPlatform());
                    }
                }
                for (StartupExtender group : StartupExtender.getExtenders(new AbstractLookup(ic), mode)) {
                    fixedArgs.addAll(group.getArguments());
                }
                
                // split the 'exec.args' property to main and user arguments; userArgs will be null
                // if no user arguments are present or the marker is not found
                String[] argParts = MavenExecuteUtils.splitAll(entry.getValue(), false);
                
                String[] vmArgs = splitCommandLine(argParts[0]);
                String[] mainClass = splitCommandLine(argParts[1]);
                String[] userArgs = splitCommandLine(argParts[2]);
                if (mainClass.length == 0) {
                    // accept userargs, since we don't know where the division is, make it fixed in the processing.
                    mainClass = userArgs;
                    userArgs = null;
                }
                String[] fixed = null;
                fixedArgs.addAll(Arrays.asList(mainClass));
                
                // TODO: would be better to get them from ExecutionContext.
                ExplicitProcessParameters injectParams = ExplicitProcessParameters.buildExplicitParameters(Lookup.getDefault());

                if (!(fixedArgs.isEmpty() && injectParams.isEmpty())) {
                    ExplicitProcessParameters changedParams = ExplicitProcessParameters.
                        builder().
                        // get extender input as a base
                        priorityArgs(vmArgs).
                        // include user arguments, if any
                        args(userArgs).
                        // allow to append or override from context injectors.
                        combine(
                            injectParams
                        ).build();
                    // the existing args is a series of VM parameters and the main class name
                    
                    String newParams = String.join(" ", changedParams.getAllArguments(fixedArgs));
                    config.setProperty(entry.getKey(), newParams);
                }
            }
            if (entry.getKey().equals("exec.classpathScope") && "test".equals(entry.getValue())) {
                isTestScope = true;
            }
        }
        if (isTestScope) { //#230190
            String[] goals = config.getGoals().toArray(new String[0]);
            for (int i = 0; i < goals.length;i++) {
                if ("process-classes".equals(goals[i])) {
                    goals[i] = "process-test-classes";
                }
            }
            ((BeanRunConfig)config).setGoals(new ArrayList<String>(Arrays.asList(goals)));
        }
        return true;
    }

}
