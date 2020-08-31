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

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.formatter.SimpleTypeFormatter;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.StackTraceArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import org.netbeans.api.debugger.ActionsManager;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.modules.debugger.jpda.truffle.frames.TruffleStackFrame;
import org.netbeans.modules.debugger.jpda.truffle.source.Source;
import org.openide.util.Exceptions;

public class NbStackTraceRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.STACKTRACE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        StackTraceArguments stacktraceArgs = (StackTraceArguments) arguments;
        List<Types.StackFrame> result = new ArrayList<>();
        if (stacktraceArgs.startFrame < 0 || stacktraceArgs.levels < 0) {
            response.body = new Responses.StackTraceResponseBody(result, 0);
            return CompletableFuture.completedFuture(response);
        }
        
        /*
        String f = "netbeans-JPDASession/GraalVM_Script/DebuggingView";
        List<? extends TreeModelFilter> res = DebuggerManager.getDebuggerManager().lookup(f, TreeModelFilter.class);
        System.err.println("res: " + res);
        List<? extends TreeModelFilter> res2 = DebuggerManager.getDebuggerManager().getCurrentEngine().lookup("DebuggingView", TreeModelFilter.class);
        System.err.println("res: " + res2);
        JPDADebugger jpdaDeb = DebuggerManager.getDebuggerManager().lookupFirst("", JPDADebugger.class);
        System.err.println("jpdaDeb: " + jpdaDeb);
        */
        JPDADebugger jpdaDebSes = DebuggerManager.getDebuggerManager().getCurrentSession().lookupFirst("", JPDADebugger.class);
//        System.err.println("jpdaDeb: " + jpdaDebSes);
                
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), stacktraceArgs.threadId);
        int totalFrames = 0;
        if (thread != null) {
            JPDAThread jpdaThread = null;
            for (JPDAThread t : jpdaDebSes.getThreadsCollector().getAllThreads()) {
                if (thread.name().equals(t.getName())) {
                    jpdaThread = t;
                    break;
                }
            }
            
            ActionsManager am = DebuggerManager.getDebuggerManager().getCurrentEngine().getActionsManager();
            System.err.println("am: " + am);
            am.doAction("pauseInGraalScript");
            
            System.err.println("found thread: " + jpdaThread);
            
            Object[] newChildren = org.netbeans.modules.debugger.jpda.truffle.api.DebuggingTruffleTreeModel.filterAndAppend(jpdaThread, new Object[0]);
            
            System.err.println("newChildren: " + Arrays.asList(newChildren));
            int startFrame = stacktraceArgs.startFrame;
            
            if (newChildren.length > 0) {
                for (int i = 0; i < newChildren.length; i++) {
                    try {
                        TruffleStackFrame tsf = (TruffleStackFrame) newChildren[i];
                        final Source source = tsf.getSourcePosition().getSource();
                        String path;
                        try {
                            File fqn = new File(source.getURI());
                            path = fqn.getPath();
                        } catch (IllegalArgumentException e) {
                            path = source.getURI().getPath();
                        }
                        Types.Source src = new Types.Source(source.getName(), path, 0);
                        result.add(new Types.StackFrame(i, tsf.getMethodName(), src, tsf.getSourcePosition().getStartLine(), 1));
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        
            
            try {
                totalFrames = thread.frameCount();
                if (totalFrames <= stacktraceArgs.startFrame) {
                    response.body = new Responses.StackTraceResponseBody(result, totalFrames);
                    return CompletableFuture.completedFuture(response);
                }
                StackFrame[] frames = context.getStackFrameManager().reloadStackFrames(thread);

                int count = stacktraceArgs.levels == 0 ? totalFrames - stacktraceArgs.startFrame
                        : Math.min(totalFrames - stacktraceArgs.startFrame, stacktraceArgs.levels);
                for (int i = startFrame; i < frames.length && count > 0; i++) {
                    StackFrameReference stackframe = new StackFrameReference(thread, i);
                    int frameId = context.getRecyclableIdPool().addObject(thread.uniqueID(), stackframe);
                    Types.StackFrame frameOrNull = convertDebuggerStackFrameToClient(frames[i], frameId, context);
                    if (frameOrNull != null) {
                        result.add(frameOrNull);
                        count--;
                    }
                }
            } catch (IncompatibleThreadStateException | IndexOutOfBoundsException | URISyntaxException
                    | AbsentInformationException | ObjectCollectedException e) {
                // when error happens, the possible reason is:
                // 1. the vscode has wrong parameter/wrong uri
                // 2. the thread actually terminates
                // TODO: should record a error log here.
            }
        }
        response.body = new Responses.StackTraceResponseBody(result, totalFrames);
        return CompletableFuture.completedFuture(response);
    }

    private Types.StackFrame convertDebuggerStackFrameToClient(StackFrame stackFrame, int frameId, IDebugAdapterContext context)
            throws URISyntaxException, AbsentInformationException {
        Location location = stackFrame.location();
        Method method = location.method();
        String className = method.declaringType().name();
        if (className.startsWith("org.netbeans.modules.debugger.jpda.backend.truffle")) {
            return null;
        }
        if (className.startsWith("com.oracle.truffle")) {
            return null;
        }
        Types.Source clientSource = this.convertDebuggerSourceToClient(location, context);
        String methodName = formatMethodName(method, true, true);
        int lineNumber = AdapterUtils.convertLineNumber(location.lineNumber(), context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
        // Line number returns -1 if the information is not available; specifically, always returns -1 for native methods.
        if (lineNumber < 0) {
            if (method.isNative()) {
                // For native method, display a tip text "native method" in the Call Stack View.
                methodName += "[native method]";
            } else {
                // For other unavailable method, such as lambda expression's built-in methods run/accept/apply,
                // display "Unknown Source" in the Call Stack View.
                clientSource = null;
            }
        }
        return new Types.StackFrame(frameId, methodName, clientSource, lineNumber, context.isClientColumnsStartAt1() ? 1 : 0);
    }

    private Types.Source convertDebuggerSourceToClient(Location location, IDebugAdapterContext context) throws URISyntaxException {
        final String fullyQualifiedName = location.declaringType().name();
        String sourceName = "";
        String relativeSourcePath = "";
        try {
            // When the .class file doesn't contain source information in meta data,
            // invoking Location#sourceName() would throw AbsentInformationException.
            sourceName = location.sourceName();
            relativeSourcePath = location.sourcePath();
        } catch (AbsentInformationException e) {
            String enclosingType = AdapterUtils.parseEnclosingType(fullyQualifiedName);
            sourceName = enclosingType.substring(enclosingType.lastIndexOf('.') + 1) + ".java";
            relativeSourcePath = enclosingType.replace('.', File.separatorChar) + ".java";
        }

        return convertDebuggerSourceToClient(fullyQualifiedName, sourceName, relativeSourcePath, context);
    }

    /**
     * Find the source mapping for the specified source file name.
     */
    public static Types.Source convertDebuggerSourceToClient(String fullyQualifiedName, String sourceName, String relativeSourcePath,
            IDebugAdapterContext context) throws URISyntaxException {
        // use a lru cache for better performance
        String uri = context.getSourceLookupCache().computeIfAbsent(fullyQualifiedName, key -> {
            String fromProvider = context.getProvider(ISourceLookUpProvider.class).getSourceFileURI(key, relativeSourcePath);
            // avoid return null which will cause the compute function executed again
            return StringUtils.isBlank(fromProvider) ? "" : fromProvider;
        });

        if (!StringUtils.isBlank(uri)) {
            // The Source.path could be a file system path or uri string.
            if (uri.startsWith("file:")) {
                String clientPath = AdapterUtils.convertPath(uri, context.isDebuggerPathsAreUri(), context.isClientPathsAreUri());
                return new Types.Source(sourceName, clientPath, 0);
            } else {
                // If the debugger returns uri in the Source.path for the StackTrace response, VSCode client will try to find a TextDocumentContentProvider
                // to render the contents.
                // Language Support for Java by Red Hat extension has already registered a jdt TextDocumentContentProvider to parse the jdt-based uri.
                // The jdt uri looks like 'jdt://contents/rt.jar/java.io/PrintStream.class?=1.helloworld/%5C/usr%5C/lib%5C/jvm%5C/java-8-oracle%5C/jre%5C/
                // lib%5C/rt.jar%3Cjava.io(PrintStream.class'.
                return new Types.Source(sourceName, uri, 0);
            }
        } else {
            // If the source lookup engine cannot find the source file, then lookup it in the source directories specified by user.
            String absoluteSourcepath = AdapterUtils.sourceLookup(context.getSourcePaths(), relativeSourcePath);
            if (absoluteSourcepath != null) {
                return new Types.Source(sourceName, absoluteSourcepath, 0);
            } else {
                return null;
            }
        }
    }

    private String formatMethodName(Method method, boolean showContextClass, boolean showParameter) {
        StringBuilder formattedName = new StringBuilder();
        if (showContextClass) {
            String fullyQualifiedClassName = method.declaringType().name();
            formattedName.append(SimpleTypeFormatter.trimTypeName(fullyQualifiedClassName));
            formattedName.append(".");
        }
        formattedName.append(method.name());
        if (showParameter) {
            List<String> argumentTypeNames = method.argumentTypeNames().stream().map(SimpleTypeFormatter::trimTypeName).collect(Collectors.toList());
            formattedName.append("(");
            formattedName.append(String.join(",", argumentTypeNames));
            formattedName.append(")");
        }
        return formattedName.toString();
    }
}
