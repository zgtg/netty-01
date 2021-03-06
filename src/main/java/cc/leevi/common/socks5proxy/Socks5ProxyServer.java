package cc.leevi.common.socks5proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sun.tools.doclint.Entity.nu;

public class Socks5ProxyServer {
    private Logger logger = LoggerFactory.getLogger(Socks5ProxyServer.class);

    private ServerBootstrap serverBootstrap;

    private EventLoopGroup serverEventLoopGroup;

    private Channel acceptorChannel;

    public void startServer(){
        logger.info("Proxy Server starting...");

        serverEventLoopGroup = new NioEventLoopGroup(4);

        serverBootstrap = new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .childHandler(new SocksServerInitializer())
                .group(serverEventLoopGroup);
        acceptorChannel = serverBootstrap.bind(1088).syncUninterruptibly().channel();
    }

    public void shutdown(){
        logger.info("Proxy Server shutting down...");
        acceptorChannel.close().syncUninterruptibly();
        serverEventLoopGroup.shutdownGracefully().syncUninterruptibly();
        logger.info("shutdown completed!");
    }


    public static void main(String[] args) {
        long num = ipToNum("192.168.1.93");
        System.out.println(num);
        System.out.println(numToIp(num));
    }


    /**
     * ip转换为数字
     * @param ip
     * @return
     */
    public static Long ipToNum(String ip) {
        String[] ipArr = ip.split("\\.");

        return (Long.parseLong(ipArr[0]) << 24) + (Long.parseLong(ipArr[1]) << 16) + (Long.parseLong(ipArr[2]) << 8) + (Long.parseLong(ipArr[3]));
    }

    /**
     * 数字转换为IP
     * @param num
     * @return
     */
    public static String numToIp(long num ) {
        long ip1 = (num & 0xffffffff) >>> 24;
        long ip2 = ((num & 0x00ffffff) >>> 16);
        long ip3 = (num & 0x0000ffff) >>> 8;
        long ip4 = (num) & 0x000000ff;
        return ip1 + "." + ip2 + "."+ ip3 + "."+ ip4;
    }

}
