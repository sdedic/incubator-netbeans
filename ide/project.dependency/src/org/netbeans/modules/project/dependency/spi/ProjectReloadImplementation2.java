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

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.modules.project.dependency.ProjectReload;
import org.netbeans.modules.project.dependency.ProjectReload.ProjectStateRequest;
import org.netbeans.modules.project.dependency.ProjectReload.Quality;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Provides information on files affecting the project reload, and allows to reload project metadata.
 * The implementations usually monitors the on-disk file changes and manages background project reloads.
 * But with programmatic changes to project files, it may be necessary to wait for the project reload to pick 
 * the new project's metadata. The project infrastructure may not be able to pick in-memory document changes
 * to the project settings; especially when invokes external tools such as Maven, Gradle etc. This interface
 * also allows to collect project files, that should be saved before project reload can pick fresh data.
 * <p>
 * The implementation must hold some invariants.
 * <li>if it reports a FileObject for the reload set, it <b>must</b> ensure that {@link #getTimestamp} reports
 * time the same or newer than that FileObject's lastModified time.
 * <p>
 * When reloading, the {@link #createReloader} is called to obtain the actual callable implementation stateful object.
 * The infrastructure checks the project's validity against files reported from {@link #findProjectFiles}. 
 * 
 * @author sdedic
 */
public interface ProjectReloadImplementation2<Result> {
    
    /**
     * Describes the current project's metadata state. When the metadata validity changes, the
     * implementation must call {@link #fireChanged}.
     * <p/>
     * This structure is not directly exposed to API users, it communicates data and events from
     * SPI to API utility methods.
     */
    public final class ProjectStateData {
        private final Collection<FileObject> files;
        private final Quality quality;
        private final long timestamp;
        private boolean consistent;
        private boolean valid;
        
        private volatile ChangeListener listener;

        public ProjectStateData(Collection<FileObject> files, Quality quality, long timestamp) {
            this.files = files;
            this.quality = quality;
            this.timestamp = timestamp;
        }
        
        public Collection<FileObject> getFiles() {
            return files;
        }

        public Quality getQuality() {
            return quality;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isConsistent() {
            return consistent;
        }
        
        public void fireChanged(boolean invalidate, boolean inconsistent) {
            if (invalidate) {
                this.valid = false;
            }
            if (inconsistent) {
                this.consistent = false;
            }
            ChangeListener l = this.listener;
            if (l != null) {
                l.stateChanged(new ChangeEvent(this));
            }
        }
    }
    
    public ProjectStateData getProjectState();
    
    /**
     * Requests to load project's state. The caller does a basic file timestamp check, and informs the implementation
     * about the result. The implementation may do its own checking and caching, but if it had invalidated the {@link ProjectState},
     * it must load a new one.
     * 
     * @param request parameters for project load.
     * @param updatedFiles set of files that have updated from the last state, or {@code null} to indicate that files are updated in an unexpected way.
     * @return new ProjectStateData 
     */
    public @CheckForNull ProjectStateData loadProjectState(ProjectStateRequest request, @NullAllowed Collection<FileObject> updatedFiles);
}

