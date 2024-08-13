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
package org.netbeans.modules.project.dependency.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.netbeans.modules.project.dependency.ProjectReload;
import org.netbeans.modules.project.dependency.ProjectReload.ProjectState;
import org.netbeans.modules.project.dependency.ProjectReload.ProjectStateRequest;
import org.netbeans.modules.project.dependency.spi.ProjectReloadImplementation;
import org.netbeans.modules.project.dependency.spi.ProjectReloadImplementation.ProjectStateData;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.BaseUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.Pair;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author sdedic
 */
public class ProjectReloadInternal {
    private static Reference<ProjectReloadInternal> INSTANCE = new WeakReference<>(null);
    
    public static synchronized ProjectReloadInternal get() {
        ProjectReloadInternal i = INSTANCE.get();
        if (i != null) {
            return i;
        }
        i = new ProjectReloadInternal();
        INSTANCE = new WeakReference<>(i);
        return i;
    }
    
    private static final Logger LOG = Logger.getLogger(ProjectReloadInternal.class.getName());
    
    /**
     * Timeout to clear stale ProjectStates from the cache.
     */
    private static final int STATE_TIMEOUT_MS = 10 * 1000;

    /**
     * Dedicated thread to fire state listeners.
     */
    private static final RequestProcessor STATE_CLEANER = new RequestProcessor(ProjectReload.class.getName());
    
    /**
     * Timed reference, that expires after 30 seconds.
     */
    public static final class StateRef extends WeakReference<ProjectReload.ProjectState> implements Runnable, ChangeListener {
        private volatile long lastAccessed;
        private final RequestProcessor.Task evictTask  = STATE_CLEANER.post(this);
        private final ProjectStateKey key;
        private volatile ProjectReload.ProjectState hard;

        // tracks additional internal properties, originally a separate object.
        volatile boolean internalValid = true;
        List<Reference<ProjectReload.ProjectState>> previous;

        public StateRef(ProjectStateKey key, ProjectReload.ProjectState referent) {
            super(referent, BaseUtilities.activeReferenceQueue());
            referent.addChangeListener(this);
            this.key = key;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            // do nothing :) this is just to keep this object as long as State exists.
        }

        @Override
        public ProjectReload.ProjectState get() {
            ProjectReload.ProjectState o = hard;
            if (o == null) {
                o = super.get();
            }
            if (o != null) {
                hard = o;
                if (lastAccessed == 0) {
                    evictTask.schedule(STATE_TIMEOUT_MS);
                }
                lastAccessed = System.currentTimeMillis();
            }
            return o; 
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
        private final Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts;
        private volatile int hashCode = -1;
        
        public ProjectStateKey(Project project, Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts) {
            this.parts = parts;
        }
        
        @Override
        public int hashCode() {
            if (hashCode != -1) {
                return hashCode;
            }
            int hash = 7;
            for (Pair<?, ProjectStateData> d : parts.values()) {
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
                Pair<?, ProjectStateData> d = parts.get(impl);
                Pair<?, ProjectStateData> od = other.parts.get(impl);
                if (!Objects.equals(d, od)) {
                    return false;
                }
            }
            return true;
        }
    }
    
    /**
     * Caches ProjectStates. States are kept by reference, so fogetting ProjectState will eventually 
     * evict it + its project from this Cache. Only <b>last known</b> state is kept here - each
     * request to reload will replace the entry for the project.
     */
    private static final Map<ProjectStateKey, StateRef> STATE_CACHE = new WeakHashMap<>();
    
    static class StateListener extends FileChangeAdapter implements ChangeListener, LookupListener {

        final Project p;
        final StateRef tracker;

        Collection<FileObject> watchedFiles;
        Collection<Pair<Lookup.Result, LookupListener>> lcls = new ArrayList<>();
        Collection<FileChangeListener> fcls = new ArrayList<>();

        public StateListener(Project p, StateRef ref, Collection<ProjectStateData> parts) {
            this.p = p;
            this.tracker = ref;
        }
        
        void init() {
            updateFileListeners();
            for (Pair<?, ProjectStateData> d : tracker.key.parts.values()) {
                DependencySpiAccessor.get().addProjectStateListener(d.second(), this);
            }
        }

        private boolean updateFileListeners() {
            Collection<FileObject> updatedFiles = new LinkedHashSet<>();
            for (Pair<?, ProjectStateData> sd : tracker.key.parts.values()) {
                updatedFiles.addAll(sd.second().getFiles());
            }
            Collection<FileObject> obsoletes;
            Collection<FileObject> newFiles = new HashSet<>(updatedFiles);

            if (this.watchedFiles != null) {
                obsoletes = new HashSet<>(this.watchedFiles);
                obsoletes.removeAll(updatedFiles);
                newFiles.removeAll(watchedFiles);
            } else {
                obsoletes = Collections.emptySet();
            }
            LOG.log(Level.FINER, "{0}: UpdateListeners called. Added: {1}, removed: {2}", new Object[]{
                p, newFiles, obsoletes
            });
            if (obsoletes.isEmpty() && newFiles.isEmpty()) {
                return false;
            }
            List<FileChangeListener> listeners = new ArrayList<>();
            if (!obsoletes.isEmpty()) {
                Iterator<FileChangeListener> lit = this.fcls.iterator();
                for (Iterator<FileObject> fit = this.watchedFiles.iterator(); fit.hasNext();) {
                    FileChangeListener l = lit.next();
                    FileObject f = fit.next();
                    if (obsoletes.contains(f)) {
                        f.removeFileChangeListener(l);
                    } else {
                        listeners.add(l);
                    }
                }
            } else if (this.watchedFiles != null && !this.watchedFiles.isEmpty()) {
                listeners.addAll(fcls);
            }
            for (FileObject f : newFiles) {
                FileChangeListener l = FileUtil.weakFileChangeListener(this, f);
                f.addFileChangeListener(l);
                listeners.add(l);
            }

            this.watchedFiles = updatedFiles;
            this.fcls = listeners;
            return true;
        }

        void detachListeners() {
            Iterator<FileChangeListener> fl = fcls.iterator();
            for (FileObject f : watchedFiles) {
                f.removeFileChangeListener(fl.next());
            }
            for (Pair<Lookup.Result, LookupListener> p : lcls) {
                p.first().removeLookupListener(p.second());
            }
        }

        private void reportFile(FileObject f, long t) {
            ProjectReload.ProjectState state = this.tracker.get();
            if (state == null) {
                detachListeners();
                return;
            }
            long t2 = t == -1 ? f.lastModified().getTime() : t;
            if (t2 < state.getTimestamp()) {
                return;
            }
            ReloadApiAccessor.get().updateProjectState(state, true, false, null);
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            ProjectReload.ProjectState state = this.tracker.get();
            if (state == null) {
                detachListeners();
                return;
            }

            ProjectStateData d = (ProjectStateData) e.getSource();
            boolean c = d.isConsistent();
            boolean v = d.isValid();
            Collection<FileObject> obs = d.getFiles();
            boolean fire = false;
            
            boolean invalid = false;
            boolean inconsistent = false;
            Collection<FileObject> setModified = null;

            synchronized (tracker) {
                if (!state.getModifiedFiles().containsAll(obs)) {
                    Set<FileObject> s = new LinkedHashSet<>(state.getModifiedFiles());
                    if (s.addAll(obs)) {
                        setModified = s;
                    }
                }
                if (state.isConsistent() && !c) {
                    inconsistent = true;
                }
                if (state.isValid() && !v) {
                    invalid = true;
                }
                updateFileListeners();
            }
            if (!v) {
                // some of the providers invalidated the data/status
                tracker.internalValid = false;
                // invalid states 
                detachListeners();
            }
            LOG.log(Level.FINE, "Got state change on {0}, firing change: {1}", new Object[]{d, fire});
            ReloadApiAccessor.get().updateProjectState(state, inconsistent, invalid, setModified);
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            reportFile(fe.getFile(), System.currentTimeMillis());
        }

        @Override
        public void fileChanged(FileEvent fe) {
            reportFile(fe.getFile(), -1);
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            reportFile(fe.getFile().getParent(), fe.getFile().lastModified().getTime());
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            ProjectReload.ProjectState state = this.tracker.get();
            if (state == null) {
                detachListeners();
                return;
            }

            Lookup.Result lr = (Lookup.Result) ev.getSource();
            if (!lr.allItems().isEmpty()) {
                ReloadApiAccessor.get().updateProjectState(state, true, false, null);
            }
        }
    }
    
    private static ProjectState doCreateState(Project p, Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts) {
        ProjectReload.Quality status = null;
        boolean consistent = true;
        Set<FileObject> modified = new LinkedHashSet<>();
        Set<FileObject> loadedFiles = new LinkedHashSet<>();
        long timestamp = Long.MAX_VALUE;
        boolean valid = true;
        
        for (Pair<?, ProjectStateData> implData : parts.values()) {
            ProjectStateData data = implData.second();
            if (data == null) {
                continue;
            }
            ProjectReload.Quality q = data.getQuality();
            if (status == null || q.isWorseThan(status)) {
                status = q;
            }
            Collection<FileObject> mods = data.getFiles();
            if (mods != null) {
                loadedFiles.addAll(mods);
            }
            long time = data.getTimestamp();
            if (time < timestamp) {
                timestamp = time;
            }
            valid &= data.isValid();
        }
        
        for (FileObject f : loadedFiles) {
            long t = f.lastModified().getTime();
            if (f.getLookup().lookup(SaveCookie.class) != null) {
                modified.add(f);
                consistent = false;
                break;
            }
            if (timestamp > 0 && t > timestamp) {
                consistent = false;
            }
        }
        return ReloadApiAccessor.get().createState(p, timestamp, parts, status, consistent, valid, loadedFiles, modified);
    }

    private static Pair<StateRef, ProjectState> createState(StateRef prevRef, ProjectReload.ProjectState previous, Project p, Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts, ProjectStateKey key) {
        ProjectReload.Quality status = null;
        boolean consistent = true;
        Set<FileObject> modified = new LinkedHashSet<>();
        Set<FileObject> loadedFiles = new LinkedHashSet<>();
        long timestamp = Long.MAX_VALUE;
        boolean valid = true;
        
        Collection<ProjectStateData> collected = new ArrayList<>();
        
        for (Pair<?, ProjectStateData> implData : parts.values()) {
            ProjectStateData data = implData.second();
            if (data == null) {
                continue;
            }
            collected.add(data);
            ProjectReload.Quality q = data.getQuality();
            if (status == null || q.isWorseThan(status)) {
                status = q;
            }
            Collection<FileObject> mods = data.getFiles();
            if (mods != null) {
                loadedFiles.addAll(mods);
            }
            long time = data.getTimestamp();
            if (time < timestamp) {
                timestamp = time;
            }
            valid &= data.isValid();
        }
        
        for (FileObject f : loadedFiles) {
            long t = f.lastModified().getTime();
            if (f.getLookup().lookup(SaveCookie.class) != null) {
                modified.add(f);
                consistent = false;
                break;
            }
            if (timestamp > 0 && t > timestamp) {
                consistent = false;
            }
        }
        
        ProjectReload.ProjectState state = ReloadApiAccessor.get().createState(p, timestamp, parts, status, consistent, valid, loadedFiles, modified);
        StateRef ref = new StateRef(key, state);
        StateListener l = new StateListener(p, ref, collected);
        
        List<Reference<ProjectReload.ProjectState>> olds = new ArrayList<>();
        synchronized (STATE_CACHE) {
            StateRef r = STATE_CACHE.get(key);
            ProjectReload.ProjectState cur = r == null ? null : r.get();
            if (cur != null && r.internalValid) {
                return Pair.of(r, cur);
            }
            l.init();
            if (cur != null && r.previous != null) {
                olds.addAll(r.previous);
                olds.add(new WeakReference<>(cur));
            }
            if (previous != null) {
                if (prevRef.previous != null) {
                    olds.addAll(prevRef.previous);
                }
                olds.add(new WeakReference<>(previous));
            }
            if (!olds.isEmpty()) {
                ref.previous = olds;
            }
            STATE_CACHE.put(key, ref);
            return Pair.of(ref, state);
        }
    }
    
    public ProjectState getProjectState(Project p) {
        return getProjectState0(p).second();
    }
    
    public Pair<StateRef, ProjectState> getProjectState0(Project p) {
        Collection<? extends ProjectReloadImplementation<Object>> col = p.getLookup().lookupAll(ProjectReloadImplementation.class);
        Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts = new WeakHashMap<>();
        
        for (ProjectReloadImplementation impl : col) {
            Object implData = impl.getProjectData();
            if (implData == null) {
                continue;
            }
            ProjectStateData data = impl.getState(implData);
            parts.put(impl, Pair.of(implData, data));
        }
        ProjectStateKey pk = new ProjectStateKey(p, parts);
        ProjectState previous;
        
        StateRef r;
        synchronized (STATE_CACHE) {
             r = STATE_CACHE.get(pk);
            previous = r == null ? null : r.get();
            if (previous != null && r.internalValid) {
                return Pair.of(r, previous);
            }
            STATE_CACHE.remove(pk);
        }
        return createState(r, previous, p, parts, pk);
    }

    public static boolean checkConsistency(ProjectState ps, ProjectStateRequest stateRequest, boolean forceReload) {
        boolean doReload = !ps.isValid() || forceReload;
        
        if (stateRequest.isConsistent() && !ps.isConsistent()) {
            doReload = true;
        }
        if (ps.getQuality().isWorseThan(stateRequest.getMinQuality())) {
            doReload = true;
        }
        return doReload;
    }
    
    private static boolean satisfies(ProjectStateRequest next, ProjectStateRequest existing) {
        if (existing.isForceReload() && !existing.isForceReload()) {
            return false;
        }
        if (!existing.isOfflineOperation() && existing.isOfflineOperation()) {
            return false;
        }
        if (next.isSaveModifications() && !existing.isSaveModifications()) {
            return false;
        }
        if (existing.getMinQuality().isWorseThan(next.getMinQuality())) {
            return false;
        }
        return true;
    }
    
    @NbBundle.Messages({
        "# {0} - project name",
        "TEXT_ConcurrentlyModified=Project {0} has been concurrently modified",
    })
    public CompletionStage<Project> withProjectState2(StateRef ref, Project p, ProjectStateRequest stateRequest) {
        CompletableFuture<Project> retVal = new CompletableFuture<>();
        synchronized (pendingReloads) {
            Collection<R> requests = pendingReloads.computeIfAbsent(p, (p2) -> new ArrayList<>());
            for (R pr : requests) {
                if (satisfies(pr.request, stateRequest)) {
                    return pr.pending;
                }
            }
            requests.add(new R(retVal, stateRequest));
        }
        Collection<? extends ProjectReloadImplementation> col = p.getLookup().lookupAll(ProjectReloadImplementation.class);
        if (col.isEmpty()) {
            return CompletableFuture.completedFuture(p);
        }
        Iterator<? extends ProjectReloadImplementation> it = col.iterator();
        F[] chainNext = new F[1];
        
        Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts = new WeakHashMap<>();
        
        boolean fforce = stateRequest.isForceReload();
        chainNext[0] = (impl, implData) -> {
            ProjectStateData data = impl.getState(implData);
            parts.put(impl, Pair.of(implData, data));
            if (it.hasNext()) {
                ProjectReloadImplementation<Object> nextImpl = it.next();
                try {
                    Object psd = fforce ? null : ref.key.parts.get(impl).first();
                    return nextImpl.reload(stateRequest, psd).thenCompose(d -> chainNext[0].reload(impl, d));
                } catch (ProjectOperationException ex) {
                    return CompletableFuture.<Project>failedStage(ex).toCompletableFuture();
                }
            } else {
                ProjectStateKey key = new ProjectStateKey(p, parts);
                Pair<StateRef, ProjectState> nr = createState(null, null, p, parts, key);
                ProjectState ns = nr.second();
                
                if (checkConsistency(ns, stateRequest, false)) {
                    ProjectOperationException ex = new ProjectOperationException(p, ProjectOperationException.State.OUT_OF_SYNC, 
                            Bundle.TEXT_ConcurrentlyModified(ProjectUtils.getInformation(p).getDisplayName()),
                            new HashSet<>(ns.getModifiedFiles()));
                    return CompletableFuture.<Project>failedStage(ex);
                } else {
                    return CompletableFuture.completedStage(p);
                }
            }
        };
        ProjectReloadImplementation firstImpl = it.next();
        Object psd = fforce ? null : ref.key.parts.get(firstImpl).first();
        firstImpl.reload(stateRequest, psd).thenCompose(d -> chainNext[0].reload(firstImpl, d)).handle((x, t) -> {
            if (t != null) {
                retVal.completeExceptionally((Throwable)t);
            } else {
                retVal.complete(p);
            }
            return null;
        });
        return retVal;
    }

    // Just dummy functional interface, otherwise I would need to declare an array of generic Function items.
    private interface F {
        CompletionStage<Project> reload(ProjectReloadImplementation<Object> impl, Object p);
    }
    
    static class R {
        private final CompletionStage<Project> pending;
        private final ProjectReload.ProjectStateRequest request;

        public R(CompletableFuture<Project> pending, ProjectReload.ProjectStateRequest request) {
            this.pending = pending;
            this.request = request;
        }
    }
    
    private static Map<Project, Collection<R>>    pendingReloads = new WeakHashMap<>();
    
    public class LoadContextImpl {
        private final Project project;
        private final ProjectStateData cachedData;
        private final Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts;

        public LoadContextImpl(Project project, ProjectStateData cachedData, Map<ProjectReloadImplementation<?>, Pair<?, ProjectStateData>> parts) {
            this.project = project;
            this.cachedData = cachedData;
            this.parts = parts;
        }
        
        private InstanceContent ic = new InstanceContent();
        private Lookup loadLookup = new AbstractLookup(ic);
        private boolean retry;
        private Lookup stateImpl;
        private ProjectState cachedState;

        public ProjectStateData getCachedData() {
            return cachedData;
        }

        public ProjectState getCachedState() {
            return cachedState;
        }
        
        public ProjectState partialStateImpl() {
            if (cachedState != null) {
                return cachedState;
            }
            return cachedState = doCreateState(project, parts);
        }
        
        public <T> T contextDataImpl(Class<T> dataClass, Function<Class<T>, T> factory) {
            T data = loadLookup.lookup(dataClass);
            if (data == null && factory != null) {
                data = factory.apply(dataClass);
                if (data != null) {
                    ic.add(data);
                }
            }
            return data;
        }
        
        public void retryReloadImpl() {
            this.retry = true;
        }
    }
    
}
