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
package org.netbeans.modules.cloud.common.project;

import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.nodes.Node;
import org.openide.util.ChangeSupport;

/**
 *
 * @author Jan Horvath
 */
@NodeFactory.Registration(projectType = {"org-netbeans-modules-maven", "org-netbeans-modules-gradle"}, position = 1000)
public class CloudResourcesNodeFactory implements NodeFactory {


    @Override
    public NodeList<?> createNodes(Project project) {
        return new CloudNodeList(project);
    }


    public class CloudNodeList implements NodeList<Void>, ChangeListener {
        private CloudResourcesStorage storage;

        private final ChangeSupport cs = new ChangeSupport(this);
        private final Project project;

        private CloudNodeList(Project project) {
            this.project = project;
            storage = project.getLookup().lookup(CloudResourcesStorage.class);
            storage.addChangeListener(this);
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            cs.fireChange();
        }
        
        @Override
        public void addChangeListener(ChangeListener list) {
            cs.addChangeListener(list);
        }

        @Override
        public void removeChangeListener(ChangeListener list) {
            cs.removeChangeListener(list);
        }

        @Override
        public void addNotify() {
        }

        @Override
        public void removeNotify() {
        }

        @Override
        public List<Void> keys() {
            if (storage.items().isEmpty()) {
                return Collections.<Void>emptyList();
            } else {
                return Collections.<Void>singletonList(null);
            }
        }

        @Override
        public Node node(Void key) {
            return new CloudResourcesNode(project);
        }

    }
    
}
