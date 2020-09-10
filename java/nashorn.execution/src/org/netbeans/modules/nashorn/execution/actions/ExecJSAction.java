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

package org.netbeans.modules.nashorn.execution.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.autoupdate.InstallSupport;
import org.netbeans.api.autoupdate.UpdateManager;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.autoupdate.ui.api.PluginManager;
import org.netbeans.modules.nashorn.execution.ModuleRequestException;
import org.netbeans.modules.nashorn.execution.NashornPlatform;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ui.support.FileSensitiveActions;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.DynamicMenuContent;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;

/**
 *
 * @author Martin
 */
abstract class ExecJSAction extends AbstractAction implements ContextAwareAction, ActionListener, ChangeListener {
    
    protected static final Action NO_ACTION = createNoAction();
    
    private final FileObject js;
    
    protected ExecJSAction(String name) {
        putValue(Action.NAME, name);
        js = null;
        setEnabled(true);
    }
    
    protected ExecJSAction(String name, FileObject js, String command) {
        putValue(Action.NAME, name);
        this.js = js;
        KeyStroke actionKeyStroke = getActionKeyStroke(command);
        putValue(Action.ACCELERATOR_KEY, actionKeyStroke);
        setEnabled(true);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        JavaPlatform javaPlatform = NashornPlatform.getDefault().getPlatform();
        if (javaPlatform == null) {
            //System.err.println("No suitable Java!");
            // TODO: Show a dialog that opens Java Platform Manager
            return ;
        }
        FileObject file = getCurrentFile();
        if (file == null) {
            return ;
        }
        boolean tryAgain = false;
        int repeats = 0;
        do {
            repeats++;
            try {
                exec(javaPlatform, file);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } catch (ModuleRequestException ex) {
                tryAgain = maybeInstallModule(ex);
            } catch (UnsupportedOperationException ex) {
                Exceptions.printStackTrace(ex);
            }
        } while (tryAgain && repeats < 2);
    }
    
    @NbBundle.Messages({
        "TITLE_CannotRunScript=Execution failed",
        "# {0} - JDK name",
        "ERR_CannotRunScriptQuestion=Could not run the script: {0}. Javascript implementation may be downloaded as a module, do you want to install it ?",
        "# {0} - JDK name",
        "ERR_CannotRunScript=Could not run the script: {0}."
    })
    private boolean maybeInstallModule(ModuleRequestException e) {
        Preferences p = NbPreferences.forModule(ExecJSAction.class);
        if (p.getBoolean(e.getModuleName(), false)) {
            StatusDisplayer.getDefault().setStatusText(Bundle.ERR_CannotRunScript(e.getMessage()), 1);
            return false;
        }
        NotifyDescriptor.Confirmation conf = new NotifyDescriptor.Confirmation(
                Bundle.ERR_CannotRunScriptQuestion(e.getMessage()), 
                Bundle.TITLE_CannotRunScript(), NotifyDescriptor.YES_NO_CANCEL_OPTION);
        Object o = DialogDisplayer.getDefault().notify(conf);
        if (o == NotifyDescriptor.CANCEL_OPTION) {
            return false;
        }
        if (o == NotifyDescriptor.NO_OPTION) {
            p.putBoolean(e.getModuleName(), true);
            return false;
        }
        Object res = PluginManager.installSingle(e.getModuleName(), e.getModuleDescription());
        return res == null;
    }
    
    private FileObject getCurrentFile() {
        if (js != null) {
            return js;
        }
        return Utilities.actionsGlobalContext().lookup(FileObject.class);
    }
    
    abstract protected void exec(JavaPlatform javaPlatform, FileObject fo) throws IOException, UnsupportedOperationException;

    @Override
    public void stateChanged(ChangeEvent e) {
        JavaPlatform platform = NashornPlatform.getDefault().getPlatform();
        setEnabled(platform != null);
    }
    
    protected static KeyStroke getActionKeyStroke(String command) {
        Action fileCommandAction = FileSensitiveActions.fileCommandAction(command, "name", null);
        if (fileCommandAction != null) {
            return (KeyStroke) fileCommandAction.getValue(Action.ACCELERATOR_KEY);
        } else {
            return null;
        }
    }
    
    protected static boolean isEnabledAction(String command, FileObject fo, Lookup actionContext) {
        Project p = findProject(fo);
        if (p != null) {
            ActionProvider ap = p.getLookup().lookup(ActionProvider.class);
            if (ap != null && ap.getSupportedActions() != null && Arrays.asList(ap.getSupportedActions()).contains(command)) {
                return ap.isActionEnabled(command, actionContext);
            }
        }
        return false;
    }
    
    private static Project findProject(FileObject fo) {
        return FileOwnerQuery.getOwner(fo);
    }
    
    private static Action createNoAction() {
        return new NoAction();
    }
    
    private static final class NoAction implements Action, Presenter.Popup {
        
        private NoItem NO_ITEM = new NoItem();

        @Override
        public Object getValue(String key) {
            return null;
        }

        @Override
        public void putValue(String key, Object value) {}

        @Override
        public void setEnabled(boolean b) {}

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {}

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {}

        @Override
        public void actionPerformed(ActionEvent e) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public JMenuItem getPopupPresenter() {
            return NO_ITEM;
        }
        
        private static class NoItem extends JMenuItem implements DynamicMenuContent {

            @Override
            public JComponent[] getMenuPresenters() {
                return new JComponent[]{};
            }

            @Override
            public JComponent[] synchMenuPresenters(JComponent[] items) {
                return items;
            }
            
        }
    }
}
