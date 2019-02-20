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

package org.matrix.androidsdk.crypto.keysbackup

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.common.*
import org.matrix.androidsdk.crypto.MXCRYPTO_ALGORITHM_MEGOLM_BACKUP
import org.matrix.androidsdk.crypto.MegolmSessionData
import org.matrix.androidsdk.crypto.OutgoingRoomKeyRequest
import org.matrix.androidsdk.crypto.data.ImportRoomKeysResult
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession2
import org.matrix.androidsdk.listeners.ProgressListener
import org.matrix.androidsdk.rest.callback.SuccessCallback
import org.matrix.androidsdk.rest.callback.SuccessErrorCallback
import org.matrix.androidsdk.rest.model.keys.CreateKeysBackupVersionBody
import org.matrix.androidsdk.rest.model.keys.KeysVersion
import org.matrix.androidsdk.rest.model.keys.KeysVersionResult
import org.matrix.androidsdk.util.JsonUtils
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KeysBackupTest {

    private val mTestHelper = CommonTestHelper()
    private val mCryptoTestHelper = CryptoTestHelper(mTestHelper)

    private val defaultSessionParams = SessionTestParams(
            withInitialSync = false,
            withCryptoEnabled = true,
            withLazyLoading = true,
            withLegacyCryptoStore = false)
    private val defaultSessionParamsWithInitialSync = SessionTestParams(
            withInitialSync = true,
            withCryptoEnabled = true,
            withLazyLoading = true,
            withLegacyCryptoStore = false)

    /**
     * - From doE2ETestWithAliceAndBobInARoomWithEncryptedMessages, we should have no backed up keys
     * - Check backup keys after having marked one as backed up
     * - Reset keys backup markers
     */
    @Test
    fun roomKeysTest_testBackupStore_ok() {
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val store = cryptoTestData.firstSession.crypto!!.cryptoStore

        // From doE2ETestWithAliceAndBobInARoomWithEncryptedMessages, we should have no backed up keys
        val sessions = store.inboundGroupSessionsToBackup(100)
        val sessionsCount = sessions.size

        assertFalse(sessions.isEmpty())
        assertEquals(sessionsCount, store.inboundGroupSessionsCount(false))
        assertEquals(0, store.inboundGroupSessionsCount(true))

        // - Check backup keys after having marked one as backed up
        val session = sessions[0]

        store.markBackupDoneForInboundGroupSessionWithId(session.mSession.sessionIdentifier(), session.mSenderKey)

        assertEquals(sessionsCount, store.inboundGroupSessionsCount(false))
        assertEquals(1, store.inboundGroupSessionsCount(true))

        val sessions2 = store.inboundGroupSessionsToBackup(100)
        assertEquals(sessionsCount - 1, sessions2.size)

        // - Reset keys backup markers
        store.resetBackupMarkers()

        val sessions3 = store.inboundGroupSessionsToBackup(100)
        assertEquals(sessionsCount, sessions3.size)
        assertEquals(sessionsCount, store.inboundGroupSessionsCount(false))
        assertEquals(0, store.inboundGroupSessionsCount(true))
    }

    /**
     * Check that prepareKeysBackupVersionWithPassword returns valid data
     */
    @Test
    fun prepareKeysBackupVersionTest() {
        val bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams)

        bobSession.enableCrypto(mTestHelper)

        assertNotNull(bobSession.crypto)
        assertNotNull(bobSession.crypto!!.keysBackup)

        val keysBackup = bobSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        assertFalse(keysBackup.isEnabled)

        val latch = CountDownLatch(1)

        keysBackup.prepareKeysBackupVersion(null, null, object : SuccessErrorCallback<MegolmBackupCreationInfo> {
            override fun onSuccess(info: MegolmBackupCreationInfo?) {
                assertNotNull(info)

                assertEquals(MXCRYPTO_ALGORITHM_MEGOLM_BACKUP, info!!.algorithm)
                assertNotNull(info.authData)
                assertNotNull(info.authData!!.publicKey)
                assertNotNull(info.authData!!.signatures)
                assertNotNull(info.recoveryKey)

                latch.countDown()
            }

            override fun onUnexpectedError(e: Exception?) {
                fail(e?.localizedMessage)

                latch.countDown()
            }
        })
        latch.await()

        stateObserver.stopAndCheckStates(null)
        bobSession.clear(InstrumentationRegistry.getContext())
    }

    /**
     * Test creating a keys backup version and check that createKeysBackupVersion() returns valid data
     */
    @Test
    fun createKeysBackupVersionTest() {
        val bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams)
        bobSession.enableCrypto(mTestHelper)

        val keysBackup = bobSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        assertFalse(keysBackup.isEnabled)

        var megolmBackupCreationInfo: MegolmBackupCreationInfo? = null
        val latch = CountDownLatch(1)
        keysBackup.prepareKeysBackupVersion(null, null, object : SuccessErrorCallback<MegolmBackupCreationInfo> {

            override fun onSuccess(info: MegolmBackupCreationInfo) {
                megolmBackupCreationInfo = info

                latch.countDown()
            }

            override fun onUnexpectedError(e: Exception) {
                fail(e.localizedMessage)

                latch.countDown()
            }
        })
        latch.await()

        assertNotNull(megolmBackupCreationInfo)

        assertFalse(keysBackup.isEnabled)

        val latch2 = CountDownLatch(1)

        // Create the version
        keysBackup.createKeysBackupVersion(megolmBackupCreationInfo!!, object : TestApiCallback<KeysVersion>(latch2) {
            override fun onSuccess(info: KeysVersion) {
                assertNotNull(info)
                assertNotNull(info.version)

                super.onSuccess(info)
            }
        })
        mTestHelper.await(latch2)

        // Backup must be enable now
        assertTrue(keysBackup.isEnabled)

        stateObserver.stopAndCheckStates(null)
        bobSession.clear(InstrumentationRegistry.getContext())
    }

    /**
     * - Check that createKeysBackupVersion() launches the backup
     * - Check the backup completes
     */
    @Test
    fun backupAfterCreateKeysBackupVersionTest() {
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val cryptoStore = cryptoTestData.firstSession.crypto!!.cryptoStore
        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val latch = CountDownLatch(1)

        assertEquals(2, cryptoStore.inboundGroupSessionsCount(false))
        assertEquals(0, cryptoStore.inboundGroupSessionsCount(true))

        val stateObserver = StateObserver(keysBackup, latch, 5)

        prepareAndCreateKeysBackupData(keysBackup)

        mTestHelper.await(latch)


        val nbOfKeys = cryptoStore.inboundGroupSessionsCount(false)
        val backedUpKeys = cryptoStore.inboundGroupSessionsCount(true)

        assertEquals(2, nbOfKeys)
        assertEquals("All keys must have been marked as backed up", nbOfKeys, backedUpKeys)

        // Check the several backup state changes
        stateObserver.stopAndCheckStates(
                listOf(
                        KeysBackupStateManager.KeysBackupState.Enabling,
                        KeysBackupStateManager.KeysBackupState.ReadyToBackUp,
                        KeysBackupStateManager.KeysBackupState.WillBackUp,
                        KeysBackupStateManager.KeysBackupState.BackingUp,
                        KeysBackupStateManager.KeysBackupState.ReadyToBackUp
                )
        )
        cryptoTestData.clear(context)
    }


    /**
     * Check that backupAllGroupSessions() returns valid data
     */
    @Test
    fun backupAllGroupSessionsTest() {
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val cryptoStore = cryptoTestData.firstSession.crypto!!.cryptoStore
        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        prepareAndCreateKeysBackupData(keysBackup)

        // Check that backupAllGroupSessions returns valid data
        val nbOfKeys = cryptoStore.inboundGroupSessionsCount(false)

        assertEquals(2, nbOfKeys)

        val latch = CountDownLatch(1)

        var lastBackedUpKeysProgress = 0

        keysBackup.backupAllGroupSessions(object : ProgressListener {
            override fun onProgress(progress: Int, total: Int) {
                assertEquals(nbOfKeys, total)
                lastBackedUpKeysProgress = progress
            }

        }, TestApiCallback(latch))

        mTestHelper.await(latch)
        assertEquals(nbOfKeys, lastBackedUpKeysProgress)

        val backedUpKeys = cryptoStore.inboundGroupSessionsCount(true)

        assertEquals("All keys must have been marked as backed up", nbOfKeys, backedUpKeys)

        stateObserver.stopAndCheckStates(null)
        cryptoTestData.clear(context)
    }

    /**
     * Check encryption and decryption of megolm keys in the backup.
     * - Pick a megolm key
     * - Check [MXKeyBackup encryptGroupSession] returns stg
     * - Check [MXKeyBackup pkDecryptionFromRecoveryKey] is able to create a OLMPkDecryption
     * - Check [MXKeyBackup decryptKeyBackupData] returns stg
     * - Compare the decrypted megolm key with the original one
     */
    @Test
    fun testEncryptAndDecryptKeysBackupData() {
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val cryptoStore = cryptoTestData.firstSession.crypto!!.cryptoStore
        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        // - Pick a megolm key
        val session = cryptoStore.inboundGroupSessionsToBackup(1)[0]

        val keyBackupCreationInfo = prepareAndCreateKeysBackupData(keysBackup).megolmBackupCreationInfo

        // - Check encryptGroupSession() returns stg
        val keyBackupData = keysBackup.encryptGroupSession(session)
        assertNotNull(keyBackupData)
        assertNotNull(keyBackupData.sessionData)

        // - Check pkDecryptionFromRecoveryKey() is able to create a OlmPkDecryption
        val decryption = keysBackup.pkDecryptionFromRecoveryKey(keyBackupCreationInfo.recoveryKey)
        assertNotNull(decryption)
        // - Check decryptKeyBackupData() returns stg
        val sessionData = keysBackup.decryptKeyBackupData(keyBackupData, session.mSession.sessionIdentifier(), cryptoTestData.roomId, decryption!!)
        assertNotNull(sessionData)
        // - Compare the decrypted megolm key with the original one
        assertKeysEquals(session.exportKeys(), sessionData)

        stateObserver.stopAndCheckStates(null)
        cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a recovery key
     * - Log Alice on a new device
     * - Restore the e2e backup from the homeserver with the recovery key
     * - Restore must be successful
     */
    @Test
    fun restoreKeysBackupTest() {
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(null)

        // - Restore the e2e backup from the homeserver
        val latch2 = CountDownLatch(1)
        var importRoomKeysResult: ImportRoomKeysResult? = null
        testData.aliceSession2.crypto!!.keysBackup.restoreKeysWithRecoveryKey(testData.prepareKeysBackupDataResult.version,
                testData.prepareKeysBackupDataResult.megolmBackupCreationInfo.recoveryKey,
                null,
                null,
                object : TestApiCallback<ImportRoomKeysResult>(latch2) {
                    override fun onSuccess(info: ImportRoomKeysResult) {
                        importRoomKeysResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        checkRestoreSuccess(testData, importRoomKeysResult!!.totalNumberOfKeys, importRoomKeysResult!!.successfullyNumberOfImportedKeys)

        testData.cryptoTestData.clear(context)
    }

    /**
     *
     * This is the same as `testRestoreKeyBackup` but this test checks that pending key
     * share requests are cancelled.
     *
     * - Do an e2e backup to the homeserver with a recovery key
     * - Log Alice on a new device
     * - *** Check the SDK sent key share requests
     * - Restore the e2e backup from the homeserver with the recovery key
     * - Restore must be successful
     * - *** There must be no more pending key share requests
     */
    @Test
    fun restoreKeysBackupAndKeyShareRequestTest() {
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(null)


        // - Check the SDK sent key share requests
        val unsentRequest = testData.aliceSession2.crypto?.cryptoStore
                ?.getOutgoingRoomKeyRequestByState(setOf(OutgoingRoomKeyRequest.RequestState.UNSENT))
        val sentRequest = testData.aliceSession2.crypto?.cryptoStore
                ?.getOutgoingRoomKeyRequestByState(setOf(OutgoingRoomKeyRequest.RequestState.SENT))

        // Request is either sent or unsent
        assertTrue(unsentRequest != null || sentRequest != null)

        // - Restore the e2e backup from the homeserver
        val latch2 = CountDownLatch(1)
        var importRoomKeysResult: ImportRoomKeysResult? = null
        testData.aliceSession2.crypto!!.keysBackup.restoreKeysWithRecoveryKey(testData.prepareKeysBackupDataResult.version,
                testData.prepareKeysBackupDataResult.megolmBackupCreationInfo.recoveryKey,
                null,
                null,
                object : TestApiCallback<ImportRoomKeysResult>(latch2) {
                    override fun onSuccess(info: ImportRoomKeysResult) {
                        importRoomKeysResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        checkRestoreSuccess(testData, importRoomKeysResult!!.totalNumberOfKeys, importRoomKeysResult!!.successfullyNumberOfImportedKeys)

        // - There must be no more pending key share requests
        val unsentRequestAfterRestoration = testData.aliceSession2.crypto?.cryptoStore
                ?.getOutgoingRoomKeyRequestByState(setOf(OutgoingRoomKeyRequest.RequestState.UNSENT))
        val sentRequestAfterRestoration = testData.aliceSession2.crypto?.cryptoStore
                ?.getOutgoingRoomKeyRequestByState(setOf(OutgoingRoomKeyRequest.RequestState.SENT))

        // Request is either sent or unsent
        assertTrue(unsentRequestAfterRestoration == null && sentRequestAfterRestoration == null)

        testData.cryptoTestData.clear(context)
    }


    /**
     * - Do an e2e backup to the homeserver with a recovery key
     * - And log Alice on a new device
     * - The new device must see the previous backup as not trusted
     * - Trust the backup from the new device
     * - Backup must be enabled on the new device
     * - Retrieve the last version from the server
     * - It must be the same
     * - It must be trusted and must have with 2 signatures now
     */
    @Test
    fun trustKeyBackupVersionTest() {
        // - Do an e2e backup to the homeserver with a recovery key
        // - And log Alice on a new device
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(null, true)

        val stateObserver = StateObserver(testData.aliceSession2.crypto!!.keysBackup)

        // Wait for backup state to be NotTrusted
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.NotTrusted)

        // - The new device must see the previous backup as not trusted
        assertNotNull(testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion)
        assertFalse(testData.aliceSession2.crypto!!.keysBackup.isEnabled)
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, testData.aliceSession2.crypto!!.keysBackup.state)

        // - Trust the backup from the new device
        val latch = CountDownLatch(1)
        testData.aliceSession2.crypto!!.keysBackup.trustKeysBackupVersion(
                testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion!!,
                true,
                TestApiCallback(latch)
        )
        mTestHelper.await(latch)

        // Wait for backup state to be ReadyToBackUp
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.ReadyToBackUp)

        // - Backup must be enabled on the new device, on the same version
        assertEquals(testData.prepareKeysBackupDataResult.version, testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion?.version)
        assertTrue(testData.aliceSession2.crypto!!.keysBackup.isEnabled)

        // - Retrieve the last version from the server
        val latch2 = CountDownLatch(1)
        var keysVersionResult: KeysVersionResult? = null
        testData.aliceSession2.crypto!!.keysBackup.getCurrentVersion(
                object : TestApiCallback<KeysVersionResult?>(latch2) {
                    override fun onSuccess(info: KeysVersionResult?) {
                        keysVersionResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        // - It must be the same
        assertEquals(testData.prepareKeysBackupDataResult.version, keysVersionResult!!.version)

        val latch3 = CountDownLatch(1)
        var keysBackupVersionTrust: KeysBackupVersionTrust? = null
        testData.aliceSession2.crypto!!.keysBackup.getKeysBackupTrust(keysVersionResult!!,
                object : TestApiCallback<KeysBackupVersionTrust>(latch3) {
                    override fun onSuccess(info: KeysBackupVersionTrust) {
                        keysBackupVersionTrust = info
                        super.onSuccess(info)
                    }
                })
        mTestHelper.await(latch3)

        // - It must be trusted and must have 2 signatures now
        assertTrue(keysBackupVersionTrust!!.usable)
        assertEquals(2, keysBackupVersionTrust!!.signatures.size)

        stateObserver.stopAndCheckStates(null)
        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a recovery key
     * - And log Alice on a new device
     * - The new device must see the previous backup as not trusted
     * - Trust the backup from the new device with the recovery key
     * - Backup must be enabled on the new device
     * - Retrieve the last version from the server
     * - It must be the same
     * - It must be trusted and must have with 2 signatures now
     */
    @Test
    fun trustKeyBackupVersionWithRecoveryKeyTest() {
        // - Do an e2e backup to the homeserver with a recovery key
        // - And log Alice on a new device
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(null, true)

        val stateObserver = StateObserver(testData.aliceSession2.crypto!!.keysBackup)

        // Wait for backup state to be NotTrusted
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.NotTrusted)

        // - The new device must see the previous backup as not trusted
        assertNotNull(testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion)
        assertFalse(testData.aliceSession2.crypto!!.keysBackup.isEnabled)
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, testData.aliceSession2.crypto!!.keysBackup.state)

        // - Trust the backup from the new device with the recovery key
        val latch = CountDownLatch(1)
        testData.aliceSession2.crypto!!.keysBackup.trustKeysBackupVersionWithRecoveryKey(
                testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion!!,
                testData.prepareKeysBackupDataResult.megolmBackupCreationInfo.recoveryKey,
                TestApiCallback(latch)
        )
        mTestHelper.await(latch)

        // Wait for backup state to be ReadyToBackUp
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.ReadyToBackUp)

        // - Backup must be enabled on the new device, on the same version
        assertEquals(testData.prepareKeysBackupDataResult.version, testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion?.version)
        assertTrue(testData.aliceSession2.crypto!!.keysBackup.isEnabled)

        // - Retrieve the last version from the server
        val latch2 = CountDownLatch(1)
        var keysVersionResult: KeysVersionResult? = null
        testData.aliceSession2.crypto!!.keysBackup.getCurrentVersion(
                object : TestApiCallback<KeysVersionResult?>(latch2) {
                    override fun onSuccess(info: KeysVersionResult?) {
                        keysVersionResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        // - It must be the same
        assertEquals(testData.prepareKeysBackupDataResult.version, keysVersionResult!!.version)

        val latch3 = CountDownLatch(1)
        var keysBackupVersionTrust: KeysBackupVersionTrust? = null
        testData.aliceSession2.crypto!!.keysBackup.getKeysBackupTrust(keysVersionResult!!,
                object : TestApiCallback<KeysBackupVersionTrust>(latch3) {
                    override fun onSuccess(info: KeysBackupVersionTrust) {
                        keysBackupVersionTrust = info
                        super.onSuccess(info)
                    }
                })
        mTestHelper.await(latch3)

        // - It must be trusted and must have 2 signatures now
        assertTrue(keysBackupVersionTrust!!.usable)
        assertEquals(2, keysBackupVersionTrust!!.signatures.size)

        stateObserver.stopAndCheckStates(null)
        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a recovery key
     * - And log Alice on a new device
     * - The new device must see the previous backup as not trusted
     * - Try to trust the backup from the new device with a wrong recovery key
     * - It must fail
     * - The backup must still be untrusted and disabled
     */
    @Test
    fun trustKeyBackupVersionWithWrongRecoveryKeyTest() {
        // - Do an e2e backup to the homeserver with a recovery key
        // - And log Alice on a new device
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(null, true)

        val stateObserver = StateObserver(testData.aliceSession2.crypto!!.keysBackup)

        // Wait for backup state to be NotTrusted
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.NotTrusted)

        // - The new device must see the previous backup as not trusted
        assertNotNull(testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion)
        assertFalse(testData.aliceSession2.crypto!!.keysBackup.isEnabled)
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, testData.aliceSession2.crypto!!.keysBackup.state)

        // - Try to trust the backup from the new device with a wrong recovery key
        val latch = CountDownLatch(1)
        testData.aliceSession2.crypto!!.keysBackup.trustKeysBackupVersionWithRecoveryKey(
                testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion!!,
                "Bad recovery key",
                TestApiCallback(latch, false)
        )
        mTestHelper.await(latch)

        // - The new device must still see the previous backup as not trusted
        assertNotNull(testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion)
        assertFalse(testData.aliceSession2.crypto!!.keysBackup.isEnabled)
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, testData.aliceSession2.crypto!!.keysBackup.state)

        stateObserver.stopAndCheckStates(null)
        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a password
     * - And log Alice on a new device
     * - The new device must see the previous backup as not trusted
     * - Trust the backup from the new device with the password
     * - Backup must be enabled on the new device
     * - Retrieve the last version from the server
     * - It must be the same
     * - It must be trusted and must have with 2 signatures now
     */
    @Test
    fun trustKeyBackupVersionWithPasswordTest() {
        val password = "Password"

        // - Do an e2e backup to the homeserver with a password
        // - And log Alice on a new device
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(password, true)

        val stateObserver = StateObserver(testData.aliceSession2.crypto!!.keysBackup)

        // Wait for backup state to be NotTrusted
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.NotTrusted)

        // - The new device must see the previous backup as not trusted
        assertNotNull(testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion)
        assertFalse(testData.aliceSession2.crypto!!.keysBackup.isEnabled)
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, testData.aliceSession2.crypto!!.keysBackup.state)

        // - Trust the backup from the new device with the password
        val latch = CountDownLatch(1)
        testData.aliceSession2.crypto!!.keysBackup.trustKeysBackupVersionWithPassphrase(
                testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion!!,
                password,
                TestApiCallback(latch)
        )
        mTestHelper.await(latch)

        // Wait for backup state to be ReadyToBackUp
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.ReadyToBackUp)

        // - Backup must be enabled on the new device, on the same version
        assertEquals(testData.prepareKeysBackupDataResult.version, testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion?.version)
        assertTrue(testData.aliceSession2.crypto!!.keysBackup.isEnabled)

        // - Retrieve the last version from the server
        val latch2 = CountDownLatch(1)
        var keysVersionResult: KeysVersionResult? = null
        testData.aliceSession2.crypto!!.keysBackup.getCurrentVersion(
                object : TestApiCallback<KeysVersionResult?>(latch2) {
                    override fun onSuccess(info: KeysVersionResult?) {
                        keysVersionResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        // - It must be the same
        assertEquals(testData.prepareKeysBackupDataResult.version, keysVersionResult!!.version)

        val latch3 = CountDownLatch(1)
        var keysBackupVersionTrust: KeysBackupVersionTrust? = null
        testData.aliceSession2.crypto!!.keysBackup.getKeysBackupTrust(keysVersionResult!!,
                object : TestApiCallback<KeysBackupVersionTrust>(latch3) {
                    override fun onSuccess(info: KeysBackupVersionTrust) {
                        keysBackupVersionTrust = info
                        super.onSuccess(info)
                    }
                })
        mTestHelper.await(latch3)

        // - It must be trusted and must have 2 signatures now
        assertTrue(keysBackupVersionTrust!!.usable)
        assertEquals(2, keysBackupVersionTrust!!.signatures.size)

        stateObserver.stopAndCheckStates(null)
        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a password
     * - And log Alice on a new device
     * - The new device must see the previous backup as not trusted
     * - Try to trust the backup from the new device with a wrong password
     * - It must fail
     * - The backup must still be untrusted and disabled
     */
    @Test
    fun trustKeyBackupVersionWithWrongPasswordTest() {
        val password = "Password"
        val badPassword = "Bad Password"

        // - Do an e2e backup to the homeserver with a password
        // - And log Alice on a new device
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(password, true)

        val stateObserver = StateObserver(testData.aliceSession2.crypto!!.keysBackup)

        // Wait for backup state to be NotTrusted
        waitForKeysBackupToBeInState(testData.aliceSession2, KeysBackupStateManager.KeysBackupState.NotTrusted)

        // - The new device must see the previous backup as not trusted
        assertNotNull(testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion)
        assertFalse(testData.aliceSession2.crypto!!.keysBackup.isEnabled)
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, testData.aliceSession2.crypto!!.keysBackup.state)

        // - Try to trust the backup from the new device with a wrong password
        val latch = CountDownLatch(1)
        testData.aliceSession2.crypto!!.keysBackup.trustKeysBackupVersionWithPassphrase(
                testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion!!,
                badPassword,
                TestApiCallback(latch, false)
        )
        mTestHelper.await(latch)

        // - The new device must still see the previous backup as not trusted
        assertNotNull(testData.aliceSession2.crypto!!.keysBackup.mKeysBackupVersion)
        assertFalse(testData.aliceSession2.crypto!!.keysBackup.isEnabled)
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, testData.aliceSession2.crypto!!.keysBackup.state)

        stateObserver.stopAndCheckStates(null)
        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a recovery key
     * - Log Alice on a new device
     * - Try to restore the e2e backup with a wrong recovery key
     * - It must fail
     */
    @Test
    fun restoreKeysBackupWithAWrongRecoveryKeyTest() {
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(null)

        // - Try to restore the e2e backup with a wrong recovery key
        val latch2 = CountDownLatch(1)
        var importRoomKeysResult: ImportRoomKeysResult? = null
        testData.aliceSession2.crypto!!.keysBackup.restoreKeysWithRecoveryKey(testData.prepareKeysBackupDataResult.version,
                "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d",
                null,
                null,
                object : TestApiCallback<ImportRoomKeysResult>(latch2, false) {
                    override fun onSuccess(info: ImportRoomKeysResult) {
                        importRoomKeysResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        // onSuccess may not have been called
        assertNull(importRoomKeysResult)

        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a password
     * - Log Alice on a new device
     * - Restore the e2e backup with the password
     * - Restore must be successful
     */
    @Test
    fun testBackupWithPassword() {
        val password = "password"

        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(password)

        // - Restore the e2e backup with the password
        val latch2 = CountDownLatch(1)
        var importRoomKeysResult: ImportRoomKeysResult? = null
        testData.aliceSession2.crypto!!.keysBackup.restoreKeyBackupWithPassword(testData.prepareKeysBackupDataResult.version,
                password,
                null,
                null,
                object : TestApiCallback<ImportRoomKeysResult>(latch2) {
                    override fun onSuccess(info: ImportRoomKeysResult) {
                        importRoomKeysResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        checkRestoreSuccess(testData, importRoomKeysResult!!.totalNumberOfKeys, importRoomKeysResult!!.successfullyNumberOfImportedKeys)

        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a password
     * - Log Alice on a new device
     * - Try to restore the e2e backup with a wrong password
     * - It must fail
     */
    @Test
    fun restoreKeysBackupWithAWrongPasswordTest() {
        val password = "password"
        val wrongPassword = "passw0rd"

        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(password)

        // - Try to restore the e2e backup with a wrong password
        val latch2 = CountDownLatch(1)
        var importRoomKeysResult: ImportRoomKeysResult? = null
        testData.aliceSession2.crypto!!.keysBackup.restoreKeyBackupWithPassword(testData.prepareKeysBackupDataResult.version,
                wrongPassword,
                null,
                null,
                object : TestApiCallback<ImportRoomKeysResult>(latch2, false) {
                    override fun onSuccess(info: ImportRoomKeysResult) {
                        importRoomKeysResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        // onSuccess may not have been called
        assertNull(importRoomKeysResult)

        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a password
     * - Log Alice on a new device
     * - Restore the e2e backup with the recovery key.
     * - Restore must be successful
     */
    @Test
    fun testUseRecoveryKeyToRestoreAPasswordBasedKeysBackup() {
        val password = "password"

        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(password)

        // - Restore the e2e backup with the recovery key.
        val latch2 = CountDownLatch(1)
        var importRoomKeysResult: ImportRoomKeysResult? = null
        testData.aliceSession2.crypto!!.keysBackup.restoreKeysWithRecoveryKey(testData.prepareKeysBackupDataResult.version,
                testData.prepareKeysBackupDataResult.megolmBackupCreationInfo.recoveryKey,
                null,
                null,
                object : TestApiCallback<ImportRoomKeysResult>(latch2) {
                    override fun onSuccess(info: ImportRoomKeysResult) {
                        importRoomKeysResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        checkRestoreSuccess(testData, importRoomKeysResult!!.totalNumberOfKeys, importRoomKeysResult!!.successfullyNumberOfImportedKeys)

        testData.cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a recovery key
     * - And log Alice on a new device
     * - Try to restore the e2e backup with a password
     * - It must fail
     */
    @Test
    fun testUsePasswordToRestoreARecoveryKeyBasedKeysBackup() {
        val context = InstrumentationRegistry.getContext()

        val testData = createKeysBackupScenarioWithPassword(null)

        // - Try to restore the e2e backup with a password
        val latch2 = CountDownLatch(1)
        var importRoomKeysResult: ImportRoomKeysResult? = null
        testData.aliceSession2.crypto!!.keysBackup.restoreKeyBackupWithPassword(testData.prepareKeysBackupDataResult.version,
                "password",
                null,
                null,
                object : TestApiCallback<ImportRoomKeysResult>(latch2, false) {
                    override fun onSuccess(info: ImportRoomKeysResult) {
                        importRoomKeysResult = info
                        super.onSuccess(info)
                    }
                }
        )
        mTestHelper.await(latch2)

        // onSuccess may not have been called
        assertNull(importRoomKeysResult)

        testData.cryptoTestData.clear(context)
    }

    /**
     * - Create a backup version
     * - Check the returned KeysVersionResult is trusted
     */
    @Test
    fun testIsKeysBackupTrusted() {
        // - Create a backup version
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        // - Do an e2e backup to the homeserver
        prepareAndCreateKeysBackupData(keysBackup)

        // Get key backup version from the home server
        var keysVersionResult: KeysVersionResult? = null
        val lock = CountDownLatch(1)
        keysBackup.getCurrentVersion(object : TestApiCallback<KeysVersionResult?>(lock) {
            override fun onSuccess(info: KeysVersionResult?) {
                keysVersionResult = info
                super.onSuccess(info)
            }
        })
        mTestHelper.await(lock)

        assertNotNull(keysVersionResult)

        // - Check the returned KeyBackupVersion is trusted
        val latch = CountDownLatch(1)
        var keysBackupVersionTrust: KeysBackupVersionTrust? = null
        keysBackup.getKeysBackupTrust(keysVersionResult!!, SuccessCallback { info ->
            keysBackupVersionTrust = info

            latch.countDown()
        })
        mTestHelper.await(latch)

        assertNotNull(keysBackupVersionTrust)
        assertTrue(keysBackupVersionTrust!!.usable)
        assertEquals(1, keysBackupVersionTrust!!.signatures.size)

        val signature = keysBackupVersionTrust!!.signatures[0]
        assertTrue(signature.valid)
        assertNotNull(signature.device)
        assertEquals(cryptoTestData.firstSession.crypto?.myDevice?.deviceId, signature.deviceId)
        assertEquals(signature.device!!.deviceId, cryptoTestData.firstSession.credentials.deviceId)

        stateObserver.stopAndCheckStates(null)
        cryptoTestData.clear(context)
    }

    /**
     * Check backup starts automatically if there is an existing and compatible backup
     * version on the homeserver.
     * - Create a backup version
     * - Restart alice session
     * -> The new alice session must back up to the same version
     */
    @Test
    fun testCheckAndStartKeysBackupWhenRestartingAMatrixSession() {
        // - Create a backup version
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        assertFalse(keysBackup.isEnabled)

        val keyBackupCreationInfo = prepareAndCreateKeysBackupData(keysBackup)

        assertTrue(keysBackup.isEnabled)

        // - Restart alice session
        // - Log Alice on a new device
        val aliceSession2 = mTestHelper.logIntoAccount(cryptoTestData.firstSession.myUserId, defaultSessionParamsWithInitialSync)

        cryptoTestData.clear(context)

        val keysBackup2 = aliceSession2.crypto!!.keysBackup

        val stateObserver2 = StateObserver(keysBackup2)

        // -> The new alice session must back up to the same version
        val latch = CountDownLatch(1)
        var count = 0
        keysBackup2.addListener(object : KeysBackupStateManager.KeysBackupStateListener {
            override fun onStateChange(newState: KeysBackupStateManager.KeysBackupState) {
                // Check the backup completes
                if (keysBackup.state == KeysBackupStateManager.KeysBackupState.ReadyToBackUp) {
                    count++

                    if (count == 2) {
                        // Remove itself from the list of listeners
                        keysBackup.removeListener(this)

                        latch.countDown()
                    }
                }
            }
        })
        mTestHelper.await(latch)

        assertEquals(keyBackupCreationInfo.version, keysBackup2.currentBackupVersion)

        stateObserver.stopAndCheckStates(null)
        stateObserver2.stopAndCheckStates(null)
        aliceSession2.clear(context)
    }

    /**
     * Check WrongBackUpVersion state
     *
     * - Make alice back up her keys to her homeserver
     * - Create a new backup with fake data on the homeserver
     * - Make alice back up all her keys again
     * -> That must fail and her backup state must be WrongBackUpVersion
     */
    @Test
    fun testBackupWhenAnotherBackupWasCreated() {
        // - Create a backup version
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        assertFalse(keysBackup.isEnabled)

        // Wait for keys backup to be finished
        val latch0 = CountDownLatch(1)
        var count = 0
        keysBackup.addListener(object : KeysBackupStateManager.KeysBackupStateListener {
            override fun onStateChange(newState: KeysBackupStateManager.KeysBackupState) {
                // Check the backup completes
                if (newState == KeysBackupStateManager.KeysBackupState.ReadyToBackUp) {
                    count++

                    if (count == 2) {
                        // Remove itself from the list of listeners
                        keysBackup.removeListener(this)

                        latch0.countDown()
                    }
                }
            }
        })

        // - Make alice back up her keys to her homeserver
        prepareAndCreateKeysBackupData(keysBackup)

        assertTrue(keysBackup.isEnabled)

        mTestHelper.await(latch0)

        // - Create a new backup with fake data on the homeserver, directly using the rest client
        val latch = CountDownLatch(1)

        val megolmBackupCreationInfo = mCryptoTestHelper.createFakeMegolmBackupCreationInfo()
        val createKeysBackupVersionBody = CreateKeysBackupVersionBody()
        createKeysBackupVersionBody.algorithm = megolmBackupCreationInfo.algorithm
        createKeysBackupVersionBody.authData = JsonUtils.getBasicGson().toJsonTree(megolmBackupCreationInfo.authData)
        cryptoTestData.firstSession.roomKeysRestClient.createKeysBackupVersion(createKeysBackupVersionBody, TestApiCallback(latch))
        mTestHelper.await(latch)

        // Reset the store backup status for keys
        cryptoTestData.firstSession.crypto!!.cryptoStore.resetBackupMarkers()

        // - Make alice back up all her keys again
        val latch2 = CountDownLatch(1)
        keysBackup.backupAllGroupSessions(object : ProgressListener {
            override fun onProgress(progress: Int, total: Int) {
            }

        }, TestApiCallback(latch2, false))
        mTestHelper.await(latch2)

        // -> That must fail and her backup state must be WrongBackUpVersion
        assertEquals(KeysBackupStateManager.KeysBackupState.WrongBackUpVersion, keysBackup.state)
        assertFalse(keysBackup.isEnabled)

        stateObserver.stopAndCheckStates(null)
        cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver
     * - Log Alice on a new device
     * - Post a message to have a new megolm session
     * - Try to backup all
     * -> It must fail. Backup state must be NotTrusted
     * - Validate the old device from the new one
     * -> Backup should automatically enable on the new device
     * -> It must use the same backup version
     * - Try to backup all again
     * -> It must success
     */
    @Test
    fun testBackupAfterVerifyingADevice() {
        // - Create a backup version
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        // - Make alice back up her keys to her homeserver
        prepareAndCreateKeysBackupData(keysBackup)

        // Wait for keys backup to finish by asking again to backup keys.
        val latch = CountDownLatch(1)
        keysBackup.backupAllGroupSessions(object : ProgressListener {
            override fun onProgress(progress: Int, total: Int) {

            }
        }, TestApiCallback(latch))
        mTestHelper.await(latch)

        val oldDeviceId = cryptoTestData.firstSession.credentials.deviceId
        val oldKeyBackupVersion = keysBackup.currentBackupVersion
        val aliceUserId = cryptoTestData.firstSession.myUserId

        // Close first Alice session, else they will share the same Crypto store and the test fails.
        cryptoTestData.firstSession.clear(context)

        // - Log Alice on a new device
        val aliceSession2 = mTestHelper.logIntoAccount(aliceUserId, defaultSessionParamsWithInitialSync)

        // - Post a message to have a new megolm session
        aliceSession2.crypto!!.setWarnOnUnknownDevices(false)

        val room2 = aliceSession2.dataHandler.getRoom(cryptoTestData.roomId)

        mTestHelper.sendTextMessage(room2, "New key", 1)

        // - Try to backup all in aliceSession2, it must fail
        val keysBackup2 = aliceSession2.crypto!!.keysBackup

        val stateObserver2 = StateObserver(keysBackup2)

        var isSuccessful = false
        val latch2 = CountDownLatch(1)
        keysBackup2.backupAllGroupSessions(object : ProgressListener {
            override fun onProgress(progress: Int, total: Int) {
            }

        }, object : TestApiCallback<Void?>(latch2, false) {
            override fun onSuccess(info: Void?) {
                isSuccessful = true
                super.onSuccess(info)
            }
        })
        mTestHelper.await(latch2)

        assertFalse(isSuccessful)

        // Backup state must be NotTrusted
        assertEquals(KeysBackupStateManager.KeysBackupState.NotTrusted, keysBackup2.state)
        assertFalse(keysBackup2.isEnabled)

        // - Validate the old device from the new one
        val latch3 = CountDownLatch(1)
        aliceSession2.crypto!!.setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED, oldDeviceId, aliceSession2.myUserId, TestApiCallback(latch3))
        mTestHelper.await(latch3)

        // -> Backup should automatically enable on the new device
        val latch4 = CountDownLatch(1)
        keysBackup2.addListener(object : KeysBackupStateManager.KeysBackupStateListener {
            override fun onStateChange(newState: KeysBackupStateManager.KeysBackupState) {
                // Check the backup completes
                if (keysBackup2.state == KeysBackupStateManager.KeysBackupState.ReadyToBackUp) {
                    // Remove itself from the list of listeners
                    keysBackup2.removeListener(this)

                    latch4.countDown()
                }
            }
        })
        mTestHelper.await(latch4)

        // -> It must use the same backup version
        assertEquals(oldKeyBackupVersion, aliceSession2.crypto!!.keysBackup.currentBackupVersion)

        val latch5 = CountDownLatch(1)
        aliceSession2.crypto!!.keysBackup.backupAllGroupSessions(null, TestApiCallback(latch5))
        mTestHelper.await(latch5)

        // -> It must success
        assertTrue(aliceSession2.crypto!!.keysBackup.isEnabled)

        stateObserver.stopAndCheckStates(null)
        stateObserver2.stopAndCheckStates(null)
        aliceSession2.clear(context)
        cryptoTestData.clear(context)
    }

    /**
     * - Do an e2e backup to the homeserver with a recovery key
     * - Delete the backup
     */
    @Test
    fun deleteKeysBackupTest() {
        // - Create a backup version
        val context = InstrumentationRegistry.getContext()
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        assertFalse(keysBackup.isEnabled)

        val keyBackupCreationInfo = prepareAndCreateKeysBackupData(keysBackup)

        assertTrue(keysBackup.isEnabled)

        val latch = CountDownLatch(1)

        // Delete the backup
        keysBackup.deleteBackup(keyBackupCreationInfo.version, TestApiCallback(latch))

        mTestHelper.await(latch)

        // Backup is now disabled
        assertFalse(keysBackup.isEnabled)

        stateObserver.stopAndCheckStates(null)
        cryptoTestData.clear(context)
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    /**
     * As KaysBackup is doing asynchronous call to update its internal state, this method help to wait for the
     * KeysBackup object to be in the specified state
     */
    private fun waitForKeysBackupToBeInState(mxSession: MXSession, state: KeysBackupStateManager.KeysBackupState) {
        // If already in the wanted state, return
        if (mxSession.crypto?.keysBackup?.state == state) {
            return
        }

        // Else observe state changes
        val latch = CountDownLatch(1)

        mxSession.crypto?.keysBackup?.addListener(object : KeysBackupStateManager.KeysBackupStateListener {
            override fun onStateChange(newState: KeysBackupStateManager.KeysBackupState) {
                if (newState == state) {
                    mxSession.crypto?.keysBackup?.removeListener(this)
                    latch.countDown()
                }
            }
        })

        mTestHelper.await(latch)
    }

    private data class PrepareKeysBackupDataResult(val megolmBackupCreationInfo: MegolmBackupCreationInfo,
                                                   val version: String)

    private fun prepareAndCreateKeysBackupData(keysBackup: KeysBackup,
                                               password: String? = null): PrepareKeysBackupDataResult {
        val stateObserver = StateObserver(keysBackup)

        var megolmBackupCreationInfo: MegolmBackupCreationInfo? = null
        val latch = CountDownLatch(1)
        keysBackup.prepareKeysBackupVersion(password, null, object : SuccessErrorCallback<MegolmBackupCreationInfo> {

            override fun onSuccess(info: MegolmBackupCreationInfo) {
                megolmBackupCreationInfo = info

                latch.countDown()
            }

            override fun onUnexpectedError(e: Exception) {
                fail(e.localizedMessage)

                latch.countDown()
            }
        })
        mTestHelper.await(latch)

        assertNotNull(megolmBackupCreationInfo)

        assertFalse(keysBackup.isEnabled)

        val latch2 = CountDownLatch(1)

        // Create the version
        var version: String? = null
        keysBackup.createKeysBackupVersion(megolmBackupCreationInfo!!, object : TestApiCallback<KeysVersion>(latch2) {
            override fun onSuccess(info: KeysVersion) {
                assertNotNull(info)
                assertNotNull(info.version)

                version = info.version

                // Backup must be enable now
                assertTrue(keysBackup.isEnabled)

                super.onSuccess(info)
            }
        })
        mTestHelper.await(latch2)

        stateObserver.stopAndCheckStates(null)
        return PrepareKeysBackupDataResult(megolmBackupCreationInfo!!, version!!)
    }

    private fun assertKeysEquals(keys1: MegolmSessionData?, keys2: MegolmSessionData?) {
        assertNotNull(keys1)
        assertNotNull(keys2)

        assertEquals(keys1?.algorithm, keys2?.algorithm)
        assertEquals(keys1?.roomId, keys2?.roomId)
        // No need to compare the shortcut
        // assertEquals(keys1?.sender_claimed_ed25519_key, keys2?.sender_claimed_ed25519_key)
        assertEquals(keys1?.senderKey, keys2?.senderKey)
        assertEquals(keys1?.sessionId, keys2?.sessionId)
        assertEquals(keys1?.sessionKey, keys2?.sessionKey)

        assertListEquals(keys1?.forwardingCurve25519KeyChain, keys2?.forwardingCurve25519KeyChain)
        assertDictEquals(keys1?.senderClaimedKeys, keys2?.senderClaimedKeys)
    }

    /**
     * Data class to store result of [createKeysBackupScenarioWithPassword]
     */
    private data class KeysBackupScenarioData(val cryptoTestData: CryptoTestData,
                                              val aliceKeys: MutableList<MXOlmInboundGroupSession2>,
                                              val prepareKeysBackupDataResult: PrepareKeysBackupDataResult,
                                              val aliceSession2: MXSession)

    /**
     * Common initial condition
     * - Do an e2e backup to the homeserver
     * - Log Alice on a new device
     *
     * @param password optional password
     * @param logoutFirstAliceSession set to true to logout first Alice session before opening the second one (default is false)
     */
    private fun createKeysBackupScenarioWithPassword(password: String?,
                                                     logoutFirstAliceSession: Boolean = false): KeysBackupScenarioData {
        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true)

        val cryptoStore = cryptoTestData.firstSession.crypto!!.cryptoStore
        val keysBackup = cryptoTestData.firstSession.crypto!!.keysBackup

        val stateObserver = StateObserver(keysBackup)

        val aliceKeys = cryptoStore.inboundGroupSessionsToBackup(100)

        // - Do an e2e backup to the homeserver
        val prepareKeysBackupDataResult = prepareAndCreateKeysBackupData(keysBackup, password)

        val latch = CountDownLatch(1)
        var lastProgress = 0
        var lastTotal = 0
        keysBackup.backupAllGroupSessions(object : ProgressListener {
            override fun onProgress(progress: Int, total: Int) {
                lastProgress = progress
                lastTotal = total
            }
        }, TestApiCallback(latch))
        mTestHelper.await(latch)

        assertEquals(2, lastProgress)
        assertEquals(2, lastTotal)

        val aliceUserId = cryptoTestData.firstSession.myUserId

        if (logoutFirstAliceSession) {
            // Logout first Alice session, else they will share the same Crypto store and some tests may fail.
            val latch2 = CountDownLatch(1)
            cryptoTestData.firstSession.logout(InstrumentationRegistry.getContext(), TestApiCallback(latch2))
            mTestHelper.await(latch2)
        }

        // - Log Alice on a new device
        val aliceSession2 = mTestHelper.logIntoAccount(aliceUserId, defaultSessionParamsWithInitialSync)

        // Test check: aliceSession2 has no keys at login
        assertEquals(0, aliceSession2.crypto!!.cryptoStore.inboundGroupSessionsCount(false))

        stateObserver.stopAndCheckStates(null)

        return KeysBackupScenarioData(cryptoTestData,
                aliceKeys,
                prepareKeysBackupDataResult,
                aliceSession2)
    }

    /**
     * Common restore success check after [createKeysBackupScenarioWithPassword]:
     * - Imported keys number must be correct
     * - The new device must have the same count of megolm keys
     * - Alice must have the same keys on both devices
     */
    private fun checkRestoreSuccess(testData: KeysBackupScenarioData,
                                    total: Int,
                                    imported: Int) {
        // - Imported keys number must be correct
        assertEquals(testData.aliceKeys.size, total)
        assertEquals(total, imported)

        // - The new device must have the same count of megolm keys
        assertEquals(testData.aliceKeys.size, testData.aliceSession2.crypto!!.cryptoStore.inboundGroupSessionsCount(false))

        // - Alice must have the same keys on both devices
        for (aliceKey1 in testData.aliceKeys) {
            val aliceKey2 = testData.aliceSession2.crypto!!
                    .cryptoStore.getInboundGroupSession(aliceKey1.mSession.sessionIdentifier(), aliceKey1.mSenderKey)
            assertNotNull(aliceKey2)
            assertKeysEquals(aliceKey1.exportKeys(), aliceKey2!!.exportKeys())
        }
    }
}