/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
 *
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.netbeans.modules.java.lsp.server.debugging;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.netbeans.modules.java.lsp.server.debugging.breakpoints.BreakpointManager;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbDebugSession;
import org.netbeans.modules.java.lsp.server.debugging.utils.IdCollection;
import org.netbeans.modules.java.lsp.server.debugging.utils.LRUCache;
import org.netbeans.modules.java.lsp.server.debugging.utils.RecyclableObjectPool;

public final class DebugAdapterContext {

    private static final int MAX_CACHE_ITEMS = 10000;

    private final Map<String, String> sourceMappingCache = Collections.synchronizedMap(new LRUCache<>(MAX_CACHE_ITEMS));

    private IDebugProtocolClient client;
    private NbDebugSession debugSession;
    private boolean clientLinesStartAt1 = true;
    private boolean clientColumnsStartAt1 = true;
    private boolean debuggerLinesStartAt1 = true;
    private boolean clientPathsAreUri = false;
    private boolean debuggerPathsAreUri = true;
    private boolean supportsRunInTerminalRequest = false;
    private boolean isAttached = false;
    private String[] sourcePaths;
    private Charset debuggeeEncoding;
    private boolean isVmStopOnEntry = false;
    private boolean isDebugMode = true;
    private Process debuggeeProcess;
    private Path classpathJar = null;
    private Path argsfile = null;

    private final IdCollection<String> sourceReferences = new IdCollection<>();
    private final RecyclableObjectPool<Integer, Object> recyclableIdPool = new RecyclableObjectPool<>();

    private final NBConfigurationSemaphore configurationSemaphore = new NBConfigurationSemaphore();
    private final NbSourceProvider sourceProvider = new NbSourceProvider();
    private final NbThreads threadsProvider = new NbThreads();
    private final BreakpointManager breakpointManager = new BreakpointManager();
    private final ExceptionManager exceptionManager = new ExceptionManager();

    public DebugAdapterContext() {
    }

    public IDebugProtocolClient getClient() {
        return client;
    }

    public void setClient(IDebugProtocolClient client) {
        this.client = client;
    }

    public NbDebugSession getDebugSession() {
        return debugSession;
    }

    public void setDebugSession(NbDebugSession session) {
        debugSession = session;
    }

    public boolean isClientLinesStartAt1() {
        return clientLinesStartAt1;
    }

    public void setClientLinesStartAt1(Boolean clientLinesStartAt1) {
        if (clientLinesStartAt1 != null) {
            this.clientLinesStartAt1 = clientLinesStartAt1;
        }
    }

    public boolean isClientColumnsStartAt1() {
        return clientColumnsStartAt1;
    }

    public void setClientColumnsStartAt1(Boolean clientColumnsStartAt1) {
        if (clientColumnsStartAt1) {
            this.clientColumnsStartAt1 = clientColumnsStartAt1;
        }
    }

    public boolean isDebuggerLinesStartAt1() {
        return debuggerLinesStartAt1;
    }

    public void setDebuggerLinesStartAt1(boolean debuggerLinesStartAt1) {
        this.debuggerLinesStartAt1 = debuggerLinesStartAt1;
    }

    public boolean isClientPathsAreUri() {
        return clientPathsAreUri;
    }

    public void setClientPathsAreUri(boolean clientPathsAreUri) {
        this.clientPathsAreUri = clientPathsAreUri;
    }

    public boolean isDebuggerPathsAreUri() {
        return debuggerPathsAreUri;
    }

    public void setDebuggerPathsAreUri(boolean debuggerPathsAreUri) {
        this.debuggerPathsAreUri = debuggerPathsAreUri;
    }

    public boolean isSupportsRunInTerminalRequest() {
        return supportsRunInTerminalRequest;
    }

    public void setSupportsRunInTerminalRequest(Boolean supportsRunInTerminalRequest) {
        if (supportsRunInTerminalRequest) {
            this.supportsRunInTerminalRequest = supportsRunInTerminalRequest;
        }
    }

    public boolean isAttached() {
        return isAttached;
    }

    public void setAttached(boolean isAttached) {
        this.isAttached = isAttached;
    }

    public String[] getSourcePaths() {
        return sourcePaths;
    }

    public void setSourcePaths(String[] sourcePaths) {
        this.sourcePaths = sourcePaths;
    }

    public String getSourceUri(int sourceReference) {
        return sourceReferences.get(sourceReference);
    }

    public int createSourceReference(String uri) {
        return sourceReferences.create(uri);
    }

    public RecyclableObjectPool<Integer, Object> getRecyclableIdPool() {
        return recyclableIdPool;
    }

    public Map<String, String> getSourceLookupCache() {
        return sourceMappingCache;
    }

    public void setDebuggeeEncoding(Charset encoding) {
        debuggeeEncoding = encoding;
    }

    public Charset getDebuggeeEncoding() {
        return debuggeeEncoding;
    }

    public boolean isVmStopOnEntry() {
        return isVmStopOnEntry;
    }

    public void setVmStopOnEntry(Boolean stopOnEntry) {
        if (stopOnEntry != null) {
            isVmStopOnEntry = stopOnEntry;
        }
    }

    public boolean isDebugMode() {
        return isDebugMode;
    }

    public void setDebugMode(boolean mode) {
        this.isDebugMode = mode;
    }

    public Process getDebuggeeProcess() {
        return this.debuggeeProcess;
    }

    public void setDebuggeeProcess(Process debuggeeProcess) {
        this.debuggeeProcess = debuggeeProcess;
    }

    public void setClasspathJar(Path classpathJar) {
        this.classpathJar = classpathJar;
    }

    public Path getClasspathJar() {
        return this.classpathJar;
    }

    public void setArgsfile(Path argsfile) {
        this.argsfile = argsfile;
    }

    public Path getArgsfile() {
        return this.argsfile;
    }

    public NBConfigurationSemaphore getConfigurationSemaphore() {
        return this.configurationSemaphore;
    }

    public NbSourceProvider getSourceProvider() {
        return this.sourceProvider;
    }

    public NbThreads getThreadsProvider() {
        return this.threadsProvider;
    }

    public BreakpointManager getBreakpointManager() {
        return this.breakpointManager;
    }

    public ExceptionManager getExceptionManager() {
        return this.exceptionManager;
    }
}
