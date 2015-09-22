package org.matrix.androidsdk.ssl;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Implements a TrustManager that checks Certificates against an explicit list of known
 * fingerprints.
 */
public class PinnedTrustManager implements X509TrustManager {
    private byte[][] mFingerprints;
    private X509TrustManager mDefaultTrustManager;

    /**
     * @param fingerprints An array of SHA256 cert fingerprints
     * @param defaultTrustManager Optional trust manager to fall back on if cert does not match
     *                            any of the fingerprints. Can be null.
     */
    public PinnedTrustManager(byte[][] fingerprints, X509TrustManager defaultTrustManager) {
        mFingerprints = fingerprints;
        mDefaultTrustManager = defaultTrustManager;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String s) throws IncorrectCertificateException {
        try {
            if (mDefaultTrustManager != null) {
                mDefaultTrustManager.checkClientTrusted(
                        chain, s
                );
            }
        } catch (CertificateException e) {
            // If there is an exception we full back to checking fingerprints
        }
        checkTrusted("client", chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String s) throws IncorrectCertificateException {
        try {
            if (mDefaultTrustManager != null) {
                mDefaultTrustManager.checkServerTrusted(
                        chain, s
                );
            }
        } catch (CertificateException e) {
            // If there is an exception we full back to checking fingerprints
        }
        checkTrusted("server", chain);
    }

    private void checkTrusted(String type, X509Certificate[] chain) throws IncorrectCertificateException {
        X509Certificate cert = chain[0];

        final byte[] fingerprint;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA256");
            fingerprint = md.digest(cert.getEncoded());
        } catch(Exception e) {
            throw new RuntimeException(e);
        }

        boolean found = false;
        for (byte[] allowedFingerprint: mFingerprints) {
            if (Arrays.equals(fingerprint, allowedFingerprint)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IncorrectCertificateException(type, cert);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
