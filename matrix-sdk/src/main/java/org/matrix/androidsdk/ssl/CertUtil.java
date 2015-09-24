package org.matrix.androidsdk.ssl;

import org.matrix.androidsdk.HomeserverConnectionConfig;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

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

    public static SSLSocketFactory newPinnedSSLSocketFactory(HomeserverConnectionConfig hsConfig) {
        try {
            X509TrustManager defaultTrustManager = null;

            // If we haven't specified that we wanted to pin the certs, fallback to standard
            // X509 checks if fingerprints don't match.
            if (!hsConfig.shouldPin()) {
                TrustManagerFactory tf = TrustManagerFactory.getInstance("PKIX");
                tf.init((KeyStore) null);
                TrustManager[] trustManagers = tf.getTrustManagers();

                for (int i = 0; i < trustManagers.length; i++) {
                    if (trustManagers[i] instanceof X509TrustManager) {
                        defaultTrustManager = (X509TrustManager) trustManagers[i];
                        break;
                    }
                }
            }

            TrustManager[] trustPinned = new TrustManager[]{
                    new PinnedTrustManager(hsConfig.getAllowedFingerprints(), defaultTrustManager)
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustPinned, new java.security.SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            return sslSocketFactory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static HostnameVerifier newHostnameVerifier(HomeserverConnectionConfig hsConfig) {
        final HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        final List<Fingerprint> trusted_fingerprints = hsConfig.getAllowedFingerprints();

        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                if (defaultVerifier.verify(hostname, session)) return true;
                if (trusted_fingerprints == null || trusted_fingerprints.size() == 0) return false;

                // If remote cert matches an allowed fingerprint, just accept it.
                try {
                    boolean found = false;
                    for (Certificate cert : session.getPeerCertificates()) {
                        for (Fingerprint allowedFingerprint : trusted_fingerprints) {
                            if (allowedFingerprint != null && cert instanceof X509Certificate && allowedFingerprint.matchesCert((X509Certificate) cert)) {
                                return true;
                            }
                        }
                    }
                } catch (SSLPeerUnverifiedException e) {
                    return false;
                } catch (CertificateException e) {
                    return false;
                }

                return false;
            }
        };
    }
}
