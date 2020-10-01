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
package org.netbeans.modules.javascript2.editor.spi;

import java.util.Collection;
import java.util.Collections;
import org.netbeans.modules.csl.spi.ParserResult;

/**
 *
 * @author sdedic
 */
public final class ProposalRequest {
    private final CompletionContext context;
    private final ParserResult  info;
    private final int anchor;
    private final Collection<String> selectors;
    private final String prefix;

    public ProposalRequest(CompletionContext context, ParserResult info, int anchor, Collection<String> selectors, String prefix) {
        this.context = context;
        this.info = info;
        this.anchor = anchor;
        this.selectors = selectors;
        this.prefix = prefix;
    }

    public CompletionContext getContext() {
        return context;
    }

    public ParserResult getInfo() {
        return info;
    }

    public int getAnchor() {
        return anchor;
    }

    public Collection<String> getSelectors() {
        return selectors == null ? Collections.emptyList() : selectors;
    }

    public String getPrefix() {
        return prefix;
    }

}
