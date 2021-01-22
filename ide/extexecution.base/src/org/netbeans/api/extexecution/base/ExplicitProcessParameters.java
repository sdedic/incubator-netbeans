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
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.openide.util.Lookup;

/**
 * Allows to augment or replace process parameters for a single execution action.
 * The class is intended to be used by launchers which build parameters based on some
 * persistent configuration (project, workspace) to allow additions, or replacements
 * for a single execution only.
 * <p>
 * It is <b>strongly recommended</b> for any feature that performs execution of a process to support {@link ExplicitProcessParameters},
 * from a contextual {@link Lookup}, or at worst from {@link Lookup#getDefault()}. It will allow for future customizations and
 * automation of the feature, enhancing the process launch for various environments, technologies etc.
 * <p>
 * <i>Note:</i> please refer also to {@code StartupExtender} API in the {@code extexecution} module, which contributes globally
 * to priority arguments.
 * <p>
 * Two groups of parameters are recognized: {@link #getPriorityArguments()}, which should be passed
 * first to the process (i.e. launcher parameters) and {@link #getArguments()} that represent the ordinary
 * process arguments.
 * <p>
 * If the object is marked as {@link #isArgReplacement()}, the launcher implementor SHOULD replace all
 * default or configured parameters with contents of this instruction. Both arguments and priorityArguments can have value {@code null}, which means "undefined": 
 * in that case, the relevant group of configured parameters should not be affected.
 * <p>
 * Since these parameters are passed <b>externally<b>, there's an utility method, {@link #buildExplicitParameters(org.openide.util.Lookup)}
 * that builds the explicit parameter instruction based on {@link Lookup} contents. The parameters are
 * merged in the order of the {@link Builder#withRank configured rank} and appearance (in the sort priority order). 
 * The default rank is {@code 0}, which allows both append or prepend parameters. If an item's 
 * {@link ExplicitProcessParameters#isArgReplacement()} is true, all arguments collected so far are discarded.
 * <p>
 * If the combining algorithm is acceptable for the caller's purpose, the following pattern may be used to build the final
 * command line:
 * <code><pre>
 *      ExplicitProcessParameters contextParams = ExplicitProcessParameters.buildExplicitParameters(runContext);
 *      ExplicitProcessParameters combined = ExplicitProcessParameters.builder().
 *              priorityArgs(extraArgs).
 *              args(args).
 *              combine(contextParams).build();
 * </pre></code>
 * This example will combine some args and extra args from project, or configuration with arguments passed from the
 * {@code runContext} Lookup. 
 * @author sdedic
 * @since 1.16
 */
public final class ExplicitProcessParameters {
    final int rank;
    private final List<String>    priorityArguments;
    private final List<String>    arguments;
    private final boolean  appendArgs;
    private final boolean  appendPriorityArgs;

    private ExplicitProcessParameters(int rank, List<String> priorityArguments, 
            List<String> arguments, boolean appendArgs, boolean appendPriorityArgs) {
        this.rank = rank;
        this.priorityArguments = priorityArguments == null ? null : Collections.unmodifiableList(priorityArguments);
        this.arguments = arguments == null ? null : Collections.unmodifiableList(arguments);
        this.appendArgs = appendArgs;
        this.appendPriorityArgs = appendPriorityArgs;
    }
    
    /**
     * Returns true, if the instance has no effect when {@link Builder#combine}d onto base parameters.
     * @return true, if no effect is expected.
     */
    public boolean isEmpty() {
        boolean change = false;
        if (isArgReplacement() || isPriorityArgReplacement()) {
            return false;
        }
        return (((arguments == null) || arguments.isEmpty()) && (priorityArguments == null || priorityArguments.isEmpty()));
    }

    /**
     * Returns the arguments to be passed. Returns {@code null} if the object does not
     * want to alter the argument list.
     * @return arguments to be passed or {@code null} if the argument list should not be altered.
     */
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * Convenience method that returns an empty list, if no arguments are present. Useful to build
     * the final argument list.
     * @return arguments list to be passed, possibly empty (not null)
     */
    public @NonNull List<String> getArgumentsOrEmpty() {
        return arguments == null ? Collections.emptyList() : getArguments();
    }

    /**
     * Returns the priorityarguments to be passed. Returns {@code null} if the object does not
     * want to alter the argument list.
     * @return arguments to be passed or {@code null} if the priority argument list should not be altered.
     */
    public List<String> getPriorityArguments() {
        return priorityArguments;
    }
    
    /**
     * Convenience method that returns an empty list, if no priority arguments are present. Useful to build
     * the final argument list.
     * @return priority arguments list to be passed, possibly empty (not null)
     */
    public @NonNull List<String> getPriorityArgumentsOrEmpty() {
        return priorityArguments;
    }
    
    /**
     * Instructs to replace arguments collected so far.
     * @return true, if arguments collected should be discarded.
     */
    public boolean isArgReplacement() {
        return !appendArgs;
    }

    /**
     * Instructs to replace priority arguments collected so far.
     * @return true, if priority arguments collected should be discarded.
     */
    public boolean isPriorityArgReplacement() {
        return !appendPriorityArgs;
    }
    
    /**
     * Returns the argument lists merged. Priority arguments (if any) are passed first, followed
     * by {@code middle} (if any), then (normal) arguments. The method is a convenience to build
     * a complete command line for the launcher + command + command arguments.
     * @return combined arguments.
     */
    public @NonNull List<String> getAllArguments(@NullAllowed String... middle) {
        List<String> a = new ArrayList<>();
        if (priorityArguments != null) {
            a.addAll(priorityArguments);
        }
        if (middle != null && middle.length > 0) {
            a.addAll(Arrays.asList(middle));
        }
        if (arguments != null) {
            a.addAll(arguments);
        }
        return a;
    }

    /**
     * Merges ExplicitProcessParameters instructions found in the Lookup. See {@link #buildExplicitParameters(java.util.Collection)}
     * for more details.
     * @param context context for the execution
     * @return merged instructions
     */
    @NonNull
    public static ExplicitProcessParameters buildExplicitParameters(Lookup context) {
        return buildExplicitParameters(context.lookupAll(ExplicitProcessParameters.class));
    }
    
    /**
     * Merges individual instruction. 
     * This method serves as a convenience and uniform ("standard") methods to merge argument lists for process execution. Should be used
     * whenever a process (build, run, tool, ...) is executed. If the feature diverges, it should document how it processes the
     * {@link ExplicitProcessParamters}. It is <b>strongly recommended</b> to support explicit parameters in order to allow for 
     * customizations and automation.
     * <p>
     * Processes instructions in the order of {@link Builder#withRank(int)} and appearance. Whenever an item is flagged as
     * a replacement, all arguments (priority arguments) collected to that point are discarded. Item's arguments (priority arguments)
     * will become the only ones listed.
     * <p>
     * <i>Note:</i> if a replacement instruction and all the following (if any) have {@link #getArguments()} {@code null} (= no change), 
     * the result will report <b>no change</b>. It is therefore possible to <b>discard all contributions</b> by appending a no-change replacement 
     * last.
     * 
     * @param items individual instructions.
     * @return combined instructions.
     */
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
        Builder b = builder().appendArgs(all.isEmpty());
        for (ExplicitProcessParameters item : all) {
            b.combine(item);
        }
        return b.build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builds the {@link ExplicitProcessParameters} instance. The builder initially:
     * <ul>
     * <li><b>appends</b> priority arguments
     * <li><b>replaces</b> (normal) arguments
     * </ul>
     * and the mode can be overriden for each group.
     */
    public final static class Builder {
        private int rank = 0;
        private List<String> priorityArguments = null;
        private List<String> arguments = null;
        private Boolean  appendArgs;
        private Boolean  appendPriorityArgs;
        
        private void initArgs() {
            if (arguments == null) {
                arguments = new ArrayList<>();
                if (appendArgs == null) {
                    appendArgs = false;
                }
            }
        }
        
        /**
         * Appends a single argument. {@code null} is ignored.
         * @param a argument
         * @return the builder
         */
        public Builder arg(@NullAllowed String a) {
            if (a == null) {
                return this;
            }
            initArgs();
            arguments.add(a);
            return this;
        }

        /**
         * Appends arguments in the list. {@code null} is ignored as well as {@code null}
         * items in the list.
         * @param args argument list
         * @return the builder
         */
        public Builder args(@NullAllowed List<String> args) {
            if (args == null) {
                return this;
            }
            // init even if the list is empty.
            initArgs();
            args.forEach(this::arg);
            return this;
        }

        /**
         * Appends arguments in the list. {@code null} is ignored as well as {@code null}
         * items in the list.
         * @param args argument list
         * @return the builder
         */
        public Builder args(@NullAllowed String... args) {
            if (args == null) {
                return this;
            }
            return args(Arrays.asList(args));
        }
        
        private void initPriorityArgs() {
            if (priorityArguments == null) {
                priorityArguments = new ArrayList<>();
                if (appendPriorityArgs == null) {
                    appendPriorityArgs = true;
                }
            }
        }
        
        /**
         * Appends a single priority argument. {@code null} is ignored.
         * @param a priority argument
         * @return the builder
         */
        public Builder priorityArg(@NullAllowed String a) {
            if (a == null) {
                return this;
            }
            initPriorityArgs();
            priorityArguments.add(a);
            return this;
        }

        /**
         * Appends arguments in the list. {@code null} is ignored as well as {@code null}
         * items in the list.
         * @param args argument list
         * @return the builder
         */
        public Builder priorityArgs(@NullAllowed List<String> args) {
            if (args == null) {
                return this;
            }
            initPriorityArgs();
            args.forEach(this::priorityArg);
            return this;
        }

        /**
         * Appends arguments in the list. {@code null} is ignored as well as {@code null}
         * items in the list.
         * @param args argument list
         * @return the builder
         */
        public Builder priorityArgs(@NullAllowed String... args) {
            if (args == null) {
                return this;
            }
            return args(Arrays.asList(args));
        }
        
        /**
         * Changes the combining  mode for args. Setting to false instructs
         * that all arguments that may precede should be discarded and the
         * arguments provided by the built {@link ExplicitProcessParameters} are the only
         * ones passed to the process.
         * @param append true to append, false to replace
         * @return the builder
         */
        public Builder appendArgs(boolean append) {
            this.appendArgs = append;
            return this;
        }
        
        /**
         * Changes the combining mode for priority args. Setting to false instructs
         * that all arguments that may precede should be discarded and the
         * priority arguments provided by the built {@link ExplicitProcessParameters} are the only
         * ones passed to the process.
         * @param append true to append, false to replace
         * @return the builder
         */
        public Builder appendPriorityArgs(boolean append) {
            this.appendArgs = append;
            return this;
        }

        /**
         * Defines a rank (position) for combining. The default rank is {@code 0}.
         * @param rank rank of the instruction
         * @return the builder
         */
        public Builder withRank(int rank) {
            this.rank = rank;
            return this;
        }
        
        /**
         * Apply {@link ExplicitProcessParameters} on top of this Builder's state.
         * It will merge in the passed instruction as described in {@link ExplicitProcessParameters#buildExplicitParameters(java.util.Collection)}.
         * 
         * @param p the instruction to combine
         * @return the modified builder
         */
        public Builder combine(@NullAllowed ExplicitProcessParameters p) {
            if (p == null) {
                return this;
            }
            if (p.isPriorityArgReplacement()) {
                priorityArguments = null;
                if (p.getPriorityArguments() != null) {
                    appendPriorityArgs = false;
                }
            }
            if (p.isArgReplacement()) {
                arguments = null;
                if (p.getArguments() != null) {
                    appendArgs = false;
                }
            }
            if (p.getPriorityArguments() != null) {
                priorityArgs(p.getPriorityArguments());
            }
            if (p.getArguments() != null) {
                args(p.getArguments());
            }
            return this;
        }
        
        /**
         * Produces the {@link ExplicitProcessParameters} instruction.
         * @return the {@link ExplicitProcessParameters} instance.
         */
        public ExplicitProcessParameters build() {
            return new ExplicitProcessParameters(rank, priorityArguments, arguments, 
                    // if no args / priority args given and no explicit instruction on append,
                    // make the args appending.
                    appendArgs != Boolean.FALSE, appendPriorityArgs != Boolean.FALSE);
        }
    }
}
