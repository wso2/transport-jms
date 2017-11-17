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

package org.wso2.carbon.transport.jms.callback;

import org.wso2.carbon.transport.jms.exception.JMSConnectorException;

import java.util.concurrent.CountDownLatch;
import javax.jms.JMSException;
import javax.jms.Session;

/**
 * Holds common fields and operations for a JMS callback implementation.
 */
public abstract class JMSCallback {

    /**
     * The {@link Session} instance representing JMS Session related with this call back.
     */
    private Session session;

    /**
     * States whether the callback operation is completed.
     */
    private boolean operationComplete = false;

    /**
     * Is the operation a success.
     */
    private boolean success;

    private CountDownLatch countDownLatch = new CountDownLatch(1);

    /**
     * Creates a call back initializing the JMS session object and saving caller object to be notified.
     *
     * @param session JMS Session connected with this callback.
     */
    public JMSCallback(Session session) {
        this.session = session;
    }

    /**
     * States whether acknowledgement process has been completed. This is used by a caller who is waiting to be
     * notified by the callback.
     *
     * @return True if acknowledging process is completed.
     */
    public boolean isOperationComplete() {
        return operationComplete;
    }

    /**
     * Commits the JMS session.
     *
     * @throws JMSConnectorException if the JMS provider fails to commit the transaction.
     */
    protected void commitSession() throws JMSConnectorException {
        try {
            session.commit();
        } catch (JMSException e) {
            throw new JMSConnectorException("Error while committing the session.");
        }
    }

    /**
     * Rollbacks the JMS session.
     *
     * @throws JMSConnectorException if the JMS provider fails to roll back the transaction.
     */
    protected void rollbackSession() throws JMSConnectorException {
        try {
            session.rollback();
        } catch (JMSException e) {
            throw new JMSConnectorException("Error while rolling back the session.", e);
        }
    }

    /**
     * Recover the JMS session.
     *
     * @throws JMSConnectorException if the JMS provider fails to recover the session.
     */
    protected void recoverSession() throws JMSConnectorException {
        try {
            session.recover();
        } catch (JMSException e) {
            throw new JMSConnectorException("Error while recovering the JMS session", e);
        }
    }

    /**
     * Invoke this method to update the status of the message consumption.
     *
      * @param success status of the message processing.
     */
    public void done(boolean success) {
        this.success = success;
        countDownLatch.countDown();
    }

    /**
     * Get acknowledgement mode of this JMSCallback.
     * @return Ack mode.
     */
    public abstract int getAcknowledgementMode();

    /**
     * Is this transaction/ack a success.
     *
     * @return true if success false otherwise.
     */
    protected boolean isSuccess() {
        return success;
    }

    public void waitForProcessing() throws InterruptedException {
        countDownLatch.await();
    }
}
