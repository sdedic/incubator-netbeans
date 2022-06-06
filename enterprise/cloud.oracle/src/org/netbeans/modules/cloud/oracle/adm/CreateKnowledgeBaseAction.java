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
import com.oracle.bmc.adm.model.CreateKnowledgeBaseDetails;
import com.oracle.bmc.adm.model.KnowledgeBase;
import com.oracle.bmc.adm.requests.CreateKnowledgeBaseRequest;
import com.oracle.bmc.adm.requests.GetKnowledgeBaseRequest;
import com.oracle.bmc.adm.responses.CreateKnowledgeBaseResponse;
import com.oracle.bmc.adm.responses.GetKnowledgeBaseResponse;
import com.oracle.bmc.model.BmcException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Optional;
import org.netbeans.api.lsp.Diagnostic;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.modules.cloud.oracle.OCIManager;
import org.netbeans.modules.cloud.oracle.actions.CreateAutonomousDBDialog;
import org.netbeans.modules.cloud.oracle.compartment.CompartmentItem;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Pair;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Petr Pisl
 */
@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.cloud.oracle.adm.CreateKnowledgeBaseAction"
)
@ActionRegistration(
        displayName = "#CTL_CreateKnowledgeBaseAction",
        asynchronous = true
)

@ActionReferences(value = {
    @ActionReference(path = "Cloud/Oracle/Compartment/Actions", position = 270)
})
@NbBundle.Messages({
    "CTL_CreateKnowledgeBaseAction=Create Knowledge Base",
    "MSG_KBCreated=Knowledge Base {0} was created.",
    "MSG_KBNotCreated=Knowledge Base {0} failed to create with code: {1}"

})
public class CreateKnowledgeBaseAction implements ActionListener {

    private final CompartmentItem context;

    public CreateKnowledgeBaseAction(CompartmentItem context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Optional<String> result = CreateKnowledgeBaseDialog.showDialog(context);
        result.ifPresent((p) -> {
            RequestProcessor.getDefault().execute(() -> {
                ProgressHandle progressHandle = ProgressHandle.createHandle(String.format(Bundle.MSG_AuditIsRunning(), result.get()));
                progressHandle.start();
                
                try (ApplicationDependencyManagementClient client
                        = new ApplicationDependencyManagementClient(OCIManager.getDefault().getConfigProvider())) {

                    CreateKnowledgeBaseDetails params = CreateKnowledgeBaseDetails.builder()
                            .compartmentId(context.getKey().getValue())
                            .displayName(result.get()).build();
                    CreateKnowledgeBaseRequest request = CreateKnowledgeBaseRequest.builder()
                            .createKnowledgeBaseDetails(params).build();
                    CreateKnowledgeBaseResponse response = client.createKnowledgeBase(request);
                    int resultCode = response.get__httpStatusCode__();
                    String message;
                    if (resultCode == 202) {
                        context.refresh();
                        message = Bundle.MSG_KBCreated(result.get());
                    } else {
                        message = Bundle.MSG_KBNotCreated(result.get(), resultCode);
                    }
                    
                    
                    DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(message));
                    
                } catch (BmcException e) {
                    Exceptions.printStackTrace(e);
                } finally {
                    progressHandle.finish();
                }

            });
        });
    }

}
