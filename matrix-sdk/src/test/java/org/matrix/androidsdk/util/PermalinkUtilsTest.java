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

package org.matrix.androidsdk.util;

import android.net.Uri;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PermalinkUtilsTest {

    // supported host list
    private static final List<String> sSupportedVectorHosts = Arrays.asList("vector.im", "riot.im");

    // supported paths list
    private static final List<String> sSupportedVectorLinkPaths = Arrays.asList("/beta/", "/develop/", "/app/", "/staging/");


    @Test
    public void parseUniversalLink_standardCase() {
        Map<String, String> result = testUri("https://matrix.to/#/!GnEEPYXUhoaHbkFBNX:matrix.org/$154089010924835FMJsT:sorunome.de");

        Assert.assertEquals("!GnEEPYXUhoaHbkFBNX:matrix.org", result.get(PermalinkUtils.ULINK_ROOM_ID_OR_ALIAS_KEY));
        Assert.assertEquals("$154089010924835FMJsT:sorunome.de", result.get(PermalinkUtils.ULINK_EVENT_ID_KEY));
        Assert.assertNull(result.get(PermalinkUtils.ULINK_GROUP_ID_KEY));
        Assert.assertNull(result.get(PermalinkUtils.ULINK_MATRIX_USER_ID_KEY));
    }


    private Map<String, String> testUri(String uri) {
        Map<String, String> result = PermalinkUtils.parseUniversalLink(Uri.parse(uri), sSupportedVectorHosts, sSupportedVectorLinkPaths);

        Assert.assertNotNull(result);

        return result;
    }
}
