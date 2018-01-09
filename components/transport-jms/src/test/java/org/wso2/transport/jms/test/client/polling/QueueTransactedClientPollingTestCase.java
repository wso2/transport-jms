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
import org.wso2.transport.jms.sender.wrappers.SessionWrapper;
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
 * Test case for queue polling tx.
 */
public class QueueTransactedClientPollingTestCase {
    private static final Logger logger = LoggerFactory.getLogger(QueueTopicAutoAckListeningTestCase.class);

    private static final int timeout = 1000;

    private final String queueName = "txPollingQueue";

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
                createJMSParameterMap(queueName, JMSTestConstants.QUEUE_CONNECTION_FACTORY,
                        JMSConstants.DESTINATION_TYPE_QUEUE, JMSConstants.SESSION_TRANSACTED_MODE);
        jmsServer = new JMSServer();
        jmsServer.startServer();

        // this will make the broker start and keep on running until the test is over
        connection = jmsServer.createConnection(null, null);
        connection.start();

        //publish a message to the queue
        jmsServer.publishMessagesToQueue(queueName, 1);

        jmsClientConnectorQueue = new JMSConnectorFactoryImpl().createClientConnector(jmsClientParameters);
    }

    /**
     * Test Rollback operation.
     *
     * @throws JMSConnectorException  Error when polling messages through JMS transport connector.
     * @throws JMSException           Error when running the test message consumer.
     * @throws NoSuchFieldException   Error when accessing the private field.
     * @throws IllegalAccessException Error when accessing the private field.
     */
    @Test(groups = "jmsPolling",
          description = "Queue Polling rollback test case")
    public void queuePollingRollbackTestCase()
            throws JMSConnectorException, NoSuchFieldException, IllegalAccessException, InterruptedException,
            JMSException {

        SessionWrapper sessionWrapper = jmsClientConnectorQueue.acquireSession();

        // poll and rollback
        jmsClientConnectorQueue.pollTransacted(queueName, timeout, sessionWrapper, null);
        sessionWrapper.getSession().rollback();

        // poll again
        Message message = jmsClientConnectorQueue.pollTransacted(queueName, timeout, sessionWrapper, null);
        sessionWrapper.getSession().rollback();

        Assert.assertNotNull(message, "JMSClientConnector transacted polling rollback failed");
    }

    /**
     * Test the commit operation.
     *
     * @throws JMSConnectorException  Error when polling messages through JMS transport connector.
     * @throws JMSException           Error when running the test message consumer.
     * @throws NoSuchFieldException   Error when accessing the private field.
     * @throws IllegalAccessException Error when accessing the private field.
     */
    @Test(groups = "jmsPolling",
          dependsOnMethods = { "queuePollingRollbackTestCase" },
          description = "Queue Polling commit test case")
    public void queuePollingCommitTestCase()
            throws JMSConnectorException, NoSuchFieldException, IllegalAccessException, InterruptedException,
            JMSException {

        SessionWrapper sessionWrapper = jmsClientConnectorQueue.acquireSession();

        // poll and commit
        jmsClientConnectorQueue.pollTransacted(queueName, timeout, sessionWrapper, null);
        sessionWrapper.getSession().commit();

        // poll again
        Message message = jmsClientConnectorQueue.pollTransacted(queueName, timeout, sessionWrapper, null);

        Assert.assertNull(message, "JMSClientConnector transacted polling commit failed");
    }

    @AfterClass(groups = "jmsPolling",
                description = "Shutting down the server by closing the only connection")
    public void cleanup() throws JMSException, IllegalAccessException, JMSConnectorException, NoSuchFieldException {
        if (connection != null) {
            connection.close();
        }
        JMSTestUtils.closeResources((JMSClientConnectorImpl) jmsClientConnectorQueue);
    }
}
