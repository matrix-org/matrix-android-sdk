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

package org.matrix.androidsdk.core;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.RequestBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public final class PolymorphicRequestBodyConverter<T> implements Converter<T, RequestBody> {
    public static final Factory FACTORY = new Factory() {
        @Override
        public Converter<?, RequestBody> requestBodyConverter(
                Type type,
                Annotation[] parameterAnnotations,
                Annotation[] methodAnnotations,
                Retrofit retrofit
        ) {
            return new PolymorphicRequestBodyConverter<>(
                    this, parameterAnnotations, methodAnnotations, retrofit
            );
        }
    };

    private final Factory skipPast;
    private final Annotation[] parameterAnnotations;
    private final Annotation[] methodsAnnotations;
    private final Retrofit retrofit;
    private final Map<Class<?>, Converter<T, RequestBody>> cache = new LinkedHashMap<>();

    PolymorphicRequestBodyConverter(
            Factory skipPast,
            Annotation[] parameterAnnotations,
            Annotation[] methodsAnnotations,
            Retrofit retrofit) {
        this.skipPast = skipPast;
        this.parameterAnnotations = parameterAnnotations;
        this.methodsAnnotations = methodsAnnotations;
        this.retrofit = retrofit;
    }

    @Override
    public RequestBody convert(T value) throws IOException {
        Class<?> cls = value.getClass();
        Converter<T, RequestBody> requestBodyConverter;
        synchronized (cache) {
            requestBodyConverter = cache.get(cls);
        }
        if (requestBodyConverter == null) {
            requestBodyConverter = retrofit.nextRequestBodyConverter(
                    skipPast, cls, parameterAnnotations, methodsAnnotations
            );
            synchronized (cache) {
                cache.put(cls, requestBodyConverter);
            }
        }
        return requestBodyConverter.convert(value);
    }
}
