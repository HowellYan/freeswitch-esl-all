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


import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.DefaultThreadFactory;
import link.thingscloud.freeswitch.esl.OutboundClient;
import link.thingscloud.freeswitch.esl.constant.EslConstant;
import link.thingscloud.freeswitch.esl.exception.InboundClientException;
import link.thingscloud.freeswitch.esl.outbound.handler.OutboundChannelHandler;
import link.thingscloud.freeswitch.esl.outbound.listener.EventListener;
import link.thingscloud.freeswitch.esl.outbound.listener.ServerOptionListener;
import link.thingscloud.freeswitch.esl.outbound.option.ConnectState;
import link.thingscloud.freeswitch.esl.outbound.option.OutboundClientOption;
import link.thingscloud.freeswitch.esl.outbound.option.ServerOption;
import link.thingscloud.freeswitch.esl.transport.CommandResponse;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import link.thingscloud.freeswitch.esl.transport.message.EslHeaders;
import link.thingscloud.freeswitch.esl.transport.message.EslMessage;
import link.thingscloud.freeswitch.esl.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>flow :</p>
 * |------------------------------------|
 * |                                    |
 * \|            |---» CONNECTED  ---» CLOSED  ---» SHUTDOWN
 * INIT ----» CONNECTING -----|
 * /|            |---» FAILED
 * |                     |
 * ----------------------|
 *
 * @author : <a href="mailto:ant.zhou@aliyun.com">zhouhailin</a>
 */
abstract class AbstractOutboundClient extends AbstractNettyOutboundClient implements OutboundClient {



    private final ScheduledThreadPoolExecutor scheduledPoolExecutor = new ScheduledThreadPoolExecutor(1,
            new DefaultThreadFactory("scheduled-pool", true));

    private final Map<String, OutboundChannelHandler> handlerTable = new HashMap<>(32);


    AbstractOutboundClient(OutboundClientOption option) {
        super(option);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutboundClientOption option() {
        return option;
    }

    @Override
    public ServerBootstrap bootstrap() {
        return bootstrap;
    }





    
    /**
     * {@inheritDoc}
     */
    @Override
    public void handleAuthRequest(String address, OutboundChannelHandler outboundChannelHandler) {
        log.info("Auth requested[{}], sending [auth {}]", address, "*****");
        for (ServerOption serverOption : option().serverOptions()) {
            String password = serverOption.password();
            if (password == null) {
                password = option().defaultPassword();
            }
            if (StringUtils.equals(address, serverOption.address())) {
                EslMessage response = outboundChannelHandler.sendSyncSingleLineCommand("auth " + password);
                log.debug("Auth response [{}]", response);
                if (response.getContentType().equals(EslHeaders.Value.COMMAND_REPLY)) {
                    CommandResponse reply = new CommandResponse("auth " + password, response);
                    serverOption.state(ConnectState.AUTHED);
                    log.info("Auth response success={}, message=[{}]", reply.isOk(), reply.getReplyText());
                    if (!option().events().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (String event : option().events()) {
                            sb.append(event).append(" ");
                        }
                        setEventSubscriptions(address, "plain", sb.toString());
                    }
                } else {
                    serverOption.state(ConnectState.AUTHED_FAILED);
                    log.error("Bad auth response message [{}]", response);
                    throw new IllegalStateException("Incorrect auth response");
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleEslEvent(String address, EslEvent event) {
        option().listeners().forEach(listener -> {
            long start = 0L;
            if (option().performance()) {
                start = System.currentTimeMillis();
            }
            if (option().eventPerformance()) {
                long cost = 0L;
                if (start > 0L) {
                    cost = start - (event.getEventDateTimestamp() / 1000);
                } else {
                    cost = System.currentTimeMillis() - (event.getEventDateTimestamp() / 1000);
                }
                if (cost > option().eventPerformanceCostTime()) {
                    log.warn("[event performance] received esl event diff time : {}ms, event is blocked.", cost);
                }
            }
            log.debug("Event address[{}] received [{}]", address, event);
            /*
             *  Notify listeners in a different thread in order to:
             *    - not to block the IO threads with potentially long-running listeners
             *    - generally be defensive running other people's code
             *  Use a different worker thread pool for async job results than for event driven
             *  events to keep the latency as low as possible.
             */
            if (StringUtils.equals(event.getEventName(), EslConstant.BACKGROUND_JOB)) {
                try {
                    listener.backgroundJobResultReceived(address, event);
                } catch (Throwable t) {
                    log.error("Error caught notifying listener of job result [{}], remote address [{}]", event, address, t);
                }
            } else {
                try {
                    listener.eventReceived(address, event);
                } catch (Throwable t) {
                    log.error("Error caught notifying listener of event [{}], remote address [{}]", event, address, t);
                }
            }
            if (option().performance()) {
                long cost = System.currentTimeMillis() - start;
                if (cost >= option().performanceCostTime()) {
                    log.warn("[performance] handle esl event cost time : {}ms", cost);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleDisconnectNotice(String address) {
        log.info("Disconnected[{}] ...", address);
    }

    /**
     * <p>getAuthedHandler.</p>
     *
     * @param address a {@link String} object.
     * @return a {@link OutboundChannelHandler} object.
     */
    public OutboundChannelHandler getAuthedHandler(String address) {
        OutboundChannelHandler handler = handlerTable.get(address);
        if (handler == null) {
            throw new InboundClientException("not found outbound handler for address : " + address);
        }
        List<ServerOption> serverOptions = option().serverOptions();
        for (ServerOption serverOption : serverOptions) {
            if (StringUtils.equals(serverOption.address(), address)) {
                if (serverOption.state() != ConnectState.AUTHED) {
                    throw new InboundClientException("outbound handler is not authed for address : " + address);
                }
                break;
            }
        }
        return handler;
    }

    


    private void doClose(ServerOption serverOption) {
        log.info("doClose remote server [{}:{}] success.", serverOption.host(), serverOption.port());
        serverOption.state(ConnectState.CLOSING);
        option().serverOptions().remove(serverOption);
        String remoteAddr = serverOption.address();
        OutboundChannelHandler outboundChannelHandler = handlerTable.get(remoteAddr);
        if (outboundChannelHandler != null) {
            outboundChannelHandler.close().addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("close remote server [{}:{}] success.", serverOption.host(), serverOption.port());
                } else {
                    log.info("close remote server [{}:{}] failed, cause : ", serverOption.host(), serverOption.port(), future.cause());
                }
            });
        }
    }

    private int getTimeoutSeconds(ServerOption serverOption) {
        return serverOption.timeoutSeconds() == 0 ? option().defaultTimeoutSeconds() : serverOption.timeoutSeconds();
    }
}
