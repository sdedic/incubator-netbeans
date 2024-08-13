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
package org.netbeans.modules.maven.queries;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.project.MavenProject;
import org.netbeans.api.actions.Savable;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.maven.NbMavenProjectImpl;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.modelcache.MavenProjectCache;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.netbeans.modules.project.dependency.spi.ProjectReloadImplementation;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author sdedic
 */
@ProjectServiceProvider(service = ProjectReloadImplementation.class, projectType = NbMavenProject.TYPE)
public class MavenReloadImplementation2 implements ProjectReloadImplementation {
    private final Project project;
    
    /**
     * Allows sanity build ONCE per IDE run and project.
     */
    private volatile boolean sanityBuildAllowed = true;

    public MavenReloadImplementation2(Project project) {
        this.project = project;
    }

    @Override
    public ProjectDependencies.ProjectState getProjectData() {
        return null;
    }

    /**
     * Returns project metadata files. If 'forReload' is false, it just returns project's
     * pom.xml file. If the 'forReload' is true, it will return all the settings and 
     * referenced parent project's files.
     * 
     * @param forReload
     * @return 
     */
    @Override
    public Set<FileObject> findProjectFiles(boolean forReload) {
        NbMavenProject p = project.getLookup().lookup(NbMavenProject.class);
        File pomFile = p.getMavenProject().getFile();
        Set<FileObject> fileSet = new HashSet<>();
        fileSet.add(FileUtil.toFileObject(pomFile));
        if (!forReload) {
            return fileSet;
        }
        MavenExecutionRequest rq = EmbedderFactory.getProjectEmbedder().createMavenExecutionRequest();
        File userSettings = rq.getUserSettingsFile();
        File toolchains = rq.getGlobalToolchainsFile();
        File globalSettings = rq.getGlobalSettingsFile();
        if (userSettings != null && userSettings.exists()) {
            fileSet.add(FileUtil.toFileObject(userSettings));
        }
        if (toolchains != null && toolchains.exists()) {
            fileSet.add(FileUtil.toFileObject(toolchains));
        }
        if (globalSettings != null && globalSettings.exists()) {
            fileSet.add(FileUtil.toFileObject(globalSettings));
        }
        
        Set<Artifact> processed = new HashSet<>();
        ArrayDeque<Artifact> toProcess = new ArrayDeque<>();
        Artifact a = p.getMavenProject().getParentArtifact();
        if (a != null) {
            toProcess.add(a);
        }
        toProcess.addAll(p.getMavenProject().getArtifacts());
        
        while ((a = toProcess.poll()) != null) {
            if (!processed.add(a)) {
                continue;
            }
            File pom = MavenFileOwnerQueryImpl.getInstance().getOwnerPOM(a.getGroupId(), a.getArtifactId(), a.getVersion());
            if (pom != null) {
                Project parentOwner = FileOwnerQuery.getOwner(FileUtil.toFileObject(pom));
                Set<FileObject> fos = ProjectDependencies.findProjectFiles(parentOwner, false);
                for (FileObject f : fos) {
                    if (f != parentOwner.getProjectDirectory()) {
                        fileSet.add(f);
                    }
                }
                NbMavenProject p2 = project.getLookup().lookup(NbMavenProject.class);
                a = p2.getMavenProject().getParentArtifact();
                if (a != null) {
                    toProcess.add(a);
                    }
                toProcess.addAll(p2.getMavenProject().getArtifacts());
            }
        }
        return fileSet;
    }

    @Override
    public long getTimestamp() {
        return project.getLookup().lookup(NbMavenProject.class).getLoadTimestamp();
    }
    
    @NbBundle.Messages({
        "# {0} - number of files",
        "ERR_UnsavedFiles2={0} project files are not saved"
    })
    private Set<FileObject> checkMemoryModified(boolean failIfModified) throws ProjectOperationException {
        Set<FileObject> fos = findProjectFiles(true);
        Set<FileObject> modified = new HashSet<>();
        for (FileObject f : fos) {
            Savable cake = f.getLookup().lookup(Savable.class);
            if (cake != null) {
                modified.add(f);
            }
        }
        if (!modified.isEmpty() && failIfModified) {
            throw new ProjectOperationException(project, ProjectOperationException.State.OUT_OF_SYNC, Bundle.ERR_UnsavedFiles(modified.size()));
        }
        return modified;
    }
    
    private void reloadProject(ProjectDependencies.ProjectStateRequest request) {
    }
    
    private boolean isUpToDate(Set<FileObject> fos) {
        long ts = getTimestamp();
        Date d = new Date(ts);
        for (FileObject f : fos) {
            if (f.lastModified().after(d)) {
                return false;
            }
        }
        return true;
    }
    
    @NbBundle.Messages({
        "# {0} - project name",
        "ERR_PrimingBuildFailed2=Priming build of {0} failed.",
        "# {0} - project name",
        "ERR_UnprimedInOfflineMode2=Priming build for {0} is required, but offline operation was requested."
    })
    class Reloader {
        private final Set<FileObject> files;
        private final ProjectDependencies.ProjectStateRequest request;
        private boolean initial = true;
        private int repeats;
        /**
         * Request allows to perform sanity build. Initialized in constructor, reset after 1st sanity build.
         */
        private boolean sanityBuild;
        
        public Reloader(Set<FileObject> files, ProjectDependencies.ProjectStateRequest request) {
            this.files = files;
            this.request = request;
            this.sanityBuild = !isProjectFullyLoaded();
        }

        private CompletableFuture<Project> checkOrReloadAgain(MavenProject p) {
            long ts = getTimestamp();
            if (ts < 0) {
                ts = System.currentTimeMillis();
            }
            Date d = new Date(ts);
            boolean upToDate = !initial;


            if (sanityBuild) {
                upToDate = false;
            } else {
                for (FileObject f : files) {
                    if (f.lastModified().after(d)) {
                        upToDate = false;
                        break;
                    }
                }
            }
            if (upToDate) {
                return CompletableFuture.completedFuture(project);
            }
            
            if (initial && sanityBuild && sanityBuildAllowed) {
                if (request.isOfflineOperation()) {
                    // refuse to sanity-build in offline mode
                    throw new ProjectOperationException(project, ProjectOperationException.State.OFFLINE, 
                            Bundle.ERR_UnprimedInOfflineMode(ProjectUtils.getInformation(project).getDisplayName()));
                }
                // first the sanity build has to be run.
                ActionProvider ap = project.getLookup().lookup(ActionProvider.class);
                if (ap.isActionEnabled(ActionProvider.COMMAND_PRIME, Lookup.EMPTY)) {
                    sanityBuild = false;
                    sanityBuildAllowed = false;
                    CompletableFuture<Project> primingFinishes = new CompletableFuture<>();
                    
                    // PENDING: report progress through progress API, use request.getReason().
                    ActionProgress prg = new ActionProgress() {
                        @Override
                        protected void started() {
                        }

                        @Override
                        public void finished(boolean success) {
                            if (success) {
                                checkOrReloadAgain(null).thenAccept(x -> {
                                    primingFinishes.complete(x);
                                });
                            } else {
                                String n = ProjectUtils.getInformation(project).getDisplayName();
                                ProjectOperationException ex = new ProjectOperationException(project, ProjectOperationException.State.BROKEN, Bundle.ERR_PrimingBuildFailed(n));
                                primingFinishes.completeExceptionally(ex);
                            }
                        }
                    };
                    ap.invokeAction(ActionProvider.COMMAND_PRIME, Lookups.fixed(prg));
                    return primingFinishes;
                }
            }
            
            NbMavenProjectImpl nbImpl = project.getLookup().lookup(NbMavenProjectImpl.class);
            RequestProcessor.Task t = nbImpl.fireProjectReload(true);
            CompletableFuture<Project> f = new CompletableFuture<>();
            t.addTaskListener((l) -> {
                initial = false;
                nbImpl.getFreshOriginalMavenProject().thenCompose((mp) -> checkOrReloadAgain(p)).thenAccept((x) -> {
                    f.complete(project);
                });
            });
            return f;
        }
    }
    
    boolean isProjectFullyLoaded() {
        NbMavenProject impl = project.getLookup().lookup(NbMavenProject.class);
        return !MavenProjectCache.isIncompleteProject(impl.getMavenProject()) ||
               MavenProjectCache.getFakedArtifacts(impl.getMavenProject()).isEmpty();
    }

    @Override
    public CompletionStage<Project> withReadyState(ProjectDependencies.ProjectStateRequest request) throws ProjectOperationException {
        // if the request allows (!) online operation AND the project was not primed properly, the project MAY be not up-to-date, if the 
        // sanity build was not done.
        Set<FileObject> fos = checkMemoryModified(!request.isIgnoreModifications());
        boolean upToDate = true;

        if (!isProjectFullyLoaded()) {
            upToDate = false;
        } else {
            long ts = getTimestamp();
            if (ts < 0) {
                ts = System.currentTimeMillis();
            }
            if (ts >= 0 && !request.isForceReload()) {
                upToDate = isUpToDate(fos);
                if (upToDate) {
                    return CompletableFuture.completedFuture(project);
                }
            }
        }
        return new Reloader(fos, request).checkOrReloadAgain(null);
    }
}
