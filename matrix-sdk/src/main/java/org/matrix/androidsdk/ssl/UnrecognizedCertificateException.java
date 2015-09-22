package org.matrix.androidsdk.ssl;

import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


/**
 * Thrown when we are given a certificate that does match the certificate we were told to
 * expect.
 */
public class UnrecognizedCertificateException extends CertificateException {
    private X509Certificate mCert;
    private Fingerprint mFingerprint;

    public UnrecognizedCertificateException(X509Certificate cert, Fingerprint fingerprint, Throwable cause) {
        super("Unrecognized certificate with unknown fingerprint: " + cert.getSubjectDN(), cause);
        mCert = cert;
        mFingerprint = fingerprint;
    }

    public X509Certificate getCertificate() {
        return mCert;
    }

    public Fingerprint getFingerprint() {
        return mFingerprint;
    }
}
