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

import java.io.File;
import java.util.List;
import static junit.framework.TestCase.assertNotNull;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.gradle.ProjectTrust;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.options.GradleExperimentalSettings;
import org.netbeans.modules.project.dependency.ArtifactSpec;
import org.netbeans.modules.project.dependency.ProjectArtifactsQuery;
import org.netbeans.modules.project.dependency.ProjectArtifactsQuery.ArtifactsResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.DummyInstalledFileLocator;
import org.openide.windows.IOProvider;

/**
 *
 * @author sdedic
 */
public class GradleJarArtifactTest extends NbTestCase {

    public GradleJarArtifactTest(String name) {
        super(name);
    }
    
    private static File getTestNBDestDir() {
        String destDir = System.getProperty("test.netbeans.dest.dir");
        // set in project.properties as test-unit-sys-prop.test.netbeans.dest.dir
        assertNotNull("test.netbeans.dest.dir property has to be set when running within binary distribution", destDir);
        return new File(destDir);
    }

    File destDirF;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // This is needed, otherwose the core window's startup code will redirect
        // System.out/err to the IOProvider, and its Trivial implementation will redirect
        // it back to System.err - loop is formed. Initialize IOProvider first, it gets
        // the real System.err/out references.
        IOProvider p = IOProvider.getDefault();
        System.setProperty("test.reload.sync", "true");
        
        destDirF = getTestNBDestDir();
        clearWorkDir();
    
        DummyInstalledFileLocator.registerDestDir(destDirF);
        GradleExperimentalSettings.getDefault().setOpenLazy(false);
    }

    FileObject projectDir;

    private Project makeProject(String subdir) throws Exception {
        FileObject src = FileUtil.toFileObject(getDataDir()).getFileObject(subdir);
        projectDir = FileUtil.copyFile(src, FileUtil.toFileObject(getWorkDir()), src.getNameExt());
        
        Project p = ProjectManager.getDefault().findProject(projectDir);
        assertNotNull(p);
        ProjectTrust.getDefault().trustProject(p);
        
        OpenProjects.getDefault().open(new Project[] { p }, true);
        OpenProjects.getDefault().openProjects().get();
        
        NbGradleProject.get(p).toQuality("Load data", NbGradleProject.Quality.FULL, false).toCompletableFuture().get();
        return p;
    }
    
    public void testSimpleProject() throws Exception {
        Project p = makeProject("dependencies/simple1");
        ArtifactsResult res = ProjectArtifactsQuery.findArtifacts(p, ProjectArtifactsQuery.newQuery(null));
        List<ArtifactSpec> specs = res.getArtifacts();
        assertNotNull(specs);
        assertEquals(1, specs.size());
    }
}
