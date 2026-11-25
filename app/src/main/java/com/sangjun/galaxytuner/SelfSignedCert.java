package com.sangjun.galaxytuner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A tiny self-signed X.509 v3 certificate writer.
 *
 * ADB pairing needs the RSA key wrapped in a certificate (TLS client certificate). Android does not
 * expose a public API to build one, and pulling in BouncyCastle's certificate builder or the
 * hidden sun.security classes just for this is not worth it, so the DER is assembled by hand:
 *
 * <pre>
 * Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
 * tbsCertificate ::= SEQUENCE {
 *     [0] { INTEGER 2 }            -- version v3
 *     INTEGER serialNumber
 *     AlgorithmIdentifier          -- sha256WithRSAEncryption
 *     Name issuer                  -- CN=...
 *     Validity { notBefore, notAfter }
 *     Name subject                 -- same
 *     SubjectPublicKeyInfo         -- taken from the public key itself
 * }
 * </pre>
 *
 * No extensions are written: the certificate is only ever used as a TLS client certificate for the
 * device's own adbd, and the peer accepts any certificate for the pairing handshake.
 */
public final class SelfSignedCert {

    private static final String OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11";
    private static final String OID_COMMON_NAME = "2.5.4.3";

    private SelfSignedCert() {
    }

    /**
     * Builds a self-signed certificate for {@code keyPair}.
     *
     * @param commonName the subject (for example the app label); must be plain ASCII
     */
    public static X509Certificate create(KeyPair keyPair, String commonName) throws Exception {
        return create(keyPair, commonName, 20L * 365L * 24L * 60L * 60L * 1000L);
    }

    /**
     * Builds a self-signed certificate that stays valid for {@code validityMillis}.
     */
    public static X509Certificate create(KeyPair keyPair, String commonName, long validityMillis)
            throws Exception {
        byte[] algorithm = sequence(oid(OID_SHA256_WITH_RSA), nullValue());
        Date notBefore = new Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
        Date notAfter = new Date(System.currentTimeMillis() + validityMillis);
        byte[] name = sequence(set(sequence(oid(OID_COMMON_NAME), utf8(commonName))));
        byte[] validity = sequence(utcTime(notBefore), utcTime(notAfter));
        BigInteger serial = new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE);

        byte[] tbs = sequence(
                // [0] EXPLICIT version 2 = X.509 v3
                explicit(0, integer(BigInteger.valueOf(2))),
                integer(serial),
                algorithm,
                name,
                validity,
                name,
                keyPair.getPublic().getEncoded()
        );

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(tbs);
        byte[] signature = signer.sign();

        byte[] certificate = sequence(tbs, algorithm, bitString(signature));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate));
    }

    // ---------------------------------------------------------------------------------------
    // Minimal DER writer
    // ---------------------------------------------------------------------------------------

    private static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 6);
        out.write(tag);
        int length = content.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            int bytes = 0;
            for (int value = length; value > 0; value >>= 8) {
                bytes++;
            }
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) {
                out.write((length >> (8 * i)) & 0xFF);
            }
        }
        out.write(content, 0, content.length);
        return out.toByteArray();
    }

    private static byte[] sequence(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    private static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    private static byte[] integer(BigInteger value) {
        return tlv(0x02, value.toByteArray());
    }

    private static byte[] nullValue() {
        return tlv(0x05, new byte[0]);
    }

    private static byte[] utf8(String text) {
        return tlv(0x0C, text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] utcTime(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(0x17, format.format(date).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] bitString(byte[] bytes) {
        byte[] content = new byte[bytes.length + 1];
        // First byte is the number of unused bits at the end of the last byte.
        System.arraycopy(bytes, 0, content, 1, bytes.length);
        return tlv(0x03, content);
    }

    private static byte[] explicit(int tagNumber, byte[] content) {
        return tlv(0xA0 | tagNumber, content);
    }

    /** Encodes a dotted OID (for example 1.2.840.113549.1.1.11) as DER. */
    private static byte[] oid(String dotted) {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long value = Long.parseLong(parts[i]);
            int bytes = 1;
            for (long probe = value >> 7; probe > 0; probe >>= 7) {
                bytes++;
            }
            for (int b = bytes - 1; b >= 0; b--) {
                int piece = (int) ((value >> (7 * b)) & 0x7F);
                out.write(b == 0 ? piece : (piece | 0x80));
            }
        }
        return tlv(0x06, out.toByteArray());
    }

    private static byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
