/*
 * Copyright (c) 2015 David Boissier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codinjutsu.tools.nosql.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RawCommandLineEditor;
import org.apache.commons.lang.StringUtils;
import org.codinjutsu.tools.nosql.ServerConfiguration;
import org.codinjutsu.tools.nosql.database.DatabaseVendor;
import org.codinjutsu.tools.nosql.database.DatabaseVendorManager;
import org.codinjutsu.tools.nosql.logic.ConfigurationException;
import org.codinjutsu.tools.nosql.utils.GuiUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ServerConfigurationPanel implements Disposable {

    public static final Icon SUCCESS = GuiUtils.loadIcon("success.png");
    public static final Icon FAIL = GuiUtils.loadIcon("fail.png");
    private final Project project;

    private JPanel rootPanel;

    private JTextField serverUrlsField;
    private JTextField usernameField;
    private JPasswordField passwordField;

    private JButton testConnectionButton;
    private JLabel feedbackLabel;
    private JTextField collectionsToIgnoreField;

    private RawCommandLineEditor shellArgumentsLineField;
    private JPanel mongoShellOptionsPanel;
    private JTextField labelField;
    private JCheckBox autoConnectCheckBox;
    private JTextField databaseField;
    private TextFieldWithBrowseButton shellWorkingDirField;
    private JCheckBox userDatabaseAsMySingleDatabaseField;
    private JCheckBox sslConnectionField;
    private ComboBox databaseVendorField;
    private JLabel databaseInfoLabel;

    private final DatabaseVendorManager vendorManager;


    public ServerConfigurationPanel(Project project, DatabaseVendorManager databaseVendorManager) {
        this.project = project;
        this.vendorManager = databaseVendorManager;
        mongoShellOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder("Mongo shell options", true));

        shellArgumentsLineField.setDialogCaption("Mongo arguments");
        serverUrlsField.setName("serverUrlsField");
        usernameField.setName("usernameField");
        passwordField.setName("passwordField");
        feedbackLabel.setName("feedbackLabel");
        labelField.setName("labelField");
        userDatabaseAsMySingleDatabaseField.setName("userDatabaseAsMySingleDatabaseField");
        userDatabaseAsMySingleDatabaseField.setToolTipText("This should be checked when using a MongoLab single database for instance");
        sslConnectionField.setName("sslConnectionField");
        autoConnectCheckBox.setName("autoConnectField");
        databaseField.setName("databaseListField");
        databaseVendorField.setName("databaseVendorField");
        databaseField.setToolTipText("If your access is restricted to a specific database, you can set it right here");

        testConnectionButton.setName("testConnection");

        shellWorkingDirField.setText(null);

        for (DatabaseVendor databaseVendor : DatabaseVendor.values()) {
            databaseVendorField.addItem(databaseVendor);
        }
        databaseVendorField.setRenderer(new ColoredListCellRenderer() {
            @Override
            protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
                DatabaseVendor databaseVendor = (DatabaseVendor) value;
                setIcon(databaseVendor.icon);
                append(databaseVendor.label);
            }
        });
        databaseVendorField.setSelectedIndex(-1);
        initListeners();
        databaseVendorField.setSelectedIndex(0);
    }

    private void initListeners() {
        testConnectionButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    validateUrls();
                    testConnectionButton.setEnabled(false);
                    testConnectionButton.setText("Connecting...");
                    testConnectionButton.repaint();
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                            try {
                                ServerConfiguration configuration = ServerConfiguration.byDefault();
                                configuration.setServerUrls(getServerUrls());
                                configuration.setUsername(getUsername());
                                configuration.setPassword(getPassword());
                                configuration.setUserDatabase(getUserDatabase());
                                vendorManager.get(project, getSelectedDatabaseVendor()).connect(configuration);

                                feedbackLabel.setIcon(SUCCESS);
                                feedbackLabel.setText("Connection successfull");
                            } catch (ConfigurationException ex) {
                                setErrorMessage(ex.getMessage());
                            } finally {
                                testConnectionButton.setEnabled(true);
                                testConnectionButton.setText("Test connection");
                            }
                        }
                    });
                } catch (ConfigurationException ex) {
                    setErrorMessage(ex.getMessage());
                }
            }
        });

        databaseVendorField.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                DatabaseVendor selected = (DatabaseVendor) databaseVendorField.getSelectedItem();
                if (selected == null) {
                    return;
                }
                serverUrlsField.setText(selected.defaultUrl);
                databaseInfoLabel.setText(selected.info);
            }
        });

    }

    private void validateUrls() {
        List<String> serverUrls = getServerUrls();
        if (serverUrls == null) {
            throw new ConfigurationException("URL(s) should be set");
        }
        for (String serverUrl : serverUrls) {
            String[] host_port = serverUrl.split(":");
            if (host_port.length < 2) {
                throw new ConfigurationException(String.format("URL '%s' format is incorrect. It should be 'host:port'", serverUrl));
            }

            try {
                Integer.valueOf(host_port[1]);
            } catch (NumberFormatException e) {
                throw new ConfigurationException(String.format("Port in the URL '%s' is incorrect. It should be a number", serverUrl));
            }
        }

    }


    private List<String> getCollectionsToIgnore() {
        String collectionsToIgnoreText = collectionsToIgnoreField.getText();
        if (StringUtils.isNotBlank(collectionsToIgnoreText)) {
            String[] collectionsToIgnore = collectionsToIgnoreText.split(",");

            List<String> collections = new LinkedList<String>();
            for (String collectionToIgnore : collectionsToIgnore) {
                collections.add(StringUtils.trim(collectionToIgnore));
            }
            return collections;
        }
        return Collections.emptyList();
    }


    public void applyConfigurationData(ServerConfiguration configuration) {
        validateUrls();

        configuration.setLabel(getLabel());
        configuration.setDatabaseVendor(getSelectedDatabaseVendor());
        configuration.setServerUrls(getServerUrls());
        configuration.setSslConnection(isSslConnection());
        configuration.setUsername(getUsername());
        configuration.setPassword(getPassword());
        configuration.setUserDatabase(getUserDatabase());
        configuration.setUserDatabaseAsMySingleDatabase(isUserDatabaseAsMySingleDatabase());
        configuration.setCollectionsToIgnore(getCollectionsToIgnore());
        configuration.setShellArgumentsLine(getShellArgumentsLine());
        configuration.setShellWorkingDir(getShellWorkingDir());
        configuration.setConnectOnIdeStartup(isAutoConnect());
    }


    private DatabaseVendor getSelectedDatabaseVendor() {
        return ((DatabaseVendor) databaseVendorField.getSelectedItem());
    }

    private String getLabel() {
        String label = labelField.getText();
        if (StringUtils.isNotBlank(label)) {
            return label;
        }
        return null;
    }

    private List<String> getServerUrls() {
        String serverUrls = serverUrlsField.getText();
        if (StringUtils.isNotBlank(serverUrls)) {
            return Arrays.asList(StringUtils.split(StringUtils.deleteWhitespace(serverUrls), ","));
        }
        return null;
    }

    private boolean isSslConnection() {
        return sslConnectionField.isSelected();
    }

    private String getUsername() {
        String username = usernameField.getText();
        if (StringUtils.isNotBlank(username)) {
            return username;
        }
        return null;
    }

    private String getPassword() {
        char[] password = passwordField.getPassword();
        if (password != null && password.length != 0) {
            return String.valueOf(password);
        }
        return null;
    }

    private String getUserDatabase() {
        String userDatabase = databaseField.getText();
        if (StringUtils.isNotBlank(userDatabase)) {
            return userDatabase;
        }
        return null;
    }

    private boolean isUserDatabaseAsMySingleDatabase() {
        return userDatabaseAsMySingleDatabaseField.isSelected();
    }

    private String getShellArgumentsLine() {
        String shellArgumentsLine = shellArgumentsLineField.getText();
        if (StringUtils.isNotBlank(shellArgumentsLine)) {
            return shellArgumentsLine;
        }

        return null;
    }

    private String getShellWorkingDir() {
        String shellWorkingDir = shellWorkingDirField.getText();
        if (StringUtils.isNotBlank(shellWorkingDir)) {
            return shellWorkingDir;
        }

        return null;
    }

    private boolean isAutoConnect() {
        return autoConnectCheckBox.isSelected();
    }

    public void loadConfigurationData(ServerConfiguration configuration) {
        labelField.setText(configuration.getLabel());
        databaseVendorField.getModel().setSelectedItem(configuration.getDatabaseVendor());
        serverUrlsField.setText(StringUtils.join(configuration.getServerUrls(), ","));
        usernameField.setText(configuration.getUsername());
        passwordField.setText(configuration.getPassword());
        databaseField.setText(configuration.getUserDatabase());
        sslConnectionField.setSelected(configuration.isSslConnection());
        userDatabaseAsMySingleDatabaseField.setSelected(configuration.isUserDatabaseAsMySingleDatabase());
        collectionsToIgnoreField.setText(StringUtils.join(configuration.getCollectionsToIgnore(), ","));
        shellArgumentsLineField.setText(configuration.getShellArgumentsLine());
        shellWorkingDirField.setText(configuration.getShellWorkingDir());
        autoConnectCheckBox.setSelected(configuration.isConnectOnIdeStartup());
    }

    private void createUIComponents() {
        shellWorkingDirField = new TextFieldWithBrowseButton();
        FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> browseFolderActionListener =
                new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>("Mongo shell working directory",
                        null,
                        shellWorkingDirField,
                        null,
                        fileChooserDescriptor,
                        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
        shellWorkingDirField.addBrowseFolderListener(null, browseFolderActionListener, false);
        shellWorkingDirField.setName("shellWorkingDirField");
    }

    public void setErrorMessage(String message) {
        feedbackLabel.setIcon(FAIL);
        feedbackLabel.setText(message);
    }

    @Override
    public void dispose() {

    }

    public JPanel getRootPanel() {
        return rootPanel;
    }
}
