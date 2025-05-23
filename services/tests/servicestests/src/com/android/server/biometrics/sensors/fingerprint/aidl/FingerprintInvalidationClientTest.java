/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.biometrics.sensors.fingerprint.aidl;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import android.hardware.biometrics.IInvalidationCallback;
import android.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.biometrics.log.BiometricContext;
import com.android.server.biometrics.log.BiometricLogger;
import com.android.server.biometrics.sensors.ClientMonitorCallback;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.util.HashMap;

@Presubmit
@SmallTest
public class FingerprintInvalidationClientTest {

    private static final int SENSOR_ID = 4;
    private static final int USER_ID = 0;


    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private IBiometricsFingerprint mFingerprint;
    @Mock
    private AidlResponseHandler mAidlResponseHandler;
    @Mock
    private BiometricLogger mBiometricLogger;
    @Mock
    private BiometricContext mBiometricContext;
    @Mock
    private IInvalidationCallback mInvalidationCallback;
    @Mock
    private ClientMonitorCallback mClientMonitorCallback;

    @Test
    public void testStartInvalidationClient_whenHalIsHidl() throws RemoteException {
        final AidlSession aidlSession = new AidlSession(
                () -> mFingerprint, USER_ID, mAidlResponseHandler);
        final FingerprintInvalidationClient fingerprintInvalidationClient =
                new FingerprintInvalidationClient(mContext, () -> aidlSession, USER_ID,
                        SENSOR_ID, mBiometricLogger, mBiometricContext, new HashMap<>(),
                        mInvalidationCallback);

        doAnswer((Answer<Void>) invocationOnMock -> {
            fingerprintInvalidationClient.cancel();
            return null;
        }).when(mAidlResponseHandler).onUnsupportedClientScheduled(
                FingerprintInvalidationClient.class);

        fingerprintInvalidationClient.start(mClientMonitorCallback);

        verify(mInvalidationCallback).onCompleted();
    }
}
