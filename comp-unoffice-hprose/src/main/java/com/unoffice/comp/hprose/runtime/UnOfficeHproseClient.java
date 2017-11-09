package com.unoffice.comp.hprose.runtime;

import hprose.client.HproseClient;
import hprose.client.HproseHttpClient;
import hprose.client.HproseTcpClient;
import hprose.util.concurrent.Action;
import hprose.util.concurrent.Promise;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelAttrib;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.runtime.EntityData;
import org.jumpmind.metl.core.runtime.EntityDataMessage;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.component.AbstractComponentRuntime;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by User on 2016/12/28.
 */
public class UnOfficeHproseClient extends AbstractComponentRuntime {
    final static Logger logger = LoggerFactory.getLogger(UnOfficeHproseClient.class);
    public static final String TYPE = "UnOffice Hprose Client";
    public static final String SETTING_CONTROL_MESSAGE_ON_TEXT_SEND = "control.message.on.text.send";
    //visit protocol
    public static final String PROTOCOL_TCP = "TCP";
    public static final String PROTOCOL_HTTP = "HTTP";
    /*hprose param  ,view docs*/
    public static final String PROTOCOL = "hprose.protocol";
    public static final String URI = "hprose.uri";
    public static final String METHOD = "hprose.method";
    public static final String KEEPALIVE = "hprose.keepalive";
    public static final String KEEPALIVETIMEOUT = "hprose.keepalivetimeout";
    public static final String TCP_FULLDUPLEX = "hprose.tcp.fullduplex";
    public static final String TCP_ISNODELAY = "hprose.tcp.isnodelay";
    public static final String TCP_MAXPOOLSIZE = "hprose.tcp.maxpoolsize";
    public static final String TCP_IDLETIMEOUT = "hprose.tcp.idletimeout";
    public static final String TCP_READTIMEOUT = "hprose.tcp.readtimeout";
    public static final String TCP_WRITETIMEOUT = "hprose.tcp.writetimeout";
    public static final String TCP_CONNECTTIMEOUT = "hprose.tcp.connecttimeout";

    public String protocol;
    public String uri;
    public String method;
    public Boolean keepAlive;
    public Integer keepAliveTimeout;
    public Boolean tcpFullDuplex;
    public Boolean tcpIsNoDelay;
    public Integer tcpMaxPoolSize;
    public Integer tcpIdleTimeout;
    public Integer tcpReadTimeout;
    public Integer tcpWriteTimeout;
    public Integer tcpConnectTimeout;


    //flow compenent params
    String runWhen = PER_UNIT_OF_WORK;
    int textRowsPerMessage;
    boolean controlMessageOnTextSend;

    //hprose return type
    public static final String HPROSE_RETURN_TYPE_TEXT = "Text";
    public static final String HPROSE_RETURN_TYPE_BYTES = "Byte";
    public static final String HPROSE_RETURN_TYPE_MAP = "Map";
    public static final String RETURN_TYPE = "hprose.return.type";
    public String hproseReturnType ;


    /*input model */
    private Model inputModel ;
    private List<ModelEntity> inputModelEntityList;
    private ModelEntity inputModelEntity;
    private List<ModelAttrib> inputModelAttributeList;
    /*output model */
    private Model outputModel ;
    private List<ModelEntity> outputModelEntityList;
    private ModelEntity outputModelEntity;
    private List<ModelAttrib> outputModelAttributeList;
    private Map<String,String> mapToOutputEntityData=new HashMap<String,String>();
    @Override
    public void start() {
        super.start();
        textRowsPerMessage = context.getFlowStep().getComponent().getInt(ROWS_PER_MESSAGE, 1000);
        controlMessageOnTextSend = context.getFlowStep().getComponent().getBoolean(SETTING_CONTROL_MESSAGE_ON_TEXT_SEND, false);
        runWhen = getComponent().get(RUN_WHEN, PER_UNIT_OF_WORK);

        if (getInputModel() == null) {
            throw new IllegalStateException("A Hprose client must have an input model defined  for params");
        }

        /*hprose param  ,view docs*/
        protocol = getComponent().get(PROTOCOL);
        uri = getComponent().get(URI);
        method = getComponent().get(METHOD);
        keepAlive = getComponent().getBoolean(KEEPALIVE, true);
        keepAliveTimeout = getComponent().getInt(KEEPALIVETIMEOUT, 300);

        tcpFullDuplex = getComponent().getBoolean(TCP_FULLDUPLEX, false);
        tcpIsNoDelay = getComponent().getBoolean(TCP_ISNODELAY, false);
        tcpMaxPoolSize = getComponent().getInt(TCP_MAXPOOLSIZE, 2);
        tcpIdleTimeout = getComponent().getInt(TCP_IDLETIMEOUT, 30000);
        tcpReadTimeout = getComponent().getInt(TCP_READTIMEOUT, 30000);
        tcpWriteTimeout = getComponent().getInt(TCP_WRITETIMEOUT, 30000);
        tcpConnectTimeout = getComponent().getInt(TCP_CONNECTTIMEOUT, 30000);

        /*return type*/
        hproseReturnType =getComponent().get(RETURN_TYPE);
        if(HPROSE_RETURN_TYPE_MAP.equals(hproseReturnType) && getOutputModel() ==null){
            throw new IllegalStateException("return map must have output model");
        }

        /*output model */
        if(HPROSE_RETURN_TYPE_MAP.equals(hproseReturnType)) {
            outputModel = getOutputModel();
            outputModelEntityList = inputModel.getModelEntities();
            if (outputModelEntityList == null || outputModelEntityList.size() == 0) {
                throw new IllegalStateException("No Entity exists in the input model");
            }
            outputModelEntity = outputModelEntityList.get(0);
            outputModelAttributeList = outputModelEntity.getModelAttributes();
            if (outputModelAttributeList == null || outputModelAttributeList.size() == 0) {
                throw new IllegalStateException("No attributes  exists in  the first entity of the output model");
            }
            else{
                for(ModelAttrib oa :  outputModelAttributeList){
                     mapToOutputEntityData.put(oa.getName(),oa.getId());
                }
            }
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
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {
        //use flow params
        uri = resolveParamsAndHeaders(uri, inputMessage);
        method = resolveParamsAndHeaders(method, inputMessage);



        if (inputMessage instanceof EntityDataMessage) {

            ArrayList<Object> payload = new ArrayList<Object>();


            ArrayList<EntityData> inputRows = ((EntityDataMessage) inputMessage).getPayload();
            if (inputRows != null && inputRows.size() > 0) {

                HproseClient client =null;
                if (PROTOCOL_TCP.equals(protocol)) {
                    client = new HproseTcpClient();
                    client.setUriList(uri.split(";"));
                    ((HproseTcpClient)client).setConnectTimeout(tcpConnectTimeout);
                    ((HproseTcpClient)client).setFullDuplex(tcpFullDuplex);
                    ((HproseTcpClient)client).setIdleTimeout(tcpIdleTimeout);
                    ((HproseTcpClient)client).setKeepAlive(keepAlive);
                    ((HproseTcpClient)client).setMaxPoolSize(tcpMaxPoolSize);
                    ((HproseTcpClient)client).setNoDelay(tcpIsNoDelay);
                    ((HproseTcpClient)client).setReadTimeout(tcpReadTimeout);
                    ((HproseTcpClient)client).setWriteTimeout(tcpWriteTimeout);



                }
                else
                if(PROTOCOL_HTTP.equals(protocol)){
                    client =new HproseHttpClient();
                    client.setUriList(uri.split(";"));
                    ((HproseHttpClient)client).setKeepAlive(keepAlive);
                }
                //for async call
                ArrayList<Promise>  promiseList =new  ArrayList<Promise>();


                for (EntityData row : inputRows) {
                    if (inputModelAttributeList != null && inputModelAttributeList.size() > 0) {
                        if (row.get(inputModelAttributeList.get(0).getId()) == null) {
                            continue;
                        }
                        List<Object> params = new ArrayList<Object>();
                        for (ModelAttrib ma : inputModelAttributeList) {
                            params.add(row.get(ma.getId()));
                        }

                        if(client!=null){

                                try {
                                    payload.add(formatReturnValue(client.invoke(method ,params.toArray())));
                                } catch (Throwable ex) {
                                    throw new RuntimeException(ex);
                                }

                        }

                    }
                }



                    sendPayload(callback ,payload);

                    //send controll message
                    if (PER_MESSAGE.equals(runWhen) && controlMessageOnTextSend) {
                        callback.sendControlMessage();
                    }


                if(client!=null){
                    client.close();
                }

            }


        }
    }

    @Override
    public boolean supportsStartupMessages() {
        return true;
    }


    //send the values of client recevies to the data flow
    public void sendPayload(ISendMessageCallback callback, List<?> payload){
        if(HPROSE_RETURN_TYPE_MAP.equals(hproseReturnType) ){
            callback.sendEntityDataMessage(null, (ArrayList<EntityData>)payload);
        }
        else{
            callback.sendTextMessage(null ,(ArrayList<String>)payload);
        }
    }

    public Object formatReturnValue(Object originValue){
        if(originValue==null){
            return null;
        }
        if(HPROSE_RETURN_TYPE_TEXT.equals(hproseReturnType)){
            return String.valueOf(originValue);
        }
        else
        if(HPROSE_RETURN_TYPE_BYTES.equals(hproseReturnType)){
            return new String((byte[])originValue);
        }
        else
        if(HPROSE_RETURN_TYPE_MAP.equals(hproseReturnType) ){
            EntityData data =new EntityData();
            Map<String,Object> originMap =(Map<String,Object>)originValue;
            originMap.forEach((key,value)->{
                data.put(mapToOutputEntityData.get(key),value);
            });

            return data;

        }
        return null;
    }
}
