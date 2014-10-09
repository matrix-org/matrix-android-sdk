package org.matrix.androidsdk.api.response.login;

/**
 * Object to pass to a /login call of type password.
 */
public class PasswordLoginParams extends LoginParams {
    public String user;
    public String password;

    public PasswordLoginParams() {
        type = "m.login.password";
    }
}
