/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.netbeans.modules.nbcode.integration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.java.lsp.server.LspServerUtils;
import org.netbeans.modules.java.lsp.server.Utils;
import org.netbeans.modules.java.lsp.server.protocol.NbCodeLanguageClient;
import org.netbeans.spi.lsp.ApplyEditsImplementation;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author sdedic
 */
@ServiceProvider(service = ApplyEditsImplementation.class, position = 10000)
public class LspApplyEditsImplementation implements ApplyEditsImplementation{

    @Override
    public CompletableFuture<List<String>> applyChanges(List<WorkspaceEdit> edits, boolean saveResources) {
        NbCodeLanguageClient client = LspServerUtils.requireLspClient(Lookup.getDefault());
        
        ClientCapabilities clientCaps = client.getNbCodeCapabilities().getClientCapabilities();
        if (clientCaps.getWorkspace() != null && clientCaps.getWorkspace().getWorkspaceEdit() != null) {
            WorkspaceEditCapabilities wecaps = clientCaps.getWorkspace().getWorkspaceEdit();
            CompletableFuture<ApplyWorkspaceEditResponse> next = CompletableFuture.completedFuture(null);
            for (WorkspaceEdit e : edits) {
                org.eclipse.lsp4j.WorkspaceEdit lspEdit = Utils.workspaceEditFromApi(e, null, client);
                ApplyWorkspaceEditParams params = new ApplyWorkspaceEditParams(lspEdit);
                next = next.thenCompose((r) -> {
                   if (r != null) {
                   } 
                   return client.applyEdit(params);
                });
            }
        } else {
            return null;
        }
    }
}
