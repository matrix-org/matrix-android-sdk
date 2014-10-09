package org.matrix.androidsdk.api.response.login;

import java.util.List;

/**
 * Response to a GET /login call with the different login flows.
 */
public class LoginFlowResponse {
    public List<LoginFlow> flows;
}
