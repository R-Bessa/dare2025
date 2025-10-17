package protocols.broadcast.messages;

import java.io.IOException;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

public class SignedBroadcastMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 303;
	
	private final Host originalSender;
	private final UUID messageID;
	private final byte[] payload;
    private final byte[] originalSignature;


    public SignedBroadcastMessage(Host originalSender, byte[] payload, byte[] originalSignature) {
        super(MESSAGE_ID);
        this.originalSender = originalSender;
        this.messageID = UUID.randomUUID();
        this.payload = payload;
        this.originalSignature = originalSignature;
    }

    public SignedBroadcastMessage(Host originalSender, UUID mID, byte[] payload, byte[] originalSignature) {
        super(MESSAGE_ID);
        this.originalSender = originalSender;
        this.messageID = mID;
        this.payload = payload;
        this.originalSignature = originalSignature;
    }
	
	public Host getOriginalSender() {
		return this.originalSender;
	}
	
	public UUID getMessageID() {
		return this.messageID;
	}
	
	public byte[] getPayload() {
		return this.payload;
	}

    public byte[] getOriginalSignature() {
        return originalSignature;
    }

    public final static SignedMessageSerializer<SignedBroadcastMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(SignedBroadcastMessage msg, ByteBuf out) throws IOException {
            Host.serializer.serialize(msg.originalSender, out);
            out.writeLong(msg.messageID.getMostSignificantBits());
            out.writeLong(msg.messageID.getLeastSignificantBits());
            if (msg.payload != null) {
                out.writeInt(msg.payload.length);
                out.writeBytes(msg.payload);
            } else {
                out.writeInt(0);
            }

            if (msg.originalSignature != null) {
                out.writeInt(msg.originalSignature.length);
                out.writeBytes(msg.originalSignature);
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

            byte[] sig = null;
            int sig_len = in.readInt();
            if (sig_len > 0) {
                sig = new byte[sig_len];
                in.readBytes(sig);
            }
            return new SignedBroadcastMessage(sender, id, payload, sig);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return SignedBroadcastMessage.serializer;
    }
	
}
