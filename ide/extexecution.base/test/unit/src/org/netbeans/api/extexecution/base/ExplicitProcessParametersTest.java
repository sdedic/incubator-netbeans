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
package org.netbeans.api.extexecution.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author sdedic
 */
public class ExplicitProcessParametersTest extends NbTestCase {

    public ExplicitProcessParametersTest(String name) {
        super(name);
    }
    
    List<String> existingVMArgs = new ArrayList<>(Arrays.asList(
        "-Xmx100m"
    ));
    
    List<String> existingAppArgs = new ArrayList<>(Arrays.asList(
        "File1"
    ));
    
    private void assertContains(List<String> args, String... items) {
        for (String s : items) {
            assertTrue("Must contain: " + s, args.stream().map(String::trim).filter(a -> s.equals(a)).findAny().isPresent());
        }
    }
    
    private void assertNotContains(List<String> args, String... items) {
        for (String s : items) {
            assertFalse("Must NOT contain: " + s, args.stream().map(String::trim).filter(a -> s.equals(a)).findAny().isPresent());
        }
    }
    
    /**
     * Empty params, or params created w/o any content should have no effect when applied.
     * 
     * @throws Exception 
     */
    public void testEmptyExplicitParameters() throws Exception {
        ExplicitProcessParameters empty = ExplicitProcessParameters.makeEmpty();
        assertTrue(empty.isEmpty());
        assertFalse(empty.isArgReplacement());
        assertFalse(empty.isPriorityArgReplacement());

        ExplicitProcessParameters empty2 = ExplicitProcessParameters.builder().build();
        assertTrue(empty2.isEmpty());
        assertFalse(empty2.isArgReplacement());
        assertFalse(empty2.isPriorityArgReplacement());
        
        ExplicitProcessParameters base = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                build();
        
        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                combine(base).
                combine(empty).
                build();
        
        ExplicitProcessParameters p2 = ExplicitProcessParameters.builder().
                combine(base).
                combine(empty).
                build();
        
        assertEquals(existingVMArgs, p.getPriorityArguments());
        assertEquals(existingAppArgs, p.getArguments());

        assertEquals(existingVMArgs, p2.getPriorityArguments());
        assertEquals(existingAppArgs, p2.getArguments());
    }
    
    public void testSingleAddVMParams() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                priorityArg("-Dfoo=bar").
                build();
        
        assertFalse("No override requested", extra.isPriorityArgReplacement());
        assertFalse("No arguments given", extra.isArgReplacement());
        assertNull(extra.getArguments());

        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();
        
        
        assertContains(p.getPriorityArguments(), "-Xmx100m", "-Dfoo=bar");
    }
    
    public void testSingleReplaceAppParams() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                arg("avalanche").
                build();
        
        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();

        assertFalse("No override requested", extra.isPriorityArgReplacement());
        assertTrue("Args must be replaced by default", extra.isArgReplacement());
        assertNull(extra.getPriorityArguments());

        
        assertContains(p.getArguments(), "avalanche");
        assertNotContains(p.getArguments(), "File1");
    }
    
    public void testSingleDefaultLaunchAugmentation() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                arg("avalanche").
                priorityArg("-Dfoo=bar").
                build();
        
        assertFalse("No prio override requested", extra.isPriorityArgReplacement());
        assertTrue("Args must be replaced by default", extra.isArgReplacement());
        assertNotNull(extra.getPriorityArguments());
        assertNotNull(extra.getArguments());

        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();
        
        assertContains(p.getPriorityArguments(), "-Xmx100m", "-Dfoo=bar");
        assertContains(p.getArguments(), "avalanche");
        assertNotContains(p.getArguments(), "File1");
    }
    
    /**
     * Checks that VM parmeters can be replaced.
     * @throws Exception 
     */
    public void testReplacePriorityArgs() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                priorityArg("-Dfoo=bar").
                appendPriorityArgs(false).
                build();
        
        ExplicitProcessParameters extra2 = ExplicitProcessParameters.builder().
                priorityArg("-Dsun=shines").
                build();

        assertTrue("Must replace priority args", extra.isPriorityArgReplacement());
        assertFalse("No arguments were specified", extra.isArgReplacement());
        assertNull(extra.getArguments());

        
        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();
        
        assertContains(p.getPriorityArguments(), "-Dfoo=bar");
        assertNotContains(p.getPriorityArguments(), "-Xmx100m");
        
        ExplicitProcessParameters p2 = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(
                    ExplicitProcessParameters.buildExplicitParameters(Arrays.asList(extra, extra2))
                ).
                build();
        
        assertContains(p2.getPriorityArguments(), "-Dfoo=bar", "-Dsun=shines");
        assertNotContains(p2.getPriorityArguments(), "-Xmx100m");
    }
    
    public void testAppendNormalArgs() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                appendArgs(true).
                args("File2", "File3").
                build();
        
        assertFalse("Must append args", extra.isArgReplacement());
        assertFalse("No prio arguments were specified", extra.isPriorityArgReplacement());
        assertNull(extra.getPriorityArguments());

        
        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();
        
        assertContains(p.getPriorityArguments(), "-Xmx100m");
        assertEquals(Arrays.asList("File1", "File2", "File3"), p.getArguments());
    }
    
    public void testAddMorePriorityArgs() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                priorityArg("-Dfoo=bar").
                priorityArg("-Xms=200m").
                appendPriorityArgs(false).
                build();
        
        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();
        
        assertContains(p.getPriorityArguments(), "-Dfoo=bar", "-Xms=200m");
        assertNotContains(p.getPriorityArguments(), "-Xmx100m");
    }
    
    public void testReplaceWithMoreArgs() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                arg("avalanche").
                arg("storm").
                build();
        
        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();
        
        assertContains(p.getArguments(), "avalanche", "storm");
        assertNotContains(p.getArguments(), "File1");
    }
    
    public void testJustClearArguments() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                appendArgs(false).
                appendPriorityArgs(false).
                build();
        
        ExplicitProcessParameters p = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(extra).
                build();
        
        assertNull(p.getPriorityArguments());
        assertNull(p.getArguments());
    }
    
    public void testReplaceDiscardAndAddMorePriortyArgs() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                priorityArg("-Dfoo=bar").
                appendPriorityArgs(false).
                build();
        
        ExplicitProcessParameters extra2 = ExplicitProcessParameters.builder().
                priorityArg("-Xms=200m").
                build();
     
        ExplicitProcessParameters check1 = ExplicitProcessParameters.buildExplicitParameters(Arrays.asList(extra, extra2));
        
        assertFalse(check1.isArgReplacement());
        assertTrue(check1.isPriorityArgReplacement());
        assertEquals(2, check1.getPriorityArguments().size());
        assertNull(check1.getArguments());
        
        ExplicitProcessParameters base = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                build();
        
        ExplicitProcessParameters res;
        
        res = ExplicitProcessParameters.builder().
                combine(base).
                combine(check1).
                build();
        
        assertEquals(Arrays.asList("-Dfoo=bar", "-Xms=200m", "~", "File1"),
                res.getAllArguments("~"));
    }
    
    public void testDiscardAllEffects() throws Exception {
        ExplicitProcessParameters extra = ExplicitProcessParameters.builder().
                priorityArg("-Dfoo=bar").
                arg("avalanche").
                build();
        
        ExplicitProcessParameters discard = ExplicitProcessParameters.builder().
                appendArgs(false).
                appendPriorityArgs(false).
                build();
        
        ExplicitProcessParameters override = ExplicitProcessParameters.buildExplicitParameters(Arrays.asList(extra, discard));
        
        assertTrue(override.isEmpty());
        
        ExplicitProcessParameters result = ExplicitProcessParameters.builder().
                priorityArgs(existingVMArgs).
                args(existingAppArgs).
                combine(override).
                build();
        
        assertEquals(Arrays.asList("-Xmx100m"), result.getPriorityArguments());
        assertEquals(Arrays.asList("File1"), result.getArguments());
    }
}
