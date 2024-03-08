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

/**
 * Merges in several ProjectScopes into a single view. The class also does canonicalization,
 * if more implementations use the same scope ID. If an implies() would close a cycle, the
 * relation will not be added, and its contributor will be logged at error level.
 * 
 * @author sdedic
 */
final class ProjectScopesImpl implements ProjectScopes {
    private static final Logger LOG = Logger.getLogger(ProjectScopesImpl.class.getName());
    
    final List<ProjectScopes> impls;
    
    volatile Map<String, Scope> name2Scope;
    volatile Collection<? extends Scope> scopes;
    
    // @GuardedBy(this)
    Map<String, Set<Scope>> direct = new HashMap<>();
    // @GuardedBy(this)
    Map<String, Set<Scope>> indirect = new HashMap<>();

    ProjectScopesImpl(List<ProjectScopes> results) {
        this.impls = results;
    }
    
    private Collection<Scope> checkCycle(Map<String, Scope> scopeList, ProjectScopes x, Scope scope, Collection<? extends Scope> ss, Map<String, Set<Scope>> check) {
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

    @Override
    public Collection<? extends Scope> scopes() {
        init();
        return scopes;
    }
    
    private Set<Scope> computeIndirectClosure(ProjectScopes impl, Scope s) {
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

    @Override
    public Collection<? extends Scope> implies(Scope scope, boolean direct) {
        init();
        Map<String, Set<Scope>> m = direct ? this.direct : indirect;
        Set<Scope> r = m.get(scope.name());
        return r != null ? r : List.of();
        
        /*
        Map<String, Set<Scope>> mIndirect;
        Set<Scope> r;
        
        synchronized (this) {
            r = m.get(scope.name());
            if (r != null) {
                return r;
            }
            mIndirect = new HashMap<>(indirect);
        }
        
        final Set fr = new LinkedHashSet<>();
        impls.forEach(x -> {
            Collection<? extends Scope> ss = x.implies(scope, direct);
            if (ss == null && !direct) {
                ss = computeIndirectClosure(x, scope);
            }
            for (Scope s : ss) {
                Scope u = name2Scope.get(s.name());
                Set<Scope> closure = mIndirect.get(u.name());
                if (closure != null && closure.contains(scope.name())) {
                    LOG.log(Level.SEVERE, "Scope provider {0} closes a cycle for scope {1}, with implies {2}", new Object[] { 
                        x, scope.name(), s.name()
                    });
                    continue;
                }
                if (u != null) {
                    fr.add(u);
                }
            }
        });
        r = Collections.unmodifiableSet(fr);
        synchronized (this) {
            if (!m.containsKey(scope.name())) {
                m.put(scope.name(), r);
            }
        }
        return r != null ? r : List.of();
        */
    }
}
