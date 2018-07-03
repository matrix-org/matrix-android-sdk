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

import android.os.Build;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class CompatUtil {

    /**
     * Create a GZIPOutputStream instance
     * Special treatment on KitKat device, force the syncFlush param to false
     * Before Kitkat, this param does not exist and after Kitkat it is set to false by default
     *
     * @param fileOutputStream the output stream
     */
    public static GZIPOutputStream createGzipOutputStream(FileOutputStream fileOutputStream) throws IOException {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            return new GZIPOutputStream(fileOutputStream, false);
        } else {
            return new GZIPOutputStream(fileOutputStream);
        }
    }
}
