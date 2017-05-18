package org.jumpmind.metl.ignite.runtime;

import org.apache.commons.lang.StringUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelAttribute;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.runtime.EntityData;
import org.jumpmind.metl.core.runtime.EntityDataMessage;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.component.AbstractComponentRuntime;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;
import org.jumpmind.metl.ignite.resource.UnofficeIgniteCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by User on 2017/5/18.
 */
public class UnofficeIgniteSQLWriter extends AbstractComponentRuntime {
    public final static String TYPE = "Unoffice Ignite SQL Writer";
    public static final String SETTING_CONTROL_MESSAGE_ON_TEXT_SEND = "control.message.on.text.send";
    String runWhen = PER_UNIT_OF_WORK;
    int textRowsPerMessage;
    boolean controlMessageOnTextSend;


    /*input model */
    private Model inputModel;
    private List<ModelEntity> inputModelEntityList;
    private ModelEntity inputModelEntity;
    private List<ModelAttribute> inputModelAttributeList;

    private StringBuffer insertSQL =new StringBuffer();

    private Ignite igniteClient = null;
    private UnofficeIgniteCache unofficeIgniteCache =null;
    private IgniteCache cacheObject;

    @Override
    public void start() {

        super.start();

        textRowsPerMessage = context.getFlowStep().getComponent().getInt(ROWS_PER_MESSAGE, 1000);
        controlMessageOnTextSend = context.getFlowStep().getComponent().getBoolean(SETTING_CONTROL_MESSAGE_ON_TEXT_SEND, false);
        runWhen = getComponent().get(RUN_WHEN, PER_UNIT_OF_WORK);

        if (getInputModel() == null) {
            throw new IllegalStateException("A Producer writer must have an input model defined");
        }

        /*input model */
        inputModel = this.getInputModel();
        inputModelEntityList = inputModel.getModelEntities();
        if (inputModelEntityList == null || inputModelEntityList.size() == 0) {
            throw new IllegalStateException("No Entity exists in the input model");
        }
        inputModelEntity = inputModelEntityList.get(0);
        inputModelAttributeList = inputModelEntity.getModelAttributes();
        if (inputModelAttributeList == null || inputModelAttributeList.size() == 0) {
            throw new IllegalStateException("No attributes  exists in  the first entity of the input model");
        }

        if (getResourceRuntime() == null) {
            throw new IllegalStateException("An Ignite sql writer must have a ignite cache defined");
        }

        insertSQL =new StringBuffer();
        insertSQL.append("insert into ");
        insertSQL.append(inputModelEntity.getName());
        insertSQL.append("(");

        List<String> el =new ArrayList();
        for (ModelAttribute ma : inputModelAttributeList) {
            el.add(ma.getName());
        }
        insertSQL.append(StringUtils.join(el,","));
        insertSQL.append(") values (");

        List<String> vl =new ArrayList();
        for (ModelAttribute ma : inputModelAttributeList) {
            vl.add("?");
        }
        insertSQL.append(StringUtils.join(vl,","));
        insertSQL.append(")");

        igniteClient=(Ignite)this.getResourceReference();

        unofficeIgniteCache =(UnofficeIgniteCache)this.getResourceRuntime();
        
        cacheObject =igniteClient.getOrCreateCache(unofficeIgniteCache.getCacheName());
    }

    @Override
    public boolean supportsStartupMessages() {
        return false;
    }

    @Override
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {
        if (inputMessage instanceof EntityDataMessage) {
            ArrayList<EntityData> inputRows = ((EntityDataMessage) inputMessage).getPayload();
            if (inputRows != null && inputRows.size() > 0) {


                try {


                    for (EntityData row : inputRows) {
                        if (inputModelAttributeList != null && inputModelAttributeList.size() > 0) {
                            if (row.get(inputModelAttributeList.get(0).getId()) == null) {
                                continue;
                            }
                            ArrayList<Object> args = new ArrayList<Object>();
                            for (ModelAttribute ma : inputModelAttributeList) {
                                args.add( row.get(ma.getId()));
                            }
                            cacheObject.query(new SqlFieldsQuery(insertSQL.toString()).setArgs(args.toArray()));
                        }
                    }

                }catch(Exception e){
                    log.error("",e);
                    throw new RuntimeException(e);
                }
                finally {

                    if (PER_MESSAGE.equals(runWhen) && controlMessageOnTextSend) {
                        callback.sendControlMessage();
                    }
                }
            }
        }
    }
}
