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

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.NumberDocument;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.mongodb.DBObject;
import org.codinjutsu.tools.nosql.ServerConfiguration;
import org.codinjutsu.tools.nosql.database.mongo.MongoManager;
import org.codinjutsu.tools.nosql.database.mongo.model.MongoCollection;
import org.codinjutsu.tools.nosql.database.mongo.model.MongoResult;
import org.codinjutsu.tools.nosql.utils.GuiUtils;
import org.codinjutsu.tools.nosql.view.action.*;

import javax.swing.*;
import java.awt.*;

public class MongoPanel extends NoSqlResultView {

    private JPanel rootPanel;
    private Splitter splitter;
    private JPanel toolBar;
    private JPanel errorPanel;
    private final JTextField rowLimitField = new JTextField("");
    private final MongoResultPanel resultPanel;
    private final QueryPanel queryPanel;

    private final MongoManager mongoManager;
    private final ServerConfiguration configuration;
    private final MongoCollection mongoCollection;

    public MongoPanel(Project project, final MongoManager mongoManager, final ServerConfiguration configuration, final MongoCollection mongoCollection) {
        this.mongoManager = mongoManager;
        this.mongoCollection = mongoCollection;
        this.configuration = configuration;

        errorPanel.setLayout(new BorderLayout());

        queryPanel = new QueryPanel(project);
        queryPanel.setVisible(false);

        resultPanel = createResultPanel(project, new MongoDocumentOperations() {

            public DBObject getMongoDocument(Object _id) {
                return mongoManager.findMongoDocument(configuration, mongoCollection, _id);
            }

            public void updateMongoDocument(DBObject mongoDocument) {
                mongoManager.update(configuration, mongoCollection, mongoDocument);
                executeQuery();
            }

            public void deleteMongoDocument(Object objectId) {
                mongoManager.delete(configuration, mongoCollection, objectId);
                executeQuery();
            }
        });

        splitter.setOrientation(true);
        splitter.setProportion(0.2f);
        splitter.setSecondComponent(resultPanel);

        setLayout(new BorderLayout());
        add(rootPanel);

        initToolBar();
    }

    private void initToolBar() {
        toolBar.setLayout(new BorderLayout());

        rowLimitField.setColumns(5);
        rowLimitField.setDocument(new NumberDocument());

        JPanel rowLimitPanel = new NonOpaquePanel();
        rowLimitPanel.add(new JLabel("Row limit:"), BorderLayout.WEST);
        rowLimitPanel.add(rowLimitField, BorderLayout.CENTER);
        rowLimitPanel.add(Box.createHorizontalStrut(5), BorderLayout.EAST);
        toolBar.add(rowLimitPanel, BorderLayout.WEST);

        installResultPanelActions();
    }

    private MongoResultPanel createResultPanel(Project project, MongoDocumentOperations mongoDocumentOperations) {
        return new MongoResultPanel(project, mongoDocumentOperations);
    }


    void installResultPanelActions() {
        DefaultActionGroup actionResultGroup = new DefaultActionGroup("MongoResultGroup", true);
        if (ApplicationManager.getApplication() != null) {
            actionResultGroup.add(new ExecuteQuery(this));
            actionResultGroup.add(new OpenFindAction(this));
            actionResultGroup.add(new EnableAggregateAction(queryPanel));
            actionResultGroup.addSeparator();
            actionResultGroup.add(new AddMongoDocumentAction(resultPanel));
            actionResultGroup.add(new EditMongoDocumentAction(resultPanel));
            actionResultGroup.add(new CopyResultAction(resultPanel));
        }
        final TreeExpander treeExpander = new TreeExpander() {
            @Override
            public void expandAll() {
                resultPanel.expandAll();
            }

            @Override
            public boolean canExpand() {
                return true;
            }

            @Override
            public void collapseAll() {
                resultPanel.collapseAll();
            }

            @Override
            public boolean canCollapse() {
                return true;
            }
        };

        CommonActionsManager actionsManager = CommonActionsManager.getInstance();

        final AnAction expandAllAction = actionsManager.createExpandAllAction(treeExpander, resultPanel);
        final AnAction collapseAllAction = actionsManager.createCollapseAllAction(treeExpander, resultPanel);

        Disposer.register(this, new Disposable() {
            @Override
            public void dispose() {
                collapseAllAction.unregisterCustomShortcutSet(resultPanel);
                expandAllAction.unregisterCustomShortcutSet(resultPanel);
            }
        });

        actionResultGroup.addSeparator();
        actionResultGroup.add(expandAllAction);
        actionResultGroup.add(collapseAllAction);
        actionResultGroup.add(new CloseFindEditorAction(this));

        ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("MongoResultGroupActions", actionResultGroup, true);
        actionToolBar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
        JComponent actionToolBarComponent = actionToolBar.getComponent();
        actionToolBarComponent.setBorder(null);
        actionToolBarComponent.setOpaque(false);

        toolBar.add(actionToolBarComponent, BorderLayout.CENTER);
    }

    public MongoCollection getMongoCollection() {
        return mongoCollection;
    }


    public void showResults() {
        executeQuery();
    }

    public void executeQuery() {
        try {
            errorPanel.setVisible(false);
            validateQuery();
            MongoResult mongoResult = mongoManager.loadRecords(configuration, mongoCollection, queryPanel.getQueryOptions(rowLimitField.getText()));
            resultPanel.updateResultTableTree(mongoResult);
        } catch (Exception ex) {
            errorPanel.invalidate();
            errorPanel.removeAll();
            errorPanel.add(new ErrorPanel(ex), BorderLayout.CENTER);
            errorPanel.validate();
            errorPanel.setVisible(true);
        }
    }

    private void validateQuery() {
        queryPanel.validateQuery();
    }

    @Override
    public void dispose() {
        resultPanel.dispose();
    }

    public MongoResultPanel getResultPanel() {
        return resultPanel;
    }

    public void openFindEditor() {
        queryPanel.setVisible(true);
        splitter.setFirstComponent(queryPanel);
        GuiUtils.runInSwingThread(new Runnable() {
            @Override
            public void run() {
                focusOnEditor();
            }
        });
    }

    public void closeFindEditor() {
        splitter.setFirstComponent(null);
        queryPanel.setVisible(false);
    }

    public void focusOnEditor() {
        queryPanel.requestFocusOnEditor();
    }

    public boolean isFindEditorOpened() {
        return splitter.getFirstComponent() == queryPanel;
    }

    interface MongoDocumentOperations {
        DBObject getMongoDocument(Object _id);

        void deleteMongoDocument(Object mongoDocument);

        void updateMongoDocument(DBObject mongoDocument);
    }
}
