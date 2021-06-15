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

import static android.net.TetheringManager.ACTION_TETHER_STATE_CHANGED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_CHANGED_ACTION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceGroup;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;

public class WifiTetherSettings extends PreferenceControllerFragment
        implements WifiTetherBasePreferenceController.OnTetherConfigUpdateListener {
    private <T extends AbstractPreferenceController> T use(Class<T> clazz) {
        return getOnePreferenceController(clazz);
    }

    private static final String TAG = "WifiTetherSettings";
    private static final IntentFilter TETHER_STATE_CHANGE_FILTER;
    private static final int EXPANDED_CHILD_COUNT_WITH_SECURITY_NON = 3;
    private static final int EXPANDED_CHILD_COUNT_DEFAULT = 4;

    static final String KEY_WIFI_TETHER_ENABLE = "wifi_tether_enable";
    static final String KEY_WIFI_TETHER_AUTO_OFF = "wifi_tether_auto_turn_off";

    private WifiTetherSwitchBarController mSwitchBarController;
    private WifiTetherSSIDPreferenceController mSSIDPreferenceController;
    private WifiTetherPasswordPreferenceController mPasswordPreferenceController;
    private WifiTetherApBandPreferenceController mApBandPreferenceController;
    private WifiTetherSecurityPreferenceController mSecurityPreferenceController;

    private WifiManager mWifiManager;
    private boolean mRestartWifiApAfterConfigChange;

    @VisibleForTesting
    TetherChangeReceiver mTetherChangeReceiver;

    static {
        TETHER_STATE_CHANGE_FILTER = new IntentFilter(ACTION_TETHER_STATE_CHANGED);
        TETHER_STATE_CHANGE_FILTER.addAction(WIFI_AP_STATE_CHANGED_ACTION);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mTetherChangeReceiver = new TetherChangeReceiver();

        mSSIDPreferenceController = use(WifiTetherSSIDPreferenceController.class);
        mSecurityPreferenceController = use(WifiTetherSecurityPreferenceController.class);
        mPasswordPreferenceController = use(WifiTetherPasswordPreferenceController.class);
        mApBandPreferenceController = use(WifiTetherApBandPreferenceController.class);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        mSwitchBarController = new WifiTetherSwitchBarController(getContext(), KEY_WIFI_TETHER_ENABLE);
        mSwitchBarController.displayPreference(getPreferenceScreen());
    }

    @Override
    public void onStart() {
        super.onStart();
        final Context context = getContext();
        if (context != null) {
            context.registerReceiver(mTetherChangeReceiver, TETHER_STATE_CHANGE_FILTER);
        }
        mSwitchBarController.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        final Context context = getContext();
        if (context != null) {
            context.unregisterReceiver(mTetherChangeReceiver);
        }
        mSwitchBarController.onStop();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_tether_settings;
    }

    protected List<AbstractPreferenceController> onCreatePreferenceControllers(
            Context context){
        return buildPreferenceControllers(context, this::onTetherConfigUpdated);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            WifiTetherBasePreferenceController.OnTetherConfigUpdateListener listener) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new WifiTetherSSIDPreferenceController(context, listener));
        controllers.add(new WifiTetherSecurityPreferenceController(context, listener));
        controllers.add(new WifiTetherPasswordPreferenceController(context, listener));
        controllers.add(new WifiTetherApBandPreferenceController(context, listener));
        controllers.add(
                new WifiTetherAutoOffPreferenceController(context, KEY_WIFI_TETHER_AUTO_OFF));

        return controllers;
    }

    @Override
    public void onTetherConfigUpdated(AbstractPreferenceController context) {
        final SoftApConfiguration config = buildNewConfig();
        mPasswordPreferenceController.updateVisibility(config.getSecurityType());

        /**
         * if soft AP is stopped, bring up
         * else restart with new config
         * TODO: update config on a running access point when framework support is added
         */
        if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
            Log.d("TetheringSettings",
                    "Wifi AP config changed while enabled, stop and restart");
            mRestartWifiApAfterConfigChange = true;
            mSwitchBarController.stopTether();
        }
        mWifiManager.setSoftApConfiguration(config);

        if (context instanceof WifiTetherSecurityPreferenceController) {
            reConfigInitialExpandedChildCount();
        }
    }

    private SoftApConfiguration buildNewConfig() {
        final SoftApConfiguration.Builder configBuilder = new SoftApConfiguration.Builder();
        final int securityType = mSecurityPreferenceController.getSecurityType();
        configBuilder.setSsid(mSSIDPreferenceController.getSSID());
        if (securityType == SoftApConfiguration.SECURITY_TYPE_WPA2_PSK) {
            configBuilder.setPassphrase(
                    mPasswordPreferenceController.getPasswordValidated(securityType),
                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        }
        configBuilder.setBand(mApBandPreferenceController.getBandIndex());
        return configBuilder.build();
    }

    private void startTether() {
        mRestartWifiApAfterConfigChange = false;
        mSwitchBarController.startTether();
    }

    private void updateDisplayWithNewConfig() {
        use(WifiTetherSSIDPreferenceController.class)
                .updateDisplay();
        use(WifiTetherSecurityPreferenceController.class)
                .updateDisplay();
        use(WifiTetherPasswordPreferenceController.class)
                .updateDisplay();
        use(WifiTetherApBandPreferenceController.class)
                .updateDisplay();
    }

    @VisibleForTesting
    class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "updating display config due to receiving broadcast action " + action);
            updateDisplayWithNewConfig();
            if (action.equals(ACTION_TETHER_STATE_CHANGED)) {
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_DISABLED
                        && mRestartWifiApAfterConfigChange) {
                    startTether();
                }
            } else if (action.equals(WIFI_AP_STATE_CHANGED_ACTION)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, 0);
                if (state == WifiManager.WIFI_AP_STATE_DISABLED
                        && mRestartWifiApAfterConfigChange) {
                    startTether();
                }
            }
        }
    }

    private void reConfigInitialExpandedChildCount() {
        final PreferenceGroup screen = getPreferenceScreen();
        if (mSecurityPreferenceController.getSecurityType()
                == SoftApConfiguration.SECURITY_TYPE_OPEN) {
            screen.setInitialExpandedChildrenCount(EXPANDED_CHILD_COUNT_WITH_SECURITY_NON);
            return;
        }
        screen.setInitialExpandedChildrenCount(EXPANDED_CHILD_COUNT_DEFAULT);
    }

    public int getInitialExpandedChildCount() {
        if (mSecurityPreferenceController == null) {
            return EXPANDED_CHILD_COUNT_DEFAULT;
        }

        return (mSecurityPreferenceController.getSecurityType()
                == SoftApConfiguration.SECURITY_TYPE_OPEN)
            ? EXPANDED_CHILD_COUNT_WITH_SECURITY_NON : EXPANDED_CHILD_COUNT_DEFAULT;
    }
}
