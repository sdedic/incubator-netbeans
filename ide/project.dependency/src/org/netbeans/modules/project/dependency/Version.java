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

import java.util.Collections;
import java.util.List;


/**
 * Represents a parsed version of the dependency or an artifact. The version may be 
 * <ul>
 * <li>a single specific version
 * <li>a range of versions, with lower and upper bound
 * <li>a constraint for some version or version range.
 * </ul>
 * 
 * @author sdedic
 */
public abstract class Version {
    Version() {}
    
    /**
     * True, if the version is a concrete version number. False, if the version is a constraint,
     * range specification or other buildsystem-dependent value.
     * @return true for specific versions, false for complex specifications.
     */
    public boolean isSpecific() {
        return this instanceof Single;
    }
    
    /**
     * Represents a single version.
     * @author sdedic
     */
    public final static class Single extends Version {
        private final String spec;
        private final List<String> parts;

        Single(String spec, List<String> parts) {
            this.spec = spec;
            this.parts = Collections.unmodifiableList(parts);
        }
        
        public String getVersion() {
            return spec;
        }
        
        public List<String> getParts() {
            return parts;
        }

        @Override
        public boolean isSpecific() {
            return true;
        }
    }
    
    /**
     * Represents a version range. 
     */
    public final static class Range extends Version {
        private final Single from;
        private final Single to;
        private final boolean fromInclusive;
        private final boolean toInclusive;

        public Range(Single from, Single to, boolean fromInclusive, boolean toInclusive) {
            this.from = from;
            this.to = to;
            this.fromInclusive = fromInclusive;
            this.toInclusive = toInclusive;
        }

        public Single getFrom() {
            return from;
        }

        public Single getTo() {
            return to;
        }

        public boolean isFromInclusive() {
            return fromInclusive;
        }

        public boolean isToInclusive() {
            return toInclusive;
        }

        @Override
        public boolean isSpecific() {
            return false;
        }
    }
    
    /**
     * Represents a version constraint, which does not specify artifact version itself,
     * but constrains versions of other dependencies.
     */
    public final static class Constraint extends Version {
        public static final String STRICT = "strict"; // NOI18N
        public static final String PREFERRED = "preferred"; // NOI18N
        
        private final String type;
        private final Version version;
        private final Version preferred;

        public Constraint(String type, Version version, Version preferred) {
            this.type = type;
            this.version = version;
            this.preferred = preferred;
        }

        @Override
        public boolean isSpecific() {
            return false;
        }

        public Version getPreferred() {
            return preferred;
        }

        /**
         * Returns constraint type. Can be STRICT or PREFERRED.
         * @return 
         */
        public String getType() {
            return type;
        }

        /**
         * Get version or version range.
         * @return constraint version or version range.
         */
        public Version getVersion() {
            return version;
        }
    }
}
