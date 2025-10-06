package protocols.broadcast.byzantine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.SignaturesHelper;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class EchoMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 304;

    private final Host originalSender;
    private final Host sender;
    private final UUID messageID;
    private final byte[] payload;
    private final byte[] originalSignature;


    public EchoMessage(Host originalSender, Host sender, UUID mID, byte[] payload, byte[] originalSignature) {
        super(MESSAGE_ID);
        this.originalSender = originalSender;
        this.sender = sender;
        this.messageID = mID;
        this.payload = payload;
        this.originalSignature = originalSignature;
    }

    public Host getOriginalSender() {
        return this.originalSender;
    }

    public boolean verifyOriginalSignature(PublicKey publicKey) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        return SignaturesHelper.checkSignature(payload, originalSignature, publicKey);
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

    public final static SignedMessageSerializer<EchoMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(EchoMessage msg, ByteBuf out) throws IOException {
            Host.serializer.serialize(msg.originalSender, out);
            Host.serializer.serialize(msg.sender, out);
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
        public EchoMessage deserializeBody(ByteBuf in) throws IOException {
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
            return new EchoMessage(original_sender, sender, id, payload, sig);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return EchoMessage.serializer;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EchoMessage echo = (EchoMessage) o;
        return Objects.equals(originalSender, echo.originalSender) && Objects.equals(sender, echo.sender) && Objects.equals(messageID, echo.messageID) && Objects.deepEquals(payload, echo.payload) && Objects.deepEquals(originalSignature, echo.originalSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalSender, sender, messageID, Arrays.hashCode(payload), Arrays.hashCode(originalSignature));
    }
}
