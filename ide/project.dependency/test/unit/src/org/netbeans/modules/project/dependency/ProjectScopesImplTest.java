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
package org.netbeans.modules.project.dependency;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import static java.util.Map.entry;
import java.util.Set;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import org.junit.Assert;
import org.junit.Test;
import org.netbeans.modules.project.dependency.spi.ProjectDependencyScopes;

/**
 *
 * @author sdedic
 */
public class ProjectScopesImplTest {
    private static final Scope API = Scope.named("api");
    private static final Scope COMPILE_ONLY = Scope.named("compileOnly");
    private static final Scope COMPILE = Scope.named("compile");
    private static final Scope RUNTIME = Scope.named("runtime");
    private static final Scope RUNTIME_CP = Scope.named("runtimeClasspath");
    private static final Scope TEST_ONLY = Scope.named("testOnly");
    private static final Scope TEST = Scope.named("test");
    private static final Scope TEST_RUNTIME = Scope.named("testRuntime");
    private static final Scope IMPLEMENTATION = Scope.named("implementation");
    
    private Set<Scope> SET1 = Set.of(
            API, COMPILE, RUNTIME, IMPLEMENTATION, RUNTIME_CP
    );
    
    private Map<Scope, Set<Scope>> DIRECT1 = Map.ofEntries(
        entry(COMPILE_ONLY, Set.of(COMPILE)),
        entry(API, Set.of(COMPILE, RUNTIME)),
        entry(IMPLEMENTATION, Set.of(COMPILE, RUNTIME)),
            entry(RUNTIME, Set.of(RUNTIME_CP))
    );
    
    private Set<Scope> SET2 = Set.of(
            TEST, TEST_RUNTIME, TEST_ONLY
    );
    
    private Map<Scope, Set<Scope>> DIRECT2 = Map.ofEntries(
        entry(API, Set.of(TEST)),
        entry(TEST, Set.of(TEST_RUNTIME)),
//        entry(IMPLEMENTATION, Set.of(TEST)),
        entry(RUNTIME, Set.of(TEST_RUNTIME)),
        entry(TEST_ONLY, Set.of(TEST))
    );
    
    private Set<Scope> SET3 = Set.of(
            TEST, TEST_RUNTIME
    );
    
    private Map<Scope, Set<Scope>> DIRECT3 = Map.ofEntries(
        entry(IMPLEMENTATION, Set.of(TEST)),
        entry(RUNTIME, Set.of(TEST_RUNTIME))
    );
    
    private static class PS implements ProjectDependencyScopes {
        private final Set<Scope> scopes;
        private final Map<Scope, Set<Scope>> implied;

        public PS(Set<Scope> scopes, Map<Scope, Set<Scope>> implied) {
            this.scopes = scopes;
            this.implied = implied;
        }
        
        @Override
        public Collection<? extends Scope> scopes() {
            return scopes;
        }

        @Override
        public Collection<? extends Scope> implies(Scope s, boolean direct) {
            return direct ? implied.get(s) : null;
        }
        
    }
    
    /**
     * Checks single set of relations. Checks that direct and indirect queries work.
     */
    @Test
    public void testSingleRelations() throws Exception {
        ProjectScopes ps = new ProjectScopes(List.of(new PS(SET1, DIRECT1)));
        assertEquals(SET1.size(), ps.scopes().size());
        
        assertTrue("Compile directly depends on Api", ps.implies(API, true).contains(COMPILE));
        assertFalse("RuntimeClassPath does not directly depend on API", ps.implies(API, true).contains(RUNTIME_CP));
        assertTrue("RuntimeClasspath indirectly depends on API", ps.implies(API, false).contains(RUNTIME_CP));
        assertFalse("CompileOnly is unrelated to runtimeClasspath", ps.implies(COMPILE_ONLY, false).contains(RUNTIME_CP));
    }
    
    @Test
    public void testSimpleMerge() throws Exception {
        ProjectScopes ps = new ProjectScopes(List.of(
            new PS(SET1, DIRECT1),
            new PS(SET2, DIRECT2))
        );
        
        assertTrue("testRuntimeClasspath indirectly depends on API", ps.implies(API, false).contains(TEST_RUNTIME));
        assertFalse("API is unrelated to testOnly", ps.implies(API, false).contains(TEST_ONLY));
    }
    
    @Test
    public void testOverlappingMerge() throws Exception {
        ProjectScopes ps1 = new ProjectScopes(List.of(
            new PS(SET1, DIRECT1),
            new PS(SET2, DIRECT2)
        ));
        ProjectScopes ps = new ProjectScopes(List.of(
            new PS(SET1, DIRECT1),
            new PS(SET2, DIRECT2),
            new PS(SET2, DIRECT3)
        ));
        
        assertEquals(ps1.scopes(), ps.scopes());
        Assert.assertNotEquals(ps1.implies(IMPLEMENTATION, false), ps.implies(IMPLEMENTATION, false));
    }
}
