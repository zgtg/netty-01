package com.netty02;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class PersonClient {
    public static void main(String[] args) {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();

            bootstrap
                    .group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            pipeline.addLast(new PersonCodec());


                            pipeline.addLast(new SimpleChannelInboundHandler<Person>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    for(int i=0; i<10; i++) {
                                        Person person = new Person();

                                        String content = "消息" + i;

                                        person.setLength(content.getBytes().length);
                                        person.setContent(content.getBytes());

                                        ctx.writeAndFlush(person);
                                    }
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Person msg) throws Exception {
                                    int length = msg.getLength();
                                    String content = new String(msg.getContent());

                                    System.out.println("收到服务器消息, 长度: " + length + ", 内容: " + content);
                                }
                            });
                        }
                    });
            ChannelFuture future = bootstrap.connect("127.0.0.1", 6666).sync();
            future.channel().closeFuture().sync();
        } catch(Exception e) {
            e.printStackTrace();
        }finally {
            workerGroup.shutdownGracefully();
        }
    }
}
