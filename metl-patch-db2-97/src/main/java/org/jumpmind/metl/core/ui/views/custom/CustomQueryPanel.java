package org.jumpmind.metl.core.ui.views.custom;


import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.event.ShortcutAction.ModifierKey;
import com.vaadin.event.ShortcutListener;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Resource;
import com.vaadin.server.VaadinSession;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.TabSheet.Tab;
import com.vaadin.ui.themes.ValoTheme;
import org.apache.commons.lang.StringUtils;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.sqlexplorer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.aceeditor.*;
import org.vaadin.aceeditor.AceEditor.SelectionChangeEvent;
import org.vaadin.aceeditor.AceEditor.SelectionChangeListener;

import java.io.Serializable;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.*;

public class CustomQueryPanel extends VerticalSplitPanel implements IContentTab {

    protected static final Logger log = LoggerFactory.getLogger(CustomQueryPanel.class);

    private static final long serialVersionUID = 1L;

    AceEditor sqlArea;

    IDb db;

    SelectionChangeListener selectionChangeListener;

    List<ShortcutListener> shortCutListeners = new ArrayList<ShortcutListener>();

    boolean executeAtCursorButtonValue = false;

    boolean executeScriptButtonValue = false;

    boolean commitButtonValue = false;

    boolean rollbackButtonValue = false;

    IButtonBar buttonBar;

    TabSheet resultsTabs;

    Tab errorTab;

    int maxNumberOfResultTabs = 10;

    ISettingsProvider settingsProvider;

    String user;

    Connection connection;

    Label status;

    SqlSuggester suggester;

    boolean canceled = false;

    VerticalLayout emptyResults;

    Map<Component, String> resultStatuses;

    Tab generalResultsTab;

    private SuggestionExtension suggestionExtension;

    private AceEditor editor;

    transient Set<CustomSqlRunner> runnersInProgress = new HashSet<CustomSqlRunner>();

    public CustomQueryPanel(IDb db, ISettingsProvider settingsProvider, IButtonBar buttonBar, String user) {
        this.settingsProvider = settingsProvider;
        this.db = db;
        this.user = user;
        this.buttonBar = buttonBar;
        this.sqlArea = buildSqlEditor();
        this.shortCutListeners.add(createExecuteSqlShortcutListener());

        VerticalLayout resultsLayout = new VerticalLayout();
        resultsLayout.setSizeFull();

        resultsTabs = CommonUiUtils.createTabSheet();
        resultStatuses = new HashMap<Component, String>();

        HorizontalLayout statusBar = new HorizontalLayout();
        statusBar.addStyleName(ValoTheme.PANEL_WELL);
        statusBar.setMargin(new MarginInfo(true, true, true, true));
        statusBar.setWidth(100, Unit.PERCENTAGE);

        status = new Label("No Results");
        statusBar.addComponent(status);

        setSelectedTabChangeListener();

        resultsLayout.addComponents(resultsTabs, statusBar);
        resultsLayout.setExpandRatio(resultsTabs, 1);

        addComponents(sqlArea, resultsLayout);

        setSplitPosition(400, Unit.PIXELS);

        emptyResults = new VerticalLayout();
        emptyResults.setSizeFull();
        Label label = new Label("New results will appear here");
        label.setWidthUndefined();
        emptyResults.addComponent(label);
        emptyResults.setComponentAlignment(label, Alignment.MIDDLE_CENTER);
        resultStatuses.put(emptyResults, "No Results");

        if (!settingsProvider.get().getProperties().is(Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS)) {
            createGeneralResultsTab();
        }
    }

    public IDb getDb() {
        return db;
    }

    protected AceEditor buildSqlEditor() {
        editor = CommonUiUtils.createAceEditor();
        editor.setMode(AceMode.sql);
        editor.addValueChangeListener(new ValueChangeListener() {

            private static final long serialVersionUID = 1L;

            @Override
            public void valueChange(ValueChangeEvent event) {
                if (!editor.getValue().equals("")) {
                    executeAtCursorButtonValue = true;
                    executeScriptButtonValue = true;
                } else {
                    executeAtCursorButtonValue = false;
                    executeScriptButtonValue = false;
                }
                setButtonsEnabled();
            }
        });

        boolean autoSuggestEnabled = settingsProvider.get().getProperties().is(SQL_EXPLORER_AUTO_COMPLETE);
        setAutoCompleteEnabled(autoSuggestEnabled);


        //suggester = new SqlSuggester(db);
        //new SuggestionExtension(suggester).extend(editor);

        selectionChangeListener = new DummyChangeListener();
        return editor;
    }

    public IButtonBar getButtonBar() {
        return buttonBar;
    }

    protected void setSelectedTabChangeListener() {
        resultsTabs.addSelectedTabChangeListener(new SelectedTabChangeListener() {
            private static final long serialVersionUID = 1L;

            @Override
            public void selectedTabChange(SelectedTabChangeEvent event) {
                Component tab = resultsTabs.getSelectedTab();
                String st = resultStatuses.get(tab);
                if (st == null && tab instanceof VerticalLayout) {
                    if (((VerticalLayout) tab).getComponentCount() > 0) {
                        st = resultStatuses.get(((VerticalLayout) tab).getComponent(0));
                    }
                }
                if (st == null) {
                    st = "No Results";
                }
                status.setValue(st);
            }
        });
    }

    public Tab getGeneralResultsTab() {
        return generalResultsTab;
    }

    public void createGeneralResultsTab() {
        if (generalResultsTab == null) {
            VerticalLayout generalResultsPanel = new VerticalLayout();
            generalResultsPanel.setSizeFull();
            generalResultsTab = resultsTabs.addTab(generalResultsPanel, "Results", null, 0);
            resetGeneralResultsTab();
        }
    }

    public void removeGeneralResultsTab() {
        if (generalResultsTab != null) {
            Component content = ((VerticalLayout) generalResultsTab.getComponent()).getComponent(0);
            if (content instanceof CustomTabularResultLayout) {
                addResultsTab(((CustomTabularResultLayout) content).refreshWithoutSaveButton(),
                        StringUtils.abbreviate(((CustomTabularResultLayout) content).getSql(), 20),
                        generalResultsTab.getIcon(), 0);
            }
            resultsTabs.removeComponent(generalResultsTab.getComponent());
            generalResultsTab = null;
        }
    }

    public void resetGeneralResultsTab() {
        if (generalResultsTab != null) {
            replaceGeneralResultsWith(emptyResults, null);
        }
    }

    public void replaceGeneralResultsWith(Component newComponent, FontAwesome icon) {
        ((VerticalLayout) generalResultsTab.getComponent()).removeAllComponents();
        ((VerticalLayout) generalResultsTab.getComponent()).addComponent(newComponent);
        generalResultsTab.setIcon(icon);
    }

    @Override
    public void selected() {
        unselected();

        sqlArea.addSelectionChangeListener(selectionChangeListener);
        for (ShortcutListener l : shortCutListeners) {
            sqlArea.addShortcutListener(l);
        }

        setButtonsEnabled();
        sqlArea.focus();
    }

    @Override
    public void unselected() {
        sqlArea.removeSelectionChangeListener(selectionChangeListener);
        for (ShortcutListener l : shortCutListeners) {
            sqlArea.removeShortcutListener(l);
        }
    }

    protected void setButtonsEnabled() {
        buttonBar.setExecuteScriptButtonEnabled(executeScriptButtonValue);
        buttonBar.setExecuteAtCursorButtonEnabled(executeAtCursorButtonValue);
        buttonBar.setCommitButtonEnabled(commitButtonValue);
        buttonBar.setRollbackButtonEnabled(rollbackButtonValue);
    }

    protected ShortcutListener createExecuteSqlShortcutListener() {
        return new ShortcutListener("", KeyCode.ENTER, new int[] { ModifierKey.CTRL }) {

            private static final long serialVersionUID = 1L;

            @Override
            public void handleAction(Object sender, Object target) {
                if (target instanceof Table) {
                    Table table = (Table) target;
                    TabularResultLayout layout = (TabularResultLayout) table.getParent();
                    reExecute(layout.getSql());
                } else if (target instanceof AceEditor) {
                    if (executeAtCursorButtonValue) {
                        if (execute(false)
                                && !settingsProvider.get().getProperties()
                                .is(Settings.SQL_EXPLORER_AUTO_COMMIT)) {
                            setButtonsEnabled();
                        }
                    }
                }
            }
        };
    }

    protected void addToSqlHistory(String sqlStatement, Date executeTime, long executeDuration,
                                   String userId) {
        sqlStatement = sqlStatement.trim();
        Settings settings = settingsProvider.load();
        SqlHistory history = settings.getSqlHistory(sqlStatement);
        if (history == null) {
            history = new SqlHistory();
            history.setSqlStatement(sqlStatement);
            settings.addSqlHistory(history);
        }
        history.setLastExecuteDuration(executeDuration);
        history.setExecuteCount(history.getExecuteCount() + 1);
        history.setLastExecuteUserId(userId);
        history.setLastExecuteTime(executeTime);
        settingsProvider.save(settings);
    }

    protected boolean reExecute(String sql) {
        Component comp = resultsTabs.getSelectedTab();
        Tab tab = resultsTabs.getTab(comp);
        int tabPosition = resultsTabs.getTabPosition(tab);
        if (generalResultsTab != null && generalResultsTab == tab) {
            return execute(false, sql, tabPosition);
        } else {
            resultsTabs.removeTab(tab);
            return execute(false, sql, tabPosition, true);
        }
    }

    public boolean execute(final boolean runAsScript) {
        return execute(runAsScript, null, resultsTabs.getComponentCount());
    }

    public void appendSql(String sql) {
        if (isNotBlank(sql)) {
            sqlArea.setValue((isNotBlank(sqlArea.getValue()) ? sqlArea.getValue() + "\n" : "") + sql);
        }
    }

    public String getSql() {
        return sqlArea.getValue();
    }

    protected void executeSql(String sql, boolean writeToQueryWindow) {
        if (writeToQueryWindow) {
            appendSql(sql);
        }
        execute(false, sql, resultsTabs.getComponentCount());
    }

    protected boolean execute(final boolean runAsScript, String sqlText, final int tabPosition) {
        return execute(runAsScript, sqlText, tabPosition, false);
    }

    protected boolean execute(final boolean runAsScript, String sqlText, final int tabPosition, final boolean forceNewTab) {
        boolean scheduled = false;
        if (runnersInProgress == null) {
            runnersInProgress = new HashSet<CustomSqlRunner>();
        }

        if (sqlText == null) {
            if (!runAsScript) {
                sqlText = selectSqlToRun();
            } else {
                sqlText = sqlArea.getValue();
            }

            sqlText = sqlText != null ? sqlText.trim() : null;
        }

        if (StringUtils.isNotBlank(sqlText)) {

            final HorizontalLayout executingLayout = new HorizontalLayout();
            executingLayout.setMargin(true);
            executingLayout.setSizeFull();
            final Label label = new Label("Executing:\n\n" + StringUtils.abbreviate(sqlText, 250), ContentMode.PREFORMATTED);
            label.setEnabled(false);
            executingLayout.addComponent(label);
            executingLayout.setComponentAlignment(label, Alignment.TOP_LEFT);

            final String sql = sqlText;
            final Tab executingTab;
            if (!forceNewTab && generalResultsTab != null) {
                replaceGeneralResultsWith(executingLayout, FontAwesome.SPINNER);
                executingTab = null;
            } else {
                executingTab = resultsTabs.addTab(executingLayout,
                        StringUtils.abbreviate(sql, 20), FontAwesome.SPINNER, tabPosition);
            }

            if (executingTab != null) {
                executingTab.setClosable(true);
                resultsTabs.setSelectedTab(executingTab);
            }

            final CustomSqlRunner runner = new CustomSqlRunner(sql, runAsScript, user, db, settingsProvider.get(), this, generalResultsTab != null);
            runnersInProgress.add(runner);
            runner.setConnection(connection);
            runner.setListener(new CustomSqlRunner.ISqlRunnerListener() {

                private static final long serialVersionUID = 1L;

                @Override
                public void writeSql(String sql) {
                    CustomQueryPanel.this.appendSql(sql);
                }

                @Override
                public void reExecute(String sql) {
                    CustomQueryPanel.this.reExecute(sql);
                }

                public void finished(final FontAwesome icon, final List<Component> results,
                                     final long executionTimeInMs, final boolean transactionStarted,
                                     final boolean transactionEnded) {
                    VaadinSession.getCurrent().access(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                if (transactionEnded) {
                                    transactionEnded();
                                } else if (transactionStarted) {
                                    rollbackButtonValue = true;
                                    commitButtonValue = true;
                                    setButtonsEnabled();
                                    sqlArea.setStyleName("transaction-in-progress");
                                    connection = runner.getConnection();
                                }

                                addToSqlHistory(StringUtils.abbreviate(sql, 1024*8), runner.getStartTime(), executionTimeInMs, user);

                                for (Component resultComponent : results) {
                                    resultComponent.setSizeFull();

                                    if (forceNewTab || generalResultsTab == null || results.size() > 1) {
                                        if (resultComponent instanceof CustomTabularResultLayout) {
                                            resultComponent = ((CustomTabularResultLayout) resultComponent).refreshWithoutSaveButton();
                                        }
                                        addResultsTab(resultComponent, StringUtils.abbreviate(sql, 20), icon, tabPosition);
                                    } else {
                                        replaceGeneralResultsWith(resultComponent, icon);
                                        resultsTabs.setSelectedTab(generalResultsTab.getComponent());
                                    }

                                    String statusVal;
                                    if (canceled) {
                                        statusVal = "Sql canceled after " + executionTimeInMs
                                                + " ms for " + db.getName() + ".  Finished at "
                                                + SimpleDateFormat.getTimeInstance().format(new Date());
                                    } else {
                                        statusVal = "Sql executed in " + executionTimeInMs
                                                + " ms for " + db.getName() + ".  Finished at "
                                                + SimpleDateFormat.getTimeInstance().format(new Date());
                                    }
                                    status.setValue(statusVal);
                                    resultStatuses.put(resultComponent, statusVal);
                                    canceled = false;
                                }
                            } finally {
                                setButtonsEnabled();
                                if (executingTab != null) {
                                    resultsTabs.removeTab(executingTab);
                                } else if (results.size() > 1) {
                                    resetGeneralResultsTab();
                                }
                                runnersInProgress.remove(runner);
                                runner.setListener(null);
                            }
                        }
                    });

                }

            });

            final Button cancel = new Button("Cancel");
            cancel.addClickListener(new ClickListener() {
                private static final long serialVersionUID = 1L;

                @Override
                public void buttonClick(ClickEvent event) {
                    log.info("Canceling sql: "+sql);
                    label.setValue("Canceling"+label.getValue().substring(9));
                    executingLayout.removeComponent(cancel);
                    canceled = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            runner.cancel();
                        }
                    }).start();
                }
            });
            executingLayout.addComponent(cancel);

            scheduled = true;
            runner.start();

        }
        setButtonsEnabled();
        return scheduled;
    }

    public void addResultsTab(Component resultComponent, String title, Resource icon) {
        addResultsTab(resultComponent, title, icon, resultsTabs.getComponentCount());
    }

    public void addResultsTab(Component resultComponent, String title, Resource icon, int position) {
        Tab tab = resultsTabs.addTab(resultComponent, title, icon, position);

        tab.setClosable(true);

        resultsTabs.setSelectedTab(tab.getComponent());

        if (errorTab != null) {
            resultsTabs.removeTab(errorTab);
            errorTab = null;
        }

        if (maxNumberOfResultTabs > 0
                && resultsTabs.getComponentCount() > maxNumberOfResultTabs) {
            resultsTabs.removeTab(resultsTabs.getTab(resultsTabs
                    .getComponentCount() - 1));
        }

        if (icon == FontAwesome.STOP) {
            errorTab = tab;
        }
    }

    public void commit() {
        try {
            SqlRunner.commit(connection);
        } catch (Exception ex) {
            Notification.show(ex.getMessage());
        } finally {
            commitButtonValue = false;
            rollbackButtonValue = false;
            executeAtCursorButtonValue = true;
            executeScriptButtonValue = true;
            setButtonsEnabled();
            connection = null;
        }
    }

    public void transactionEnded() {
        commitButtonValue = false;
        rollbackButtonValue = false;
        executeAtCursorButtonValue = true;
        executeScriptButtonValue = true;
        setButtonsEnabled();
        connection = null;
    }

    public void rollback() {
        try {
            SqlRunner.rollback(connection);
        } catch (Exception ex) {
            Notification.show(ex.getMessage());
        } finally {
            commitButtonValue = false;
            rollbackButtonValue = false;
            executeAtCursorButtonValue = true;
            executeScriptButtonValue = true;
            setButtonsEnabled();
            connection = null;
        }
    }

    protected String selectSqlToRun() {
        String delimiter = settingsProvider.get().getProperties().get(Settings.SQL_EXPLORER_DELIMITER);
        String sql = sqlArea.getValue();
        TextRange range = sqlArea.getSelection();
        boolean selected = !range.isZeroLength();
        if (selected) {
            if (range.isBackwards()) {
                if (sql.length() > range.getStart()) {
                    sql = sql.substring(range.getEnd(), range.getStart());
                } else {
                    selected = false;
                }

            } else {
                if (sql.length() > range.getEnd()) {
                    sql = sql.substring(range.getStart(), range.getEnd());
                } else {
                    selected = false;
                }
            }
        }

        if (!selected){
            StringBuilder sqlBuffer = new StringBuilder();
            String[] lines = sql.split("\n");
            int charCount = 0;
            boolean pastCursor = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                charCount += line.length() + (i > 0 ? 1 : 0);
                if (charCount >= sqlArea.getCursorPosition()) {
                    pastCursor = true;
                }
                if (!pastCursor) {
                    if (line.trim().endsWith(delimiter) || line.trim().equals("")) {
                        sqlBuffer.setLength(0);
                    } else {
                        sqlBuffer.append(line).append("\n");
                    }
                } else if (line.trim().endsWith(delimiter)) {
                    sqlBuffer.append(line);
                    break;
                } else if (line.trim().equals("")) {
                    break;
                } else {
                    sqlBuffer.append(line).append("\n");
                }
            }
            sql = sqlBuffer.toString();
        }
        sql = sql.trim();
        if (sql.endsWith(delimiter)) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql;
    }

    public void setAutoCompleteEnabled(boolean enabled) {
        if (enabled) {
            suggester = new SqlSuggester(db);
            suggestionExtension = new SuggestionExtension(suggester);
            suggestionExtension.extend(editor);
        } else if (suggestionExtension != null) {
            suggestionExtension.remove();
            CustomQueryPanel.BlankSuggester blank = new CustomQueryPanel.BlankSuggester();
            suggestionExtension = new SuggestionExtension(blank);
            suggestionExtension.extend(editor);
        }
    }



    class DummyChangeListener implements SelectionChangeListener, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void selectionChanged(SelectionChangeEvent e) {
        }
    }

    class BlankSuggester implements Suggester {

        @Override
        public List<Suggestion> getSuggestions(String text, int cursor) {
            return new ArrayList<Suggestion>();
        }

        @Override
        public String applySuggestion(Suggestion sugg, String text, int cursor) {
            return null;
        }
    }
}

