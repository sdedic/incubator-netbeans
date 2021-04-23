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

package org.netbeans;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;

/** More special module for representing fixed OSGi bundles, usually on the classpath.
 * Usual reload/enable semantics do not apply here.
 * 
 * @author sdedic
 */
final class NetigsoFixedModule extends Module {
    private final File jar;
    private final Manifest manifest;
    private InvalidException problem;

    public NetigsoFixedModule(Manifest mani, File jar, ModuleManager mgr, Events ev, Object history, ClassLoader ldr, boolean autoload, boolean eager) throws InvalidException {
        super(mgr, ev, history, ldr, autoload, eager);
        this.jar = jar;
        this.manifest = mani;
    }

    @Override
    ModuleData createData(ObjectInput in, Manifest mf) throws IOException {
        if (in != null) {
            return new ModuleData(in);
        } else {
            return ModuleData.forNetigsoModule(mf, this);
        }
    }

    @Override
    boolean isNetigsoImpl() {
        return true;
    }

    @Override
    protected void parseManifest() throws InvalidException {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getJarFile() {
        return jar;
    }

    @Override
    public List<File> getAllJars() {
        return Collections.singletonList(jar);
    }

    @Override
    public void setReloadable(boolean r) {
        throw new IllegalStateException();
    }

    @Override
    public void reload() throws IOException {
        throw new IOException("Fixed module cannot be reloaded!"); // NOI18N
    }

    @Override
    protected void classLoaderUp(Set<Module> parents) throws IOException {
    }

    @Override
    protected void classLoaderDown() {
    }

    @Override
    public Set<Object> getProblems() {
        InvalidException ie = problem;
        return ie == null ? Collections.emptySet() :
            Collections.<Object>singleton(ie);
    }
    
    final void setProblem(InvalidException ie) {
        problem = ie;
    }

    @Override
    protected void cleanup() {
    }

    @Override
    protected void destroy() {
    }

    @Override
    public boolean isFixed() {
        return true;
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    public Object getLocalizedAttribute(String attr) {
        // TBD;
        return null;
    }

    @Override
    public String toString() {
        return "Fixigso: " + jar;
    }
}
