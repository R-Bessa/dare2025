package protocols.broadcast.request.message;

import java.io.IOException;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

public class SignedBroadcastMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 301;
	
	private Host sender;
	private final UUID messageID;
	private final byte[] payload;
	
	public SignedBroadcastMessage(Host sender, byte[] payload) {
		super(MESSAGE_ID);
		this.sender = sender;
		this.messageID = UUID.randomUUID();
		this.payload = payload;
	}
	
	public SignedBroadcastMessage(Host sender, UUID mID, byte[] payload) {
		super(MESSAGE_ID);
		this.sender = sender;
		this.messageID = mID;
		this.payload = payload;
	}
	
	public Host getSender() {
		return this.sender;
	}
	
	public UUID getMessageID() {
		return this.messageID;
	}
	
	public byte[] getPayload() {
		return this.payload;
	}
	
	public void setSender(Host sender) {
		this.sender = sender;
	}

	public final static SignedMessageSerializer<SignedBroadcastMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(SignedBroadcastMessage msg, ByteBuf out) throws IOException {
            Host.serializer.serialize(msg.sender, out);
            out.writeLong(msg.messageID.getMostSignificantBits());
            out.writeLong(msg.messageID.getLeastSignificantBits());
            if (msg.payload != null) {
                out.writeInt(msg.payload.length);
                out.writeBytes(msg.payload);
            } else {
                out.writeInt(0);
            }
        }

        @Override
        public SignedBroadcastMessage deserializeBody(ByteBuf in) throws IOException {
            Host sender = Host.serializer.deserialize(in);
            UUID id = new UUID(in.readLong(), in.readLong());
            byte[] payload = null;
            int len = in.readInt();
            if (len > 0) {
                payload = new byte[len];
                in.readBytes(payload);
            }
            return new SignedBroadcastMessage(sender, id, payload);
        }
    };
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return SignedBroadcastMessage.serializer;
	}
	
}
