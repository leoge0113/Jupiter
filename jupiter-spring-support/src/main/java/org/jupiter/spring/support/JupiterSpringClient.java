/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jupiter.spring.support;

import org.jupiter.common.util.ExceptionUtil;
import org.jupiter.common.util.Lists;
import org.jupiter.common.util.Strings;
import org.jupiter.common.util.SystemPropertyUtil;
import org.jupiter.registry.RegistryService;
import org.jupiter.rpc.DefaultClient;
import org.jupiter.rpc.JClient;
import org.jupiter.transport.JConnection;
import org.jupiter.transport.JConnector;
import org.jupiter.transport.UnresolvedAddress;
import org.springframework.beans.factory.InitializingBean;

import java.util.Collections;
import java.util.List;

import static org.jupiter.common.util.Preconditions.checkNotNull;

/**
 * jupiter client wrapper, 负责初始化并启动客户端.
 *
 * jupiter
 * org.jupiter.spring.support
 *
 * @author jiachun.fjc
 */
public class JupiterSpringClient implements InitializingBean {

    private JClient client;
    private String appName;
    private RegistryService.RegistryType registryType;
    private JConnector<JConnection> connector;

    private String registryServerAddresses;                             // 注册中心地址 [host1:port1,host2:port2....]
    private String providerServerAddresses;                             // IP直连到providers [host1:port1,host2:port2....]
    private List<UnresolvedAddress> providerServerUnresolvedAddresses;  // IP直连的地址列表
    private boolean hasRegistryServer;                                  // true: 需要连接注册中心; false: IP直连方式

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    private void init() {
        client = new DefaultClient(appName, registryType);
        if (connector == null) {
            connector = createDefaultConnector();
        }
        client.withConnector(connector);

        // 注册中心
        if (Strings.isNotBlank(registryServerAddresses)) {
            client.connectToRegistryServer(registryServerAddresses);
            hasRegistryServer = true;
        }

        if (!hasRegistryServer) {
            // IP直连方式
            if (Strings.isNotBlank(providerServerAddresses)) {
                String[] array = Strings.split(providerServerAddresses, ',');
                providerServerUnresolvedAddresses = Lists.newArrayList();
                for (String s : array) {
                    String[] addressStr = Strings.split(s, ':');
                    String host = addressStr[0];
                    int port = Integer.parseInt(addressStr[1]);
                    UnresolvedAddress address = new UnresolvedAddress(host, port);
                    providerServerUnresolvedAddresses.add(address);

                    JConnector<JConnection> connector = client.connector();
                    JConnection connection = connector.connect(address, true); // 异步建立连接
                    connector.connectionManager().manage(connection);
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                client.shutdownGracefully();
            }
        });
    }

    public JClient getClient() {
        return client;
    }

    public void setClient(JClient client) {
        this.client = client;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public RegistryService.RegistryType getRegistryType() {
        return registryType;
    }

    public void setRegistryType(String registryType) {
        this.registryType = RegistryService.RegistryType.parse(registryType);
    }

    public JConnector<JConnection> getConnector() {
        return connector;
    }

    public void setConnector(JConnector<JConnection> connector) {
        this.connector = connector;
    }

    public String getRegistryServerAddresses() {
        return registryServerAddresses;
    }

    public void setRegistryServerAddresses(String registryServerAddresses) {
        this.registryServerAddresses = registryServerAddresses;
    }

    public String getProviderServerAddresses() {
        return providerServerAddresses;
    }

    public void setProviderServerAddresses(String providerServerAddresses) {
        this.providerServerAddresses = providerServerAddresses;
    }

    public List<UnresolvedAddress> getProviderServerUnresolvedAddresses() {
        return providerServerUnresolvedAddresses == null
                ?
                Collections.<UnresolvedAddress>emptyList()
                :
                providerServerUnresolvedAddresses;
    }

    public boolean isHasRegistryServer() {
        return hasRegistryServer;
    }

    @SuppressWarnings("unchecked")
    private JConnector<JConnection> createDefaultConnector() {
        JConnector<JConnection> defaultConnector = null;
        try {
            String className = SystemPropertyUtil
                    .get("jupiter.io.default.connector", "org.jupiter.transport.netty.JNettyTcpConnector");
            Class<?> clazz = Class.forName(className);
            defaultConnector = (JConnector<JConnection>) clazz.newInstance();
        } catch (Exception e) {
            ExceptionUtil.throwException(e);
        }
        return checkNotNull(defaultConnector, "default connector");
    }
}
