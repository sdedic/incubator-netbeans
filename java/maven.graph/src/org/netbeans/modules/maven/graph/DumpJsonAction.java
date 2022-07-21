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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.netbeans.api.project.Project;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.ProjectSpec;
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
    @ActionReference(path = "Projects/org-netbeans-modules-maven/Actions"),
    @ActionReference(path = "Projects/org-netbeans-modules-gradle/Actions"),
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
        if (false) {
            Exceptions.printStackTrace(ex);
            return;
        }
        //DependencyNode n = DependencyTreeFactory.createDependencyTree(mp, EmbedderFactory.getProjectEmbedder(), "runtime");
        dumpAnyProject(ideProject);
    }
    
    private void dumpAnyProject(Project p) {
        DependencyResult res = ProjectDependencies.findDependencies(p, ProjectDependencies.newQuery(Scopes.RUNTIME));
        
        res.addSourceChangeListener(e -> {});

        ObjectMapper mapper = new ObjectMapper();
        Map<Object, Integer> numbers = new HashMap<>();
        
        int[] counter = { 1 };
        final Function<Dependency, Integer> idProvider = (dn) -> {
            ProjectSpec pspec = dn.getProject();
            ArtifactSpec art = dn.getArtifact();
            
            return numbers.computeIfAbsent(art == null ? pspec : art, (x) -> counter[0]++);
        };
        
        JSONArray arr = new JSONArray();
        Queue<Dependency> toProcess = new ArrayDeque<>();
        
        toProcess.add(res.getRoot());

        Dependency d;
        Set<ArtifactSpec> processed = new HashSet<>();
        while ((d = toProcess.poll()) != null) {
            JSONObject dep = new JSONObject();
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
            try {
                SourceLocation loc = res.getDeclarationRange(d, null);
                if (loc != null) {
                    dep.put("start", "" + loc.getStartOffset());
                    dep.put("end", "" + loc.getEndOffset());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
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


