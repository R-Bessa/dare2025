package protocols.broadcast.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class BroadcastMessage extends ProtoMessage {

	public final static short MESSAGE_ID = 302;

	private final Host sender;
	private final UUID messageID;
	private final byte[] payload;
    private final Map<Host, Integer> version_vector;

	public BroadcastMessage(Host sender, byte[] payload, Map<Host, Integer> version_vector) {
		super(MESSAGE_ID);
		this.sender = sender;
		this.messageID = UUID.randomUUID();
		this.payload = payload;
        this.version_vector = version_vector;
	}

    public BroadcastMessage(Host sender, UUID mID, byte[] payload, Map<Host, Integer> version_vector) {
        super(MESSAGE_ID);
        this.sender = sender;
        this.messageID = mID;
        this.payload = payload;
        this.version_vector = version_vector;
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

    public Map<Host, Integer> getVersion_vector() {
        return version_vector;
    }

    public static ISerializer<BroadcastMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(BroadcastMessage msg, ByteBuf out) throws IOException {
            Host.serializer.serialize(msg.sender, out);
            out.writeLong(msg.messageID.getMostSignificantBits());
            out.writeLong(msg.messageID.getLeastSignificantBits());
            if (msg.payload != null) {
                out.writeInt(msg.payload.length);
                out.writeBytes(msg.payload);
            } else {
                out.writeInt(0);
            }

            out.writeInt(msg.version_vector.size());
            for (Map.Entry<Host, Integer> e : msg.version_vector.entrySet()) {
                Host.serializer.serialize(e.getKey(), out);
                out.writeInt(e.getValue());
            }
        }

        @Override
        public BroadcastMessage deserialize(ByteBuf in) throws IOException {
            Host sender = Host.serializer.deserialize(in);
            UUID id = new UUID(in.readLong(), in.readLong());
            byte[] payload = null;
            int len = in.readInt();
            if (len > 0) {
                payload = new byte[len];
                in.readBytes(payload);
            }

            int vvSize = in.readInt();
            Map<Host, Integer> vv = new HashMap<>();
            for (int i = 0; i < vvSize; i++) {
                Host h = Host.serializer.deserialize(in);
                int clock = in.readInt();
                vv.put(h, clock);
            }

            return new BroadcastMessage(sender, id, payload, vv);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BroadcastMessage that = (BroadcastMessage) o;
        return Objects.equals(messageID, that.messageID);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messageID);
    }
}
