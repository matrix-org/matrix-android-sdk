package org.matrix.androidsdk.ssl;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
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
    public static byte[] generateSha256Fingerprint(X509Certificate cert) throws CertificateException {
        return generateFingerprint(cert, "SHA-256");
    }

    /**
     * Generates the SHA-1 fingerprint of the given certificate
     * @param cert
     * @return
     */
    public static byte[] generateSha1Fingerprint(X509Certificate cert) throws CertificateException {
        return generateFingerprint(cert, "SHA-1");
    }

    private static byte[] generateFingerprint(X509Certificate cert, String type) throws CertificateException {
        final byte[] fingerprint;
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance(type);
        } catch(Exception e) {
            // This really *really* shouldn't throw, as java should always have a SHA-256 and SHA-1 impl.
            throw new CertificateException(e);
        }

        fingerprint = md.digest(cert.getEncoded());

        return fingerprint;
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String fingerprintToHexString(byte[] fingerprint) {
        return fingerprintToHexString(fingerprint, ' ');
    }

    public static String fingerprintToHexString(byte[] fingerprint, char sep) {
        char[] hexChars = new char[fingerprint.length * 3];
        for ( int j = 0; j < fingerprint.length; j++ ) {
            int v = fingerprint[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = sep;
        }
        return new String(hexChars, 0, hexChars.length - 1);
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
