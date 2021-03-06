package com.neety01;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

public class NettyClient {

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

                            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                int status = 1;
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    ByteBuf byteBuf = Unpooled.buffer();
                                    byteBuf.writeByte(0x05);
                                    byteBuf.writeByte(0x03);
                                    byteBuf.writeByte(0x00);
                                    byteBuf.writeByte(0x01);
                                    byteBuf.writeByte(0x02);
                                    ctx.writeAndFlush(byteBuf);
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ByteBuf byteBuf = (ByteBuf) msg;

                                    if (status == 1) {
                                        handleInit(ctx, byteBuf);
                                    } else if (status == 2) {
                                        byte version = byteBuf.readByte();// version
                                        byte resp = byteBuf.readByte();// resp
                                        byte rsv = byteBuf.readByte();// rsv
                                        byte addressType = byteBuf.readByte();
                                        byte ip1 = byteBuf.readByte();
                                        byte ip2 = byteBuf.readByte();
                                        byte ip3 = byteBuf.readByte();
                                        byte ip4 = byteBuf.readByte();
                                        short port = byteBuf.readShort();

                                        System.out.println("服务端ip:" + ip1 + "." + ip2 + "." + ip3 + "." + ip4 + ", 端口: " + port);

                                        status = 3;

                                        ctx.channel().writeAndFlush(Unpooled.copiedBuffer("test", CharsetUtil.UTF_8));
                                    } else if (status == 3) {
                                        byte[] contents = new byte[byteBuf.readableBytes()];
                                        byteBuf.readBytes(contents, 0, contents.length);

                                        System.out.println(new String(contents));
                                    }
                                }

                                private void handleInit(ChannelHandlerContext ctx, ByteBuf byteBuf) {
                                    byte version = byteBuf.readByte();
                                    byte method = byteBuf.readByte();

                                    System.out.println("服务端返回认证方式: " + method);
                                    if (method == 0x00) {// 不认证
                                        sendConnectRequest(ctx);
                                    } else if (method == 0x02) {// 认证

                                    }
                                }

                                /**
                                 * 发送链接请求
                                 * @param ctx
                                 */
                                private void sendConnectRequest(ChannelHandlerContext ctx) {
                                    ByteBuf byteBuf = Unpooled.buffer();
                                    byteBuf.writeByte(0x05);//version
                                    byteBuf.writeByte(0x01);//cmd
                                    byteBuf.writeByte(0x00);// rsv
                                    byteBuf.writeByte(0x03);// addressType

                                    String host = "game01.g.youwanshe.cn";
                                    byte[] hostBytes = host.getBytes();
                                    byteBuf.writeByte(hostBytes.length);
                                    byteBuf.writeBytes(hostBytes);

                                    byteBuf.writeShort(0x50);

                                    ctx.writeAndFlush(byteBuf);

                                    status = 2;
                                }
                            });
                        }
                    });

            ChannelFuture future = bootstrap.connect("127.0.0.1", 1088).sync();
            future.channel().closeFuture().sync();
        } catch(Exception e) {
            e.printStackTrace();
        }finally {
            workerGroup.shutdownGracefully();
        }
    }
}
