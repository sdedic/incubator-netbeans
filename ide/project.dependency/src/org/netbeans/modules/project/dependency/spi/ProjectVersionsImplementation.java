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
package org.netbeans.modules.project.dependency.spi;

import java.util.Comparator;
import java.util.function.BiPredicate;
import org.netbeans.modules.project.dependency.Dependency;

/**
 * Allows to delegate some versioning semantics to the project provider.
 * 
 * @author sdedic
 */
public interface ProjectVersionsImplementation {
    /**
     * Returns a Comparator that can compare resolved versions. Versions are compared
     * according to project semantics.
     * 
     * @return see {@link Comparator} specification.
     */
    public Comparator<String> getVersionComparator();
    
    /**
     * Determines if the Dependency has a concrete version. Return false,
     * if the dependency specifies a version range or a constraint. 
     * 
     * @param d existing project Dependency
     * @return true, if the dependency version is a concrete version.
     */
    public boolean hasSpecificVersion(Dependency d);

    /**
     * Checks if how a specific version relates to the Dependency's version.
     * The method determines, if the dependency is newer that the specified version,
     * or older than the version. Comparing a version range, or a constraint to a 
     * dependency is not supported.
     * If the dependency contains a concrete (or resolved)
     * version, a version comparison is made:
     * <ul>
     * <li>If the dependency is newer, -1 is returned
     * <li>If the dependency is older than the version, 1 is returned
     * <li>If the dependency is exactly the version, returns 0.
     * </ul>
     * If the dependency specifies a range, the method compares upper and lower bounds
     * of the range with the version:
     * <ul>
     * <li>if the version is, for any reason, not acceptable for the dependency, returns Integer.MAX_VALUE.
     * <li>If the dependency's upper version bound is less than the version, returns 1
     * <li>If the dependency's lower version bound is greater than the version, returns -1
     * <li>If the dependency's lower version bound is equal to the version, returns 0
     * <li>Otherwise, returns Integer.MAX_VALUE.
     * </ul>
     * This strange rules allow the client to determine:
     * <ul>
     * <li>if the dependency is newer than client's minimal version requirement, the result will be negative.
     * <li>if the dependency is older than the client's requirement, the result will be positive.
     * <li>if the version requirement is matched by the dependency, the result will be zero
     * <li>if the relationship cannot be determined, Integer.MAX_VALUE is returned.
     * <p>
     * If the project system does not support version ranges, it should just compare dependency
     * version against the passed one.
     * 
     * @param d existing project Dependency
     * @param version a specific version
     * @return whether the dependency is older, newer or comparable to the passed version.
     */
    public int checkVersion(Dependency d, String version);
    
    /**
     * Returns a predicate that decides if a version is acceptable for 
     * a given dependency. A version String is accepted, if it matches the single version of
     * the Dependency, or falls into its specified range.
     * @return true, if the version satisfies Dependency's requirements.
     */
    public BiPredicate<Dependency, String> getVersionAcceptor();
}
