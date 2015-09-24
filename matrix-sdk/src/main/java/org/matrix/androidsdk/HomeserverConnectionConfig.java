package org.matrix.androidsdk;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.ssl.Fingerprint;

import java.util.ArrayList;


/**
 * Represents how to connect to a specific Homeserver, may include credentials to use.
 */
public class HomeserverConnectionConfig {
    private Uri mHsUri;
    private ArrayList<Fingerprint> mAllowedFingerprints;
    private Credentials mCredentials;
    private boolean mPin;

    /**
     * @param hsUri The URI to use to connect to the homeserver
     */
    public HomeserverConnectionConfig(Uri hsUri) {
        this(hsUri, null);
    }

    /**
     * @param hsUri The URI to use to connect to the homeserver
     * @param credentials The credentials to use, if needed. Can be null.
     */
    public HomeserverConnectionConfig(Uri hsUri, Credentials credentials) {
        this(hsUri, credentials, new ArrayList<Fingerprint>(), false);
    }

    /**
     * @param hsUri The URI to use to connect to the homeserver
     * @param credentials The credentials to use, if needed. Can be null.
     * @param allowedFingerprints If using SSL, allow server certs that match these fingerprints.
     * @param pin If true only allow certs matching given fingerprints, otherwise fallback to
     *            standard X509 checks.
     */
    public HomeserverConnectionConfig(Uri hsUri, Credentials credentials, ArrayList<Fingerprint> allowedFingerprints, boolean pin) {
        if (hsUri == null || (!"http".equals(hsUri.getScheme()) && !"https".equals(hsUri.getScheme())) ) {
            throw new RuntimeException("Invalid home server URI: "+hsUri);
        }

        this.mHsUri = hsUri;
        this.mAllowedFingerprints = allowedFingerprints;
        this.mPin = pin;
        this.mCredentials = credentials;
    }

    public Uri getHomeserverUri() { return mHsUri; }
    public ArrayList<Fingerprint> getAllowedFingerprints() { return mAllowedFingerprints; }

    public Credentials getCredentials() { return mCredentials; }
    public void setCredentials(Credentials credentials) { this.mCredentials = credentials; }


    /**
     * @return whether we should reject X509 certs that were issued by trusts CAs and only trust
     * certs with matching fingerprints.
     */
    public boolean shouldPin() {
        return mPin;
    }

    @Override
    public String toString() {
        return "HomeserverConnectionConfig{" +
                "mHsUri=" + mHsUri +
                ", mAllowedFingerprints size=" + mAllowedFingerprints.size() +
                ", mCredentials=" + mCredentials +
                ", mPin=" + mPin +
                '}';
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("home_server_url", mHsUri.toString());
        json.put("pin", mPin);
        if (mCredentials != null) json.put("credentials", mCredentials.toJson());
        if (mAllowedFingerprints != null) {
            ArrayList<JSONObject> fingerprints = new ArrayList<JSONObject>(mAllowedFingerprints.size());

            for (Fingerprint fingerprint : mAllowedFingerprints) {
                fingerprints.add(fingerprint.toJson());
            }

            json.put("fingerprints", new JSONArray(fingerprints));
        }

        return json;
    }

    public static HomeserverConnectionConfig fromJson(JSONObject obj) throws JSONException {
        JSONArray fingerprintArray = obj.optJSONArray("fingerprints");
        ArrayList<Fingerprint> fingerprints = new ArrayList<Fingerprint>();
        if (fingerprintArray != null) {
            for (int i = 0; i < fingerprintArray.length(); i++) {
                fingerprints.add(Fingerprint.fromJson(fingerprintArray.getJSONObject(i)));
            }
        }

        JSONObject credentialsObj = obj.optJSONObject("credentials");
        Credentials creds = credentialsObj != null ? Credentials.fromJson(credentialsObj) : null;

        return new HomeserverConnectionConfig(
                Uri.parse(obj.getString("home_server_url")),
                creds,
                fingerprints,
                obj.optBoolean("pin", false)
        );
    }
}
