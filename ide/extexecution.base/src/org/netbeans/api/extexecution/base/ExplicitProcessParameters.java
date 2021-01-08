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
package org.netbeans.api.extexecution.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.openide.util.Lookup;

/**
 * Allows to augment or replace process parameters for a single execution action.
 * The class is intended to be used by launchers which build parameters based on some
 * persistent configuration (project, workspace) to allow additions, or replacements
 * for a single execution only.
 * <p>
 * Two groups of parameters are recognized: {@link #getPriorityArguments()}, which should be passed
 * first to the process (i.e. launcher parameters) and {@link #getArguments()} that represent the ordinary
 * process arguments.
 * <p>
 * If the object is marked as {@link #isReplacement()}, the launcher implementor SHOULD replace all
 * default or configured parameters with contents of this instruction. Both arguments and priorityArguments can have value {@code null}, which means "undefined": 
 * in that case, the relevant group of configured parameters should not be affected.
 * <p>
 * Since these parameters are passed <b>externally<b>, there's an utility method, {@link #buildExplicitParameters(org.openide.util.Lookup)}
 * that builds the explicit parameter instruction based on {@link Lookup} contents. The parameters are
 * merged in the order of the {@link Builder#withRank configured rank} and appearance (in the sort priority order). 
 * The default rank is {@code 0}, which allows both append or prepend parameters. If an item's 
 * {@link ExplicitProcessParameters#isReplacement()} is true, all arguments collected so far are discarded.
 * <p>
 * 
 * @author sdedic
 * @since 1.16
 */
public final class ExplicitProcessParameters {
    final int rank;
    private final List<String>    priorityArguments;
    private final List<String>    arguments;
    private final boolean  appendToExisting;

    public ExplicitProcessParameters(int rank, List<String> priorityArguments, 
            List<String> arguments, boolean appendToExisting) {
        this.rank = rank;
        this.priorityArguments = priorityArguments;
        this.arguments = arguments;
        this.appendToExisting = appendToExisting;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public List<String> getPriorityArguments() {
        return priorityArguments;
    }
    
    public boolean isReplacement() {
        return !appendToExisting;
    }

    public static ExplicitProcessParameters buildExplicitParameters(Lookup context) {
        return buildExplicitParameters(context.lookupAll(ExplicitProcessParameters.class));
    }
    
    public static ExplicitProcessParameters buildExplicitParameters(Collection<? extends ExplicitProcessParameters> items) {
        List<? extends ExplicitProcessParameters> all = new ArrayList<>(items);
        Collections.sort(all, (a, b) -> {
            ExplicitProcessParameters x;
            int d = a.rank - b.rank;
            if (d != 0) {
                return d;
            }
            return all.indexOf(a) - all.indexOf(b);
        });
        ExplicitProcessParameters.Builder b = new ExplicitProcessParameters.Builder();
        for (ExplicitProcessParameters item : all) {
            if (item.isReplacement()) {
                b.clearAll();
                b.appendToExisting(false);
            }
            b.priorityArgs(item.getPriorityArguments());
            b.args(item.getArguments());
        }
        return b.build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public final static class Builder {
        private int rank = 0;
        private List<String> priorityArguments = new ArrayList<>();
        private List<String> arguments = new ArrayList<>();
        private boolean  appendParameters;
        
        public void clearAll() {
            priorityArguments.clear();
            arguments.clear();
        }
        
        public Builder arg(String a) {
            arguments.add(a);
            return this;
        }

        public Builder args(List<String> args) {
            arguments.addAll(args);
            return this;
        }

        public Builder args(String[] args) {
            return args(Arrays.asList(args));
        }
        
        public Builder priorityArg(String a) {
            priorityArguments.add(a);
            return this;
        }

        public Builder priorityArgs(List<String> args) {
            priorityArguments.addAll(args);
            return this;
        }

        public Builder priorityArgs(String[] args) {
            return args(Arrays.asList(args));
        }
        
        public Builder appendToExisting(boolean append) {
            this.appendParameters = append;
            return this;
        }
        
        public Builder withRank(int rank) {
            this.rank = rank;
            return this;
        }
        
        public ExplicitProcessParameters build() {
            return new ExplicitProcessParameters(rank, priorityArguments, arguments, appendParameters);
        }
    }
}
