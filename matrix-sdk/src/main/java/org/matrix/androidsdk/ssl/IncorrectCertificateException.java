package org.matrix.androidsdk.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


/**
 * Thrown when we are given a certificate that does match the certificate we were told to
 * expect.
 */
public class IncorrectCertificateException extends CertificateException {
    public IncorrectCertificateException(String type, X509Certificate cert) {
        super(type + " certificate with unknown fingerprint: " + cert.getSubjectDN());
    }
}
