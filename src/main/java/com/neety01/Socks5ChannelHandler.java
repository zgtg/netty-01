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
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static sun.jvm.hotspot.runtime.PerfMemory.start;

public class Socks5ChannelHandler extends ChannelInboundHandlerAdapter {
    AttributeKey<Integer> attr = AttributeKey.valueOf("status");

    // key:src, val:dest
    Map<Channel, Channel> chanel1Map = new ConcurrentHashMap<>();
    // key:desc, val:src
    Map<Channel, Channel> chanel2Map = new ConcurrentHashMap<>();

    Bootstrap bootstrap = new Bootstrap();

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
            handleAuth(ctx, byteBuf);
        } else if (statusAttr.get() == 3) {// 建立连接
            handleConnection(ctx, byteBuf);
        } else {// 转发请求
            // 打印一下发送的内容
            byte[] reqBytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(reqBytes, 0, reqBytes.length);
//            System.out.println("客户端发送的消息内容: \n" + new String(reqBytes));


            chanel1Map.get(ctx.channel()).writeAndFlush(Unpooled.wrappedBuffer(reqBytes));
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
        respBytes[1] = 0x02;// 无需认证

        if (respBytes[1] == 0x00) {
            ctx.channel().attr(attr).set(3);
        } else {
            ctx.channel().attr(attr).set(2);
        }

        ctx.writeAndFlush(Unpooled.wrappedBuffer(respBytes));
    }

    /**
     * 认证过程
     * @param ctx
     * @param byteBuf
     */
    private void handleAuth(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        byte version = byteBuf.readByte();

        // 用户名
        byte nameLen = byteBuf.readByte();
        byte[] nameBytes = new byte[nameLen];
        byteBuf.readBytes(nameBytes, 0, nameLen);
        String name = new String(nameBytes, CharsetUtil.UTF_8);

        // 密码
        byte passLen = byteBuf.readByte();
        byte[] passBytes = new byte[passLen];
        byteBuf.readBytes(passBytes, 0, passLen);
        String pass = new String(passBytes, CharsetUtil.UTF_8);

        System.out.println("认证成功, 用户名: " + name + ", 密码: " + pass);

        ctx.writeAndFlush(Unpooled.buffer().writeByte(version).writeByte(0x00));
        ctx.channel().attr(attr).set(3);
    }

    /**
     * 连接阶段
     * @param ctx
     * @param byteBuf
     */
    private void handleConnection(final ChannelHandlerContext ctx, ByteBuf byteBuf) {
        Channel srcChannel = ctx.channel();
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
            int port = byteBuf.readShort();
            System.out.println("域名: " + domain + ", 端口: " + port);

            initProxyChannel(ctx.channel(), domain, port);
        } else if (addressType == 0x04) {

        }
    }

    /**
     * 初始化代理通道
     * @param host
     * @param port
     */
    void initProxyChannel(Channel srcChannel, String host, int port) {
        try {
            bootstrap
                    .group(srcChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    Socks5ChannelHandler.this.chanel1Map.put(srcChannel, ctx.channel());
                                    Socks5ChannelHandler.this.chanel2Map.put(ctx.channel(), srcChannel);

                                    ByteBuf respBuf = Unpooled.buffer();
                                    respBuf.writeByte(0x05);
                                    respBuf.writeByte(0x00);// 连接成功
                                    respBuf.writeByte(0x00);
                                    respBuf.writeByte(0x01);
                                    respBuf.writeByte(0x7f);
                                    respBuf.writeByte(0x00);
                                    respBuf.writeByte(0x00);
                                    respBuf.writeByte(0x01);
                                    respBuf.writeShort(((short)((InetSocketAddress)srcChannel.localAddress()).getPort()));
//                                    System.out.println("建立连接成功");

                                    srcChannel.attr(attr).set(4);

                                    srcChannel.writeAndFlush(respBuf);
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ByteBuf serverBuf = (ByteBuf) msg;

                                    byte[] reqBytes = new byte[serverBuf.readableBytes()];
                                    serverBuf.readBytes(reqBytes, 0, reqBytes.length);
//                                    System.out.println("服务端响应的消息内容: \n" + new String(reqBytes));


                                    srcChannel.writeAndFlush(Unpooled.wrappedBuffer(reqBytes));
                                }
                            });
                        }
                    });

            bootstrap.connect(host, port);
//            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
    }
}
