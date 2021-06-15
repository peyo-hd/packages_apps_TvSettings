/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.wifi.tether;

import static android.net.ConnectivityManager.TETHERING_WIFI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

/**
 * Controller for logic pertaining to switch Wi-Fi tethering.
 */
public class WifiTetherSwitchBarController extends BasePreferenceController implements
        Preference.OnPreferenceChangeListener {
    private static final IntentFilter WIFI_INTENT_FILTER;

    private final Context mContext;
    private SwitchPreference mSwitchPref;
    private final ConnectivityManager mConnectivityManager;
    private final WifiManager mWifiManager;

    @VisibleForTesting
    final ConnectivityManager.OnStartTetheringCallback mOnStartTetheringCallback =
            new ConnectivityManager.OnStartTetheringCallback() {
                @Override
                public void onTetheringFailed() {
                    super.onTetheringFailed();
                    mSwitchPref.setChecked(false);
                    updateWifiSwitch();
                }
            };

    static {
        WIFI_INTENT_FILTER = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
    }

    public WifiTetherSwitchBarController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mContext = context;
        mConnectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mSwitchPref = (SwitchPreference) screen.findPreference(getPreferenceKey());
        mSwitchPref.setOnPreferenceChangeListener(this);
        mSwitchPref.setChecked(mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED);
        updateWifiSwitch();
    }

    public void onStart() {
        mContext.registerReceiver(mReceiver, WIFI_INTENT_FILTER);
    }

    public void onStop() {
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean settingsOn = (Boolean) newValue;
        if (!settingsOn) {
            stopTether();
        } else if (!mWifiManager.isWifiApEnabled()) {
            startTether();
        }
        return true;
    }

    void stopTether() {
        mSwitchPref.setEnabled(false);
        mConnectivityManager.stopTethering(TETHERING_WIFI);
    }

    void startTether() {
        mSwitchPref.setEnabled(false);
        mConnectivityManager.startTethering(TETHERING_WIFI, true /* showProvisioningUi */,
                mOnStartTetheringCallback, new Handler(Looper.getMainLooper()));
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                final int state = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
                handleWifiApStateChanged(state);
            }
        }
    };

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mSwitchPref.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                if (!mSwitchPref.isChecked()) {
                    mSwitchPref.setChecked(true);
                }
                updateWifiSwitch();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                if (mSwitchPref.isChecked()) {
                    mSwitchPref.setChecked(false);
                }
                mSwitchPref.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mSwitchPref.setChecked(false);
                updateWifiSwitch();
                break;
            default:
                mSwitchPref.setChecked(false);
                updateWifiSwitch();
                break;
        }
    }

    private void updateWifiSwitch() {
        mSwitchPref.setEnabled(true);
    }

    @Override
    public int getAvailabilityStatus() {
        return 0;
    }

}
