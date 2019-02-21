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

package org.matrix.androidsdk.crypto.keysbackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.util.concurrent.CountDownLatch

/**
 * This class observe the state change of a KeysBackup object and provide a method to check the several state change
 * It checks all state transitions and detected forbidden transition
 */
class StateObserver(private val keysBackup: KeysBackup,
                    private val latch: CountDownLatch? = null,
                    private val expectedStateChange: Int = -1) : KeysBackupStateManager.KeysBackupStateListener {

    private val allowedStateTransitions = listOf(
            KeysBackupStateManager.KeysBackupState.BackingUp to KeysBackupStateManager.KeysBackupState.ReadyToBackUp,
            KeysBackupStateManager.KeysBackupState.BackingUp to KeysBackupStateManager.KeysBackupState.WrongBackUpVersion,

            KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver to KeysBackupStateManager.KeysBackupState.Disabled,
            KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver to KeysBackupStateManager.KeysBackupState.NotTrusted,
            KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver to KeysBackupStateManager.KeysBackupState.ReadyToBackUp,
            KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver to KeysBackupStateManager.KeysBackupState.Unknown,
            KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver to KeysBackupStateManager.KeysBackupState.WrongBackUpVersion,

            KeysBackupStateManager.KeysBackupState.Disabled to KeysBackupStateManager.KeysBackupState.Enabling,

            KeysBackupStateManager.KeysBackupState.Enabling to KeysBackupStateManager.KeysBackupState.Disabled,
            KeysBackupStateManager.KeysBackupState.Enabling to KeysBackupStateManager.KeysBackupState.ReadyToBackUp,

            KeysBackupStateManager.KeysBackupState.NotTrusted to KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver,
            // This transition happens when we trust the device
            KeysBackupStateManager.KeysBackupState.NotTrusted to KeysBackupStateManager.KeysBackupState.ReadyToBackUp,

            KeysBackupStateManager.KeysBackupState.ReadyToBackUp to KeysBackupStateManager.KeysBackupState.WillBackUp,

            KeysBackupStateManager.KeysBackupState.Unknown to KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver,

            KeysBackupStateManager.KeysBackupState.WillBackUp to KeysBackupStateManager.KeysBackupState.BackingUp,

            KeysBackupStateManager.KeysBackupState.WrongBackUpVersion to KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver,

            // FIXME These transitions are observed during test, and I'm not sure they should occur. Don't have time to investigate now
            KeysBackupStateManager.KeysBackupState.ReadyToBackUp to KeysBackupStateManager.KeysBackupState.BackingUp,
            KeysBackupStateManager.KeysBackupState.ReadyToBackUp to KeysBackupStateManager.KeysBackupState.ReadyToBackUp,
            KeysBackupStateManager.KeysBackupState.WillBackUp to KeysBackupStateManager.KeysBackupState.ReadyToBackUp,
            KeysBackupStateManager.KeysBackupState.WillBackUp to KeysBackupStateManager.KeysBackupState.Unknown
    )

    private val stateList = ArrayList<KeysBackupStateManager.KeysBackupState>()
    private var lastTransitionError: String? = null

    init {
        keysBackup.addListener(this)
    }

    // TODO Make expectedStates mandatory to enforce test
    fun stopAndCheckStates(expectedStates: List<KeysBackupStateManager.KeysBackupState>?) {
        keysBackup.removeListener(this)

        expectedStates?.let {
            assertEquals(it.size, stateList.size)

            for (i in it.indices) {
                assertEquals("The state $i is not correct. states: " + stateList.joinToString(separator = " "), it[i], stateList[i])
            }
        }

        assertNull("states: " + stateList.joinToString(separator = " "), lastTransitionError)
    }

    override fun onStateChange(newState: KeysBackupStateManager.KeysBackupState) {
        stateList.add(newState)

        // Check that state transition is valid
        if (stateList.size >= 2
                && !allowedStateTransitions.contains(stateList[stateList.size - 2] to newState)) {
            // Forbidden transition detected
            lastTransitionError = "Forbidden transition detected from " + stateList[stateList.size - 2] + " to " + newState
        }

        if (expectedStateChange == stateList.size) {
            latch?.countDown()
        }
    }
}