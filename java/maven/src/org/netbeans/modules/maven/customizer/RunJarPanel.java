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

package org.netbeans.modules.maven.customizer;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.java.api.common.project.ui.ProjectUISupport;
import org.netbeans.modules.maven.NbMavenProjectImpl;
import org.netbeans.modules.maven.api.customizer.ModelHandle2;
import org.netbeans.modules.maven.classpath.MavenSourcesImpl;
import org.netbeans.modules.maven.execute.MavenExecuteUtils;
import org.netbeans.modules.maven.execute.model.ActionToGoalMapping;
import org.netbeans.modules.maven.execute.model.NetbeansActionMapping;
import org.netbeans.modules.maven.options.MavenSettings;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ui.support.ProjectCustomizer;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.MouseUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * panel for displaying the Run Jar project related properties..
 * in older version was bound to netbeans-jar-plugin, now is bound to plain
 * exec-maven-plugin:exec
 * @author Milos Kleint 
 */
public class RunJarPanel extends javax.swing.JPanel implements HelpCtx.Provider {
    
    private boolean isCurrentRun = true;
    private boolean isCurrentDebug = true;
    private boolean isCurrentProfile = true;
    private static final String RUN_VM_PARAMS = "exec.vmArgs"; //NOI18N
    private static final String RUN_APP_PARAMS = "exec.appArgs"; //NOI18N
    private static final String RUN_PARAMS = "exec.args"; //NOI18N
    private static final String RUN_WORKDIR = "exec.workingdir"; //NOI18N
    private static final String DEFAULT_DEBUG_PARAMS = "-agentlib:jdwp=transport=dt_socket,server=n,address=${jpda.address}"; //NOI18N
    private static final String PROFILE_CMD = "profile"; // NOI18N
    
    private ModelHandle2 handle;
    private NbMavenProjectImpl project;
    private NetbeansActionMapping run;
    private NetbeansActionMapping debug;
    private NetbeansActionMapping profile;
    private String oldMainClass;
    private String oldParams;
    private String oldVMParams;
    private String oldWorkDir;
    private String oldAllParams;
    private DocumentListener docListener;
    private ActionListener comboListener;
    private ProjectCustomizer.Category category;
    
    public RunJarPanel(ModelHandle2 handle, NbMavenProjectImpl project, ProjectCustomizer.Category category) {
        initComponents();
        boolean isVMOptionsWrap = MavenSettings.getDefault().isVMOptionsWrap();
        wrapCheckBox.setSelected(isVMOptionsWrap);
        txtVMOptions.setLineWrap(isVMOptionsWrap);
        
        this.category = category;
        this.handle = handle;
        this.project = project;
        comConfiguration.setEditable(false);
        comConfiguration.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component com = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (com instanceof JLabel) {
                    if (value == RunJarPanel.this.handle.getActiveConfiguration()) {
                        com.setFont(com.getFont().deriveFont(Font.BOLD));
                    }
                }
                return com;
            }
        });
        setupConfigurations();
        
        initValues();
        lblMainClass.setFont(lblMainClass.getFont().deriveFont(Font.BOLD));
        List<FileObject> roots = new ArrayList<FileObject>();
        Sources srcs =  ProjectUtils.getSources(project);
        SourceGroup[] grps = srcs.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
        for (int i = 0; i < grps.length; i++) {
            SourceGroup sourceGroup = grps[i];
            if (MavenSourcesImpl.NAME_SOURCE.equals(sourceGroup.getName())) {
                roots.add(sourceGroup.getRootFolder());
            }
        }
        grps = srcs.getSourceGroups(MavenSourcesImpl.TYPE_GEN_SOURCES);
        for (int i = 0; i < grps.length; i++) {
            SourceGroup sourceGroup = grps[i];
            roots.add(sourceGroup.getRootFolder());
        }

        btnMainClass.addActionListener(new MainClassListener(roots.toArray(new FileObject[roots.size()]), txtMainClass));
        docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent arg0) {
                applyChanges();
            }

            @Override
            public void removeUpdate(DocumentEvent arg0) {
                applyChanges();
            }

            @Override
            public void changedUpdate(DocumentEvent arg0) {
                applyChanges();
            }
        };
        comboListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeListeners();
                initValues();
                addListeners();
            }
        };
    }

    private void addListeners() {
        comConfiguration.addActionListener(comboListener);
        txtMainClass.getDocument().addDocumentListener(docListener);
        txtArguments.getDocument().addDocumentListener(docListener);
        txtVMOptions.getDocument().addDocumentListener(docListener);
        txtWorkDir.getDocument().addDocumentListener(docListener);
    }
    
    private void removeListeners() {
        comConfiguration.removeActionListener(comboListener);
        txtMainClass.getDocument().removeDocumentListener(docListener);
        txtArguments.getDocument().removeDocumentListener(docListener);
        txtVMOptions.getDocument().removeDocumentListener(docListener);
        txtWorkDir.getDocument().removeDocumentListener(docListener);
    }
    
    
    private String fallbackParams(String paramName) {
        String val = run.getProperties().get(paramName);
        if (val == null && debug != null) {
            val = debug.getProperties().get(paramName);
        }
        if (val == null && profile != null) {
            val = profile.getProperties().get(paramName);
        }
        return val == null ? "" : val; // NOI18N
    }
    
    private String appendIfNotEmpty(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        if (b == null || b.isEmpty()) {
            return a;
        }
        return a + " " + b;
    }
    
    private MavenExecuteUtils.ExecutionEnvHelper execEnvHelper;
    
    @NbBundle.Messages({"MsgModifiedAction=One of Run/Debug/Profile Project actions has been modified and the Run panel cannot be safely edited"})
    private void initValues() {
        run = null;
        debug = null;
        profile = null;
        ActionToGoalMapping mapp = handle.getActionMappings((ModelHandle2.Configuration) comConfiguration.getSelectedItem());
        @SuppressWarnings("unchecked")
        List<NetbeansActionMapping> lst = mapp.getActions();
        for (NetbeansActionMapping m : lst) {
            if (ActionProvider.COMMAND_RUN.equals(m.getActionName())) {
                run = m;
            }
            if (ActionProvider.COMMAND_DEBUG.equals(m.getActionName())) {
                debug = m;
            }
            if (PROFILE_CMD.equals(m.getActionName())) {
                profile = m;
            }
        }
        if (run == null) {
            run = ModelHandle2.getDefaultMapping(ActionProvider.COMMAND_RUN, project);
        }
        if (debug == null) {
            debug = ModelHandle2.getDefaultMapping(ActionProvider.COMMAND_DEBUG, project);
        }
        if (profile == null) {
            profile = ModelHandle2.getDefaultMapping(PROFILE_CMD, project);
        }
        execEnvHelper = MavenExecuteUtils.createExecutionEnvHelper(project, run, debug, profile, mapp);
        
        isCurrentRun = checkNewMapping(run);
        isCurrentDebug = checkNewMapping(debug);
        isCurrentProfile = checkNewMapping(profile);
        if (execEnvHelper.isValid()) {
            oldWorkDir = execEnvHelper.getWorkDir();
            oldAllParams = execEnvHelper.getAllParams();
            oldVMParams = execEnvHelper.getVmParams();
            oldParams = execEnvHelper.getAppParams();
            oldMainClass = execEnvHelper.getMainClass();
            
            txtMainClass.setEnabled(true);
            txtArguments.setEnabled(true);
            txtVMOptions.setEnabled(true);
            btnMainClass.setEnabled(true);
            txtWorkDir.setEnabled(true);
            btnWorkDir.setEnabled(true);
            category.setErrorMessage(null);
            customizeOptionsButton.setEnabled(true);
            wrapCheckBox.setEnabled(true);
        } else {
            txtMainClass.setEnabled(false);
            txtArguments.setEnabled(false);
            txtVMOptions.setEnabled(false);
            txtWorkDir.setEnabled(false);
            btnWorkDir.setEnabled(false);
            btnMainClass.setEnabled(false);
            customizeOptionsButton.setEnabled(false);
            wrapCheckBox.setEnabled(false);
            category.setErrorMessage(Bundle.MsgModifiedAction());
        }
        
        if (oldMainClass == null) {
            oldMainClass = ""; //NOI18N
        }
        txtMainClass.setText(oldMainClass);
        if (oldParams == null) {
            oldParams = ""; //NOI18N
        }
        txtArguments.setText(oldParams);
        if (oldVMParams == null) {
            oldVMParams = ""; //NOI18N
        }
        txtVMOptions.setText(oldVMParams);
        if (oldWorkDir == null) {
            oldWorkDir = ""; //NOI18N
        }
        txtWorkDir.setText(oldWorkDir);
        
    }

    @Override
    public void addNotify() {
        super.addNotify();
        setupConfigurations();
        initValues();
        addListeners();
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        removeListeners();
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblMainClass = new javax.swing.JLabel();
        txtMainClass = new javax.swing.JTextField();
        btnMainClass = new javax.swing.JButton();
        lblArguments = new javax.swing.JLabel();
        txtArguments = new javax.swing.JTextField();
        lblWorkDir = new javax.swing.JLabel();
        txtWorkDir = new javax.swing.JTextField();
        btnWorkDir = new javax.swing.JButton();
        lblVMOptions = new javax.swing.JLabel();
        lblHint = new javax.swing.JLabel();
        lblConfiguration = new javax.swing.JLabel();
        comConfiguration = new javax.swing.JComboBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtVMOptions = new javax.swing.JTextArea();
        customizeOptionsButton = new javax.swing.JButton();
        wrapCheckBox = new javax.swing.JCheckBox();

        lblMainClass.setLabelFor(txtMainClass);
        org.openide.awt.Mnemonics.setLocalizedText(lblMainClass, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "LBL_MainClass")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(btnMainClass, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "BTN_Browse_Main")); // NOI18N

        lblArguments.setLabelFor(txtArguments);
        org.openide.awt.Mnemonics.setLocalizedText(lblArguments, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "LBL_Arguments")); // NOI18N

        lblWorkDir.setLabelFor(txtWorkDir);
        org.openide.awt.Mnemonics.setLocalizedText(lblWorkDir, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "LBL_WorkDir")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(btnWorkDir, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "BTN_Browse_WorkingDir")); // NOI18N
        btnWorkDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnWorkDirActionPerformed(evt);
            }
        });

        lblVMOptions.setLabelFor(txtVMOptions);
        org.openide.awt.Mnemonics.setLocalizedText(lblVMOptions, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "LBL_VMOptions")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lblHint, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "LBL_VMHint")); // NOI18N

        lblConfiguration.setLabelFor(comConfiguration);
        org.openide.awt.Mnemonics.setLocalizedText(lblConfiguration, NbBundle.getMessage(RunJarPanel.class, "RunJarPanel.lblConfiguration.text")); // NOI18N

        comConfiguration.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        txtVMOptions.setColumns(20);
        txtVMOptions.setLineWrap(true);
        txtVMOptions.setRows(5);
        jScrollPane1.setViewportView(txtVMOptions);
        txtVMOptions.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(RunJarPanel.class, "RunJarPanel.txtVMOptions.AccessibleContext.accessibleDescription")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(customizeOptionsButton, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "RunJarPanel.customizeOptionsButton.text")); // NOI18N
        customizeOptionsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                customizeOptionsButtonActionPerformed(evt);
            }
        });

        wrapCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(wrapCheckBox, org.openide.util.NbBundle.getMessage(RunJarPanel.class, "RunJarPanel.wrapCheckBox.text")); // NOI18N
        wrapCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                wrapCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblWorkDir)
                    .addComponent(lblVMOptions)
                    .addComponent(lblArguments)
                    .addComponent(lblConfiguration)
                    .addComponent(lblMainClass))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblHint)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 261, Short.MAX_VALUE)
                    .addComponent(txtWorkDir)
                    .addComponent(txtArguments)
                    .addComponent(txtMainClass)
                    .addComponent(comConfiguration, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(wrapCheckBox))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(btnWorkDir, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnMainClass, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(customizeOptionsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblConfiguration)
                    .addComponent(comConfiguration, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblMainClass)
                    .addComponent(btnMainClass)
                    .addComponent(txtMainClass, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblArguments)
                    .addComponent(txtArguments, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblWorkDir)
                    .addComponent(txtWorkDir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnWorkDir))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblVMOptions)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 55, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblHint)
                        .addGap(40, 40, 40))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(customizeOptionsButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(wrapCheckBox)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );

        txtMainClass.getAccessibleContext().setAccessibleDescription("Main class");
        btnMainClass.getAccessibleContext().setAccessibleDescription("Browse main class");
        txtArguments.getAccessibleContext().setAccessibleDescription("Arguments");
        txtWorkDir.getAccessibleContext().setAccessibleDescription("Working directory");
        btnWorkDir.getAccessibleContext().setAccessibleDescription("Browse working directory");
        comConfiguration.getAccessibleContext().setAccessibleDescription("Configuration");
    }// </editor-fold>//GEN-END:initComponents

    private void btnWorkDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnWorkDirActionPerformed
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(null);
        chooser.setFileSelectionMode (JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        
        String workDir = txtWorkDir.getText();
        if (workDir.equals("")) { //NOI18N
            workDir = FileUtil.toFile(project.getProjectDirectory()).getAbsolutePath();
        }
        chooser.setSelectedFile(new File(workDir));
        chooser.setDialogTitle(org.openide.util.NbBundle.getMessage(RunJarPanel.class, "TIT_SelectWorkingDirectory"));
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) { //NOI18N
            File file = FileUtil.normalizeFile(chooser.getSelectedFile());
            txtWorkDir.setText(file.getAbsolutePath());
        }
    }//GEN-LAST:event_btnWorkDirActionPerformed

    private void customizeOptionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_customizeOptionsButtonActionPerformed
        String origin = txtVMOptions.getText();
        try {
            String result = ProjectUISupport.showVMOptionCustomizer(SwingUtilities.getWindowAncestor(this), origin);
            result = splitJVMParams(result, true);
            txtVMOptions.setText(result);
        } catch (Exception e) {
            Logger.getLogger(RunJarPanel.class.getName()).log(Level.WARNING, "Cannot parse vm options.", e); // NOI18N
        }
    }//GEN-LAST:event_customizeOptionsButtonActionPerformed

    private void wrapCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_wrapCheckBoxActionPerformed
        boolean selected = wrapCheckBox.isSelected();
        MavenSettings.getDefault().setVMOptionsWrap(selected);
        txtVMOptions.setLineWrap(selected);
    }//GEN-LAST:event_wrapCheckBoxActionPerformed

    void applyChanges() {
        String newMainClass = txtMainClass.getText().trim();
        String newParams = txtArguments.getText().trim();
        String newVMParams = txtVMOptions.getText().trim();
        String newWorkDir = txtWorkDir.getText().trim();
        
        execEnvHelper.setMainClass(newMainClass);
        execEnvHelper.setAppParams(newParams);
        execEnvHelper.setWorkDir(newWorkDir);
        execEnvHelper.setVmParams(newVMParams);
        
        execEnvHelper.applyToMappings();
    }

    private boolean checkNewMapping(NetbeansActionMapping map) {
        if (map == null || map.getGoals() == null) {
            return false; //#164323
        }
        Iterator it = map.getGoals().iterator();
        while (it.hasNext()) {
            String goal = (String) it.next();
            if (goal.matches("org\\.codehaus\\.mojo\\:exec-maven-plugin\\:(.)+\\:exec") //NOI18N
                    || goal.indexOf("exec:exec") > -1) { //NOI18N
                if (map.getProperties() != null) {
                    if (map.getProperties().containsKey("exec.args")) {
                        String execArgs = map.getProperties().get("exec.args");
                        if (execArgs.contains("-classpath")) {
                            return true;
                        }
                    }
                    if (map.getProperties().containsKey("exec.vmArgs")) {
                        String execArgs = map.getProperties().get("exec.vmArgs");
                        if (execArgs.contains("-classpath")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * used by quickrun configuration.
     * @param argline
     * @return
     */
    public static String[] splitAll(String argline) {
        String jvm = splitJVMParams(argline);
        String mainClazz = splitMainClass(argline);
        String args = splitParams(argline);
        if (jvm != null && jvm.contains("-classpath %classpath")) {
            jvm = jvm.replace("-classpath %classpath", "");
        }
        if (mainClazz != null && mainClazz.equals("${packageClassName}")) {
                    mainClazz = "";
        }
        return new String[] {
            (jvm != null ? jvm : ""),
            (mainClazz != null ? mainClazz : ""),
            (args != null ? args : "")
        };
    }
    
    static String splitJVMParams(String line) {
        return splitJVMParams(line, false);
    }
    
    private static String splitJVMParams(String line, boolean newLines) {
        return MavenExecuteUtils.splitJVMParams(line, newLines);
    }
    
    static String splitMainClass(String line) {
        return MavenExecuteUtils.splitMainClass(line);
    }
    
    static String splitParams(String line) {
        return MavenExecuteUtils.splitParams(line);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnMainClass;
    private javax.swing.JButton btnWorkDir;
    private javax.swing.JComboBox comConfiguration;
    private javax.swing.JButton customizeOptionsButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblArguments;
    private javax.swing.JLabel lblConfiguration;
    private javax.swing.JLabel lblHint;
    private javax.swing.JLabel lblMainClass;
    private javax.swing.JLabel lblVMOptions;
    private javax.swing.JLabel lblWorkDir;
    private javax.swing.JTextField txtArguments;
    private javax.swing.JTextField txtMainClass;
    private javax.swing.JTextArea txtVMOptions;
    private javax.swing.JTextField txtWorkDir;
    private javax.swing.JCheckBox wrapCheckBox;
    // End of variables declaration//GEN-END:variables

    private void setupConfigurations() {
        lblConfiguration.setVisible(true);
        comConfiguration.setVisible(true);
        DefaultComboBoxModel comModel = new DefaultComboBoxModel();
        for (ModelHandle2.Configuration conf : handle.getConfigurations()) {
            comModel.addElement(conf);
        }
        comConfiguration.setModel(comModel);
        comConfiguration.setSelectedItem(handle.getActiveConfiguration());
    }
    
    @Override
    public HelpCtx getHelpCtx() {
        return CustomizerProviderImpl.HELP_CTX;
    }    
    // End of variables declaration

        // Innercasses -------------------------------------------------------------
    
    private class MainClassListener implements ActionListener /*, DocumentListener */ {
        
        private final JButton okButton;
        private FileObject[] sourceRoots;
        private JTextField mainClassTextField;
        
        MainClassListener( FileObject[] sourceRoots, JTextField mainClassTextField ) {            
            this.sourceRoots = sourceRoots;
            this.mainClassTextField = mainClassTextField;
            this.okButton  = new JButton (NbBundle.getMessage (RunJarPanel.class, "LBL_ChooseMainClass_OK"));
            this.okButton.getAccessibleContext().setAccessibleDescription (NbBundle.getMessage (RunJarPanel.class, "AD_ChooseMainClass_OK"));
        }
        
        // Implementation of ActionListener ------------------------------------
        
        /** Handles button events
         */        
        @Override
        public void actionPerformed( ActionEvent e ) {
            
            // only chooseMainClassButton can be performed
            
            final MainClassChooser panel = new MainClassChooser (sourceRoots);
            Object[] options = new Object[] {
                okButton,
                DialogDescriptor.CANCEL_OPTION
            };
            panel.addChangeListener (new ChangeListener () {
                @Override
               public void stateChanged(ChangeEvent e) {
                   if (e.getSource () instanceof MouseEvent && MouseUtils.isDoubleClick (((MouseEvent)e.getSource ()))) {
                       // click button and finish the dialog with selected class
                       okButton.doClick ();
                   } else {
                       okButton.setEnabled (panel.getSelectedMainClass () != null);
                   }
               }
            });
            okButton.setEnabled (false);
            DialogDescriptor desc = new DialogDescriptor (
                panel,
                NbBundle.getMessage (RunJarPanel.class, "LBL_ChooseMainClass_Title" ),
                true, 
                options, 
                options[0], 
                DialogDescriptor.BOTTOM_ALIGN, 
                null, 
                null);
            //desc.setMessageType (DialogDescriptor.INFORMATION_MESSAGE);
            Dialog dlg = DialogDisplayer.getDefault ().createDialog (desc);
            dlg.setVisible (true);
            if (desc.getValue() == options[0]) {
               mainClassTextField.setText (panel.getSelectedMainClass ());
            } 
            dlg.dispose();
        }
        
    }
}
