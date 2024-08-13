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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.junit.Assert;
import org.netbeans.api.project.Project;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.project.dependency.impl.DependencyResultContextImpl;
import org.netbeans.modules.project.dependency.spi.ProjectDependenciesImplementation;
import org.netbeans.modules.project.dependency.spi.ProjectDependencyScopes;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 *
 * @author sdedic
 */
public class DependencyTest extends NbTestCase {
    static {
        Class c = ProjectDependencies.class;
        try {
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    DependencyResultContextImpl ctx = new DependencyResultContextImpl(Collections.singletonList(new ProjectDependencyScopes() {
        @Override
        public Collection<? extends Scope> scopes() {
            return Collections.emptyList();
        }

        @Override
        public Collection<? extends Scope> implies(Scope s, boolean direct) {
            return Collections.emptyList();
        }
    }));

    ProjectDependencies.DependencyQuery q;
    P project;

    public DependencyTest(String name) {
        super(name);
    }
    
    static class P implements Project {
        final FileObject wd;

        public P(FileObject wd) {
            this.wd = wd;
        }
        
        @Override
        public FileObject getProjectDirectory() {
            return wd;
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }
        
    }
    
    static class R implements ProjectDependenciesImplementation.Result {
        volatile boolean valid;
        volatile Lookup lkp;
        
        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void addChangeListener(ChangeListener l) {
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
        }

        @Override
        public Lookup getLookup() {
            return lkp;
        }
    }
    
    private DependencyResult createResult(Dependency root) {
        q = ProjectDependencies.newBuilder().build();
        ctx.setProjectArtifact(root.getArtifact());
        ctx.setProjectSpec(root.getProject());
        ctx.addRootChildren(root.getChildren());
        
        return new DependencyResult(project, List.of(new R()), ctx, q);
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        project = new P(FileUtil.toFileObject(getWorkDir()));
    }
    
    /**
     * Checks that two dependencies are equal. 
     * @throws Exception 
     */
    public void testDependencyEquals() throws Exception {
        Dependency d1 = Dependency.builder(
            ArtifactSpec.make("org.netbeans.test", "projectDependencies"),
            Scopes.RUNTIME, null
            ).create();

        Dependency d2 = Dependency.builder(
            ArtifactSpec.make("org.netbeans.test", "projectDependencies2"),
            Scopes.RUNTIME, null
            ).create();
        
        Assert.assertNotEquals(d1, d2);
        Assert.assertNotEquals(d2, d1);
        
        d2 = Dependency.builder(
            ArtifactSpec.make("org.netbeans.test", "projectDependencies"),
            Scopes.RUNTIME, null
            ).create();
        assertEquals(d1, d2);
        assertEquals(d2, d1);
        assertEquals(d1.hashCode(), d2.hashCode());
    }
    
    public void testRootHasArtifactAndProject() throws Exception {
        Dependency root = Dependency.create(
                ProjectSpec.create("test", project.getProjectDirectory()),
                ArtifactSpec.make("org.netbeans.test", "projectDependencies"),
                Scopes.RUNTIME,
                List.of(), null);
        DependencyResult r = createResult(root);
        
        Dependency.Path p = Dependency.Path.of(r);
        
        // the dependency has the correct root node's artifact.
        assertEquals(r.getRoot().getArtifact(), root.getArtifact());
        assertSame(r, p.getResult());
        assertSame(r.getRoot(), p.getLeaf());
        assertNull(p.getParent());
    }
    
    public void testChildPath() throws Exception {
        List<Dependency> children = List.of(
            Dependency.create(
                    ArtifactSpec.make("org.netbeans.test", "a1"), 
                    Scopes.RUNTIME, List.of(), null)
        );
        Dependency root = Dependency.create(
                ProjectSpec.create("test", project.getProjectDirectory()),
                ArtifactSpec.make("org.netbeans.test", "projectDependencies"),
                Scopes.RUNTIME,
                children, null);
        DependencyResult r = createResult(root);
        
        assertEquals(1, r.getRoot().getChildren().size());
        
        Dependency.Path p = Dependency.Path.of(r, r.getRoot().getChildren().get(0));
        assertNotNull(p.getParent());
        assertSame(r.getRoot(), p.getParent().getLeaf());
        assertEquals(children.get(0), p.getLeaf());
        
        List<Dependency> fromRoot = p.listFromRoot();
        assertEquals(r.getRoot(), fromRoot.get(0));
        assertEquals(children.get(0), fromRoot.get(1));
    }
    
    /**
     * If the Path is formed from list of dependencies INCLUDING the root, the root is skipped when constructing the Path.
     * @throws Exception 
     */
    public void testChildPathIncludingRoot() throws Exception {
        List<Dependency> children = List.of(
            Dependency.create(
                    ArtifactSpec.make("org.netbeans.test", "a1"), 
                    Scopes.RUNTIME, List.of(), null)
        );
        Dependency root = Dependency.create(
                ProjectSpec.create("test", project.getProjectDirectory()),
                ArtifactSpec.make("org.netbeans.test", "projectDependencies"),
                Scopes.RUNTIME,
                children, null);
        DependencyResult r = createResult(root);
        
        assertEquals(1, r.getRoot().getChildren().size());
        
        Dependency.Path p = Dependency.Path.of(r, r.getRoot(), r.getRoot().getChildren().get(0));
        List<Dependency> fromRoot = p.listFromRoot();
        assertEquals(r.getRoot(), fromRoot.get(0));
        assertEquals(children.get(0), fromRoot.get(1));
    }
    
    public void testDetachedPath() throws Exception {
        List<Dependency> detachedList = List.of(
            Dependency.create(
                    ArtifactSpec.make("org.netbeans.test", "projectDependencies"),
                    Scopes.RUNTIME, List.of(), null),
            Dependency.create(
                    ArtifactSpec.make("org.netbeans.test", "a1"), 
                    Scopes.RUNTIME, List.of(), null)
        );
        
        
        List<Dependency> children = List.of(
            Dependency.create(
                    ArtifactSpec.make("org.netbeans.test", "a1"), 
                    Scopes.RUNTIME, List.of(), null)
        );
        Dependency root = Dependency.create(
                ProjectSpec.create("test", project.getProjectDirectory()),
                ArtifactSpec.make("org.netbeans.test", "projectDependencies"),
                Scopes.RUNTIME,
                children, null);
        DependencyResult r = createResult(root);
        Dependency.Path detached = Dependency.Path.detached(detachedList);
     
        Dependency.Path p = Dependency.Path.of(r, r.getRoot(), r.getRoot().getChildren().get(0));
        
        assertEquals(detached, p);
    }
}
