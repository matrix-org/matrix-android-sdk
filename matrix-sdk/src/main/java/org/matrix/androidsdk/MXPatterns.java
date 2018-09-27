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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class contains pattern to match the different Matrix ids
 * TODO Add examples of values
 */
public class MXPatterns {

    private MXPatterns() {
        // Cannot be instantiated
    }

    // regex pattern to find matrix user ids in a string.
    // See https://matrix.org/speculator/spec/HEAD/appendices.html#historical-user-ids
    private static final String MATRIX_USER_IDENTIFIER_REGEX = "@[A-Z0-9\\x21-\\x39\\x3B-\\x7F]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER = Pattern.compile(MATRIX_USER_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find room ids in a string.
    private static final String MATRIX_ROOM_IDENTIFIER_REGEX = "![A-Z0-9]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER = Pattern.compile(MATRIX_ROOM_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find room aliases in a string.
    private static final String MATRIX_ROOM_ALIAS_REGEX = "#[A-Z0-9._%#@=+-]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_ALIAS = Pattern.compile(MATRIX_ROOM_ALIAS_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find message ids in a string.
    private static final String MATRIX_MESSAGE_IDENTIFIER_REGEX = "\\$[A-Z0-9]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER = Pattern.compile(MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find group ids in a string.
    private static final String MATRIX_GROUP_IDENTIFIER_REGEX = "\\+[A-Z0-9=_\\-./]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER = Pattern.compile(MATRIX_GROUP_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find permalink with message id.
    // Android does not support in URL so extract it.
    public static final Pattern PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ID
            = Pattern.compile("https:\\/\\/matrix\\.to\\/#\\/" + MATRIX_ROOM_IDENTIFIER_REGEX + "\\/"
            + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ALIAS
            = Pattern.compile("https:\\/\\/matrix\\.to\\/#\\/" + MATRIX_ROOM_ALIAS_REGEX + "\\/"
            + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    public static final Pattern PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ID
            = Pattern.compile("https:\\/\\/[A-Z0-9.-]+\\.[A-Z]{2,}\\/[A-Z]{3,}\\/#\\/room\\/" + MATRIX_ROOM_IDENTIFIER_REGEX + "\\/"
            + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ALIAS =
            Pattern.compile("https:\\/\\/[A-Z0-9.-]+\\.[A-Z]{2,}\\/[A-Z]{3,}\\/#\\/room\\/" + MATRIX_ROOM_ALIAS_REGEX + "\\/"
                    + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // list of patterns to find some matrix item.
    public static final List<Pattern> MATRIX_PATTERNS = Arrays.asList(
            MXPatterns.PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ID,
            MXPatterns.PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ALIAS,
            MXPatterns.PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ID,
            MXPatterns.PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ALIAS,
            MXPatterns.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER,
            MXPatterns.PATTERN_CONTAIN_MATRIX_ALIAS,
            MXPatterns.PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER,
            MXPatterns.PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER,
            MXPatterns.PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER
    );

    /**
     * Tells if a string is a valid user Id.
     *
     * @param anUserId the string to test
     * @return true if the string is a valid user id
     */
    public static boolean isUserId(String anUserId) {
        return anUserId != null && PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER.matcher(anUserId).matches();
    }

    /**
     * Tells if a string is a valid room id.
     *
     * @param aRoomId the string to test
     * @return true if the string is a valid room Id
     */
    public static boolean isRoomId(String aRoomId) {
        return aRoomId != null && PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER.matcher(aRoomId).matches();
    }

    /**
     * Tells if a string is a valid room alias.
     *
     * @param aRoomAlias the string to test
     * @return true if the string is a valid room alias.
     */
    public static boolean isRoomAlias(String aRoomAlias) {
        return aRoomAlias != null && PATTERN_CONTAIN_MATRIX_ALIAS.matcher(aRoomAlias).matches();
    }

    /**
     * Tells if a string is a valid message id.
     *
     * @param aMessageId the string to test
     * @return true if the string is a valid message id.
     */
    public static boolean isMessageId(String aMessageId) {
        return aMessageId != null && PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER.matcher(aMessageId).matches();
    }

    /**
     * Tells if a string is a valid group id.
     *
     * @param aGroupId the string to test
     * @return true if the string is a valid message id.
     */
    public static boolean isGroupId(String aGroupId) {
        return aGroupId != null && PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER.matcher(aGroupId).matches();
    }
}
