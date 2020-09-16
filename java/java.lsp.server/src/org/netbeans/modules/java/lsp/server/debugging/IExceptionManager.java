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

public interface IExceptionManager {
    /**
     * Returns the Exception associated with the thread.
     */
    ExceptionInfo getException(long threadId);

    /**
     * Removes the Exception associated with the thread. Returns the previous Exception mapping to the thread,
     * null if no mapping exists.
     */
    ExceptionInfo removeException(long threadId);

    /**
     * Associates an Exception with the thread. Returns the previous Exception mapping to the thread,
     * null if no mapping exists before.
     */
    ExceptionInfo setException(long threadId, ExceptionInfo exception);

    /**
     * Clear all Exceptions.
     */
    void removeAllExceptions();

    public static final class ExceptionInfo {

        private final Throwable exception;
        private final boolean caught;

        public ExceptionInfo(Throwable exception, boolean caught) {
            this.exception = exception;
            this.caught = caught;
        }

        public Throwable getException() {
            return exception;
        }

        public boolean isCaught() {
            return caught;
        }

        
    }
}
