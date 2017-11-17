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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.transport.jms.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.transport.jms.exception.JMSConnectorException;
import org.wso2.carbon.transport.jms.utils.JMSConstants;
import org.wso2.carbon.transport.jms.utils.JMSUtils;

import java.util.Properties;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XASession;
import javax.jms.XATopicConnection;
import javax.jms.XATopicConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

/**
 * JMSServerConnectionFactory that handles the JMS Connection, Session creation and closing.
 */
public class JMSConnectionResourceFactory {
    private static final Logger logger = LoggerFactory.getLogger(JMSConnectionResourceFactory.class);
    /**
     * The {@link Context} instance representing initial Context.
     */
    private Context ctx;
    /**
     * The {@link ConnectionFactory} instance representing jms connection factory.
     */
    private ConnectionFactory connectionFactory;
    /**
     * The {@link XAConnectionFactory} instance representing jms connection factory.
     */
    private XAConnectionFactory xAConnectionFactory;
    /**
     * The {@link String} instance representing the connection factory JNDI name.
     */
    private String connectionFactoryString;
    /**
     * Represents whether to listen queue or topic.
     */
    protected JMSConstants.JMSDestinationType destinationType;
    /**
     * The {@link Boolean} instance representing whether the session is transacted or not.
     */
    private boolean transactedSession = false;
    /**
     * The {@link Boolean} instance representing whether the connection factory is created for distributed transactions.
     */
    private boolean xATransacted = false;
    /**
     * The {@link Integer} instance representing the session acknowledgement mode.
     */
    private int sessionAckMode = Session.AUTO_ACKNOWLEDGE;
    /**
     * The {@link String} instance representing the jms spec version.
     */
    protected String jmsSpec;
    /**
     * The {@link String} instance representing the client id of the durable subscription.
     */
    protected String clientId;
    /**
     * The {@link Properties} instance representing jms configuration properties.
     */
    private Properties properties;
    /**
     * The {@link String} instance representing username for connecting the broker.
     */
    private String username;
    /**
     * The {@link String} instance representing password for connecting the broker.
     */
    private String password;

    /**
     * Initialization of JMS ConnectionFactory with the user specified properties.
     *
     * @param properties Properties to be added to the initial context.
     * @throws JMSConnectorException Thrown when initial context name is wrong or when creating connection factory.
     */
    public JMSConnectionResourceFactory(Properties properties) throws JMSConnectorException {
        this.properties = properties;
        try {
            ctx = new InitialContext(properties);
        } catch (NamingException e) {
            throw new JMSConnectorException("NamingException while obtaining initial context. ", e);
        }

        //Setting Connection factory type
        String connectionFactoryType = properties.getProperty(JMSConstants.PARAM_CONNECTION_FACTORY_TYPE);

        if (JMSConstants.DESTINATION_TYPE_TOPIC.equalsIgnoreCase(connectionFactoryType)) {
            this.destinationType = JMSConstants.JMSDestinationType.TOPIC;
        } else {
            this.destinationType = JMSConstants.JMSDestinationType.QUEUE;
        }

        //Setting JMS version
        String jmsSpecVersion = properties.getProperty(JMSConstants.PARAM_JMS_SPEC_VER);

        if (null == jmsSpecVersion) {
            jmsSpec = JMSConstants.JMS_SPEC_VERSION_1_1;
        } else {
            switch (jmsSpecVersion) {
            case JMSConstants.JMS_SPEC_VERSION_1_1:
                jmsSpec = JMSConstants.JMS_SPEC_VERSION_1_1;
                break;
            case JMSConstants.JMS_SPEC_VERSION_2_0:
                jmsSpec = JMSConstants.JMS_SPEC_VERSION_2_0;
                break;
            case JMSConstants.JMS_SPEC_VERSION_1_0:
                jmsSpec = JMSConstants.JMS_SPEC_VERSION_1_0;
                break;
            default:
                jmsSpec = JMSConstants.JMS_SPEC_VERSION_1_1;
            }
        }

        //Setting Client ID
        clientId = properties.getProperty(JMSConstants.PARAM_CLIENT_ID);

        //Setting Connection Factory Name
        this.connectionFactoryString = properties.getProperty(JMSConstants.PARAM_CONNECTION_FACTORY_JNDI_NAME);
        if (null == connectionFactoryString || "".equals(connectionFactoryString)) {
            connectionFactoryString = "QueueConnectionFactory";
        }

        //Setting Acknowledgement mode and Transaction
        String strSessionAck = properties.getProperty(JMSConstants.PARAM_ACK_MODE);
        if (null == strSessionAck) {
            sessionAckMode = Session.AUTO_ACKNOWLEDGE;
        } else if (strSessionAck.equals(JMSConstants.CLIENT_ACKNOWLEDGE_MODE)) {
            sessionAckMode = Session.CLIENT_ACKNOWLEDGE;
        } else if (strSessionAck.equals(JMSConstants.DUPS_OK_ACKNOWLEDGE_MODE)) {
            sessionAckMode = Session.DUPS_OK_ACKNOWLEDGE;
        } else if (strSessionAck.equals(JMSConstants.SESSION_TRANSACTED_MODE)) {
            sessionAckMode = Session.SESSION_TRANSACTED;
            transactedSession = true;
        } else if (strSessionAck.equals(JMSConstants.XA_TRANSACTED_MODE)) {
            sessionAckMode = -1;
            xATransacted = true;
        }

        //Setting broker credentials
        this.username = properties.getProperty(JMSConstants.CONNECTION_USERNAME);
        this.password = properties.getProperty(JMSConstants.CONNECTION_PASSWORD);

        createConnectionFactory();
    }

    /**
     * To get the JMS Connection Factory.
     *
     * @return JMS Connection Factory.
     * @throws JMSConnectorException Thrown when creating jms connection.
     */
    public ConnectionFactory getConnectionFactory() throws JMSConnectorException {
        if (this.connectionFactory == null) {
            createConnectionFactory();
        }
        return this.connectionFactory;
    }

    /**
     * To create the JMS Connection Factory.
     *
     * @throws JMSConnectorException Thrown when creating {@link ConnectionFactory} instance.
     */
    private void createConnectionFactory() throws JMSConnectorException {
        try {
            if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec)) {
                if (isxATransacted()) {
                    xAConnectionFactory = (XAConnectionFactory) ctx.lookup(connectionFactoryString);
                } else {
                    connectionFactory = (ConnectionFactory) ctx.lookup(connectionFactoryString);
                }
            } else {
                if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                    if (isxATransacted()) {
                        xAConnectionFactory = (XAQueueConnectionFactory) ctx.lookup(connectionFactoryString);
                    } else {
                        connectionFactory = (QueueConnectionFactory) ctx.lookup(connectionFactoryString);
                    }
                } else if (JMSConstants.JMSDestinationType.TOPIC.equals(this.destinationType)) {
                    if (isxATransacted()) {
                        xAConnectionFactory = (XATopicConnectionFactory) ctx.lookup(connectionFactoryString);
                    } else {
                        connectionFactory = (TopicConnectionFactory) ctx.lookup(connectionFactoryString);
                    }
                }
            }
        } catch (NamingException e) {
            throw new JMSConnectorException(
                    "Naming exception while obtaining connection factory for " + this.connectionFactoryString, e);
        }
    }

    /**
     * Create a {@link Connection} instance using the initialized configurations.
     *
     * @return Connection instance.
     * @throws JMSException thrown when creating the connection object from JMS connection factory.
     */
    public Connection createConnection() throws JMSException {
        if (null == connectionFactory) {
            throw new JMSException("Connection cannot be establish to the broker. Connection Factory is null. Please "
                    + "check the Please check the broker libs provided.");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Creating a new JMS Connection on: " + this.connectionFactoryString);
        }

        if (username != null && password != null) {
            return createConnection(username, password);
        }
        //Create new Connection without username and password
        if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                .equals(jmsSpec)) {
            return connectionFactory.createConnection();
        } else {
            QueueConnectionFactory qConFac = null;
            TopicConnectionFactory tConFac = null;
            if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                qConFac = (QueueConnectionFactory) this.connectionFactory;
            } else {
                tConFac = (TopicConnectionFactory) this.connectionFactory;
            }
            if (null != qConFac) {
                return qConFac.createQueueConnection();
            } else {
                return tConFac.createTopicConnection();
            }
        }
    }

    private Connection createConnection(String userName, String password) throws JMSException {
        if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                .equals(jmsSpec)) {
            return connectionFactory.createConnection(userName, password);
        } else {
            QueueConnectionFactory qConFac = null;
            TopicConnectionFactory tConFac = null;
            if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                qConFac = (QueueConnectionFactory) this.connectionFactory;
            } else {
                tConFac = (TopicConnectionFactory) this.connectionFactory;
            }
            if (null != qConFac) {
                return qConFac.createQueueConnection(userName, password);
            } else {
                return tConFac.createTopicConnection(userName, password);
            }
        }
    }

    /**
     * Create a {@link XAConnection} instance using the initialized configurations.
     *
     * @return XAConnection instance.
     * @throws JMSException thrown when creating the XAconnection object from JMS XAconnection factory.
     */
    public XAConnection createXAConnection() throws JMSException {
        if (null == xAConnectionFactory) {
            throw new JMSException("XAConnection cannot be establish to the broker. XAConnection Factory is null. "
                    + "Please check the Please check the broker libs provided.");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Creating a new JMS XAConnection on: " + this.connectionFactoryString);
        }

        if (username != null && password != null) {
            return createXAConnection(username, password);
        }

        // create new xaConnection without username and password
        if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                .equals(jmsSpec)) {
            return xAConnectionFactory.createXAConnection();
        } else {
            XAQueueConnectionFactory qConFac = null;
            XATopicConnectionFactory tConFac = null;
            if (JMSConstants.JMSDestinationType.QUEUE.equals(destinationType)) {
                qConFac = (XAQueueConnectionFactory) xAConnectionFactory;
            } else {
                tConFac = (XATopicConnectionFactory) xAConnectionFactory;
            }
            if (null != qConFac) {
                return qConFac.createXAQueueConnection();
            } else {
                return tConFac.createXATopicConnection();
            }
        }
    }

    private XAConnection createXAConnection(String userName, String password) throws JMSException {
        if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                .equals(jmsSpec)) {
            return xAConnectionFactory.createXAConnection(userName, password);
        } else {
            XAQueueConnectionFactory qConFac = null;
            XATopicConnectionFactory tConFac = null;
            if (JMSConstants.JMSDestinationType.QUEUE.equals(destinationType)) {
                qConFac = (XAQueueConnectionFactory) xAConnectionFactory;
            } else {
                tConFac = (XATopicConnectionFactory) xAConnectionFactory;
            }
            if (null != qConFac) {
                return qConFac.createXAQueueConnection(userName, password);
            } else {
                return tConFac.createXATopicConnection(userName, password);
            }
        }
    }

    /**
     * To create the destination.
     *
     * @param session         relevant session to create the destination.
     * @param destinationName Destination jms destination.
     * @return the destination that is created from session.
     * @throws JMSConnectorException Thrown when looking up destination.
     */
    public Destination createDestination(Session session, String destinationName) throws JMSConnectorException {
        Destination destination = null;

        if (logger.isDebugEnabled()) {
            logger.debug("Creating a new JMS Destination on: " + this.connectionFactoryString);
        }

        try {
            if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                destination = JMSUtils.lookupDestination(ctx, destinationName, JMSConstants.DESTINATION_TYPE_QUEUE);
            } else if (JMSConstants.JMSDestinationType.TOPIC.equals(this.destinationType)) {
                destination = JMSUtils.lookupDestination(ctx, destinationName, JMSConstants.DESTINATION_TYPE_TOPIC);
            }
        } catch (NameNotFoundException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Could not find destination '" + destinationName + "' on connection factory for '"
                        + this.connectionFactoryString + "'. " + e.getMessage());
                logger.debug("Creating destination '" + destinationName + "' on connection factory for '"
                        + this.connectionFactoryString + ".");
            }
            /*
              If the destination is not found already, create the destination
             */
            try {
                if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                    destination = session.createQueue(destinationName);
                } else if (JMSConstants.JMSDestinationType.TOPIC.equals(this.destinationType)) {
                    destination = session.createTopic(destinationName);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Created '" + destinationName + "' on connection factory for '"
                            + this.connectionFactoryString + "'.");
                }
            } catch (JMSException e1) {
                throw new JMSConnectorException(
                        "Could not find nor create '" + destinationName + "' on connection factory for "
                                + this.connectionFactoryString, e1);
            }
        } catch (NamingException e) {
            throw new JMSConnectorException(
                    "Naming exception while looking up for the destination name " + destinationName, e);
        }
        return destination;
    }

    /**
     * Create JMS {@link Session} instance on top of the provided {@link Connection} instance.
     *
     * @param connection JMS Connection.
     * @return Session instance.
     * @throws JMSConnectorException Error when creating the JMS Session.
     */
    public Session createSession(Connection connection) throws JMSConnectorException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating a new JMS Session on: " + this.connectionFactoryString);
            }
            if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec)) {
                return connection.createSession(transactedSession, sessionAckMode);
            } else if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                return ((QueueConnection) (connection)).createQueueSession(transactedSession, sessionAckMode);
            } else {
                return ((TopicConnection) (connection)).createTopicSession(transactedSession, sessionAckMode);

            }
        } catch (JMSException e) {
            throw new JMSConnectorException(
                    "JMS Exception while obtaining session for factory " + connectionFactoryString, e);
        }
    }

    /**
     * Create JMS {@link XASession} instance on top of the provided {@link Connection} instance.
     *
     * @param xAConnection JMS Connection.
     * @return Session instance.
     * @throws JMSConnectorException Error when creating the XASession.
     */
    public XASession createXASession(XAConnection xAConnection) throws JMSConnectorException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating a new JMS XASession on: " + this.connectionFactoryString);
            }

            if (JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec) || JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec)) {
                return xAConnection.createXASession();
            } else if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                return ((XAQueueConnection) (xAConnection)).createXAQueueSession();
            } else {
                return ((XATopicConnection) (xAConnection)).createXATopicSession();
            }
        } catch (JMSException e) {
            throw new JMSConnectorException(
                    "JMS Exception while obtaining session for factory " + connectionFactoryString, e);
        }
    }

    /**
     * Create {@link MessageProducer} instance for the provided session.
     *
     * @param session JMS Session instance.
     * @return Message producer.
     * @throws JMSConnectorException Error when creating the JMS Message Producer.
     */
    public MessageProducer createMessageProducer(Session session) throws JMSConnectorException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating a new JMS Message Producer on: " + this.connectionFactoryString);
            }
            if ((JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) || (JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec))) {
                return session.createProducer(null);
            } else {
                if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                    return ((QueueSession) session).createSender(null);
                } else {
                    return ((TopicSession) session).createPublisher(null);
                }
            }
        } catch (JMSException e) {
            throw new JMSConnectorException("JMS Exception while creating the producer for the destination ", e);
        }
    }

    public boolean isTransactedSession() {
        return this.transactedSession;
    }

    public boolean isxATransacted() {
        return xATransacted;
    }

    public int getSessionAckMode() {
        return this.sessionAckMode;
    }

    public Properties getProperties() {
        return properties;
    }

    /**
     * This method will get invoked by the JMS Connection Error listener whenever as error occurs in the
     * JMS Connection level.
     * <p>
     * This can be used to close all the Connections, Sessions, Producer, Consumers created on top of this
     * Connection factory and initialize again.
     *
     *  @param ex JMS Exception that occurred in Connection.
     */
    public void notifyError(JMSException ex) {
    }

    /**
     * Get JMS specification version.
     *
     * @return 1.0, 1.1 or 2.0.
     */
    public String getJmsSpec() {
        return jmsSpec;
    }

    /**
     * Get destination type of this Connection factory.
     *
     * @return queue or topic.
     */
    public JMSConstants.JMSDestinationType getDestinationType() {
        return destinationType;
    }

    /**
     * Close a JMS {@link Connection}.
     * @param connection Connection that need to be closed.
     * @throws JMSException if an error occurs while closing the connection.
     */
    public void closeConnection(Connection connection) throws JMSException {
        if (connection != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing a JMS Connection of: " + this.connectionFactoryString);
            }
            if ((JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) || (JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec))) {
                connection.close();
            } else {
                if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                    ((QueueConnection) connection).close();
                } else {
                    ((TopicConnection) connection).close();
                }
            }
        }
    }

    /**
     * Close a JMS {@link Session}.
     * @param session Session that needs to be closed.
     * @throws JMSException if an error occurs while closing the session.
     */
    public void closeSession(Session session) throws JMSException {
        if (session != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing a JMS Session of: " + this.connectionFactoryString);
            }
            if ((JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) || (JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec))) {
                session.close();
            } else {
                if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                    ((QueueSession) session).close();
                } else {
                    ((TopicSession) session).close();
                }
            }
        }
    }

    /**
     * Close a JMS {@link MessageProducer}.
     * @param messageProducer JMS Message Producer that needs to be closed.
     * @throws JMSException if an error occurs while closing the producer.
     */
    public void closeProducer(MessageProducer messageProducer) throws JMSException {
        if (messageProducer != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing a JMS Message Producer of: " + this.connectionFactoryString);
            }
            if ((JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) || (JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec))) {
                messageProducer.close();
            } else {
                if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                    ((QueueSender) messageProducer).close();
                } else {
                    ((TopicPublisher) messageProducer).close();
                }
            }
        }
    }

    /**
     * Close a JMS {@link MessageConsumer}.
     * @param messageConsumer JMS Message Consumer that needs to be closed.
     * @throws JMSException if an error occurs while closing the consumer.
     */
    public void closeConsumer(MessageConsumer messageConsumer) throws JMSException {
        if (messageConsumer != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing a JMS Message Consumer of: " + this.connectionFactoryString);
            }
            if ((JMSConstants.JMS_SPEC_VERSION_1_1.equals(jmsSpec)) || (JMSConstants.JMS_SPEC_VERSION_2_0
                    .equals(jmsSpec))) {
                messageConsumer.close();
            } else {
                if (JMSConstants.JMSDestinationType.QUEUE.equals(this.destinationType)) {
                    if (messageConsumer instanceof QueueReceiver) {
                        ((QueueReceiver) messageConsumer).close();
                    }
                } else {
                    if (messageConsumer instanceof  TopicSubscriber) {
                        ((TopicSubscriber) messageConsumer).close();
                    }
                }
            }
        }
    }

    /**
     * Start the jms connection to start the message delivery.
     *
     * @param connection Connection that need to be started.
     * @throws JMSConnectorException Thrown when starting jms connection.
     * @throws JMSSecurityException Thrown for issues in security (eg: credentials).
     */
    public void start(Connection connection) throws JMSConnectorException, JMSSecurityException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Starting a JMS Connection of: " + this.connectionFactoryString);
            }
            connection.start();
        } catch (JMSSecurityException e) {
            throw e;
        } catch (JMSException e) {
            throw new JMSConnectorException(
                    "JMS Exception while starting connection for factory " + this.connectionFactoryString, e);
        }
    }

    /**
     * Stop the jms connection to stop the message delivery.
     *
     * @param connection JMS connection that need to be stopped.
     * @throws JMSConnectorException Thrown when stopping jms connection.
     */
    public void stop(Connection connection) throws JMSConnectorException {
        try {
            if (null != connection) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Stopping a JMS Connection of: " + this.connectionFactoryString);
                }
                connection.stop();
            }
        } catch (JMSException e) {
            throw new JMSConnectorException(
                    "JMS Exception while stopping the connection for factory " + this.connectionFactoryString, e);
        }
    }

    /**
     * Get ConnectionFactory Name.
     * @return Connection factory name.
     */
    protected String getConnectionFactoryString() {
        return this.connectionFactoryString;
    }
}
