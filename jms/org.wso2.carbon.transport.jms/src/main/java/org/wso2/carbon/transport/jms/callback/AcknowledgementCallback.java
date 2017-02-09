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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.transport.jms.utils.JMSConstants;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * Callback to be used when there is a need for acknowledgement.
 */
public class AcknowledgementCallback implements CarbonCallback {
    private Message message;
    private Session session;
    private static final Logger logger = LoggerFactory.getLogger(AcknowledgementCallback.class);

    public AcknowledgementCallback(Message message, Session session) {
        this.message = message;
        this.session = session;
    }

    @Override
    public void done(CarbonMessage carbonMessage) {
        if (carbonMessage.getProperty(JMSConstants.JMS_MESSAGE_DELIVERY_STATUS).toString()
                .equalsIgnoreCase(JMSConstants.JMS_MESSAGE_DELIVERY_SUCCESS)) {
            try {
                message.acknowledge();
            } catch (JMSException e) {
                logger.error("Error while acknowledging the message. " + e.getMessage(), e);
                throw new RuntimeException("Error while acknowledging the message. " + e.getMessage(), e);
            }
        } else {
            try {
                session.recover();
            } catch (JMSException e) {
                logger.error("Error while recovering the session. " + e.getMessage(), e);
                throw new RuntimeException("Error while recovering the session. " + e.getMessage(), e);
            }
        }
    }
}
