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
package org.netbeans.modules.gradle.java.execute;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.netbeans.api.extexecution.base.ExplicitProcessParameters;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.BaseUtilities;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author sdedic
 */
public class JavaExecTokenProviderTest extends NbTestCase {

    public JavaExecTokenProviderTest(String name) {
        super(name);
    }
    
    public void testTokensNotAvailableInNonJava() throws Exception {
        FileObject dataRoot = FileUtil.toFileObject(getDataDir());
        FileObject nj = dataRoot.getFileObject("nonjava");
        
        Project p = ProjectManager.getDefault().findProject(nj);
        
        JavaExecTokenProvider jetp = new JavaExecTokenProvider(p);
        assertEquals(0, jetp.getSupportedTokens().size());
        
        Collections.list(System.getProperties().propertyNames()).stream().
                filter(n -> n.toString().startsWith("test.")).
                map(Object::toString).sorted().toArray();
        
    }
    
    private Project createSimpleJavaProject() throws IOException {
        FileObject dataRoot = FileUtil.toFileObject(getDataDir());
        FileObject nj = dataRoot.getFileObject("javasimple");
        
        Project p = ProjectManager.getDefault().findProject(nj);
        return p;
    }
    
    public void testTokensAvailableWithJavaPlugin() throws Exception {
        JavaExecTokenProvider jetp = new JavaExecTokenProvider(createSimpleJavaProject());
        assertEquals(4, jetp.getSupportedTokens().size());
    }
    
    private void assertParamList(String val, String... items) {
        assertNotNull("Value present", val);
        String[] pars  = BaseUtilities.parseParameters(val);
        assertEquals(items.length + " params passed", items.length, pars.length);
        assertEquals(Arrays.asList(items), Arrays.asList(pars));
    }
    
    private void assertListInValue(String val, String... items) {
        assertNotNull("Value present", val);
        String[] check  = BaseUtilities.parseParameters(val);
        assertEquals("Passed as single arg", 1, check.length);
        
        String[] pars = BaseUtilities.parseParameters(check[0].substring(check[0].indexOf('=') + 1));
        assertEquals(items.length + " params passed", items.length, pars.length);
        assertEquals(Arrays.asList(items), Arrays.asList(pars));
    }
    
    private void assertArgs(String val, String... items) {
        assertNotNull("Value present", val);
        String[] check  = BaseUtilities.parseParameters(val);
        assertEquals("--args + param", 2, check.length);
        assertEquals("Passed with --args", "--args", check[0]);
        assertParamList(check[1], items);
    }
    
    private String[] TWO_PROPS = {
        "-Dtest.foo=bar",
        "-Dtest.bar=foo"
    };
    
    private String[] THREE_PROPS = {
        "-Dtest.foo=bar",
        "-Dtest.bar=foo",
        "-Dtest.space=with space"
    };
    
    public void testExplicitJVMArgsSimple() throws Exception {
        JavaExecTokenProvider jetp = new JavaExecTokenProvider(createSimpleJavaProject());
        
        ExplicitProcessParameters params = ExplicitProcessParameters.builder().
                launcherArgs(TWO_PROPS).
                build();
        
        Map<String, String> map = jetp.createReplacements(ActionProvider.COMMAND_RUN, Lookups.singleton(params));
        assertEquals(4, map.size());
        
        assertParamList(map.get("java.jvmArgs"), TWO_PROPS);
        assertListInValue(map.get("javaExec.jvmArgs"), TWO_PROPS);
    }
    
    public void testExplicitJVMArgsWithSpace() throws Exception {
        JavaExecTokenProvider jetp = new JavaExecTokenProvider(createSimpleJavaProject());
        
        ExplicitProcessParameters params = ExplicitProcessParameters.builder().
                launcherArgs(THREE_PROPS).
                build();
        
        Map<String, String> map = jetp.createReplacements(ActionProvider.COMMAND_RUN, Lookups.singleton(params));
        assertEquals(4, map.size());

        assertParamList(map.get("java.jvmArgs"), THREE_PROPS);
        assertListInValue(map.get("javaExec.jvmArgs"), THREE_PROPS);
    }
    
    public void testAppParams() throws Exception {
        JavaExecTokenProvider jetp = new JavaExecTokenProvider(createSimpleJavaProject());
        
        ExplicitProcessParameters params = ExplicitProcessParameters.builder().
                args(TWO_PROPS).
                build();

        Map<String, String> map = jetp.createReplacements(ActionProvider.COMMAND_RUN, Lookups.singleton(params));
        assertEquals(4, map.size());

        assertArgs(map.get("javaExec.args"), TWO_PROPS);
        assertParamList(map.get("java.args"), TWO_PROPS);
    }
    
    public void testAppParamsWithSpace() throws Exception {
        JavaExecTokenProvider jetp = new JavaExecTokenProvider(createSimpleJavaProject());
        
        ExplicitProcessParameters params = ExplicitProcessParameters.builder().
                args(THREE_PROPS).
                build();

        Map<String, String> map = jetp.createReplacements(ActionProvider.COMMAND_RUN, Lookups.singleton(params));
        assertEquals(4, map.size());

        assertArgs(map.get("javaExec.args"), THREE_PROPS);
        assertParamList(map.get("java.args"), THREE_PROPS);
    }
}
