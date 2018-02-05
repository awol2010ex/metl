/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
