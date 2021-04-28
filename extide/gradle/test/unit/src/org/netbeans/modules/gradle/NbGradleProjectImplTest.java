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
package org.netbeans.modules.gradle;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Random;
import java.util.Set;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.test.MockLookup;

/**
 *
 * @author sdedic
 */
public class NbGradleProjectImplTest extends AbstractGradleProjectTestCase {
    private FileObject prjDir;
    
    public NbGradleProjectImplTest(String name) {
        super(name);
    }
    
    private Project createProject() throws Exception {
        int rnd = new Random().nextInt(1000000);
        FileObject a = createGradleProject("projectA-" + rnd,
                "apply plugin: 'java'\n", "");
        FileUtil.createFolder(a, "src/main/java");
        FileUtil.createFolder(a, "src/main/groovy");
        prjDir = a;
        return ProjectManager.getDefault().findProject(a);
    }
    
    /**
     * Checks that untrusted unopened project will present itself as a fallback.
     * @throws Exception 
     */
    public void testUntrustedProjectFallback() throws Exception {
        Project prj = createProject();
        NbGradleProject ngp = NbGradleProject.get(prj);
        assertTrue(ngp.getQuality().worseThan(NbGradleProject.Quality.EVALUATED));
    }
    
    /**
     * Checks that an attempt to reload project with escalated quality will fail
     * for an untrusted project.
     * @throws Exception 
     */
    public void testUntrustedProjectCannotGoUp() throws Exception {
        Project prj = createProject();
        
        NbGradleProjectImpl prjImpl = prj.getLookup().lookup(NbGradleProjectImpl.class);
        assertTrue(prjImpl.getGradleProject().getQuality().worseThan(NbGradleProject.Quality.EVALUATED));
        // attempt to load everything
        prjImpl.setAimedQuality(NbGradleProject.Quality.FULL);
        // ... it loaded, but did not escalate the quality bcs not trusted
        assertTrue(prjImpl.getGradleProject().getQuality().worseThan(NbGradleProject.Quality.EVALUATED));
    }
    
    /**
     * Check that a trusted project can be loaded as 'evaluated' quality, at least.
     * @throws Exception 
     */
    public void testTrustedProjectLoadsToEvaluated() throws Exception {
        
    }
    
    public void testBaseProject() throws Exception {
        int rnd = new Random().nextInt(1000000);
        FileObject a = createGradleProject("projectA-" + rnd,
                "apply plugin: 'groovy'\n", "");
        Project prj = ProjectManager.getDefault().findProject(a);
        OpenProjects.getDefault().open(new Project[] { prj } , false);
        // wait for initialization
        OpenProjects.getDefault().openProjects().get();

        Collection<? extends ProjectService> srv2 = prj.getLookup().lookupAll(ProjectService.class);
        System.err.println(srv2);
    }
    
    /**
     * Checks that lookups provided for different Plugins do not change their
     * relative order and most importantly their relative order to the NB_GENERAL or NB_ROOT_PLUGIN
     * Lookups.
     */
    public void testPluginLookupsDoNotReorder() throws Exception {
        String gradleFolder = "Projects/org-netbeans-modules-gradle/Lookup";
        FileObject data = FileUtil.toFileObject(getDataDir()).getFileObject("projectlayer/Projects");
        FileObject target = FileUtil.getConfigRoot();
        copy(data, target);

        Project prj = createProject();
        FileObject buildscript = prjDir.getFileObject("build.gradle");
        try (OutputStream os = buildscript.getOutputStream();
             OutputStreamWriter wr = new OutputStreamWriter(os)) {
            wr.write("apply plugin: 'groovy'\n");
        }
        ProjectTrust.getDefault().trustProject(prj);
        Collection<? extends ProjectService> srv1 = prj.getLookup().lookupAll(ProjectService.class);
        
        NbGradleProjectImpl prjImpl = prj.getLookup().lookup(NbGradleProjectImpl.class);
        NbGradleProject ngp = NbGradleProject.get(prj);
        // force at least FALLBACK
        ngp.getQuality();
        
        Set<String> plugins = prjImpl.getGradleProject().getBaseProject().getPlugins();
        
        OpenProjects.getDefault().open(new Project[] { prj } , false);
        // wait for initialization
        OpenProjects.getDefault().openProjects().get();
        
        Collection<? extends ProjectService> srv2 = prj.getLookup().lookupAll(ProjectService.class);
        System.err.println(srv2);
        
        Thread.sleep(2000);
        try (OutputStream os = buildscript.getOutputStream();
             OutputStreamWriter wr = new OutputStreamWriter(os)) {
            wr.write("apply plugin: 'scala'\n");
            wr.write("apply plugin: 'java-library-distribution'\n");
            wr.write("apply plugin: 'jacoco'\n");
        }
        Thread.sleep(100000);

        Collection<? extends ProjectService> srv3 = prj.getLookup().lookupAll(ProjectService.class);
        System.err.println(srv3);
    }
    
    private FileObject copy(FileObject from, FileObject to) throws IOException {
        FileObject r = to.getFileObject(from.getNameExt());
        if (r == null) {
            return from.copy(to, from.getName(), from.getExt());
        } else {
            for (FileObject c : from.getChildren()) {
                copy(c, r);
            }
            return r;
        }
    }
}
