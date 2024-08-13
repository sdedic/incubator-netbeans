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
package org.netbeans.modules.maven.queries;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.text.Document;
import static org.junit.Assert.assertNotEquals;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.project.dependency.ProjectDependencies;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.DummyInstalledFileLocator;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.windows.IOProvider;

/**
 *
 * @author sdedic
 */
public class MavenReloadImplementationTest extends NbTestCase {
    private FileObject d;
    private File repo;
    private FileObject repoFO;
    private FileObject dataFO;

    public MavenReloadImplementationTest(String name) {
        super(name);
    }
    
    private static File getTestNBDestDir() {
        String destDir = System.getProperty("test.netbeans.dest.dir");
        // set in project.properties as test-unit-sys-prop.test.netbeans.dest.dir
        assertNotNull("test.netbeans.dest.dir property has to be set when running within binary distribution", destDir);
        return new File(destDir);
    }

    protected @Override void setUp() throws Exception {
        // this property could be eventually initialized by NB module system, as MavenCacheDisabler i @OnStart, but that's unreliable.
        System.setProperty("maven.defaultProjectBuilder.disableGlobalModelCache", "true");
        
        clearWorkDir();
        
        // This is needed, otherwose the core window's startup code will redirect
        // System.out/err to the IOProvider, and its Trivial implementation will redirect
        // it back to System.err - loop is formed. Initialize IOProvider first, it gets
        // the real System.err/out references.
        IOProvider p = IOProvider.getDefault();
        d = FileUtil.toFileObject(getWorkDir());
        System.setProperty("test.reload.sync", "false");
        repo = EmbedderFactory.getProjectEmbedder().getLocalRepositoryFile();
        repoFO = FileUtil.toFileObject(repo);
        dataFO = FileUtil.toFileObject(getDataDir());
        
        // Configure the DummyFilesLocator with NB harness dir
        File destDirF = getTestNBDestDir();
        DummyInstalledFileLocator.registerDestDir(destDirF);
    }

    @Override
    protected void tearDown() throws Exception {
        OpenProjects.getDefault().close(OpenProjects.getDefault().getOpenProjects());
        super.tearDown(); 
    }
    
    FileObject prjCopy;
    Project root;
    Project oci;
    Project lib;
    
    void setupMicronautProject() throws Exception {
        FileUtil.toFileObject(getWorkDir()).refresh();

        FileObject testApp = dataFO.getFileObject("projects/multiproject/democa");
        prjCopy = FileUtil.copyFile(testApp, FileUtil.toFileObject(getWorkDir()), "democa");
        root = ProjectManager.getDefault().findProject(prjCopy);
        oci = ProjectManager.getDefault().findProject(prjCopy.getFileObject("oci"));
        lib =  ProjectManager.getDefault().findProject(prjCopy.getFileObject("lib"));
        OpenProjects.getDefault().open(new Project[] { root, oci, lib }, false);
        OpenProjects.getDefault().openProjects().get();
    }
    
    private void primeProject() throws Exception {
        CountDownLatch cdl = new CountDownLatch(1);
        
        ActionProvider ap = root.getLookup().lookup(ActionProvider.class);
        if (!ap.isActionEnabled(ActionProvider.COMMAND_PRIME, Lookup.EMPTY)) {
            return;
        }

        ActionProgress prg = new ActionProgress() {
            @Override
            protected void started() {
            }

            @Override
            public void finished(boolean success) {
                cdl.countDown();
            }
        };
        ap.invokeAction(ActionProvider.COMMAND_PRIME, Lookups.fixed(prg));
        cdl.await(100, TimeUnit.SECONDS);
    }
    
    /**
     * Checks that root POM is reported as project file.
     * @throws Exception 
     */
    public void testSingleRootProject() throws Exception {
        setupMicronautProject();
        primeProject();
        
        Set<FileObject> fos = ProjectDependencies.findProjectFiles(root, false);
        assertEquals(1, fos.size());
        assertSame(prjCopy.getFileObject("pom.xml"), fos.iterator().next());
    }
    
    /**
     * Checks that the root project just reports its own POM and the settings.
     * @throws Exception 
     */
    public void testRootProjectReloadJustOnePom() throws Exception {
        setupMicronautProject();
        primeProject();
        
        Set<FileObject> fos = ProjectDependencies.findProjectFiles(root, true);
        
        assertEquals(2, fos.size());
        assertTrue(fos.contains(prjCopy.getFileObject("pom.xml")));
        assertTrue(fos.contains(FileUtil.toFileObject(new File(System.getProperty("user.home"))).getFileObject(".m2/settings.xml")));
    }
    
    
    /**
     * Checks that project with dependencies and parent report both parent and the dependent projects
     * in NB workspace (must be opened). 
     * @throws Exception 
     */
    public void testReloadFilesDependentAndParent() throws Exception {
        setupMicronautProject();
        primeProject();
        
        Set<FileObject> fos = ProjectDependencies.findProjectFiles(oci, true);
        
        assertEquals(4, fos.size());
        assertTrue(fos.contains(prjCopy.getFileObject("pom.xml")));
        assertTrue(fos.contains(prjCopy.getFileObject("oci/pom.xml")));
        assertTrue(fos.contains(prjCopy.getFileObject("lib/pom.xml")));
        assertTrue(fos.contains(FileUtil.toFileObject(new File(System.getProperty("user.home"))).getFileObject(".m2/settings.xml")));
    }

    /**
     * Checks that for a subproject, just the project pom is reported
     * if 'forReload' is false.
     * @throws Exception 
     */
    public void testReportJustSubproject() throws Exception {
        setupMicronautProject();
        primeProject();

        Set<FileObject> fos = ProjectDependencies.findProjectFiles(oci, false);
        assertEquals(1, fos.size());
        assertSame(prjCopy.getFileObject("oci/pom.xml"), fos.iterator().next());
    }
    
    
    /**
     * Checks that reload of a subproject fails if its parent-pom is modified.
     * @throws Exception 
     */
    public void testReloadFailsWithParentPomModified() throws Exception {
        setupMicronautProject();
        
        primeProject();
        
        FileObject rootPomFile = root.getProjectDirectory().getFileObject("pom.xml");
        EditorCookie cake = rootPomFile.getLookup().lookup(EditorCookie.class);
        Document doc = cake.openDocument();
        doc.insertString(0, "aaa", null);
        doc.remove(0, 3);
        
        try {
            Project p = ProjectDependencies.withProjectState(oci, 
                    ProjectDependencies.ProjectStateRequest.refresh()).toCompletableFuture().get();
            fail("Should fail as pom is modified in memory");
        } catch (ProjectOperationException ex) {
            // expected
            assertEquals(ProjectOperationException.State.OUT_OF_SYNC, ex.getState());
        }
    }

    
    /**
     * Checks that the reload succeeds, if only submodule has been modified. The reload should
     * succeed immediately as on-disk state is not modified after the project load.
     * @throws Exception 
     */
    public void testReloadOKWithSubprojectModified() throws Exception {
        setupMicronautProject();
        primeProject();
        
        FileObject ociPomFile = oci.getProjectDirectory().getFileObject("pom.xml");
        EditorCookie cake = ociPomFile.getLookup().lookup(EditorCookie.class);
        Document doc = cake.openDocument();
        doc.insertString(0, "aaa", null);
        doc.remove(0, 3);

        CompletableFuture f;
        synchronized (this) {
            f = ProjectDependencies.withProjectState(lib, 
                ProjectDependencies.ProjectStateRequest.refresh()).toCompletableFuture().thenAccept(p -> {
                    synchronized (this) {
                        // just block
                    }
                });
            // the reload actually does not happen. The Future completes even before the withProjectState returns.
            assertTrue(f.isDone());
        }
    }
    
    /**
     * Checks that a reload succeeds when the in-memory modification check
     * is disabled.
     */
    public void testReloadIgnoresModifications() throws Exception {
        setupMicronautProject();
        
        FileObject rootPomFile = root.getProjectDirectory().getFileObject("pom.xml");
        EditorCookie cake = rootPomFile.getLookup().lookup(EditorCookie.class);
        Document doc = cake.openDocument();
        doc.insertString(0, "aaa", null);
        doc.remove(0, 3);
        
        Thread.sleep(2000);
        // let's touch the root's pom.xml file to be formally newer than the loaded
        // project
        Files.setLastModifiedTime(Paths.get(rootPomFile.toURI()), FileTime.fromMillis(System.currentTimeMillis()));
        
        CompletableFuture f;
        AtomicInteger n = new AtomicInteger();
        synchronized (this) {
            f = ProjectDependencies.withProjectState(root, 
                    ProjectDependencies.ProjectStateRequest.refresh().noModifications()).toCompletableFuture().thenAccept(p -> {
                assertNotEquals(0, n.get());
                synchronized (this) {
                    assertNotEquals(0, n.get());
                    n.incrementAndGet();
                }
            });
            // the Future must not complete synchronously - a good signt that the project is actually being reloaded.
            assertFalse(f.isDone());
            n.incrementAndGet();
        }
        
        f.get();
        assertEquals(2, n.get());
    }
    
    /**
     * Checks that a project, that desperately needs to download artifacts will fail to reach ready state in offline mode. For this, we 
     * remove micronaut-parent parent POM that is referenced by the project.
     */
    public void testReloadFailsInOfflineMode() throws Exception {
        // must remove artifacts from .m2 BEFORE the project is opened / scanned.
        Path p = Paths.get(System.getProperty("user.home"), ".m2", "repository", "io", "micronaut", "platform", "micronaut-parent");
        if (Files.exists(p)) {
            Files.walk(p)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        setupMicronautProject();
        try {
            ProjectDependencies.withProjectState(root, 
                ProjectDependencies.ProjectStateRequest.refresh().offline()).toCompletableFuture().get();
            fail("Project has no parent POM, and offline is forced");
        } catch (ProjectOperationException ex) {
            assertEquals(ProjectOperationException.State.OFFLINE, ex.getState());
        }
    }

    /**
     * Checks that project reload fails, if the POM is modified.
     */
    public void testReloadFailsWithPomModified() throws Exception {
        setupMicronautProject();
        primeProject();
        
        FileObject libPomFile = lib.getProjectDirectory().getFileObject("pom.xml");
        EditorCookie cake = libPomFile.getLookup().lookup(EditorCookie.class);
        Document doc = cake.openDocument();
        doc.insertString(0, "aaa", null);
        doc.remove(0, 3);
        
        try {
            Project p = ProjectDependencies.withProjectState(oci, 
                    ProjectDependencies.ProjectStateRequest.refresh()).toCompletableFuture().get();
            fail("Should fail as pom is modified in memory");
        } catch (ProjectOperationException ex) {
            // expected
            assertEquals(ProjectOperationException.State.OUT_OF_SYNC, ex.getState());
        }
    }
    
}
