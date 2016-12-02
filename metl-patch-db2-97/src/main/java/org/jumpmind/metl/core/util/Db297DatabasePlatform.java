package org.jumpmind.metl.core.util;

import org.apache.commons.lang.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.db2.Db2DdlBuilder;
import org.jumpmind.db.platform.db2.Db2DdlReader;
import org.jumpmind.db.platform.db2.Db2JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;

import javax.sql.DataSource;
/** for DB2 9.7
 * Created by User on 2016/11/30.
 * The DB2 platform implementation.
 */
public class Db297DatabasePlatform extends AbstractJdbcDatabasePlatform{
    /* The standard DB2 jdbc driver. */
    public static final String JDBC_DRIVER = "com.ibm.db2.jcc.DB2Driver";

    /* The subprotocol used by the standard DB2 driver. */
    public static final String JDBC_SUBPROTOCOL = "db2";

    /*
     * Creates a new platform instance.
     */
    public Db297DatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    protected Db2DdlBuilder createDdlBuilder() {
        return new Db2DdlBuilder();
    }

    @Override
    protected Db297DdlReader createDdlReader() {
        return new Db297DdlReader(this);
    }

    @Override
    protected Db2JdbcSqlTemplate createSqlTemplate() {
        return new Db2JdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    public String getName() {
        return DatabaseNamesConstants.DB2;
    }

    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = (String) getSqlTemplate().queryForObject("select CURRENT SCHEMA from sysibm.sysdummy1", String.class);
        }
        return defaultSchema;
    }

    public String getDefaultCatalog() {
        return "";
    }

    @Override
    public boolean canColumnBeUsedInWhereClause(Column column) {
        return !column.isOfBinaryType();
    }
}
