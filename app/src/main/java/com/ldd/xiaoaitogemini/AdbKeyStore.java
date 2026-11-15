package com.ldd.xiaoaitogemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Creates and retains a local 2048-bit ADB host key. No key material is uploaded.
 * This private key allows ADB shell access after device-side authorization;
 * Android backups are disabled in the manifest to limit key exposure.
 */
final class AdbKeyStore {
    private static final String PREFS = "adb_local_host_key";
    private static final String PRIVATE = "privatePkcs8";
    private static final String PUBLIC = "publicX509";
    private final Context ctx;

    AdbKeyStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    synchronized KeyPair loadOrCreate() throws Exception {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String privB64 = prefs.getString(PRIVATE, null);
        String pubB64 = prefs.getString(PUBLIC, null);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (privB64 != null && pubB64 != null) {
            return new KeyPair(
                    factory.generatePublic(new X509EncodedKeySpec(Base64.decode(pubB64, Base64.DEFAULT))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.DEFAULT))));
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        if (!prefs.edit()
                .putString(PRIVATE, Base64.encodeToString(pair.getPrivate().getEncoded(), Base64.NO_WRAP))
                .putString(PUBLIC, Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP))
                .commit()) {
            throw new IllegalStateException("Không lưu được khóa ADB cục bộ");
        }
        return pair;
    }

    /** ADB's custom RSAPublicKey structure, NOT X.509 encoding. */
    static byte[] encodeAdbPublicKey(RSAPublicKey publicKey) {
        BigInteger n = publicKey.getModulus();
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        BigInteger nLow = n.and(two32.subtract(BigInteger.ONE));
        BigInteger n0inv = nLow.modInverse(two32).negate().mod(two32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);

        ByteBuffer blob = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        blob.putInt(64); // 2048-bit modulus = 64 32-bit words
        blob.putInt(n0inv.intValue());
        blob.put(littleEndianFixed(n, 256));
        blob.put(littleEndianFixed(rr, 256));
        blob.putInt(publicKey.getPublicExponent().intValue());
        String base64 = Base64.encodeToString(blob.array(), Base64.NO_WRAP);
        return (base64 + " XiaoAIBackBridge@android\0").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] littleEndianFixed(BigInteger number, int size) {
        byte[] bigEndian = number.toByteArray();
        byte[] out = new byte[size];
        for (int i = 0; i < size && i < bigEndian.length; i++) {
            out[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return out;
    }
}
