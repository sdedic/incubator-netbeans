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
'use strict';

import { commands, window, workspace, ExtensionContext, ProgressLocation } from 'vscode';

import {
	LanguageClient,
	LanguageClientOptions,
	ServerOptions
} from 'vscode-languageclient';

import * as path from 'path';
import { execSync, spawn, ChildProcess } from 'child_process';
import { resolve } from 'path';
import { rejects } from 'assert';
import * as vscode from 'vscode';

let client: LanguageClient;
let nbProcess : ChildProcess | null = null;

export function activate(context: ExtensionContext) {
    //verify acceptable JDK is available/set:
    let specifiedJDK = workspace.getConfiguration('netbeans').get('jdkhome');

    let serverPath = path.resolve(context.extensionPath, "nb-java-lsp-server", "bin", "nb-java-lsp-server");

    let serverOptions: ServerOptions;
    let ideArgs: string[] = [];
    if (specifiedJDK) {
        ideArgs = ['--jdkhome', specifiedJDK as string];
    }
    let serverArgs: string[] = new Array<string>(...ideArgs);
    serverArgs.push("--start-java-language-server");

    serverOptions = {
        command: serverPath,
        args: serverArgs,
        options: { cwd: workspace.rootPath },

    }

    // give the process some reasonable command
    ideArgs.push("--modules");
    ideArgs.push("--list");

    let log = vscode.window.createOutputChannel("Java Language Server");
    log.show(true);
    log.appendLine("Launching Java Language Server");
    vscode.window.showInformationMessage("Launching Java Language Server");

    let ideRunning = new Promise((resolve, reject) => {
        let collectedText : string | null = '';
        function logAndWaitForEnabled(text: string) {
            log.append(text);
            if (collectedText == null) {
                return;
            }
            collectedText += text;
            if (collectedText.match(/org.netbeans.modules.java.lsp.server.*Enabled/)) {
                resolve(text);
                collectedText = null;
            }
        }

        let p = spawn(serverPath, ideArgs, {
            stdio : ["ignore", "pipe", "pipe"]
        });
        p.stdout.on('data', function(d: any) {
            logAndWaitForEnabled(d.toString());
        });
        p.stderr.on('data', function(d: any) {
            logAndWaitForEnabled(d.toString());
        });
        nbProcess = p;
        nbProcess.on('close', function(code: number) {
            if (code != 0) {
                vscode.window.showWarningMessage("Java Language Server exited with " + code);
            }
            log.appendLine("");
            if (collectedText != null) {
                reject("Exit code " + code);
            } else {
                log.appendLine("Exit code " + code);
            }
            nbProcess = null;
        });
    });

    ideRunning.then((value) => {
        // Options to control the language client
        let clientOptions: LanguageClientOptions = {
            // Register the server for java documents
            documentSelector: ['java'],
            synchronize: {
                configurationSection: 'java',
                fileEvents: [
                    workspace.createFileSystemWatcher('**/*.java')
                ]
            },
            outputChannelName: 'Java',
            revealOutputChannelOn: 4 // never
        }

        // Create the language client and start the client.
        client = new LanguageClient(
                'java',
                'NetBeans Java',
                serverOptions,
                clientOptions
        );

        // Start the client. This will also launch the server
        client.start();
        client.onReady().then((value) => {
            commands.executeCommand('setContext', 'nbJavaLSReady', true);
        });

        //register debugger:
        let configProvider = new NetBeansConfigurationProvider();
        context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('java', configProvider));

        let debugDescriptionFactory = new NetBeansDebugAdapterDescriptionFactory();
        context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory('java', debugDescriptionFactory));
        window.showErrorMessage('NB debug - all setup.');

        // register commands
        context.subscriptions.push(commands.registerCommand('java.workspace.compile', () => {
            return window.withProgress({ location: ProgressLocation.Window }, p => {
                return new Promise(async (resolve, reject) => {
                    const commands = await vscode.commands.getCommands();
                    if (commands.includes('java.build.workspace')) {
                        p.report({ message: 'Compiling workspace...' });
                        const start = new Date().getTime();
                        const res = await vscode.commands.executeCommand('java.build.workspace');
                        const elapsed = new Date().getTime() - start;
                        const humanVisibleDelay = elapsed < 1000 ? 1000 : 0;
                        setTimeout(() => { // set a timeout so user would still see the message when build time is short
                            if (res) {
                                resolve();
                            } else {
                                reject();
                            }
                        }, humanVisibleDelay);
                    } else {
                        reject();
                    }
                });
            });
        }));

    }).catch((reason) => {
        log.append(reason);
        window.showErrorMessage('Error initializing ' + reason);
    });

}

export function deactivate(): Thenable<void> {
    if (nbProcess != null) {
        nbProcess.kill();
    }
	if (!client) {
		return Promise.resolve();
	}
	return client.stop();
}

class NetBeansDebugAdapterDescriptionFactory implements vscode.DebugAdapterDescriptorFactory {
    createDebugAdapterDescriptor(session: vscode.DebugSession, executable: vscode.DebugAdapterExecutable | undefined): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
        window.showErrorMessage('NB debug - debug adapter server.');
        return new vscode.DebugAdapterServer(10001);
    }

}


class NetBeansConfigurationProvider implements vscode.DebugConfigurationProvider {

    resolveDebugConfiguration(folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
        window.showErrorMessage('NB debug - resolveDebugConfiguration.');
        config.mainClass = config.program;
        config.classPaths = ['any'];
        config.console = 'internalConsole';

        return config;
    }
}
