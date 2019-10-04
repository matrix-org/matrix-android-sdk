package org.matrix.androidsdk.core.json;
/*
 * Copyright 2019 New Vector Ltd
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.matrix.androidsdk.rest.json.MatrixFieldNamingStrategy;

import java.lang.reflect.Modifier;

public class GsonProvider {

    private static Gson gson = new GsonBuilder()
            .setFieldNamingStrategy(new MatrixFieldNamingStrategy())
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .registerTypeAdapter(boolean.class, new BooleanDeserializer(false))
            .registerTypeAdapter(Boolean.class, new BooleanDeserializer(true))
            .create();

    public static Gson provideGson() {
        return gson;
    }

    // Can serialize/deserialise kt objects without the need to add @JVMField
    private static final Gson kotlinGson = new GsonBuilder()
            .registerTypeAdapter(boolean.class, new BooleanDeserializer(false))
            .registerTypeAdapter(Boolean.class, new BooleanDeserializer(true))
            .create();

    public static Gson provideKotlinGson() {
        return kotlinGson;
    }


}
