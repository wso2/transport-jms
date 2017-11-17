/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.wso2.carbon.transport.jms.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.transport.jms.exception.JMSConnectorException;
import org.wso2.carbon.transport.jms.factory.JMSClientConnectionFactory;
import org.wso2.carbon.transport.jms.utils.JMSConstants;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.naming.Context;

/**
 * Singleton class to manage JMSConnectionFactories.
 */
public class JMSConnectionFactoryManager {

    private static final Logger logger = LoggerFactory.getLogger(JMSConnectionFactoryManager.class);

    private static JMSConnectionFactoryManager jmsConnectionFactoryManager = new JMSConnectionFactoryManager();

    private Map<String, JMSClientConnectionFactory> connectionFactoryMap = null;

    private JMSConnectionFactoryManager() {
        connectionFactoryMap = new HashMap<>();
    }

    /**
     * Compare two values preventing NPEs.
     */
    private static boolean equals(Object s1, Object s2) {
        return s1 == s2 || s1 != null && s1.equals(s2);
    }

    /**
     * Get an instance of this Singleton class.
     *
     * jmsConnectionFactoryManager object has initialized in the class level to avoid unnecessary synchronization
     * overhead in runtime.
     *
     * @return instance of {@link JMSConnectionFactoryManager}
     */
    public static JMSConnectionFactoryManager getInstance() {
        return jmsConnectionFactoryManager;
    }

    /**
     * Get the JMSServerConnectionFactory against the passed parameters. Return if it already exists, create new if not.
     *
     * @param properties JMS properties.
     * @return JMSServerConnectionFactory.
     * @throws JMSConnectorException if an error occurs when creating the connection factory.
     */
    public synchronized JMSClientConnectionFactory getJMSConnectionFactory(Properties properties)
            throws JMSConnectorException {
        Iterator<String> it = connectionFactoryMap.keySet().iterator();
        JMSClientConnectionFactory jmsConnectionFactory;

        // if caching is disabled return a new Connection factory
        if (!isCached(properties)) {
            return new JMSClientConnectionFactory(properties, Boolean.FALSE);
        }

        while (it.hasNext()) {
            jmsConnectionFactory = connectionFactoryMap.get(it.next());
            Properties facProperties = jmsConnectionFactory.getProperties();

            if (equals(facProperties.getProperty(Context.INITIAL_CONTEXT_FACTORY),
                    properties.get(Context.INITIAL_CONTEXT_FACTORY)) && equals(
                    facProperties.getProperty(Context.PROVIDER_URL), properties.get(Context.PROVIDER_URL)) && equals(
                    facProperties.getProperty(Context.SECURITY_PRINCIPAL), properties.get(Context.SECURITY_PRINCIPAL))
                    && equals(facProperties.getProperty(Context.SECURITY_CREDENTIALS),
                    properties.get(Context.SECURITY_CREDENTIALS)) && equals(
                    facProperties.getProperty(JMSConstants.PARAM_ACK_MODE),
                    properties.get(JMSConstants.PARAM_ACK_MODE)) && equals(
                    facProperties.getProperty(JMSConstants.PARAM_CONNECTION_FACTORY_TYPE),
                    properties.get(JMSConstants.PARAM_CONNECTION_FACTORY_TYPE))) {
                return jmsConnectionFactory;
            }
        }

        jmsConnectionFactory = new JMSClientConnectionFactory(properties, Boolean.TRUE);

        if (jmsConnectionFactory.isClientCaching()) {
            connectionFactoryMap.put(UUID.randomUUID().toString(), jmsConnectionFactory);
        }
        return jmsConnectionFactory;
    }

    /**
     * Check if the caching is set in the properties.
     * @param properties Connection factory properties.
     * @return false is the caching is disabled, true otherwise.
     */
    private boolean isCached(Properties properties) {
        String cacheLevel = properties.getProperty(JMSConstants.PARAM_JMS_CACHING);
        // We need to check this following because default should be caching enabled
        if (null != cacheLevel && !cacheLevel.isEmpty()) {
            return Boolean.parseBoolean(cacheLevel);
        } else {
            return Boolean.TRUE;
        }
    }
}
