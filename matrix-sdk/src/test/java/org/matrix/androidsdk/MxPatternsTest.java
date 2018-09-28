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
public class MxPatternsTest {

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
        assertAllFalse(null);
    }

    @Test
    public void MxPatterns_common_error_empty() {
        assertAllFalse("");
    }

    @Test
    public void MxPatterns_common_error_invalidPrefix() {
        assertAllFalse("a");
        assertAllFalse("1");
        assertAllFalse("test@example.org");
        assertAllFalse("https://www.example.org");
        assertAllFalse("benoit:matrix.org");
    }

    /* ==========================================================================================
     * Common valid cases
     * ========================================================================================== */

    @Test
    public void MxPatterns_common_ok() {
        assertAllTrueWithCorrectPrefix("id:matrix.org");
        assertAllTrueWithCorrectPrefix("id:matrix.org:80");
        assertAllTrueWithCorrectPrefix("id:matrix.org:808");
        assertAllTrueWithCorrectPrefix("id:matrix.org:8080");
        assertAllTrueWithCorrectPrefix("id:matrix.org:65535");
    }

    @Test
    public void MxPatterns_common_ok_dash() {
        assertAllTrueWithCorrectPrefix("id:matrix-new.org");
        assertAllTrueWithCorrectPrefix("id:matrix-new.org:80");
        assertAllTrueWithCorrectPrefix("id:matrix-new.org:808");
        assertAllTrueWithCorrectPrefix("id:matrix-new.org:8080");
        assertAllTrueWithCorrectPrefix("id:matrix-new.org:65535");
    }

    @Test
    public void MxPatterns_common_ok_localhost() {
        assertAllTrueWithCorrectPrefix("id:localhost");
        assertAllTrueWithCorrectPrefix("id:localhost:8080");
        assertAllTrueWithCorrectPrefix("id:localhost:65535");
    }

    @Test
    public void MxPatterns_common_ok_ipAddress() {
        assertAllTrueWithCorrectPrefix("id:1.1.1.1");
        assertAllTrueWithCorrectPrefix("id:1.1.1.1:8080");
        assertAllTrueWithCorrectPrefix("id:1.1.1.1:65535");

        assertAllTrueWithCorrectPrefix("id:888.888.888.888");
        assertAllTrueWithCorrectPrefix("id:888.888.888.888:8080");
        assertAllTrueWithCorrectPrefix("id:888.888.888.888:65535");
    }

    /* ==========================================================================================
     * Common error cases using correct prefix
     * ========================================================================================== */

    @Test
    public void MxPatterns_common_error_with_prefix_invalidChars() {
        assertAllFalseWithCorrectPrefix("idé:matrix.org");
        assertAllFalseWithCorrectPrefix("id :matrix.org");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_empty() {
        assertAllFalseWithCorrectPrefix("");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_noTwoPoint() {
        assertAllFalseWithCorrectPrefix("idmatrix.org");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_noId() {
        assertAllFalseWithCorrectPrefix(":matrix.org");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_noDomain() {
        assertAllFalseWithCorrectPrefix("id:");
    }

    @Test
    public void MxPatterns_common_error_with_prefix_bad_port() {
        assertAllFalseWithCorrectPrefix("id:matrix.org:");
        assertAllFalseWithCorrectPrefix("id:matrix.org:8");
        assertAllFalseWithCorrectPrefix("id:matrix.org:abc");
        assertAllFalseWithCorrectPrefix("id:matrix.org:8080a");
        assertAllFalseWithCorrectPrefix("id:matrix.org:808080");
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

    private void assertAllTrueWithCorrectPrefix(String value) {
        Assert.assertTrue(MXPatterns.isUserId("@" + value));
        Assert.assertTrue(MXPatterns.isRoomId("!" + value));
        Assert.assertTrue(MXPatterns.isRoomAlias("#" + value));
        Assert.assertTrue(MXPatterns.isEventId("$" + value));
        Assert.assertTrue(MXPatterns.isGroupId("+" + value));
    }

    private void assertAllFalseWithCorrectPrefix(String value) {
        assertAllFalse("@" + value);
        assertAllFalse("!" + value);
        assertAllFalse("#" + value);
        assertAllFalse("$" + value);
        assertAllFalse("+" + value);
    }

    private void assertAllFalse(String value) {
        Assert.assertFalse(MXPatterns.isUserId(value));
        Assert.assertFalse(MXPatterns.isRoomId(value));
        Assert.assertFalse(MXPatterns.isRoomAlias(value));
        Assert.assertFalse(MXPatterns.isEventId(value));
        Assert.assertFalse(MXPatterns.isGroupId(value));
    }
}
