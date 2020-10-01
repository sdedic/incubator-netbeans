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
package org.netbeans.modules.java.lsp.server.debugging.breakpoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.netbeans.api.debugger.DebuggerManager;

public final class BreakpointManager {

    private static final Logger LOGGER = Logger.getLogger(BreakpointManager.class.getName());

    private final List<NbBreakpoint> breakpoints;
    private final HashMap<String, HashMap<Integer, NbBreakpoint>> sourceToBreakpoints;
    private final AtomicInteger nextBreakpointId = new AtomicInteger(1);

    /**
     * Constructor.
     */
    public BreakpointManager() {
        this.breakpoints = Collections.synchronizedList(new ArrayList<>(5));
        this.sourceToBreakpoints = new HashMap<>();
    }

    /**
     * Set breakpoints to the given source.
     * <p>
     * Deletes all old breakpoints from the source.
     * 
     * @return a new list of breakpoints in that source
     */
    public NbBreakpoint[] setBreakpoints(String source, NbBreakpoint[] breakpoints, boolean sourceModified) {
        List<NbBreakpoint> result = new ArrayList<>();
        HashMap<Integer, NbBreakpoint> breakpointMap = this.sourceToBreakpoints.get(source);
        // When source file is modified, delete all previously added breakpoints.
        if (sourceModified && breakpointMap != null) {
            for (NbBreakpoint bp : breakpointMap.values()) {
                try {
                    // Destroy the breakpoint on the debugee VM.
                    bp.close();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, String.format("Remove breakpoint exception: %s", e.toString()), e);
                }
                this.breakpoints.remove(bp);
            }
            this.sourceToBreakpoints.put(source, null);
            breakpointMap = null;
        }
        if (breakpointMap == null) {
            breakpointMap = new HashMap<>();
            this.sourceToBreakpoints.put(source, breakpointMap);
        }

        // Compute the breakpoints that are newly added.
        List<NbBreakpoint> toAdd = new ArrayList<>();
        List<Integer> visitedLineNumbers = new ArrayList<>();
        for (NbBreakpoint breakpoint : breakpoints) {
            NbBreakpoint existed = breakpointMap.get(breakpoint.getLineNumber());
            if (existed != null) {
                result.add(existed);
                visitedLineNumbers.add(existed.getLineNumber());
                continue;
            } else {
                result.add(breakpoint);
            }
            toAdd.add(breakpoint);
        }

        // Compute the breakpoints that are no longer listed.
        List<NbBreakpoint> toRemove = new ArrayList<>();
        for (NbBreakpoint breakpoint : breakpointMap.values()) {
            if (!visitedLineNumbers.contains(breakpoint.getLineNumber())) {
                toRemove.add(breakpoint);
            }
        }

        removeBreakpointsInternally(source, toRemove.toArray(new NbBreakpoint[0]));
        addBreakpointsInternally(source, toAdd.toArray(new NbBreakpoint[0]));

        return result.toArray(new NbBreakpoint[0]);
    }

    private void addBreakpointsInternally(String source, NbBreakpoint[] breakpoints) {
        Map<Integer, NbBreakpoint> breakpointMap = this.sourceToBreakpoints.computeIfAbsent(source, k -> new HashMap<>());

        if (breakpoints != null && breakpoints.length > 0) {
            for (NbBreakpoint breakpoint : breakpoints) {
                breakpoint.putProperty("id", this.nextBreakpointId.getAndIncrement());
                this.breakpoints.add(breakpoint);
                breakpointMap.put(breakpoint.getLineNumber(), breakpoint);
            }
        }
    }

    /**
     * Removes the specified breakpoints from breakpoint manager.
     */
    private void removeBreakpointsInternally(String source, NbBreakpoint[] breakpoints) {
        Map<Integer, NbBreakpoint> breakpointMap = this.sourceToBreakpoints.get(source);
        if (breakpointMap == null || breakpointMap.isEmpty() || breakpoints.length == 0) {
            return;
        }

        for (NbBreakpoint breakpoint : breakpoints) {
            if (this.breakpoints.contains(breakpoint)) {
                try {
                    // Destroy the breakpoint on the debugee VM.
                    breakpoint.close();
                    this.breakpoints.remove(breakpoint);
                    breakpointMap.remove(breakpoint.getLineNumber());
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, String.format("Remove breakpoint exception: %s", e.toString()), e);
                }
            }
        }
    }

    public NbBreakpoint[] getBreakpoints() {
        return this.breakpoints.toArray(new NbBreakpoint[0]);
    }

    /**
     * Gets the registered breakpoints at the source file.
     */
    public NbBreakpoint[] getBreakpoints(String source) {
        HashMap<Integer, NbBreakpoint> breakpointMap = this.sourceToBreakpoints.get(source);
        if (breakpointMap == null) {
            return new NbBreakpoint[0];
        }
        return breakpointMap.values().toArray(new NbBreakpoint[0]);
    }

    /**
     * Breakpoints are always being set from the client. We must clean them so that
     * they are not duplicated on the next start.
     */
    public void disposeBreakpoints() {
        DebuggerManager debuggerManager = DebuggerManager.getDebuggerManager();
        for (NbBreakpoint breakpoint : breakpoints) {
            debuggerManager.removeBreakpoint(breakpoint.getNBBreakpoint());
        }
        debuggerManager.removeAllWatches();
        this.sourceToBreakpoints.clear();
        this.breakpoints.clear();
        this.nextBreakpointId.set(1);
    }
}
