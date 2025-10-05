package protocols.crdt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Operation {
    private final String type;
    private final UUID add_id;
    private final String element;

    public Operation(String type, UUID add_id, String element) {
        this.type = type;
        this.add_id = add_id;
        this.element = element;
    }

    public String getType() { return type; }

    public UUID getAdd_id() {
        return add_id;
    }

    public String getElement() {
        return element;
    }

    public byte[] encode() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {

            byte[] typeBytes = this.type.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(typeBytes.length);
            dos.write(typeBytes);

            byte[] elemBytes = this.element.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(elemBytes.length);
            dos.write(elemBytes);

            dos.writeLong(this.add_id.getMostSignificantBits());
            dos.writeLong(this.add_id.getLeastSignificantBits());
            dos.flush();
            return bos.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Operation decode(byte[] payload) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(payload);
             DataInputStream dis = new DataInputStream(bis)) {

            int typeLen = dis.readInt();
            byte[] typeBytes = new byte[typeLen];
            dis.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.UTF_8);

            int elemLen = dis.readInt();
            byte[] elemBytes = new byte[elemLen];
            dis.readFully(elemBytes);
            String element = new String(elemBytes, StandardCharsets.UTF_8);

            long mostSig = dis.readLong();
            long leastSig = dis.readLong();
            UUID add_id = new UUID(mostSig, leastSig);

            return new Operation(type, add_id, element);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
