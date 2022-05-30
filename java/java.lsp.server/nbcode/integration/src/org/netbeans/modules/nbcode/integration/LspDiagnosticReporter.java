/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.netbeans.modules.nbcode.integration;

import org.netbeans.modules.java.lsp.server.ui.AbstractDiagnosticReporter;
import org.openide.util.lookup.ServiceProvider;
import org.netbeans.spi.lsp.DiagnosticReporter;
/**
 *
 * @author sdedic
 */
@ServiceProvider(service = DiagnosticReporter.class)
public class LspDiagnosticReporter extends AbstractDiagnosticReporter {
    
}
