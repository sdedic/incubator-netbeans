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

import java.util.Collection;
import javax.swing.event.ChangeListener;
import org.netbeans.modules.project.dependency.Scope;

/**
 * Supplies information about scopes recognized by the project. The provider may
 * inform about changes in its scope structure by firing an event to {@link ChangeListeners}.
 * @author sdedic
 */
public interface ProjectDependencyScopes {
    /**
     * Returns the set of supported scopes. The returned set should include 
     * those abstract scopes supported by the project. Note that if additional
     * plugins are added to the build system, the set of scopes may change.
     * 
     * @return set of supported scopes.
     */
    public Collection<? extends Scope>  scopes();
    
    /**
     * Returns the scopes that this one implies. Note that the the {@code implies}
     * relation need not to be transitive (i.e. some scopes may be filtered from
     * further inheritance).
     * 
     * @param s the scope
     * @param direct if true, just direct implications are returned.
     * @return Collection of scopes that satisfy the query, never {@code null}.
     */
    public Collection<? extends Scope> implies(Scope s, boolean direct);
    
    /**
     * Registers a Listener that is informed if this provider changes the set
     * of scopes or dependencies. Most providers do not support changes, so they
     * can use the default method implementation that does nothing.
     * 
     * @param l the listener.
     */
    public default void addChangeListener(ChangeListener l) {}
    
    /**
     * Removes a change listener. The default implementation does nothing.
     * @param l 
     */
    public default void removeChangeListener(ChangeListener l) {}
}
