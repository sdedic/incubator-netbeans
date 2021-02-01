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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.netbeans.api.extexecution.base.ExplicitProcessParameters;
import org.netbeans.api.extexecution.startup.StartupExtender;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.extexecution.startup.StartupExtenderRegistrationProcessor;
import org.netbeans.modules.maven.NbMavenProjectImpl;
import org.netbeans.modules.maven.api.PluginPropertyUtils;
import org.netbeans.modules.maven.api.customizer.ModelHandle2;
import org.netbeans.modules.maven.api.execute.PrerequisitesChecker;
import org.netbeans.modules.maven.configurations.M2ConfigProvider;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.execute.MavenExecuteUtils.ExecutionEnvHelper;
import org.netbeans.modules.maven.execute.model.ActionToGoalMapping;
import org.netbeans.modules.maven.execute.model.NetbeansActionMapping;
import org.netbeans.modules.maven.execute.model.io.xpp3.NetbeansBuildActionXpp3Reader;
import org.netbeans.modules.maven.runjar.RunJarPrereqChecker;
import org.netbeans.spi.extexecution.startup.StartupExtenderImplementation;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.test.TestFileUtils;
import org.openide.loaders.DataFolder;
import org.openide.loaders.InstanceDataObject;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.test.MockLookup;
import org.openide.windows.InputOutput;

/**
 *
 * @author sdedic
 */
public class ExecutionEnvHelperTest extends NbTestCase {
    Map<String, String> runP = new HashMap<>();
    Map<String, String> debugP = new HashMap<>();
    Map<String, String> profileP = new HashMap<>();
    FileObject pom;
    FileObject nbActions;
    
    public ExecutionEnvHelperTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp(); 
        clearWorkDir();
        MockLookup.setLayersAndInstances();
    }

    @Override
    protected void tearDown() throws Exception {
        TestExtender.vmArg = null;
        super.tearDown();
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
     * Sets up properties for pre-12.3 maven projects, with some customizations
     * <ul>
     * <li>a system property
     * <li>classpath
     * <li>an application parameter
     * </ul>
     */
    private void initOldProperties() {
        makeOldDefaultProperties();
        
        runP.put("exec.args", "-Dprop=val -classpath %classpath test.mavenapp.App param1");
        profileP.putAll(runP);
        debugP.put("exec.args", "-agentlib:jdwp=transport=dt_socket,server=n,address=${jpda.address} -Dprop=val -classpath %classpath test.mavenapp.App param1");
    }
    
    /**
     * Sets up DEFAULT pre-12.3 properties, no new properties will be defined at all. Use to test
     * compatibility with pre-existing projects.
     */
    private void makeOldDefaultProperties() {
        runP.remove("exec.vmArgs");
        runP.remove("exec.appArgs");
        runP.remove("exec.mainClass");
        runP.put("exec.executable", "java");
        runP.put("exec.args", MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH);
        profileP.putAll(runP);

        debugP.put("exec.args", MavenExecuteUtils.DEFAULT_DEBUG_PARAMS + " " + MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH);
        debugP.put("exec.executable", "java");
    }
    
    private ActionToGoalMapping mapp;
    
    private ExecutionEnvHelper createAndLoadHelper() throws Exception {
        FileObject actions = createNbActions(runP, debugP, profileP);
        FileObject pom = createPom("", "");
        
        this.nbActions = actions;
        this.pom = pom;
        
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        loadActionMappings(project);
        
        mapp = new NetbeansBuildActionXpp3Reader().read(new StringReader(actions.asText()));
        NbMavenProjectImpl mavenProject = project.getLookup().lookup(NbMavenProjectImpl.class);
        
        ExecutionEnvHelper helper = MavenExecuteUtils.createExecutionEnvHelper(mavenProject, runMapping, debugMapping, profileMapping, mapp);
        helper.loadFromProject();
        return helper;
    }
    
    /**
     * Checks that exec.args are correctly split into vm, app and main class.
     */
    public void testLoadFromExecArgs() throws Exception {
        initOldProperties();
        
        ExecutionEnvHelper helper = createAndLoadHelper();
        assertEquals("-Dprop=val", helper.getVmParams());
        assertEquals("param1", helper.getAppParams());
        assertEquals("test.mavenapp.App", helper.getMainClass());
    }
    
    /**
     * Sets up project's split properties - individual parts split to separate
     * properties (vm args, app args, main class).
     */
    private void initSplitProperties() {
        runP.put("exec.executable", "java");
        runP.put("exec.mainClass", "${packageClassName}");
        runP.put("exec.vmArgs", "");
        runP.put("exec.appArgs", "");
        runP.put("exec.args", MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH2);

        profileP.putAll(runP);
        debugP.putAll(runP);
        
        debugP.put("exec.args", MavenExecuteUtils.DEFAULT_DEBUG_PARAMS + " " + MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH2);
        debugP.put("exec.executable", "java");
    }
    
    /**
     * Sets up customized split properties. A VM arg, app arg and a defined main class are present.
     */
    private void initCustomizedSplitProperties() {
        initSplitProperties();
        runP.put("exec.vmArgs", "-Dprop=val");
        runP.put("exec.mainClass", "test.mavenapp.App");
        runP.put("exec.appArgs", "paramX");
        profileP.putAll(runP);
        
        debugP.put("exec.vmArgs", "-Dprop=val");
        debugP.put("exec.mainClass", "test.mavenapp.App");
        debugP.put("exec.appArgs", "paramX");
    }
    
    /**
     * Loads action mapping split into multiple properties.
     */
    public void testLoadFromNewCustomizer() throws Exception {
        initCustomizedSplitProperties();
        ExecutionEnvHelper helper = createAndLoadHelper();
        
        assertEquals("-Dprop=val", helper.getVmParams());
        assertEquals("paramX", helper.getAppParams());
        assertEquals("test.mavenapp.App", helper.getMainClass());
    }
    
    /**
     * Checks that a change to a helper loaded from an old config will result in proper properties being set.
     * 
     * @throws Exception 
     */
    public void testChangeFromOldConfig() throws Exception {
        initOldProperties();
        ExecutionEnvHelper helper = createAndLoadHelper();        
        
        helper.setAppParams("param2");
        helper.setMainClass("bar.FooBar");
        helper.setVmParams("-Dwhatever=true");
        
        helper.applyToMappings();
        
        checkActionMapping(getActionMapping("run"), "bar.FooBar", "param2", "-Dwhatever=true");
        checkActionMapping(getActionMapping("debug"), "bar.FooBar", "param2", "-Dwhatever=true " + MavenExecuteUtils.DEFAULT_DEBUG_PARAMS);
        checkActionMapping(getActionMapping("profile"), "bar.FooBar", "param2", "-Dwhatever=true");
    }
    
    /**
     * If the original config was 'mixed', that is did not contain '%classpath' in some of the actions,
     * that action's properties will not change when altered in the Helper. This is consistent with
     * previous function of the RunJarPanel.
     */
    public void testMixedConfigNotChanged() throws Exception {
        initOldProperties();
        
        // remove the classpath
        runP.put("exec.args", "-Dprop=val test.mavenapp.App param1");

        ExecutionEnvHelper helper = createAndLoadHelper();        
        
        helper.setAppParams("param2");
        helper.setMainClass("bar.FooBar");
        helper.setVmParams("-Dwhatever=true");
        
        helper.applyToMappings();
        
        // changes debug and profile are OK
        checkActionMapping(getActionMapping("debug"), "bar.FooBar", "param2", "-Dwhatever=true " + MavenExecuteUtils.DEFAULT_DEBUG_PARAMS);
        checkActionMapping(getActionMapping("profile"), "bar.FooBar", "param2", "-Dwhatever=true");
        
        // but 'run' profile didn't contain %classpath, so it should not be changed at all:
        NetbeansActionMapping rm = getActionMapping("run");
        assertEquals(runP, rm.getProperties());
    }

    private void checkActionMapping(NetbeansActionMapping map, String mainClass, String appArgs, String vmArgs) {
        String execArgs = map.getProperties().get("exec.args");
        assertTrue(execArgs.contains("${exec.mainClass}"));
        assertTrue(execArgs.contains("${exec.vmArgs}"));
        assertTrue(execArgs.contains("${exec.appArgs}"));
        assertTrue(execArgs.contains("-classpath %classpath"));
        
        assertEquals(appArgs, map.getProperties().get("exec.appArgs"));
        assertEquals(vmArgs, map.getProperties().get("exec.vmArgs"));
        assertEquals(mainClass, map.getProperties().get("exec.mainClass"));
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

    
    private void createPomWithArguments() throws Exception {
        pom = createPom("<arguments>"
                    + "<argument>-DsomeProperty=${AA}</argument>"
                    + "<argument>-classpath</argument>"
                    + "<classpath></classpath>"
                + "</arguments>", "<properties><AA>blah</AA></properties>");
    }
    
    private void assertPOMArguments(NetbeansActionMapping mapp, String actionsDefaultArgs) throws Exception {
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        ModelRunConfig cfg = new ModelRunConfig(project, mapp, "run", null, Lookup.EMPTY, true);

        assertTrue(cfg.getProperties().get(MavenExecuteUtils.RUN_PARAMS).contains("-DsomeProperty=blah"));
        assertTrue(cfg.getProperties().get(MavenExecuteUtils.RUN_PARAMS).contains(actionsDefaultArgs));
    }
    
    /**
     * Checks that default actions set up for 12.3 and previous projects will merge in maven
     * settings.
     * @throws Exception 
     */
    public void testMergedMappingUsesPOMArguments() throws Exception {
        makeOldDefaultProperties();
        createNbActions(runP, debugP, profileP);
        createPomWithArguments();
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        loadActionMappings(project);

        assertPOMArguments(runMapping, MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH);
    }
    
    /**
     * Checks that default actions set up for post-12.3 will merge in maven
     * settings.
     * @throws Exception 
     */
    public void testSplitPropertyMappingUsesPOMArguments() throws Exception {
        initSplitProperties();
        createNbActions(runP, debugP, profileP);
        createPomWithArguments();
        
        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        loadActionMappings(project);

        assertPOMArguments(runMapping, MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH2);
    }
    
    /**
     * Checks that a project with no nb-actions still merges in default actions
     * + the POM settings
     * @throws Exception 
     */
    public void testDefaultNoActionsMappingUsesPOMArguments() throws Exception {
        createPomWithArguments();

        Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        loadActionMappings(project);

        assertPOMArguments(runMapping, MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH2);
    }
    
    private NetbeansActionMapping getActionMapping(String aName) {
        return mapp.getActions().stream().filter(m -> m.getActionName().equals(aName)).findAny().get();
    }
    
    /**
     * Evaluates property references in arguments. Uses Maven evaluator for the project + properties defined in the 
     * ModelRunConfig (which are passed as -D to the maven executor).
     */
    private List<String> substituteProperties(List<String> args, Project p, Map<? extends String, ? extends String> properties) {
        Map<String, String> props = new HashMap<>(properties);
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.startsWith("-D")) {
                int eq = a.indexOf('=');
                String k = a.substring(2, eq);
                String v = a.substring(eq + 1);
                props.put(k, v);
            }
        }
        ExpressionEvaluator e = PluginPropertyUtils.createEvaluator(p);
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            int pos;
            int end = a.length() -1;
            for (pos = -1; (pos = a.indexOf("${", pos + 1)) > -1; ) {
                end = a.indexOf("}", pos + 1);
                String name = a.substring(pos + 2, end);
                String v = props.get(name);
                if (v != null) {
                    a = a.substring(0, pos) + v + a.substring(end + 1);
                } else {
                    pos = end + 1;
                }
            }
            try {
                a = e.evaluate(a).toString();
            } catch (ExpressionEvaluationException ex) {
                // do nothing
            }
            args.set(i, a);
        }
        return args;
    }

    private void assertPOMArgumentsUsed() throws Exception {
        createNbActions(runP, debugP, profileP);
        createPomWithArguments();
        if (runMapping == null) {
            final Project project = ProjectManager.getDefault().findProject(pom.getParent());
            loadActionMappings(project);
        }
        assertRunArguments(runMapping, "-DsomeProperty=blah", DEFAULT_MAIN_CLASS_TOKEN,  null);
    }
    
    private void assertActionOverridesArguments(String vmArg, String mainClass, String appArg) throws Exception {
        createNbActions(runP, debugP, profileP);
        createPomWithArguments();
        if (runMapping == null) {
            final Project project = ProjectManager.getDefault().findProject(pom.getParent());
            loadActionMappings(project);
        }
        assertRunArguments(runMapping, vmArg, mainClass == null ? DEFAULT_MAIN_CLASS_TOKEN : mainClass,  appArg);
    }
    
    /**
     * Checks that without mapping maven arguments are properly passed to exec.args, so
     * they are not overriden.
     */
    public void test123DefaultProjectPassesPOMArguments() throws Exception {
        makeOldDefaultProperties();
        assertPOMArgumentsUsed();
    }
    
    /**
     * Checks that old mapping (just exec.args) properly passes POM arguments if
     * exec.args does not define any
     */
    public void test123WithActionsAndNoArgsPassesPOMArguments() throws Exception {
        makeOldDefaultProperties();
        createNbActions(runP, debugP, profileP);
        assertPOMArgumentsUsed();
    }
    
    /**
     * Checks that new default mapping (no customizations) will pass POM arguments.
     */
    public void testNewDefaultActionPassesPOMArguments() throws Exception {
        initSplitProperties();
        assertPOMArgumentsUsed();
    }

    /**
     * Checks that argument in mapping's exec.args overrides POM arguments
     */
    public void test123WithActionsArgumentsOverridePOM() throws Exception {
        makeOldDefaultProperties();
        runP.put(MavenExecuteUtils.RUN_PARAMS, 
                "-DdifferentProperty=blurb " + MavenExecuteUtils.DEFAULT_EXEC_ARGS_CLASSPATH + " ${pkgClassName} param2 prevParam");
        assertActionOverridesArguments("-DdifferentProperty=blurb", DEFAULT_MAIN_CLASS_TOKEN, "param2 prevParam");
    }
    
    private static final String DEFAULT_MAIN_CLASS_TOKEN = "main.class.TokenMarker";
    
    
    private void initSplitPropertiesWithArguments() throws Exception {
        initSplitProperties();
        createPomWithArguments();
        runP.put(MavenExecuteUtils.RUN_APP_PARAMS, "firstParam nextParam");
        runP.put(MavenExecuteUtils.RUN_VM_PARAMS, "-DvmArg=1");
    }
    
    /**
     * Checks that if a mapping defines arguments, they are used in preference to the 
     * POM ones.
     */
    public void testNewDefaultMappingPassesArguments() throws Exception {
        initSplitPropertiesWithArguments();
        assertActionOverridesArguments("-DvmArg=1", null, "firstParam nextParam");
    }
    
    /**
     * Checks that pre-12.3 default actions will inject VM arguments and arguments from Lookup.
     */
    public void test123DefaultActionWithAddition() throws Exception {
        makeOldDefaultProperties();
        createNbActions(runP, debugP, profileP);
        ExplicitProcessParameters explicit = ExplicitProcessParameters.builder().
                priorityArg("-DvmArg2=2").
                arg("paramY").build();
        MockLookup.setLayersAndInstances(explicit);
        createPomWithArguments();
        assertActionOverridesArguments("-DvmArg2=2", null, "paramY");
        // check that default pom arguments are ALSO present
        assertTrue(mavenVmArgs.contains("-DsomeProperty="));
    }
    
    /**
     * Checks that pre-12.3 default actions will inject arguments from Lookup. VM args
     * should be added <b>in addition to the existing ones</b> while application args
     * should be replaced.
     */
    public void test123DefaultActionWithVMReplacement() throws Exception {
        makeOldDefaultProperties();
        createNbActions(runP, debugP, profileP);
        ExplicitProcessParameters explicit = ExplicitProcessParameters.builder().
                priorityArg("-DvmArg2=2").
                appendPriorityArgs(false).
                arg("paramY").build();
        MockLookup.setLayersAndInstances(explicit);
        createPomWithArguments();
        assertActionOverridesArguments("-DvmArg2=2", null, "paramY");
        // check that default pom arguments are not present
        assertFalse(mavenVmArgs.contains("-DsomeProperty="));
    }
    
    /**
     * New actions: Checks that explicit params by default _append_ VM args and
     * replaces args.
     */
    public void testNewActionWithVMAdditionAndArgReplacement() throws Exception {
        initSplitPropertiesWithArguments();
        ExplicitProcessParameters explicit = ExplicitProcessParameters.builder().
                priorityArg("-DvmArg2=2").
                arg("paramY").build();
        MockLookup.setLayersAndInstances(explicit);
        createPomWithArguments();
        assertActionOverridesArguments("-DvmArg2=2", null, "paramY");
        // check that default pom arguments are not present
        assertTrue(mavenVmArgs.contains("-DvmArg=1"));
        // by default arguments are replaced:
        assertFalse(mavenAppArgs.contains("firstParam nextParam"));
    }
    
    /**
     * New actions: Checks that args can be appended, if necessary.
     */
    public void testNewActionWithArgAddition() throws Exception {
        initSplitPropertiesWithArguments();
        ExplicitProcessParameters explicit = ExplicitProcessParameters.builder().
                priorityArg("-DvmArg2=2").
                arg("paramY").build();
        MockLookup.setLayersAndInstances(explicit);
        createPomWithArguments();
        assertActionOverridesArguments("-DvmArg2=2", null, "paramY");
        // check that default pom arguments are not present
        assertFalse(mavenVmArgs.contains("-DsomeProperty="));
    }

    /**
     * New actions: checks that VM args can be replaced.
     */
    public void testNewActionWithVMReplacement() throws Exception {
        initSplitPropertiesWithArguments();
        ExplicitProcessParameters explicit = ExplicitProcessParameters.builder().
                appendPriorityArgs(false).
                priorityArg("-DvmArg2=2").
                arg("paramY").build();
        MockLookup.setLayersAndInstances(explicit);
        createPomWithArguments();
        assertActionOverridesArguments("-DvmArg2=2", null, "paramY");
        // check that default pom arguments are not present
        assertFalse(mavenVmArgs.contains("-DvmArg=1"));
    }
    
    static class TestExtender implements StartupExtenderImplementation {
        static String vmArg;
        
        @Override
        public List<String> getArguments(Lookup context, StartupExtender.StartMode mode) {
            return vmArg == null ? Collections.emptyList() : Collections.singletonList(vmArg);
        }
    }
    
    private void registerExtender() throws IOException {
        FileObject p = FileUtil.getConfigFile(StartupExtenderRegistrationProcessor.PATH);
        if (p == null) {
            p = FileUtil.getConfigRoot().createFolder(StartupExtenderRegistrationProcessor.PATH);
        }
        DataFolder fld = DataFolder.findFolder(p);
        InstanceDataObject.create(fld, "test-extender", TestExtender.class);
    }
    
    /**
     * New actions: checks that appended VM args are also merged with a
     * startup extender
     */
    public void testNewActionVMAppendMergesWithExtenders() throws Exception {
        initSplitPropertiesWithArguments();
        ExplicitProcessParameters explicit = ExplicitProcessParameters.builder().
//                appendPriorityArgs(false).
                priorityArg("-DvmArg2=2").
                arg("paramY").build();
        registerExtender();
        TestExtender.vmArg = "-Dbar=foo";
        MockLookup.setLayersAndInstances(explicit);
        createPomWithArguments();
        assertActionOverridesArguments("-DvmArg2=2", null, "paramY");
        // check that default pom arguments are not present
        assertTrue(mavenVmArgs.contains("-DvmArg=1"));
        assertTrue(mavenVmArgs.contains("-Dbar=foo"));
    }
    
    /**
     * New actions: checks that appended VM args are merged with a
     * startup extender EVEN If config args are replaced.
     */
    public void testNewActionVMReplaceSillMergesWithExtenders() throws Exception {
        initSplitPropertiesWithArguments();
        ExplicitProcessParameters explicit = ExplicitProcessParameters.builder().
                appendPriorityArgs(false).
                priorityArg("-DvmArg2=2").
                arg("paramY").build();
        registerExtender();
        TestExtender.vmArg = "-Dbar=foo";
        MockLookup.setLayersAndInstances(explicit);
        createPomWithArguments();
        assertActionOverridesArguments("-DvmArg2=2", null, "paramY");
        // check that default pom arguments are not present
        assertFalse(mavenVmArgs.contains("-DvmArg=1"));
        assertTrue(mavenVmArgs.contains("-Dbar=foo"));
    }

    private String mavenVmArgs = ""; // NOI18N
    private String mavenAppArgs = ""; // NOI18N
    private Map<String, String> mavenExecutorDefines = new HashMap<>();
    
    private void assertRunArguments(NetbeansActionMapping mapping, String vmArgs, String mainClass, String args) throws Exception {
        final Project project = ProjectManager.getDefault().findProject(pom.getParent());        
        
        final String prefix = "-D" + MavenExecuteUtils.RUN_PARAMS + "=";
        assertMavenRunAction(project, mapping, "run", (List<String> cmdLine) -> {
            mavenExecutorDefines.clear();
            for (String s : cmdLine) {
                if (s.startsWith("-D")) {
                    int equalsIndex = s.indexOf('=');
                    String k = s.substring(2, equalsIndex);
                    String v = s.substring(equalsIndex + 1);
                    mavenExecutorDefines.put(k, v);
                }
            }
            String argString = mavenExecutorDefines.get(MavenExecuteUtils.RUN_PARAMS);
            int indexOfDefine = argString.indexOf(vmArgs);
            assertTrue(indexOfDefine >= 0);
            int indexOfMainClass = argString.indexOf(mainClass);
            assertTrue(indexOfMainClass >= 0);
            mavenVmArgs = argString.substring(0, indexOfMainClass).trim();
            mavenAppArgs = argString.substring(indexOfMainClass + mainClass.length()).trim();
            assertTrue("VM args must precede main class", indexOfDefine < indexOfMainClass);
            if (args != null) {
                int indexOfAppParams = argString.indexOf(args);
                assertTrue(indexOfAppParams >= 0);
                assertTrue("App args must followmain class", indexOfMainClass <  indexOfAppParams);
            }
        });
    }
    
    private void assertMavenRunAction(Project project, NetbeansActionMapping mapping, String actionName, Consumer<List<String>> commandLineAcceptor) throws Exception {
        NbPreferences.root().node("org/netbeans/modules/maven").put(EmbedderFactory.PROP_COMMANDLINE_PATH, "mvn");
        ModelRunConfig cfg = new ModelRunConfig(project, mapping, actionName, null, Lookup.EMPTY, true);
        // prevent displaying dialogs.
        RunJarPrereqChecker.setMainClass(DEFAULT_MAIN_CLASS_TOKEN);
        for (PrerequisitesChecker elem : cfg.getProject().getLookup().lookupAll(PrerequisitesChecker.class)) {
            if (!elem.checkRunConfig(cfg)) {
                fail("");
            }
            if (cfg.getPreExecution() != null) {
                if (!elem.checkRunConfig(cfg.getPreExecution())) {
                    fail("");
                }
            }
        }
        MavenCommandLineExecutor exec = new MavenCommandLineExecutor(cfg, InputOutput.NULL, null) {
            @Override
            int executeProcess(CommandLineOutputHandler out, ProcessBuilder builder, Consumer<Process> processSetter) throws IOException, InterruptedException {
                List<String> args = substituteProperties(builder.command(), project, cfg.getProperties());
                commandLineAcceptor.accept(args);
                return 0;
            }
        };
        exec.task = new ExecutorTask(exec) {
            @Override
            public void stop() {
            }
            
            @Override
            public int result() {
                return 0;
            }
            
            @Override
            public InputOutput getInputOutput() {
                return InputOutput.NULL;
            }
        };
        exec.run();
    }
}
