package com.netty02;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.UUID;

public class PersonServer {
    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();

            bootstrap
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new PersonCodec());

                            pipeline.addLast(new SimpleChannelInboundHandler<Person>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Person msg) throws Exception {
                                    int length = msg.getLength();
                                    byte[] content = msg.getContent();

                                    System.out.println("收到客户端消息, 长度: " + length + ", 内容:" + new String(content));

                                    Person person = new Person();
                                    String tmpContent = UUID.randomUUID().toString();
                                    person.setLength(tmpContent.getBytes().length);
                                    person.setContent(tmpContent.getBytes());

                                    ctx.writeAndFlush(person);
                                }
                            });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)          // (5)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(6666).sync();
            future.channel().closeFuture().sync();
        } catch(Exception e) {
            e.printStackTrace();
        }finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
