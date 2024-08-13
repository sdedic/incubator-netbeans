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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.modules.project.dependency.ProjectReload;
import org.netbeans.modules.project.dependency.ProjectReload.ProjectState;
import org.netbeans.modules.project.dependency.ProjectReload.ProjectStateRequest;
import org.netbeans.modules.project.dependency.ProjectReload.Quality;
import org.netbeans.modules.project.dependency.impl.ProjectReloadInternal.LoadContextImpl;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

/**
 * Provides information on files affecting the project reload, and allows to reload project metadata. 
 * The implementation must produce a {@link ProjectState} according to its internal state:
 * <ul>
 * <li>It <b>may</b>  report files that were loaded by the project system; in that case, these files will be automatically
 * monitored for changes. 
 * <li>it <b>must</b> report timestamp of the project metadata load. If the Implementation can not determine a timestamp, it must
 * return -1. Timestamps are used to determine consistency.
 * <li>it <b>must</b> evaluate quality of data. See {@link ProjectReload.Quality} for detailed levels.
 * </ul>
 * The implementation can degrade {@code consistency} property on an existing {@link ProjectState} indicating the state is out-of-date.
 * It can also degrade {@code valid} property, indicating a more recent data has been already loaded.
 * <p>
 * The implementation must produce a data from its {@link #getProjectData()}, possibly {@code null} indicating that it does not
 * participate in this project's metadata. The returned metadata should be whatever is currently available, the method should not 
 * inspect the project system, or execute scripts: it is expected it is fast. 
 * If it returns non-{@code null}, it must extract {@link ProjectStateData} from its internal
 * data representation. The API stores and manages lifecycle of the returned internal data. {@link Object#hashCode} and {@link Object#equals} 
 * must be defined for the internal representation, otherwise object identity is used.
 * <p>
 * The implementation is asked to {@code reload} when the API client asks for data in higher quality than available, or when some of the
 * {@link ProjectReloadImplementation} does not agree the current data is valid/good enough in its {@link #accepts} method.
 * <p>
 * 
 * @author sdedic
 */
public interface ProjectReloadImplementation<D> {
    /**
     * Describes the current project's metadata state. When the metadata validity changes, the
     * implementation must call {@link ProjectStateControl#fireChanged}. ProjectState can transition from {@link #isConsistent()} 
     * state to inconsistent, and from {@link #isValid} state to invalid (or both at once). It cannot
     * transition back. {@code valid == false} state is a terminal one.
     * <p/>
     * The implementation should not monitor file timestamps and changes, this is done by the infrastructure once the files are reported.
     * But if a new file appears, that has not been present but now could affect the state, the implementation should call {@link ProjectStateControl#fireFileSetChanged}
     * to change the set of files.
     * <p/>
     * This structure is not directly exposed to API users, it communicates data and events from
     * SPI to API utility methods.
     */
    public final class ProjectStateData {
        private final String id;
        private final Collection<FileObject> files;
        private final Quality quality;
        private final long timestamp;
        private final Lookup lookup;
        private final String key;
        
        private boolean consistent = true;
        private boolean valid;
        
        private List<ChangeListener> listeners = new ArrayList<>();

        ProjectStateData(String id, String key, Collection<FileObject> files, boolean valid, Quality quality, long timestamp, Lookup lkp) {
            this.key = key;
            this.lookup = lkp;
            this.id = id;
            this.valid = valid;
            this.files = files;
            this.quality = quality;
            this.timestamp = timestamp;
        }

        /**
         * @return Lookup with project-related data.
         */
        public Lookup getLookup() {
            return lookup;
        }
        
        /**
         * @return Returns the set of files loaded into project metadata.
         */
        public Collection<FileObject> getFiles() {
            return files;
        }

        /**
         * @return project's data quality
         */
        public Quality getQuality() {
            return quality;
        }

        /**
         * @return timestamp of project's metadata.
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * @return true, if the no new metadata has been loaded yet.
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * @return true, if the metadata is consistent with on-disk state or other
         * configuration sources.
         */
        public boolean isConsistent() {
            return consistent;
        }

        private void fire() {
            ChangeListener[] ll;
            synchronized (this) {
                if (listeners.isEmpty()) {
                    return;
                }
                ll = listeners.toArray(new ChangeListener[0]);
            }
            ChangeEvent e = new ChangeEvent(this);
            for (ChangeListener l : ll) {
                l.stateChanged(e);
            }
        }

        public void fireFileSetChanged(Collection<FileObject> files) {
            if (files.containsAll(this.files) && this.files.size() == files.size()) {
                return;
            }
            this.consistent = false;
            fire();
        }
        
        public void fireChanged(boolean invalidate, boolean inconsistent) {
            if (invalidate) {
                this.valid = false;
            }
            if (inconsistent) {
                this.consistent = false;
            }
            fire();
        }
        
        synchronized void addListener(ChangeListener l) {
            this.listeners.add(l);
        }
        
        public String toString() {
            return id + "[quality=" + quality + ", consistent=" + consistent + ", valid=" + valid + ", files: " + this.files + "]";
        }
    }

    /**
     * Creates a {@link ProjectState} instance.
     */
    public static final class ProjectStateBuilder {
        private long time = -1;
        private boolean valid = true;
        private boolean consistent = true;
        private Quality q;
        private String id;
        private String key;
        private Lookup lkp;
        private Collection<FileObject> files;
        
        public void files(Collection<FileObject> files) {
            this.files = files;
        }
        
        public void timestamp(long time) {
            this.time = time;
        }
        
        public void state(boolean valid, boolean consistent) {
            this.valid = valid;
            this.consistent = consistent;
        }

        public void definePart(String id, Quality q) {
            this.id = id;
            this.q = q;
        }
        
        public void queryKey(String key) {
            this.key = key;
        }
        
        public void attachLookup(Lookup lkp) {
            this.lkp = lkp;
        }
        
        public ProjectStateData build() {
            return new ProjectStateData(id, key, files, valid, q, time, lkp);
        }
    }

    /**
     * Creates a {@link #ProjectStateData} instance. This class allows the creator to fire events
     * on the {@link ProjectStateData}.
     */
    public final class ProjectStateControl {
        private final ProjectStateData data;
        
        /**
         * Creates a {@link ProjectStateData} report.
         * @param id id of the implementation. Diagnostics only
         * @param key an optional key, that depends on ProjectStateRequest other state interpreted by the implenentation.
         * @param files files that were loaded by the implementation into the project metadata.
         * @param valid true, if no newer metadata has been loaded
         * @param quality quality of the metadata
         * @param timestamp time of metadata load. Will be used for consistency checks.
         */
        public ProjectStateControl(String id, String key, Collection<FileObject> files, boolean valid, Quality quality, long timestamp) {
            this(id, key, files, valid, quality, timestamp, Lookup.EMPTY);
        }
        
        /**
         * Creates a {@link ProjectStateData} report.
         * @param id id of the implementation. Diagnostics only
         * @param key an optional key, that depends on ProjectStateRequest other state interpreted by the implenentation.
         * @param files files that were loaded by the implementation into the project metadata.
         * @param valid true, if no newer metadata has been loaded
         * @param quality quality of the metadata
         * @param timestamp time of metadata load. Will be used for consistency checks.
         * @param lkp project metadata
         */
        public ProjectStateControl(String id, String key, Collection<FileObject> files, boolean valid, Quality quality, long timestamp, Lookup lkp) {
            data = new ProjectStateData(id, key, files, valid, quality, timestamp, lkp);
        }
        
        /**
         * @return the report instance.
         */
        public ProjectStateData getData() {
            return data;
        }

        /**
         * Allows the implementation to change the set of files that the project is picking up.
         * Doing so will always make the project contents <b>inconsistent</b>,
         * @param files the new set of files.
         */
        public void fireFileSetChanged(Collection<FileObject> files) {
            data.fireFileSetChanged(files);
        }
        
        /**
         * Informs that the validity or consistency has been changed.
         * @param invalidate true, if the ProjectStateData.valid should be degraded to false.
         * @param inconsistent true, if ProjectStateData.consistent should be degraded to false.
         */
        public void fireChanged(boolean invalidate, boolean inconsistent) {
            data.fireChanged(invalidate, inconsistent);
        }
    }
    
    /**
     * Generates project state report. The implementation may return {@code null} to indicate it will not participate in project loads.
     * @return the report instance.
     */
    public D getProjectData();
    
    /**
     * Checks if the current state request might be solved by the pending one. The implementation should not check quality levels or
     * file timestamps, this is already done by the infrastructure. It should check whatever additional implementation-dependent conditions
     * that may vary between these two requests.
     * <p>
     * The convenience default implementation returns true, meaning there are not any data affecting the decision except file timestamps and
     * overall project quality.
     * 
     * @param pending the request already pending
     * @param current the request about to be executed
     * @return true, if {@code pending} request can satisfy the {@code current} one.
     */
    public default boolean satisfies(ProjectStateRequest pending, ProjectStateRequest current) {
        return true;
    }
    
    /**
     * Checks if the request current {@link ProjectStateData} are acceptable for the request. This should
     * only check extended or custom information: checks for file consistency, overall quality etc are implemented by
     * the API and were already checked before.
     * <p/>
     * The convenience default implementation simply returns {@code true}.
     * 
     * @param request the request
     * @param current currently known state data.
     * @return false, if the project reload should be initiated.
     */
    public default boolean accepts(ProjectStateRequest request, D current) {
       return true; 
    }
    
    /**
     * Context information for project loading. The context provides access to partial
     * project state and its data loaded so far, by previous ProjectReloadImplementations.
     * <p>
     * The load may happen several times for one operation, if a participant
     * triggers a retry. 
     * 
     */
    public final class LoadContext {
        final ProjectState previousState;
        final LoadContextImpl impl;

        public LoadContext(ProjectState previousState, LoadContextImpl impl) {
            this.previousState = previousState;
            this.impl = impl;
        }
        
        /**
         * Provides access to the project information lookup, as it is 
         * incrementally loaded. After each {@link ProjectReloadImplementation} completes,
         * this Lookup will contain contents of that implementation's {@link ProjectStateData}'s Lookup.
         * 
         * @return project state lookup
         */
        public Lookup stateLookup() {
            return impl.partialStateImpl().getLookup();
        }
        
        /**
         * Returns partial state constructed so far.
         * @return partial state.
         */
        public @NonNull ProjectState getPartialState() {
            return impl.partialStateImpl();
        }
        
        /**
         * Attempts to locate load data stored previously by {@link #attachLoadData}. If the data does not
         * exist, the factory is called to create it - it can possibly return {@code null}.
         * 
         * @param <T> type of the data
         * @param dataClass class of the data.
         * @return 
         */
        @CheckForNull
        public <T> T memento(Class<T> dataClass, Function<Class<T>, T> factory) {
            return impl.contextDataImpl(dataClass, factory);
        }

        /**
         * Restarts the loading sequence. This can be used if a participant fills in some data that earlier
         * steps were failing on.
         */
        public void retryReload() {
            impl.retryReloadImpl();
        }
        
        public ProjectStateData getLastData() {
            return impl.getCachedData();
        }
    }
    
    /**
     * Loads the project metadata, possibly asynchronously. The last known {@link ProjectStateData} produced by this implementation is
     * passed as a reference point, if it is known. {@code null} can be passed to indicate that the load should be forced, but the implementation
     * is free to decide on using cached information. File and modification consistency have been already checked by the API.
     * <p>
     * The returned data must reflect the reloaded (or cached) project metadata quality. The same instance ought be
     * from now on returned from {@link #getProjectData} for the same query conditions. 
     * The implementation may return {@code null} to indicate it will not participate on this project loads. In that state, it should also return 
     * {@code null} from {@link #getPojectData}.
     * <p/>
     * The reload operation may be <b>invoked multiple times</b> if some of the participants requests a reload. In that case, the "prevState" will
     * be the data returned from the last {@link #reload} invocation for this request.
     * <p>
     * <b>Do not block the calling thread for long blocking operations or external processes</b>, use a {@link RequestProcessor}.
     */
    public <T> @CheckForNull CompletionStage<D> reload(ProjectStateRequest request, LoadContext context, @NullAllowed D prevState);

    /**
     * Extracts ProjectStateData from the internal state. It may return {@code null}. The implementation will be given the data returned
     * previously from {@link #reload} or {@link #getProjectData}.
     * 
     * @param data internal data 
     * @return the project state data, or {@code null}
     */
    public ProjectStateData getState(D data);
}
