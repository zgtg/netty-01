package com.netty02;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

public class PersonCodec extends ByteToMessageCodec<Person> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List out) throws Exception {
        int length = 0;
        if (in.readableBytes() >= 4) {
            length = in.readInt();
        }

        byte[] content = null;
        if (in.readableBytes() >= length) {
            content = new byte[length];

            in.readBytes(content);
        }

        Person person = new Person();
        person.setLength(length);
        person.setContent(content);

        out.add(person);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Person msg, ByteBuf out) throws Exception {
        out.writeInt(msg.getLength());
        out.writeBytes(msg.getContent());
    }
}
