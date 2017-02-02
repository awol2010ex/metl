package org.jumpmind.metl.core.persist;

import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.Project;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.metl.core.model.AuditEvent;
import org.jumpmind.metl.core.model.Flow;
import org.jumpmind.metl.core.model.FlowName;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelName;
import org.jumpmind.metl.core.model.ProjectVersion;
import org.jumpmind.metl.core.model.ReleasePackage;
import org.jumpmind.metl.core.model.ReleasePackageProjectVersion;
import org.jumpmind.metl.core.model.Resource;
import org.jumpmind.metl.core.model.ResourceName;
import org.jumpmind.metl.core.security.ISecurityService;
import org.jumpmind.metl.core.security.SecurityConstants;
import org.jumpmind.metl.core.util.GeneralUtils;
import org.jumpmind.metl.core.util.MessageException;
import org.jumpmind.metl.core.util.VersionUtils;
import org.jumpmind.persist.IPersistenceManager;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.LinkedCaseInsensitiveMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ImportExportService extends AbstractService implements IImportExportService {
    
    final static Integer TABLE = new Integer(0);
    final static Integer SQL = new Integer(1);
    final static Integer KEY_COLUMNS = new Integer(2);
    
    final static Integer PROJECT_IDX = new Integer(0);
    final static Integer PROJECT_VERSION_IDX = new Integer(1);
    final static Integer MODEL_IDX = new Integer(0);
    final static Integer RESOURCE_IDX = new Integer(0);
    final static Integer FLOW_IDX = new Integer(4);
    
    final static Integer CREATE_TIME_IDX = new Integer(0);
    final static Integer LAST_UPDATE_TIME_IDX = new Integer(1);
    final static Integer CREATE_BY_IDX = new Integer(2);
    final static Integer LAST_UPDATE_BY_IDX = new Integer(3);

    final String[][] RELEASE_PACKAGE_SQL = {
            {"_RELEASE_PACKAGE","SELECT * FROM %1$s_RELEASE_PACKAGE WHERE ID='%2$s' ORDER BY ID", "ID"},
            {"_RELEASE_PACKAGE_PROJECT_VERSION","SELECT * FROM %1$s_RELEASE_PACKAGE_PROJECT_VERSION WHERE " +
                    "RELEASE_PACKAGE_ID='%2$s' ORDER BY RELEASE_PACKAGE_ID", "RELEASE_PACKAGE_ID, PROJECT_VERSION_ID"}
    };

    final String[][] PROJECT_SQL = {
            {"_PROJECT","SELECT * FROM %1$s_PROJECT WHERE ID IN (SELECT PROJECT_ID FROM %1$s_PROJECT_VERSION WHERE ID='%2$s') UNION SELECT * FROM %1$s_PROJECT WHERE ID='%3$s' ORDER BY ID","ID"},
            {"_PROJECT_VERSION","SELECT * FROM %1$s_PROJECT_VERSION WHERE ID='%2$s' ORDER BY ID","ID"},
            {"_PROJECT_VERSION_DEFINITION_PLUGIN","SELECT * FROM %1$s_PROJECT_VERSION_DEFINITION_PLUGIN WHERE PROJECT_VERSION_ID='%2$s' ORDER BY PROJECT_VERSION_ID","PROJECT_VERSION_ID,COMPONENT_TYPE_ID"},           
            {"_PROJECT_VERSION_DEPENDENCY","SELECT * FROM %1$s_PROJECT_VERSION_DEPENDENCY WHERE PROJECT_VERSION_ID='%2$s' ORDER BY ID","ID"}
    };
    
    final String[][] MODEL_SQL = {
            {"_MODEL","SELECT * FROM %1$s_MODEL WHERE PROJECT_VERSION_ID='%2$s' AND ID='%3$s' ORDER BY ID","ID"},
            {"_MODEL_ENTITY","SELECT * FROM %1$s_MODEL_ENTITY WHERE MODEL_ID='%3$s' ORDER BY ID","ID"},
            {"_MODEL_ATTRIBUTE","SELECT * FROM %1$s_MODEL_ATTRIBUTE WHERE ENTITY_ID IN "
            + "(SELECT ID FROM %1$s_MODEL_ENTITY WHERE MODEL_ID IN "
            + "(SELECT ID FROM %1$s_MODEL WHERE PROJECT_VERSION_ID='%2$s' AND ID='%3$s')) ORDER BY ID","ID"}
    };    
    
    final String[][] RESOURCE_SQL = {
            {"_RESOURCE","SELECT * FROM %1$s_RESOURCE WHERE PROJECT_VERSION_ID = '%2$s' AND ID='%3$s' ORDER BY ID","ID"},
            {"_RESOURCE_SETTING","SELECT * FROM %1$s_RESOURCE_SETTING WHERE RESOURCE_ID='%3$s' ORDER BY RESOURCE_ID, NAME","RESOURCE_ID,NAME"}
    };
    
    final String[][] FLOW_SQL = {
            {"_COMPONENT","SELECT * FROM %1$s_COMPONENT WHERE PROJECT_VERSION_ID='%2$s' AND ID IN "
                    + "(SELECT DISTINCT COMPONENT_ID FROM %1$s_FLOW_STEP WHERE FLOW_ID='%3$s') ORDER BY ID", "ID"},
            {"_COMPONENT_SETTING","SELECT * FROM %1$s_COMPONENT_SETTING WHERE COMPONENT_ID IN "
                    + "(SELECT DISTINCT COMPONENT_ID FROM %1$s_FLOW_STEP WHERE FLOW_ID='%3$s') ORDER BY ID", "ID"},
            {"_COMPONENT_ENTITY_SETTING","SELECT * FROM %1$s_COMPONENT_ENTITY_SETTING WHERE COMPONENT_ID IN "
                    + "(SELECT DISTINCT COMPONENT_ID FROM %1$s_FLOW_STEP WHERE FLOW_ID='%3$s') ORDER BY ID", "ID"},
            {"_COMPONENT_ATTRIBUTE_SETTING","SELECT * FROM %1$s_COMPONENT_ATTRIBUTE_SETTING WHERE COMPONENT_ID IN "
                    + "(SELECT DISTINCT COMPONENT_ID FROM %1$s_FLOW_STEP WHERE FLOW_ID='%3$s') ORDER BY ID", "ID"},
            {"_FLOW","SELECT * FROM %1$s_FLOW WHERE PROJECT_VERSION_ID='%2$s' AND ID='%3$s' ORDER BY ID", "ID"},
            {"_FLOW_PARAMETER","SELECT * FROM %1$s_FLOW_PARAMETER WHERE FLOW_ID='%3$s' ORDER BY ID", "ID"},
            {"_FLOW_STEP","SELECT * FROM %1$s_FLOW_STEP WHERE FLOW_ID='%3$s' ORDER BY ID", "ID"},
            {"_FLOW_STEP_LINK","SELECT * FROM %1$s_FLOW_STEP_LINK WHERE SOURCE_STEP_ID IN "
                    + "(SELECT DISTINCT ID FROM %1$s_FLOW_STEP WHERE FLOW_ID='%3$s') ORDER BY SOURCE_STEP_ID, TARGET_STEP_ID", "SOURCE_STEP_ID,TARGET_STEP_ID"}            
    };
    
    private IDatabasePlatform databasePlatform;
    private IConfigurationService configurationService;
    private ISecurityService securityService;
    private String tablePrefix;
    private String[] columnsToExclude;
    private Set<String> importsToAudit = new HashSet<>();

    public ImportExportService(IDatabasePlatform databasePlatform,
            IPersistenceManager persistenceManager, String tablePrefix,
            IConfigurationService configurationService, ISecurityService securityService) {
        super(persistenceManager, tablePrefix);
        this.securityService = securityService;
        this.databasePlatform = databasePlatform;
        this.configurationService = configurationService;
        this.tablePrefix = tablePrefix;
        importsToAudit.add(tableName(Project.class).toUpperCase());
        importsToAudit.add(tableName(ProjectVersion.class).toUpperCase());
        importsToAudit.add(tableName(Flow.class).toUpperCase());
        importsToAudit.add(tableName(Model.class).toUpperCase());
        importsToAudit.add(tableName(Resource.class).toUpperCase());
        setColumnsToExclude();
    }

    private void setColumnsToExclude() {
        columnsToExclude = new String[4];
        columnsToExclude[CREATE_TIME_IDX] = "CREATE_TIME";
        columnsToExclude[LAST_UPDATE_TIME_IDX] = "LAST_UPDATE_TIME";
        columnsToExclude[CREATE_BY_IDX] = "CREATE_BY";
        columnsToExclude[LAST_UPDATE_BY_IDX] = "LAST_UPDATE_BY";
    }
    
    @Override
    public String exportProjectVersion(String projectVersionId, String userId) {
        List<FlowName> flows = new ArrayList<>();
        flows.addAll(configurationService.findFlowsInProject(projectVersionId, true));
        flows.addAll(configurationService.findFlowsInProject(projectVersionId, false));
        List<String> flowIds = new ArrayList<>();
        for (FlowName flowName : flows) {
            flowIds.add(flowName.getId());
        }

        List<ModelName> models = configurationService.findModelsInProject(projectVersionId);
        List<String> modelIds = new ArrayList<>();
        for (ModelName modelName : models) {
            modelIds.add(modelName.getId());
        }

        List<ResourceName> resources = configurationService
                .findResourcesInProject(projectVersionId);
        List<String> resourceIds = new ArrayList<>();
        for (ResourceName resource : resources) {
            resourceIds.add(resource.getId());
        }
        return exportFlows(projectVersionId, flowIds, modelIds, resourceIds, userId);
    }

    @Override
    public String exportReleasePackage(String releasePackageId, String userId) {        
        
        Set<FlowName> flows = new HashSet<FlowName>();
        Set<ModelName> models = new HashSet<ModelName>();
        Set<ResourceName> resources = new HashSet<ResourceName>();
        ConfigData exportData = initExport();
        
        ReleasePackage releasePackage = configurationService.findReleasePackage(releasePackageId);
        addConfigData(exportData.getReleasePackageData(), RELEASE_PACKAGE_SQL, releasePackageId, releasePackageId);
        for (ReleasePackageProjectVersion rppv : releasePackage.getProjectVersions()) {
            flows.addAll(configurationService.findFlowsInProject(rppv.getProjectVersionId(), false));
            models.addAll(configurationService.findModelsInProject(rppv.getProjectVersionId()));
            resources.addAll(configurationService.findResourcesInProject(rppv.getProjectVersionId()));            
            addConfigData(exportData.getProjectData(), PROJECT_SQL, rppv.getProjectVersionId(), null);
        }        
        for (FlowName flow : flows) {
            addConfigData(exportData.getFlowData(), FLOW_SQL, flow.getProjectVersionId(), flow.getId());
        }
        for (ModelName model : models) {
            addConfigData(exportData.getModelData(), MODEL_SQL, model.getProjectVersionId(), model.getId());
        }
        for (ResourceName resource : resources) {
            addConfigData(exportData.getResourceData(), RESOURCE_SQL, resource.getProjectVersionId(), resource.getId());
        }        
        save(new AuditEvent(AuditEvent.EventType.EXPORT, String.format("%s, flows: %d, models %d, resources: %d", 
                releasePackage.getName(), flows.size(), models.size(), resources.size()), userId));
        return serializeExportToJson(exportData);
    }

    @Override
    public String exportFlows(String projectVersionId, List<String> flowIds, List<String> modelIds,
            List<String> resourceIds, String userId) {     
        
        ProjectVersion version = configurationService.findProjectVersion(projectVersionId);
        save(new AuditEvent(AuditEvent.EventType.EXPORT, String.format("%s, flows: %d, models %d, resources: %d", 
                version.getName(), flowIds.size(), modelIds.size(), resourceIds.size()), userId));

        ConfigData exportData = initExport();
        addConfigData(exportData.getProjectData(), PROJECT_SQL, projectVersionId, null);
        for (String flowId : flowIds) {
            addConfigData(exportData.getFlowData(), FLOW_SQL, projectVersionId, flowId);
        }
        for (String modelId : modelIds) {
            addConfigData(exportData.getModelData(), MODEL_SQL, projectVersionId, modelId);
        }
        for (String resourceId : resourceIds) {
            addConfigData(exportData.getResourceData(), RESOURCE_SQL, projectVersionId, resourceId);
        }
        return serializeExportToJson(exportData);
    }

    protected ConfigData initExport() {
        ConfigData exportData = new ConfigData();
        exportData.setHostName(AppUtils.getHostName());
        exportData.setVersionNumber(VersionUtils.getCurrentVersion());

        initConfigData(exportData.getReleasePackageData(), RELEASE_PACKAGE_SQL);
        initConfigData(exportData.getProjectData(), PROJECT_SQL);
        initConfigData(exportData.getModelData(), MODEL_SQL);
        initConfigData(exportData.getResourceData(), RESOURCE_SQL);
        initConfigData(exportData.getFlowData(), FLOW_SQL);

        return exportData;        
    }
    
    @Override
    public void importConfiguration(String configDataString, String userId) {
        ConfigData configData = deserializeConfigurationData(configDataString);
        importConfiguration(configData, userId);
    }

    private String serializeExportToJson(ConfigData exportData) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String outData;

        try {
            outData = mapper.writeValueAsString(exportData);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
            throw new UnsupportedOperationException("Error processing export to json");
        }
        return outData;
    }

    private void addConfigData(List<TableData> tableData, String[][] sqlElements,
            String projectVersionId, String keyValue) {
        for (int i = 0; i <= sqlElements.length - 1; i++) {
            String[] entry = sqlElements[i];

            List<Row> rows = getConfigTableData(String.format(entry[SQL], 
                    tablePrefix, projectVersionId, keyValue));
            
            for (Row row : rows) {
                if (isPassword(row.getString("NAME", false))) {
                    String value = row.getString("VALUE", false);
                    if (isNotBlank(value)) {
                        if (value.startsWith(SecurityConstants.PREFIX_ENC)) {
                            try {
                                row.put("VALUE", securityService.decrypt(
                                        value.substring(SecurityConstants.PREFIX_ENC.length() - 1)));
                            } catch (Exception e) {
                            }
                        }
                    }
                }
                tableData.get(i).rows.put(getPkDataAsString(row, entry[KEY_COLUMNS]), row);
            }
        }
    }

    private List<Row> getConfigTableData(String sql) {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> rows = template.query(sql);
        excludeColumnData(rows);

        return rows;
    }

    private void excludeColumnData(List<Row> rows) {
        for (Row row : rows) {
            for (int i = 0; i < columnsToExclude.length; i++) {
                row.remove(columnsToExclude[i]);
            }
        }
    }

    private void importConfiguration(ConfigData configData, String userId) {
        ImportConfigData importData = new ImportConfigData(configData);
        ISqlTransaction transaction = databasePlatform.getSqlTemplate().startSqlTransaction();
        try {

            if (importData.getProjectData().size() > 0
                    && importData.getProjectData().get(PROJECT_IDX).rows.size() > 0) {
                importProjectConfiguration(importData, transaction, userId);
            }
            if (importData.getResourceData().size() > 0
                    && importData.getResourceData().get(RESOURCE_IDX).rows.size() > 0) {
                importResourceConfiguration(importData, transaction, userId);
            }
            if (importData.getModelData().size() > 0
                    && importData.getModelData().get(MODEL_IDX).rows.size() > 0) {
                importModelConfiguration(importData, transaction, userId);
            }
            if (importData.getFlowData().size() > 0
                    && importData.getFlowData().get(FLOW_IDX).rows.size() > 0) {
                importFlowConfiguration(importData, transaction, userId);
            }
            processDeletes(importData, transaction);

            transaction.commit();

        } catch (Exception e) {
            String msg =  String.format("Failed to import from host: %s from Metl version: %s",
                    configData.getHostName(), configData.getVersionNumber());
            transaction.rollback();
            save(new AuditEvent(AuditEvent.EventType.IMPORT, msg, userId));
            rethrow(e);
        }

    }

    private void processDeletes(ImportConfigData importData, ISqlTransaction transaction) {
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_FLOW_STEP_LINK"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_FLOW_STEP"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_FLOW_PARAMETER"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_FLOW"), transaction);
        processTableDeletes(
                importData.deletesToProcess.get(tablePrefix + "_COMPONENT_ATTRIBUTE_SETTING"),
                transaction);
        processTableDeletes(
                importData.deletesToProcess.get(tablePrefix + "_COMPONENT_ENTITY_SETTING"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_COMPONENT_SETTING"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_COMPONENT"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_RESOURCE_SETTING"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_RESOURCE"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_MODEL_ATTRIBUTE"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_MODEL_ENTITY"),
                transaction);
        processTableDeletes(importData.deletesToProcess.get(tablePrefix + "_MODEL"), transaction);
    }

    private void importProjectConfiguration(ImportConfigData importData,
            ISqlTransaction transaction, String userId) {        
        String projectVersionId = (String)getOneRow(importData.getProjectData().get(PROJECT_VERSION_IDX)).get("ID");
        List<TableData> existingProjectData = new ArrayList<TableData>();
        initConfigData(existingProjectData, PROJECT_SQL);
        
        Iterator<String> itr = importData.getProjectData().get(PROJECT_IDX)
                .getTableData().keySet().iterator();
        while (itr.hasNext()) {
            String key = itr.next();
            LinkedCaseInsensitiveMap<Object> row = importData.getProjectData().get(PROJECT_IDX).getTableData().get(key);
            addConfigData(existingProjectData, PROJECT_SQL, projectVersionId,
                    (String) row.get(PROJECT_SQL[PROJECT_IDX][KEY_COLUMNS]));
        }
        
        for (int i = 0; i <= PROJECT_SQL.length - 1; i++) {
            if (importData.projectData.size() > i) {
                TableData importProjectData = importData.projectData.get(i);
                try {
                    processConfigTableData(importData, existingProjectData.get(i), importProjectData,
                            PROJECT_SQL[i][KEY_COLUMNS], transaction, userId);
                } catch (RuntimeException e) {
                    if (importProjectData.getTableName().toLowerCase().endsWith("project_version_dependency")) {
                        Collection<LinkedCaseInsensitiveMap<Object>> maps = importProjectData.getTableData().values();
                        StringBuilder ids = new StringBuilder();
                        for (LinkedCaseInsensitiveMap<Object> linkedCaseInsensitiveMap : maps) {
                            if (ids.length() > 0) {
                                ids.append(",");
                            }
                            ids.append(linkedCaseInsensitiveMap.get("TARGET_PROJECT_VERSION_ID"));
                        }
                        throw new MessageException("Missing dependent project.  Please load projects with the following ids first: " + ids); 
                    } else {
                        throw e;
                    }
                }
            }
        }   
    }

    private void importResourceConfiguration(ImportConfigData importData,
            ISqlTransaction transaction, String userId) {
        String projectVersionId = (String)getOneRow(importData.getResourceData().get(RESOURCE_IDX)).get("PROJECT_VERSION_ID");        
        List<TableData> existingResourceData = new ArrayList<TableData>();
        initConfigData(existingResourceData, RESOURCE_SQL);
        
        Iterator<String> itr = importData.getResourceData().get(RESOURCE_IDX)
                .getTableData().keySet().iterator();        
        while (itr.hasNext()) {
            String key = itr.next();
            LinkedCaseInsensitiveMap<Object> row = importData.getResourceData().get(RESOURCE_IDX).getTableData().get(key);
            addConfigData(existingResourceData, RESOURCE_SQL, projectVersionId,
                    (String) row.get(RESOURCE_SQL[RESOURCE_IDX][KEY_COLUMNS]));
        }       
        
        for (int i = 0; i <= RESOURCE_SQL.length - 1; i++) {
            TableData importResourceData = importData.resourceData.get(i);
            processConfigTableData(importData, existingResourceData.get(i), importResourceData,
                    RESOURCE_SQL[i][KEY_COLUMNS], transaction, userId);
        }
    }

    private void importModelConfiguration(ImportConfigData importData,
            ISqlTransaction transaction, String userId) {
        String projectVersionId = (String)getOneRow(importData.getModelData().get(MODEL_IDX)).get("PROJECT_VERSION_ID");        
        List<TableData> existingModelData = new ArrayList<TableData>();
        initConfigData(existingModelData, MODEL_SQL);
        
        Iterator<String> itr = importData.getModelData().get(MODEL_IDX)
                .getTableData().keySet().iterator();        
        while (itr.hasNext()) {
            String key = itr.next();
            LinkedCaseInsensitiveMap<Object> row = importData.getModelData().get(MODEL_IDX).getTableData().get(key);
            addConfigData(existingModelData, MODEL_SQL, projectVersionId,
                    (String) row.get(MODEL_SQL[MODEL_IDX][KEY_COLUMNS]));
        }
        
        for (int i = 0; i <= MODEL_SQL.length - 1; i++) {
            TableData importModelData = importData.modelData.get(i);
            processConfigTableData(importData, existingModelData.get(i), importModelData,
                    MODEL_SQL[i][KEY_COLUMNS], transaction, userId);
        }
    }

    private void importFlowConfiguration(ImportConfigData importData, ISqlTransaction transaction, String userId) {
        String projectVersionId = (String)getOneRow(importData.getFlowData().get(FLOW_IDX)).get("PROJECT_VERSION_ID");        
        List<TableData> existingFlowData = new ArrayList<TableData>();
        initConfigData(existingFlowData, FLOW_SQL);
                
        Iterator<String> itr = importData.getFlowData().get(FLOW_IDX)
                .getTableData().keySet().iterator();        
        while (itr.hasNext()) {
            String key = itr.next();
            LinkedCaseInsensitiveMap<Object> row = importData.getFlowData().get(FLOW_IDX).getTableData().get(key);
            addConfigData(existingFlowData, FLOW_SQL, projectVersionId,
                    (String) row.get(FLOW_SQL[FLOW_IDX][KEY_COLUMNS]));
        }  

        for (int i = 0; i <= FLOW_SQL.length - 1; i++) {
            TableData importFlowData = importData.flowData.get(i);
            processConfigTableData(importData, existingFlowData.get(i), importFlowData,
                    FLOW_SQL[i][KEY_COLUMNS], transaction, userId);
        }
    }

    private LinkedCaseInsensitiveMap<Object> getOneRow(TableData tableData) {        
        Iterator<String> keyItr = tableData.rows.keySet().iterator();
        String key = null;
        while (keyItr.hasNext()) {
            key = keyItr.next();
            break;
        }
        if (key != null) {
            return tableData.rows.get(key);
        } else {
            return null;
        }
    }
    
    private final boolean isPassword(String name) {
        return isNotBlank(name) && name.contains("password");
    }
    
    private void processConfigTableData(ImportConfigData configData, TableData existingData,
            TableData importData, String primaryKeyColumns, ISqlTransaction transaction, String userId) {        
        if (importsToAudit.contains(importData.getTableName().toUpperCase())) {
            for (LinkedCaseInsensitiveMap<Object> row : importData.getTableData().values()) {
                String name = (String) row.get("name");                
                if (name == null) {
                    name = (String) row.get("version_label");
                }
                save(new AuditEvent(AuditEvent.EventType.IMPORT,
                        String.format("%s: %s from host: %s from Metl version: %s", importData.getTableName(), name, configData.getHostName(), 
                                configData.getVersionNumber()), userId));
            }
        }
        
        for (LinkedCaseInsensitiveMap<Object> row : importData.getTableData().values()) {
            if (isPassword((String)row.get("NAME"))) {
                String value = (String)row.get("VALUE");
                if (isNotBlank(value)) {
                    if (!value.startsWith(SecurityConstants.PREFIX_ENC)) {
                        row.put("VALUE",
                                SecurityConstants.PREFIX_ENC + securityService.encrypt(value));
                    }
                }
            }
        }
        
        TableData inserts = findInserts(existingData, importData, primaryKeyColumns);
        processTableInserts(inserts, transaction);

        TableData updates = findUpdates(existingData, importData, primaryKeyColumns);
        processTableUpdates(updates, transaction);

        TableData deletes = findDeletes(existingData, importData, primaryKeyColumns);
        configData.deletesToProcess.put(importData.getTableName(), deletes);
    }

    private void processTableInserts(TableData inserts, ISqlTransaction transaction) {
            Table table = databasePlatform.getTableFromCache(null, null, inserts.getTableName(),
                    false);
            excludeInsertColumns(table);

        //mysql colmn name lower case error
        // --start
            GeneralUtils.columnNameToUpperCase(table);
        //--end--

            DmlStatement stmt = databasePlatform.createDmlStatement(DmlType.INSERT,
                    table.getCatalog(), table.getSchema(), table.getName(),
                    table.getPrimaryKeyColumns(), table.getColumns(), null, null, true);
            
            Iterator<String> itr = inserts.getTableData().keySet().iterator();
            while (itr.hasNext()) {
                String key = itr.next();
                LinkedCaseInsensitiveMap<Object> row = inserts.getTableData().get(key);
                Date createTime = new Date();
                row.put("CREATE_TIME", createTime);
                row.put("LAST_UPDATE_TIME", createTime);
                useDefaultsForMissingRequiredColumns(table, row);                
                transaction.prepareAndExecute(stmt.getSql(), row);
            }              

    }

    private void processTableUpdates(TableData updates, ISqlTransaction transaction) {
            Table table = databasePlatform.getTableFromCache(null, null, updates.getTableName(), false);
            excludeUpdateColumns(table);

        //mysql colmn name lower case error
        // --start
            GeneralUtils.columnNameToUpperCase(table);
        //--end--

            DmlStatement stmt = databasePlatform.createDmlStatement(DmlType.UPDATE, table.getCatalog(),
                    table.getSchema(), table.getName(), table.getPrimaryKeyColumns(),
                    getUpdateColumns(table), null, null, true);
            
            Iterator<String> itr = updates.getTableData().keySet().iterator();
            while (itr.hasNext()) {
                String key = itr.next();
                LinkedCaseInsensitiveMap<Object> row = updates.getTableData().get(key);
                row.put("LAST_UPDATE_TIME", new Date());
                useDefaultsForMissingRequiredColumns(table, row);                
                transaction.prepareAndExecute(stmt.getSql(), row);
            }
    }

    private void useDefaultsForMissingRequiredColumns(Table table,LinkedCaseInsensitiveMap<Object> row) {        
        for (Column column : table.getColumnsAsList()) {
            if (!row.containsKey(column.getName())) {
                row.put(column.getName(), column.getDefaultValue());
            }
        }
    }
    
    private void processTableDeletes(TableData deletes, ISqlTransaction transaction) {
        if (deletes != null) {
            Table table = databasePlatform.getTableFromCache(null, null, deletes.getTableName(),
                    false);
            //mysql colmn name lower case error
            // --start
            GeneralUtils.columnNameToUpperCase(table);
            //--end--

            DmlStatement stmt = databasePlatform.createDmlStatement(DmlType.DELETE,
                    table.getCatalog(), table.getSchema(), table.getName(),
                    table.getPrimaryKeyColumns(), getUpdateColumns(table), null, null, true);
            
            Iterator<String> itr = deletes.getTableData().keySet().iterator();
            while (itr.hasNext()) {
                String key = itr.next();
                LinkedCaseInsensitiveMap<Object> row = deletes.getTableData().get(key);
                transaction.prepareAndExecute(stmt.getSql(), row);
            }            
        }
    }

    private Column[] getUpdateColumns(Table table) {
        ArrayList<Column> columns = new ArrayList<Column>();
        for (Column column : table.getColumns()) {
            if (!column.isPrimaryKey())
                columns.add(column);
        }
        return columns.toArray(new Column[0]);
    }

    private void excludeInsertColumns(Table table) {
        for (Column column : table.getColumns()) {
            if (column.getName().equalsIgnoreCase(columnsToExclude[CREATE_BY_IDX])
                    || column.getName().equalsIgnoreCase(columnsToExclude[LAST_UPDATE_BY_IDX])) {
                table.removeColumn(column);
            }
        }
    }

    private void excludeUpdateColumns(Table table) {
        for (Column column : table.getColumns()) {
            if (column.getName().equalsIgnoreCase(columnsToExclude[CREATE_BY_IDX])
                    || column.getName().equalsIgnoreCase(columnsToExclude[LAST_UPDATE_BY_IDX])
                    || column.getName().equalsIgnoreCase(columnsToExclude[CREATE_TIME_IDX])) {
                table.removeColumn(column);
            }
        }
    }
    
    
    private ConfigData deserializeConfigurationData(String configDataString) {
        ObjectMapper mapper = new ObjectMapper();
        ConfigData configData = null;
        try {
            configData = mapper.readValue(configDataString, ConfigData.class);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new UnsupportedOperationException("Error deserializing json data for import");
        }
        return configData;
    }

    private TableData findInserts(TableData existingData, TableData newData,
            String primaryKeyColumns) {
        boolean found;
        TableData inserts = new TableData(newData.tableName);
        
        Iterator<String> newRowItr = newData.getTableData().keySet().iterator();
        while (newRowItr.hasNext()) {
            String newPk = newRowItr.next();
            LinkedCaseInsensitiveMap<Object> newRow = newData.getTableData().get(newPk);
            found = false;
            
            Iterator<String> existingRowItr = existingData.getTableData().keySet().iterator();
            while (existingRowItr.hasNext()) {
                String existingPk = existingRowItr.next();
                if (newPk.equalsIgnoreCase(existingPk)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                inserts.getTableData().put(newPk, newRow);
            }
        }        
        return inserts;
    }

    private TableData findDeletes(TableData existingData, TableData newData,
            String primaryKeyColumns) {
        boolean found;
        TableData deletes = new TableData(newData.tableName);
        
        Iterator<String> existingRowItr = existingData.getTableData().keySet().iterator();
        while (existingRowItr.hasNext()) {
            String existingPk = existingRowItr.next();
            LinkedCaseInsensitiveMap<Object> existingRow = existingData.getTableData().get(existingPk);
            found = false;
            
            Iterator<String> newRowItr = newData.getTableData().keySet().iterator();
            while (newRowItr.hasNext()) {
                String newPk = newRowItr.next();
                if (newPk.equalsIgnoreCase(existingPk)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                deletes.getTableData().put(existingPk, existingRow);
            }            
        }               
        return deletes;
    }

    private TableData findUpdates(TableData existingData, TableData newData,
            String primaryKeyColumns) {
        boolean found;
        String[] pkCols = StringUtils.split(primaryKeyColumns);
        TableData updates = new TableData(newData.tableName);
        // if the pk is the entire record, don't do an update
        
        Iterator<String> itr = existingData.getTableData().keySet().iterator();
        int size=0;
        while (itr.hasNext()) {
            String key = itr.next();
            size = existingData.getTableData().get(key).size();
        }
                
        if (existingData.rows.size() > 0 && pkCols.length + 1 < size) {
            
            Iterator<String> newRowItr = newData.getTableData().keySet().iterator();
            while (newRowItr.hasNext()) {
                String newPk = newRowItr.next();
                LinkedCaseInsensitiveMap<Object> newRow = newData.getTableData().get(newPk);
                found = false;
                
                Iterator<String> existingRowItr = existingData.getTableData().keySet().iterator();
                while (existingRowItr.hasNext()) {
                    String existingPk = existingRowItr.next();
                    if (newPk.equalsIgnoreCase(existingPk)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    updates.getTableData().put(newPk, newRow);
                }               
            }            
        }
        return updates;
    }

    private String getPkDataAsString(LinkedCaseInsensitiveMap<Object> row,
            String primaryKeyColumns) {
        StringBuilder pkDataAsString = new StringBuilder();
        String[] pkCols = StringUtils.split(primaryKeyColumns, ',');
        for (int i = 0; i < pkCols.length; i++) {
            pkDataAsString.append(row.get(pkCols[i]));
        }

        return pkDataAsString.toString();
    }

    private void initConfigData(List<TableData> tableData, String[][] sqlElements) {
        for (int i = 0; i <= sqlElements.length - 1; i++) {
            tableData.add(new TableData(tablePrefix + sqlElements[i][0]));
        }
    }

    static class TableData {

        String tableName;
        Map<String, LinkedCaseInsensitiveMap<Object>> rows = new HashMap<String, LinkedCaseInsensitiveMap<Object>>();

        public TableData() {
        }

        public TableData(String tableName) {
            this.tableName = tableName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public Map<String, LinkedCaseInsensitiveMap<Object>> getTableData() {
            return rows;
        }

        public void setTableData(Map<String, LinkedCaseInsensitiveMap<Object>> tableData) {
            this.rows = tableData;
        }

    }

    static class ConfigData {

        String versionNumber;
        String hostName;
        List<TableData> releasePackageData = new ArrayList<TableData>();
        List<TableData> projectData = new ArrayList<TableData>();
        List<TableData> resourceData = new ArrayList<TableData>();
        List<TableData> modelData = new ArrayList<TableData>();
        List<TableData> flowData = new ArrayList<TableData>();

        public ConfigData() {
            releasePackageData = new ArrayList<TableData>();
            projectData = new ArrayList<TableData>();
            resourceData = new ArrayList<TableData>();
            modelData = new ArrayList<TableData>();
            flowData = new ArrayList<TableData>();
        }

        public void setVersionNumber(String versionNumber) {
            this.versionNumber = versionNumber;
        }
        
        public String getVersionNumber() {
            return versionNumber;
        }
        
        public void setHostName(String systemId) {
            this.hostName = systemId;
        }
        
        public String getHostName() {
            return hostName;
        }

        public List<TableData> getReleasePackageData() {
            return releasePackageData;
        }

        public void setReleasePackageData(List<TableData> releasePackageData) {
            this.releasePackageData = releasePackageData;
        }        
        
        public List<TableData> getResourceData() {
            return resourceData;
        }

        public void setResourceData(List<TableData> resourceData) {
            this.resourceData = resourceData;
        }

        public List<TableData> getModelData() {
            return modelData;
        }

        public void setModelData(List<TableData> modelData) {
            this.modelData = modelData;
        }

        public List<TableData> getFlowData() {
            return flowData;
        }

        public void setFlowData(List<TableData> flowData) {
            this.flowData = flowData;
        }

        public List<TableData> getProjectData() {
            return projectData;
        }

        public void setProjectData(List<TableData> projectData) {
            this.projectData = projectData;
        }
    }

    static class ImportConfigData extends ConfigData {

        public ImportConfigData(ConfigData configData) {
            this.hostName = configData.getHostName();
            this.versionNumber = configData.getVersionNumber();
            this.releasePackageData = configData.releasePackageData;
            this.projectData = configData.projectData;
            this.resourceData = configData.resourceData;
            this.modelData = configData.modelData;
            this.flowData = configData.flowData;
            this.deletesToProcess = new HashMap<String, TableData>();
        }

        Map<String, TableData> deletesToProcess;

    }
}
