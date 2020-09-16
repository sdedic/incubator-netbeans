/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.netbeans.modules.java.lsp.server.debugging;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ExceptionManager implements IExceptionManager {
    private Map<Long, ExceptionInfo> exceptions = Collections.synchronizedMap(new HashMap<>());

    @Override
    public ExceptionInfo getException(long threadId) {
        return exceptions.get(threadId);
    }

    @Override
    public ExceptionInfo removeException(long threadId) {
        return exceptions.remove(threadId);
    }

    @Override
    public ExceptionInfo setException(long threadId, ExceptionInfo exception) {
        return exceptions.put(threadId, exception);
    }

    @Override
    public void removeAllExceptions() {
        exceptions.clear();
    }
}
