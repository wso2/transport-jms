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

package org.wso2.transport.jms.contract;

import org.wso2.transport.jms.exception.JMSConnectorException;
import org.wso2.transport.jms.sender.wrappers.SessionWrapper;

import javax.jms.Destination;
import javax.jms.Message;

/**
 * Allows to send outbound messages
 */
public interface JMSClientConnector {

    /**
     * Message sending logic to send message to a backend endpoint. Additionally, this method accepts a map of
     * parameters that is used as data to create the connection and construct the message to be send.
     *
     * @param message the carbon message used with sending the a message to backend.
     * @param destinationName name of the queue/topic message should be sent
     * @return return true if the sending was successful, false otherwise.
     * @throws JMSConnectorException on error while trying to send message to backend.
     */
    boolean send(Message message, String destinationName) throws JMSConnectorException;

    /**
     * Method to use to poll messages from a destination with the provided timeout. Polling support included for queues
     * only.
     *
     * @param destinationName Name of the destination.
     * @param timeout timeout value that will blocked for a message.
     * @return Received Message from the broker (null if nothing received).
     * @throws JMSConnectorException errors when acquiring session, polling or closing resources.
     *
     * @since Transport-JMS 6.0.49.
     */
    Message poll(String destinationName, int timeout) throws JMSConnectorException;

    /**
     * Create a {@link Destination} instance using a {@link javax.jms.Session}.
     *
     * @param destinationName Name of the destination.
     * @return  Created destination object.
     * @throws JMSConnectorException Error when creating a {@link Destination}.
     *
     * @since Transport-JMS 6.0.49.
     */
    Destination createDestination(String destinationName) throws JMSConnectorException;

    /**
     * Create a {@link Message} instance using a {@link javax.jms.Session}.
     *
     * @param messageType Type of the JMS Message.
     * @return  Created JMS Message instance.
     * @throws JMSConnectorException Error when creating a {@link Message}.
     */
    Message createMessage(String messageType) throws JMSConnectorException;

    /**
     * Get a {@link SessionWrapper} instance on this particular connection factory.
     *
     * @return a SessionWrapper.
     * @throws JMSConnectorException Error when acquiring a session wrapper instance.
     */
    SessionWrapper acquireSession() throws JMSConnectorException;

    /**
     * Send a message using provided transacted session.
     *
     * @param jmsMessage JMS Message instance.
     * @param destinationName Name of the outbound queue/topic.
     * @param sessionWrapper   SessionWrapper instance.
     * @return return true if the sending was successful, false otherwise.
     * @throws JMSConnectorException error when sending the transacted message.
     */
    boolean sendTransactedMessage(Message jmsMessage, String destinationName, SessionWrapper sessionWrapper)
            throws JMSConnectorException;

    /**
     * Poll method for a transacted session. Difference of this from Poll method is that we have tp pass a
     * {@link SessionWrapper} instance as an argument. Polling will be done on that.
     *
     * @param destinationName Name of the destination.
     * @param timeout timeout value that will blocked for a message.
     * @param sessionWrapper already acquired SessionWrapper instance.
     * @return Received Message from the broker (null if nothing received).
     * @throws JMSConnectorException errors when polling or closing resources.
     *
     * @since Transport-JMS 6.0.49.
     */
    Message pollTransacted(String destinationName, int timeout, SessionWrapper sessionWrapper)
            throws JMSConnectorException;

    /**
     * Release a SessionWrapper instance to the pool after completing the task.
     *
     * @param sessionWrapper SessionWrapper to be released.
     * @throws JMSConnectorException error when releasing the session wrapper instance.
     */
    void releaseSession(SessionWrapper sessionWrapper) throws JMSConnectorException;

    /**
     * Close the Client Connection factory resources.
     * @throws JMSConnectorException error when closing the connection factory resources.
     */
    void closeConnectionFactory() throws JMSConnectorException;

}
