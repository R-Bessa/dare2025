package protocols.broadcast.messages;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.SignaturesHelper;

public class SignedBroadcastMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 303;
	
	private final Host originalSender;
    private final Host sender;
	private final UUID messageID;
	private final byte[] payload;
    private final byte[] originalSignature;
    private final Map<Host, Integer> version_vector;



    public SignedBroadcastMessage(Host originalSender, Host sender, byte[] payload, byte[] originalSignature, Map<Host, Integer> version_vector) {
        super(MESSAGE_ID);
        this.originalSender = originalSender;
        this.sender = sender;
        this.messageID = UUID.randomUUID();
        this.payload = payload;
        this.originalSignature = originalSignature;
        this.version_vector = version_vector;
    }

    public SignedBroadcastMessage(Host originalSender, Host sender, UUID mID, byte[] payload, byte[] originalSignature, Map<Host, Integer> version_vector) {
        super(MESSAGE_ID);
        this.originalSender = originalSender;
        this.sender = sender;
        this.messageID = mID;
        this.payload = payload;
        this.originalSignature = originalSignature;
        this.version_vector = version_vector;
    }
	
	public Host getOriginalSender() {
		return this.originalSender;
	}

    public Host getSender() {
        return sender;
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

    public Map<Host, Integer> getVersion_vector() {
        return version_vector;
    }

    public boolean verifyOriginalSignature(PublicKey publicKey) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        return SignaturesHelper.checkSignature(payload, originalSignature, publicKey);
    }

    public final static SignedMessageSerializer<SignedBroadcastMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(SignedBroadcastMessage msg, ByteBuf out) throws IOException {
            Host.serializer.serialize(msg.originalSender, out);
            Host.serializer.serialize(msg.sender, out);
            out.writeLong(msg.messageID.getMostSignificantBits());
            out.writeLong(msg.messageID.getLeastSignificantBits());
            if (msg.payload != null) {
                out.writeInt(msg.payload.length);
                out.writeBytes(msg.payload);
            } else
                out.writeInt(0);

            if (msg.originalSignature != null) {
                out.writeInt(msg.originalSignature.length);
                out.writeBytes(msg.originalSignature);
            } else
                out.writeInt(0);

            if (msg.version_vector != null) {
                out.writeInt(msg.version_vector.size());
                for (Map.Entry<Host, Integer> e : msg.version_vector.entrySet()) {
                    Host.serializer.serialize(e.getKey(), out);
                    out.writeInt(e.getValue());
                }
            }
            else
                out.writeInt(0);

        }

        @Override
        public SignedBroadcastMessage deserializeBody(ByteBuf in) throws IOException {
            Host original_sender = Host.serializer.deserialize(in);
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

            int vvSize = in.readInt();
            Map<Host, Integer> vv = new HashMap<>();
            for (int i = 0; i < vvSize; i++) {
                Host h = Host.serializer.deserialize(in);
                int clock = in.readInt();
                vv.put(h, clock);
            }

            return new SignedBroadcastMessage(original_sender, sender, id, payload, sig, vv);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return SignedBroadcastMessage.serializer;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SignedBroadcastMessage that = (SignedBroadcastMessage) o;
        return Objects.equals(messageID, that.messageID);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messageID);
    }
}
