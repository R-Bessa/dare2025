package protocols.broadcast.request;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

public class BroadcastRequest extends ProtoRequest {

	public final static short REQUEST_ID = 302;
	
	private final Host sender;
	private final byte[] payload;
	
	public BroadcastRequest(Host sender, byte[] payload) {
		super(REQUEST_ID);
		this.sender = sender;
		this.payload = payload;
	}

    public byte[] encode() throws IOException {
		ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
		Host.serializer.serialize(sender, buffer);
		buffer.writeInt(payload.length);
		buffer.writeBytes(payload);

		byte[] result = new byte[buffer.readableBytes()];
		buffer.resetReaderIndex();
		buffer.readBytes(result);
		return result;
	}
}
