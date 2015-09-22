package org.matrix.androidsdk.ssl;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;

/**
 * Various utility classes for dealing with X509Certificates
 */
public class CertUtil {

    /**
     * Generates the SHA-256 fingerprint of the given certificate
     * @param cert
     * @return
     */
    public static byte[] generateSha256Fingerprint(X509Certificate cert) {
        final byte[] fingerprint;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA256");
            fingerprint = md.digest(cert.getEncoded());
        } catch(Exception e) {
            throw new RuntimeException(e);
        }

        return fingerprint;
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String fingerprintToHexString(byte[] fingerprint) {
        char[] hexChars = new char[fingerprint.length * 3];
        for ( int j = 0; j < fingerprint.length; j++ ) {
            int v = fingerprint[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars).trim();
    }

    /**
     * Recursively checks the exception to see if it was caused by an
     * UnrecognizedCertificateException
     * @param e
     * @return The UnrecognizedCertificateException if exists, else null.
     */
    public static UnrecognizedCertificateException getCertificateException(Throwable e) {
        int i = 0; // Just in case there is a getCause loop
        while (e != null && i < 10) {
            if (e instanceof UnrecognizedCertificateException) {
                return (UnrecognizedCertificateException) e;
            }
            e = e.getCause();
            i++;
        }

        return null;
    }
}
