/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.transport.jms.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.jms.contract.JMSClientConnector;
import org.wso2.transport.jms.exception.JMSConnectorException;
import org.wso2.transport.jms.factory.JMSClientConnectionFactory;
import org.wso2.transport.jms.sender.wrappers.SessionWrapper;
import org.wso2.transport.jms.utils.JMSConstants;

import java.util.Map;
import java.util.Properties;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TopicPublisher;

/**
 * JMS sender Connector API Implementation. JMS transport sender is invoked through this API.
 */
public class JMSClientConnectorImpl implements JMSClientConnector {

    private static final Logger logger = LoggerFactory.getLogger(JMSClientConnectorImpl.class);

    private JMSClientConnectionFactory jmsConnectionFactory;

    public JMSClientConnectorImpl(Map<String, String> propertyMap) throws JMSConnectorException {
        try {
            Properties properties = new Properties();
            properties.putAll(propertyMap);

            jmsConnectionFactory = JMSConnectionFactoryManager.getInstance().getJMSConnectionFactory(properties);

        } catch (JMSConnectorException e) {
            throw new JMSConnectorException("Error connecting to JMS provider. " + e.getMessage(), e);
        }
    }

    @Override
    public boolean send(Message jmsMessage, String destinationName) throws JMSConnectorException {
        SessionWrapper sessionWrapper = null;
        try {
            if (!jmsConnectionFactory.isClientCaching()) {
                sendNonCached(jmsMessage, destinationName);
                return true;
            }
            sessionWrapper = jmsConnectionFactory.getSessionWrapper();
            Destination destination = jmsConnectionFactory
                    .createDestination(sessionWrapper.getSession(), destinationName);
            sendJMSMessage(destination, jmsMessage, sessionWrapper.getMessageProducer());
        } catch (JMSConnectorException e) {
            throw e;
        } catch (JMSException e) {
            throw new JMSConnectorException("JMS Send Failed with [ " + e.getMessage() + " ]", e);
        } catch (Exception e) {
            throw new JMSConnectorException("Error getting the session. " + e.getMessage(), e);
        } finally {
            if (sessionWrapper != null) {
                jmsConnectionFactory.returnSessionWrapper(sessionWrapper);
            }
        }
        return true;
    }

    @Override
    public boolean sendTransactedMessage(Message jmsMessage, String destinationName, SessionWrapper sessionWrapper)
            throws JMSConnectorException {
        Destination destination;
        try {
            destination = jmsConnectionFactory.createDestination(sessionWrapper.getSession(), destinationName);
            sendJMSMessage(destination, jmsMessage, sessionWrapper.getMessageProducer());
        } catch (JMSConnectorException e) {
            throw e;
        } catch (JMSException e) {
            throw new JMSConnectorException("JMS Send Failed with [" + e.getMessage() + " ]", e);
        }
        return true;
    }

    @Override
    @Deprecated
    public Message poll(String destinationName, int timeout) throws JMSConnectorException {
        // message selector is set to null
        return poll(destinationName, timeout, null);
    }

    @Override
    public Message poll(String destinationName, int timeout, String messageSelector) throws JMSConnectorException {
        SessionWrapper sessionWrapper = null;
        Message message;
        try {
            if (!jmsConnectionFactory.isClientCaching()) {
                Connection connection = null;
                Session session = null;
                try {
                    connection = jmsConnectionFactory.createConnection();
                    session = jmsConnectionFactory.createSession(connection);
                    connection.start();
                    message = receiveMessage(session, destinationName, timeout, messageSelector);
                } finally {
                    try {
                        jmsConnectionFactory.closeSession(session);
                        jmsConnectionFactory.closeConnection(connection);
                    } catch (JMSException e) {
                        throw new JMSConnectorException("Error closing JMS resources. " + e.getMessage(), e);
                    }
                }
            } else {
                sessionWrapper = jmsConnectionFactory.getSessionWrapper();
                message = receiveMessage(sessionWrapper.getSession(), destinationName, timeout, messageSelector);
            }
        } catch (JMSConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new JMSConnectorException("Error getting the session. " + e.getMessage(), e);
        } finally {
            if (sessionWrapper != null) {
                jmsConnectionFactory.returnSessionWrapper(sessionWrapper);
            }
        }
        return message;
    }

    @Override
    @Deprecated
    public Message pollTransacted(String destinationName, int timeout, SessionWrapper sessionWrapper)
            throws JMSConnectorException {
        // message selector is set to null
        return pollTransacted(destinationName, timeout, sessionWrapper, null);
    }

    @Override
    public Message pollTransacted(String destinationName, int timeout, SessionWrapper sessionWrapper,
            String messageSelector) throws JMSConnectorException {
        return receiveMessage(sessionWrapper.getSession(), destinationName, timeout, messageSelector);
    }

    @Override
    public Destination createDestination(String destinationName) throws JMSConnectorException {
        Destination destination;

        if (jmsConnectionFactory.isClientCaching()) {
            SessionWrapper sessionWrapper = null;
            try {
                sessionWrapper = jmsConnectionFactory.getSessionWrapper();
                destination = jmsConnectionFactory.createDestination(sessionWrapper.getSession(), destinationName);
            } catch (JMSConnectorException e) {
                throw e;
            } catch (Exception e) {
                throw new JMSConnectorException("Error acquiring the session. " + e.getMessage(), e);
            } finally {
                if (sessionWrapper != null) {
                    jmsConnectionFactory.returnSessionWrapper(sessionWrapper);
                }
            }
        } else {
            Connection connection = null;
            Session session = null;

            try {
                connection = jmsConnectionFactory.createConnection();
                session = jmsConnectionFactory.createSession(connection);
                destination = jmsConnectionFactory.createDestination(session, destinationName);
            } catch (JMSException e) {
                throw new JMSConnectorException("Error creating the JMS Destination. " + e.getMessage(), e);
            } finally {
                try {
                    jmsConnectionFactory.closeSession(session);
                    jmsConnectionFactory.closeConnection(connection);
                } catch (JMSException e) {
                    throw new JMSConnectorException("Error releasing the JMS resources. " + e.getMessage(), e);
                }
            }
        }
        return destination;
    }

    @Override
    public Message createMessage(String messageType) throws JMSConnectorException {

        Message jmsMessage;

        if (jmsConnectionFactory.isClientCaching()) {
            SessionWrapper sessionWrapper = null;
            try {
                sessionWrapper = jmsConnectionFactory.getSessionWrapper();
                jmsMessage = createJMSMessage(sessionWrapper.getSession(), messageType);
            } catch (JMSException e) {
                throw new JMSConnectorException("Error creating the JMS Message. " + e.getMessage(), e);
            } catch (Exception e) {
                throw new JMSConnectorException("Error acquiring the session JMS Message. " + e.getMessage(), e);
            } finally {
                if (sessionWrapper != null) {
                    jmsConnectionFactory.returnSessionWrapper(sessionWrapper);
                }
            }
        } else {
            Connection connection = null;
            Session session = null;

            try {
                connection = jmsConnectionFactory.createConnection();
                session = jmsConnectionFactory.createSession(connection);
                jmsMessage = createJMSMessage(session, messageType);
            } catch (JMSException e) {
                throw new JMSConnectorException("Error creating the JMS Message. " + e.getMessage(), e);
            } finally {
                try {
                    jmsConnectionFactory.closeSession(session);
                    jmsConnectionFactory.closeConnection(connection);
                } catch (JMSException e) {
                    throw new JMSConnectorException("Error releasing the JMS resources. " + e.getMessage(), e);
                }
            }
        }
        return jmsMessage;
    }

    @Override
    public SessionWrapper acquireSession() throws JMSConnectorException {
        SessionWrapper sessionWrapper;
        try {
            sessionWrapper = jmsConnectionFactory.getSessionWrapper();
        } catch (Exception e) {
            throw new JMSConnectorException("Error when acquiring the session. ", e);
        }
        return sessionWrapper;
    }

    @Override
    public void releaseSession(SessionWrapper sessionWrapper) throws JMSConnectorException {
        jmsConnectionFactory.returnSessionWrapper(sessionWrapper);
    }

    @Override
    public void closeConnectionFactory() throws JMSConnectorException {
        jmsConnectionFactory.closeJMSResources();
    }

    /**
     * Send the JMS Message using matching Message Sender implementation.
     *
     * @param destination JMS Queue/Topic.
     * @param message   JMS Message.
     * @param producer JMS Message Producer.
     * @throws JMSException Thrown when sending the message.
     */
    private void sendJMSMessage(Destination destination, Message message, MessageProducer producer)
            throws JMSException {

        if (JMSConstants.JMSDestinationType.QUEUE.equals(jmsConnectionFactory.getDestinationType())
                || !JMSConstants.JMS_SPEC_VERSION_1_0.equals(jmsConnectionFactory.getJmsSpec())) {
            producer.send(destination, message, message.getJMSDeliveryMode(), message.getJMSPriority(),
                    message.getJMSExpiration());
        } else {
            ((TopicPublisher) producer)
                    .send(destination, message, message.getJMSDeliveryMode(), message.getJMSPriority(),
                            message.getJMSExpiration());
        }
    }

    /**
     * Send the JMS Message by bypassing the Caching pool. This is used when the connector is created using caching
     * disabled.
     *
     * @param message JMS Message.
     * @param destinationName Name of the JMS queue/topic.
     * @throws JMSException Thrown when creating connection, session, messageProducer and destination.
     * @throws JMSConnectorException If the destination is not found, NameNotfound exceptions are notified through.
     * JMSConnectorExceptions.
     */
    private void sendNonCached(Message message, String destinationName) throws JMSException, JMSConnectorException {
        Connection connection = null;
        Session session = null;
        Destination destination;
        MessageProducer messageProducer = null;
        try {
            connection = jmsConnectionFactory.createConnection();
            session = jmsConnectionFactory.createSession(connection);
            destination = jmsConnectionFactory.createDestination(session, destinationName);
            messageProducer = session.createProducer(destination);
            sendJMSMessage(destination, message, messageProducer);
        } finally {
            jmsConnectionFactory.closeProducer(messageProducer);
            jmsConnectionFactory.closeSession(session);
            jmsConnectionFactory.closeConnection(connection);
        }
    }

    /**
     * Perform reception of a JMS Message using polling mechanism of JMS API.
     *
     * @param session JMS Session instance.
     * @param destinationName Name of the destination.
     * @param timeout blocking timeout value.
     * @param messageSelector message selector string.
     * @return received JMS message (null if nothing returned).
     * @throws JMSConnectorException errors when creating consumer, receiving the message or releasing the resources.
     */
    private Message receiveMessage(Session session, String destinationName, int timeout, String messageSelector)
            throws JMSConnectorException {
        MessageConsumer consumer = null;
        Message message;
        Destination queue = jmsConnectionFactory.createDestination(session, destinationName);
        try {
            // MessageConsumer is not included inside SessionWrapper because SessionWrapper is destination
            // independent but MessageConsumer is not. For each Poll call new MessageConsumer will be created.
            // ClientCaching parameter will control whether we need to create new session and connection when using
            // JMS Client Connector
            if (JMSConstants.JMS_SPEC_VERSION_1_0.equals(jmsConnectionFactory.getJmsSpec())) {
                if (messageSelector != null) {
                    consumer = ((QueueSession) session).createReceiver((Queue) queue, messageSelector);
                } else {
                    consumer = ((QueueSession) session).createReceiver((Queue) queue);
                }
            } else {
                if (messageSelector != null) {
                    consumer = session.createConsumer(queue, messageSelector);
                } else {
                    consumer = session.createConsumer(queue);
                }

            }
            message = consumer.receive(timeout);
        } catch (JMSException e) {
            throw new JMSConnectorException("Error JMS Client poll. " + e.getMessage(), e);
        } finally {
            if (consumer != null) {
                try {
                    jmsConnectionFactory.closeConsumer(consumer);
                } catch (JMSException e) {
                    throw new JMSConnectorException("Error JMS Client poll consumer close. " + e.getMessage(), e);
                }
            }
        }
        return message;
    }


    private Message createJMSMessage(Session session, String messageType) throws JMSException {

        Message jmsMessage = null;

        switch (messageType) {
        case JMSConstants.TEXT_MESSAGE_TYPE:
            jmsMessage = session.createTextMessage();
            break;
        case JMSConstants.OBJECT_MESSAGE_TYPE:
            jmsMessage = session.createObjectMessage();
            break;
        case JMSConstants.MAP_MESSAGE_TYPE:
            jmsMessage = session.createMapMessage();
            break;
        case JMSConstants.BYTES_MESSAGE_TYPE:
            jmsMessage = session.createBytesMessage();
            break;
        case JMSConstants.STREAM_MESSAGE_TYPE:
            jmsMessage = session.createStreamMessage();
            break;
        default:
            logger.error("Unsupported JMS Message type");
        }

        if (jmsMessage != null) {
            //Set default values to the newly created message
            jmsMessage.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
            jmsMessage.setJMSPriority(Message.DEFAULT_PRIORITY);
            jmsMessage.setJMSExpiration(Message.DEFAULT_TIME_TO_LIVE);
        }

        return jmsMessage;
    }
}
