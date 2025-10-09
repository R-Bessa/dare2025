package protocols.crdt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Operation {
    private final String type;
    private final Set<UUID> add_ids;
    private final String element;

    public Operation(String type, Set<UUID> add_ids, String element) {
        this.type = type;
        this.add_ids = add_ids;
        this.element = element;
    }

    public String getType() { return type; }

    public Set<UUID> getAdd_ids() {
        return add_ids;
    }

    public String getElement() {
        return element;
    }

    public byte[] encode() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {

            byte[] typeBytes = type.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(typeBytes.length);
            dos.write(typeBytes);

            byte[] elemBytes = element.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(elemBytes.length);
            dos.write(elemBytes);

            dos.writeInt(add_ids.size());
            for (UUID id : add_ids) {
                dos.writeLong(id.getMostSignificantBits());
                dos.writeLong(id.getLeastSignificantBits());
            }

            dos.flush();
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to decode Operation", e);
        }

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

            int setSize = dis.readInt();
            Set<UUID> add_ids = new HashSet<>();
            for (int i = 0; i < setSize; i++) {
                long mostSig = dis.readLong();
                long leastSig = dis.readLong();
                add_ids.add(new UUID(mostSig, leastSig));
            }

            return new Operation(type, add_ids, element);

        } catch (IOException e) {
            throw new RuntimeException("Failed to decode Operation", e);
        }
    }
}
