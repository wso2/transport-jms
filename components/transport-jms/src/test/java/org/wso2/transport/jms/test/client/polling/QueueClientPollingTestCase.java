/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.transport.jms.test.client.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.transport.jms.contract.JMSClientConnector;
import org.wso2.transport.jms.exception.JMSConnectorException;
import org.wso2.transport.jms.impl.JMSConnectorFactoryImpl;
import org.wso2.transport.jms.sender.JMSClientConnectorImpl;
import org.wso2.transport.jms.test.server.QueueTopicAutoAckListeningTestCase;
import org.wso2.transport.jms.test.util.JMSServer;
import org.wso2.transport.jms.test.util.JMSTestConstants;
import org.wso2.transport.jms.test.util.JMSTestUtils;
import org.wso2.transport.jms.utils.JMSConstants;

import java.util.Map;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;

/**
 * Test case for queue polling.
 */
public class QueueClientPollingTestCase {
    private static final Logger logger = LoggerFactory.getLogger(QueueTopicAutoAckListeningTestCase.class);
    private final String queueName = "pollingQueue";

    private JMSServer jmsServer;

    private Map<String, String> jmsClientParameters;

    private JMSClientConnector jmsClientConnectorQueue;

    private Connection connection = null;

    /**
     * Starts the JMS Server, and publish messages.
     *
     * @throws JMSConnectorException Client Connector Exception.
     */
    @BeforeClass(groups = "jmsPolling",
                 description = "Setting up the server, publish messages")
    public void setUp() throws JMSConnectorException, InterruptedException, JMSException {
        jmsClientParameters = JMSTestUtils.
                createJMSParameterMap(JMSTestConstants.QUEUE_NAME, JMSTestConstants.QUEUE_CONNECTION_FACTORY,
                        JMSConstants.DESTINATION_TYPE_QUEUE, JMSConstants.AUTO_ACKNOWLEDGE_MODE);
        jmsServer = new JMSServer();
        jmsServer.startServer();

        // this will make the broker start and keep on running until the test is over
        connection = jmsServer.createConnection(null, null);
        connection.start();

        //publish 10 messages to the queue
        jmsServer.publishMessagesToQueue(queueName, 10);

        jmsClientConnectorQueue = new JMSConnectorFactoryImpl().createClientConnector(jmsClientParameters);
    }

    /**
     * This test will publish 10 messages to the provided queue and consume them using JMS transport client polling
     * interface and validate the number of produced messages.
     *
     * @throws JMSConnectorException  Error when polling messages through JMS transport connector.
     * @throws JMSException           Error when running the test message consumer.
     * @throws NoSuchFieldException   Error when accessing the private field.
     * @throws IllegalAccessException Error when accessing the private field.
     */
    @Test(groups = "jmsPolling",
          description = "Queue Polling test case")
    public void queuePollingTestCase()
            throws JMSConnectorException, NoSuchFieldException, IllegalAccessException, InterruptedException,
            JMSException {

        int receivedMsgCount = 0;
        for (int i = 0; i < 10; i++) {
            Message message = jmsClientConnectorQueue.poll(queueName, 1000);
            if (message != null) {
                receivedMsgCount++;
            }
        }

        JMSTestUtils.closeResources((JMSClientConnectorImpl) jmsClientConnectorQueue);

        Assert.assertEquals(receivedMsgCount, 10,
                "JMS Client polling receiver expected message count " + 10 + " , received message count "
                        + receivedMsgCount);
    }

    @AfterClass(groups = "jmsPolling",
                description = "Shutting down the server by closing the only connection")
    public void cleanup() throws JMSException {
        if (connection != null) {
            connection.close();
        }
    }
}
