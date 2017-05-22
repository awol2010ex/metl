package org.jumpmind.metl.ignite.runtime;

import org.apache.commons.io.IOUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.jumpmind.db.sql.SqlScriptReader;
import org.jumpmind.metl.core.runtime.*;
import org.jumpmind.metl.core.runtime.component.AbstractRdbmsComponentRuntime;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;
import org.jumpmind.metl.ignite.resource.UnofficeIgniteCache;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * Created by User on 2017/5/22.
 */
public class UnofficeIgniteSQLExecutor extends AbstractRdbmsComponentRuntime {
    public final static String TYPE = "Unoffice Ignite SQL Executor";

    public static final String SETTING_CONTROL_MESSAGE_ON_TEXT_SEND = "control.message.on.text.send";

    int textRowsPerMessage;
    boolean controlMessageOnTextSend;


    private Ignite igniteClient = null;
    private UnofficeIgniteCache unofficeIgniteCache =null;
    private IgniteCache cacheObject;

    List<String> sqls;
    String runWhen = PER_MESSAGE;

    String file;
    private static final String FILE = "sql.file";
    @Override
    public void start() {

        super.start();

        textRowsPerMessage = context.getFlowStep().getComponent().getInt(ROWS_PER_MESSAGE, 1000);
        controlMessageOnTextSend = context.getFlowStep().getComponent().getBoolean(SETTING_CONTROL_MESSAGE_ON_TEXT_SEND, false);
        runWhen = getComponent().get(RUN_WHEN, PER_MESSAGE);
        file = properties.get(FILE);
        sqls = getExecutorSqlStatements();
        if (getResourceRuntime() == null) {
            throw new IllegalStateException("An Ignite sql executor must have a ignite cache defined");
        }
        igniteClient=(Ignite)this.getResourceReference();

        unofficeIgniteCache =(UnofficeIgniteCache)this.getResourceRuntime();

        cacheObject =igniteClient.getOrCreateCache(unofficeIgniteCache.getCacheName());
    }
    @Override
    public boolean supportsStartupMessages() {
        return true;
    }

    @Override
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {
        results.clear();

        int sqlCount = 0;
        int resultCount = 0;
        int inboundRecordCount = 0;

        Iterator<?> inboundPayload = null;
        if (PER_ENTITY.equals(runWhen) && inputMessage instanceof ContentMessage<?>) {
            inboundPayload = ((Collection<?>) ((ContentMessage<?>) inputMessage).getPayload()).iterator();
            inboundRecordCount = ((Collection<?>) ((ContentMessage<?>) inputMessage).getPayload()).size();
        } else if (PER_MESSAGE.equals(runWhen) && !(inputMessage instanceof ControlMessage)) {
            inboundPayload = null;
            inboundRecordCount = 1;
        } else if (PER_UNIT_OF_WORK.equals(runWhen) && inputMessage instanceof ControlMessage) {
            inboundPayload = null;
            inboundRecordCount = 1;
        }

        for (int i = 0; i < inboundRecordCount; i++) {
            Object entity = inboundPayload != null && inboundPayload.hasNext() ? inboundPayload.next() : null;
            for (String sql : this.sqls) {
                String sqlToExecute = prepareSql(sql, inputMessage, entity);
                Map<String, Object> paramMap = prepareParams(sqlToExecute, inputMessage, entity, runWhen);
                log(LogLevel.INFO, "About to run: %s", sqlToExecute);
                log(LogLevel.INFO, "Passing params: %s", paramMap);
                resultCount = cacheObject.query(new SqlFieldsQuery(sqlToExecute)).getAll().size();
                getComponentStatistics().incrementNumberEntitiesProcessed(resultCount);
                sqlCount++;
            }
        }
        if (callback != null && sqlCount > 0) {
            callback.sendTextMessage(null, convertResultsToTextPayload(results));
        }
        log(LogLevel.INFO, "Ran %d sql statements", sqlCount);
    }

    protected List<String> getExecutorSqlStatements() {

        List<String> sqlStatements=null;;

        //sqlstatements come from file or sql setting in the component
        if (isNotBlank(file)) {
            sqlStatements = new ArrayList<String>();
            FileReader fileReader = null;
            SqlScriptReader sqlReader = null;
            try {
                fileReader = new FileReader(file);
                sqlReader = new SqlScriptReader(fileReader);
                String sqlToExecute = sqlReader.readSqlStatement();
                while (sqlToExecute != null) {
                    sqlStatements.add(sqlToExecute);
                    sqlToExecute = sqlReader.readSqlStatement();
                }
            } catch (FileNotFoundException e) {
                throw new MisconfiguredException("Could not find configured file: %s", file);
            } finally {
                IOUtils.closeQuietly(fileReader);
                IOUtils.closeQuietly(sqlReader);
            }
        } else {
            sqlStatements = getSqlStatements(isBlank(file));
        }
        return sqlStatements;
    }

}