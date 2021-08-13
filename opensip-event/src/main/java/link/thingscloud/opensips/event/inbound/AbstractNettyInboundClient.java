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

package link.thingscloud.opensips.event.inbound;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import link.thingscloud.opensips.event.InboundClientService;
import link.thingscloud.opensips.event.inbound.handler.InboundChannelHandler;
import link.thingscloud.opensips.event.inbound.listener.ChannelEventListener;
import link.thingscloud.opensips.event.inbound.option.OutboundClientOption;
import link.thingscloud.opensips.event.inbound.option.ServerOption;
import link.thingscloud.opensips.event.transport.message.EslFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author : <a href="mailto:ant.zhou@aliyun.com">zhouhailin</a>
 */
abstract class AbstractNettyInboundClient implements ChannelEventListener, InboundClientService {

    final Bootstrap bootstrap;
    final EventLoopGroup workerGroup;
    final ExecutorService publicExecutor;

    final OutboundClientOption option;
    final Logger log = LoggerFactory.getLogger(getClass());
    private Channel channel;

    AbstractNettyInboundClient(OutboundClientOption option) {
        this.option = option;

        bootstrap = new Bootstrap();

        publicExecutor = new ScheduledThreadPoolExecutor(option.publicExecutorThread(),
                new DefaultThreadFactory("Opensips-Executor", true));

        workerGroup = new NioEventLoopGroup(option.workerGroupThread());
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.SO_SNDBUF, option.sndBufSize())
                .option(ChannelOption.SO_RCVBUF, option.rcvBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("encoder", new StringEncoder());
                        pipeline.addLast("decoder", new EslFrameDecoder(8192));
                        if (option.readerIdleTimeSeconds() > 0 && option.readTimeoutSeconds() > 0
                                && option.readerIdleTimeSeconds() < option.readTimeoutSeconds()) {
                            pipeline.addLast("idleState", new IdleStateHandler(option.readerIdleTimeSeconds(), 0, 0));
                            pipeline.addLast("readTimeout", new ReadTimeoutHandler(option.readTimeoutSeconds()));
                        }
                        // now the inbound client logic
                        pipeline.addLast("clientHandler", new InboundChannelHandler(AbstractNettyInboundClient.this, publicExecutor, option.disablePublicExecutor()));
                    }
                });
    }

    @Override
    public void start() {
        log.info("outbound client will start...");
        ServerOption serverOption = option.serverOptions().get(0);
        ChannelFuture f = bootstrap.bind(serverOption.host(), serverOption.port()).syncUninterruptibly();

        if (f != null && f.isSuccess()) {
            log.info("outbound client server start success, listen port on {}", serverOption.port());
            //获取通道
            channel = f.channel();
        } else {
            log.info("outbound client server start fail");
        }
    }

}
