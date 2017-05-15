/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.rest.client;

import android.os.HandlerThread;
import android.support.annotation.NonNull;

import org.matrix.androidsdk.util.MXOsHandler;

import java.util.concurrent.Executor;

/**
 * MXRestExecutor is a basic thread executor
 */
public class MXRestExecutor implements Executor {
    private HandlerThread mHandlerThread;
    private MXOsHandler mHandler;

    public MXRestExecutor() {
        mHandlerThread = new HandlerThread("MXRestExecutor" + this.hashCode(), Thread.MIN_PRIORITY);
        mHandlerThread.start();
        mHandler = new MXOsHandler(mHandlerThread.getLooper());
    }

    @Override
    public void execute(@NonNull final Runnable r) {
        mHandler.post(r);
    }
    /**
     * Stop any running thread
     */
    public void stop() {
        if (null != mHandlerThread) {
            mHandlerThread.quit();
        }
    }
}
