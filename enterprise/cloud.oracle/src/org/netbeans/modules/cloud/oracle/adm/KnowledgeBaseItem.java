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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import org.netbeans.modules.cloud.oracle.items.OCID;
import org.netbeans.modules.cloud.oracle.items.OCIItem;
import org.openide.util.Exceptions;

/**
 *
 * @author Jan Horvath
 */
public class KnowledgeBaseItem extends OCIItem implements URLProvider{

    protected final Date timeUpdated;
    protected final String compartmentId;
//    private final KnowledgeBaseSummary knowledgeBaseSummary;
    
    public KnowledgeBaseItem(OCID id, String compartmentId, String displayName, Date timeUpdated) {
        super(id, displayName);
        this.timeUpdated = timeUpdated;
        this.compartmentId = compartmentId;
//        this.knowledgeBaseSummary = knowledgeBaseSummary;
        
    }
    
//    public KnowledgeBaseSummary getKnowledgeBaseSummary() {
//        return knowledgeBaseSummary;
//    }

    @Override
    public URL getURL() {
        try {
            return new URL("https://cloud.oracle.com/adm/knowledgeBases/" + getKey().getValue());
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

    @Override
    public int maxInProject() {
        return 1;
    }

}