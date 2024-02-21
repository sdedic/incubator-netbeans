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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
    /**
     * A special value indicating the children of the dependency are not resolved yet.
     */
    static final List<Dependency> UNRESOLVED = new ArrayList<>();
    
    private final ArtifactSpec  artifact;
    private final ProjectSpec   project;
    private volatile List<Dependency> children;
    private Supplier<List<Dependency>> supplier;
    private final Scope scope;
    private Dependency parent;
    private final Dependency    original;
    final Object data;
    
    Dependency(ProjectSpec project, ArtifactSpec artifact, Supplier<List<Dependency>> childSupplier, Scope scope, Dependency original, Object data) {
        this.project = project;
        this.artifact = artifact;
        this.supplier = childSupplier;
        this.scope = scope;
        this.original = original;
        this.data = data;
    }

    Dependency(ProjectSpec project, ArtifactSpec artifact, List<Dependency> children, Scope scope, Dependency original, Object data) {
        this.project = project;
        this.artifact = artifact;
        this.children = children;
        this.scope = scope;
        this.original = original;
        this.data = data;
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
    
    boolean isResolved() {
        return this.children != null;
    }
    
    public boolean isDuplicate() {
        return original != null;
    }
    
    public Dependency getOriginal() {
        return original;
    }

    public List<Dependency> getChildren() {
        List<Dependency> c = this.children;
        // this is just weakly synchronized, supplier may compute the children several times
        if (c != null) {
            return c;
        }
        Supplier<List<Dependency>> sup;
        synchronized (this) {
            sup = this.supplier;
        }
        if (sup != null) {
            c = sup.get();
        }
        if (c == null) {
            // TODO: log warning
            c = Collections.emptyList();
        }
        synchronized (this) {
            if (this.children == null) {
                this.children = c;
                this.supplier = null;
            }
        }
        return c;
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
        return getArtifact() + "[" + scope + "]";
    }
    
    /**
     * Returns parent Dependency that injected this one in the project. Returns
     * {@code null}, if this dependency is directly specified or configured for the
     * project itself.
     * @return parent dependency or {@code null}.
     */
    @CheckForNull
    public Dependency getParent() {
        return parent;
    }
    
    private static Dependency assignParent(Dependency d) {
        d.getChildren().forEach(c -> c.parent = d);
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
        return assignParent(new Dependency(null, artifact, children, scope, null, data));
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
        return assignParent(new Dependency(project, artifact, children, scope, null, data));
    }
    
    public static Dependency create(ProjectSpec project, ArtifactSpec artifact, Scope scope, Supplier<List<Dependency>> children, Object data) {
        return assignParent(new Dependency(project, artifact, children, scope, null, data));
    }
    
    public static Dependency create(ProjectSpec project, ArtifactSpec artifact, Scope scope, Dependency original, Object data) {
        return assignParent(new Dependency(project, artifact, (List)null, scope, original, data));
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
    
    public static final class Path implements Iterable<Dependency> {
        private final DependencyResult result;
        private final Path parent;
        private final Dependency leaf;

        private Path(DependencyResult result, Path parent, Dependency leaf) {
            this.result = result;
            this.parent = parent;
            this.leaf = leaf;
        }
        
        public DependencyResult getResult() {
            return result;
        }
        
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
        
        public static final Path of(DependencyResult res, Dependency... deps) {
            Path p = new Path(res, null, res.getRoot());
            if (deps == null || deps.length == 0) {
                return p;
            }
            return p.next(deps);
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
