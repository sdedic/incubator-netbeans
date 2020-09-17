/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.netbeans.modules.java.lsp.server.debugging.launch;

import java.util.concurrent.CompletableFuture;

import java.util.function.Consumer;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages.Response;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.LaunchArguments;
import org.openide.filesystems.FileObject;

public interface ILaunchDelegate {

    void postLaunch(LaunchArguments launchArguments, IDebugAdapterContext context);

    void preLaunch(LaunchArguments launchArguments, IDebugAdapterContext context);

    CompletableFuture<Response> launchInTerminal(LaunchArguments launchArguments, Response response, IDebugAdapterContext context);

    CompletableFuture<Void> nbLaunch(FileObject toRun, IDebugAdapterContext context, boolean debug, Consumer<NbProcessConsole.ConsoleMessage> consoleMessages);

}
