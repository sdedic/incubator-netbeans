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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.project.dependency.spi.ProjectDependencyScopes;

/**
 * Provides access to the set of scopes of a project. Apart from a set of scopes, 
 * ProjectScopes can tell which scopes are <b>implied</b> by a particular Scope,
 * directly or indirectly. If a {@link Dependency} is present in a {@link Scope} A,
 * it will be present in all implied Scopes, unless it is specifically excluded
 * or overridden in those Scopes.
 * <p>
 * This API allows to check if a project supports a particular Scope, or select
 * a Scope that will contribute to a desired target Scope.
 * 
 * @author sdedic
 */
public final class ProjectScopes {
    private static final Logger LOG = Logger.getLogger(ProjectScopes.class.getName());
    
    final List<ProjectDependencyScopes> impls;
    
    volatile Map<String, Scope> name2Scope;
    volatile Collection<? extends Scope> scopes;
    
    // @GuardedBy(this)
    Map<String, Set<Scope>> direct = new HashMap<>();
    // @GuardedBy(this)
    Map<String, Set<Scope>> indirect = new HashMap<>();

    ProjectScopes(List<ProjectDependencyScopes> results) {
        this.impls = results;
    }
    
    private Collection<Scope> checkCycle(Map<String, Scope> scopeList, ProjectDependencyScopes x, Scope scope, Collection<? extends Scope> ss, Map<String, Set<Scope>> check) {
        Collection<Scope> ret = new ArrayList<>();
        for (Scope s : ss) {
            Scope u = scopeList.get(s.name());
            Set<Scope> closure = check.get(u.name());
            
            if (u == null) {
                continue;
            }
            if (closure != null && closure.contains(scope.name())) {
                LOG.log(Level.SEVERE, "Scope provider {0} closes a cycle for scope {1}, with implies {2}", new Object[] { 
                    x, scope.name(), s.name()
                });
                continue;
            }
            ret.add(u);
        }
        return ret;
    }
    
    private void init() {
        if (name2Scope != null) {
            return;
        }
        Map<String, Scope> scopeList = new LinkedHashMap<>();
        Map<String, Set<Scope>> indirectScopes = new LinkedHashMap<>();
        Map<String, Set<Scope>> directScopes = new LinkedHashMap<>();
        impls.forEach(r -> {
            for (Scope s : r.scopes()) {
                scopeList.putIfAbsent(s.name(), s);
            }
        });
        
        for (Scope scope : scopeList.values()) {
            impls.forEach(x -> {
                Collection<? extends Scope> implied = x.implies(scope, true);
                if (implied != null) {
                    implied = checkCycle(scopeList, x, scope, implied, indirectScopes);
                    directScopes.computeIfAbsent(scope.name(), n -> new LinkedHashSet<>()).addAll(implied);
                }
                
                Collection<Scope> impliedIndirect = computeIndirectClosure(x, scope);
                if (impliedIndirect != null) {
                    // indirect relations must contain all direct ones.
                    if (!impliedIndirect.isEmpty() && implied != null && !impliedIndirect.containsAll(implied)) {
                        Set<Scope> diff = new HashSet<>(implied);
                        diff.removeAll(impliedIndirect);
                        LOG.log(Level.WARNING, "Provider {0} is inconsistent, does not report direct relations {1} in indirect set {2}", new Object[] {
                            x, diff, impliedIndirect
                        });
                        impliedIndirect.addAll(diff);
                    }
                    impliedIndirect = checkCycle(scopeList, x, scope, impliedIndirect, indirectScopes);
                    indirectScopes.computeIfAbsent(scope.name(), n -> new LinkedHashSet<>()).addAll(impliedIndirect);
                }
            });
        }
        synchronized (this) {
            if (name2Scope != null) {
                return;
            }
            this.direct = directScopes;
            this.indirect = indirectScopes;
            scopes = Collections.unmodifiableSet(new LinkedHashSet<>(scopeList.values()));
            name2Scope = scopeList;
        }
    }

    /**
     * Returns set of {@link Scope Scopes} supported by the project. The returned Collection is unmodifiable.
     * @return set of Scopes.
     */
    public Collection<? extends Scope> scopes() {
        init();
        return scopes;
    }
    
    private Set<Scope> computeIndirectClosure(ProjectDependencyScopes impl, Scope s) {
        Set<Scope> indirects = new HashSet<>();
        Set<Scope> processed = new HashSet<>();
        Deque<Scope> toProcess = new ArrayDeque<>();
        toProcess.add(s);
        while (!toProcess.isEmpty()) {
            Scope next = toProcess.poll();
            if (!processed.add(next)) {
                continue;
            }
            Collection<? extends Scope> add = impl.implies(next, false);
            if (add == null) {
                add = impl.implies(next, true);
            }
            if (add != null) {
                add = new ArrayList<>(add);
                if (add != null) {
                    indirects.addAll(add);
                    add.removeAll(processed);
                    toProcess.addAll(add);
                }
            }
        }
        return indirects;
    }

    /**
     * Returns Scopes implied by the initial one. If `direct' is {@code true}, 
     * it returns just immediate descendants that "scope" contributes to. With
     * `direct' set to {@code false}, it returns a closure of all scopes to which
     * "scope" contributes its dependencies, taking into account possible filtering
     * when importing from scope to scope.
     * @param scope origin scope
     * @param direct if {@code true}, returns just direct consumers of "scope".
     * @return set of Scopes contributed to.
     */
    public Collection<? extends Scope> implies(Scope scope, boolean direct) {
        init();
        Map<String, Set<Scope>> m = direct ? this.direct : indirect;
        Set<Scope> r = m.get(scope.name());
        return r != null ? r : List.of();
    }
}
