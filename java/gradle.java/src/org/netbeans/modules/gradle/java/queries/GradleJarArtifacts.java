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
package org.netbeans.modules.gradle.java.queries;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectActionContext;
import org.netbeans.modules.gradle.api.BuildPropertiesSupport;
import org.netbeans.modules.gradle.api.BuildPropertiesSupport.Property;
import org.netbeans.modules.gradle.api.GradleBaseProject;
import org.netbeans.modules.gradle.api.GradleTask;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.api.execute.ActionMapping;
import org.netbeans.modules.gradle.api.execute.GradleExecConfiguration;
import org.netbeans.modules.gradle.api.execute.RunConfig;
import org.netbeans.modules.gradle.api.execute.RunUtils;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.ProjectArtifactsQuery;
import org.netbeans.modules.project.dependency.spi.ProjectArtifactsImplementation;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.Lookup;
import org.openide.util.WeakListeners;

/**
 *
 * @author sdedic
 */
@ProjectServiceProvider(service = ProjectArtifactsImplementation.class,  projectType = NbGradleProject.GRADLE_PROJECT_TYPE)
public class GradleJarArtifacts implements ProjectArtifactsImplementation<GradleJarArtifacts.Result> {
    private final Project   project;

    public GradleJarArtifacts(Project project) {
        this.project = project;
    }
    
    @Override
    public Result evaluate(ProjectArtifactsQuery.Filter query) {
        GradleBaseProject gbp = GradleBaseProject.get(project);
        NbGradleProject proj = NbGradleProject.get(project);
        if (gbp == null) {
            return null;
        }
        if (proj == null) {
            return null;
        }
        
        if (!(query.getArtifactType() == null || query.getArtifactType().equals(ProjectArtifactsQuery.Filter.TYPE_ALL))) {
            if ("jar".equals(query.getArtifactType())) {
                return null;
            }
        }
        if (!(query.getClassifier() == null || query.getClassifier().equals(ProjectArtifactsQuery.Filter.CLASSIFIER_ANY))) {
            return null;
        }
        
        return new Result(project, query, proj);
    }

    @Override
    public Project findProject(Result r) {
        return r.project;
    }

    @Override
    public List<ArtifactSpec> findArtifacts(Result r) {
        return r.getArtifacts();
    }

    @Override
    public Collection<ArtifactSpec> findExcludedArtifacts(Result r) {
        return Collections.emptySet();
    }

    @Override
    public void handleChangeListener(Result r, ChangeListener l, boolean add) {
        r.addListener(l, add);
    }

    @Override
    public boolean computeSupportsChanges(Result r) {
        return true;
    }
    
    static class Result implements PropertyChangeListener {
        private final Project project;
        private final ProjectArtifactsQuery.Filter filter;
        private final NbGradleProject gradleProject;
        private final List<String> buildTasks;
        private List<ChangeListener> listeners;
        private List<ArtifactSpec> artifacts;

        public Result(Project project, ProjectArtifactsQuery.Filter filter, NbGradleProject gradleProject) {
            this.project = project;
            this.filter = filter;
            this.gradleProject = gradleProject;
            
            String action = ActionProvider.COMMAND_BUILD;
            GradleExecConfiguration cfg = null;
            Lookup lkp = Lookup.EMPTY;
            if (filter.getBuildContext() != null) {
                ProjectActionContext pac = filter.getBuildContext();
                if (pac.getProjectAction() != null) {
                    action = pac.getProjectAction();
                }
                if (pac.getConfiguration() != null) {
                    cfg = (GradleExecConfiguration)pac.getConfiguration();
                }
            }
            ActionMapping mapping = RunUtils.findActionMapping(project, action, cfg);
            final String[] args = RunUtils.evaluateActionArgs(project, action, mapping.getArgs(), lkp);
            RunConfig rc = RunUtils.createRunConfig(project, action, "Searching for artifacts", Lookup.EMPTY, cfg, Collections.emptySet(), args);
            buildTasks = new ArrayList<>(rc.getCommandLine().getTasks());
        }
        
        private void addListener(ChangeListener l, boolean add) {
            synchronized (this) {
                if (add) {
                    if (listeners == null) {
                        listeners = new ArrayList<>();
                        gradleProject.addPropertyChangeListener(WeakListeners.propertyChange(this, project));
                    }
                    listeners.add(l);
                } else if (listeners == null) {
                    return;
                } else {
                    listeners.remove(l);
                }
            }
        }
        
        public List<ArtifactSpec> getArtifacts() {
            List<ArtifactSpec> as = this.artifacts;
            if (as != null) {
                return as;
            }
            as = update();
            synchronized (this) {
                if (this.artifacts == null) {
                    this.artifacts = as;
                }
            }
            return as;
        }
        
        public List<ArtifactSpec> update() {
            GradleBaseProject gbp = GradleBaseProject.get(project);
            Set<String> allTaskNames = new HashSet<>();
            for (String taskName : buildTasks) {
                GradleTask gt = gbp.getTaskByName(taskName);
                for (GradleTask dep : gbp.getTaskPredecessors(gt)) {
                    allTaskNames.add(dep.getName());
                }
            }
            
            List<ArtifactSpec> result = new ArrayList<>();
            // if there's a JAR task, let's inspect the details; otherwise we don't have an artifact (?)
            if (allTaskNames.contains("jar")) {
                addJarArtifacts(gbp, result, false);
            }
            
            return result;
        }
        
        private void addJarArtifacts(GradleBaseProject gbp, List<ArtifactSpec> results, boolean annotateWithBase) {
            String name = gbp.getName();
            String group = gbp.getGroup();
            String version = gbp.isVersionSpecified() ? gbp.getVersion() : null;
            String baseName = name;
            String filename;
            Path path = null;
            String dir = null;
            String classifier = null;
            String appendix = null;
            
            BuildPropertiesSupport props = BuildPropertiesSupport.get(project);
            Property p;
            p = props.findTaskProperty("jar", "archiveFile");
            if (p != null && p.getStringValue() != null) {
                path = Paths.get(p.getStringValue());
            } else {
                p = props.findTaskProperty("jar", "archiveFileName");
                if (p != null && p.getStringValue() != null) {
                    filename = p.getStringValue();
                } else {
                    if (gbp.isVersionSpecified()) {
                        filename = String.format("%s-%s.jar", baseName, version);
                    } else {
                        filename = baseName + ".jar";
                    }
                }

                p = props.findTaskProperty("jar", "destinationDirectory");
                if (p != null && p.getStringValue() != null) {
                    dir = p.getStringValue();
                }
                if (dir == null) {
                    dir = gbp.getBuildDir().toPath().toString();
                }
                if (dir != null && filename != null) {
                    path = Paths.get(dir).resolve(filename);
                }
            }
            
            p = props.findTaskProperty("jar", "archiveAppendix");
            if (p != null && p.getStringValue() != null) {
                appendix = p.getStringValue();
            }
            p = props.findTaskProperty("jar", "archiveClassifier");
            if (p != null && p.getStringValue() != null) {
                classifier = p.getStringValue();
                if (classifier.isEmpty()) {
                    classifier = null;
                }
            }
            String artName = appendix == null ? name : name + "-" + appendix;
            ArtifactSpec.Builder b = ArtifactSpec.builder(group, artName, version, project).
                    classifier(classifier).
                    type("jar");
            if (path != null) {
                b.location(path.toUri());
            }
            if (annotateWithBase) {
                
            }
            results.add(b.build());
        }
        

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
        }
    }
}
