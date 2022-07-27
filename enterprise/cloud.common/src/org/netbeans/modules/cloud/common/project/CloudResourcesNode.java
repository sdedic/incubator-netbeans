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

import java.awt.Image;
import java.util.List;
import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.Project;
import org.netbeans.modules.cloud.common.explorer.CloudItem;
import org.netbeans.modules.cloud.common.explorer.NodeProvider;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Jan Horvath
 */
@NbBundle.Messages({
    "CloudResources=Cloud Resources"
})
public class CloudResourcesNode extends AbstractNode {
    private static final String ICON_KEY_UIMANAGER = "Tree.closedIcon"; // NOI18N
    private static final String OPENED_ICON_KEY_UIMANAGER = "Tree.openIcon"; // NOI18N
    private static final String ICON_KEY_UIMANAGER_NB = "Nb.Explorer.Folder.icon"; // NOI18N
    private static final String OPENED_ICON_KEY_UIMANAGER_NB = "Nb.Explorer.Folder.openedIcon"; // NOI18N

    private static final String LIBS_BADGE = "org/netbeans/modules/cloud/common/explorer/resources/cloudBadge.svg"; // NOI18N
    private static final String DEFAULT_FOLDER = "org/netbeans/modules/cloud/common/explorer/resources/defaultFolder.svg"; // NOI18N
    
    public CloudResourcesNode(Project project) {
        super(Children.create(new CloudResourcesChildFactory(project), true), Lookups.fixed(project));
        setName(Bundle.CloudResources());
        setDisplayName(Bundle.CloudResources());
        setIconBaseWithExtension(DEFAULT_FOLDER); //NOI18N
    }

    @Override
    public Image getIcon(int param) {
        Image retValue = ImageUtilities.mergeImages(getTreeFolderIcon(false),
                ImageUtilities.loadImage(LIBS_BADGE), //NOI18N
                8, 8);
        return retValue;
    }
    
    @Override
    public Image getOpenedIcon(int param) {
        Image retValue = ImageUtilities.mergeImages(getTreeFolderIcon(true),
                ImageUtilities.loadImage(LIBS_BADGE), //NOI18N
                8, 8);
        return retValue;
    }
    
     /**
     * Returns default folder icon as {@link java.awt.Image}. Never returns
     * <code>null</code>.
     *
     * @param opened wheter closed or opened icon should be returned.
     * 
     * copied from apisupport/project
     */
    public static Image getTreeFolderIcon(boolean opened) {
        Image base;
        Icon baseIcon = UIManager.getIcon(opened ? OPENED_ICON_KEY_UIMANAGER : ICON_KEY_UIMANAGER); // #70263
        if (baseIcon != null) {
            base = ImageUtilities.icon2Image(baseIcon);
        } else {
            base = (Image) UIManager.get(opened ? OPENED_ICON_KEY_UIMANAGER_NB : ICON_KEY_UIMANAGER_NB); // #70263
        }
        assert base != null;
        return base;
    }
    

    public static class CloudResourcesChildFactory extends ChildFactory<CloudItem> implements ChangeListener {

        private final CloudResourcesStorage storage;

        public CloudResourcesChildFactory(Project project) {
            storage = project.getLookup().lookup(CloudResourcesStorage.class);
            storage.addChangeListener(this);
        }

        @Override
        protected boolean createKeys(List<CloudItem> toPopulate) {
            
            toPopulate.addAll(storage.items());
            return true;
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            refresh(false);
        }

        @Override
        protected Node createNodeForKey(CloudItem key) {
            NodeProvider nodeProvider = Lookups.forPath(
                    String.format("Cloud/%s/Nodes", key.getKey().getPath())) //NOI18N
                    .lookup(NodeProvider.class);
            if (nodeProvider == null) {
                AbstractNode node = new AbstractNode(Children.LEAF, Lookups.fixed(key));
                node.setDisplayName(key.getName());
                return node;
            }
            return nodeProvider.apply(key);
        }
    }

}
