/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 * <p>
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.ignite.resource;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import org.jumpmind.metl.core.runtime.resource.AbstractResourceRuntime;
import org.jumpmind.properties.TypedProperties;

import java.util.Arrays;

public class UnofficeIgniteCache extends AbstractResourceRuntime {

    public static final String TYPE = "UnofficeIgniteCache";


    public final static String CACHE_NAME ="cache.name";
    public final static String IP_ADDRESS = "ip.address";
    private Ignite igniteClient = null;

    private String ipAddress =null ;
    private String cacheName =null;

    @Override
    protected void start(TypedProperties properties) {

        if(igniteClient!=null ){
            igniteClient.close();
            igniteClient=null;
        }

        if(igniteClient==null) {
            Ignition.setClientMode(true);
            IgniteConfiguration cfg = new IgniteConfiguration();

            ipAddress = properties.get(IP_ADDRESS ,"");
            cacheName =properties.get(CACHE_NAME ,"");
            TcpDiscoveryMulticastIpFinder ipFinder = new TcpDiscoveryMulticastIpFinder();
            ipFinder.setAddresses(Arrays.asList(ipAddress.split(";")));
            TcpDiscoverySpi tcpDiscoverySpi = new TcpDiscoverySpi();
            tcpDiscoverySpi.setIpFinder(ipFinder);
            cfg.setDiscoverySpi(tcpDiscoverySpi);

            igniteClient = Ignition.start(cfg);
        }
    }

    @Override
    public void stop() {
        if (igniteClient != null) {
            igniteClient.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T reference() {
        return (T) igniteClient;
    }


    public String getCacheName(){
        return this.cacheName;
    }
}
