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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.netbeans.api.project.Project;
import org.netbeans.modules.project.dependency.impl.DependencyResultContextImpl;
import org.netbeans.modules.project.dependency.impl.DependencySpiAccessor;
import org.netbeans.modules.project.dependency.impl.ProjectModificationResultImpl;
import org.netbeans.modules.project.dependency.spi.DependencyModifierContext;
import org.netbeans.modules.project.dependency.spi.ProjectDependenciesImplementation;
import org.netbeans.modules.project.dependency.spi.ProjectDependencyModifier;
import org.openide.util.Lookup;

/**
 * Project Query that collects dependencies using project-specific services.
 * @author sdedic
 */
public final class ProjectDependencies {
    /**
     * Finds project dependencies.
     * 
     * @param target the project to query
     * @param query the query
     * @return the dependency tree, result of the query.
     * @throws ProjectOperationException 
     */
    public static DependencyResult findDependencies(Project target, DependencyQuery query) throws ProjectOperationException {
        Collection<? extends ProjectDependenciesImplementation> pds = target.getLookup().lookupAll(ProjectDependenciesImplementation.class);
        if (pds.isEmpty()) {
            return null;
        }
        DependencyResultContextImpl context = new DependencyResultContextImpl();
        // force load and init class.
        ProjectDependenciesImplementation.class.getMethods();
        ProjectDependenciesImplementation.Context apiContext = DependencySpiAccessor.get().createContextImpl(context);
        List<ProjectDependenciesImplementation.Result> rs = new ArrayList<>();
        for (ProjectDependenciesImplementation impl : pds) {
            ProjectDependenciesImplementation.Result r = impl.findDependencies(query, apiContext);
            if (r != null) {
                rs.add(r);
                Lookup lkp = r.getLookup();
                if (lkp != null) {
                    context.addLookup(lkp);
                }
            }
        }
        
        // None of the providers recognized the project.
        if (context.getProjectSpec() == null) {
            return null;
        }
        
        return new DependencyResult(target, rs, context, query);
    }
    
    /**
     * Creates the dependency query builder
     * @return the builder instance.
     */
    public static DependencyQueryBuilder newBuilder() {
        return new DependencyQueryBuilder();
    }
    
    /**
     * Creates a simple query. The query runs offline and uses available caches. Different
     * behaviour can be configured by using {@link DependencyQueryBuilder}
     * @param scopes scope(s) to query
     * @return the query instance
     */
    public static DependencyQuery newQuery(Scope... scopes) {
        return newBuilder().scope(scopes).build();
    }
    
    /**
     * Specifies the preferred handling of duplicates. Duplicate elimination is always based on a best-effort: a 
     * duplicate may be eventually reported, if the implementation does not the knowledge of exact duplicates.
     */
    public enum Duplicates {
        /**
         * Report duplicate artifacts. The duplicate and its entire subtree will be repeated in the report. Ideal for 
         * exhaustive traversal. Dependencies may be marked with {@link Dependency#getOriginal()}. This is the default setting.
         */
        DUPLICATE, 
        
        /**
         * A duplicate will be filtered out entirely, each artifact will be present at most once in the tree. Use if you are
         * interested in collecting dependencies.
         */
        FILTER, 
        
        /**
         * A duplicate will be present, but will report no transitive dependencies. Preferred, if it is not necessary to process
         * transitive dependencies for each artifact's appearance. Trimmed dependencies should be marked with {@link Dependency#getOriginal()}
         */
        LEAVES
    }

    /**
     * Builder that can create {@link DependencyQuery} instance.
     */
    public static final class DependencyQueryBuilder {
        private Set<Scope> scopes;
        private Dependency.Filter filter;
        private boolean offline = true;
        private boolean flush;
        private Duplicates duplicates = Duplicates.DUPLICATE;
        
        private DependencyQueryBuilder() {
        }
        
        public DependencyQuery build() {
            if (scopes == null) {
                scope(Scopes.COMPILE);
            }
            return new DependencyQuery(scopes, filter, offline, flush, duplicates);
        }
        
        /**
         * Set the filter. Previous filter is overwritten.
         * @param f the filter, {@code null} to clear the filter.
         * @return builder instance
         */
        public DependencyQueryBuilder filter(Dependency.Filter f) {
            this.filter = f;
            return this;
        }
        
        /**
         * Adds scopes to the query.
         * @param s additional scopes
         * @return builder instance
         */
        public DependencyQueryBuilder scope(Scope... s) {
            if (s == null || s.length == 0) {
                return this;
            }
            if (scopes == null) {
                scopes = new LinkedHashSet<>();
            }
            scopes.addAll(Arrays.asList(s));
            return this;
        }
        
        /**
         * Allow to work online. By default, dependency reading may fail, if some
         * of the artifacts is not available locally.
         * @return builder instance.
         */
        public DependencyQueryBuilder online() {
            this.offline = false;
            return this;
        }
        
        /**
         * Require offline operation. Dependency reading may fail, if some
         * of the artifacts is not available locally.
         * @return builder instance.
         */
        public DependencyQueryBuilder offline() {
            this.offline = true;
            return this;
        }
        
        /**
         * Requests to throw away any possible caches, like project model in memory and try to
         * read the data again from the project files.
         * @return builder instance.
         */
        public DependencyQueryBuilder withoutCaches() {
            flush = true;
            return this;
        }
    }
    
    public static final class DependencyQuery {
        private final Set<Scope> scopes;
        private final Dependency.Filter filter;
        private final boolean offline;
        private final boolean flushChaches;
        private final Duplicates dupls;

        private DependencyQuery(Set<Scope> scopes, Dependency.Filter filter, boolean offline, boolean flushCaches, Duplicates dupls) {
            this.scopes = scopes == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
            this.filter = filter;
            this.offline = offline;
            this.flushChaches = flushCaches;
            this.dupls = dupls;
        }

        public Duplicates getDuplicates() {
            return dupls;
        }

        public Set<Scope> getScopes() {
            return scopes;
        }

        public Dependency.Filter getFilter() {
            return filter;
        }

        public boolean isOffline() {
            return offline;
        }

        public boolean isFlushChaches() {
            return flushChaches;
        }
    }
    
    /**
     * Convenience method that modifies the project to add dependencies.
     * @param project the target project
     * @param dependencies list of dependencies to add
     * @return modification result
     * @see #modifyDependencies(org.netbeans.api.project.Project, org.netbeans.modules.project.dependency.DependencyChange) 
     * @since 1.7
     */
    public static ProjectModificationResult addDependencies(Project project, Dependency... dependencies) throws DependencyChangeException, ProjectOperationException {
        return modifyDependencies(project, new DependencyChangeRequest(
                Collections.singletonList(
                        DependencyChange.builder(DependencyChange.Kind.ADD).dependency(dependencies).create())
        ));
    }

    /**
     * Convenience method that modifies the project to remove dependencies.
     * @param project the target project
     * @param dependencies list of dependencies to remove
     * @return modification result
     * @see #modifyDependencies(org.netbeans.api.project.Project, org.netbeans.modules.project.dependency.DependencyChange) 
     * @since 1.7
     */
    public static ProjectModificationResult removeDependencies(Project project, List<Dependency> dependencies) throws DependencyChangeException, ProjectOperationException {
        return modifyDependencies(project, new DependencyChangeRequest(
                Collections.singletonList(DependencyChange.builder(DependencyChange.Kind.REMOVE).dependency(dependencies).create())));
    }
    
    /**
     * Convenience method that makes simple dependency change, either add or remove. For detailed information,
     * see {@link #modifyDependencies(org.netbeans.api.project.Project, org.netbeans.modules.project.dependency.DependencyChangeRequest)}.
     * @param p the project
     * @param change add or remove change
     * @return modification result.
     * @throws DependencyChangeException if the modification fails because of project constraints
     * @throws ProjectOperationException in case of project system failure.
     */
    public static ProjectModificationResult modifyDependencies(Project p, DependencyChange change) throws DependencyChangeException, ProjectOperationException {
        return modifyDependencies(p, new DependencyChangeRequest(Collections.singletonList(change)));
    }
    
    /**
     * Makes modifications to project dependencies. The modifications are specified in the request. Note that the project system
     * is likely to require reload of the project after the change, since dependency changes change resolution of everything, and if plugins
     * are added/removed, the entire build system may work differently. All dependency changes are better done in one request, not
     * sequentially, so the disruption to the project model is minimized.
     * <p/>
     * The operation may also throw {@link ProjectOperationException} to indicate that the operation cannot be done safely, or there's not enough
     * project information to perform the operation.
     * 
     * @since 1.7
     * @param p the project
     * @param  the change to made
     * @return proposed changes to make.
     * @throws DependencyChangeException in case of error or dependency conflicts
     * @throws ProjectOperationException if the project could not be properly loaded
     */
    public static ProjectModificationResult modifyDependencies(Project p, DependencyChangeRequest change) throws DependencyChangeException, ProjectOperationException {
        Collection<? extends ProjectDependencyModifier> modifiers = p.getLookup().lookupAll(ProjectDependencyModifier.class);
        if (modifiers.isEmpty()) {
            // simply unsupported.
            return null;
        }
        
        ProjectModificationResultImpl impl = new ProjectModificationResultImpl(p);
        DependencyModifierContext ctx = DependencySpiAccessor.get().createModifierContext(change, impl);
        
        boolean accepted = false;
        for (ProjectDependencyModifier m : modifiers) {
            ProjectDependencyModifier.Result res = m.computeChange(ctx);
            if (res != null) {
                accepted = true;
                impl.add(res);
            }
        }
        if (!accepted) {
            return null;
        }
        
        return new ProjectModificationResult(impl);
    }
}
