package com.ldd.xiaoaitogemini;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;

/**
 * Minimal, single-purpose ADB host client for 127.0.0.1:5555.
 * No root, AccessibilityService, Shizuku or native ADB binary required.
 * IMPORTANT: Requires adbd TCP mode and authorization. It does not bypass it.
 */
final class LocalAdbClient {
    private static final int CNXN = code("CNXN");
    private static final int AUTH = code("AUTH");
    private static final int OPEN = code("OPEN");
    private static final int OKAY = code("OKAY");
    private static final int WRTE = code("WRTE");
    private static final int CLSE = code("CLSE");
    private static final int ADB_PROTOCOL_VERSION = 0x01000001;
    private static final int MAX_PAYLOAD = 4096;
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = fromHex("3021300906052b0e03021a05000414");

    private final AdbKeyStore keys;

    LocalAdbClient(Context context) {
        keys = new AdbKeyStore(context);
    }

    /** The command is hardcoded by the caller; no free-form command UI exists. */
    String shell(String command) throws Exception {
        KeyPair keyPair = keys.loadOrCreate();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 1500);
            socket.setSoTimeout(4000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            send(out, CNXN, ADB_PROTOCOL_VERSION, MAX_PAYLOAD, bytes("host::\0"));
            boolean sentSignature = false;
            boolean sentPublicKey = false;
            boolean connected = false;
            for (int attempts = 0; attempts < 8; attempts++) {
                Message m = receive(in);
                if (m.command == CNXN) {
                    connected = true;
                    break;
                }
                if (m.command != AUTH || m.arg0 != 1) {
                    throw new IOException("ADB bắt tay không hợp lệ: " + fourcc(m.command));
                }
                if (!sentSignature) {
                    send(out, AUTH, 2, 0, signToken(m.data, keyPair));
                    sentSignature = true;
                } else if (!sentPublicKey) {
                    send(out, AUTH, 3, 0, AdbKeyStore.encodeAdbPublicKey((RSAPublicKey)keyPair.getPublic()));
                    sentPublicKey = true;
                    // On first pairing the device will display an RSA authorization dialog.
                } else {
                    throw new IOException("ADB chưa được cấp quyền. Hãy chấp nhận hộp thoại gỡ lỗi trên điện thoại.");
                }
            }
            if (!connected) throw new IOException("Không thể xác thực ADB trên điện thoại");

            int localId = 1;
            send(out, OPEN, localId, 0, bytes("shell:" + command + "\0"));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int remoteId = 0;
            boolean opened = false;
            while (true) {
                Message m = receive(in);
                if (m.command == OKAY) {
                    if (m.arg1 == localId) {
                        opened = true;
                        remoteId = m.arg0;
                    }
                } else if (m.command == WRTE) {
                    if (m.arg1 != localId) continue;
                    if (output.size() < 64 * 1024) output.write(m.data);
                    send(out, OKAY, localId, m.arg0, new byte[0]);
                } else if (m.command == CLSE) {
                    if (opened) send(out, CLSE, localId, remoteId, new byte[0]);
                    return output.toString("UTF-8").trim();
                } else if (m.command == AUTH) {
                    throw new IOException("ADB mất xác thực giữa phiên");
                }
            }
        }
    }

    private static byte[] signToken(byte[] token, KeyPair pair) throws Exception {
        if (token.length != 20) throw new IOException("ADB token không phải SHA-1 (20 byte)");
        byte[] digestInfo = new byte[SHA1_DIGEST_INFO_PREFIX.length + token.length];
        System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA1_DIGEST_INFO_PREFIX.length);
        System.arraycopy(token, 0, digestInfo, SHA1_DIGEST_INFO_PREFIX.length, token.length);
        // RSA private-key encryption with PKCS#1 type-1 padding == RSA_sign(NID_sha1, token).
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pair.getPrivate());
        return cipher.doFinal(digestInfo);
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] data) throws IOException {
        int checksum = 0;
        for (byte b : data) checksum += (b & 0xff);
        byte[] header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1).putInt(data.length)
                .putInt(checksum).putInt(command ^ 0xffffffff).array();
        out.write(header);
        if (data.length > 0) out.write(data);
        out.flush();
    }

    private static Message receive(InputStream in) throws IOException {
        byte[] header = new byte[24];
        readFully(in, header);
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = bb.getInt();
        int arg0 = bb.getInt();
        int arg1 = bb.getInt();
        int len = bb.getInt();
        int checksum = bb.getInt();
        int magic = bb.getInt();
        if ((cmd ^ 0xffffffff) != magic || len < 0 || len > 1024 * 1024)
            throw new IOException("ADB packet không hợp lệ");
        byte[] data = new byte[len];
        readFully(in, data);
        int sum = 0;
        for (byte b : data) sum += (b & 0xff);
        if (sum != checksum) throw new IOException("ADB checksum không đúng");
        return new Message(cmd, arg0, arg1, data);
    }

    private static void readFully(InputStream in, byte[] data) throws IOException {
        int pos = 0;
        while (pos < data.length) {
            int n = in.read(data, pos, data.length - pos);
            if (n < 0) throw new EOFException("ADB connection closed");
            pos += n;
        }
    }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static int code(String name) {
        byte[] b = bytes(name);
        return (b[0] & 255) | ((b[1] & 255) << 8) | ((b[2] & 255) << 16) | ((b[3] & 255) << 24);
    }
    private static String fourcc(int c) {
        return "" + (char) (c & 255) + (char) ((c >> 8) & 255) + (char) ((c >> 16) & 255) + (char) ((c >> 24) & 255);
    }
    private static byte[] fromHex(String s) {
        byte[] data = new byte[s.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return data;
    }
    private static final class Message {
        final int command, arg0, arg1;
        final byte[] data;
        Message(int command, int arg0, int arg1, byte[] data) {
            this.command = command; this.arg0 = arg0; this.arg1 = arg1; this.data = data;
        }
    }
}
