package org.jumpmind.metl.core.util;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;

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
}
