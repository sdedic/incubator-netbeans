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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NullAllowed;

/**
 * Represents a dependency of an artifact. The {@link #getChildren()} artifacts are
 * needed in a certain {@link #getScope()}; should be ignored in unrelated scopes.
 * The artifact that is subject of this dependency is the {@link #getArtifact()} and/or {@link #getProject()}.
 * Both may be present. Children are expressed using other {@link Dependency} instances.
 * <p>
 * If `project` is filled, it means that the build system may eventually build this dependency. A project does not need to produce a 
 * publishable (identifiable) artifact; in such cases, the {@link #getArtifact} may return {@code null}, but the project should be filled.
 * Artifacts with blank {@code groupId} are reserved to represent local resources not managed by the build system
 * <p>
 * Children may be lazy-computed. Avoid traversing to children unless they are really needed.
 * <p>
 * The hashCode and equals favor artifact over project specification. Scope is not taken into account.
 * <p>
 * {@link #getProjectData()} contains implementation-specific data. They should be filled on output from the queries, to allow further
 * inspection using project or technology-specific APIs. Clients are advised to use Dependency instances obtained from queries in API
 * calls.
 * <p>
 * The Dependency may indirectly (through project data or suppliers) hold a reference on its originating {@link DependencyResult}. 
 * 
 * @author sdedic
 */
public final class Dependency {
    private final ArtifactSpec  artifact;
    private final ProjectSpec   project;
    private final Scope scope;

    /**
     * List of children, or {@code null}, if children are supplied by {@link #supplier}.
     */
    private volatile List<Dependency> children;
    
    /**
     * Potential link to an equivalent / original node within the dependency graph.
     */
    private volatile Dependency original;

    /**
     * Supplier that lazily computes the children. One-time, then cleared.
     */
    private Function<Dependency, List<Dependency>>supplier;
   
    /**
     * Lazily provides the linked node. One time, then cleared.
     */
    private volatile Supplier<Dependency>  originalProvider;
    
    /**
     * Implementation-specific data, possibly {@code null}
     */
    final Object data;

    Dependency(ProjectSpec project, ArtifactSpec artifact, 
            Function<Dependency, List<Dependency>> childSupplier, List<Dependency> children,
            Scope scope, 
            Supplier<Dependency> originalSupplier, Dependency original, Object data) {
        this.project = project;
        this.artifact = artifact;
        this.supplier = childSupplier;
        this.scope = scope;
        this.data = data;
        this.original = original;
        this.children = children;
        this.originalProvider = originalSupplier;
    }

    Dependency(ProjectSpec project, ArtifactSpec artifact, List<Dependency> children, Scope scope, Dependency original, Object data) {
        this.project = project;
        this.artifact = artifact;
        this.children = children;
        this.scope = scope;
        this.original = original;
        this.data = data;
        this.originalProvider = null;
    }

    /**
     * Returns the artifact that represents this dependency. For project dependencies, the artifact returned may be
     * {@code null}, if the project does not translate to an identifiable artifact. But even such dependencies can have
     * further children.
     * 
     * @return 
     */
    @CheckForNull
    public ArtifactSpec getArtifact() {
        return artifact;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (artifact != null) {
            hash = 83 * hash + Objects.hashCode(this.artifact);
        } else if (project != null) {
            hash = 83 * hash + Objects.hashCode(this.project);
        }
        return hash;
    }

    /**
     * Matches dependencies. <b>This is not full equals</b>: two dependencies are equal, if
     * <ul>
     * <li>their {@link #getArtifact()}s are not {@code null} and are equal, or
     * <li>their {@link #getProject()}s are equal
     * </ul>
     * Dependency scope is not taken into account. This equals definition allows to match
     * artifacts, make unique sets of them and should be sufficient for most purposes.
     * 
     * @param obj
     * @return 
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Dependency other = (Dependency) obj;
        // two dependnencies are equal if:
        // its artifacts are equal, or
        //      one of them does not define artifact at all, and
        //      their project specs equal.
        if (!(this.artifact == null || other.artifact == null)) {
            return Objects.equals(this.artifact, other.artifact);
        }
        return Objects.equals(this.project, other.project);
    }

    /**
     * Returns children of the dependency. If children area lazily computed, the
     * method materializes the child Dependency instances. Depending on
     * query options, the children may be enumerated in project-declaration order.
     * 
     * @return list of children.
     */
    public List<Dependency> getChildren() {
        List<Dependency> resolved = this.children;
        if (resolved != null) {
            return resolved;
        }
        Function<Dependency, List<Dependency>> s = this.supplier;
        if (supplier != null) {
            resolved = s.apply(this);
        } else {
            resolved = this.children;
        }
        synchronized (this) {
            if (children == null) {
                children = resolved;
                supplier = null;
            }
        }
        return resolved;
    }
    
    /**
     * Returns project description for project dependencies, otherwise {@code null}
     * The Dependency may also return {@link #getArtifact()}, but some projects do not produce
     * externalized artifacts or the artifact specification is not known.
     * @return project description for this dependency.
     */
    @CheckForNull
    public ProjectSpec getProject() {
        return project;
    }

    /**
     * Describes how the dependency is used in the project. 
     * @return dependency scope
     */
    public Scope getScope() {
        return scope;
    }
    
    /**
     * Access to implementation-specific data. Typically contains project-system model
     * object that describes further the dependency. You must use project system - specific
     * tools to access or extract useful information.
     * @return implementation data. 
     */
    public Object getProjectData() {
        return data;
    }
    
    /**
     * String representation. The value is for diagnostic purposes only, its format may change
     * at any time.
     * @return string representation of the dependency.
     */
    public String toString() {
        if (artifact != null) {
            return getArtifact() + "[" + scope + "]";
        } else {
            return getProject() + "[" + scope + "]";
        }
    }
    
    /**
     * True if the dependency duplicates other node in the graph. In that case
     * {@link #getOriginal} should point to the original node.
     * 
     * Note that duplicates may still have children, but the children should be the same
     * as children of {@link #getOriginal()}.
     * 
     * @return true, if the node is duplicate.
     */
    public boolean isDuplicate() {
        return originalProvider != null || original != null;
    }

    /**
     * Returns the original dependency for duplicates. Duplicate dependencies may
     * occur in the dependency tree. To avoid complete expansion by the client, the dependency
     * indicates it is a duplicate ({@link #isDuplicate()} and points to the original one.
     * The client may just detect a duplicate and rely on reaching the original at some time,
     * or proceed to the original. Note that it is not guaranteed that all duplicates point
     * to the same original; just that each of duplicates is marked.
     * @return original dependency or {@code null}
     */
    public Dependency getOriginal() {
        if (original != null || originalProvider == null) {
            return original;
        }
        Dependency d = original = originalProvider.get();
        this.originalProvider = null;
        return d;
    }
    
    /**
     * A convenience method to make a dependency descriptor.
     * @param spec artifact specification
     * @param scope the dependency scope
     * @return dependency instance
     */
    public static Dependency make(ArtifactSpec spec, Scope scope) {
        return create(spec, scope, Collections.emptyList(), null);
    }
    
    /**
     * Creates an artifact dependency. The artifact need not physically exist on the filesystem, but its coordinates
     * must be known. 
     * @param artifact artifact of this dependency
     * @param scope scope of the dependency
     * @param children precomputed list of children, can be {@code null}.
     * @param data project-specific data
     * @return Dependency instance
     */
    public static Dependency create(ArtifactSpec artifact, Scope scope, @NullAllowed List<Dependency> children, Object data) {
        return new Dependency(null, artifact, children, scope, null, data);
    }
    
    /**
     * Creates a dependency on a project. Project or artifact can be {@code null}, but at least one of them
     * is required. If `project` is filled, it means that the build system may eventually build this dependency. 
     * Artifact may be {@code null} for dependencies that do not have any identification. Artifacts with blank {@code groupId}
     * are reserved to represent local resources not managed by the build system.
     * @param project project specification.
     * @param artifact artifact specification
     * @param scope usage of the dependency
     * @param children children of the dependency, can be {@code null}
     * @param data implementation-defined data
     * @return Dependency instance.
     */
    public static Dependency create(ProjectSpec project, ArtifactSpec artifact, Scope scope, @NullAllowed List<Dependency> children, Object data) {
        return new Dependency(project, artifact, children, scope, null, data);
    }
    
    /**
     * Makes a copy of the Dependency. Copy has no children; all other properties are copied. Use {@link Builder#addChildren(java.util.Collection)}
     * to copy over children references. This method ensures that all future-added Dependency properties are copied. The caller
     * can then override the values it needs to change.
     * 
     * @param blueprint the Dependency to copy
     * @param includeChildren clones the blueprint's dependencies.
     * @return copied instance.
     */
    public static Builder copy(Dependency blueprint, boolean includeChildren) {
        return new Builder(blueprint.getArtifact(), blueprint.getScope(), blueprint.getProjectData()).
                project(blueprint.getProject());
    }
    
    /**
     * Creates a builder for the dependency for a artifact-based dependency.
     * @param spec artifact specification
     * @param scope dependency scope
     * @param data project-specific data.
     * @return builder builder
     */
    public static Builder builder(ArtifactSpec spec, Scope scope, @NullAllowed Object data) {
        return new Builder(spec, scope, data);
    }
    
    /**
     * Creates a builder for the dependency for a artifact-based dependency.
     * @param spec artifact specification
     * @param scope dependency scope
     * @param data project specific data
     * @return builder instance 
     */
    public static Builder builder(ProjectSpec spec, Scope scope, @NullAllowed Object data) {
        return new Builder(spec, scope, data);
    }

    /**
     * Builder that creates Dependency instances. Children and linked original Dependency
     * can be specified either as values, or as providers.
     */
    public static final class Builder {
        private ProjectSpec project;
        private ArtifactSpec artifact;
        private Scope scope;
        private List<Dependency> children;
        private Function<Dependency, List<Dependency>> childSupplier;
        private Object data;
        private Dependency original;
        private Supplier<Dependency> originalSupplier;

        Builder(ProjectSpec project, Scope scope, Object data) {
            this.project = project;
            this.scope = scope;
            this.data = data;
        }
        
        Builder(ArtifactSpec artifact, Scope scope, Object data) {
            this.artifact = artifact;
            this.scope = scope;
            this.data = data;
        }
        
        /**
         * Specifies the artifact for the dependency.
         * @param spec artifact
         * @return this Builder
         */
        public Builder artifact(ArtifactSpec spec) {
            this.artifact = spec;
            return this;
        }
        
        /**
         * Annotates the dependency with the project
         * @param spec the project
         * @return this builder.
         */
        public Builder project(ProjectSpec spec) {
            this.project = spec;
            return this;
        }
        
        /**
         * Adds lazy-computed children to the dependency. The provider function will
         * be called once to resolve children. Can not be combined with {@link #addChildren} methods.
         * @param provider resolver
         * @return builder instance.
         */
        public Builder children(Function<Dependency, List<Dependency>> provider) {
            if (provider != null) {
                this.children = null;
            }
            this.childSupplier = provider;
            return this;
        }
        
        /**
         * Adds children to the dependency. Exclusive with {@link #children(java.util.function.Function)} method. If used,
         * will remove any previous child provider.
         * @param children children to add.
         * @return builder instance.
         */
        public Builder children(Collection<Dependency> children) {
            this.childSupplier = null;
            if (this.children == null) {
                this.children = new ArrayList<>();
            }
            if (children != null) {
                this.children.addAll(children);
            }
            return this;
        }
        
        /**
         * Adds children to the dependency. Exclusive with {@link #children(java.util.function.Function)} method. If used,
         * will remove any previous child provider.
         * 
         * @param children children
         * @return builder instance.
         */
        public Builder children(Dependency... children) {
            this.childSupplier = null;
            if (children == null) {
                return this;
            }
            return children(Arrays.asList(children));
        }
        
        /**
         * Links the dependency to an original. Exclusive with {@link #linkOriginal(java.util.function.Supplier)},
         * calling with non-null "orig" will remove supplier.
         * @param orig original dependency
         * @return builder instance
         */
        public Builder linkOriginal(Dependency orig) {
            this.original = orig;
            if (orig != null) {
                originalSupplier = null;
            }
            return this;
        }
        
        /**
         * Links the dependency to an lazy-resolved original.
         * @param orig original
         * @return builder instance.
         */
        public Builder linkOriginal(Supplier<Dependency> orig) {
            this.originalSupplier = orig;
            if (orig != null) {
                this.original = null;
            }
            return this;
        }
        
        /**
         * Creates a dependency instance from the accumulated data.
         * @return initialized dependency
         */
        public Dependency create() {
            return new Dependency(project, artifact, childSupplier, children, scope, originalSupplier, original, data);
        }
    }

    /**
     * Allows to filter artifacts and their dependency subtrees.
     */
    public interface Filter {
        /**
         * Decide if the artifact 'a' and its dependencies should be included in the report.
         * @param s the scope which requires dependency on "a"
         * @param a the artifact
         * @return true, if "a" should be included in the result; false to exclude it and its
         * dependencies.
         */
        public boolean accept(Scope s, ArtifactSpec a);
    }
    
    /**
     * A convenience utility, that does a depth-first scan of the tree. For each node in the tree,
     * {@link #visitDependency} is invoked and should produce a result for the Dependency subtree of
     * type R. The default implementation will scan and reduce dependency's children. The default reduce
     * strategy is last-wins, the default scan result is {@code null}.
     * <p>
     * The utility class maintains the current path in the tree ({@link #getPath}).
     * 
     * @param <R> result type
     * @param <P> parameter type
     */
    public static class DependencyPathScanner<R, P> {
        private Path path;
        
        /**
         * Gets the current path in the tree.
         * @return 
         */
        public final Path getPath() {
            return path;
        }
        
        /**
         * Scans the dependency result, from its root node.
         * @param r the result
         * @param p the parameter
         * @return result of the scan
         */
        public final R scan(DependencyResult r, P p) {
            return scan(Path.of(r), p);
        }
        
        /**
         * Scans a dependency subtree at "path"
         * @param p parameter
         * @return scanning result
         */
        public R scan(Path path, P p) {
            this.path = path;
            try {
                return scan(path.getLeaf(), p);
            } finally {
                this.path = null;
            }
        }
        
        R scan(Dependency node, P p) {
            if (node == null) {
                return null;
            }
            Path prev = path;
            path = path.append(node);            
            try {
                return visitDependency(node, p);
            } finally {
                path = prev;
            }
        }

        /**
         * Scans a series of nodes, adding them to the current path.
         * @param nodes the nodes.
         * @param p parameter
         * @return scanning 
         */
        public R scan(Iterable<? extends Dependency> nodes, P p) {
            R r = null;
            if (nodes != null) {
                boolean first = true;
                for (Dependency node : nodes) {
                    r = (first ? scan(node, p) : scanAndReduce(node, p, r));
                    first = false;
                }
            }
            return r;
        }
  
        /**
         * Combines two results of dependency traversal. The default is to return r1.
         * @param r1 last result
         * @param r2 previous result
         * @return combined value
         */
        public R reduce(R r1, R r2) {
            return r1;
        }
        
        private R scanAndReduce(Dependency node, P p, R r) {
            return reduce(scan(node, p), r);
        }

        /**
         * The default visit operation will compute a value from the node's children.
         * @param d the dependency
         * @param p operation parameter
         * @return computed value
         */
        public R visitDependency(Dependency d, P p) {
            return scan(d.getChildren(), p);
        }
        
    }

    /**
     * Represents a path from the root of the dependency tree to a specific node throughout
     * the dependency graph. A {@link DependencyResult} can be associated with a Path. The Path
     * can enumerate its items from the root ({@link #listFromRoot()}, and serve a Stream of
     * Dependencies towards the root. Two paths are considered equal, if they contain the
     * same list of dependencies from the root (excluding the root).
     */
    public static final class Path implements Iterable<Dependency> {
        private final DependencyResult result;
        private final Path parent;
        private final Dependency leaf;

        private Path(DependencyResult result, Path parent, Dependency leaf) {
            this.result = result;
            this.parent = parent;
            
            this.leaf = leaf;
        }
        
        /**
         * Returns the current dependency.
         * @return the current Dependency instance.
         */
        public Dependency getLeaf() {
            return leaf;
        }
        
        /**
         * Creates a dependency list originating at the root, going down to this Path.
         * @return list of dependencies, starting at a root.
         */
        public List<Dependency> listFromRoot() {
            Deque<Dependency> ll = new LinkedList<>();
            for (Path p = this; p != null; p = p.getParent()) {
                ll.addFirst(p.leaf);
            }
            return new ArrayList<>(ll);
        }
        
        /**
         * @return a stream from the path's {@link #iterator()}.
         */
        public Stream<Dependency> stream() {
            return StreamSupport.stream(this.spliterator(), false);
        }
        
        /**
         * @return the associated {@link DependencyResult}
         */
        public DependencyResult getResult() {
            return result;
        }

        /**
         * Returns the parent Path. It will return {@code null}, if this path
         * identifies the root.
         * 
         * @return parent path, or {@code null}, if this is the root.
         */
        public Path getParent() {
            return parent;
        }
        
        /**
         * Creates a Path detached from a {@link DependencyResult}. This Path can be
         * seen as a 'relative' and can be appended to other Path.
         * 
         * @param deps dependencies on the path
         * @return Path instance.
         */
        public static final Path detached(Collection<Dependency> deps) {
            Path p = null;
            for (Dependency d : deps) {
                if (d == null) {
                    throw new NullPointerException();
                }
                if (p == null) {
                    p = new Path(null, null, d);
                } else {
                    p = p.append(d);
                }
            }
            return p;
        }
        
        /**
         * Creates a Path from the root. The path starts at the {@link DependencyResult}'s root, and
         * includes Dependncies from listed in `deps`, in their iteration order.
         * 
         * @param res result
         * @param deps dependencies, in the proper order
         * @return Path instance
         */
        public static final Path of(DependencyResult res, Collection<Dependency> deps) {
            Path p = new Path(res, null, res.getRoot());
            if (deps == null || deps.isEmpty()) {
                return p;
            }
            for (Dependency d : deps) {
                if (d == null) {
                    throw new NullPointerException();
                }
                if (p.getParent() == null && p.getLeaf().equals(d)) {
                    // skip repeated root
                    continue;
                }
                p = new Path(res, p, d);
            }
            return p;
        }
        
        /**
         * Creates a Path from the root. The path starts at the {@link DependencyResult}'s root, and
         * includes Dependncies from listed in `deps`, in their iteration order.
         * 
         * @param res result
         * @param deps dependencies, in the proper order
         * @return Path instance
         */
        public static final Path of(DependencyResult res, Dependency... deps) {
            if (deps == null || deps.length == 0) {
                return new Path(res, null, res.getRoot());
            }
            return of(res, Arrays.asList(deps));
        }

        /**
         * Creates a new Path, appending one or more dependencies.
         * @param deps
         * @return new path.
         */
        public Path append(Dependency... deps) {
            return append(Arrays.asList(deps));
        }
        
        /**
         * Creates a new Path, appending one or more dependencies.
         * @param deps
         * @return new path.
         */
        public Path append(List<Dependency> deps) {
            Iterator<Dependency> it = deps.iterator();
            if (deps == null || !it.hasNext()) {
                return this;
            }
            Path p = this;
            for (Dependency d : deps) {
                if (d == null) {
                    throw new NullPointerException();
                }
                p = new Path(getResult(), p, d);
            }
            return p;
        }
        
        /**
         * Creates an iterator that enumerates dependencies from the root to this
         * one. This instance is enumerated as the last one.
         */
        @Override
        public Iterator<Dependency> iterator() {
            return listFromRoot().iterator();
        }

        @Override
        public int hashCode() {
            int hash = 3;
            if (this.parent != null || this.result == null) {
                hash = 71 * hash + Objects.hashCode(this.parent);
                hash = 71 * hash + Objects.hashCode(this.leaf);
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Path other = (Path) obj;
            if (!Objects.equals(this.leaf, other.leaf)) {
                return false;
            }
            if (this.parent == null && other.parent == null) {
                if (this.result != null && other.result != null) {
                    // both roots of a different result, just compare as relative paths under the root
                    return true;
                }
                // for detached paths, compare even their roots.
            }
            return Objects.equals(this.parent, other.parent);
        }
    }
}
