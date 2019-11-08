/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.rest.model.sync;

import java.io.Serializable;
import java.util.Map;

// It's a LightEvent
public class AccountDataElement implements Serializable {
    /**
     * Account data known possible values for {@link #type}
     */
    public static final String ACCOUNT_DATA_TYPE_IGNORED_USER_LIST = "m.ignored_user_list";
    public static final String ACCOUNT_DATA_TYPE_DIRECT_MESSAGES = "m.direct";
    public static final String ACCOUNT_DATA_TYPE_PREVIEW_URLS = "org.matrix.preview_urls";
    public static final String ACCOUNT_DATA_TYPE_WIDGETS = "m.widgets";
    public static final String ACCOUNT_DATA_TYPE_PUSH_RULES = "m.push_rules";
    public static final String ACCOUNT_DATA_TYPE_ACCEPTED_TERMS = "m.accepted_terms";
    public static final String ACCOUNT_DATA_TYPE_IDENTITY_SERVER = "m.identity_server";
    public static final String ACCOUNT_DATA_TYPE_INTEGRATION_PROVISIONING = "im.vector.setting.integration_provisioning";
    public static final String ACCOUNT_DATA_TYPE_ALLOWED_WIDGETS = "im.vector.setting.allowed_widgets";

    /**
     * Account data known possible values for key in {@link #content}
     */
    public static final String ACCOUNT_DATA_KEY_IGNORED_USERS = "ignored_users";
    public static final String ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE = "disable";
    public static final String ACCOUNT_DATA_KEY_ACCEPTED_TERMS = "accepted";
    public static final String ACCOUNT_DATA_KEY_IDENTITY_SERVER_BASE_URL = "base_url";

    // Type of account data element
    public String type;

    // Content of account data element
    public Map<String, Object> content;
}
