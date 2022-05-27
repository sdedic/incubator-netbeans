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
package org.netbeans.modules.cloud.common.explorer;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Jan Horvath
 */
public class CloudNode extends AbstractNode {

    private final CloudItem item;

    public CloudNode(CloudItem item) {
        super(Children.create(new CloudChildFactory(item), true), Lookups.fixed(item));
        setName(item.getName());
        this.item = item;
    }
    
    public CloudNode(CloudItem item, Children children) {
        super(children, Lookups.fixed(item));
        setName(item.getName());
        this.item = item;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> result = new ArrayList<>();
        
        String path = item.getKey().getPath();
        String provider = path.substring(0, path.indexOf("/"));
        
        result.addAll(Utilities.actionsForPath(
                String.format("Cloud/%s/Common/Actions", provider)));

        result.addAll(Utilities.actionsForPath(
                String.format("Cloud/%s/Actions",
                        item.getKey().getPath())));

        return result.toArray(new Action[0]); // NOI18N
    }

    @Override
    public Handle getHandle() {
        return super.getHandle(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    
}
