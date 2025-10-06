package protocols.broadcast.byzantine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class ReadyMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 305;

    private final Host sender;
    private final UUID messageID;
    private final byte[] payload;


    public ReadyMessage(Host sender, UUID mID, byte[] payload) {
        super(MESSAGE_ID);
        this.sender = sender;
        this.messageID = mID;
        this.payload = payload;
    }

    public UUID getMessageID() {
        return this.messageID;
    }

    public byte[] getPayload() {
        return this.payload;
    }


    public final static SignedMessageSerializer<ReadyMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(ReadyMessage msg, ByteBuf out) throws IOException {
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
        public ReadyMessage deserializeBody(ByteBuf in) throws IOException {
            Host sender = Host.serializer.deserialize(in);
            UUID id = new UUID(in.readLong(), in.readLong());
            byte[] payload = null;
            int len = in.readInt();
            if (len > 0) {
                payload = new byte[len];
                in.readBytes(payload);
            }
            return new ReadyMessage(sender, id, payload);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return ReadyMessage.serializer;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ReadyMessage that = (ReadyMessage) o;
        return Objects.equals(sender, that.sender) && Objects.equals(messageID, that.messageID) && Objects.deepEquals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, messageID, Arrays.hashCode(payload));
    }
}
