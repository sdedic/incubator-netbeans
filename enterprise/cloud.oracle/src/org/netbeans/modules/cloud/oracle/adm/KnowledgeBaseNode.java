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

import com.oracle.bmc.adm.ApplicationDependencyManagementClient;
import com.oracle.bmc.adm.model.KnowledgeBase;
import com.oracle.bmc.adm.model.KnowledgeBaseSummary;
import com.oracle.bmc.adm.requests.GetKnowledgeBaseRequest;
import com.oracle.bmc.adm.requests.ListKnowledgeBasesRequest;
import com.oracle.bmc.adm.responses.GetKnowledgeBaseResponse;
import com.oracle.bmc.adm.responses.ListKnowledgeBasesResponse;
import com.oracle.bmc.model.BmcException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.modules.cloud.common.explorer.ChildrenProvider;
import org.netbeans.modules.cloud.common.explorer.CloudItem;
import org.netbeans.modules.cloud.common.explorer.ItemLoader;
import org.netbeans.modules.cloud.common.explorer.NodeProvider;
import org.netbeans.modules.cloud.oracle.OCIManager;
import org.netbeans.modules.cloud.oracle.OCINode;
import org.netbeans.modules.cloud.oracle.compartment.CompartmentItem;
import org.netbeans.modules.cloud.oracle.items.OCID;
import org.openide.util.Exceptions;

/**
 *
 * @author Jan Horvath
 */
public class KnowledgeBaseNode extends OCINode { //implements PropertyChangeListener{

    private static final String ICON = "org/netbeans/modules/cloud/oracle/resources/knowledgeBase.png"; // NOI18N
    private final KnowledgeBaseItem base;
    
    public KnowledgeBaseNode(KnowledgeBaseItem item) {
        super(item);
        setIconBaseWithExtension(ICON);
        this.base = item;
//        setDisplayName(getDisplayName(item.getKnowledgeBaseSummary()));
        setShortDescription(getDescription(item));
//        DefaultKnowledgeBaseStorage.getInstance().addChangeListener(this);
        
    }

    @NodeProvider.Registration(path = "Oracle/KnowledgeBase")
    public static NodeProvider<KnowledgeBaseItem> createNode() {
        return KnowledgeBaseNode::new;
    }

    @ItemLoader.Registration(path = "Oracle/KnowledgeBase")
    public static class KnowledgeBaseLoader implements ItemLoader<OCID> {

        @Override
        public CloudItem loadItem(OCID key) {
            try ( ApplicationDependencyManagementClient client 
                    = new ApplicationDependencyManagementClient(OCIManager.getDefault().getConfigProvider())) {
                
                GetKnowledgeBaseRequest request = GetKnowledgeBaseRequest.builder()
                        .knowledgeBaseId(key.getValue())
                        .build();
                GetKnowledgeBaseResponse response = client.getKnowledgeBase(request);
                KnowledgeBase knowledgeBase = response.getKnowledgeBase();
                return new KnowledgeBaseItem(key, knowledgeBase.getCompartmentId(), knowledgeBase.getDisplayName(), knowledgeBase.getTimeUpdated());
            } catch(BmcException e) {
                Exceptions.printStackTrace(e);
            }
            return null;
        }

        @Override
        public OCID fromPersistentForm(String persistedKey) {
            return OCID.of(persistedKey, "Oracle/KnowledgeBase");
        }
        
    }
    
    @ChildrenProvider.Registration(parentPath = "Oracle/Compartment")
    public static ChildrenProvider<CompartmentItem, KnowledgeBaseItem> listKnowledgeBases() {
        return compartment -> {
            try ( ApplicationDependencyManagementClient client 
                    = new ApplicationDependencyManagementClient(OCIManager.getDefault().getConfigProvider())) {
                
                ListKnowledgeBasesRequest request = ListKnowledgeBasesRequest.builder()
                        .compartmentId(compartment.getKey().getValue()).lifecycleState(KnowledgeBase.LifecycleState.Active).build();
                ListKnowledgeBasesResponse response = client.listKnowledgeBases(request);
                List<KnowledgeBaseSummary> baseSummary = response.getKnowledgeBaseCollection().getItems();
                return baseSummary.stream().map(p -> new KnowledgeBaseItem(OCID.of(p.getId(), "Oracle/KnowledgeBase"), 
                        p.getCompartmentId(), p.getDisplayName(), p.getTimeUpdated())).collect(Collectors.toList());
            }
        };
    }
    
//    private static String getDisplayName(KnowledgeBaseSummary base) {
//        StringBuilder sb = new StringBuilder();
//        sb.append(base.getDisplayName());
//        if (base.getLifecycleState() == KnowledgeBase.LifecycleState.Deleted 
//                || base.getLifecycleState() == KnowledgeBase.LifecycleState.Deleting) {
//            // TODO change the text
//            sb.append(" (Deleted)");
//        }
//        String defaultBaseID = DefaultKnowledgeBaseStorage.getInstance().getDefaultKnowledgeBaseId();
//        if ( base.getId().equals(defaultBaseID)) {
//            sb.append(" (Default)");
//        }
//        return sb.toString();
//    }
    
    protected static String getDescription(KnowledgeBaseItem item) {
        String lifeState = "Active";
//        lifeState = lifeState.toLowerCase();
//        lifeState = StringUtils.capitalize(lifeState);
        StringBuilder sb = new StringBuilder();
        sb.append(lifeState);
        SimpleDateFormat df = new SimpleDateFormat (" HH:mm:ss dd.MM.yy");
        sb.append(", Last updated: ");
        sb.append(df.format(item.timeUpdated));
        return sb.toString();
    }

//    @Override
//    public void propertyChange(PropertyChangeEvent evt) {
//        String myId = base.getKey().getValue();
//        if (myId.equals(evt.getNewValue()) || myId.equals(evt.getOldValue())) {
//            setDisplayName(getDisplayName(this.base.getKnowledgeBaseSummary()));
//        }
//    }
}
