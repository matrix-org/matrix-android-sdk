package org.matrix.androidsdk.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

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
    public void checkClientTrusted(X509Certificate[] chain, String s) throws CertificateException {
        try {
            if (mDefaultTrustManager != null) {
                mDefaultTrustManager.checkClientTrusted(
                        chain, s
                );
            }
        } catch (CertificateException e) {
            // If there is an exception we fall back to checking fingerprints
            if (mFingerprints == null || mFingerprints.length == 0) {
                byte[] fingerprint = CertUtil.generateSha256Fingerprint(chain[0]);
                throw new UnrecognizedCertificateException(chain[0], fingerprint, e.getCause());
            }
        }
        checkTrusted("client", chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String s) throws CertificateException {
        try {
            if (mDefaultTrustManager != null) {
                mDefaultTrustManager.checkServerTrusted(
                        chain, s
                );
            }
        } catch (CertificateException e) {
            // If there is an exception we fall back to checking fingerprints
            if (mFingerprints == null || mFingerprints.length == 0) {
                byte[] fingerprint = CertUtil.generateSha256Fingerprint(chain[0]);
                throw new UnrecognizedCertificateException(chain[0], fingerprint, e.getCause());
            }
        }
        checkTrusted("server", chain);
    }

    private void checkTrusted(String type, X509Certificate[] chain) throws CertificateException {
        X509Certificate cert = chain[0];

        byte[] fingerprint = CertUtil.generateSha256Fingerprint(cert);

        boolean found = false;
        for (byte[] allowedFingerprint: mFingerprints) {
            if (Arrays.equals(fingerprint, allowedFingerprint)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new UnrecognizedCertificateException(cert, fingerprint, null);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
