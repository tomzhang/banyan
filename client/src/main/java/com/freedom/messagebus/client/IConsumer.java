package com.freedom.messagebus.client;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * the interface of consumer
 */
interface IConsumer {

    /**
     * consume message
     *
     * @param appKey          the app key which the consumer representation
     * @param msgType         message type (business / system)
     * @param queueName       the name of queue that the consumer want to connect
     *                        generally, is the app-name
     * @param receiveListener the message receiver
     * @return a consumer's closer used to let the app control the consumer
     * (actually, the message receiver is needed to be controlled)
     * @throws IOException
     */
    @NotNull
    public IConsumerCloser consume(@NotNull String appKey,
                                   @NotNull String msgType,
                                   @NotNull String queueName,
                                   @NotNull IMessageReceiveListener receiveListener) throws IOException;

}