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

package org.netbeans.modules.lsp.client.debugger.breakpoints;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.api.debugger.DebuggerEngine;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.DebuggerManagerAdapter;
import org.netbeans.api.debugger.DebuggerManagerListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.lsp.client.debugger.DAPDebugger;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.Line;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;



public final class DAPLineBreakpoint extends Breakpoint {

    public static final String PROP_CONDITION = "condition";                    // NOI18N
    public static final String PROP_HIDDEN = "hidden";                          // NOI18N

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean hidden = new AtomicBoolean(false);
    private final FileObject fileObject; // The user file that contains the breakpoint
    private final int lineNumber; // The breakpoint line number
    private volatile String condition;

    private DAPLineBreakpoint (FileObject fileObject, String filePath, int lineNumber) {
        this.fileObject = fileObject;
        this.lineNumber = lineNumber;
    }

    public static DAPLineBreakpoint create(Line line) {
        int lineNumber = line.getLineNumber() + 1;
        FileObject fileObject = line.getLookup().lookup(FileObject.class);
        return create(fileObject, lineNumber);
    }

    /**
     * Create a new DAP breakpoint based on a user file.
     * @param fileObject the file path of the breakpoint
     * @param lineNumber 1-based line number
     * @return a new breakpoint.
     */
    public static DAPLineBreakpoint create(FileObject fileObject, int lineNumber) {
        String filePath = FileUtil.toFile(fileObject).getAbsolutePath();
        return new DAPLineBreakpoint(fileObject, filePath, lineNumber);
    }

    /**
     * 1-based line number.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    @NonNull
    public FileObject getFileObject() {
        return fileObject;
    }

    @CheckForNull
    public Line getLine() {
        FileObject fo = fileObject;
        if (fo == null) {
            return null;
        }
        DataObject dataObject;
        try {
            dataObject = DataObject.find(fo);
        } catch (DataObjectNotFoundException ex) {
            return null;
        }
        LineCookie lineCookie = dataObject.getLookup().lookup(LineCookie.class);
        if (lineCookie != null) {
            Line.Set ls = lineCookie.getLineSet ();
            if (ls != null) {
                try {
                    return ls.getCurrent(lineNumber - 1);
                } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                }
            }
        }
        return null;
    }
    /**
     * Test whether the breakpoint is enabled.
     *
     * @return <code>true</code> if so
     */
    @Override
    public boolean isEnabled () {
        return enabled.get();
    }

    /**
     * Disables the breakpoint.
     */
    @Override
    public void disable () {
        if (enabled.compareAndSet(true, false)) {
            firePropertyChange (PROP_ENABLED, Boolean.TRUE, Boolean.FALSE);
        }
    }

    /**
     * Enables the breakpoint.
     */
    @Override
    public void enable () {
        if (enabled.compareAndSet(false, true)) {
            firePropertyChange (PROP_ENABLED, Boolean.FALSE, Boolean.TRUE);
        }
    }

    /**
     * Get the breakpoint condition, or <code>null</code>.
     */
    public String getCondition() {
        return condition;
    }

    /**
     * Set the breakpoint condition.
     */
    public void setCondition(String condition) {
        String oldCondition;
        synchronized (this) {
            oldCondition = this.condition;
            if (Objects.equals(oldCondition, condition)) {
                return ;
            }
            this.condition = condition;
        }
        firePropertyChange (PROP_CONDITION, oldCondition, condition);
    }

    /**
     * Gets value of hidden property.
     *
     * @return value of hidden property
     */
    public boolean isHidden() {
        return hidden.get();
    }

    /**
     * Sets value of hidden property.
     *
     * @param h a new value of hidden property
     */
    public void setHidden(boolean h) {
        boolean old = hidden.getAndSet(h);
        if (old != h) {
            firePropertyChange(PROP_HIDDEN, old, h);
        }
    }

    @Override
    public GroupProperties getGroupProperties() {
        return new CPPGroupProperties();
    }

    private final class CPPGroupProperties extends GroupProperties {

        private CPPEngineListener engineListener;

        @Override
        public String getLanguage() {
            return "C/C++";
        }

        @Override
        public String getType() {
            return NbBundle.getMessage(DAPLineBreakpoint.class, "LineBrkp_Type");
        }

        private FileObject getFile() {
            return getFileObject();
        }

        @Override
        public FileObject[] getFiles() {
            return new FileObject[] { getFileObject() };
        }

        @Override
        public Project[] getProjects() {
            FileObject f = getFile();
            while (f != null) {
                f = f.getParent();
                if (f != null && ProjectManager.getDefault().isProject(f)) {
                    break;
                }
            }
            if (f != null) {
                try {
                    return new Project[] { ProjectManager.getDefault().findProject(f) };
                } catch (IOException ex) {
                } catch (IllegalArgumentException ex) {
                }
            }
            return null;
        }

        @Override
        public DebuggerEngine[] getEngines() {
            if (engineListener == null) {
                engineListener = new CPPEngineListener();
                DebuggerManager.getDebuggerManager().addDebuggerListener(
                        WeakListeners.create(DebuggerManagerListener.class,
                                             engineListener,
                                             DebuggerManager.getDebuggerManager()));
            }
            DebuggerEngine[] engines = DebuggerManager.getDebuggerManager().getDebuggerEngines();
            if (engines.length == 0) {
                return null;
            }
            if (engines.length == 1) {
                if (isDAPEngine(engines[0])) {
                    return engines;
                } else {
                    return null;
                }
            }
            // Several running sessions
            List<DebuggerEngine> antEngines = null;
            for (DebuggerEngine e : engines) {
                if (isDAPEngine(e)) {
                    if (antEngines == null) {
                        antEngines = new ArrayList<>();
                    }
                    antEngines.add(e);
                }
            }
            if (antEngines == null) {
                return null;
            } else {
                return antEngines.toArray(new DebuggerEngine[0]);
            }
        }

        private boolean isDAPEngine(DebuggerEngine e) {
            return e.lookupFirst(null, DAPDebugger.class) != null;
        }

        @Override
        public boolean isHidden() {
            return false;
        }

        private final class CPPEngineListener extends DebuggerManagerAdapter {

            @Override
            public void engineAdded(DebuggerEngine engine) {
                if (isDAPEngine(engine)) {
                    firePropertyChange(PROP_GROUP_PROPERTIES, null, CPPGroupProperties.this);
                }
            }

            @Override
            public void engineRemoved(DebuggerEngine engine) {
                if (isDAPEngine(engine)) {
                    firePropertyChange(PROP_GROUP_PROPERTIES, null, CPPGroupProperties.this);
                }
            }

        }
        
    }
    
}
