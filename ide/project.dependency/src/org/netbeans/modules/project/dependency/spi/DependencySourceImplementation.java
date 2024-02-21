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

import org.netbeans.modules.project.dependency.Dependency;
import org.netbeans.modules.project.dependency.DependencyResult;
import org.netbeans.modules.project.dependency.SourceLocation;

/**
 * Locates a point in the source that corresponds to the specified dependency path.
 * @author sdedic
 */
public interface DependencySourceImplementation {
    /**
     * Attempts to locate source for the dependency.
     * @param result dependency result context
     * @param path path to the dependency
     * @param part part of the dependency to return source for.
     * @return SourceLocation instance or {@code null}, if the source is not available.
     */
    SourceLocation  findDependencyLocation(DependencyResult result, Dependency.Path path, String part);
}
