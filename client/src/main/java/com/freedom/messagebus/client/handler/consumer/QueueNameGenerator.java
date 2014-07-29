package com.freedom.messagebus.client.handler.consumer;

import com.freedom.messagebus.client.MessageContext;
import com.freedom.messagebus.client.handler.AbstractHandler;
import com.freedom.messagebus.client.handler.IHandlerChain;
import com.freedom.messagebus.client.model.RuleModel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * the queue name generator
 */
public class QueueNameGenerator extends AbstractHandler {

    private static final Log logger = LogFactory.getLog(QueueNameGenerator.class);

    /**
     * the main process method all sub class must implement
     *
     * @param context the message context
     * @param chain   the instance of IHandlerChain
     */
    @Override
    public void handle(@NotNull MessageContext context,
                       @NotNull IHandlerChain chain) {
        Map<String, RuleModel> queueNameRules = context.getConfigManager().getQueueNameRules();
        String queueName = "";
        String msgType = context.getMsgType();
        String ruleValue = context.getRuleValue();
        String rulePattern = queueNameRules.get(msgType).getRulePattern();
        queueName = rulePattern + ruleValue;

        if (logger.isDebugEnabled()) {
            logger.debug("[handle] queue name : " + queueName);
        }

        context.setRuleValue(queueName);

        chain.handle(context);
    }
}