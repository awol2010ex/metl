package com.unoffice.comp.kafka.runtime;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.jumpmind.metl.core.runtime.ControlMessage;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.component.AbstractComponentRuntime;
import org.jumpmind.metl.core.runtime.flow.ISendMessageCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.KafkaConsumer;
/** UnOffice Kafka Reader
 * Created by User on 2016/12/14.
 */
public class UnOfficeKafkaClient extends AbstractComponentRuntime {

    public static final String TYPE = "UnOffice Kafka Reader";

    public static final String SETTING_CONTROL_MESSAGE_ON_TEXT_SEND = "control.message.on.text.send";

    public static final String BOOTSTRAP_SERVERS="bootstrap.servers";
    public static final String GROUP_ID="group.id";
    public static final String ENABLE_AUTO_COMMIT="enable.auto.commit";

    public static final String AUTO_COMMIT_INTERVAL_MS="auto.commit.interval.ms";

    public static final String SESSION_TIMEOUT_MS="session.timeout.ms";

    public static final String KEY_DESERIALIZER="key.deserializer";

    public static final String VALUE_DESERIALIZER="value.deserializer";
    String runWhen = PER_UNIT_OF_WORK;

    int textRowsPerMessage;

    boolean controlMessageOnTextSend;


    String bootstrapServers;

    String groupId ;
    boolean enableAutoCommit;

    Integer autoCommitIntervalMs;

    Integer sessionTimeoutMs;

    String keyDeserializer;
    String valueDeserializer;

    String topic ;
    @Override
    public void start() {
        textRowsPerMessage = context.getFlowStep().getComponent().getInt(ROWS_PER_MESSAGE, 1000);
        controlMessageOnTextSend = context.getFlowStep().getComponent().getBoolean(SETTING_CONTROL_MESSAGE_ON_TEXT_SEND, false);
        runWhen = getComponent().get(RUN_WHEN, PER_UNIT_OF_WORK);

    }
    @Override
    public void handle(Message inputMessage, ISendMessageCallback callback, boolean unitOfWorkBoundaryReached) {
        
        bootstrapServers = getComponent().get(BOOTSTRAP_SERVERS,"localhost:9092");

        groupId= getComponent().get(GROUP_ID,"test");
        enableAutoCommit =getComponent().getBoolean(ENABLE_AUTO_COMMIT,true);

        autoCommitIntervalMs =getComponent().getInt(AUTO_COMMIT_INTERVAL_MS,1000);

        sessionTimeoutMs =getComponent().getInt(SESSION_TIMEOUT_MS,1000);

        keyDeserializer =getComponent().get(KEY_DESERIALIZER,"org.apache.kafka.common.serialization.StringDeserializer");

        valueDeserializer=getComponent().get(VALUE_DESERIALIZER,"org.apache.kafka.common.serialization.StringDeserializer");

        topic =getComponent().get("topic","topic1");
		
		
		
		if ((PER_UNIT_OF_WORK.equals(runWhen) && inputMessage instanceof ControlMessage)
                || (!PER_UNIT_OF_WORK.equals(runWhen) && !(inputMessage instanceof ControlMessage))) {

            int linesInMessage = 0;
            ArrayList<String> payload = new ArrayList<String>();

            Properties props = new Properties();
            props.put("bootstrap.servers",bootstrapServers);
            props.put("group.id", GROUP_ID);
            props.put("enable.auto.commit", String.valueOf(enableAutoCommit));
            props.put("auto.commit.interval.ms", String.valueOf(autoCommitIntervalMs));
            props.put("session.timeout.ms", String.valueOf(sessionTimeoutMs));
            props.put("key.deserializer", keyDeserializer);
            props.put("value.deserializer", valueDeserializer);

            KafkaConsumer<String, Object> consumer =null;
            try {
                consumer = new KafkaConsumer<>(props);
                consumer.subscribe(Arrays.asList(topic));

                ConsumerRecords<String, Object> records = consumer.poll(textRowsPerMessage);
                for (ConsumerRecord<String, Object> record : records) {
                    payload.add(String.valueOf(record.value()).trim());
                }
                consumer.close();
            }catch(Exception e){
                log.error("",e);
            }finally {
                if (payload.size() > 0) {
                    callback.sendTextMessage(null, payload);
                    if (PER_MESSAGE.equals(runWhen) && controlMessageOnTextSend) {
                        callback.sendControlMessage();
                    }
                }
            }


        }
    }

    @Override
    public boolean supportsStartupMessages() {
        return true;
    }


    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "test");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("topic1"));
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(100);
            for (ConsumerRecord<String, String> record : records)
                System.out.printf("offset = %d, key = %s, value = %s\n", record.offset(), record.key(), record.value());
        }
    }
}
