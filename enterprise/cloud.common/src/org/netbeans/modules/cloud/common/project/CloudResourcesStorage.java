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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.cloud.common.explorer.CloudItem;
import org.netbeans.modules.cloud.common.explorer.CloudItemKey;
import org.netbeans.modules.cloud.common.explorer.ItemLoaderFactory;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.util.ChangeSupport;
import org.openide.util.RequestProcessor;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Jan Horvath
 */
@ProjectServiceProvider(service = CloudResourcesStorage.class, projectType = {"org-netbeans-modules-maven", "org-netbeans-modules-gradle"})
public class CloudResourcesStorage {

    private static final String CLOUD_RESOURCES_NS = "http://www.netbeans.org/ns/cloud-common/1"; // NOI18N
    private static final String CLOUD_ELEMENT = "cloud-resources"; // NOI18N
    private static final String ITEM_ELEMENT = "item"; // NOI18N
    private static final String PATH_ELEMENT = "path"; // NOI18N
    private static final String KEY_ELEMENT = "key"; // NOI18N

    private final Project project;
    private final ChangeSupport cs = new ChangeSupport(this);
    private final Set<CloudItem> items = new HashSet<> ();
    private final RequestProcessor RP = new RequestProcessor("CloudResources", 1);
    

    public CloudResourcesStorage(Project project) {
        this.project = project;
        RP.post(() -> {
            List<CloudItemKey> keys = keysForProject();
            for (CloudItemKey key : keys) {
                CloudItem item = ItemLoaderFactory.getDefault().loadItem(key);
                if (item != null && items.add(item)) {
                    cs.fireChange();
                }
            }
        });
    }
    
    public synchronized void addToProject(CloudItem item) {
        if (!items.add(item)) {
            return;
        }
        AuxiliaryConfiguration config = ProjectUtils.getAuxiliaryConfiguration(project);
        Element bindings = config.getConfigurationFragment(CLOUD_ELEMENT, CLOUD_RESOURCES_NS, true);

        if (bindings == null) {
            Document xml = XMLUtil.createDocument(CLOUD_ELEMENT, CLOUD_RESOURCES_NS, null, null);
            bindings = xml.createElementNS(CLOUD_RESOURCES_NS, CLOUD_ELEMENT);
        }
        Element itemElement = bindings.getOwnerDocument().createElement(ITEM_ELEMENT);

        Element pathElement = itemElement.getOwnerDocument().createElement(PATH_ELEMENT);
        pathElement.appendChild(itemElement.getOwnerDocument().createTextNode(item.getKey().getPath()));
        itemElement.appendChild(pathElement);

        Element keyElement = itemElement.getOwnerDocument().createElement(KEY_ELEMENT);
        keyElement.appendChild(itemElement.getOwnerDocument().createTextNode(item.getKey().toPersistentForm()));
        itemElement.appendChild(keyElement);

        bindings.appendChild(itemElement);
        config.putConfigurationFragment(bindings, true);
        cs.fireChange();
    }
    
    public synchronized Collection<CloudItem> items() {
        return items;
    }
    
    private List<CloudItemKey> keysForProject() {
        AuxiliaryConfiguration config = ProjectUtils.getAuxiliaryConfiguration(project);
        List<CloudItemKey> result = new ArrayList<>();
        Element bindings = config.getConfigurationFragment(CLOUD_ELEMENT, CLOUD_RESOURCES_NS, true);
        if (bindings != null) {
            NodeList list = bindings.getElementsByTagName(ITEM_ELEMENT);
            for (int i = 0; i < list.getLength(); i++) {
                NodeList nl = list.item(i).getChildNodes();
                String key = null;
                String path = null;
                for (int j = 0; j < nl.getLength(); j++) {
                    Node n = nl.item(j);
                    if (KEY_ELEMENT.equals(n.getNodeName())) {
                        key = n.getTextContent();
                    }
                    if (PATH_ELEMENT.equals(n.getNodeName())) {
                        path = n.getTextContent();
                    }
                }
                if (key != null && path != null) {
                    CloudItemKey ik = ItemLoaderFactory.getDefault().fromPersistent(path, key);
                    if (ik != null) {
                        result.add(ik);
                    }
                }
            }
        }
        return result;
    }
    
    public void addChangeListener(ChangeListener listener) {
        cs.addChangeListener(listener);
    }
    
    public void removeChangeListener(ChangeListener listener) {
        cs.removeChangeListener(listener);
    }

}
