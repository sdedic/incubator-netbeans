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

import java.io.IOException;
import java.util.Collection;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.project.Project;
import org.openide.util.Lookup;

/**
 * Results of a dependency inspection. Contains dependency graph from the {@link #getRoot}.
 * The result may become invalid, as a result of project changes or dependency changes.
 * The state change will be reported by {@link ChangeListener}. If the client is interested
 * in an updated result, it must perform another dependency scan. Once invalid instance
 * will never turn back to valid.
 * <p>
 * The {@link #getLookup() lookup} can be used to search for project-specific services that
 * can provide further info on the artifacts or dependencies.
 * @author sdedic
 */
public interface DependencyResult extends Lookup.Provider {
    /**
     * @return the inspected project
     */
    public Project getProject();
    /**
     * The root of the dependency tree. Its artifact should represent the project itself.
     * @return project dependency root.
     */
    public Dependency getRoot();
    
    /**
     * Checks if the data is still valid
     * @return true, if the data is valid
     */
    public boolean isValid();
    
    /**
     * Returns artifacts that may be unavailable or erroneous.
     * @return problem artifacts
     */
    public Collection<ArtifactSpec> getProblemArtifacts();
    
    /**
     * Registers a Listener to be notified when validity changes.
     * @param l listener
     */
    public void addChangeListener(ChangeListener l);

    /**
     * Unregisters a previously registered Listener.
     * @param l listener
     */
    public void removeChangeListener(ChangeListener l);

    /**
     * Attempts to find location where this dependency is declared.
     * @param d
     * @return 
     */
    public @CheckForNull SourceLocation getDeclarationRange(@NonNull Dependency d) throws IOException;
    
    /**
     * Returns a location for the specific declaration part. Not all parts are converted by the API, but the most
     * important ones are. To locate the dependency source, the {@link DependencySpecPart#FULL}, {@link DependencySpecPart#ARTIFACT} should
     * be used, depending on workflow. If the version is an issue (i.e. old version), the {@Link DependencySpecPart#VERSION} can be used.
     * <p/>
     * The {@link DependencySpecPart#VARIANT} covers auxiliary artifact identification, whatever it is. If the dependency is declared with
     * some scope
     * <p/>
     * The method returns {@code null] if th<e specified part is not present in the source; for example version can be specified in a parent 
     * POM
     * 
     * @param d the dependency to query
     * @param part the part 
     * @return
     * @throws IOException 
     */
    public @CheckForNull SourceLocation getDeclarationRange(@NonNull Dependency d, @NullAllowed DependencySpecPart part) throws IOException;
    
    /**
     * Specifies a part of the dependency specification in the source.
     */
    public enum DependencySpecPart {
        /**
         * Full dependency specification.
         */
        FULL, 
        
        /**
         * Represents location of group specification.
         */
        GROUP, 

        /**
         * Represents location of artifact id specification.
         */
        ARTIFACT, 

        /**
         * Represents the complete group-artifact spec, usually an union of
         * {@link #GROUP} and {@link #ARTIFACT}
         */
        GROUP_AND_ARTIFACT,

        /**
         * Represents the variant specification like type or classifier.
         */
        VARIANT,
        
        /**
         * Represents the scope specification.
         */
        SCOPE,
        
        /**
         * The artifact version specification.
         */
        VERSION
    }
}
