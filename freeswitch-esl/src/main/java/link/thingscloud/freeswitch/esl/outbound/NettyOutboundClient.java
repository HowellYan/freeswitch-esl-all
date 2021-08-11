/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package link.thingscloud.freeswitch.esl.outbound;

import link.thingscloud.freeswitch.esl.OutboundClient;
import link.thingscloud.freeswitch.esl.constant.EslConstant;
import link.thingscloud.freeswitch.esl.exception.InboundTimeoutExcetion;
import link.thingscloud.freeswitch.esl.outbound.handler.OutboundChannelHandler;
import link.thingscloud.freeswitch.esl.outbound.option.OutboundClientOption;
import link.thingscloud.freeswitch.esl.transport.CommandResponse;
import link.thingscloud.freeswitch.esl.transport.SendEvent;
import link.thingscloud.freeswitch.esl.transport.SendMsg;
import link.thingscloud.freeswitch.esl.transport.message.EslMessage;
import link.thingscloud.freeswitch.esl.util.StringUtils;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


/**
 * <p>NettyInboundClient class.</p>
 *
 * @author : <a href="mailto:ant.zhou@aliyun.com">zhouhailin</a>
 * @version 1.0.0
 */
public class NettyOutboundClient extends AbstractOutboundClientCommand {

    /**
     * <p>Constructor for NettyInboundClient.</p>
     *
     * @param option a {@link OutboundClientOption} object.
     */
    public NettyOutboundClient(OutboundClientOption option) {
        super(option);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EslMessage sendSyncApiCommand(String address, String command, String arg) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        StringBuilder sb = new StringBuilder();
        if (command != null && !command.isEmpty()) {
            sb.append("api ");
            sb.append(command);
        }
        if (arg != null && !arg.isEmpty()) {
            sb.append(' ');
            sb.append(arg);
        }
        log.debug("sendSyncApiCommand address : {}, command : {}, arg : {}", address, command, arg);
        return handler.sendSyncSingleLineCommand(sb.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EslMessage sendSyncApiCommand(String address, String command, String arg, long timeoutSeconds) throws InboundTimeoutExcetion {
        try {
            return publicExecutor.submit(() -> sendSyncApiCommand(address, command, arg)).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new InboundTimeoutExcetion(String.format("sendSyncApiCommand address : %s, command : %s, arg : %s, timeoutSeconds : %s", address, command, arg, timeoutSeconds), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendSyncApiCommand(String address, String command, String arg, Consumer<EslMessage> consumer) {
        publicExecutor.execute(() -> {
            EslMessage msg = sendSyncApiCommand(address, command, arg);
            if (consumer != null) {
                consumer.accept(msg);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String sendAsyncApiCommand(String address, String command, String arg) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        StringBuilder sb = new StringBuilder();
        if (command != null && !command.isEmpty()) {
            sb.append("bgapi ");
            sb.append(command);
        }
        if (arg != null && !arg.isEmpty()) {
            sb.append(' ');
            sb.append(arg);
        }
        return handler.sendAsyncCommand(sb.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendAsyncApiCommand(String address, String command, String arg, Consumer<String> consumer) {
        publicExecutor.execute(() -> {
            String msg = sendAsyncApiCommand(address, command, arg);
            if (consumer != null) {
                consumer.accept(msg);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse setEventSubscriptions(String address, String format, String events) {
        if (!StringUtils.equals(format, EslConstant.PLAIN)) {
            throw new IllegalStateException("Only 'plain' event format is supported at present");
        }
        OutboundChannelHandler handler = getAuthedHandler(address);

        StringBuilder sb = new StringBuilder();
        sb.append("event ");
        sb.append(format);
        if (events != null && !events.isEmpty()) {
            sb.append(' ');
            sb.append(events);
        }
        EslMessage response = handler.sendSyncSingleLineCommand(sb.toString());
        return new CommandResponse(sb.toString(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse cancelEventSubscriptions(String address) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        EslMessage response = handler.sendSyncSingleLineCommand("noevents");
        return new CommandResponse("noevents", response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse addEventFilter(String address, String eventHeader, String valueToFilter) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        StringBuilder sb = new StringBuilder();
        if (eventHeader != null && !eventHeader.isEmpty()) {
            sb.append("filter ");
            sb.append(eventHeader);
        }
        if (valueToFilter != null && !valueToFilter.isEmpty()) {
            sb.append(' ');
            sb.append(valueToFilter);
        }
        EslMessage response = handler.sendSyncSingleLineCommand(sb.toString());

        return new CommandResponse(sb.toString(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse deleteEventFilter(String address, String eventHeader, String valueToFilter) {
        OutboundChannelHandler handler = getAuthedHandler(address);

        StringBuilder sb = new StringBuilder();
        if (eventHeader != null && !eventHeader.isEmpty()) {
            sb.append("filter delete ");
            sb.append(eventHeader);
        }
        if (valueToFilter != null && !valueToFilter.isEmpty()) {
            sb.append(' ');
            sb.append(valueToFilter);
        }
        EslMessage response = handler.sendSyncSingleLineCommand(sb.toString());
        return new CommandResponse(sb.toString(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse sendEvent(String address, SendEvent sendEvent) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        EslMessage response = handler.sendSyncMultiLineCommand(sendEvent.getMsgLines());
        return new CommandResponse(sendEvent.toString(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendEvent(String address, SendEvent sendEvent, Consumer<CommandResponse> consumer) {
        publicExecutor.execute(() -> {
            CommandResponse response = sendEvent(address, sendEvent);
            if (consumer != null) {
                consumer.accept(response);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse sendMessage(String address, SendMsg sendMsg) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        EslMessage response = handler.sendSyncMultiLineCommand(sendMsg.getMsgLines());
        return new CommandResponse(sendMsg.toString(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessage(String address, SendMsg sendMsg, Consumer<CommandResponse> consumer) {
        publicExecutor.execute(() -> {
            CommandResponse response = sendMessage(address, sendMsg);
            if (consumer != null) {
                consumer.accept(response);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse setLoggingLevel(String address, String level) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        StringBuilder sb = new StringBuilder();
        if (level != null && !level.isEmpty()) {
            sb.append("log ");
            sb.append(level);
        }
        EslMessage response = handler.sendSyncSingleLineCommand(sb.toString());
        return new CommandResponse(sb.toString(), response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse cancelLogging(String address) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        EslMessage response = handler.sendSyncSingleLineCommand("nolog");
        return new CommandResponse("nolog", response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CommandResponse close(String address) {
        OutboundChannelHandler handler = getAuthedHandler(address);
        EslMessage response = handler.sendSyncSingleLineCommand("exit");
        return new CommandResponse("exit", response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutboundClient closeChannel(String address) {
        getAuthedHandler(address).close();
        return this;
    }
}
