package org.jumpmind.metl.core.ui.views.custom;

/**
 * Created by User on 2016/12/3.
 */

import com.vaadin.event.Action;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Tree;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.sqlexplorer.IDb;
import org.jumpmind.vaadin.ui.sqlexplorer.IDbProvider;
import org.jumpmind.vaadin.ui.sqlexplorer.ISettingsProvider;
import org.jumpmind.vaadin.ui.sqlexplorer.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CustomDbTree extends Tree {

    public static final String PROPERTY_SCHEMA_NAME = "schemaName";
    public static final String PROPERTY_CATALOG_NAME = "catalogName";

    private static final long serialVersionUID = 1L;

    final Logger log = LoggerFactory.getLogger(getClass());

    public final static String[] TABLE_TYPES = new String[] { "TABLE",
            "SYSTEM TABLE", "SYSTEM VIEW" };

    public static final String NODE_TYPE_DATABASE = "Database";
    public static final String NODE_TYPE_CATALOG = "Catalog";
    public static final String NODE_TYPE_SCHEMA = "Schema";
    public static final String NODE_TYPE_TABLE = "Table";
    public static final String NODE_TYPE_TRIGGER = "Trigger";

    IDbProvider databaseProvider;

    ISettingsProvider settingsProvider;

    Set<CustomDbTreeNode> expanded = new HashSet<CustomDbTreeNode>();

    Set<CustomDbTreeNode> hasBeenExpanded = new HashSet<CustomDbTreeNode>();

    Map<String, List<CustomDbTreeAction>> actionsByNodeType = new HashMap<String, List<CustomDbTreeAction>>();

    public CustomDbTree(IDbProvider databaseProvider,
                  ISettingsProvider settingsProvider) {
        this.databaseProvider = databaseProvider;
        this.settingsProvider = settingsProvider;
        setWidth(100, Unit.PERCENTAGE);
        setImmediate(true);
        setMultiSelect(true);
        setSelectable(true);
        setItemStyleGenerator(new CustomDbTree.StyleGenerator());
        CustomDbTree.Listener listener = new CustomDbTree.Listener();
        addCollapseListener(listener);
        addExpandListener(listener);
        addActionHandler(new CustomDbTree.Handler());
    }

    public void registerAction(CustomDbTreeAction action, String... nodeTypes) {
        for (String nodeType : nodeTypes) {
            List<CustomDbTreeAction> actions = actionsByNodeType.get(nodeType);
            if (actions == null) {
                actions = new ArrayList<CustomDbTreeAction>();
                actionsByNodeType.put(nodeType, actions);
            }
            actions.add(action);
        }
    }

    public void refresh() {
        hasBeenExpanded.clear();
        List<IDb> databases = databaseProvider.getDatabases();
        Set<CustomDbTreeNode> expandedItems = new HashSet<CustomDbTreeNode>(expanded);
        expanded.clear();
        Set<CustomDbTreeNode> selected = getSelected();
        removeAllItems();
        CustomDbTreeNode firstNode = null;
        for (IDb database : databases) {
            CustomDbTreeNode databaseNode = new CustomDbTreeNode(this, database.getName(), NODE_TYPE_DATABASE, FontAwesome.DATABASE, null);
            addItem(databaseNode);
            setItemIcon(databaseNode, databaseNode.getIcon());

            if (firstNode == null) {
                firstNode = databaseNode;
            }
        }

        for (CustomDbTreeNode expandedItem : expandedItems) {
            expandItem(expandedItem);
        }

        if (selected == null || selected.size() == 0) {
            selected = new HashSet<CustomDbTreeNode>();
            selected.add(firstNode);
        }
        setValue(selected);
        focus();

    }

    @SuppressWarnings("unchecked")
    public Set<CustomDbTreeNode> getSelected() {
        return (Set<CustomDbTreeNode>) getValue();
    }

    @SuppressWarnings("unchecked")
    public Set<CustomDbTreeNode> getSelected(String type) {
        HashSet<CustomDbTreeNode> nodes = new HashSet<CustomDbTreeNode>();
        Set<CustomDbTreeNode> selected = (Set<CustomDbTreeNode>) getValue();
        for (CustomDbTreeNode treeNode : selected) {
            if (treeNode.getType().equals(type)) {
                nodes.add(treeNode);
            }
        }
        return nodes;
    }

    public Set<Table> getSelectedTables() {
        Set<Table> tables = new HashSet<Table>();
        for (CustomDbTreeNode treeNode : getSelected()) {
            Table table = treeNode.getTableFor();
            if (table != null) {
                tables.add(table);
            }
        }
        return tables;
    }

    public IDb getDbForNode(CustomDbTreeNode node) {
        while (node.getParent() != null) {
            node = node.getParent();
        }
        String databaseName = node.getName();
        List<IDb> databases = databaseProvider.getDatabases();
        for (IDb database : databases) {
            if (database.getName().equals(databaseName)) {
                return database;
            }
        }
        return null;
    }

    protected void expanded(CustomDbTreeNode treeNode) {
        if (!hasBeenExpanded.contains(treeNode)) {
            hasBeenExpanded.add(treeNode);

            try {
                IDatabasePlatform platform = getDbForNode(treeNode).getPlatform();
                IDdlReader reader = platform.getDdlReader();

                Collection<?> children = getChildren(treeNode);
                if (children == null || children.size() == 0) {
                    if (treeNode.getType().equals(NODE_TYPE_DATABASE)) {
                        List<CustomDbTreeNode> nextLevel = new ArrayList<CustomDbTreeNode>();
                        List<String> catalogs = reader.getCatalogNames();
                        Collections.sort(catalogs);
                        if (catalogs.size() > 0) {
                            if (catalogs.remove(platform.getDefaultCatalog())) {
                                catalogs.add(0, platform.getDefaultCatalog());
                            }
                            for (String catalog : catalogs) {
                                CustomDbTreeNode catalogNode = new CustomDbTreeNode(this, catalog, NODE_TYPE_CATALOG, FontAwesome.BOOK, treeNode);
                                nextLevel.add(catalogNode);
                            }
                        } else {
                            List<String> schemas = reader.getSchemaNames(null);
                            Collections.sort(schemas);
                            if (schemas.remove(platform.getDefaultSchema())) {
                                schemas.add(0, platform.getDefaultSchema());
                            }
                            for (String schema : schemas) {
                                CustomDbTreeNode schemaNode = new CustomDbTreeNode(this, schema, NODE_TYPE_SCHEMA, FontAwesome.BOOK, treeNode);
                                nextLevel.add(schemaNode);
                            }
                        }

                        if (nextLevel.size() == 0) {
                            nextLevel.addAll(getTableTreeNodes(reader, treeNode, null, null));
                        }

                        treeNode.getChildren().addAll(nextLevel);
                        for (CustomDbTreeNode node : nextLevel) {
                            addTreeNode(node);
                        }
                    } else if (treeNode.getType().equals(NODE_TYPE_CATALOG)) {
                        List<String> schemas = reader.getSchemaNames(treeNode.getName());
                        Collections.sort(schemas);
                        if (schemas.size() > 0) {
                            if (schemas.remove(platform.getDefaultSchema())) {
                                schemas.add(0, platform.getDefaultSchema());
                            }
                            for (String schema : schemas) {
                                CustomDbTreeNode schemaNode = new CustomDbTreeNode(this, schema, NODE_TYPE_SCHEMA, FontAwesome.BOOK, treeNode);
                                treeNode.getChildren().add(schemaNode);
                                addTreeNode(schemaNode);
                            }
                        } else {
                            addTableNodes(reader, treeNode, treeNode.getName(), null);
                        }

                    } else if (treeNode.getType().equals(NODE_TYPE_SCHEMA)) {
                        String catalogName = null;
                        CustomDbTreeNode parent = (CustomDbTreeNode) getParent(treeNode);
                        if (parent != null && parent.getType().equals(NODE_TYPE_CATALOG)) {
                            catalogName = parent.getName();
                        }
                        addTableNodes(reader, treeNode, catalogName,
                                treeNode.getName());
                    } else if (treeNode.getType().equals(NODE_TYPE_TABLE)) {
                        String catalogName = null, schemaName = null;
                        CustomDbTreeNode parent = (CustomDbTreeNode) getParent(treeNode);
                        if (parent != null && parent.getType().equals(NODE_TYPE_SCHEMA)) {
                            schemaName = parent.getName();
                            CustomDbTreeNode grandparent = (CustomDbTreeNode) getParent(parent);
                            if (grandparent != null && grandparent.getType().equals(NODE_TYPE_CATALOG)) {
                                catalogName = grandparent.getName();
                            }
                        } else if (parent != null && parent.getType().equals(NODE_TYPE_CATALOG)) {
                            catalogName = parent.getName();
                        }
                        addTriggerNodes(reader, treeNode, catalogName, schemaName);
                    }

                    setChildrenAllowed(treeNode,
                            treeNode.getChildren().size() > 0);
                }
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
                CommonUiUtils.notify(ex);
            }
        }
    }

    protected void addTreeNode(CustomDbTreeNode node) {
        addItem(node);
        setParent(node, node.getParent());
        setItemIcon(node, node.getIcon());
        setChildrenAllowed(node, !node.getType().equals(NODE_TYPE_TRIGGER));
    }

    protected List<CustomDbTreeNode> getTableTreeNodes(IDdlReader reader,
                                                 CustomDbTreeNode parent, String catalogName, String schemaName) {
        List<CustomDbTreeNode> list = new ArrayList<CustomDbTreeNode>();
        List<String> tables = reader.getTableNames(catalogName, schemaName, TABLE_TYPES);
        Collections.sort(tables, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.toUpperCase().compareTo(o2.toUpperCase());
            }
        });

        for (String tableName : tables) {
            String excludeRegex = settingsProvider.get().getProperties().get(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX);
            if (!tableName.matches(excludeRegex)
                    && !tableName.toUpperCase().matches(excludeRegex)
                    && !tableName.toLowerCase().matches(excludeRegex)) {

                CustomDbTreeNode treeNode = new CustomDbTreeNode(this, tableName,
                        NODE_TYPE_TABLE, FontAwesome.TABLE, parent);
                if (catalogName != null) {
                    treeNode.getProperties().setProperty(PROPERTY_CATALOG_NAME, catalogName);
                }
                if (schemaName != null) {
                    treeNode.getProperties().setProperty(PROPERTY_SCHEMA_NAME, schemaName);
                }

                list.add(treeNode);
            }
        }
        return list;
    }

    protected void addTableNodes(IDdlReader reader, CustomDbTreeNode parent,
                                 String catalogName, String schemaName) {
        List<CustomDbTreeNode> nodes = getTableTreeNodes(reader, parent, catalogName, schemaName);
        for (CustomDbTreeNode treeNode : nodes) {
            parent.getChildren().add(treeNode);
            addTreeNode(treeNode);
        }
    }

    protected List<CustomDbTreeNode> getTriggerTreeNodes(IDdlReader reader,
                                                         CustomDbTreeNode parent, String catalogName, String schemaName) {
        List<CustomDbTreeNode> list = new ArrayList<CustomDbTreeNode>();
        List<Trigger> triggers = reader.getTriggers(catalogName, schemaName, parent.getName());
        for (Trigger trigger : triggers) {
            CustomDbTreeNode treeNode = new CustomDbTreeNode(this, trigger.getName(), NODE_TYPE_TRIGGER, FontAwesome.CROSSHAIRS, parent);
            if (catalogName != null) {
                treeNode.getProperties().setProperty(PROPERTY_CATALOG_NAME, catalogName);
            }
            if (schemaName != null) {
                treeNode.getProperties().setProperty(PROPERTY_SCHEMA_NAME, schemaName);
            }
            list.add(treeNode);
        }
        return list;
    }

    protected void addTriggerNodes(IDdlReader reader, CustomDbTreeNode parent, String catalogName, String schemaName) {
        List<CustomDbTreeNode> nodes = getTriggerTreeNodes(reader, parent, catalogName, schemaName);
        for (CustomDbTreeNode treeNode : nodes) {
            parent.getChildren().add(treeNode);
            addTreeNode(treeNode);
        }
    }

    class Listener implements CollapseListener, ExpandListener {

        private static final long serialVersionUID = 1L;

        @Override
        public void nodeCollapse(CollapseEvent event) {
            expanded.remove(event.getItemId());
        }

        @Override
        public void nodeExpand(ExpandEvent event) {
            CustomDbTreeNode node = (CustomDbTreeNode) event.getItemId();
            expanded.add(node);
            expanded(node);
        }

    }

    class StyleGenerator implements ItemStyleGenerator {
        private static final long serialVersionUID = 1L;

        public String getStyle(Tree source, Object itemId) {
            if (itemId instanceof CustomDbTreeNode) {
                try {
                    CustomDbTreeNode node = (CustomDbTreeNode) itemId;
                    if (node.getType().equals(NODE_TYPE_CATALOG)) {
                        IDatabasePlatform platform = getDbForNode(node).getPlatform();
                        String catalog = platform.getDefaultCatalog();
                        if (catalog != null && catalog.equals(node.getName())) {
                            return "bold";
                        }
                    } else if (node.getType().equals(NODE_TYPE_SCHEMA)) {
                        IDatabasePlatform platform = getDbForNode(node).getPlatform();
                        String schema = platform.getDefaultSchema();
                        if (schema != null && schema.equals(node.getName())) {
                            return "bold";
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to see if this node is the default catalog and/or schema", e);
                }
            }
            return null;

        }
    }

    class Handler implements com.vaadin.event.Action.Handler {

        private static final long serialVersionUID = 1L;

        @Override
        public Action[] getActions(Object target, Object sender) {
            if (target instanceof CustomDbTreeNode) {
                CustomDbTreeNode treeNode = (CustomDbTreeNode) target;
                List<CustomDbTreeAction> actions = actionsByNodeType.get(treeNode.getType());
                if (actions != null) {
                    return actions.toArray(new Action[actions.size()]);
                }
            }
            return new Action[0];

        }

        @Override
        public void handleAction(Action action, Object sender, Object target) {
            if (action instanceof CustomDbTreeAction) {
                if (!getSelected().contains(target)) {
                    select(target);
                }
                CustomDbTreeNode node = (CustomDbTreeNode) target;
                ((CustomDbTreeAction) action).handle(getSelected(node.getType()));
            }
        }
    }

}
