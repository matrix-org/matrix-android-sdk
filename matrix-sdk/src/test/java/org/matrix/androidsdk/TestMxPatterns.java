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

package org.matrix.androidsdk;

import junit.framework.Assert;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Arrays;
import java.util.List;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestMxPatterns {

    private static final List<String> validUserIds = Arrays.asList(
            "@benoit:matrix.org",
            "@benoit:matrix.org:1234"
    );

    private static final List<String> validRoomIds = Arrays.asList(
            "!fLXbhWnqiSoyBfgGgb:matrix.org",
            "!fLXbhWnqiSoyBfgGgb:matrix.org:1234"
    );

    private static final List<String> validRoomAliasIds = Arrays.asList(
            "#linux:matrix.org",
            "#linux:matrix.org:1234",
            // Room alias can contain special char: ._%#@=+-
            "#linux._%#@=+-:matrix.org:1234"
    );

    private static final List<String> validEventIds = Arrays.asList(
            "$1536732077213115wbNdt:matrix.org",
            "$1536732077213115wbNdt:matrix.org:1234"
    );

    private static final List<String> validGroupIds = Arrays.asList(
            "+matrix:matrix.org",
            "+matrix:matrix.org:1234",
            // Group id special char
            "+matrix=_-./:matrix.org:1234"

    );

    /* ==========================================================================================
     * Common error cases
     * ========================================================================================== */

    @Test
    public void MxPatterns_common_error_null() {
        testAllFalse(null);
    }

    @Test
    public void MxPatterns_common_error_empty() {
        testAllFalse("");
    }

    @Test
    public void MxPatterns_common_error_invalidPrefix() {
        testAllFalse("a");
        testAllFalse("1");
        testAllFalse("test@example.org");
        testAllFalse("https://www.example.org");
        testAllFalse("benoit:matrix.org");
    }

    /* ==========================================================================================
     * Common valid cases
     * ========================================================================================== */

    @Test
    public void MxPatterns_common_ok() {
        testAllTrueWithCorrectPrefix("id:matrix.org");
        testAllTrueWithCorrectPrefix("id:matrix.org:80");
        testAllTrueWithCorrectPrefix("id:matrix.org:808");
        testAllTrueWithCorrectPrefix("id:matrix.org:8080");
        testAllTrueWithCorrectPrefix("id:matrix.org:65535");
    }

    @Test
    public void MxPatterns_common_ok_dash() {
        testAllTrueWithCorrectPrefix("id:matrix-new.org");
        testAllTrueWithCorrectPrefix("id:matrix-new.org:80");
        testAllTrueWithCorrectPrefix("id:matrix-new.org:808");
        testAllTrueWithCorrectPrefix("id:matrix-new.org:8080");
        testAllTrueWithCorrectPrefix("id:matrix-new.org:65535");
    }

    @Test
    public void MxPatterns_common_ok_localhost() {
        testAllTrueWithCorrectPrefix("id:localhost");
        testAllTrueWithCorrectPrefix("id:localhost:8080");
        testAllTrueWithCorrectPrefix("id:localhost:65535");
    }

    @Test
    public void MxPatterns_common_ok_ipAddress() {
        testAllTrueWithCorrectPrefix("id:1.1.1.1");
        testAllTrueWithCorrectPrefix("id:1.1.1.1:8080");
        testAllTrueWithCorrectPrefix("id:888.888.888.888");
        testAllTrueWithCorrectPrefix("id:888.888.888.888:8080");
        testAllTrueWithCorrectPrefix("id:888.888.888.888:65535");
    }

    /* ==========================================================================================
     * Common error cases using correct prefix
     * ========================================================================================== */

    @Test
    public void MxPatterns_common_error_with_prefix_invalidChars() {
        testAllFalseWithCorrectPrefix("idé:matrix.org");
        testAllFalseWithCorrectPrefix("id :matrix.org");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_empty() {
        testAllFalseWithCorrectPrefix("");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_noTwoPoint() {
        testAllFalseWithCorrectPrefix("idmatrix.org");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_noId() {
        testAllFalseWithCorrectPrefix(":matrix.org");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_noDomain() {
        testAllFalseWithCorrectPrefix("id:");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_bad_port() {
        testAllFalseWithCorrectPrefix("id:matrix.org:");
        testAllFalseWithCorrectPrefix("id:matrix.org:8");
        testAllFalseWithCorrectPrefix("id:matrix.org:abc");
        testAllFalseWithCorrectPrefix("id:matrix.org:8080a");
        testAllFalseWithCorrectPrefix("id:matrix.org:808080");
    }

    /* ==========================================================================================
     * Valid cases
     * ========================================================================================== */

    @Test
    public void MxPatterns_userId_ok() {
        for (String value : validUserIds) {
            Assert.assertTrue(MXPatterns.isUserId(value));

            Assert.assertFalse(MXPatterns.isRoomId(value));
            Assert.assertFalse(MXPatterns.isRoomAlias(value));
            Assert.assertFalse(MXPatterns.isEventId(value));
            Assert.assertFalse(MXPatterns.isGroupId(value));
        }
    }

    @Test
    public void MxPatterns_roomId_ok() {
        for (String value : validRoomIds) {
            Assert.assertFalse(MXPatterns.isUserId(value));

            Assert.assertTrue(MXPatterns.isRoomId(value));

            Assert.assertFalse(MXPatterns.isRoomAlias(value));
            Assert.assertFalse(MXPatterns.isEventId(value));
            Assert.assertFalse(MXPatterns.isGroupId(value));
        }
    }

    @Test
    public void MxPatterns_roomAlias_ok() {
        for (String value : validRoomAliasIds) {
            Assert.assertFalse(MXPatterns.isUserId(value));
            Assert.assertFalse(MXPatterns.isRoomId(value));

            Assert.assertTrue(MXPatterns.isRoomAlias(value));

            Assert.assertFalse(MXPatterns.isEventId(value));
            Assert.assertFalse(MXPatterns.isGroupId(value));
        }
    }

    @Test
    public void MxPatterns_eventId_ok() {
        for (String value : validEventIds) {
            Assert.assertFalse(MXPatterns.isUserId(value));
            Assert.assertFalse(MXPatterns.isRoomId(value));
            Assert.assertFalse(MXPatterns.isRoomAlias(value));

            Assert.assertTrue(MXPatterns.isEventId(value));

            Assert.assertFalse(MXPatterns.isGroupId(value));
        }
    }

    @Test
    public void MxPatterns_groupId_ok() {
        for (String value : validGroupIds) {
            Assert.assertFalse(MXPatterns.isUserId(value));
            Assert.assertFalse(MXPatterns.isRoomId(value));
            Assert.assertFalse(MXPatterns.isRoomAlias(value));
            Assert.assertFalse(MXPatterns.isEventId(value));

            Assert.assertTrue(MXPatterns.isGroupId(value));
        }
    }

    /* ==========================================================================================
     * Private methods
     * ========================================================================================== */

    private void testAllTrueWithCorrectPrefix(String value) {
        Assert.assertTrue(MXPatterns.isUserId("@" + value));
        Assert.assertTrue(MXPatterns.isRoomId("!" + value));
        Assert.assertTrue(MXPatterns.isRoomAlias("#" + value));
        Assert.assertTrue(MXPatterns.isEventId("$" + value));
        Assert.assertTrue(MXPatterns.isGroupId("+" + value));
    }

    private void testAllFalseWithCorrectPrefix(String value) {
        testAllFalse("@" + value);
        testAllFalse("!" + value);
        testAllFalse("#" + value);
        testAllFalse("$" + value);
        testAllFalse("+" + value);
    }

    private void testAllFalse(String value) {
        Assert.assertFalse(MXPatterns.isUserId(value));
        Assert.assertFalse(MXPatterns.isRoomId(value));
        Assert.assertFalse(MXPatterns.isRoomAlias(value));
        Assert.assertFalse(MXPatterns.isEventId(value));
        Assert.assertFalse(MXPatterns.isGroupId(value));
    }
}
