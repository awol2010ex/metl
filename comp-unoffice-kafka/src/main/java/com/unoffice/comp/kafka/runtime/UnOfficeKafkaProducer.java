package com.unoffice.comp.kafka.runtime;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.runtime.EntityData;
import org.jumpmind.metl.core.runtime.EntityDataMessage;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.component.AbstractComponentRuntime;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;

import java.util.ArrayList;
import java.util.Properties;

/**
 * Created by User on 2016/12/21.
 */
public class UnOfficeKafkaProducer extends AbstractComponentRuntime {
    public final static String TYPE = "UnOffice Kafka Producer";

    public static final String SETTING_CONTROL_MESSAGE_ON_TEXT_SEND = "control.message.on.text.send";



    public static final String KEY_SERIALIZER = "key.serializer";

    public static final String VALUE_SERIALIZER = "value.serializer";

    //producer params --start
    public static final String BOOTSTRAP_SERVERS = "bootstrap.servers";
    public static final String ACKS = "acks";
    public static final String RETRIES ="retries";
    public static final String BATCH_SIZE ="batch.size";
    public static final String LINGER_MS ="linger.ms";
    public static final String BUFFER_MEMORY ="buffer.memory";
    //producer params --end

    public static final String KEY_ATTRIBUTE_NAME="key.attribute.name";
    public static final String VALUE_ATTRIBUTE_NAME="value.attribute.name";

    String runWhen = PER_UNIT_OF_WORK;


    int textRowsPerMessage;

    boolean controlMessageOnTextSend;



    String keySerializer;
    String valueSerializer;

    //producer params --start
    String bootstrapServers;
    String acks;
    Integer retries;
    Long batchSize;
    Integer lingerMs;
    Long bufferMemory;
    //producer params --end

    String topic;



    String keyAttributeName ;
    String valueAttributeName ;
    @Override
    public void start() {

        super.start();
        textRowsPerMessage = context.getFlowStep().getComponent().getInt(ROWS_PER_MESSAGE, 1000);
        controlMessageOnTextSend = context.getFlowStep().getComponent().getBoolean(SETTING_CONTROL_MESSAGE_ON_TEXT_SEND, false);
        runWhen = getComponent().get(RUN_WHEN, PER_UNIT_OF_WORK);

        if (getInputModel() == null) {
            throw new IllegalStateException("A Producer writer must have an input model defined");
        }
    }

    @Override
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {
        //producer params --start
        bootstrapServers = getComponent().get(BOOTSTRAP_SERVERS, "localhost:9092");
        bootstrapServers = resolveParamsAndHeaders(bootstrapServers, inputMessage);

        keySerializer = getComponent().get(KEY_SERIALIZER, "org.apache.kafka.common.serialization.StringSerializer");
        keySerializer = resolveParamsAndHeaders(keySerializer, inputMessage);

        valueSerializer = getComponent().get(VALUE_SERIALIZER, "org.apache.kafka.common.serialization.StringSerializer");
        valueSerializer = resolveParamsAndHeaders(valueSerializer, inputMessage);


        acks =getComponent().get(ACKS ,"all");
        retries =getComponent().getInt(RETRIES,0);
        batchSize =getComponent().getLong(BATCH_SIZE ,16384);
        lingerMs =getComponent().getInt(LINGER_MS,1);
        bufferMemory =getComponent().getLong(BUFFER_MEMORY ,33554432);
        //producer params --end
        topic = getComponent().get("topic", "topic1");
        topic =resolveParamsAndHeaders(topic , inputMessage);



        //attribute name of model
        keyAttributeName =getComponent().get(KEY_ATTRIBUTE_NAME);
        valueAttributeName =getComponent().get(VALUE_ATTRIBUTE_NAME);


        Model inputModel = getInputModel();
        final String keyAttributeId =inputModel.getAttributesByName(keyAttributeName).get(0).getId();
        final String valueAttributeId =inputModel.getAttributesByName(valueAttributeName).get(0).getId();
            if (inputMessage instanceof EntityDataMessage) {
                ArrayList<EntityData> inputRows = ((EntityDataMessage) inputMessage).getPayload();
                if(inputRows!=null && inputRows.size()>0){

                    Properties props = new Properties();
                    props.put("bootstrap.servers", bootstrapServers);
                    props.put("acks", acks);
                    props.put("retries", retries);
                    props.put("batch.size", batchSize.intValue());
                    props.put("linger.ms", lingerMs);
                    props.put("buffer.memory",bufferMemory);
                    props.put("key.serializer", keySerializer);
                    props.put("value.serializer", valueSerializer);

                    final Producer<String, String> producer = new KafkaProducer<>(props);
                    try {

                        inputRows.forEach(row -> {
                            producer.send(new ProducerRecord<>(topic, String.valueOf(row.get(keyAttributeId)), String.valueOf(row.get(valueAttributeId))));
                        });
                    }catch(Exception e){
                        log.error("",e);
                    } finally {
                        if(producer!=null){
                            producer.close();
                        }

                        if (PER_MESSAGE.equals(runWhen) && controlMessageOnTextSend) {
                            callback.sendControlMessage();
                        }
                    }
                }
            }

    }

    @Override
    public boolean supportsStartupMessages() {
        return false;
    }

}
