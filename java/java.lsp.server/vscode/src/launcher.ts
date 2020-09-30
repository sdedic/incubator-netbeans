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

import { ExtensionContext } from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { spawn, ChildProcessByStdio } from 'child_process';
import * as vscode from 'vscode';
import { Readable } from 'stream';

export function launch(
    context: ExtensionContext,
    jdkHome: string | unknown,
    ...extraArgs : string[]
): ChildProcessByStdio<null, Readable, Readable> {
    function findClusters(): string[] {
        let clusters = [];
        for (let e of vscode.extensions.all) {
            const dir = path.join(e.extensionPath, 'nb-java-lsp-server');
            if (!fs.existsSync(dir)) {
                continue;
            }
            const exists = fs.readdirSync(dir);
            for (let clusterName of exists) {
                let clusterPath = path.join(dir, clusterName);
                let clusterModules = path.join(clusterPath, 'config', 'Modules');
                if (!fs.existsSync(clusterModules)) {
                    continue;
                }
                let perm = fs.statSync(clusterModules);
                if (perm.isDirectory()) {
                    clusters.push(clusterPath);
                }
            }
        }
        return clusters;
    }
    let clusters = findClusters();

    const nbexec = path.join(context.extensionPath, 'nb-java-lsp-server', 'platform', 'lib', 'nbexec');
    let nbexecPerm = fs.statSync(nbexec);
    if (!nbexecPerm.isFile()) {
        throw `Cannot execute ${nbexec}`;
    }

    const userDir = path.join(context.extensionPath, "nb-java-lsp-server-user-dir");
    fs.mkdirSync(userDir, {recursive: true});
    let userDirPerm = fs.statSync(userDir);
    if (!userDirPerm.isDirectory()) {
        throw `Cannot create ${userDir}`;
    }

    let clusterPath = clusters.join(path.delimiter);
    let ideArgs: string[] = [
        "--nosplash", "--nogui", "--branding", "vscapp",
        "-J-Djava.awt.headless=true",
        "-J-Dnetbeans.logger.console=true",

        "-J--add-opens=java.base/java.net=ALL-UNNAMED",
        "-J--add-opens=java.base/java.lang.ref=ALL-UNNAMED",
        "-J--add-opens=java.base/java.lang=ALL-UNNAMED",
        "-J--add-opens=java.base/java.security=ALL-UNNAMED",
        "-J--add-opens=java.base/java.util=ALL-UNNAMED",
        "-J--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        "-J--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED",
        "-J--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "-J--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "-J--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
        "-J--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED",
        "-J--add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED",
        "-J--add-modules=jdk.jshell",
        "-J--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "-J--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
        "-J--add-exports=java.desktop/com.sun.beans.editors=ALL-UNNAMED",
        "-J--add-exports=java.desktop/sun.swing=ALL-UNNAMED",
        "-J--add-exports=java.desktop/sun.awt.im=ALL-UNNAMED",
        "-J--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
        "-J--add-exports=java.management/sun.management=ALL-UNNAMED",
        "-J--add-exports=java.base/sun.reflect.annotation=ALL-UNNAMED",
        "-J-XX:+IgnoreUnrecognizedVMOptions",

        "--clusters", clusterPath, "--userdir", userDir
    ];
    if (jdkHome) {
        ideArgs.push('--jdkhome', jdkHome as string);
    }
    ideArgs.push(...extraArgs);

    let process: ChildProcessByStdio<null, Readable, Readable> = spawn(nbexec, ideArgs, {
        cwd : userDir,
        stdio : ["ignore", "pipe", "pipe"]
    });
    return process;
}
