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
package org.netbeans.extensible.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.openide.util.Lookup;

/**
 * Describes a feature composition. The composition consists of a base selector
 * and zero or more (ordered) components. The interpretation is not defined, each
 * {@link PluginDataProvider} may turn 
 * 
 * @author sdedic
 */
public final class Composition {
    private final Lookup baseContext;
    private final String base;
    private final List<String> components;

    public Composition(Lookup baseContext, String base, List<String> components) {
        this.baseContext = baseContext;
        this.base = base;
        this.components = Collections.unmodifiableList(components);
    }

    public String getBase() {
        return base;
    }

    public Lookup getBaseContext() {
        return baseContext;
    }

    public List<String> getComponents() {
        return components;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 17 * hash + Objects.hashCode(this.base);
        hash = 17 * hash + Objects.hashCode(this.components);
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
        final Composition other = (Composition) obj;
        if (!Objects.equals(this.base, other.base)) {
            return false;
        }
        if (!Objects.equals(this.components, other.components)) {
            return false;
        }
        return true;
    }
}
