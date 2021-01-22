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
package org.netbeans.modules.maven.execute;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.maven.NbMavenProjectImpl;
import org.netbeans.modules.maven.api.customizer.ModelHandle2;
import org.netbeans.modules.maven.configurations.M2ConfigProvider;
import org.netbeans.modules.maven.execute.MavenExecuteUtils.ExecutionEnvHelper;
import org.netbeans.modules.maven.execute.model.ActionToGoalMapping;
import org.netbeans.modules.maven.execute.model.NetbeansActionMapping;
import org.netbeans.modules.maven.execute.model.io.xpp3.NetbeansBuildActionXpp3Reader;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.test.TestFileUtils;

/**
 *
 * @author sdedic
 */
public class ExecutionEnvHelperTest extends NbTestCase {
    Map<String, String> runP = new HashMap<>();
    Map<String, String> debugP = new HashMap<>();
    Map<String, String> profileP = new HashMap<>();

    public ExecutionEnvHelperTest(String name) {
        super(name);
    }
    
    private String substProperties(String input, String token, Map<String, String> properties) {
        int pos;
        while ((pos = input.indexOf(token)) != -1) {
            int last = input.lastIndexOf('\n', pos);
            String indent = String.join(" ", Collections.nCopies(pos - (last +1), ""));
            StringBuilder sb = new StringBuilder();
            
            for (String s : properties.keySet()) {
                sb.append(indent);
                sb.append("<").append(s).append(">");
                sb.append(properties.get(s));
                sb.append("</").append(s).append(">");
                sb.append("\n");
            }
            input = input.replace(token, sb.toString());
        }
        return input;
    }
    
    private FileObject  createNbActions(
            Map<String, String> runProperties, 
            Map<String, String> debugProperties, 
            Map<String, String> profileProperties) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("nbactions-template.xml"), "UTF-8"))) {
            String l;
            
            while ((l = rdr.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(l);
            }
        }
        String result = substProperties(
            substProperties(
                substProperties(sb.toString(), "&runProperties;", runProperties),
                "&debugProperties;", debugProperties),
            "&profileProperties;", profileProperties);
        return TestFileUtils.writeFile(FileUtil.toFileObject(getWorkDir()), "nbactions.xml", result);
    }
    
    NetbeansActionMapping runMapping;
    NetbeansActionMapping debugMapping;
    NetbeansActionMapping profileMapping;
    
    ActionToGoalMapping defaultActionMapping;
    
    private void loadActionMappings(Project project) throws Exception {
        runMapping = ModelHandle2.getMapping("run", project, 
                project.getLookup().lookup(M2ConfigProvider.class).getActiveConfiguration());
        debugMapping = ModelHandle2.getMapping("debug", project, 
                project.getLookup().lookup(M2ConfigProvider.class).getActiveConfiguration());
        profileMapping = ModelHandle2.getMapping("profile", project, 
                project.getLookup().lookup(M2ConfigProvider.class).getActiveConfiguration());
        
        M2ConfigProvider usr = project.getLookup().lookup(M2ConfigProvider.class);
        defaultActionMapping = new NetbeansBuildActionXpp3Reader().read(new StringReader((usr.getDefaultConfig().getRawMappingsAsString())));
    }
    
    /**
     * Checks that a pristine project has no value set, despite property references.
     */
    public void testLoadDefaultActions() throws Exception {
        FileObject pom = createPom("", "");
        
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        loadActionMappings(project);

        M2ConfigProvider usr = project.getLookup().lookup(M2ConfigProvider.class);
        NbMavenProjectImpl mavenProject = project.getLookup().lookup(NbMavenProjectImpl.class);
        
        ExecutionEnvHelper helper = MavenExecuteUtils.createExecutionEnvHelper(mavenProject, runMapping, debugMapping, profileMapping, defaultActionMapping);
        helper.loadFromProject();
        
        assertEquals("", helper.getVmParams());
        assertEquals("", helper.getAppParams());
        assertEquals("", helper.getMainClass());
    }
    
    /**
     * Checks that exec.args are correctly split into vm, app and main class.
     */
    public void testLoadFromExecArgs() throws Exception {
        runP.put("exec.executable", "java");
        runP.put("exec.args", "-Dprop=val -classpath %classpath test.mavenapp.App param1");
        profileP.putAll(runP);

        debugP.put("exec.vmArgs", "-agentlib:jdwp=transport=dt_socket,server=n,address=${jpda.address}");
        debugP.put("exec.executable", "java");
        
        FileObject actions = createNbActions(runP, debugP, profileP);
        
        FileObject pom = createPom("", "");
        
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        loadActionMappings(project);
        
        ActionToGoalMapping mapp = new NetbeansBuildActionXpp3Reader().read(new StringReader(actions.asText()));
        NbMavenProjectImpl mavenProject = project.getLookup().lookup(NbMavenProjectImpl.class);
        
        ExecutionEnvHelper helper = MavenExecuteUtils.createExecutionEnvHelper(mavenProject, runMapping, debugMapping, profileMapping, mapp);
        helper.loadFromProject();
        
        assertEquals("-Dprop=val", helper.getVmParams());
        assertEquals("param1", helper.getAppParams());
        assertEquals("test.mavenapp.App", helper.getMainClass());
    }
    
    /**
     * Loads action mapping split into multiple properties.
     */
    public void testLoadFromNewCustomizer() throws Exception {
        runP.put("exec.executable", "java");
        runP.put("exec.vmArgs", "-Dprop=val");
        runP.put("exec.mainClass", "test.mavenapp.App");
        runP.put("exec.appArgs", "param1");
        profileP.putAll(runP);
        
        debugP.put("exec.vmArgs", "-agentlib:jdwp=transport=dt_socket,server=n,address=${jpda.address}");
        debugP.put("exec.executable", "java");
        
        FileObject actions = createNbActions(runP, debugP, profileP);
        
        FileObject pom = createPom("", "");
        
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        loadActionMappings(project);
        
        ActionToGoalMapping mapp = new NetbeansBuildActionXpp3Reader().read(new StringReader(actions.asText()));
        NbMavenProjectImpl mavenProject = project.getLookup().lookup(NbMavenProjectImpl.class);
        
        ExecutionEnvHelper helper = MavenExecuteUtils.createExecutionEnvHelper(mavenProject, runMapping, debugMapping, profileMapping, mapp);
        helper.loadFromProject();
        
        assertEquals("-Dprop=val", helper.getVmParams());
        assertEquals("param1", helper.getAppParams());
        assertEquals("test.mavenapp.App", helper.getMainClass());
    }
    
    private FileObject createPom(String argsString, String propString) throws IOException {
        FileObject pom = TestFileUtils.writeFile(FileUtil.toFileObject(getWorkDir()), "pom.xml", "<project xmlns='http://maven.apache.org/POM/4.0.0'>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>grp</groupId>\n"
                + "    <artifactId>art</artifactId>\n"
                + "    <version>1.0</version>\n"
                +      propString
                + "    <build>\n"
                + "        <plugins>\n"
                + "            <plugin>\n"
                + "                <groupId>org.codehaus.mojo</groupId>\n"
                + "                <artifactId>exec-maven-plugin</artifactId>\n"
                + "                <version>3.0.0</version>\n"
                + "                <configuration>\n"
                +                      argsString 
                + "                </configuration>\n"
                + "            </plugin>\n"      
                + "        </plugins>\n"
                + "    </build>\n"
                + "</project>\n");
        return pom;
    }
    
    private void executeProject(FileObject pom, Consumer<String> a) throws IOException {
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        NetbeansActionMapping mapp = ModelHandle2.getMapping("run", project, project.getLookup().lookup(M2ConfigProvider.class).getActiveConfiguration());
        a.accept(ModelRunConfig.getExecArgsByPom(mapp, project));
    }
}
