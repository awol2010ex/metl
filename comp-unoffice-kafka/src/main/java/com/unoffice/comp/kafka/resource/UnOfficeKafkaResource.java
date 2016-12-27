package com.unoffice.comp.kafka.resource;

import org.jumpmind.metl.core.runtime.resource.AbstractResourceRuntime;
import org.jumpmind.metl.core.runtime.resource.IDirectory;
import org.jumpmind.properties.TypedProperties;

/**
 * UnOffice Kafka resource
 * Created by User on 2016/12/26.
 */
public class UnOfficeKafkaResource extends AbstractResourceRuntime {

    public static final String TYPE = "UNOFFICE_KAFKA_RESOURCE";


    IDirectory streamableResource;

    TypedProperties properties;



    @Override
    public void stop() {
        if (streamableResource != null) {
            streamableResource.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T reference() {
        return (T) streamableResource;
    }


    @Override
    protected void start(TypedProperties properties) {
        this.properties = properties;

        streamableResource = new UnOfficeKafkaDirectory(properties);
    }


}
