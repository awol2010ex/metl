package com.unoffice.comp.mongodb.resource;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.jumpmind.metl.core.runtime.resource.AbstractResourceRuntime;
import org.jumpmind.properties.TypedProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by User on 2017/1/28.
 */
public class UnOfficeMongoDBResource extends AbstractResourceRuntime {

    public static final String TYPE = "UnOffice MongoDB";
    /*mongodb params*/
    private String host ;
    private Integer port ;
    private String db ;
    private String user ;
    private String pwd;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDb() {
        return db;
    }

    public void setDb(String db) {
        this.db = db;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public void setMongoClient(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    private MongoClient mongoClient =null;
    @Override
    protected void start(TypedProperties properties) {


        if(mongoClient!=null ){
            mongoClient.close();
            mongoClient=null;
        }
        if(mongoClient==null) {
            host = properties.get("host");
            port = properties.getInt("port", 27017);
            db = properties.get("db");
            user = properties.get("user");
            pwd = properties.get("pwd");

            ServerAddress serverAddress = new ServerAddress(host, port);
            List<ServerAddress> addrs = new ArrayList<ServerAddress>();
            addrs.add(serverAddress);

            MongoCredential credential = MongoCredential.createScramSha1Credential(user, db, pwd.toCharArray());
            List<MongoCredential> credentials = new ArrayList<MongoCredential>();
            credentials.add(credential);

            mongoClient = new MongoClient(addrs, credentials);
        }
    }
    @Override
    public <T> T reference() {
        return (T)mongoClient;
    }


    @Override
    public void stop() {
        if(mongoClient!=null)
        try {
            mongoClient.close();
        } catch (Exception e) {
        } finally {
            mongoClient = null;
        }
    }
}
