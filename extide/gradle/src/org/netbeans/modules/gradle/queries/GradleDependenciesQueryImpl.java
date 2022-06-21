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
package org.netbeans.modules.gradle.queries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.netbeans.api.project.Project;
import org.netbeans.modules.gradle.GradleApiAccessor;
import org.netbeans.modules.gradle.GradleModuleFileCache21;
import org.netbeans.modules.gradle.GradleModuleFileCache21.CachedArtifactVersion;
import org.netbeans.modules.gradle.NbGradleProjectImpl;
import org.netbeans.modules.gradle.api.GradleBaseProject;
import org.netbeans.modules.gradle.api.GradleConfiguration;
import org.netbeans.modules.gradle.api.GradleDependency;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.spi.GradleFiles;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.netbeans.modules.project.dependency.ProjectSpec;
import org.netbeans.modules.project.dependency.Scope;
import org.netbeans.modules.project.dependency.Scopes;
import org.netbeans.modules.project.dependency.spi.ProjectDependenciesImplementation;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author sdedic
 */
@ProjectServiceProvider(service = ProjectDependenciesImplementation.class, projectType = NbGradleProject.GRADLE_PROJECT_TYPE)
public class GradleDependenciesQueryImpl implements ProjectDependenciesImplementation {
    private final Project project;
    
    private ArtifactSpec projectArtifact;
    private DependencyResult dependencyTree;

    public GradleDependenciesQueryImpl(Project project) {
        this.project = project;
    }
    
    @Override
    public ArtifactSpec getProjectArtifact() {
        ArtifactSpec spec = projectArtifact;
        
        synchronized (this) {
            if (spec != null) {
                return spec;
            }
        }
        GradleBaseProject gbp = GradleBaseProject.get(project);
        String group = gbp.getGroup();
        String path = gbp.getPath();
        if (path.startsWith(":")) {
            path = path.substring(1);
        }
        path = path.replace(":", ".");
        spec = ArtifactSpec.createSnapshotSpec(group, path, null, null, gbp.getVersion(), true, null, gbp);
        
        synchronized (this) {
            if (projectArtifact == null) {
                projectArtifact = spec;
            }
        }
        return spec;
    }

    @Override
    public DependencyResult findDependencies(ProjectDependencies.DependencyQuery query) throws ProjectOperationException {
        synchronized (this) {
            if (dependencyTree != null) {
                return dependencyTree;
            }
        }
        
        GradleBaseProject gbp = GradleBaseProject.get(project);
        
        Collection<Scope> scopes = query.getScopes();
        
        Set<String> confNames = new HashSet<>();
        
        if (scopes.contains(Scopes.RUNTIME)) {
            confNames.add("runtimeClasspath");
        }
        if (scopes.contains(Scopes.COMPILE)) {
            confNames.add("compileClasspath");
        }
        if (scopes.contains(Scopes.PROCESS)) {
            // the compile scope will contain these artifacts, too
            confNames.add("annotationProcessor");
        }
        if (scopes.contains(Scopes.TEST_COMPILE)) {
            // Also includes 
            confNames.add("testCompileClasspath");
        }
        if (scopes.contains(Scopes.TEST_RUNTIME)) {
            confNames.add("testRuntimeClasspath");
        }
        if (scopes.contains(Scopes.TEST)) {
            confNames.add("testCompileClasspath");
            confNames.add("testRuntimeClasspath");
        }
        
        GradleDependency projectNode = GradleApiAccessor.instance().getProjectRootNode(gbp);
        GradleDependencyHolder root = new GradleDependencyHolder(projectNode, scopes.iterator().next(), null);
        
        ProjectSpec prjSpec = ProjectSpec.create(gbp.getPath(), project.getProjectDirectory());
        
        return new DependencyBuilder(projectArtifact, 
                prjSpec, gbp, projectNode, root, confNames).build(project);
    }
    
    private class DependencyBuilder {
        final GradleBaseProject gbp;
        final GradleDependency projectNode;
        final GradleDependencyHolder root;
        final Set<String> confNames;
        final ArtifactSpec rootArtifactSpec;
        final ProjectSpec rootProjectSpec;
        
        final Set<ArtifactSpec> problems = new HashSet<>();
        final GradleModuleFileCache21 gradleCache;
        final Map<GradleDependencyHolder, Map<Object, GradleDependencyHolder>> parent2Children = new HashMap<>();
        final Set<ArtifactSpec> unresolvedDeps = new HashSet<>();
        
        Scope scope;
        GradleConfiguration config;

        public DependencyBuilder(
                ArtifactSpec rootArtifact,
                ProjectSpec rootProject,
                GradleBaseProject gbp, GradleDependency projectNode, GradleDependencyHolder root, Set<String> confNames) {
            this.rootArtifactSpec = rootArtifact;
            this.rootProjectSpec = rootProject;
            this.gbp = gbp;
            this.projectNode = projectNode;
            this.root = root;
            this.confNames = confNames;
            // TODO: maybe the project can have a specific Gradle User Home ?
            this.gradleCache = GradleModuleFileCache21.getGradleFileCache();
        }
        
        private GradleDependencyResult build(Project p) {
            NbGradleProjectImpl nbgi = p.getLookup().lookup(NbGradleProjectImpl.class);
            GradleFiles files = nbgi.getGradleFiles();
            
            Dependency root = makeDependencies();
            return new GradleDependencyResult(p, root, files, unresolvedDeps);
        }
        
        private Dependency makeDependencies() {
            for (String s : confNames) {
                oneConfiguration(s);
            }
            return createDependency(rootProjectSpec, rootArtifactSpec, root);
        }
        
        private Dependency createDependency(ProjectSpec prj, ArtifactSpec art, GradleDependencyHolder h) {
            Map<Object, GradleDependencyHolder> holders = parent2Children.get(h);
            List<Dependency> children = new ArrayList<>();
            for (Object k : holders.keySet()) {
                if (k instanceof ProjectSpec) {
                    children.add(createDependency((ProjectSpec)prj, null, h));
                } else if (k instanceof ArtifactSpec) {
                    children.add(createDependency(null, (ArtifactSpec)k, h));
                }
            }
            return Dependency.create(prj, art, h.getScope(), children, h);
        }
        
        /**
         * Merges dependencies from a configuration with the previously discovered ones.
         * Only adds new dependencies, the existing ones are retained and children of old+new are merged
         * again.
         * @param n Configuration name
         */
        private void oneConfiguration(String n) {
            config = gbp.getConfigurations().get(n);
            if (config == null) {
                return;
            }

            switch (n) {
                case "runtimeClasspath":
                    scope = Scopes.RUNTIME;
                    break;
                case "compileClasspath":
                    scope = Scopes.COMPILE;
                    break;
                case "annotationProcessor":
                    scope = Scopes.PROCESS;
                    break;
                case "testCompileClasspath":
                    scope = Scopes.TEST_COMPILE;
                    break;
                case "testRuntimeClasspath":
                    scope = Scopes.TEST_RUNTIME;
                    break;
            }

            for (GradleDependency.ModuleDependency m : config.getModules()) {
                addModule(root, m);
            }
            
            for (GradleDependency.ProjectDependency p : config.getProjects()) {
                addProject(root, p);
            }
            
            for (GradleDependency.UnresolvedDependency u : config.getUnresolved()) {
                addUnresolved(root, u);
            }
            
            for (GradleDependencyHolder h : parent2Children.getOrDefault(root, Collections.emptyMap()).values()) {
                processNode(h, h.getDependency());
            }
        }
        
        private GradleDependencyHolder addUnresolved(GradleDependencyHolder parent, GradleDependency.UnresolvedDependency u) {
            String id = u.getId();
            String[] gav = id.split(":");
            String v = gav.length > 2 ? gav[2] : "";
            ArtifactSpec spec = ArtifactSpec.createVersionSpec(
                    gav[0], gav[1], null, null, v, false, null, u);
            
            GradleDependencyHolder nh = new GradleDependencyHolder(u, scope, root);
            problems.add(spec);
            return addChild(parent, spec, nh);
        }
        
        private GradleDependencyHolder addProject(GradleDependencyHolder parent, GradleDependency.ProjectDependency p) {
            FileObject dir = FileUtil.toFileObject(p.getPath());
            ProjectSpec spec = ProjectSpec.create(p.getId(), dir);
            GradleDependencyHolder nh = new GradleDependencyHolder(p, scope, root);
            return addChild(parent, spec, nh);
        }
        
        private GradleDependencyHolder addModule(GradleDependencyHolder parent, GradleDependency.ModuleDependency m) {
            FileObject fo = null;

            CachedArtifactVersion cav = gradleCache.resolveModule(m.getId());
            if (cav != null) {
                fo = FileUtil.toFileObject(cav.getBinary().getPath().toFile());
            }
            ArtifactSpec spec = ArtifactSpec.createVersionSpec(
                    m.getGroup(), m.getName(), null, null, m.getVersion(), false, fo, m);

            GradleDependencyHolder nh = new GradleDependencyHolder(m, scope, root);
            return addChild(parent, spec, nh);
        }
        
        private GradleDependencyHolder addChild(GradleDependencyHolder parent, Object key, GradleDependencyHolder child) {
            GradleDependencyHolder nh = parent2Children.computeIfAbsent(parent, p -> new HashMap<>()).
                putIfAbsent(key, child);
            return nh == null ? child : nh;
        }
        
        private void processNode(GradleDependencyHolder node, GradleDependency... path) {
            Collection<? extends GradleDependency> deps = gbp.getDirectDependencies(config, path);
            for (GradleDependency d : deps) {
                if (d instanceof GradleDependency.ProjectDependency) {
                    addModule(node, (GradleDependency.ModuleDependency)d);
                } else if (d instanceof GradleDependency.ModuleDependency) {
                    addProject(node, (GradleDependency.ProjectDependency)d);
                }
            }
            int l;
            GradleDependency[] subPath; 
            if (path == null) {
                subPath = new GradleDependency[1];
                l = 0;
            } else {
                l = path.length;
                subPath = Arrays.copyOf(path, l + 1);
            }
            for (GradleDependencyHolder h : parent2Children.getOrDefault(node, Collections.emptyMap()).values()) {
                subPath[l] = h.getDependency();
                processNode(h, path);
            }
        }
    }
    
    private GradleDependencyHolder makeTree(GradleBaseProject gbp, GradleDependencyHolder parent, GradleDependency... path) {
        GradleDependency[] subPath;
        
        if (path == null || path.length == 0) {
            subPath = new GradleDependency[0];
        } else {
            subPath = Arrays.copyOf(path, path.length + 1);
        }
        List<GradleDependencyHolder> children = new ArrayList<>();
        
        int index = subPath.length - 1;

        String aid;
        String gid;
        String version;
        GradleDependency leaf;
        
        if (path == null || path.length == 0) {
            leaf = null;
            aid = gbp.getName();
            gid = gbp.getGroup();
            version = gbp.getVersion();
        } else {
            leaf = path[path.length - 1];
            aid = leaf.getId();
            if (leaf instanceof GradleDependency.ModuleDependency) {
                GradleDependency.ModuleDependency mdep = (GradleDependency.ModuleDependency)leaf;
                gid = mdep.getGroup();
                aid = mdep.getName();
                version = mdep.getVersion();
            }
        }
        //return new GradleDependencyHolder(children, leaf, 
        return null;
    }
    
}
