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

package org.netbeans.api.debugger.jpda;

import com.sun.jdi.BooleanValue;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LongValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadGroupReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VoidValue;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.Connector.IntegerArgument;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequestManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Listens on given port for some connection of remotely running JDK
 * and returns VirtualMachine for it.
 *
 * <br><br>
 * <b>How to use it:</b>
 * {@codesnippet org.netbeans.api.debugger.jpda.StartListeningTest}
 * 
 * @author Jan Jancura
 */
public final class ListeningDICookie extends AbstractDICookie {

    /**
     * Public ID used for registration in Meta-inf/debugger.
     */
    public static final String ID = "netbeans-jpda-ListeningDICookie";

    private final ListeningConnector listeningConnector;
    private Map<String, ? extends Argument> args;
    private boolean isListening = false;

    private ListeningDICookie (
        ListeningConnector listeningConnector,
        Map<String, ? extends Argument> args
    ) {
        this.listeningConnector = listeningConnector;
        this.args = args;
    }

    /**
     * Creates a new instance of ListeningDICookie for given parameters.
     *
     * @param listeningConnector a instance of ListeningConnector
     * @param args arguments to be used
     * @return a new instance of ListeningDICookie for given parameters
     */
    public static ListeningDICookie create (
        ListeningConnector listeningConnector,
        Map<String, ? extends Argument> args
    ) {
        return new ListeningDICookie (
            listeningConnector,
            args
        );
    }

    /**
     * Creates a new instance of ListeningDICookie for given parameters. Example
     * showing how to tell the IDE to start listening on a random port:
     * 
     * {@codesnippet org.netbeans.api.debugger.jpda.StartListeningTest}
     *
     * @param portNumber a number of port to listen on, use {@code -1} to
     *    let the system select a random port since 3.12
     * @return a new instance of ListeningDICookie for given parameters
     */
    public static ListeningDICookie create (
        int portNumber
    ) {
        return new ListeningDICookie (
            findListeningConnector ("socket"),
            getArgs (
                findListeningConnector ("socket"),
                portNumber
            )
        );
    }

    /**
     * Creates a new instance of ListeningDICookie for given parameters.
     *
     * @param name a name of shared memory block to listen on
     * @return a new instance of ListeningDICookie for given parameters
     */
    public static ListeningDICookie create (
        String name
    ) {
        return new ListeningDICookie (
            findListeningConnector ("socket"),
            getArgs (
                findListeningConnector ("socket"),
                name
            )
        );
    }

    private static ListeningConnector findListeningConnector (String s) {
        Iterator iter = Bootstrap.virtualMachineManager ().
            listeningConnectors ().iterator ();
        while (iter.hasNext ()) {
            ListeningConnector ac = (ListeningConnector) iter.next ();
            if (ac.transport() != null && ac.transport ().name ().toLowerCase ().indexOf (s) > -1)
                return ac;
        }
        return null;
    }

    private static Map<String, ? extends Argument> getArgs (
        ListeningConnector listeningConnector,
        int portNumber
    ) {
        Map<String, ? extends Argument> args = listeningConnector.defaultArguments ();
        args.get ("port").setValue (portNumber > 0 ? "" + portNumber : "");
        return args;
    }

    private static Map<String, ? extends Argument> getArgs (
        ListeningConnector listeningConnector,
        String name
    ) {
        Map<String, ? extends Argument> args = listeningConnector.defaultArguments ();
        args.get ("name").setValue (name);
        return args;
    }

    /**
     * Returns instance of ListeningConnector.
     *
     * @return instance of ListeningConnector
     */
    public ListeningConnector getListeningConnector () {
        return listeningConnector;
    }

    /**
     * Returns map of arguments to be used.
     *
     * @return map of arguments to be used
     */
    public Map<String, ? extends Argument> getArgs () {
        return args;
    }

    /**
     * Returns port number.
     *
     * @return port number
     */
    public int getPortNumber () {
        Argument a = args.get ("port");
        if (a == null) return -1;
        String pn = a.value ();
        if (pn == null || pn.length() == 0) {
            // default to system chosen port when no port is specified:
            try {
                String address = listeningConnector.startListening(args);
                isListening = true;
                int splitIndex = address.indexOf(':');
                String localaddr = null;
                if (splitIndex >= 0) {
                    localaddr = address.substring(0, splitIndex);
                    address = address.substring(splitIndex+1);
                }
                a.setValue(address);
                pn = address;
            } catch (IOException ex) {
            } catch (IllegalConnectorArgumentsException ex) {
            }
        } else if (a instanceof IntegerArgument) {
            return ((IntegerArgument) a).intValue();
        }
        try {
            return Integer.parseInt (pn);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns shared memory block name.
     *
     * @return shared memory block name
     */
    public String getSharedMemoryName () {
        Argument a = args.get ("name");
        if (a == null) return null;
        return a.value ();
    }

    /**
     * Creates a new instance of VirtualMachine for this DebuggerInfo Cookie.
     *
     * @return a new instance of VirtualMachine for this DebuggerInfo Cookie
     */
    public VirtualMachine getVirtualMachine () throws IOException,
    IllegalConnectorArgumentsException {
        try {
            if (!isListening) {
            try {
                listeningConnector.startListening(args);
            } catch (Exception e) {
                // most probably already listening
            }
            }
            final VirtualMachine vm = listeningConnector.accept (args);
            return new DelegateVM(vm);
        } finally {
            try {
                listeningConnector.stopListening(args);
            } catch (Exception e) {
                // most probably not listening anymore                
            }
        }
    }
    private static class DelegateVM implements VirtualMachine {
        final VirtualMachine vm;

        DelegateVM(VirtualMachine vm) {
            this.vm = vm;
        }

        @Override
        public List<ReferenceType> classesByName(String string) {
            return vm.classesByName(string);
        }

        @Override
        public List<ReferenceType> allClasses() {
            return vm.allClasses();
        }

        @Override
        public void redefineClasses(Map<? extends ReferenceType, byte[]> maps) {
            vm.redefineClasses(maps);
        }

        @Override
        public List<ThreadReference> allThreads() {
            return vm.allThreads();
        }

        @Override
        public void suspend() {
            vm.suspend();
        }

        @Override
        public void resume() {
            vm.resume();
        }

        @Override
        public List<ThreadGroupReference> topLevelThreadGroups() {
            return vm.topLevelThreadGroups();
        }

        final List<EventSet> sets = new ArrayList<>();
        int queueCounter = 0;
        @Override
        public EventQueue eventQueue() {
            final EventQueue del = vm.eventQueue();
            return new EventQueue() {
                final int id;
                int index;
                {
                    synchronized (sets) {
                        id = queueCounter++;
                        index = sets.size();
                    }
                }

                @Override
                public EventSet remove() throws InterruptedException {
                    return remove(Long.MAX_VALUE);
                }

                @Override
                public EventSet remove(long l) throws InterruptedException {
                    for (;;) {
                        synchronized (sets) {
                            if (sets.size() > index) {
                                return sets.get(index++);
                            } else {
                                if (id != 0) {
                                    sets.wait(l);
                                }
                            }
                        }
                        if (id == 0) {
                            EventSet es = del.remove(l);
                            synchronized (this) {    
                                sets.add(es);
                                sets.notifyAll();
                            }
                        }
                    }
                }

                @Override
                public VirtualMachine virtualMachine() {
                    return DelegateVM.this;
                }
            };
        }

        @Override
        public EventRequestManager eventRequestManager() {
            return vm.eventRequestManager();
        }

        @Override
        public BooleanValue mirrorOf(boolean bln) {
            return vm.mirrorOf(bln);
        }

        @Override
        public ByteValue mirrorOf(byte b) {
            return vm.mirrorOf(b);
        }

        @Override
        public CharValue mirrorOf(char c) {
            return vm.mirrorOf(c);
        }

        @Override
        public ShortValue mirrorOf(short s) {
            return vm.mirrorOf(s);
        }

        @Override
        public IntegerValue mirrorOf(int i) {
            return vm.mirrorOf(i);
        }

        @Override
        public LongValue mirrorOf(long l) {
            return vm.mirrorOf(l);
        }

        @Override
        public FloatValue mirrorOf(float f) {
            return vm.mirrorOf(f);
        }

        @Override
        public DoubleValue mirrorOf(double d) {
            return vm.mirrorOf(d);
        }

        @Override
        public StringReference mirrorOf(String string) {
            return vm.mirrorOf(string);
        }

        @Override
        public VoidValue mirrorOfVoid() {
            return vm.mirrorOfVoid();
        }

        @Override
        public Process process() {
            return vm.process();
        }

        @Override
        public void dispose() {
            vm.dispose();
        }

        @Override
        public void exit(int i) {
            vm.exit(i);
        }

        @Override
        public boolean canWatchFieldModification() {
            return vm.canWatchFieldModification();
        }

        @Override
        public boolean canWatchFieldAccess() {
            return vm.canWatchFieldAccess();
        }

        @Override
        public boolean canGetBytecodes() {
            return vm.canGetBytecodes();
        }

        @Override
        public boolean canGetSyntheticAttribute() {
            return vm.canGetSyntheticAttribute();
        }

        @Override
        public boolean canGetOwnedMonitorInfo() {
            return vm.canGetOwnedMonitorInfo();
        }

        @Override
        public boolean canGetCurrentContendedMonitor() {
            return vm.canGetCurrentContendedMonitor();
        }

        @Override
        public boolean canGetMonitorInfo() {
            return vm.canGetMonitorInfo();
        }

        @Override
        public boolean canUseInstanceFilters() {
            return vm.canUseInstanceFilters();
        }

        @Override
        public boolean canRedefineClasses() {
            return vm.canRedefineClasses();
        }

        @Override
        public boolean canAddMethod() {
            return vm.canAddMethod();
        }

        @Override
        public boolean canUnrestrictedlyRedefineClasses() {
            return vm.canUnrestrictedlyRedefineClasses();
        }

        @Override
        public boolean canPopFrames() {
            return vm.canPopFrames();
        }

        @Override
        public boolean canGetSourceDebugExtension() {
            return vm.canGetSourceDebugExtension();
        }

        @Override
        public boolean canRequestVMDeathEvent() {
            return vm.canRequestVMDeathEvent();
        }

        @Override
        public boolean canGetMethodReturnValues() {
            return vm.canGetMethodReturnValues();
        }

        @Override
        public boolean canGetInstanceInfo() {
            return vm.canGetInstanceInfo();
        }

        @Override
        public boolean canUseSourceNameFilters() {
            return vm.canUseSourceNameFilters();
        }

        @Override
        public boolean canForceEarlyReturn() {
            return vm.canForceEarlyReturn();
        }

        @Override
        public boolean canBeModified() {
            return vm.canBeModified();
        }

        @Override
        public boolean canRequestMonitorEvents() {
            return vm.canRequestMonitorEvents();
        }

        @Override
        public boolean canGetMonitorFrameInfo() {
            return vm.canGetMonitorFrameInfo();
        }

        @Override
        public boolean canGetClassFileVersion() {
            return vm.canGetClassFileVersion();
        }

        @Override
        public boolean canGetConstantPool() {
            return vm.canGetConstantPool();
        }

        @Override
        public void setDefaultStratum(String string) {
            vm.setDefaultStratum(string);
        }

        @Override
        public String getDefaultStratum() {
            return vm.getDefaultStratum();
        }

        @Override
        public long[] instanceCounts(List<? extends ReferenceType> list) {
            return vm.instanceCounts(list);
        }

        @Override
        public String description() {
            return vm.description();
        }

        @Override
        public String version() {
            return vm.version();
        }

        @Override
        public String name() {
            return vm.name();
        }

        @Override
        public void setDebugTraceMode(int i) {
            vm.setDebugTraceMode(i);
        }

        @Override
        public VirtualMachine virtualMachine() {
            return vm.virtualMachine();
        }


    }
}
