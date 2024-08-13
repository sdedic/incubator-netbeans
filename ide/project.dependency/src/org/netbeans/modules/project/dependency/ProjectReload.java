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
package org.netbeans.modules.project.dependency;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.lsp.ResourceModificationException;
import org.netbeans.api.lsp.ResourceOperation;
import org.netbeans.api.lsp.TextDocumentEdit;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.project.dependency.impl.ProjectReloadInternal;
import org.netbeans.modules.project.dependency.impl.ReloadApiAccessor;
import org.netbeans.modules.project.dependency.spi.ProjectReloadImplementation;
import org.netbeans.modules.project.dependency.spi.ProjectReloadImplementation.ProjectStateData;
import org.netbeans.modules.project.dependency.spi.ProjectTrustImplementation;
import org.openide.*;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.BaseUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Pair;
import org.openide.util.RequestProcessor;
import org.openide.util.Union2;
import org.openide.util.lookup.ProxyLookup;

/**
 * Utilities that relate to project (re)loading and refreshes. A client may request the project
 * to load its metadata to some defined minimum level required for the operation to work well.
 * While e.g. display operations can get away with project data which is stale (and eventually refresh),
 * operations that modify data needs that the project info is up-to-date so user changes are not reverted
 * or overwritten.
 * <p>
 * As analysis and data collection happens asynchronously, the user may modify project files before the
 * the write operation starts: the IDE should prompt the user to save work, or abort the operation. Sometimes
 * it is not desirable to prompt the user and the operation must be cancelled. This logic is better to
 * centralize.
 * <p>
 * Clients may also <b>watch</b> for project metadata updates and take appropriate actions to accommodate
 * possibly updated state: they need an event interface to be informed when the project data changes.
 * <p>
 * This API offers two main methods:
 * <ul>
 * <li>{@link #getProjectState}, that returns the current state of the project.
 * <li>{@link #withProjectState}, that requests the project to reach some state before proceeding further
 * </ul>
 * The {@link ProjectState} is watchable and can transition from consistent and valid to inconsistent and
 * ultimately invalid, when a new project metadata was loaded. Events are fired on state changes.
 * <p>
 * The {@link #withProjectState} may complete asynchronously and ensures the project metadata was updated
 * to the desired level - otherwise it will fail with {@link ProjectOperationException} with various
 * {@link ProjectOperationException.State} codes. Modified files may be saved, project may be reloaded 
 * or taken from the cache - all depends on what quality of information is available, whether it is 
 * consistent and what quality the client requires. The API client does not need to check explicitly,
 * just define its requirements to the API.
 * <p>
 * 
 * @author sdedic
 */
public final class ProjectReload {
    private static final Logger LOG = Logger.getLogger(ProjectStateData.class.getName());
  
    /**
     * Timeout to coalesce partial state changes into one ProjectState change. Events are
     * for ProjectReload clients are postponed at least this time.
     */
    private static final int STATE_COALESCE_TIMEOUT_MS = 100;
    
    /**
     * Timeout to clear stale ProjectStates from the cache.
     */
    private static final int STATE_TIMEOUT_MS = 30 * 1000;

    /**
     * Dedicated thread to fire state listeners.
     */
    private static final RequestProcessor NOTIFIER = new RequestProcessor(ProjectReload.class.getName());
    
    /**
     * Timed reference, that expires after 30 seconds.
     */
    private static final class StateRef extends WeakReference<ProjectState> implements Runnable {
        private volatile long lastAccessed;
        private final RequestProcessor.Task evictTask  = NOTIFIER.post(this);
        private final ProjectStateKey key;
        private volatile ProjectState hard;

        public StateRef(ProjectStateKey key, ProjectState referent) {
            super(referent, BaseUtilities.activeReferenceQueue());
            this.key = key;
        }

        @Override
        public ProjectState get() {
            ProjectState o = hard;
            if (o == null) {
                o = get();
            }
            if (o != null) {
                hard = o;
                if (lastAccessed == 0) {
                    evictTask.schedule(STATE_TIMEOUT_MS);
                }
                lastAccessed = System.currentTimeMillis();
            }
            return super.get(); 
        }

        @Override
        public void run() {
            if (hard == null) {
                synchronized (STATE_CACHE) {
                    STATE_CACHE.remove(key);
                }
            } else {
                long unused = System.currentTimeMillis() - lastAccessed;

                if (unused > (STATE_TIMEOUT_MS / 2)) {
                    hard = null;
                    lastAccessed = 0;
                } else {
                    evictTask.schedule(STATE_TIMEOUT_MS - (int) unused);
                }
            }
        }
    }
    
    /**
     * Makes a key based on ProjectStateData instances. The SAME ordered set of ProjectStateData instances 
     * identifies the resulting ProjectState.
     */
    private static final class ProjectStateKey{
        private final Map<ProjectReloadImplementation, ProjectStateData> parts;
        private volatile int hashCode = -1;
        
        public ProjectStateKey(Project project, Map<ProjectReloadImplementation, ProjectStateData> parts) {
            this.parts = parts;
        }
        
        @Override
        public int hashCode() {
            if (hashCode != -1) {
                return hashCode;
            }
            int hash = 7;
            for (ProjectStateData d : parts.values()) {
                hash = 17 * hash + Objects.hashCode(d);
            }
            if (hash == -1) {
                hash = -2;
            }
            this.hashCode = hash;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ProjectStateKey other = ((ProjectStateKey) obj);
            if (other.parts.size() != parts.size()) {
                return false;
            }
            for (ProjectReloadImplementation impl : parts.keySet()) {
                ProjectStateData d = parts.get(impl);
                ProjectStateData od = other.parts.get(impl);
                if (!Objects.equals(d, od)) {
                    return false;
                }
            }
            return true;
        }
    }
    
    /**
     * Describes the project state.
     */
    public static final class ProjectState {
        private final Project project;
        private final Quality status;
        private final Collection<FileObject> loaded;
        private final long timestamp;
        private final Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts;
        
        /**
         * Lazily initialized.
         */
        private volatile Lookup lookup;
        private volatile boolean consistent;
        private volatile Collection<FileObject> modified;
        private volatile boolean valid;
        private volatile boolean internalValid = true;
        private List<ChangeListener> changeListeners;
        private List<Reference<ProjectState>> previous;

        ProjectState(Project project, long timestamp, Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts, Quality status, boolean consistent, boolean valid, Collection<FileObject> loaded, Collection<FileObject> modified) {
            this.timestamp = timestamp;
            this.valid = valid;
            this.project = project;
            this.status = status;
            this.consistent = consistent;
            this.modified = modified;
            this.loaded = loaded;
            this.parts = parts;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 17 * hash + Objects.hashCode(this.project);
            hash = 17 * hash + (int) (this.timestamp ^ (this.timestamp >>> 32));
            hash = 17 * hash + Objects.hashCode(this.parts);
            return hash;
        }

        public long getTimestamp() {
            return timestamp;
        }
        
        public boolean isStillValid() {
            return internalValid;
        }

        public boolean isValid() {
            return valid;
        }

        public Collection<FileObject> getLoadedFiles() {
            return loaded;
        }

        public Lookup getLookup() {
            if (lookup != null) {
                return lookup;
            }
            Lookup[] partLookups = parts.values().stream().
                    map(d -> d.second()).filter(Objects::nonNull).
                    map(ProjectStateData::getLookup).
                    filter(l -> l != null && l != Lookup.EMPTY).
                    toArray(Lookup[]::new);
            return this.lookup = partLookups.length == 0 ? Lookup.EMPTY : new ProxyLookup(partLookups);
        }

        /**
         * The project
         * @return project instance
         */
        public Project getProject() {
            return project;
        }

        /**
         * Reports status of the project metadata.
         * @return status of metadata.
         */
        public Quality getQuality() {
            return status;
        }

        /**
         * Indicates whether metadata is consistent with editors and disk files.
         * @return true, if the metadata seems consistent.
         */
        public boolean isConsistent() {
            return consistent;
        }
        
        /**
         * Identifies modified files that determine the project metadata.
         * @return set of files modified in memory.
         */
        public Collection<FileObject> getModifiedFiles() {
            return modified;
        }

        public boolean isModified() {
            return !modified.isEmpty();
        }
        
        public void addChangeListener(ChangeListener l) {
            synchronized (this) {
                if (changeListeners == null) {
                    changeListeners = new ArrayList<>();
                }
                changeListeners.add(l);
            }
        }
        
        public void removeChangeListener(ChangeListener l) {
            synchronized (this) {
                if (changeListeners != null) {
                    changeListeners.remove(l);
                }
            }
        }
        
        void fireChange() {
            List<ChangeListener> ll = new ArrayList<>();
            synchronized (this) {
                if (changeListeners != null) {
                    ll.addAll(changeListeners);
                }
                if (previous != null) {
                    for (Iterator<Reference<ProjectState>> it = this.previous.iterator(); it.hasNext(); ) {
                        Reference<ProjectState> ps = it.next();
                        ProjectState p = ps.get();
                        if (p == null) {
                            it.remove();
                        }
                        synchronized (p) {
                            if (p.changeListeners != null) {
                                ll.addAll(p.changeListeners);
                            }
                        }
                    }
                }
            }
            if (ll.isEmpty()) {
                return;
            }
            ChangeEvent e = new ChangeEvent(this);
            ll.forEach(l -> l.stateChanged(e));
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(project).append("[");
            sb.append("stillValid=").append(internalValid);
            sb.append(", quality=").append(getQuality());
            sb.append(", consistent=").append(isConsistent());
            sb.append(", valid=").append(isValid());
            sb.append(", loaded=").append(getLoadedFiles());
            sb.append(", modified=").append(getLoadedFiles());
            sb.append("]");
            return sb.toString();
        }
    }
    
    /**
     * Request to load to a defined project state.
     */
    public final static class ProjectStateRequest {
        private Quality minQuality;
        
        /**
         * True, if the metadata should be consistent with the buildsystem
         */
        private boolean consistent = true;
        
        /**
         * Force project reload.
         */
        private boolean forceReload;
        
        /**
         * Save modifications, asking the user. If false, fails on modified files.
         */
        private boolean saveModifications;
        
        /**
         * Do not make network requests.
         */
        private boolean offlineOperation;
        
        /**
         * Reason of the reload.
         */
        private String reason;
        
        /**
         * True to grant project trust.
         */
        private boolean grantTrust;
        
        ProjectStateRequest(Quality quality, boolean forceReload, boolean saveModifications, boolean consistent, boolean offlineOperation) {
            this.minQuality = quality;
            this.forceReload = forceReload;
            this.consistent = consistent;
            this.saveModifications = saveModifications;
            this.offlineOperation = offlineOperation;
        }

        /**
         * Minimum desired quality of the project data. If the project does not load to that quality, the operation fails.
         * @return minimum quality.
         */
        public Quality getMinQuality() {
            return minQuality;
        }

        /**
         * True, if the project should be granted trust. With false value loading of untrusted project might fail.
         * @return true, if trust should be granted.
         */
        public boolean isGrantTrust() {
            return grantTrust;
        }
        
        /**
         * True, if the loaded data should be consistent with files and/or editor buffers. If set to false, 
         * any existing data that satisfy quality levels will be returned, even though it is stale.
         * @return true, if the data should be reloaded after modifications.
         */
        public boolean isConsistent() {
            return consistent;
        }

        /**
         * Returns true, if project load will be forced regardless of the project's current ready state.
         * @return true, if the project load is forced.
         */
        public boolean isForceReload() {
            return forceReload;
        }

        /**
         * Requests to save the modified files. It consistency is requested (= refresh) and some of the project files
         * are modified/unsaved, this indicates the files should be saved. If {@code false}, the operation will fail
         * as there are unsaved changes.
         */
        public boolean isSaveModifications() {
            return saveModifications;
        }

        /**
         * Returns true, if the operation is restricted to offline mode. If artifact needs to be resolved, the
         * operation fails.
         * @return True, if the operation must be offline. Default false.
         */
        public boolean isOfflineOperation() {
            return offlineOperation;
        }

        /**
         * @return  Human-readable reason for the possible project load. May be displayed in progress indicators.
         */
        public String getReason() {
            return reason;
        }

        /**
         * Sets the minimum project's quality.
         */
        public ProjectStateRequest toQuality(Quality q) {
            if (q.isWorseThan(Quality.FALLBACK)) {
                throw new IllegalArgumentException("Quality has to be at least fallback");
            }
            this.minQuality = q;
            return this;
        }

        /**
         * Permits to trust the project while loading the data. Use with caution, just during
         * project's initialization.
         * @return this instance.
         */
        public ProjectStateRequest grantTrust() {
            this.grantTrust = true;
            return this;
        }
        
        /**
         * Specifies if the metadata should be consistent with build system itself.
         * If the consistency is not required, the project metadata will not be refreshed if
         * the (stale) metadata is good enough.
         * <p/>
         * Use {@code consistent(false)} to indicate that stale data is OK.
         * @param c true to ensure consistency (refresh).
         * @return this instance
         */
        public ProjectStateRequest consistent(boolean c) {
            this.consistent = c;
            return this;
        }
        
        /**
         * Requests that project is loaded unconditionally. Will be loaded to at least the
         * minimum quality.
         * 
         * @return this instance.
         */
        public ProjectStateRequest forceReload() {
            forceReload = true;
            return this;
        }
        
        /**
         * Requests that possible in-memory changes to project files are ingored.
         * @return this instance.
         */
        public ProjectStateRequest saveModifications() {
            saveModifications = true;
            return this;
        }
        
        /**
         * Permits online operation, allowing downloads.
         * @return this instance.
         */
        public ProjectStateRequest online() {
            offlineOperation = false;
            return this;
        }

        /**
         * Disables online operations. If the project system needs to reach to the 
         * Internet for metadata or artifacts, it should fail the operation.
         * 
         * @return this instance.
         */
        public ProjectStateRequest offline() {
            offlineOperation = true;
            return this;
        }
        
        /**
         * Sets a reload reason. This reason may be displayed in progress indicators that might be 
         * displayed during the project load. Set it to a description of the intended operation.
         * @param reason
         * @return 
         */
        public ProjectStateRequest reloadReason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Makes a copy of the request. 
         * @return copy of the request.
         */
        public ProjectStateRequest copy() {
            return new ProjectStateRequest(minQuality, forceReload, saveModifications, consistent, offlineOperation);
        }
        
        /**
         * Requests that project is loaded to the desired state. It does not matter, if the project metadata is obsolete, because
         * the project files have been modified or changed on the disk and the project was not reloaded. This should be the default
         * state for read operations.
         * 
         * @return configured request
         */
        public static ProjectStateRequest load() {
            return new ProjectStateRequest(Quality.SIMPLE, false, false, false, false);
        }
        
        /**
         * Creates a request that attempts to refresh the project, if it has been modified or changed. The request
         * fails, if there are some unsaved files. If the project reloads, it is permitted to download external content.
         * 
         * @return configured request
         */
        public static ProjectStateRequest refresh() {
            return new ProjectStateRequest(Quality.SIMPLE, false, false, true, false);
        }

        /**
         * Requests to reload project's metadata and download external content for the project. If some of the project
         * files modified, the request will fail when it is executed.
         * 
         * @return configured request
         */
        public static ProjectStateRequest reload() {
            return new ProjectStateRequest(Quality.SIMPLE, true, false, true, false);
        }
    }
    
    /**
     * Status describes the quality of project metadata. For some operations, partial data may be sufficient,
     * for other operations the data must be complete and accurate. The decision is on the API client.
     * <p>
     * <table>
     * <th>Level</th><th>Explanation</th>
     * <tr><tr>BROKEN</td><td>The project metadata is unreliable. Project structure may be approximate, dependencies
     * unknown, defaults missing. Technologies and plugins unavailable and undetected. Modify operations should be aborted.</td></tr>
     * <tr><tr>SIMPLE</td><td>The project itself for its core structure should be OK, but dependency information may be missing in part or fully.</td></tr>
     * unknown, defaults missing. Technologies and plugins unavailable and undetected. Modify operations should be aborted.</td></tr>
     * <tr><td>LOADED</td><td>Project structure and technologies should be loaded OK, but external artifacts may be missing. Classpaths
     * or resources may be incomplete.</td></tr>
     * <tr><td>RESOLVED</td>The metadata was complete at the time they were created. External references were resolved and should
     * be available locally (unless they were deleted manually)</td></tr>
     * <tr><td>CONSISTENT</td><td>The metadata is consistent with the resources and memory buffers that define project metadata.</tr>
     * </table>
     */
    public static enum Quality {
        /**
         * The project metadata has not been loaded yet. Requesting project in NONE status always
         * succeeds and completes immediately, doing nothing.
         */
        NONE,
        
        /**
         * The data of this project is unreliable, based on heuristics. Project may be not trusted to run its
         * build system to get project information.
         */
        UNTRUSTED,
        
        /**
         * The data of this project is unreliable, based on heuristics.
         */
        FALLBACK,
        
        /**
         * The project state is broken. The project metadata is only partial and not reliable. This state indicates that some plugins or artifacts
         * are missing, that are required for the project to load and interpret its own data.
         */
        INCOMPLETE,

        /**
         * The project state is broken. The project metadata is only partial and not reliable. 
         */
        BROKEN,
        
        /**
         * The project itself for its core structure should be OK, but dependency information may be missing in part or fully.
         * Settings for project's technologies and build plugins may be missing.
         */
        SIMPLE,
        
        /**
         * The project metadata has been loaded. Dependencies and external artifacts might be missing, but all settings and parameters
         * should be available. Note that if a plugin is missing, the project may be in state {@link #BROKEN} or {@link #INCOMPLETE}
         */
        LOADED,
        
        /**
         * Project metadata are complete. They may be cached and stale as project files may have been modified since.
         * When used in a request, the system may try to download missing metadata in order to resolve references.
         */
        RESOLVED,
        
        /**
         * The project is resolved, and metadata are consistent with the on-disk files.
         */
        CONSISTENT;
        
        /**
         * Determines if this Quality is at least as good as the passed one. Use this check
         * to test, if the project's quality is 'good enough'.
         * @param s the tested quality
         * @return true, if this quality is same or better.
         */
        public boolean isAtLeast(Quality s) {
            return this.ordinal() >= s.ordinal();
        }
        
        /**
         * Determines if this quality is worse than the passed one. Use this check to test 
         * if the project quality does not meet your criteria.
         * @param s the tested quality
         * @return true, if this quality is worse
         */
        public boolean isWorseThan(Quality s) {
            return this.ordinal() < s.ordinal();
        }
    }
    
    /**
     * Caches ProjectStates. States are kept by reference, so fogetting ProjectState will eventually 
     * evict it + its project from this Cache. Only <b>last known</b> state is kept here - each
     * request to reload will replace the entry for the project.
     */
    private static final Map<ProjectStateKey, Reference<ProjectState>> STATE_CACHE = new WeakHashMap<>();
    
    private static final HashMap<ProjectState, RequestProcessor.Task> notifiers = new HashMap<>();
    
    /**
     * Fires a delayed state change. If a change is fired before the queued one is dispatched, the dispatch
     * is just postponed by another {@link #STATE_COALESCE_TIMEOUT_MS} timeout to avoid multiple changes for
     * fast-changing state.
     * 
     * @param s state to fire events.
     */
    private static void queueStateChange(ProjectState s) {
        AtomicReference<RequestProcessor.Task> cur = new AtomicReference<>();
        Runnable r = () -> {
            synchronized (notifiers) {
                notifiers.remove(s, cur.get());
            }
            s.fireChange();
        };
        synchronized (notifiers) {
            RequestProcessor.Task t = notifiers.computeIfAbsent(s, x -> NOTIFIER.create(r, true));
            t.schedule(STATE_COALESCE_TIMEOUT_MS);
        }
    }
    
    /**
     * Returns the current project's state.
     * @param p the project
     * @return the project's state.
     */
    public static ProjectState getProjectState(Project p) {
        return ProjectReloadInternal.get().getProjectState0(p).second();
    }
        
    private static boolean checkConsistency(ProjectState ps, ProjectStateRequest stateRequest, boolean forceReload) {
        return ProjectReloadInternal.checkConsistency(ps, stateRequest, forceReload);
    }
    
    private static String projectName(Project p) {
        return ProjectUtils.getInformation(p).getDisplayName();
    }
    
    /**
     * Ensures the desired project state. 
     * This call ensures the project metadata is up-to-date or forces reload, depending on the {@code stateRequest}. The project infrastructure
     * performs necessary (and permitted) tasks, including artifact download and after the project load completes, the returned {@code CompletionStage}
     * becomes completed. If the project metadata is current and satisfy the {@code stateRequest}, the method may return immediately with finished
     * CompletionStage. 
     * <p>
     * If the project reload fails, the returned {@CompletionStage} completes exceptionally with the failure as the exception. If the project does not support
     * reloads, it returns a finished {@link CompletionStage}.
     * <p>
     * If the project resources are modified in memory, and need to be saved, the implementation may throw {@link ProjectOperationException}
     * with {@link ProjectOperationException.State#OUT_OF_SYNC} and report set of resources that need to be saved.
     * 
     * <p>
     * <b>Implementation note:</b> this is a staging place for this API. When it matures, it will ultimately go to {@code ide.projects}. Use at your own
     * risk !
     * 
     * @param p the project.
     * @return future which completes after project reloads
     */
    @NbBundle.Messages({
        "# {0} - project name",
        "# {1} - number of modified files",
        "ERR_ProjectFilesModified=Project {0} has (1) unsaved files.",
        "# {0} - project name",
        "TEXT_RefreshProject=Reloading project {0}",
        "# {0} - project name",
        "TEXT_ConcurrentlyModified=Project {0} has been concurrently modified",
        "# {0} - project name",
        "# {1} - reload reason",
        "# {2} - number of files",
        "CONFIRM_ProjectFileSave={1}: Loading project {0} requires {2} file(s) to be saved. Proceed ?",
        "# {0} - project name",
        "# {1} - file name",
        "ERR_SaveFailed=Error saving file {1}",
        "# {0} - project name",
        "ERR_SaveProjectFailed=Error saving project {1}"
    })
    public static CompletionStage<Project> withProjectState(Project p, ProjectStateRequest stateRequest) {
        Pair<ProjectReloadInternal.StateRef, ProjectState> projectData = ProjectReloadInternal.get().getProjectState0(p);
        ProjectState ps = projectData.second();
        boolean doReload = checkConsistency(ps, stateRequest, stateRequest.isForceReload());

        if (!doReload) {
            return CompletableFuture.completedStage(p);
        }
        String reason = stateRequest.getReason();
        if (reason == null) {
            reason = Bundle.TEXT_RefreshProject(projectName(p));
            stateRequest = stateRequest.reloadReason(reason);
        }
        final String fReason = reason;
        final ProjectStateRequest fRequest = stateRequest;
        
        if (!ps.getModifiedFiles().isEmpty()) {
            if (stateRequest.isSaveModifications()) {
                NotifyDescriptor.Confirmation confirm = new NotifyDescriptor.Confirmation(
                        Bundle.CONFIRM_ProjectFileSave(projectName(p), reason, ps.getModifiedFiles().size()), 
                        reason,
                        NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.QUESTION_MESSAGE);
                return DialogDisplayer.getDefault().notifyFuture(confirm).thenCompose((nd) -> {
                    if (nd.getValue() == NotifyDescriptor.OK_OPTION) {
                        // must save through this API instead of SaveCookie.
                        List<Union2<TextDocumentEdit, ResourceOperation>> documentChanges = new ArrayList<>();
                        final List<FileObject> mods = new ArrayList<>(ps.getModifiedFiles());
                        mods.forEach(f -> {
                            documentChanges.add(Union2.createFirst(new TextDocumentEdit(URLMapper.findURL(f, URLMapper.EXTERNAL).toExternalForm(), Collections.emptyList())));
                        });
                        WorkspaceEdit wkEdit = new WorkspaceEdit(documentChanges);
                        CompletableFuture<Project> f = new CompletableFuture<>();
                        
                        WorkspaceEdit.applyEdits(Collections.singletonList(wkEdit), true).
                            thenCompose((list) -> ProjectReloadInternal.get().withProjectState2(projectData.first(), p, fRequest)).
                            thenAccept(p2 -> f.complete(p2)).
                            exceptionally(t -> {
                                ProjectOperationException pex;
                                if (t instanceof ResourceModificationException) {
                                    ResourceModificationException ex = (ResourceModificationException)t;
                                    FileObject failedFile = mods.get(ex.getFailedEditIndex());
                                    pex = new ProjectOperationException(p, ProjectOperationException.State.OUT_OF_SYNC, 
                                        Bundle.ERR_SaveFailed(projectName(p), Collections.singleton(failedFile)));
                                } else {
                                    pex = new ProjectOperationException(p, ProjectOperationException.State.ERROR, 
                                        Bundle.ERR_SaveProjectFailed(projectName(p)));
                                }
                                pex.initCause(t);
                                f.completeExceptionally(pex);
                                return null;
                            });
                        return f;
                    } else {
                        // fail with OUT_OF_SYNC
                        ProjectOperationException ex = new ProjectOperationException(p, ProjectOperationException.State.OUT_OF_SYNC, 
                                Bundle.ERR_ProjectFilesModified(projectName(p), ps.getModifiedFiles().size()),
                                new HashSet<>(ps.getModifiedFiles()));
                        return CompletableFuture.<Project>failedStage(ex);
                    }
                });
            } else {
                ProjectOperationException ex = new ProjectOperationException(p, ProjectOperationException.State.OUT_OF_SYNC, 
                        Bundle.ERR_ProjectFilesModified(projectName(p), ps.getModifiedFiles().size()),
                        new HashSet<>(ps.getModifiedFiles()));
                return CompletableFuture.<Project>failedStage(ex);
            }
        }
        return ProjectReloadInternal.get().withProjectState2(projectData.first(), p, stateRequest);
    }
    
    /**
     * Project trust level.
     */
    public enum ProjectTrust {
        /**
         * Project trust is not supported. Project is implicitly trusted.
         */
        UNSUPORTED,
        
        /**
         * Do not trust the project.
         */
        UNTRUSTED,
        
        /**
         * The project is trusted, at least in this session.
         */
        TRUSTED,
        
        /**
         * The project is trusted permanently.
         */
        TRUSTED_PERMANENTLY;
        
        public boolean isTrusted() {
            return this != UNTRUSTED;
        }
        
        public boolean isSupported() {
            return this != UNSUPORTED;
        }
        
        public boolean isPermanent() {
            return this == TRUSTED_PERMANENTLY;
        }
    }

    /**
     * Checks if the project is trusted. If project type does not require granting trust to load the project information,
     * the function will always return {@code true}. If {@code permanent} is false, then the project effective state
     * is returned. If {@code permanent} is true, the result will be the project's recorded trust setting. 
     * 
     * @param p the project
     * @param permanent if true, then permanent setting is checked.
     * @return 
     */
    public static ProjectTrust getProjectTrust(Project p) {
        ProjectTrustImplementation pi = p.getLookup().lookup(ProjectTrustImplementation.class);
        return pi == null ? ProjectTrust.UNSUPORTED : pi.checkTrusted();
    }
    
    /**
     * Checks if the project supports trust settings. It may return either {@link ProjectTrust#UNSUPORTED} or
     * the set of allowed / supported settings.
     * @param p project
     * @return supported settings.
     */
    public static EnumSet<ProjectTrust> checkTrustSupported(Project p) {
        ProjectTrustImplementation pi = p.getLookup().lookup(ProjectTrustImplementation.class);
        return pi == null ? EnumSet.of(ProjectTrust.UNSUPORTED) : pi.supportedOptions();
    }
    
    /**
     * Marks the project as trusted, so subsequent project requests can load more metadata about 
     * the project. 
     * 
     * @param p project
     * @param trust trust level. {@link ProjectTrust#UNSUPORTED} is ignored.
     * @return true, if the operation succeeded.
     */
    public static boolean setProjectTrust(Project p, ProjectTrust trust) {
        if (trust == ProjectTrust.UNSUPORTED) {
            return false;
        }
        ProjectTrustImplementation pi = p.getLookup().lookup(ProjectTrustImplementation.class);
        pi.makeTrusted(trust);
        return pi.checkTrusted().isTrusted() == trust.isTrusted();
    }
    
    static {
        ReloadApiAccessor.set(new ReloadApiAccessor() {
            @Override
            public ProjectState createState(Project project, long timestamp, Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts, Quality status, boolean consistent, boolean valid, Collection<FileObject> loaded, Collection<FileObject> modified) {
                return new ProjectReload.ProjectState(project, timestamp, parts, status, consistent, valid, loaded, modified);
            }
            
            @Override
            public void updateProjectState(ProjectReload.ProjectState ps, boolean inconsistent, boolean invalid, Collection<FileObject> modified) {
                boolean fire = false;
                if (inconsistent) {
                    fire |= ps.consistent;
                    ps.consistent = false;
                }
                if (invalid) {
                    fire |= ps.valid;
                    ps.valid = false;
                }
                if (modified != null) {
                    fire = true;
                    ps.modified = modified;
                }
                if (fire) {
                    queueStateChange(ps);
                }
            }
        });
    }
}
