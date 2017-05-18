package org.jumpmind.metl.core.util;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.util.LinkedCaseInsensitiveMap;

import java.util.ArrayList;
import java.util.Iterator;

final public class GeneralUtils {

    private GeneralUtils() {
    }

    public static String replaceSpecialCharacters(String value) {
        value = value.replaceAll("[\\s]", "_");
        value = value.replaceAll("[^a-zA-Z0-9_\\.]", "");
        return value;
    }

//mysql colmn name lower case error
    public static void columnNameToUpperCase(Table table){
        Column[]  columns =table.getColumns();
        if(columns!=null && columns.length>0){
            for(Column c :columns){
                c.setName(c.getName().toUpperCase());
            }
        }
    }

    public static void columnNameToUpperCase(LinkedCaseInsensitiveMap<Object> row){
        Iterator<java.util.Map.Entry<String, Object>> it=row.entrySet().iterator();
        ArrayList<String > keys =new ArrayList<String >();
        while(it.hasNext()){
                 keys.add(it.next().getKey());
        }
        for(String key :keys){
                 row.put(key.toUpperCase() ,row.get(key) );

        }
    }
}
