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

package org.matrix.androidsdk.common;

import android.os.Debug;

public class TestConstants {

    public static final String TESTS_HOME_SERVER_URL = "http://10.0.2.2:8080";

    // Time out to use when waiting for server response. 60s
    private static final int AWAIT_TIME_OUT_MILLIS = 60_000;

    // Time out to use when waiting for server response, when the debugger is connected. 10 minutes
    private static final int AWAIT_TIME_OUT_WITH_DEBUGGER_MILLIS = 10 * 60_000;

    public static final String USER_ALICE = "Alice";
    public static final String USER_BOB = "Bob";
    public static final String USER_SAM = "Sam";

    public static final String PASSWORD = "password";

    public static long getTimeOutMillis() {
        if (Debug.isDebuggerConnected()) {
            // Wait more
            return AWAIT_TIME_OUT_WITH_DEBUGGER_MILLIS;
        } else {
            return AWAIT_TIME_OUT_MILLIS;
        }
    }
}