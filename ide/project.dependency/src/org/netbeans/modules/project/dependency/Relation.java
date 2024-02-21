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

import java.util.Map;

/**
 * Represents a relation between dependencies.
 * 
 * @author sdedic
 */
public final class Relation {
    /**
     * The dependency duplicates other, in other part of the dependency tree. Typically
     * a dependency on some common 4th party library, that is shared between multiple 3rd party
     * libraries. There's not a guarantee that the "duplicates" will lead to a single
     * dependency or first appearance of the dependency in a tree. The implementation
     * will do the best effort to identify duplications, but is not require to identify
     * canonical dependencies.
     */
    public static final String DUPLICATE = "duplicates"; // NOI18N
    
    /**
     * For a resolved dependency, this relation points to the original dependency
     * in the project metadata, that may omit the version or specify a version range,
     * or minimal required version.
     */
    public static final String RESOLVED_FROM = "resolvedFrom"; // NOI18N
    
    /**
     * For a resolved relation, points to a Dependency that constrains the resolution.
     * So for a dependency that is {@link #RELATION_RESOLVED_FROM} some dependency range,
     * or unspecified-version dependency, this relation could point to the declaration
     * that specified the version, or constraints for it.
     */
    public static final String CONSTRAINED_BY = "constrainedBy"; // NOI18N
    
    /**
     * A dependency that defines or introduces this one. Typically a build plugin (if the
     * culprit can be identified).
     */
    public static final String DEFINED = "definedBy"; // NOI18N

    /**
     * Relation type
     */
    private final String type;
    
    /**
     * The opposite end of the relation
     */
    private final Dependency target;
    
    /**
     * Optional attributes for the relation.
     */
    private final Map<String, Object> attributes;

    Relation(String type, Dependency target, Map<String, Object> attributes) {
        this.type = type;
        this.target = target;
        this.attributes = Map.copyOf(attributes);
    }

    /**
     * @return relation type.
     */
    public String getType() {
        return type;
    }

    /**
     * Target dependency, the other side of the relation.
     * @return the target dependency
     */
    public Dependency getTarget() {
        return target;
    }

    /**
     * Creates a {@link Relation} instance.
     * @param type relation type
     * @param target target dependency
     * @return created instance
     */
    public Relation relatedDependency(String type, Dependency target) {
        return new Relation(type, target, Map.of());
    }
    
    /**
     * Creates a {@link Relation} instance.
     * @param type relation type
     * @param target target dependency
     * @param attributes optional attributes.
     * @return created instance
     */
    public Relation relatedDependency(String type, Dependency target, Map<String, Object> attributes) {
        return new Relation(type, target, attributes);
    }
}
