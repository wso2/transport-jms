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

package org.wso2.carbon.transport.jms.utils;

import javax.jms.Destination;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.Reference;

/**
 * This class is maintains the common methods used by JMS transport.
 */
public class JMSUtils {

    /**
     * Return the JMS destination with the given destination name looked up from the context.
     *
     * @param context         the Context to lookup.
     * @param destinationName name of the destination to be looked up.
     * @param destinationType type of the destination to be looked up.
     * @return the JMS destination, or null if it does not exist.
     * @throws NamingException if any naming error occurred when looking up destination.
     */
    public static Destination lookupDestination(Context context, String destinationName, String destinationType)
            throws NamingException {
        if (null == destinationName) {
            return null;
        }
            return JMSUtils.lookup(context, Destination.class, destinationName);
    }

    /**
     * JNDI look up in the context.
     *
     * @param context Context that need to looked up.
     * @param clazz   Class of the object that need to be found.
     * @param name    Name of the object that need to be looked up.
     * @param <T>     Class of the Object that need to be found.
     * @return the relevant object, if found in the context.
     * @throws NamingException, if the found object is different from the expected object.
     */
    private static <T> T lookup(Context context, Class<T> clazz, String name) throws NamingException {
        Object object = context.lookup(name);
        try {
            return clazz.cast(object);
        } catch (ClassCastException ex) {
            // Instead of a ClassCastException, throw an exception with some
            // more information.
            if (object instanceof Reference) {
                Reference ref = (Reference) object;
                String errorMessage =
                        "JNDI failed to de-reference Reference with name " + name + "; is the " + "factory " + ref
                                .getFactoryClassName() + " in your classpath?";
                throw new NamingException(errorMessage);
            } else {
                String errorMessage =
                        "JNDI lookup of name " + name + " returned a " + object.getClass().getName() + " while a "
                                + clazz + " was expected";
                throw new NamingException(errorMessage);
            }
        }
    }
}
