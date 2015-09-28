package org.matrix.androidsdk.rest.model.login;


public class TokenLoginParams extends LoginParams {
    public String user;
    public String token;
    public String txn_id;

    public TokenLoginParams() {
        type = "m.login.token";
    }
}
