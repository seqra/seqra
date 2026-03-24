package security.sources;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Netty handlers.
 */
public class NettySourceSamples {

    /** ChannelInboundHandler.channelRead */
    public static class InboundHandler extends ChannelInboundHandlerAdapter {

        private DataSource dataSource;

        @Override
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String str = msg.toString();
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
            }
        }
    }

    /** ByteToMessageDecoder.decode */
    public static class Decoder extends ByteToMessageDecoder {

        private DataSource dataSource;

        // ANALYZER LIMITATION: Taint from ByteBuf parameter does not propagate through
        // ByteBuf.readBytes() -> new String(bytes). The source rule correctly marks ByteBuf
        // as $UNTRUSTED via the decode callback pattern, but the analyzer lacks taint
        // propagation summaries for ByteBuf.readBytes() and similar ByteBuf read methods.
        // TODO: Re-enable when ByteBuf taint propagation summaries are added to opentaint-config.
        @Override
        // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            byte[] bytes = new byte[in.readableBytes()];
            in.readBytes(bytes);
            String str = new String(bytes);
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
            }
        }
    }

    /** SimpleChannelInboundHandler.channelRead0 */
    public static class SimpleHandler extends io.netty.channel.SimpleChannelInboundHandler<String> {

        private DataSource dataSource;

        // ANALYZER LIMITATION: The channelRead0 callback pattern uses a generic type
        // parameter ($MSG_TYPE $UNTRUSTED) which should match the String parameter, but
        // taint does not flow from the generic-typed parameter through to the sink.
        // The rule pattern is correct — the analyzer does not propagate taint from the
        // callback parameter when matched via the generic channelRead0 signature.
        // TODO: Re-enable when analyzer handles generic callback parameter taint propagation.
        @Override
        // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM t WHERE x = '" + msg + "'");
            }
        }
    }
}
