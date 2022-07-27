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
package org.netbeans.modules.cloud.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.modules.cloud.common.explorer.NodeProvider;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;
import org.netbeans.modules.cloud.common.explorer.ChildrenProvider;
import org.netbeans.modules.cloud.common.explorer.ItemLoader;

/**
 *
 * @author Jan Horvath
 */
@ServiceProvider(service=Processor.class)
public class CloudNodeFactoryAnnotationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet(Arrays.asList(ChildrenProvider.Registration.class.getCanonicalName(),
                ItemLoader.Registration.class.getCanonicalName(),
                NodeProvider.Registration.class.getCanonicalName()));
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(ChildrenProvider.Registration.class)) {
            ChildrenProvider.Registration r = e.getAnnotation(ChildrenProvider.Registration.class);
            if (r == null) {
                continue;
            }
            for (String type : r.parentPath()) {
                layer(e).instanceFile(String.format("Cloud/%s/Nodes", type), null, ChildrenProvider.class, r, null). //NOI18N
                        position(r.position()).write();
            }
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(ItemLoader.Registration.class)) {
            ItemLoader.Registration r = e.getAnnotation(ItemLoader.Registration.class);
            if (r == null) {
                continue;
            }
            for (String type : r.path()) {
                layer(e).instanceFile(String.format("Cloud/%s/Nodes", type), null, ItemLoader.class, r, null). //NOI18N
                        position(r.position()).write();
            }
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(NodeProvider.Registration.class)) {
            NodeProvider.Registration r = e.getAnnotation(NodeProvider.Registration.class);
            if (r == null) {
                continue;
            }
            for (String type : r.path()) {
                layer(e).instanceFile(String.format("Cloud/%s/Nodes", type), null, NodeProvider.class, r, null). //NOI18N
                        position(r.position()).write();
            }
        }
        return true;
    }

}