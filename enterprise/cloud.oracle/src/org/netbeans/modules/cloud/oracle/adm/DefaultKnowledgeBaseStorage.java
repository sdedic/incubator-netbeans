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
package org.netbeans.modules.cloud.oracle.adm;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.openide.util.NbPreferences;

/**
 *
 * @author Petr Pisl
 */
public class DefaultKnowledgeBaseStorage implements PreferenceChangeListener {
    private static final String KEY_KNOWLEDGEBASEID = "default_knowledge_base";
    
    private static DefaultKnowledgeBaseStorage INSTANCE;
    
    private final PropertyChangeSupport pcs;
    private String value;

    private DefaultKnowledgeBaseStorage() {
        this.pcs = new PropertyChangeSupport(this);
        NbPreferences.root().addPreferenceChangeListener(this);
        value = NbPreferences.root().get(KEY_KNOWLEDGEBASEID, null);
    }
    
    public static DefaultKnowledgeBaseStorage getInstance() {
        if (INSTANCE == null) {
            synchronized(DefaultKnowledgeBaseStorage.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DefaultKnowledgeBaseStorage();
                }
            }
        }
        return INSTANCE;
    }
    
    public void setAsDefault(String knowledgeBaseId) {
        NbPreferences.root().put(KEY_KNOWLEDGEBASEID, knowledgeBaseId);
    }
    
    public String getDefaultKnowledgeBaseId() {
        return NbPreferences.root().get(KEY_KNOWLEDGEBASEID, null);
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
        if (evt.getKey().equals(KEY_KNOWLEDGEBASEID)) {
            pcs.firePropertyChange(KEY_KNOWLEDGEBASEID, value, evt.getNewValue());
            value = evt.getNewValue();
        }
    }

    public void addChangeListener(PropertyChangeListener pcl) {
        pcs.addPropertyChangeListener(pcl);
    }
    
    public void removeChangeListener(PropertyChangeListener pcl) {
        pcs.removePropertyChangeListener(pcl);
    }
}
