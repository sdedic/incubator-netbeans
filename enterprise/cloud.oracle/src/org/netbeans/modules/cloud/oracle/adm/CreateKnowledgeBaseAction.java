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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Optional;
import org.netbeans.modules.cloud.oracle.OCIManager;
import org.netbeans.modules.cloud.oracle.actions.CreateAutonomousDBDialog;
import org.netbeans.modules.cloud.oracle.compartment.CompartmentItem;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
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
    "MSG_KNCreated=Knowledge Base {0} was created.",
    "MSG_KBNotCreated=Knowledge Base {0} failed to create: {1}"

})
public class CreateKnowledgeBaseAction implements ActionListener {

    private final CompartmentItem context;

    public CreateKnowledgeBaseAction(CompartmentItem context) {
        this.context = context;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<String> result = CreateKnowledgeBaseDialog.showDialog(context);
        result.ifPresent((p) -> {
            RequestProcessor.getDefault().execute(() -> {
                System.out.println(" !!! vytvorit KB " + result.get());
//                Optional<String> message = OCIManager.getDefault().createAutonomousDatabase(context.getKey().getValue(), p.first(), p.second());
//                if (!message.isPresent()) {
//                    context.refresh();
//                    DialogDisplayer.getDefault().notifyLater(
//                            new NotifyDescriptor.Message(
//                                    org.netbeans.modules.cloud.oracle.actions.Bundle.MSG_DBCreated(p.first())));
//                } else {
//                    DialogDisplayer.getDefault().notifyLater(
//                            new NotifyDescriptor.Message(
//                                    org.netbeans.modules.cloud.oracle.actions.Bundle.MSG_DBNotCreated(p.first(), message.get())));
//                }
            });
        });
    }
    
}
