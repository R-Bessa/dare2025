package utils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import com.google.common.hash.Hashing;

import pt.unl.fct.di.novasys.network.data.Host;

public class HashProducer {
    private static ByteBuffer append = null;
    private static int off = 0;
    private static int size = 0;
    private final byte[] selfAddr;

    public HashProducer(Host self) {
        selfAddr = self.getAddress().getAddress();
        size = selfAddr.length + Integer.BYTES + Long.BYTES;
        append = ByteBuffer.allocate(size);
        append.put(selfAddr);
        append.putInt(self.getPort());
        off = append.arrayOffset();
    }


    public static int hash(byte[] contents) {
        ByteBuffer buffer = ByteBuffer.allocate(contents.length + size);
        buffer.put(contents);
        append.putLong(off, System.currentTimeMillis());
        buffer.put(append);
        return Arrays.hashCode(buffer.array());
    }

    public int hash() {
        return Arrays.hashCode(selfAddr);
    }

    public static BigInteger toNumberFormat(byte[] peerID) {
        return new BigInteger(peerID);
    }

    public static byte[] hashValue(String value) {
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).asBytes();
    }

    public static int randomInitializer(byte[] peerID) {
        return Arrays.hashCode(peerID);
    }

    public static String hashSet(Set<String> set) {
        try {
            List<String> sorted = new ArrayList<>(set);
            Collections.sort(sorted);

            StringBuilder sb = new StringBuilder();
            for (String s : sorted) sb.append(s).append(",");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes());

            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) hex.append(String.format("%02x", b));

            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}

