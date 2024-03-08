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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.netbeans.api.annotations.common.CheckForNull;

/**
 * Represents a dependency of an artifact. The {@link #getChildren()} artifacts are
 * needed in a certain {@link #getScope()}; should be ignored in unrelated scopes.
 * The artifact that is subject of this dependency is the {@link #getArtifact()} or {@link #getProject()}.
 * Children are expressed using other {@link Dependency} instances.
 * <p>
 * A project does not need to produce a publishable (identifiable) artifact; in such cases, the
 * {@link #getArtifact} may return {@code null}.
 * <p>
 * Dependency does not have well-defined {@link #equals} and {@link #hashCode}, use
 * {@link #getArtifact()} or {@link #getProject()} as key in Maps.
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
    private Supplier<Dependency>  originalProvider;
    final Object data;

    Dependency(ProjectSpec project, ArtifactSpec artifact, Function<Dependency, List<Dependency>> childSupplier, Scope scope, Dependency original, Object data) {
        this.project = project;
        this.artifact = artifact;
        this.supplier = childSupplier;
        this.scope = scope;
        this.original = original;
        this.data = data;
        this.originalProvider = null;
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

    public Scope getScope() {
        return scope;
    }
    
    public Object getProjectData() {
        return data;
    }
    
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
     * Returns 
     * @return 
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
     * @param artifact
     * @param scope
     * @param children
     * @param data
     * @return 
     */
    public static Dependency create(ArtifactSpec artifact, Scope scope, List<Dependency> children, Object data) {
        return new Dependency(null, artifact, children, scope, null, data);
    }
    
    /**
     * Creates a dependency on a project. The project identifies 
     * @param project
     * @param artifact
     * @param scope
     * @param children
     * @param data
     * @return 
     */
    public static Dependency create(ProjectSpec project, ArtifactSpec artifact, Scope scope, List<Dependency> children, Object data) {
        return new Dependency(project, artifact, children, scope, null, data);
    }
    
    /**
     * Creates a builder for the dependency for a artifact-based dependency.
     * @param spec artifact specification
     * @param scope dependency scope
     * @param data
     * @return 
     */
    public static Builder builder(ArtifactSpec spec, Scope scope, Object data) {
        return new Builder(spec, scope, data);
    }
    
    /**
     * Creates a builder for the dependency for a artifact-based dependency.
     * @param spec artifact specification
     * @param scope dependency scope
     * @param data
     * @return 
     */
    public static Builder builder(ProjectSpec spec, Scope scope, Object data) {
        return new Builder(spec, scope, data);
    }

    /**
     * Builder that creates Dependency instances.
     */
    public static final class Builder {
        private ProjectSpec project;
        private ArtifactSpec artifact;
        private Scope scope;
        private List<Dependency> children;
        private Function<Dependency, List<Dependency>> childProvider;
        private Object data;
        private Dependency original;
        private Supplier<Dependency> originalProvider;

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
         * Adds lazy-computed children to the dependency. The provider function will
         * be called once to resolve children. Can not be combined with addChildren methods.
         * @param provider resolver
         * @return builder instance.
         */
        public Builder children(Function<Dependency, List<Dependency>> provider) {
            this.children = null;
            this.childProvider = provider;
            return this;
        }
        
        /**
         * Adds children to the dependency. 
         * @param children children to add.
         * @return builder instance.
         */
        public Builder addChildren(Collection<Dependency> children) {
            this.childProvider = null;
            if (this.children == null) {
                this.children = new ArrayList<>();
            }
            if (children != null) {
                this.children.addAll(children);
            }
            return this;
        }
        
        /**
         * Adds children to the dependency.
         * 
         * @param children children
         * @return builder instance.
         */
        public Builder addChildren(Dependency... children) {
            this.childProvider = null;
            if (children == null) {
                return this;
            }
            return addChildren(Arrays.asList(children));
        }
        
        /**
         * Links the dependency to an original.
         * @param orig original dependency
         * @return builder instance
         */
        public Builder linkOriginal(Dependency orig) {
            this.original = orig;
            return this;
        }
        
        /**
         * Links the dependency to an lazy-resolved original.
         * @param orig original
         * @return builder instance.
         */
        public Builder linkOriginal(Supplier<Dependency> orig) {
            this.originalProvider = orig;
            return this;
        }
        
        /**
         * Creates a dependency instance from the accumulated data.
         * @return initialized dependency
         */
        public Dependency create() {
            if (this.childProvider != null) {
                return new Dependency(project, artifact, childProvider, scope, original, data);
            } else {
                return new Dependency(project, artifact, children, scope, original, data);
            }
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
         * @return result of the traceresal
         */
        public final R scan(DependencyResult r, P p) {
            return scan(Path.of(r), p);
        }
        
        /**
         * Scans a dependency subtree at "path'
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
            path = path.next(node);            
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
     * the dependency graph. A {@link DependencyResult} is always associated with a Path.
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
        
        public Dependency getLeaf() {
            return leaf;
        }
        
        /**
         * 
         * @return the associated {@link DependencyResult}
         */
        public DependencyResult getResult() {
            return result;
        }
        
        public Path getParent() {
            return parent;
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
            Path p = new Path(res, null, res.getRoot());
            if (deps == null || deps.length == 0) {
                return p;
            }
            return of(res, Arrays.asList(deps));
        }

        /**
         * Creates a new Path, appending one or more dependencies.
         * @param deps
         * @return new path.
         */
        public final Path next(Dependency... deps) {
            if (deps == null || deps.length == 0) {
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
        
        @Override
        public Iterator<Dependency> iterator() {
            return new Iterator<Dependency>() {
                private Path next = Path.this;
                        
                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public Dependency next() {
                    if (next == null) {
                        throw new NoSuchElementException();
                    }
                    Dependency n = next.leaf;
                    next = next.parent;
                    return n;
                }
            };

        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 71 * hash + Objects.hashCode(this.parent);
            hash = 71 * hash + Objects.hashCode(this.leaf);
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
            if (!Objects.equals(this.parent, other.parent)) {
                return false;
            }
            return Objects.equals(this.leaf, other.leaf);
        }
    }
}
