package org.jumpmind.metl.core.ui.views.custom;

/**
 * Created by User on 2016/12/3.
 */

import com.vaadin.annotations.StyleSheet;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Resource;
import com.vaadin.ui.*;
import com.vaadin.ui.MenuBar.Command;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.TabSheet.Tab;
import com.vaadin.ui.themes.ValoTheme;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.ConfirmDialog;
import org.jumpmind.vaadin.ui.common.ConfirmDialog.IConfirmListener;
import org.jumpmind.vaadin.ui.sqlexplorer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS;

@StyleSheet({ "sqlexplorer.css" })
public class CustomSqlExplorer extends HorizontalSplitPanel {

    private static final long serialVersionUID = 1L;

    final Logger log = LoggerFactory.getLogger(getClass());

    final static FontAwesome QUERY_ICON = FontAwesome.FILE_O;

    final static float DEFAULT_SPLIT_POS = 225;

    IDbProvider databaseProvider;

    ISettingsProvider settingsProvider;

    MenuItem showButton;

    CustomDbTree dbTree;

    SqlExplorerTabPanel contentTabs;

    MenuBar contentMenuBar;

    IContentTab selected;

    float savedSplitPosition = DEFAULT_SPLIT_POS;

    String user;

    IDbMenuItem[] additionalMenuItems;

    Set<IInfoPanel> infoTabs = new HashSet<IInfoPanel>();

    public CustomSqlExplorer(String configDir, IDbProvider databaseProvider, ISettingsProvider settingsProvider, String user) {
        this(configDir, databaseProvider, settingsProvider, user, DEFAULT_SPLIT_POS);
    }

    public CustomSqlExplorer(String configDir, IDbProvider databaseProvider, String user, IDbMenuItem... additionalMenuItems) {
        this(configDir, databaseProvider, new DefaultSettingsProvider(configDir, user), user, DEFAULT_SPLIT_POS, additionalMenuItems);
    }

    public CustomSqlExplorer(String configDir, IDbProvider databaseProvider, String user, float leftSplitPos) {
        this(configDir, databaseProvider, new DefaultSettingsProvider(configDir, user), user, leftSplitPos);
    }

    public CustomSqlExplorer(String configDir, IDbProvider databaseProvider, ISettingsProvider settingsProvider, String user, float leftSplitSize, IDbMenuItem... additionalMenuItems) {
        this.databaseProvider = databaseProvider;
        this.settingsProvider = settingsProvider;
        this.savedSplitPosition = leftSplitSize;
        this.additionalMenuItems = additionalMenuItems;

        setSizeFull();
        addStyleName("sqlexplorer");

        VerticalLayout leftLayout = new VerticalLayout();
        leftLayout.setSizeFull();
        leftLayout.addStyleName(ValoTheme.MENU_ROOT);

        leftLayout.addComponent(buildLeftMenu());

        Panel scrollable = new Panel();
        scrollable.setSizeFull();

        dbTree = buildDbTree();
        scrollable.setContent(dbTree);

        leftLayout.addComponent(scrollable);
        leftLayout.setExpandRatio(scrollable, 1);

        VerticalLayout rightLayout = new VerticalLayout();
        rightLayout.setSizeFull();

        VerticalLayout rightMenuWrapper = new VerticalLayout();
        rightMenuWrapper.setWidth(100, Unit.PERCENTAGE);
        rightMenuWrapper.addStyleName(ValoTheme.MENU_ROOT);
        contentMenuBar = new MenuBar();
        contentMenuBar.addStyleName(ValoTheme.MENUBAR_BORDERLESS);
        contentMenuBar.setWidth(100, Unit.PERCENTAGE);
        addShowButton(contentMenuBar);

        rightMenuWrapper.addComponent(contentMenuBar);
        rightLayout.addComponent(rightMenuWrapper);

        contentTabs = new SqlExplorerTabPanel();
        contentTabs.addSelectedTabChangeListener(new SelectedTabChangeListener() {
            private static final long serialVersionUID = 1L;

            @Override
            public void selectedTabChange(SelectedTabChangeEvent event) {
                selectContentTab((IContentTab) contentTabs.getSelectedTab());
            }
        });
        rightLayout.addComponent(contentTabs);
        rightLayout.setExpandRatio(contentTabs, 1);

        addComponents(leftLayout, rightLayout);

        setSplitPosition(savedSplitPosition, Unit.PIXELS);
    }

    protected MenuBar buildLeftMenu() {
        MenuBar leftMenu = new MenuBar();
        leftMenu.addStyleName(ValoTheme.MENUBAR_BORDERLESS);
        leftMenu.setWidth(100, Unit.PERCENTAGE);
        MenuItem hideButton = leftMenu.addItem("", new Command() {
            private static final long serialVersionUID = 1L;

            @Override
            public void menuSelected(MenuItem selectedItem) {
                savedSplitPosition = getSplitPosition() > 10 ? getSplitPosition() : DEFAULT_SPLIT_POS;
                setSplitPosition(0);
                setLocked(true);
                showButton.setVisible(true);
            }
        });
        hideButton.setDescription("Hide the database explorer");
        hideButton.setIcon(FontAwesome.BARS);

        MenuItem refreshButton = leftMenu.addItem("", new Command() {
            private static final long serialVersionUID = 1L;

            @Override
            public void menuSelected(MenuItem selectedItem) {
                dbTree.refresh();
                Component tab = contentTabs.getSelectedTab();
                if (tab instanceof QueryPanel) {
                    if (findQueryPanelForDb(((QueryPanel) tab).getDb()).suggester != null) {
                        findQueryPanelForDb(((QueryPanel) tab).getDb()).suggester.clearCaches();
                    }
                }
            }
        });
        refreshButton.setIcon(FontAwesome.REFRESH);
        refreshButton.setDescription("Refresh the database explorer");

        MenuItem openQueryTab = leftMenu.addItem("", new Command() {
            private static final long serialVersionUID = 1L;

            @Override
            public void menuSelected(MenuItem selectedItem) {
                openQueryWindow(dbTree.getSelected());
            }
        });
        openQueryTab.setIcon(QUERY_ICON);
        openQueryTab.setDescription("Open a query tab");

        MenuItem settings = leftMenu.addItem("", new Command() {
            private static final long serialVersionUID = 1L;

            @Override
            public void menuSelected(MenuItem selectedItem) {
                CustomSettingsDialog dialog = new CustomSettingsDialog(CustomSqlExplorer.this);
                dialog.showAtSize(.5);
            }
        });
        settings.setIcon(FontAwesome.GEAR);
        settings.setDescription("Modify sql explorer settings");
        return leftMenu;
    }

    protected void addShowButton(MenuBar contentMenuBar) {
        boolean visible = showButton != null ? showButton.isVisible() : false;
        showButton = contentMenuBar.addItem("", new Command() {
            private static final long serialVersionUID = 1L;

            @Override
            public void menuSelected(MenuItem selectedItem) {
                setSplitPosition(savedSplitPosition, Unit.PIXELS);
                setLocked(false);
                showButton.setVisible(false);
            }
        });
        showButton.setIcon(FontAwesome.BARS);
        showButton.setDescription("Show the database explorer");
        showButton.setVisible(visible);
    }

    protected void selectContentTab(IContentTab tab) {
        if (selected != null) {
            selected.unselected();
        }
        contentTabs.setSelectedTab(tab);
        contentMenuBar.removeItems();
        addShowButton(contentMenuBar);
        if (tab instanceof CustomQueryPanel) {
            ((CustomButtonBar) ((CustomQueryPanel) tab).getButtonBar()).populate(contentMenuBar);
        }
        tab.selected();
        selected = tab;
    }

    protected CustomQueryPanel openQueryWindow(CustomDbTreeNode node) {
        return openQueryWindow(dbTree.getDbForNode(node));
    }

    protected CustomQueryPanel openQueryWindow(IDb db) {
        String dbName = db.getName();
        CustomButtonBar buttonBar = new CustomButtonBar();
        CustomQueryPanel panel = new CustomQueryPanel(db, settingsProvider, buttonBar, user);
        buttonBar.init(db, settingsProvider, panel, additionalMenuItems);
        Tab tab = contentTabs.addTab(panel, getTabName(dbName));
        tab.setClosable(true);
        tab.setIcon(QUERY_ICON);
        selectContentTab(panel);
        return panel;
    }

    protected void openQueryWindow(Set<CustomDbTreeNode> nodes) {
        Set<String> dbNames = new HashSet<String>();
        for (CustomDbTreeNode node : nodes) {
            IDb db = dbTree.getDbForNode(node);
            String dbName = db.getName();
            if (!dbNames.contains(dbName)) {
                dbNames.add(dbName);
                openQueryWindow(node);
            }
        }
    }

    public void refreshQueryPanels() {
        for (Component panel : contentTabs) {
            if (panel instanceof QueryPanel) {
                QueryPanel queryPanel = ((QueryPanel) panel);
                if (settingsProvider.get().getProperties().is(Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS)) {
                    queryPanel.removeGeneralResultsTab();
                } else if (!settingsProvider.get().getProperties().is(Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS)) {
                    queryPanel.createGeneralResultsTab();
                }
                boolean autoCompleteEnabled = settingsProvider.get().getProperties().is(Settings.SQL_EXPLORER_AUTO_COMPLETE);
                queryPanel.setAutoCompleteEnabled(autoCompleteEnabled);
            }
        }
    }

    public CustomQueryPanel findQueryPanelForDb(IDb db) {
        CustomQueryPanel panel = null;
        if (contentTabs.getComponentCount() > 0) {
            Component comp = contentTabs.getSelectedTab();
            if (comp instanceof QueryPanel) {
                CustomQueryPanel prospectiveQueryPanel = (CustomQueryPanel) comp;
                if (prospectiveQueryPanel.getDb().getName().equals(db.getName())) {
                    panel = prospectiveQueryPanel;
                }
            }

            if (panel == null) {
                Iterator<Component> i = contentTabs.iterator();
                while (i.hasNext()) {
                    comp = (Component) i.next();
                    if (comp instanceof CustomQueryPanel) {
                        CustomQueryPanel prospectiveQueryPanel = (CustomQueryPanel) comp;
                        if (prospectiveQueryPanel.getDb().getName().equals(db.getName())) {
                            panel = prospectiveQueryPanel;
                            break;
                        }
                    }
                }
            }

            if (panel == null) {
                panel = openQueryWindow(db);
            }
        }

        return panel;
    }

    protected void generateSelectForSelectedTables() {
        Set<CustomDbTreeNode> tableNodes = dbTree.getSelected(DbTree.NODE_TYPE_TABLE);
        for (CustomDbTreeNode treeNode : tableNodes) {
            IDb db = dbTree.getDbForNode(treeNode);
            CustomQueryPanel panel = findQueryPanelForDb(db);
            IDatabasePlatform platform = db.getPlatform();
            Table table = treeNode.getTableFor();
            DmlStatement dmlStatement = platform.createDmlStatement(DmlType.SELECT_ALL, table, null);
            panel.appendSql(dmlStatement.getSql());
            contentTabs.setSelectedTab(panel);
        }
    }

    protected void generateDmlForSelectedTables(DmlType dmlType) {
        Set<CustomDbTreeNode> tableNodes = dbTree.getSelected(DbTree.NODE_TYPE_TABLE);
        for (CustomDbTreeNode treeNode : tableNodes) {
            IDb db = dbTree.getDbForNode(treeNode);
            CustomQueryPanel panel = findQueryPanelForDb(db);
            IDatabasePlatform platform = db.getPlatform();
            Table table = treeNode.getTableFor();
            DmlStatement dmlStatement = platform.createDmlStatement(dmlType, table, null);
            Row row = new Row(table.getColumnCount());
            Column[] columns = table.getColumns();
            for (Column column : columns) {
                String value = null;
                if (column.getParsedDefaultValue() == null) {
                    value = CommonUiUtils.getJdbcTypeValue(column.getJdbcTypeName());
                } else {
                    value = column.getParsedDefaultValue().toString();
                }
                row.put(column.getName(), value);

            }
            String sql = dmlStatement.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
            panel.appendSql(sql);
            contentTabs.setSelectedTab(panel);
        }
    }

    protected void dropSelectedTables() {
        Set<CustomDbTreeNode> tableNodes = dbTree.getSelected(DbTree.NODE_TYPE_TABLE);
        List<Table> tables = new ArrayList<Table>();
        Map<Table, CustomDbTreeNode> tableToTreeNode = new HashMap<Table, CustomDbTreeNode>();
        for (CustomDbTreeNode treeNode : tableNodes) {
            Table table = treeNode.getTableFor();
            tables.add(table);
            tableToTreeNode.put(table, treeNode);
        }

        tables = Database.sortByForeignKeys(tables);
        Collections.reverse(tables);
        dropTables(tables, tableToTreeNode);
    }

    private void dropTables(final List<Table> tables, final Map<Table, CustomDbTreeNode> tableToTreeNode) {
        String msg = null;
        if (tables.size() > 1) {
            msg = "Do you want to drop " + tables.size() + " tables?";
        } else if (tables.size() == 1) {
            Table table = tables.get(0);
            msg = "Do you want to drop " + table.getFullyQualifiedTableName() + "?";
        }
        ConfirmDialog.show("Drop Tables?", msg, new IConfirmListener() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean onOk() {
                for (Table table : tables) {
                    CustomDbTreeNode treeNode = tableToTreeNode.get(table);
                    IDb db = dbTree.getDbForNode(treeNode);
                    try {
                        db.getPlatform().dropTables(false, table);
                    } catch (Exception e) {
                        String msg = "Failed to drop " + table.getFullyQualifiedTableName() + ".  ";
                        CommonUiUtils.notify(msg + "See log file for more details", Type.WARNING_MESSAGE);
                        log.warn(msg, e);
                    }
                }
                for (IContentTab panel : infoTabs) {
                    contentTabs.removeComponent(panel);
                }
                infoTabs.clear();
                dbTree.refresh();
                return true;

            }
        });
    }

    protected CustomDbTree buildDbTree() {

        final CustomDbTree tree = new CustomDbTree(databaseProvider, settingsProvider);
        tree.addValueChangeListener(new ValueChangeListener() {
            private static final long serialVersionUID = 1L;

            @Override
            public void valueChange(ValueChangeEvent event) {
                Set<CustomDbTreeNode> nodes = dbTree.getSelected();
                if (nodes != null) {
                    for (CustomDbTreeNode treeNode : nodes) {
                        IDb db = dbTree.getDbForNode(treeNode);
                        QueryPanel panel = getQueryPanelForDb(db);
                        if (panel == null && db != null) {
                            openQueryWindow(db);
                        }
                    }

                    String selectedTabCaption = null;
                    for (IInfoPanel panel : infoTabs) {
                        selectedTabCaption = panel.getSelectedTabCaption();
                        contentTabs.removeComponent(panel);
                    }
                    infoTabs.clear();

                    if (nodes.size() > 0) {
                        CustomDbTreeNode treeNode = nodes.iterator().next();

                        if (treeNode != null && treeNode.getType().equals(DbTree.NODE_TYPE_DATABASE)) {
                            try {
                                IDb db = dbTree.getDbForNode(treeNode);
                                CustomDatabaseInfoPanel databaseInfoTab = new CustomDatabaseInfoPanel(db, settingsProvider.get(), selectedTabCaption);
                                Tab tab = contentTabs.addTab(databaseInfoTab, db.getName(), FontAwesome.DATABASE, 0);
                                tab.setClosable(true);
                                selectContentTab(databaseInfoTab);
                                infoTabs.add(databaseInfoTab);
                            }catch(Exception e){
                                log.error("",e);
                            }
                        }
                        if (treeNode != null && treeNode.getType().equals(DbTree.NODE_TYPE_TABLE)) {
                            try {
                               Table table = treeNode.getTableFor();
                               if (table != null) {
                                   IDb db = dbTree.getDbForNode(treeNode);
                                   CustomTableInfoPanel tableInfoTab = new CustomTableInfoPanel(table, user, db, settingsProvider.get(), CustomSqlExplorer.this, selectedTabCaption);
                                   Tab tab = contentTabs.addTab(tableInfoTab, table.getFullyQualifiedTableName(), FontAwesome.TABLE, 0);
                                   tab.setClosable(true);
                                   selectContentTab(tableInfoTab);
                                   infoTabs.add(tableInfoTab);
                               }
                            }catch(Exception e){
                                log.error("",e);
                            }
                        } else if (treeNode != null && treeNode.getType().equals(DbTree.NODE_TYPE_TRIGGER)) {
                            try{
                                   Table table = treeNode.getParent().getTableFor();
                                   IDdlReader reader = dbTree.getDbForNode(treeNode).getPlatform().getDdlReader();
                                   Trigger trigger = reader.getTriggerFor(table, treeNode.getName());
                                   if (trigger != null) {
                                          IDb db = dbTree.getDbForNode(treeNode);
                                          TriggerInfoPanel triggerInfoTab = new TriggerInfoPanel(trigger, db, settingsProvider.get(), selectedTabCaption);
                                          Tab tab = contentTabs.addTab(triggerInfoTab, trigger.getName(), FontAwesome.CROSSHAIRS, 0);
                                          tab.setClosable(true);
                                          selectContentTab(triggerInfoTab);
                                          infoTabs.add(triggerInfoTab);
                                   }
                            }catch(Exception e){
                                log.error("",e);
                            }
                        }
                    }
                }
            }
        });
        tree.registerAction(new CustomDbTreeAction("Query", QUERY_ICON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                openQueryWindow(nodes);
            }
        }, DbTree.NODE_TYPE_DATABASE, DbTree.NODE_TYPE_CATALOG, DbTree.NODE_TYPE_SCHEMA, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Select", QUERY_ICON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                generateSelectForSelectedTables();
            }
        }, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Insert", QUERY_ICON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                generateDmlForSelectedTables(DmlType.INSERT);
            }
        }, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Update", QUERY_ICON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                generateDmlForSelectedTables(DmlType.UPDATE);
            }
        }, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Delete", QUERY_ICON) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                generateDmlForSelectedTables(DmlType.DELETE);
            }
        }, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Drop", FontAwesome.ARROW_DOWN) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                dropSelectedTables();
            }
        }, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Import", FontAwesome.DOWNLOAD) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                if (nodes.size() > 0) {
                    IDb db = dbTree.getDbForNode(nodes.iterator().next());
                    new DbImportDialog(db.getPlatform(), dbTree.getSelectedTables()).showAtSize(0.6);
                }
            }
        }, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Export", FontAwesome.UPLOAD) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                if (nodes.size() > 0) {
                    IDb db = dbTree.getDbForNode(nodes.iterator().next());
                    new CustomDbExportDialog(db.getPlatform(), dbTree.getSelectedTables(), findQueryPanelForDb(db)).showAtSize(0.6);
                }
            }
        }, DbTree.NODE_TYPE_TABLE, DbTree.NODE_TYPE_TRIGGER);

        tree.registerAction(new CustomDbTreeAction("Fill", FontAwesome.BEER) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {
                if (nodes.size() > 0) {
                    IDb db = dbTree.getDbForNode(nodes.iterator().next());
                    new CustomDbFillDialog(db.getPlatform(), dbTree.getSelectedTables(), findQueryPanelForDb(db)).showAtSize(0.6);
                }

            }
        }, DbTree.NODE_TYPE_TABLE);

        tree.registerAction(new CustomDbTreeAction("Copy Name", FontAwesome.COPY) {
            private static final long serialVersionUID = 1L;

            @Override
            public void handle(Set<CustomDbTreeNode> nodes) {

                for (CustomDbTreeNode treeNode : nodes) {
                    IDb db = dbTree.getDbForNode(nodes.iterator().next());
                    DatabaseInfo dbInfo = db.getPlatform().getDatabaseInfo();
                    final String quote = dbInfo.getDelimiterToken();
                    final String catalogSeparator = dbInfo.getCatalogSeparator();
                    final String schemaSeparator = dbInfo.getSchemaSeparator();

                    Table table = treeNode.getTableFor();
                    if (table != null) {
                        CustomQueryPanel panel = findQueryPanelForDb(db);
                        panel.appendSql(table.getQualifiedTableName(quote, catalogSeparator, schemaSeparator));
                        contentTabs.setSelectedTab(panel);
                    }
                }
            }
        }, DbTree.NODE_TYPE_TABLE);

        return tree;

    }

    protected QueryPanel getQueryPanelForDb(IDb db) {
        if (db != null) {
            Iterator<Component> i = contentTabs.iterator();
            while (i.hasNext()) {
                Component c = i.next();
                if (c instanceof QueryPanel) {
                    QueryPanel panel = (QueryPanel) c;
                    if (panel.getDb().getName().equals(db.getName())) {
                        return panel;
                    }
                }
            }
        }
        return null;
    }

    protected String getTabName(String name) {
        int tabs = contentTabs.getComponentCount();
        String tabName = tabs > 0 ? null : name;
        if (tabName == null) {
            for (int j = 0; j < 10; j++) {
                boolean alreadyUsed = false;
                String suffix = "";
                for (int i = 0; i < tabs; i++) {
                    Tab tab = contentTabs.getTab(i);
                    String currentTabName = tab.getCaption();

                    if (j > 0) {
                        suffix = "-" + j;
                    }
                    if (currentTabName.equals(name + suffix)) {
                        alreadyUsed = true;
                    }
                }

                if (!alreadyUsed) {
                    tabName = name + suffix;
                    break;
                }
            }
        }
        return tabName;
    }

    public ISettingsProvider getSettingsProvider() {
        return settingsProvider;
    }

    public IDbProvider getDatabaseProvider() {
        return databaseProvider;
    }

    public void refresh() {
        dbTree.refresh();
    }

    public void focus() {
        dbTree.focus();
    }

    public void addResultsTab(String caption, Resource icon, IContentTab panel) {
        Tab tab = contentTabs.addTab(panel, caption);
        tab.setClosable(true);
        tab.setIcon(icon);
        selectContentTab(panel);
    }

    public void putResultsInQueryTab(String value, IDb db) {
        openQueryWindow(db).appendSql(value);
    }

}
