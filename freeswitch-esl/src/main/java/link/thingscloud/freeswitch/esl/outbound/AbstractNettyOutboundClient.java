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

import com.google.common.util.concurrent.AbstractService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import link.thingscloud.freeswitch.esl.OutboundClientService;
import link.thingscloud.freeswitch.esl.outbound.handler.OutboundChannelHandler;
import link.thingscloud.freeswitch.esl.outbound.listener.ChannelEventListener;
import link.thingscloud.freeswitch.esl.outbound.option.OutboundClientOption;
import link.thingscloud.freeswitch.esl.transport.message.EslFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author : <a href="mailto:ant.zhou@aliyun.com">zhouhailin</a>
 */
abstract class AbstractNettyOutboundClient extends AbstractService implements ChannelEventListener, OutboundClientService {

    final ServerBootstrap bootstrap;
    final EventLoopGroup workerGroup;
    final EventLoopGroup parentGroup;
    final ExecutorService publicExecutor;
    final OutboundClientOption option;

    final Logger log = LoggerFactory.getLogger(getClass());

    AbstractNettyOutboundClient(OutboundClientOption option) {
        this.option = option;

        bootstrap = new ServerBootstrap();

        publicExecutor = new ScheduledThreadPoolExecutor(option.publicExecutorThread(),
                new DefaultThreadFactory("Outbound-Executor", true));
        parentGroup = new NioEventLoopGroup(option.parentGroupThread());
        workerGroup = new NioEventLoopGroup(option.workerGroupThread());
        bootstrap.group(parentGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.SO_SNDBUF, option.sndBufSize())
                .childOption(ChannelOption.SO_RCVBUF, option.rcvBufSize())
                .option(ChannelOption.SO_BACKLOG, 128)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("encoder", new StringEncoder());
                        pipeline.addLast("decoder", new EslFrameDecoder(8192, true));
                        if (option.readerIdleTimeSeconds() > 0 && option.readTimeoutSeconds() > 0
                                && option.readerIdleTimeSeconds() < option.readTimeoutSeconds()) {
                            pipeline.addLast("idleState", new IdleStateHandler(option.readerIdleTimeSeconds(), 0, 0));
                            pipeline.addLast("readTimeout", new ReadTimeoutHandler(option.readTimeoutSeconds()));
                        }
                        // now the inbound client logic
                        pipeline.addLast("clientHandler", new OutboundChannelHandler(AbstractNettyOutboundClient.this, publicExecutor, option.disablePublicExecutor()));
                    }
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        this.startAsync();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        this.stopAsync();
    }

}
