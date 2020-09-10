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
package org.netbeans.modules.java.lsp.server.debugging;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.LaunchMode;
import com.microsoft.java.debug.core.adapter.handler.ILaunchDelegate;
import com.microsoft.java.debug.core.adapter.handler.StackTraceRequestHandler;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Events.OutputEvent;
import com.microsoft.java.debug.core.protocol.Events.OutputEvent.Category;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.LaunchArguments;
import com.microsoft.java.debug.core.protocol.Types;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbLaunchDelegate;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbLaunchWithDebuggingDelegate;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbLaunchWithoutDebuggingDelegate;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbProcessConsole;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author martin
 */
final class NbLaunchRequestHandler implements IDebugRequestHandler {

    protected ILaunchDelegate activeLaunchHandler;
    private CompletableFuture<Boolean> waitForDebuggeeConsole = new CompletableFuture<>();

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.LAUNCH);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        LaunchArguments launchArguments = (LaunchArguments) arguments;
        activeLaunchHandler = launchArguments.noDebug ? new NbLaunchWithoutDebuggingDelegate((daContext) -> handleTerminatedEvent(daContext))
                : new NbLaunchWithDebuggingDelegate();
        return handleLaunchCommand(arguments, response, context);
    }

    protected CompletableFuture<Response> handleLaunchCommand(Arguments arguments, Response response, IDebugAdapterContext context) {
        LaunchArguments launchArguments = (LaunchArguments) arguments;
        // validation
        if (StringUtils.isBlank(launchArguments.mainClass)
                || ArrayUtils.isEmpty(launchArguments.modulePaths) && ArrayUtils.isEmpty(launchArguments.classPaths)) {
            throw AdapterUtils.createCompletionException(
                "Failed to launch debuggee VM. Missing mainClass or modulePaths/classPaths options in launch configuration.",
                ErrorCode.ARGUMENT_MISSING);
        }
        if (StringUtils.isBlank(launchArguments.encoding)) {
            context.setDebuggeeEncoding(StandardCharsets.UTF_8);
        } else {
            if (!Charset.isSupported(launchArguments.encoding)) {
                throw AdapterUtils.createCompletionException(
                    "Failed to launch debuggee VM. 'encoding' options in the launch configuration is not recognized.",
                    ErrorCode.INVALID_ENCODING);
            }
            context.setDebuggeeEncoding(Charset.forName(launchArguments.encoding));
        }

        if (StringUtils.isBlank(launchArguments.vmArgs)) {
            launchArguments.vmArgs = String.format("-Dfile.encoding=%s", context.getDebuggeeEncoding().name());
        } else {
            // if vmArgs already has the file.encoding settings, duplicate options for jvm will not cause an error, the right most value wins
            launchArguments.vmArgs = String.format("%s -Dfile.encoding=%s", launchArguments.vmArgs, context.getDebuggeeEncoding().name());
        }
        context.setLaunchMode(launchArguments.noDebug ? LaunchMode.NO_DEBUG : LaunchMode.DEBUG);

        activeLaunchHandler.preLaunch(launchArguments, context);

        return launch(launchArguments, response, context).thenCompose(res -> {
            if (res.success) {
                activeLaunchHandler.postLaunch(launchArguments, context);
            }
            return CompletableFuture.completedFuture(res);
        });
    }

    protected CompletableFuture<Response> launch(LaunchArguments launchArguments, Response response, IDebugAdapterContext context) {
        CompletableFuture<Response> resultFuture = new CompletableFuture<>();
        String filePath = launchArguments.mainClass;
        FileObject file = filePath != null ? FileUtil.toFileObject(new File(filePath)) : null;
        if (file == null) {
            resultFuture.completeExceptionally(
                    new DebugException(
                            "Missing file: " + filePath,
                            ErrorCode.LAUNCH_FAILURE.getId()
                    )
            );
            return resultFuture;
        }

        CompletableFuture<NbProcessConsole> debuggeeConsoleFuture = ((NbLaunchDelegate) activeLaunchHandler).nbLaunch(file, context, !launchArguments.noDebug);
        debuggeeConsoleFuture.thenAccept(debuggeeConsole -> {
            debuggeeConsole.lineMessages()
                    .map((message) -> convertToOutputEvent(message.output, message.category, context))
                    .doFinally(() -> waitForDebuggeeConsole.complete(true))
                    .subscribe((event) -> context.getProtocolServer().sendEvent(event));
            debuggeeConsole.start();
            resultFuture.complete(response);
        });
        return resultFuture;
    }

    private static final Pattern STACKTRACE_PATTERN = Pattern.compile("\\s+at\\s+(([\\w$]+\\.)*[\\w$]+)\\(([\\w-$]+\\.java:\\d+)\\)");

    private static OutputEvent convertToOutputEvent(String message, Category category, IDebugAdapterContext context) {
        Matcher matcher = STACKTRACE_PATTERN.matcher(message);
        if (matcher.find()) {
            String methodField = matcher.group(1);
            String locationField = matcher.group(matcher.groupCount());
            String fullyQualifiedName = methodField.substring(0, methodField.lastIndexOf("."));
            String packageName = fullyQualifiedName.lastIndexOf(".") > -1 ? fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf(".")) : "";
            String[] locations = locationField.split(":");
            String sourceName = locations[0];
            int lineNumber = Integer.parseInt(locations[1]);
            String sourcePath = StringUtils.isBlank(packageName) ? sourceName
                    : packageName.replace('.', File.separatorChar) + File.separatorChar + sourceName;
            Types.Source source = null;
            try {
                source = StackTraceRequestHandler.convertDebuggerSourceToClient(fullyQualifiedName, sourceName, sourcePath, context);
            } catch (URISyntaxException e) {
                // do nothing.
            }

            return new OutputEvent(category, message, source, lineNumber);
        }

        return new OutputEvent(category, message);
    }

    protected void handleTerminatedEvent(IDebugAdapterContext context) {
        CompletableFuture.runAsync(() -> {
            try {
                waitForDebuggeeConsole.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                // do nothing.
            }

            context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
        });
    }

}
