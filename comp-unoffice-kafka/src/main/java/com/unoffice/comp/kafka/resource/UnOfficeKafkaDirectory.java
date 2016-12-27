package com.unoffice.comp.kafka.resource;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jumpmind.metl.core.runtime.resource.AbstractDirectory;
import org.jumpmind.properties.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * UnOffice Kafka directory
 * Created by User on 2016/12/26.
 */
public class UnOfficeKafkaDirectory extends AbstractDirectory {
    public static final String BOOTSTRAP_SERVERS = "bootstrap.servers";
    /*Consumer props --start*/
    public static final String GROUP_ID = "group.id";
    public static final String ENABLE_AUTO_COMMIT = "enable.auto.commit";
    public static final String AUTO_COMMIT_INTERVAL_MS = "auto.commit.interval.ms";
    public static final String SESSION_TIMEOUT_MS = "session.timeout.ms";
    public static final String KEY_DESERIALIZER = "key.deserializer";
    public static final String VALUE_DESERIALIZER = "value.deserializer";
    /*Producer props --start*/
    public static final String ACKS = "acks";
    public static final String RETRIES = "retries";
 /*Consumer props --end*/
    public static final String BATCH_SIZE = "batch.size";
    public static final String LINGER_MS = "linger.ms";
    public static final String BUFFER_MEMORY = "buffer.memory";
    public static final String KEY_SERIALIZER = "key.serializer";
    public static final String VALUE_SERIALIZER = "value.serializer";
    /*Producer props --end*/
    public static final String TOPIC = "topic";
    public static final String MSG_TYPE_TEXT = "Text";

    public static final String MSG_TYPE_BYTES = "Byte";
    public static final String MSG_TYPE_OBJECT = "Object";
    public static final String MSG_TYPE_MAP = "Map";
    public static final String SETTING_MESSAGE_TYPE_MAP_VALUE = "setting.message.type.map.value";
    public static final String SETTING_MESSAGE_TYPE = "setting.message.type";
    final static Logger logger = LoggerFactory.getLogger(UnOfficeKafkaDirectory.class);
    protected TypedProperties properties;
    KafkaConsumer<String, Object> consumer = null;
    Producer<String, Object> producer = null;
    String bootstrapServers;

    /*consumer props --start--*/
    String groupId;
    boolean enableAutoCommit;
    Integer autoCommitIntervalMs;
    Integer sessionTimeoutMs;
    String keyDeserializer;
    String valueDeserializer;
    /*consumer props --end--*/

    //producer params --start
    String acks;
    Integer retries;
    Long batchSize;
    Integer lingerMs;
    Long bufferMemory;
    String keySerializer;
    String valueSerializer;
    //producer params --end

    String topic;


    public UnOfficeKafkaDirectory(TypedProperties properties) {
        this.properties = properties;
    }


    public KafkaConsumer<String, Object> createConsumer() {

        if (consumer == null) {
            bootstrapServers = properties.get(BOOTSTRAP_SERVERS, "localhost:9092");
            groupId = properties.get(GROUP_ID, "test");
            enableAutoCommit = Boolean.parseBoolean(properties.get(ENABLE_AUTO_COMMIT, "true"));
            autoCommitIntervalMs = properties.getInt(AUTO_COMMIT_INTERVAL_MS, 1000);
            sessionTimeoutMs = properties.getInt(SESSION_TIMEOUT_MS, 30000);
            keyDeserializer = properties.get(KEY_DESERIALIZER, "org.apache.kafka.common.serialization.StringDeserializer");
            valueDeserializer = properties.get(VALUE_DESERIALIZER, "org.apache.kafka.common.serialization.StringDeserializer");
            topic = properties.get("topic", "topic1");

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrapServers);
            props.put("group.id", GROUP_ID);
            props.put("enable.auto.commit", String.valueOf(enableAutoCommit));
            props.put("auto.commit.interval.ms", String.valueOf(autoCommitIntervalMs));
            props.put("session.timeout.ms", String.valueOf(sessionTimeoutMs));
            props.put("key.deserializer", keyDeserializer);
            props.put("value.deserializer", valueDeserializer);


            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(topic));
        }

        return consumer;
    }

    public Producer<String, Object> createProducer() {
        if (producer == null) {
            //producer params --start
            bootstrapServers = properties.get(BOOTSTRAP_SERVERS, "localhost:9092");
            keySerializer = properties.get(KEY_SERIALIZER, "org.apache.kafka.common.serialization.StringSerializer");
            valueSerializer = properties.get(VALUE_SERIALIZER, "org.apache.kafka.common.serialization.StringSerializer");


            acks = properties.get(ACKS, "all");
            retries = properties.getInt(RETRIES, 0);
            batchSize = properties.getLong(BATCH_SIZE, 16384);
            lingerMs = properties.getInt(LINGER_MS, 1);
            bufferMemory = properties.getLong(BUFFER_MEMORY, 33554432);
            //producer params --end
            topic = properties.get("topic", "topic1");

            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrapServers);
            props.put("acks", acks);
            props.put("retries", retries);
            props.put("batch.size", batchSize.intValue());
            props.put("linger.ms", lingerMs);
            props.put("buffer.memory", bufferMemory);
            props.put("key.serializer", keySerializer);
            props.put("value.serializer", valueSerializer);

            producer = new KafkaProducer<String, Object>(props);
        }

        return producer;
    }

    @Override
    public boolean supportsInputStream() {
        return true;
    }

    @Override
    public InputStream getInputStream(String relativePath, boolean mustExist) {
        return getInputStream(relativePath, mustExist, false);
    }

    @Override
    public InputStream getInputStream(String relativePath, boolean mustExist, boolean closeSession) {
        try {

            consumer = createConsumer();

            StringBuilder builder = new StringBuilder();
            try {
                ConsumerRecords<String, Object> records = consumer.poll(1000);
                if (records != null && records.count() > 0) {
                    for (ConsumerRecord<String, Object> record : records) {
                        if (record != null) {
                            Object message = record.value();
                            if (message instanceof String) {

                                String text = (String) message;
                                if (isNotBlank(text)) {
                                    builder.append(text);
                                }
                            } else if (message instanceof Map) {
                                Map mapMessage = (Map) message;
                                String keyName = properties.get(SETTING_MESSAGE_TYPE_MAP_VALUE, "Payload");
                                String text = String.valueOf(mapMessage.get(keyName));
                                if (isNotBlank(text)) {
                                    builder.append(text);
                                }
                            } else if (message instanceof byte[]) {
                                builder.append(new String((byte[]) message));
                            } else {
                                String text = String.valueOf(message);
                                if (isNotBlank(text)) {
                                    builder.append(text);
                                }
                            }
                        }
                    }
                }

            } finally {

                if (closeSession) {
                    UnOfficeKafkaDirectory.this.close();
                }
            }
            return new ByteArrayInputStream(builder.toString().getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void close(AutoCloseable toClose) {
        if (toClose != null) {
            try {
                toClose.close();
            } catch (Exception ex) {
                logger.error("", ex);
            }
        }
    }


    @Override
    public boolean supportsOutputStream() {
        return true;
    }

    @Override
    public OutputStream getOutputStream(String relativePath, boolean mustExist) {
        return new CloseableOutputStream(relativePath, false);
    }

    @Override
    public OutputStream getOutputStream(String relativePath, boolean mustExist, boolean closeSession, boolean append) {
        return new CloseableOutputStream(relativePath, closeSession);
    }


    @Override
    public void close() {
        close(consumer);
        consumer = null;
        close(producer);
        producer = null;
    }

    @Override
    public void connect() {
    }

    class CloseableOutputStream extends ByteArrayOutputStream {

        String relativePath;

        Producer<String, Object> producer;

        boolean closeSession;

        public CloseableOutputStream(String relativePath, boolean closeSession) {
            this.relativePath = relativePath;
            this.producer = createProducer();
        }

        @Override
        public void close() throws IOException {
            super.close();
            String text = new String(toByteArray());
            try {

                String msgType = properties.get(SETTING_MESSAGE_TYPE, MSG_TYPE_TEXT);
                ProducerRecord<String, Object> jmsMsg = null;
                if (MSG_TYPE_TEXT.equals(msgType)) {
                    jmsMsg = new ProducerRecord<String, Object>(topic, text);
                } else if (MSG_TYPE_BYTES.equals(msgType)) {
                    jmsMsg = new ProducerRecord<String, Object>(topic, toByteArray());
                } else if (MSG_TYPE_OBJECT.equals(msgType)) {
                    jmsMsg = new ProducerRecord<String, Object>(topic, toByteArray());
                } else if (MSG_TYPE_MAP.equals(msgType)) {
                    String keyName = properties.get(SETTING_MESSAGE_TYPE_MAP_VALUE, "Payload");
                    Map<String, Object> msg = new HashMap<String, Object>();
                    msg.put(keyName, text);
                    jmsMsg = new ProducerRecord<String, Object>(topic, msg);
                }

                if (jmsMsg != null) {
                    producer.send(jmsMsg);
                }
            } catch (Exception e) {
                UnOfficeKafkaDirectory.this.close();
                throw new RuntimeException(e);
            } finally {
                if (closeSession) {
                    UnOfficeKafkaDirectory.this.close();
                }
            }
        }
    }

}
