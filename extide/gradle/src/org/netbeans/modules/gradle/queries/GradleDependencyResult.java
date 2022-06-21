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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.modules.gradle.spi.GradleFiles;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.SourceLocation;
import org.openide.util.Lookup;

/**
 *
 * @author sdedic
 */
public class GradleDependencyResult implements DependencyResult {
    private final Project project;
    private final Dependency root;
    private final GradleFiles projectFiles;
    private final Collection<ArtifactSpec> unresolved;
    
    private volatile boolean valid;
    
    // @GuardedBy(this)
    private List<ChangeListener> listeners;

    public GradleDependencyResult(Project project, Dependency root, GradleFiles projectFiles, Collection<ArtifactSpec> unresolved) {
        this.project = project;
        this.root = root;
        this.projectFiles = projectFiles;
        this.unresolved = unresolved;
    }
    
    @Override
    public Project getProject() {
        return project;
    }

    @Override
    public Dependency getRoot() {
        return root;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public Collection<ArtifactSpec> getProblemArtifacts() {
        return Collections.emptyList();
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        synchronized (this) {
            if (listeners == null) {
                listeners = new ArrayList<>();
            }
            listeners.add(l);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (this) {
            if (listeners == null) {
                return;
            }
            listeners.remove(l);
        }
    }

    @Override
    public SourceLocation getDeclarationRange(Dependency d) throws IOException {
        return null;
    }

    @Override
    public SourceLocation getDeclarationRange(Dependency d, DependencySpecPart part) throws IOException {
        return null;
    }

    @Override
    public Lookup getLookup() {
        return Lookup.EMPTY;
    }
}
