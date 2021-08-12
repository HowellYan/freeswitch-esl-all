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

package link.thingscloud.freeswitch.esl.outbound.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import link.thingscloud.freeswitch.esl.helper.EslHelper;
import link.thingscloud.freeswitch.esl.outbound.listener.ChannelEventListener;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import link.thingscloud.freeswitch.esl.transport.message.EslHeaders;
import link.thingscloud.freeswitch.esl.transport.message.EslMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>OutboundChannelHandler class.</p>
 *
 * @author : <a href="mailto:ant.zhou@aliyun.com">zhouhailin</a>
 * @version 1.0.0
 */
@Slf4j
public class OutboundChannelHandler extends SimpleChannelInboundHandler<EslMessage> {

    private static final String MESSAGE_TERMINATOR = "\n\n";
    private static final String LINE_TERMINATOR = "\n";

    private final Lock syncLock = new ReentrantLock();
    private final Queue<SyncCallback> syncCallbacks = new ConcurrentLinkedQueue<>();
    private final ChannelEventListener listener;
    private final ExecutorService publicExecutor;
    private final boolean disablePublicExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<EslEvent>> backgroundJobs =
            new ConcurrentHashMap<>();
    private final ExecutorService backgroundJobExecutor = Executors.newCachedThreadPool();
    private final boolean isTraceEnabled = log.isTraceEnabled();
    private Channel channel;
    private String remoteAddr;

    /**
     * <p>Constructor for OutboundChannelHandler.</p>
     *
     * @param listener              a {@link ChannelEventListener} object.
     * @param publicExecutor        a {@link ExecutorService} object.
     * @param disablePublicExecutor a boolean.
     */
    public OutboundChannelHandler(ChannelEventListener listener, ExecutorService publicExecutor, boolean disablePublicExecutor) {
        this.listener = listener;
        this.publicExecutor = publicExecutor;
        this.disablePublicExecutor = disablePublicExecutor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.channel = ctx.channel();
        //this.remoteAddr = RemotingUtil.socketAddress2String(channel.remoteAddress());
        log.info("channelActive remoteAddr : {}", ctx);
//        listener.onChannelActive(remoteAddr, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.debug("channelInactive remoteAddr : {}", remoteAddr);
//        listener.onChannelClosed(remoteAddr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
                log.debug("userEventTriggered remoteAddr : {}, evt state : {} ", remoteAddr, ((IdleStateEvent) evt).state());
                publicExecutor.execute(() -> sendAsyncCommand("bgapi status"));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exceptionCaught remoteAddr : {}, cause : ", remoteAddr, cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, EslMessage msg) {

        log.info("channelRead0 esl message : {}", EslHelper.formatEslMessage(msg));

        String contentType = msg.getContentType();
        if (contentType.equals(EslHeaders.Value.TEXT_EVENT_PLAIN) ||
                contentType.equals(EslHeaders.Value.TEXT_EVENT_XML)) {
            //  transform into an event
            EslEvent eslEvent = new EslEvent(msg);
            handleEslEvent(eslEvent);
        } else {
            handleEslMessage(msg);
        }
    }

    private void handleEslMessage(EslMessage message) {
        log.debug("Received message: [{}]", message);
        String contentType = message.getContentType();
        switch (contentType) {
            case EslHeaders.Value.API_RESPONSE:
                log.debug("Api response received [{}]", message);
                Objects.requireNonNull(syncCallbacks.poll()).handle(message);
                break;
            case EslHeaders.Value.COMMAND_REPLY:
                log.debug("Command reply received [{}]", message);
                Objects.requireNonNull(syncCallbacks.poll()).handle(message);
                break;
            case EslHeaders.Value.AUTH_REQUEST:
                log.debug("Auth request received [{}]", message);
                publicExecutor.execute(() -> listener.handleAuthRequest(remoteAddr, this));
                break;
            case EslHeaders.Value.TEXT_DISCONNECT_NOTICE:
                log.debug("Disconnect notice received [{}]", message);
                publicExecutor.execute(() -> listener.handleDisconnectNotice(remoteAddr));
                break;
            default:
                log.warn("Unexpected message content type [{}]", contentType);
                break;
        }
    }

    private void handleEslEvent(EslEvent event) {
        if (disablePublicExecutor) {
            listener.handleEslEvent(remoteAddr, event);
        } else {
            publicExecutor.execute(() -> listener.handleEslEvent(remoteAddr, event));
        }
    }

    /**
     * Synthesise a synchronous command/response by creating a callback object which is placed in
     * queue and blocks waiting for another IO thread to process an incoming {@link EslMessage} and
     * attach it to the callback.
     *
     * @param command single string to send
     * @return the {@link EslMessage} attached to this command's callback
     */
    public EslMessage sendSyncSingleLineCommand(final String command) {
        if (isTraceEnabled) {
            log.trace("sendSyncSingleLineCommand command : {}", command);
        }
        SyncCallback callback = new SyncCallback();
        syncLock.lock();
        try {
            syncCallbacks.add(callback);
            channel.writeAndFlush(command + MESSAGE_TERMINATOR);
        } finally {
            syncLock.unlock();
        }

        //  Block until the response is available
        return callback.get();
    }

    /**
     * Synthesise a synchronous command/response by creating a callback object which is placed in
     * queue and blocks waiting for another IO thread to process an incoming {@link EslMessage} and
     * attach it to the callback.
     *
     * @param commandLines List of command lines to send
     * @return the {@link EslMessage} attached to this command's callback
     */
    public EslMessage sendSyncMultiLineCommand(final List<String> commandLines) {
        SyncCallback callback = new SyncCallback();
        //  Build command with double line terminator at the end
        StringBuilder sb = new StringBuilder();
        for (String line : commandLines) {
            sb.append(line);
            sb.append(LINE_TERMINATOR);
        }
        sb.append(LINE_TERMINATOR);
        if (isTraceEnabled) {
            log.trace("sendSyncMultiLineCommand command : {}", sb);
        }
        syncLock.lock();
        try {
            syncCallbacks.add(callback);
            channel.writeAndFlush(sb.toString());
        } finally {
            syncLock.unlock();
        }

        //  Block until the response is available
        return callback.get();
    }

    public CompletableFuture<EslMessage> sendApiSingleLineCommand(Channel channel, final String command) {
        syncLock.lock();
        try {
            final CompletableFuture<EslMessage> future = new CompletableFuture<>();
            channel.writeAndFlush(command + MESSAGE_TERMINATOR);
            return future;
        } finally {
            syncLock.unlock();
        }
    }

    public CompletableFuture<EslEvent> sendBackgroundApiCommand(Channel channel, final String command) {

        return sendApiSingleLineCommand(channel, command)
                .thenComposeAsync(result -> {
                    if (result.hasHeader(EslHeaders.Name.JOB_UUID)) {
                        final String jobId = result.getHeaderValue(EslHeaders.Name.JOB_UUID);
                        final CompletableFuture<EslEvent> resultFuture = new CompletableFuture<>();
                        backgroundJobs.put(jobId, resultFuture);
                        return resultFuture;
                    } else {
                        final CompletableFuture<EslEvent> resultFuture = new CompletableFuture<>();
                        resultFuture.completeExceptionally(new IllegalStateException("Missing Job-UUID header in bgapi response"));
                        return resultFuture;
                    }
                }, backgroundJobExecutor);
    }

    public CompletableFuture<EslMessage> sendApiMultiLineCommand(Channel channel, final List<String> commandLines) {
        //  Build command with double line terminator at the end
        final StringBuilder sb = new StringBuilder();
        for (final String line : commandLines) {
            sb.append(line);
            sb.append(LINE_TERMINATOR);
        }
        sb.append(LINE_TERMINATOR);
        syncLock.lock();
        try {
            final CompletableFuture<EslMessage> future = new CompletableFuture<>();
            channel.write(sb.toString());
            channel.flush();
            return future;
        } finally {
            syncLock.unlock();
        }
    }

    /**
     * Returns the Job UUID of that the response event will have.
     *
     * @param command cmd
     * @return Job-UUID as a string
     */
    public String sendAsyncCommand(final String command) {
        /*
         * Send synchronously to get the Job-UUID to return, the results of the actual
         * job request will be returned by the server as an async event.
         */
        EslMessage response = sendSyncSingleLineCommand(command);
        if (isTraceEnabled) {
            log.trace("sendAsyncCommand command : {}, response : {}", command, response);
        }
        if (response.hasHeader(EslHeaders.Name.JOB_UUID)) {
            return response.getHeaderValue(EslHeaders.Name.JOB_UUID);
        } else {
            log.warn("sendAsyncCommand command : {}, response : {}", command, EslHelper.formatEslMessage(response));
            throw new IllegalStateException("Missing Job-UUID header in bgapi response");
        }
    }

    /**
     * <p>close.</p>
     *
     * @return a {@link ChannelFuture} object.
     */
    public ChannelFuture close() {
        return channel.close();
    }

    class SyncCallback {
        private final CountDownLatch latch = new CountDownLatch(1);
        private EslMessage response;

        /**
         * Block waiting for the countdown latch to be released, then return the
         * associated response object.
         *
         * @return msg
         */
        EslMessage get() {
            try {
                log.trace("awaiting latch ... ");
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            log.trace("returning response [{}]", response);
            return response;
        }

        /**
         * Attach this response to the callback and release the countdown latch.
         *
         * @param response res
         */
        void handle(EslMessage response) {
            this.response = response;
            log.trace("releasing latch for response [{}]", response);
            latch.countDown();
        }
    }


}