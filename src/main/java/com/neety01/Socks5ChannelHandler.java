package com.neety01;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

public class Socks5ChannelHandler extends ChannelInboundHandlerAdapter {
    AttributeKey<Integer> attr = AttributeKey.valueOf("status");

    Channel destChannel = null;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(attr).set(1);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;

        Attribute<Integer> statusAttr = ctx.channel().attr(attr);
        if (statusAttr.get() == 1) {// 初始化
            handleInit(ctx, byteBuf);
        } else if (statusAttr.get() == 2) {// 认证

        } else if (statusAttr.get() == 3) {// 建立连接
            handleConnection(ctx, byteBuf);
        } else {// 转发请求
            byte[] reqBytes = new byte[byteBuf.readableBytes()];

            destChannel.writeAndFlush(byteBuf.retainedDuplicate());
        }
    }

    /**
     * 初始化阶段
     * @param ctx
     * @param byteBuf
     */
    private void handleInit(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        // 版本
        byte version = byteBuf.readByte();
        byte methodCounts = byteBuf.readByte();

        byte[] methods = new byte[methodCounts];
        byteBuf.readBytes(methods, 0, methodCounts);

        byte[] respBytes = new byte[2];
        respBytes[0] = version; // 版本号
        respBytes[1] = 0x00;// 无需认证

        if (respBytes[1] == 0x00) {
            ctx.channel().attr(attr).set(3);
        } else {
            ctx.channel().attr(attr).set(2);
        }

        ctx.writeAndFlush(Unpooled.wrappedBuffer(respBytes));
    }

    /**
     * 连接阶段
     * @param ctx
     * @param byteBuf
     */
    private void handleConnection(final ChannelHandlerContext ctx, ByteBuf byteBuf) {
        byte version = byteBuf.readByte();
        byte command = byteBuf.readByte();
        byte rsv = byteBuf.readByte();
        byte addressType = byteBuf.readByte();

        if (addressType == 0x01) {

        } else if (addressType == 0x03) {
            int domainLength = byteBuf.readByte();
            byte[] domainBytes = new byte[domainLength];
            byteBuf.readBytes(domainBytes, 0, domainLength);
            String domain = new String(domainBytes);
            System.out.println(domain);

            int port = byteBuf.readShort();
            System.out.println("域名: " + domain + ", 端口: " + port);


            EventLoopGroup workerGroup = new NioEventLoopGroup();

            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap
                        .group(workerGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                Socks5ChannelHandler.this.destChannel = ctx.channel();
                            }

                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx1, Object msg) throws Exception {
                                        ByteBuf serverBuf = (ByteBuf) msg;
                                        ctx.writeAndFlush(serverBuf.retainedDuplicate());
                                    }
                                });
                            }
                        });

                ChannelFuture future = bootstrap.connect(domain, port).sync();
                this.destChannel = future.channel();
                if (future.isSuccess()) {
                    ByteBuf respBuf = Unpooled.buffer();
                    respBuf.writeByte(0x05);
                    respBuf.writeByte(0x00);
                    respBuf.writeByte(rsv);
                    respBuf.writeByte(0x01);
                    respBuf.writeByte(127);
                    respBuf.writeByte(0);
                    respBuf.writeByte(0);
                    respBuf.writeByte(1);
                    respBuf.writeShort(((InetSocketAddress)ctx.channel().localAddress()).getPort());
                    System.out.println("建立连接成功");

                    ctx.channel().attr(attr).set(4);
                    ctx.writeAndFlush(respBuf);
                } else {
                    System.out.println("建立连接失败");
                }

                future.channel().closeFuture().sync();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                workerGroup.shutdownGracefully();
            }

        } else if (addressType == 0x04) {

        }
    }
}
