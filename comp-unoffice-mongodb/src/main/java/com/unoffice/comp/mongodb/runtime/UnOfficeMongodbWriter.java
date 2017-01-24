package com.unoffice.comp.mongodb.runtime;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelAttribute;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.runtime.EntityData;
import org.jumpmind.metl.core.runtime.EntityDataMessage;
import org.jumpmind.metl.core.runtime.component.AbstractComponentRuntime;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by User on 2017/1/24.
 */
public class UnOfficeMongodbWriter extends AbstractComponentRuntime {
    public final static String TYPE = "UnOffice Mongodb Writer";
    public static final String SETTING_CONTROL_MESSAGE_ON_TEXT_SEND = "control.message.on.text.send";
    String runWhen = PER_UNIT_OF_WORK;
    int textRowsPerMessage;
    boolean controlMessageOnTextSend;



    /*input model */
    private Model inputModel ;
    private List<ModelEntity> inputModelEntityList;
    private ModelEntity inputModelEntity;
    private List<ModelAttribute> inputModelAttributeList;


    /*mongodb params*/
    String host ;
    Integer port ;
    String db ;
    String collection;
    String user ;
    String pwd;
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
    }

    @Override
    public boolean supportsStartupMessages() {
        return false;
    }


    @Override
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {

        host = getComponent().get("host");
        port = getComponent().getInt("port",27017);
        db = getComponent().get("db");
        collection = getComponent().get("collection");
        user = getComponent().get("user");
        pwd = getComponent().get("pwd");

        if (inputMessage instanceof EntityDataMessage) {
            ArrayList<EntityData> inputRows = ((EntityDataMessage) inputMessage).getPayload();
            if (inputRows != null && inputRows.size() > 0) {


                ServerAddress serverAddress = new ServerAddress(host,port);
                List<ServerAddress> addrs = new ArrayList<ServerAddress>();
                addrs.add(serverAddress);

                MongoCredential credential = MongoCredential.createScramSha1Credential(user, db, pwd.toCharArray());
                List<MongoCredential> credentials = new ArrayList<MongoCredential>();
                credentials.add(credential);

                MongoClient mongoClient = new MongoClient(addrs,credentials);

                try {
                    MongoDatabase mongoDatabase = mongoClient.getDatabase(db);
                    MongoCollection<Document> collectionObject = mongoDatabase.getCollection(collection);

                    for (EntityData row : inputRows) {
                        if (inputModelAttributeList != null && inputModelAttributeList.size() > 0) {
                            if (row.get(inputModelAttributeList.get(0).getId()) == null) {
                                continue;
                            }

                            Document document = new Document();
                            for (ModelAttribute ma : inputModelAttributeList) {
                                document.append(ma.getName(), row.get(ma.getId()));
                            }

                            collectionObject.insertOne(document);
                        }
                    }

                }catch(Exception e){
                    log.error("",e);
                }
                finally {
                    try {
                        mongoClient.close();
                    }catch(Exception e){
                        log.error("",e);
                    }
                    if (PER_MESSAGE.equals(runWhen) && controlMessageOnTextSend) {
                        callback.sendControlMessage();
                    }
                }
            }
        }
    }
}
