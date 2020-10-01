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
package org.netbeans.modules.java.lsp.server.debugging.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.netbeans.modules.java.lsp.server.debugging.DebugAdapterContext;

/**
 *
 * @author martin
 */
public class NbLaunchWithoutDebuggingDelegate extends NbLaunchDelegate {

    private static final Logger LOGGER = Logger.getLogger(NbLaunchWithoutDebuggingDelegate.class.getName());

    protected static final String TERMINAL_TITLE = "Java Process Console";
    protected static final long RUNINTERMINAL_TIMEOUT = 10 * 1000;
    private Consumer<DebugAdapterContext> terminateHandler;

    public NbLaunchWithoutDebuggingDelegate(Consumer<DebugAdapterContext> terminateHandler) {
        this.terminateHandler = terminateHandler;
    }

    protected static String[] constructEnvironmentVariables(Map<String, Object> launchArguments) {
        String[] envVars = null;
        Map<String, String> env = (Map<String, String>) launchArguments.get("env");
        if (env != null && !env.isEmpty()) {
            Map<String, String> environment = new HashMap<>(System.getenv());
            List<String> duplicated = new ArrayList<>();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                if (environment.containsKey(entry.getKey())) {
                    duplicated.add(entry.getKey());
                }
                environment.put(entry.getKey(), entry.getValue());
            }
            // For duplicated variables, show a warning message.
            if (!duplicated.isEmpty()) {
                LOGGER.warning(String.format("There are duplicated environment variables. The values specified in launch.json will be used. "
                        + "Here are the duplicated entries: %s.", String.join(",", duplicated)));
            }

            envVars = new String[environment.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                envVars[i++] = entry.getKey() + "=" + entry.getValue();
            }
        }
        return envVars;
    }

    @Override
    public void postLaunch(Map<String, Object> launchArguments, DebugAdapterContext context) {
        // For NO_DEBUG launch mode, the debugger does not respond to requests like
        // SetBreakpointsRequest,
        // but the front end keeps sending them according to the Debug Adapter Protocol.
        // To avoid receiving them, a workaround is not to send InitializedEvent back to
        // the front end.
        // See https://github.com/Microsoft/vscode/issues/55850#issuecomment-412819676
        return;
    }

    @Override
    public void preLaunch(Map<String, Object> launchArguments, DebugAdapterContext context) {
        // TODO Auto-generated method stub
    }
    
}
