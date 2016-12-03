package org.jumpmind.metl.core.ui.views.custom;


import com.vaadin.ui.*;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.TabSheet.Tab;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.sqlexplorer.IDb;
import org.jumpmind.vaadin.ui.sqlexplorer.IInfoPanel;
import org.jumpmind.vaadin.ui.sqlexplorer.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CustomDatabaseInfoPanel extends VerticalLayout implements IInfoPanel {

    private static final long serialVersionUID = 1L;

    Logger log = LoggerFactory.getLogger(org.jumpmind.vaadin.ui.sqlexplorer.DatabaseInfoPanel.class);

    IDb db;

    Settings settings;

    TabSheet tabSheet;

    String selectedCaption;

    public CustomDatabaseInfoPanel(IDb db, Settings settings, String selectedTabCaption) {
        this.db = db;
        this.settings = settings;

        setSizeFull();

        tabSheet = CommonUiUtils.createTabSheet();
        tabSheet.setImmediate(true);
        tabSheet.addSelectedTabChangeListener(new SelectedTabChangeListener() {

            private static final long serialVersionUID = 1L;

            @Override
            public void selectedTabChange(SelectedTabChangeEvent event) {
                selectedCaption = tabSheet.getTab(tabSheet.getSelectedTab()).getCaption();
            }
        });
        addComponent(tabSheet);

        Connection c = null;
        try {
            c = ((DataSource) db.getPlatform().getDataSource()).getConnection();
            DatabaseMetaData metaData = c.getMetaData();

            tabSheet.addTab(createTabData(createTableWithReflection(DatabaseMetaData.class, metaData)), "Meta Data");
            tabSheet.addTab(createTabData(createTableWithReflection(Connection.class, c)), "Connection");

            try{
                Table clientInfoProperties = CommonUiUtils.putResultsInTable(metaData.getClientInfoProperties(), Integer.MAX_VALUE, false);
                clientInfoProperties.setSizeFull();
                tabSheet.addTab(createTabData(clientInfoProperties), "Client Info Properties");
            } catch (Exception e) {
                log.debug("Could not create Client Info Properties tab", e);
            }

            try {
                Table catalogs = CommonUiUtils.putResultsInTable(metaData.getCatalogs(), Integer.MAX_VALUE, false);
                catalogs.setSizeFull();
                tabSheet.addTab(createTabData(catalogs), "Catalogs");
            } catch (Exception e) {
                log.debug("Could not create Catalogs tab", e);
            }

            try {
                Table schemas = CommonUiUtils.putResultsInTable(metaData.getSchemas(), Integer.MAX_VALUE, false);
                schemas.setSizeFull();
                tabSheet.addTab(createTabData(schemas), "Schemas");
            } catch (Exception e) {
                log.debug("Could not create Schemas tab", e);
            }

            try {
                Table tableTypes = CommonUiUtils.putResultsInTable(metaData.getTableTypes(), Integer.MAX_VALUE, false);
                tableTypes.setSizeFull();
                tabSheet.addTab(createTabData(tableTypes), "Table Types");
            } catch (Exception e) {
                log.debug("Could not create Table Types tab", e);
            }

            try {
                Table dataTypes = CommonUiUtils.putResultsInTable(metaData.getTypeInfo(), Integer.MAX_VALUE, false);
                dataTypes.setSizeFull();
                tabSheet.addTab(createTabData(dataTypes), "Data Types");
            } catch (Exception e) {
                log.debug("Could not create Data Types tab", e);
            }

            try {
                tabSheet.addTab(createTabData(createTableFromString(metaData.getNumericFunctions(), "Numeric Functions")), "Numeric Functions");
            } catch (Exception e) {
                log.debug("Could not create Numeric Functions tab", e);
            }

            try {
                tabSheet.addTab(createTabData(createTableFromString(metaData.getStringFunctions(), "String Functions")), "String Functions");
            } catch (Exception e) {
                log.debug("Could not create String Functions tab", e);
            }

            try {
                tabSheet.addTab(createTabData(createTableFromString(metaData.getSystemFunctions(), "System Functions")), "System Functions");
            } catch (Exception e) {
                log.debug("Could not create System Functions tab", e);
            }

            try {
                tabSheet.addTab(createTabData(createTableFromString(metaData.getTimeDateFunctions(), "Date/Time Functions")), "Date/Time Functions");
            } catch (Exception e) {
                log.debug("Could not create Date/Time Functions tab", e);
            }

            try {
                tabSheet.addTab(createTabData(createTableFromString(metaData.getSQLKeywords(), "Keywords")), "Keywords");
            } catch (Exception e) {
                log.debug("Could not create Keywords tab", e);
            }
        } catch (SQLException e) {
            log.error("",e);
        } finally {
            JdbcSqlTemplate.close(c);
        }

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

    public AbstractLayout createTabData(Table table) {
        VerticalLayout layout = new VerticalLayout();
        layout.setMargin(true);
        layout.setSizeFull();
        layout.addComponent(table);
        layout.setExpandRatio(table, 1);
        return layout;
    }

    private Table createTableWithReflection(Class<?> reflectionClass, Object instance) {
        Table table = CommonUiUtils.createTable();
        table.setSizeFull();
        table.setSortEnabled(true);
        table.setSelectable(true);
        table.setMultiSelect(true);

        table.addContainerProperty(1, String.class, null);
        table.setColumnHeader(1, "Property");
        table.setColumnWidth(1, 400);
        table.addContainerProperty(2, Object.class, null);
        table.setColumnHeader(2, "Value");

        Method[] methods = reflectionClass.getMethods();
        int rowNumber = 1;
        for (Method method : methods) {
            if ((method.getReturnType().equals(Integer.TYPE) || method.getReturnType().equals(String.class)
                    || method.getReturnType().equals(Boolean.TYPE)) && method.getParameterTypes().length == 0) {
                try {
                    Object value = method.invoke(instance);
                    Object[] row = new Object[] {cleanMethodName(method.getName()), value};
                    table.addItem(row, rowNumber);
                    rowNumber++;
                } catch (Exception e) {
                    log.debug("Could not invoke method "+method.getName(), e);
                }
            }
        }

        return table;
    }

    private String cleanMethodName(String methodName) {
        if (methodName.startsWith("get")) {
            methodName = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            methodName = methodName.substring(2);
        }

        if (!methodName.isEmpty()) {
            methodName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            for (int i=0; i<methodName.length()-1; i++) {
                if (Character.isUpperCase(methodName.charAt(i)) && (Character.isLowerCase(methodName.charAt(i+1))
                        || (i > 0 && Character.isLowerCase(methodName.charAt(i-1))))) {
                    methodName = methodName.substring(0, i) + " " + methodName.substring(i);
                    i++;
                }
            }
        }

        return methodName;
    }

    private Table createTableFromString(String data, String columnName) {
        Table table = CommonUiUtils.createTable();
        table.setSizeFull();
        table.setSortEnabled(true);
        table.setSelectable(true);
        table.setMultiSelect(true);

        List<String> values = new ArrayList<String>();
        int lastComma = 0;
        for (int i=0; i<data.length(); i++) {
            if (data.charAt(i) == ',') {
                values.add(data.substring(lastComma, i).trim());
                lastComma = i + 1;
            }
        }

        table.addContainerProperty(1, String.class, null);
        table.setColumnHeader(1, columnName);
        for (int i=0; i<values.size(); i++) {
            table.addItem(new Object[]{values.get(i)}, i);
        }

        return table;
    }

    public String getSelectedTabCaption() {
        return selectedCaption;
    }

    @Override
    public void selected() {
    }

    @Override
    public void unselected() {
    }

}
