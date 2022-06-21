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
package org.netbeans.modules.maven.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.api.project.Project;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.embedder.MavenEmbedder;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.Scope;
import org.netbeans.modules.project.dependency.Scopes;
import org.netbeans.modules.project.dependency.SourceLocation;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;

/**
 *
 * @author sdedic
 */
@ActionRegistration(displayName = "Dump dependency JSON", asynchronous = true)
@ActionID(category = "Maven", id = DumpJsonAction.ID)
@ActionReferences({
    @ActionReference(path = "Loaders/text/x-maven-pom+xml/Actions"),
    @ActionReference(path = "Projects/org-netbeans-modules-maven/Actions")
})
public class DumpJsonAction implements ActionListener {
    public static final String ID = "org.netbeans.modules.maven.graph.DumpJsonAction";
    
    private final Project ideProject;

    public DumpJsonAction(Project mavenProject) {
        this.ideProject = mavenProject;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        RuntimeException ex = new IllegalStateException();
        if (true) {
            Exceptions.printStackTrace(ex);
            return;
        }
        NbMavenProject p = ideProject.getLookup().lookup(NbMavenProject.class);
        if (p == null) {
            return;
        }
        MavenProject mp = p.getMavenProject();
        //DependencyNode n = DependencyTreeFactory.createDependencyTree(mp, EmbedderFactory.getProjectEmbedder(), "runtime");
        dumpAnyProject(ideProject);
    }
    
    private void dumpAnyProject(Project p) {
        DependencyResult res = ProjectDependencies.findDependencies(p, ProjectDependencies.newQuery(Scopes.RUNTIME));

        ObjectMapper mapper = new ObjectMapper();
        Map<Dependency, Integer> numbers = new HashMap<>();
        
        int[] counter = { 1 };
        final Function<Dependency, Integer> idProvider = (dn) -> numbers.computeIfAbsent(dn, (x) -> counter[0]++);
        JSONArray arr = new JSONArray();
        Queue<Dependency> toProcess = new ArrayDeque<>();
        
        toProcess.add(res.getRoot());

        Dependency d;
        Set<ArtifactSpec> processed = new HashSet<>();
        while ((d = toProcess.poll()) != null) {
            JSONObject dep = new JSONObject();
            try {

                SourceLocation sl = res.getDeclarationRange(d);
                System.err.println(sl);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            ArtifactSpec art = d.getArtifact();
            if (!processed.add(art)) {
                continue;
            }
            dep.put("gav", String.format("%s:%s:%s", art.getGroupId(), art.getArtifactId(), art.getVersionSpec()));
            dep.put("nodeId", "" + idProvider.apply(d));
            
            if (!d.getChildren().isEmpty()) {
                JSONArray deps = new JSONArray();
                dep.put("applicationDependencyNodeIds", deps);
                
                List<Dependency> ch = d.getChildren();
                for (Dependency c : ch) {
                    deps.add("" + idProvider.apply(c));
                }
                toProcess.addAll(d.getChildren());
            }
            arr.add(dep);
        }
        try {
            System.err.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
            /*
            System.err.println(arr.toJSONString().replace("[{", "[\n\t{").replace("},{", "},\n\t{").replace("}]", "}\n]"));
            */
        } catch (JsonProcessingException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    private void directMavenDump(MavenProject mp) {
        ObjectMapper mapper = new ObjectMapper();
        Map<DependencyNode, Integer> numbers = new HashMap<>();
        
        int[] counter = { 1 };
        final Function<DependencyNode, Integer> idProvider = (dn) -> numbers.computeIfAbsent(dn, (x) -> counter[0]++);
        JSONArray arr = new JSONArray();
        Queue<DependencyNode> toProcess = new ArrayDeque<>();
        
        MavenEmbedder embedder = EmbedderFactory.getProjectEmbedder();
        MavenExecutionRequest req = embedder.createMavenExecutionRequest();
        req.setPom(mp.getFile());
        req.setOffline(true);
        
        ProjectBuildingRequest configuration = req.getProjectBuildingRequest();
        configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        configuration.setResolveDependencies(true);
        
        DependencyGraphBuilder depBuilder = embedder.lookupComponent(DependencyGraphBuilder.class);
        DependencyNode n;
        try {
            DefaultMaven maven = (DefaultMaven)embedder.getPlexus().lookup(Maven.class);
            configuration.setRepositorySession(maven.newRepositorySession(req));
            MavenProject copy = mp.clone();
            copy.setProjectBuildingRequest(configuration);
            n = depBuilder.buildDependencyGraph(copy, new ScopeArtifactFilter("runtime"));
            toProcess.add(n);
        } catch (ComponentLookupException | DependencyGraphBuilderException ex) {
            Exceptions.printStackTrace(ex);
        }
        
        DependencyNode d;
        Set<Artifact> processed = new HashSet<>();
        while ((d = toProcess.poll()) != null) {
            JSONObject dep = new JSONObject();
            Artifact art = d.getArtifact();
            if (!processed.add(art)) {
                continue;
            }
            dep.put("gav", String.format("%s:%s:%s", art.getGroupId(), art.getArtifactId(), art.getVersion()));
            dep.put("nodeId", "" + idProvider.apply(d));
            
            if (!d.getChildren().isEmpty()) {
                JSONArray deps = new JSONArray();
                dep.put("applicationDependencyNodeIds", deps);
                
                for (DependencyNode c : d.getChildren()) {
                    deps.add("" + idProvider.apply(c));
                }
                toProcess.addAll(d.getChildren());
            }
            arr.add(dep);
        }
        try {
            System.err.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
            /*
            System.err.println(arr.toJSONString().replace("[{", "[\n\t{").replace("},{", "},\n\t{").replace("}]", "}\n]"));
            */
        } catch (JsonProcessingException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}


