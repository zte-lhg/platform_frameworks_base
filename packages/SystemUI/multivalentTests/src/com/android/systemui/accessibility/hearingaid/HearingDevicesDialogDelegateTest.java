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

package com.android.systemui.accessibility.hearingaid;

import static android.bluetooth.BluetoothHapClient.PRESET_INDEX_UNAVAILABLE;

import static com.android.systemui.accessibility.hearingaid.HearingDevicesDialogDelegate.LIVE_CAPTION_INTENT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;
import android.testing.TestableLooper;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Space;
import android.widget.Spinner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.HapClientProfile;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.systemui.Flags;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.bluetooth.qsdialog.DeviceItem;
import com.android.systemui.bluetooth.qsdialog.DeviceItemType;
import com.android.systemui.model.SysUiState;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.phone.SystemUIDialogManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

/** Tests for {@link HearingDevicesDialogDelegate}. */
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class HearingDevicesDialogDelegateTest extends SysuiTestCase {
    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    private static final int TEST_LAUNCH_SOURCE_ID = 1;
    private static final String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF";
    private static final String DEVICE_NAME = "test_name";
    private static final String TEST_PKG = "pkg";
    private static final String TEST_CLS = "cls";
    private static final ComponentName TEST_COMPONENT = new ComponentName(TEST_PKG, TEST_CLS);
    private static final String TEST_LABEL = "label";
    private static final int TEST_PRESET_INDEX = 1;
    private static final String TEST_PRESET_NAME = "test_preset";

    @Mock
    private SystemUIDialogManager mSystemUIDialogManager;
    @Mock
    private SysUiState mSysUiState;
    @Mock
    private DialogTransitionAnimator mDialogTransitionAnimator;
    @Mock
    private ActivityStarter mActivityStarter;
    @Mock
    private LocalBluetoothManager mLocalBluetoothManager;
    @Mock
    private LocalBluetoothAdapter mLocalBluetoothAdapter;
    @Mock
    private LocalBluetoothProfileManager mProfileManager;
    @Mock
    private HapClientProfile mHapClientProfile;
    @Mock
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    @Mock
    private BluetoothEventManager mBluetoothEventManager;
    @Mock
    private AudioManager mAudioManager;
    @Mock
    private HearingDevicesUiEventLogger mUiEventLogger;
    @Mock
    private CachedBluetoothDevice mCachedDevice;
    @Mock
    private BluetoothDevice mDevice;
    @Mock
    private DeviceItem mHearingDeviceItem;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ActivityInfo mActivityInfo;
    @Mock
    private Drawable mDrawable;

    private SystemUIDialog mDialog;
    private SystemUIDialog.Factory mDialogFactory;
    private HearingDevicesDialogDelegate mDialogDelegate;
    private TestableLooper mTestableLooper;

    @Before
    public void setUp() {
        mTestableLooper = TestableLooper.get(this);
        when(mLocalBluetoothManager.getBluetoothAdapter()).thenReturn(mLocalBluetoothAdapter);
        when(mLocalBluetoothManager.getProfileManager()).thenReturn(mProfileManager);
        when(mProfileManager.getHapClientProfile()).thenReturn(mHapClientProfile);
        when(mLocalBluetoothAdapter.isEnabled()).thenReturn(true);
        when(mLocalBluetoothManager.getCachedDeviceManager()).thenReturn(mCachedDeviceManager);
        when(mCachedDeviceManager.getCachedDevicesCopy()).thenReturn(List.of(mCachedDevice));
        when(mLocalBluetoothManager.getEventManager()).thenReturn(mBluetoothEventManager);
        when(mSysUiState.setFlag(anyLong(), anyBoolean())).thenReturn(mSysUiState);
        when(mDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mDevice.isConnected()).thenReturn(true);
        when(mCachedDevice.getDevice()).thenReturn(mDevice);
        when(mCachedDevice.getAddress()).thenReturn(DEVICE_ADDRESS);
        when(mCachedDevice.getName()).thenReturn(DEVICE_NAME);
        when(mCachedDevice.getProfiles()).thenReturn(List.of(mHapClientProfile));
        when(mCachedDevice.isActiveDevice(BluetoothProfile.HEARING_AID)).thenReturn(true);
        when(mCachedDevice.isConnectedHearingAidDevice()).thenReturn(true);
        when(mCachedDevice.isConnectedHapClientDevice()).thenReturn(true);
        when(mCachedDevice.getDrawableWithDescription()).thenReturn(new Pair<>(mDrawable, ""));
        when(mHearingDeviceItem.getCachedBluetoothDevice()).thenReturn(mCachedDevice);

        mContext.setMockPackageManager(mPackageManager);
    }

    @Test
    public void clickPairNewDeviceButton_intentActionMatch() {
        setUpDeviceDialogWithPairNewDeviceButton();
        mDialog.show();

        getPairNewDeviceButton(mDialog).performClick();

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mActivityStarter).postStartActivityDismissingKeyguard(intentCaptor.capture(),
                anyInt(), any());
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(
                Settings.ACTION_HEARING_DEVICE_PAIRING_SETTINGS);
        verify(mUiEventLogger).log(HearingDevicesUiEvent.HEARING_DEVICES_PAIR,
                TEST_LAUNCH_SOURCE_ID);
    }

    @Test
    public void onDeviceItemGearClicked_intentActionMatch() {
        setUpDeviceDialogWithoutPairNewDeviceButton();

        mDialogDelegate.onDeviceItemGearClicked(mHearingDeviceItem, new View(mContext));

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mActivityStarter).postStartActivityDismissingKeyguard(intentCaptor.capture(),
                anyInt(), any());
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(
                HearingDevicesDialogDelegate.ACTION_BLUETOOTH_DEVICE_DETAILS);
        verify(mUiEventLogger).log(HearingDevicesUiEvent.HEARING_DEVICES_GEAR_CLICK,
                TEST_LAUNCH_SOURCE_ID);
    }

    @Test
    public void onDeviceItemOnClicked_connectedDevice_disconnect() {
        setUpDeviceDialogWithoutPairNewDeviceButton();
        when(mHearingDeviceItem.getType()).thenReturn(DeviceItemType.CONNECTED_BLUETOOTH_DEVICE);

        mDialogDelegate.onDeviceItemClicked(mHearingDeviceItem, new View(mContext));

        verify(mCachedDevice).disconnect();
        verify(mUiEventLogger).log(HearingDevicesUiEvent.HEARING_DEVICES_DISCONNECT,
                TEST_LAUNCH_SOURCE_ID);
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS)
    public void showDialog_noLiveCaption_noRelatedToolsInConfig_relatedToolLayoutGone() {
        mContext.getOrCreateTestableResources().addOverride(
                R.array.config_quickSettingsHearingDevicesRelatedToolName, new String[]{});

        setUpDeviceDialogWithoutPairNewDeviceButton();
        mDialog.show();

        assertToolsUi(0);
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS)
    public void showDialog_hasLiveCaption_noRelatedToolsInConfig_showOneRelatedTool() {
        when(mPackageManager.queryIntentActivities(
                eq(LIVE_CAPTION_INTENT), anyInt())).thenReturn(
                List.of(new ResolveInfo()));
        mContext.getOrCreateTestableResources().addOverride(
                R.array.config_quickSettingsHearingDevicesRelatedToolName, new String[]{});

        setUpDeviceDialogWithoutPairNewDeviceButton();
        mDialog.show();

        assertToolsUi(1);
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS)
    public void showDialog_hasLiveCaption_oneRelatedToolInConfig_showTwoRelatedTools()
            throws PackageManager.NameNotFoundException {
        when(mPackageManager.queryIntentActivities(eq(LIVE_CAPTION_INTENT), anyInt()))
                .thenReturn(List.of(new ResolveInfo()));
        mContext.getOrCreateTestableResources().addOverride(
                R.array.config_quickSettingsHearingDevicesRelatedToolName,
                new String[]{TEST_PKG + "/" + TEST_CLS});
        when(mPackageManager.getActivityInfo(eq(TEST_COMPONENT), anyInt())).thenReturn(
                mActivityInfo);
        when(mActivityInfo.loadLabel(mPackageManager)).thenReturn(TEST_LABEL);
        when(mActivityInfo.loadIcon(mPackageManager)).thenReturn(mDrawable);
        when(mActivityInfo.getComponentName()).thenReturn(TEST_COMPONENT);
        when(mDrawable.mutate()).thenReturn(mDrawable);

        setUpDeviceDialogWithoutPairNewDeviceButton();
        mDialog.show();

        assertToolsUi(2);
    }

    @Test
    public void showDialog_noPreset_presetLayoutGone() {
        when(mHapClientProfile.getAllPresetInfo(mDevice)).thenReturn(new ArrayList<>());
        when(mHapClientProfile.getActivePresetIndex(mDevice)).thenReturn(PRESET_INDEX_UNAVAILABLE);

        setUpDeviceDialogWithoutPairNewDeviceButton();
        mDialog.show();

        ViewGroup presetLayout = getPresetLayout(mDialog);
        assertThat(presetLayout.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void showDialog_presetExist_presetSelected() {
        BluetoothHapPresetInfo info = getTestPresetInfo();
        when(mHapClientProfile.getAllPresetInfo(mDevice)).thenReturn(List.of(info));
        when(mHapClientProfile.getActivePresetIndex(mDevice)).thenReturn(TEST_PRESET_INDEX);

        setUpDeviceDialogWithoutPairNewDeviceButton();
        mDialog.show();
        mTestableLooper.processAllMessages();

        ViewGroup presetLayout = getPresetLayout(mDialog);
        assertThat(presetLayout.getVisibility()).isEqualTo(View.VISIBLE);
        Spinner spinner = getPresetSpinner(mDialog);
        assertThat(spinner.getSelectedItemPosition()).isEqualTo(0);
    }

    @Test
    public void onActiveDeviceChanged_presetExist_presetSelected() {
        setUpDeviceDialogWithoutPairNewDeviceButton();
        mDialog.show();
        BluetoothHapPresetInfo info = getTestPresetInfo();
        when(mHapClientProfile.getAllPresetInfo(mDevice)).thenReturn(List.of(info));
        when(mHapClientProfile.getActivePresetIndex(mDevice)).thenReturn(TEST_PRESET_INDEX);

        Spinner spinner = getPresetSpinner(mDialog);
        assertThat(spinner.getSelectedItemPosition()).isEqualTo(-1);

        mDialogDelegate.onActiveDeviceChanged(mCachedDevice, BluetoothProfile.LE_AUDIO);
        mTestableLooper.processAllMessages();

        ViewGroup presetLayout = getPresetLayout(mDialog);
        assertThat(presetLayout.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(spinner.getSelectedItemPosition()).isEqualTo(0);
    }

    private void setUpDeviceDialogWithPairNewDeviceButton() {
        setUpDeviceDialog(/* showPairNewDevice= */ true);
    }

    private void setUpDeviceDialogWithoutPairNewDeviceButton() {
        setUpDeviceDialog(/* showPairNewDevice= */ false);
    }

    private void setUpDeviceDialog(boolean showPairNewDevice) {
        mDialogFactory = new SystemUIDialog.Factory(
                mContext,
                mSystemUIDialogManager,
                mSysUiState,
                getFakeBroadcastDispatcher(),
                mDialogTransitionAnimator
        );
        mDialogDelegate = new HearingDevicesDialogDelegate(
                showPairNewDevice,
                TEST_LAUNCH_SOURCE_ID,
                mDialogFactory,
                mActivityStarter,
                mDialogTransitionAnimator,
                mLocalBluetoothManager,
                new Handler(mTestableLooper.getLooper()),
                mAudioManager,
                mUiEventLogger
        );
        mDialog = mDialogDelegate.createDialog();
    }

    private BluetoothHapPresetInfo getTestPresetInfo() {
        BluetoothHapPresetInfo info = mock(BluetoothHapPresetInfo.class);
        when(info.getName()).thenReturn(TEST_PRESET_NAME);
        when(info.getIndex()).thenReturn(TEST_PRESET_INDEX);
        when(info.isAvailable()).thenReturn(true);
        return info;
    }

    private View getPairNewDeviceButton(SystemUIDialog dialog) {
        return dialog.requireViewById(R.id.pair_new_device_button);
    }

    private ViewGroup getToolsContainer(SystemUIDialog dialog) {
        return dialog.requireViewById(R.id.tools_container);
    }

    private ViewGroup getToolsLayout(SystemUIDialog dialog) {
        return dialog.requireViewById(R.id.tools_layout);
    }

    private Spinner getPresetSpinner(SystemUIDialog dialog) {
        return dialog.requireViewById(R.id.preset_spinner);
    }

    private ViewGroup getPresetLayout(SystemUIDialog dialog) {
        return dialog.requireViewById(R.id.preset_layout);
    }


    private int countChildWithoutSpace(ViewGroup viewGroup) {
        int spaceCount = 0;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            if (viewGroup.getChildAt(i) instanceof Space) {
                spaceCount++;
            }
        }
        return viewGroup.getChildCount() - spaceCount;
    }

    private void assertToolsUi(int childCount) {
        ViewGroup toolsContainer = getToolsContainer(mDialog);
        assertThat(countChildWithoutSpace(toolsContainer)).isEqualTo(childCount);

        int targetVisibility = childCount == 0 ? View.GONE : View.VISIBLE;
        ViewGroup toolsLayout = getToolsLayout(mDialog);
        assertThat(toolsLayout.getVisibility()).isEqualTo(targetVisibility);
    }

    @After
    public void reset() {
        if (mDialogDelegate != null) {
            mDialogDelegate = null;
        }
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }
}
