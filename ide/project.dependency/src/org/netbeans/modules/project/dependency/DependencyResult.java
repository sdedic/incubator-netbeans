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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.project.Project;
import org.netbeans.modules.project.dependency.impl.DependencyResultContextImpl;
import org.netbeans.modules.project.dependency.spi.DependencyLocationProvider;
import org.netbeans.modules.project.dependency.spi.ProjectDependenciesImplementation.Result;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;

/**
 * Results of a dependency inspection. Contains dependency graph from the {@link #getRoot}.
 * The result may become invalid, as a result of project changes or dependency changes.
 * The state change will be reported by {@link ChangeListener}. If the client is interested
 * in an updated result, it must perform another dependency scan. Once invalid instance
 * will never turn back to valid. Source mapping can be also monitored, the result will inform
 * a listener added by {@link #addSourceChangeListener} that the {@link SourceLocation}s returned
 * from {@link #getDeclarationRange} may have changed, e.g. as a result of a editor operation.
 * <p>
 * The {@link #getLookup() lookup} can be used to search for project-specific services that
 * can provide further info on the artifacts or dependencies.
 * 
 * PENDING: move to SPI, make API delegating wrapper.
 * @author sdedic
 */
public final class DependencyResult implements Lookup.Provider {
    private final Project project;
    private final List<Result> resultImpls;
    private final Dependency root;
    private final ProjectDependencies.DependencyQuery query;
    private final Lookup lkp;
    private final  ProjectScopes projectScopes;
    
    // @GuardedBy(this)
    private List<ChangeListener>    modelListeners = new ArrayList<>();
    // @GuardedBy(this)
    private List<ChangeListener>    sourceListeners = new ArrayList<>();
    
    private ChangeListener modelL;
    private ChangeListener sourceL;
    
    private final DependencyResultContextImpl context;
    
    private volatile List<DependencyLocationProvider> locationProviders;

    DependencyResult(Project project, List<Result> resultImpls, DependencyResultContextImpl context, ProjectDependencies.DependencyQuery query) {
        this.project = project;
        this.context = context;
        this.resultImpls = resultImpls;
        this.query = query;
        
        Lookup[] parts = resultImpls.stream().map(Result::getLookup).filter(Objects::nonNull).toArray(Lookup[]::new);
        if (parts.length == 0) {
            this.lkp = Lookup.EMPTY;
        } else {
            this.lkp = new ProxyLookup(parts);
        }
        this.projectScopes = new ProjectScopesImpl(context.getScopes());
        /*
        Optional<ProjectSpec> pa = resultImpls.stream().map(Result::getProjectArtifact).findFirst();
        this.projectArtifact = .orElse(null);
        ProjectSpec projectSpec = resultImpls.stream().map(Result::getProjectSpec).findFirst().orElseThrow(() -> new ProjectOperationException(project, 
                ProjectOperationException.State.ERROR, "No project spec"));
        */
        if (context.getProjectSpec()== null) {
            throw new IllegalStateException();
        }
        this.root = Dependency.builder(context.getProjectSpec(), null, project).children(this::getRootChildren).
                create();
    }
    
    List<Dependency> getRootChildren(Dependency root) {
        return context.getRootChildren();
    }
    
    /**
     * Returns the project artifact, if known. May return {@code null}, if the artifact is not declared
     * or the project does not publish an artifact. 
     * @return project artifact or {@code null}.
     */
    public ArtifactSpec getProjectArtifact() {
        return context.getProjectArtifact();
    }

    @Override
    public Lookup getLookup() {
        return lkp;
    }

    /**
     * @return the inspected project
     */
    public Project getProject() {
        return project;
    }
    
    /**
     * The root of the dependency tree. It should represent the project itself. 
     * To get the project artifact, use {@link #getProjectArtifact()}.
     * @return project dependency root.
     */
    public Dependency getRoot() {
        return root;
    }

    public ProjectDependencies.DependencyQuery getQuery() {
        return query;
    }
    
    /**
     * Returns files that may declare dependencies contained in this report.
     * @return project files that define dependencies.
     */
    public Collection<FileObject>   getDependencyFiles() {
        return context.getDependencyFiles();
    }
    
    /**
     * Checks if the data is still valid
     * @return true, if the data is valid
     */
    public boolean isValid() {
        return resultImpls.stream().map(Result::isValid).reduce(true, (a, b) -> a && b);
    }
    
    /**
     * Returns artifacts that may be unavailable or erroneous.
     * @return problem artifacts
     */
    public Collection<ArtifactSpec> getProblemArtifacts() {
        return context.getProblemArtifacts();
    }
    
    private void initSourceProviders() {
        if (locationProviders != null) {
            return;
        }
        List<DependencyLocationProvider> provs = new ArrayList<>(context.getLocationProviders());
        project.getLookup().lookupAll(DependencyLocationProvider.ProjectFactory.class).forEach(f -> {
            DependencyLocationProvider p = f.createLocationProvider(this);
            if (p != null) {
                provs.add(p);
            }
        });
        synchronized (this) {
            // make snapshot
            locationProviders = provs;
        }
    }
    
    /**
     * Registers a Listener to be notified when validity changes, e.g. as a result
     * of project reload.
     * @param l listener
     */
    public void addChangeListener(ChangeListener l) {
        synchronized (this) {
            if (modelListeners.isEmpty()) {
                modelL = new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        fireModelChange();
                    }
                };
                for (Result r : resultImpls) {
                    r.addChangeListener(modelL);
                }
            }
            modelListeners.add(l);
        }
    }
    
    /**
     * Unregisters a previously registered Listener.
     * @param l listener
     */
    public void removeChangeListener(ChangeListener l) {
        synchronized (this) {
            if (modelL == null || !modelListeners.remove(l)) {
                return;
            }
            if (modelListeners.isEmpty()) {
                for (Result r : resultImpls) {
                    r.removeChangeListener(modelL);
                }
            }
        }
    }
    
    /**
     * Registers a listener that gets notified if the source locations could change, as
     * a result of e.g. text edit.
     * @param l the listener
     */
    public void addSourceChangeListener(ChangeListener l) {
        boolean init = false;
        synchronized (this) {
            if (sourceL == null) {
                sourceL = new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        fireModelChange();
                    }
                };
                initSourceProviders();
                init = true;
            }
            sourceListeners.add(l);
        }
        if (init) {
            for (DependencyLocationProvider p : locationProviders) {
                p.addSourceChangeListener(sourceL);
            }
        }
    }
    
    /**
     * Removes a previously registered listener
     * @param l the listener
     */
    public void removeSourceChangeListener(ChangeListener l) {
        boolean e;
        synchronized (this) {
            e = !sourceListeners.isEmpty();
            if (sourceL == null || locationProviders == null || !sourceListeners.remove(l)) {
                return;
            }
            if (locationProviders == null) {
                return;
            }
            e &= sourceListeners.isEmpty();
        }
        if (e) {
            for (DependencyLocationProvider p : locationProviders) {
                p.removeSourceChangeListener(sourceL);
            }
        }
    }
    
    /**
     * Name part of the dependency declaration.
     */
    public static final String PART_NAME = "name"; // NOI18N

    /**
     * Group or publisher part of the dependency declaration.
     */
    public static final String PART_GROUP = "group"; // NOI18N
    
    /**
     * The version part of the dependency declaration.
     */
    public static final String PART_VERSON = "version"; // NOI18N
    
    /**
     * The scope part of the dependency declaration.
     */
    public static final String PART_SCOPE = "scope"; // NOI18N
    
    /**
     * A special part that locates a location appropriate for the surrounding
     * container. For example {@code dependencies} element in Maven or {@code dependencies}
     * block in a gradle script. Use project root or {@code null} as the dependency
     */
    public static final String PART_CONTAINER = "container"; // NOI18N

    /**
     * Attempts to find location where this dependency is declared. May return {@code null} for transitive
     * dependencies, included only indirectly in this project.
     * 
     * @param d the dependency to query
     * @param part a specific part that should be located in the text. 
     * @return the location for the dependency or its part; {@code null} if the
     * source location can not be determined.
     */
    public @CheckForNull SourceLocation getDeclarationRange(@NonNull Dependency d, String part) throws IOException {
        initSourceProviders();
        for (DependencyLocationProvider p : locationProviders) {
            if (p != null) {
                SourceLocation l = p.getDeclarationRange(this, d, part);
                if (l != null) {
                    return l;
                }
            }
        }
        return null;
    }
    
    public @CheckForNull SourceLocation findDeclarationRange(@NonNull Dependency.Path path, String part) throws IOException {
        if (path == null) {
            path = Dependency.Path.of(this, this.getRoot());
        }
        Dependency original = path.getLeaf();
        while (path != null) {
            Dependency now = path.getLeaf();
            SourceLocation l = getDeclarationRange(now, part);
            if (l != null) {
                if (original == now) {
                    return l;
                }
                return new SourceLocation(l.getFile(), l.getStartOffset(), l.getEndOffset(), 
                    now.getArtifact(), original);
            }
            path = path.getParent();
        }
        return null;
    }
    
    /**
     * Returns description of project scopes.
     * @return project scopes.
     */
    public ProjectScopes getScopes() {
        return projectScopes;
    }
    
    // package-private protocol
    private void fireModelChange() {
        
    }
}
