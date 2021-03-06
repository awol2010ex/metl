package org.jumpmind.metl.core.ui.views.custom;


import com.vaadin.server.FontAwesome;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.*;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.TabSheet.Tab;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.symmetric.io.data.DbExport;
import org.jumpmind.symmetric.io.data.DbExport.Format;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.sqlexplorer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.aceeditor.AceEditor;
import org.vaadin.aceeditor.AceMode;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class CustomTableInfoPanel extends VerticalLayout implements IInfoPanel {

    protected static final Logger log = LoggerFactory.getLogger(CustomTableInfoPanel.class);

    private static final long serialVersionUID = 1L;

    CustomSqlExplorer explorer;

    TabSheet tabSheet;

    String selectedCaption;

    public CustomTableInfoPanel(final org.jumpmind.db.model.Table table, final String user, final IDb db,
                          final Settings settings, String selectedTabCaption) {
        this(table, user, db, settings, null, selectedTabCaption);
    }

    public CustomTableInfoPanel(final org.jumpmind.db.model.Table table, final String user, final IDb db,
                          final Settings settings, CustomSqlExplorer explorer, String selectedTabCaption) {
        this.explorer = explorer;

        setSizeFull();

        tabSheet = CommonUiUtils.createTabSheet();
        tabSheet.setImmediate(true);
        tabSheet.addSelectedTabChangeListener(new SelectedTabChangeListener() {

            private static final long serialVersionUID = 1L;

            @Override
            public void selectedTabChange(SelectedTabChangeEvent event) {
                selectedCaption = tabSheet.getTab(tabSheet.getSelectedTab()).getCaption();

                if (tabSheet.getSelectedTab() instanceof AbstractLayout) {
                    AbstractLayout layout = (AbstractLayout) tabSheet.getSelectedTab();
                    if (selectedCaption.equals("Data") && layout.getData() != null && layout.getData().equals(true)) {
                        refreshData(table, user, db, settings, false);
                    } else if (layout.getData() != null && layout.getData() instanceof AbstractMetaDataTableCreator) {
                        populate((VerticalLayout) layout);
                    }
                } else if (tabSheet.getSelectedTab() instanceof AceEditor &&
                        ((AceEditor) tabSheet.getSelectedTab()).getData().equals(true)) {
                    populateSource(table, db, (AceEditor) tabSheet.getSelectedTab());
                }
            }
        });
        addComponent(tabSheet);

        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) db.getPlatform().getSqlTemplate();

        tabSheet.addTab(create(new ColumnMetaDataTableCreator(sqlTemplate, table, settings)),
                "Columns");
        tabSheet.addTab(create(new PrimaryKeyMetaDataTableCreator(sqlTemplate, table, settings)),
                "Primary Keys");
        tabSheet.addTab(create(new IndexMetaDataTableCreator(sqlTemplate, table, settings)),
                "Indexes");
        if (db.getPlatform().getDatabaseInfo().isForeignKeysSupported()) {
            tabSheet.addTab(create(new ImportedKeysMetaDataTableCreator(sqlTemplate, table,
                    settings)), "Imported Keys");
            tabSheet.addTab(create(new ExportedKeysMetaDataTableCreator(sqlTemplate, table,
                    settings)), "Exported Keys");
        }

        refreshData(table, user, db, settings, true);

        AceEditor editor = new AceEditor();
        editor.setData(true);
        tabSheet.addTab(editor, "Source");

        Iterator<Component> i = tabSheet.iterator();
        while (i.hasNext()) {
            Component component = i.next();
            Tab tab = tabSheet.getTab(component);
            if (tab.getCaption().equals(selectedTabCaption)) {
                tabSheet.setSelectedTab(component);
                break;
            }
        }

    }

    public String getSelectedTabCaption() {
        return selectedCaption;
    }

    protected void refreshData(final org.jumpmind.db.model.Table table, final String user, final IDb db,
                               final Settings settings, boolean isInit) {

        if (!isInit) {
            tabSheet.removeTab(tabSheet.getTab(1));
        }

        IDatabasePlatform platform = db.getPlatform();
        DmlStatement dml = platform.createDmlStatement(DmlType.SELECT_ALL, table, null);

        final HorizontalLayout executingLayout = new HorizontalLayout();
        executingLayout.setSizeFull();
        final ProgressBar p = new ProgressBar();
        p.setIndeterminate(true);
        executingLayout.addComponent(p);
        executingLayout.setData(isInit);
        tabSheet.addTab(executingLayout, "Data", isInit ? null : FontAwesome.SPINNER, 1);
        if (!isInit) {
            tabSheet.setSelectedTab(executingLayout);
        }

        CustomSqlRunner runner = new CustomSqlRunner(dml.getSql(), false, user, db, settings, explorer,
                new CustomSqlRunner.ISqlRunnerListener() {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public void writeSql(String sql) {
                        explorer.openQueryWindow(db).appendSql(sql);
                    }

                    @Override
                    public void reExecute(String sql) {
                        refreshData(table, user, db, settings, false);
                    }

                    @Override
                    public void finished(final FontAwesome icon, final List<Component> results,
                                         long executionTimeInMs, boolean transactionStarted,
                                         boolean transactionEnded) {
                        VaadinSession.getCurrent().access(new Runnable() {

                            @Override
                            public void run() {
                                tabSheet.removeComponent(executingLayout);
                                VerticalLayout layout = new VerticalLayout();
                                layout.setMargin(true);
                                layout.setSizeFull();
                                if (results.size() > 0) {
                                    layout.addComponent(results.get(0));
                                }
                                tabSheet.addTab(layout, "Data", null, 1);
                                tabSheet.setSelectedTab(layout);
                            }
                        });
                    }
                });
        runner.setShowSqlOnResults(false);
        runner.setLogAtDebug(true);
        if (!isInit) {
            runner.start();
        }

    }

    protected AbstractLayout create(AbstractMetaDataTableCreator creator) {
        VerticalLayout layout = new VerticalLayout();
        layout.setMargin(true);
        layout.setSizeFull();
        layout.setData(creator);
        return layout;
    }

    protected void populate(VerticalLayout layout) {
        AbstractMetaDataTableCreator creator = (AbstractMetaDataTableCreator) layout.getData();
        Table table = creator.create();
        layout.addComponent(table);
        layout.setExpandRatio(table, 1);
        layout.setData(null);
    }

    protected void populateSource(org.jumpmind.db.model.Table table, IDb db, AceEditor oldTab) {
        try {
            tabSheet.removeTab(tabSheet.getTab(oldTab));
            DbExport export = new DbExport(db.getPlatform());
            export.setNoCreateInfo(false);
            export.setNoData(true);
            export.setCatalog(table.getCatalog());
            export.setSchema(table.getSchema());
            export.setFormat(Format.SQL);
            AceEditor editor = CommonUiUtils.createAceEditor();
            editor.setMode(AceMode.sql);
            editor.setValue(export.exportTables(new org.jumpmind.db.model.Table[] { table }));
            editor.setData(false);
            tabSheet.addTab(editor, "Source");
            tabSheet.setSelectedTab(editor);
        } catch (IOException e) {
            log.warn("Failed to export the create information", e);
        }
    }

    @Override
    public void selected() {
    }

    @Override
    public void unselected() {
    }
}

