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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.ProjectScopes;
import org.netbeans.modules.project.dependency.ProjectSpec;
import org.netbeans.modules.project.dependency.spi.DependencyLocationProvider;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author sdedic
 */
public class DependencyResultContextImpl {
    private final Set<ArtifactSpec>  problemArtifacts = new LinkedHashSet<>();
    private final List<ProjectScopes> scopes = new ArrayList<>();
    private final Set<FileObject> files = new LinkedHashSet<>();
    private final List<Dependency> rootChildren = new ArrayList<>();
    private final List<DependencyLocationProvider> locationProviders = new ArrayList<>();
    private final ProxyLookup.Controller proxyControl = new ProxyLookup.Controller();
    private final Lookup lkp = new ProxyLookup(proxyControl);
    private ProjectSpec projectSpec;
    private ArtifactSpec projectArtifact;
    private List<Lookup> lookups = new ArrayList<>();

    public void addLookup(Lookup lkp) {
        lookups.add(lkp);
        proxyControl.setLookups(lookups.toArray(Lookup[]::new));
    }
    
    public Lookup getLookup() {
        return lkp;
    }
    
    public void addDependencyFile(FileObject f) {
        this.files.add(f);
    }

    public void setProjectSpec(ProjectSpec projectSpec) {
        if (projectSpec == null) {
            return;
        }
        this.projectSpec = projectSpec;
    }

    public void setProjectArtifact(ArtifactSpec projectArtifact) {
        if (projectArtifact == null) {
            return;
        }
        this.projectArtifact = projectArtifact;
    }
    
    public void addProblemArtifact(ArtifactSpec a) {
        problemArtifacts.add(a);
    }
    
    public void addScope(ProjectScopes s) {
        scopes.add(s);
    }
    
    public void addRootChildren(List<Dependency> deps) {
        rootChildren.addAll(deps);
    }
    
    public void addLocationProvider(DependencyLocationProvider p) {
        locationProviders.add(p);
    }

    public Set<FileObject> getDependencyFiles() {
        return files;
    }

    public List<ProjectScopes> getScopes() {
        return scopes;
    }

    public ProjectSpec getProjectSpec() {
        return projectSpec;
    }

    public ArtifactSpec getProjectArtifact() {
        return projectArtifact;
    }

    public List<Dependency> getRootChildren() {
        return rootChildren;
    }

    public List<DependencyLocationProvider> getLocationProviders() {
        return locationProviders;
    }

    public Set<ArtifactSpec> getProblemArtifacts() {
        return problemArtifacts;
    }
}
