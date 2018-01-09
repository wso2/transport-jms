/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Test case for queue polling with selectors.
 */
public class QueueClientPollingWithSelectorTestCase {
    private static final Logger logger = LoggerFactory.getLogger(QueueTopicAutoAckListeningTestCase.class);

    private final String queueName = "pollingQueue";

    private JMSServer jmsServer;

    private Map<String, String> jmsClientParameters;

    private JMSClientConnector jmsClientConnectorQueue;

    private Connection connection;

    private String correlationId1 = "12345abcd";

    private String correlationId2 = "12345xyz";

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

        //publish 10 messages to the queue with correlationId1
        publishToQueue(correlationId1, 10);
        //publish 5 messages to the queue with correlationId2
        publishToQueue(correlationId2, 5);

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
          description = "Queue Polling with selectors test case")
    public void queuePollingTestCase()
            throws JMSConnectorException, NoSuchFieldException, IllegalAccessException, InterruptedException,
            JMSException {

        int receivedMsgCount1 = 0;
        // this should receive 10 messages but loop will goon for two more iterations to see if any more messages comes
        for (int i = 0; i < 12; i++) {
            Message message = jmsClientConnectorQueue
                    .poll(queueName, 500, "JMSCorrelationID = '" + correlationId1 + "'");
            if (message != null) {
                receivedMsgCount1++;
            }
        }

        int receivedMsgCount2 = 0;
        // this should receive 5 messages but loop will goon for two more iterations to see if any more messages comes
        for (int i = 0; i < 7; i++) {
            Message message = jmsClientConnectorQueue
                    .poll(queueName, 500, "JMSCorrelationID = '" + correlationId2 + "'");
            if (message != null) {
                receivedMsgCount2++;
            }
        }

        JMSTestUtils.closeResources((JMSClientConnectorImpl) jmsClientConnectorQueue);

        Assert.assertEquals(receivedMsgCount1, 10,
                "JMS Client selector polling receiver one expected message count " + 10 + " , received message count "
                        + receivedMsgCount1);
        Assert.assertEquals(receivedMsgCount2, 5,
                "JMS Client selector polling receiver two expected message count " + 5 + " , received message count "
                        + receivedMsgCount2);
    }

    @AfterClass(groups = "jmsPolling",
                description = "Shutting down the server by closing the only connection")
    public void cleanup() throws JMSException {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Publish messages to a queue.
     *
     * @param correlationId correlationId of the message.
     * @param count number of messages.
     * @throws JMSException thrown if error in jms resource creation or publishing.
     * @throws InterruptedException thrown if interruption while sleeping.
     */
    private void publishToQueue(String correlationId, int count) throws JMSException {
        Connection connection = null;
        Session session = null;
        MessageProducer producer = null;
        try {
            connection = jmsServer.createConnection(null, null);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            producer = session.createProducer(session.createQueue(queueName));
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            logger.info("Publishing " + count + " messages to the queue: " + queueName);
            for (int index = 0; index < count; index++) {
                String queueText = "Queue Message : " + (index + 1);
                TextMessage queueMessage = session.createTextMessage(queueText);
                queueMessage.setJMSCorrelationID(correlationId);
                producer.send(queueMessage);
            }
        } catch (JMSException e) {
            throw e;
        } finally {
            if (producer != null) {
                producer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }
}
