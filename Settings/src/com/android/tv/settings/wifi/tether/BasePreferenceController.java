/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.tv.settings.wifi.tether;

import android.annotation.IntDef;
import android.content.Context;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.core.AbstractPreferenceController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Abstract class to consolidate utility between preference controllers and act as an interface
 * for Slices. The abstract classes that inherit from this class will act as the direct interfaces
 * for each type when plugging into Slices.
 */
public abstract class BasePreferenceController extends AbstractPreferenceController {

    private static final String TAG = "SettingsPrefController";

    /**
     * Denotes the availability of the Setting.
     * <p>
     * Used both explicitly and by the convenience methods {@link #isAvailable()} and
     * {@link #isSupported()}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AVAILABLE, AVAILABLE_UNSEARCHABLE, UNSUPPORTED_ON_DEVICE, DISABLED_FOR_USER,
            DISABLED_DEPENDENT_SETTING, CONDITIONALLY_UNAVAILABLE})
    public @interface AvailabilityStatus {
    }

    /**
     * The setting is available, and searchable to all search clients.
     */
    public static final int AVAILABLE = 0;

    /**
     * The setting is available, but is not searchable to any search client.
     */
    public static final int AVAILABLE_UNSEARCHABLE = 1;

    /**
     * A generic catch for settings which are currently unavailable, but may become available in
     * the future. You should use {@link #DISABLED_FOR_USER} or {@link #DISABLED_DEPENDENT_SETTING}
     * if they describe the condition more accurately.
     */
    public static final int CONDITIONALLY_UNAVAILABLE = 2;

    /**
     * The setting is not, and will not supported by this device.
     * <p>
     * There is no guarantee that the setting page exists, and any links to the Setting should take
     * you to the home page of Settings.
     */
    public static final int UNSUPPORTED_ON_DEVICE = 3;


    /**
     * The setting cannot be changed by the current user.
     * <p>
     * Links to the Setting should take you to the page of the Setting, even if it cannot be
     * changed.
     */
    public static final int DISABLED_FOR_USER = 4;

    /**
     * The setting has a dependency in the Settings App which is currently blocking access.
     * <p>
     * It must be possible for the Setting to be enabled by changing the configuration of the device
     * settings. That is, a setting that cannot be changed because of the state of another setting.
     * This should not be used for a setting that would be hidden from the UI entirely.
     * <p>
     * Correct use: Intensity of night display should be {@link #DISABLED_DEPENDENT_SETTING} when
     * night display is off.
     * Incorrect use: Mobile Data is {@link #DISABLED_DEPENDENT_SETTING} when there is no
     * data-enabled sim.
     * <p>
     * Links to the Setting should take you to the page of the Setting, even if it cannot be
     * changed.
     */
    public static final int DISABLED_DEPENDENT_SETTING = 5;

    protected final String mPreferenceKey;
    protected UiBlockListener mUiBlockListener;
    private int mMetricsCategory;

    /**
     * Instantiate a controller as specified controller type and user-defined key.
     * <p/>
     * This is done through reflection. Do not use this method unless you know what you are doing.
     */
    public static BasePreferenceController createInstance(Context context,
            String controllerName, String key) {
        try {
            final Class<?> clazz = Class.forName(controllerName);
            final Constructor<?> preferenceConstructor =
                    clazz.getConstructor(Context.class, String.class);
            final Object[] params = new Object[]{context, key};
            return (BasePreferenceController) preferenceConstructor.newInstance(params);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Invalid preference controller: " + controllerName, e);
        }
    }

    /**
     * Instantiate a controller as specified controller type.
     * <p/>
     * This is done through reflection. Do not use this method unless you know what you are doing.
     */
    public static BasePreferenceController createInstance(Context context, String controllerName) {
        try {
            final Class<?> clazz = Class.forName(controllerName);
            final Constructor<?> preferenceConstructor = clazz.getConstructor(Context.class);
            final Object[] params = new Object[]{context};
            return (BasePreferenceController) preferenceConstructor.newInstance(params);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Invalid preference controller: " + controllerName, e);
        }
    }

    public BasePreferenceController(Context context, String preferenceKey) {
        super(context);
        mPreferenceKey = preferenceKey;
        if (TextUtils.isEmpty(mPreferenceKey)) {
            throw new IllegalArgumentException("Preference key must be set");
        }
    }

    /**
     * @return {@link AvailabilityStatus} for the Setting. This status is used to determine if the
     * Setting should be shown or disabled in Settings. Further, it can be used to produce
     * appropriate error / warning Slice in the case of unavailability.
     * </p>
     * The status is used for the convenience methods: {@link #isAvailable()},
     * {@link #isSupported()}
     * </p>
     * The inherited class doesn't need to check work profile if
     * android:forWork="true" is set in preference xml.
     */
    @AvailabilityStatus
    public abstract int getAvailabilityStatus();

    @Override
    public String getPreferenceKey() {
        return mPreferenceKey;
    }

    /**
     * @return {@code true} when the controller can be changed on the device.
     *
     * <p>
     * Will return true for {@link #AVAILABLE} and {@link #DISABLED_DEPENDENT_SETTING}.
     * <p>
     * When the availability status returned by {@link #getAvailabilityStatus()} is
     * {@link #DISABLED_DEPENDENT_SETTING}, then the setting will be disabled by default in the
     * DashboardFragment, and it is up to the {@link BasePreferenceController} to enable the
     * preference at the right time.
     * <p>
     * This function also check if work profile is existed when android:forWork="true" is set for
     * the controller in preference xml.
     * TODO (mfritze) Build a dependency mechanism to allow a controller to easily define the
     * dependent setting.
     */
    @Override
    public final boolean isAvailable() {
        final int availabilityStatus = getAvailabilityStatus();
        return (availabilityStatus == AVAILABLE
                || availabilityStatus == AVAILABLE_UNSEARCHABLE
                || availabilityStatus == DISABLED_DEPENDENT_SETTING);
    }

    /**
     * @return {@code false} if the setting is not applicable to the device. This covers both
     * settings which were only introduced in future versions of android, or settings that have
     * hardware dependencies.
     * </p>
     * Note that a return value of {@code true} does not mean that the setting is available.
     */
    public final boolean isSupported() {
        return getAvailabilityStatus() != UNSUPPORTED_ON_DEVICE;
    }

    /**
     * Displays preference in this controller.
     */
    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (getAvailabilityStatus() == DISABLED_DEPENDENT_SETTING) {
            // Disable preference if it depends on another setting.
            final Preference preference = screen.findPreference(getPreferenceKey());
            if (preference != null) {
                preference.setEnabled(false);
            }
        }
    }

    /**
     * Updates non-indexable keys for search provider.
     *
     * Called by SearchIndexProvider#getNonIndexableKeys
     */
    public void updateNonIndexableKeys(List<String> keys) {
        final boolean shouldSuppressFromSearch = !isAvailable()
                || getAvailabilityStatus() == AVAILABLE_UNSEARCHABLE;
        if (shouldSuppressFromSearch) {
            final String key = getPreferenceKey();
            if (TextUtils.isEmpty(key)) {
                Log.w(TAG, "Skipping updateNonIndexableKeys due to empty key " + toString());
                return;
            }
            if (keys.contains(key)) {
                Log.w(TAG, "Skipping updateNonIndexableKeys, key already in list. " + toString());
                return;
            }
            keys.add(key);
        }
    }

    /**
     * Set {@link UiBlockListener}
     *
     * @param uiBlockListener listener to set
     */
    public void setUiBlockListener(UiBlockListener uiBlockListener) {
        mUiBlockListener = uiBlockListener;
    }

    /**
     * Listener to invoke when background job is finished
     */
    public interface UiBlockListener {
        /**
         * To notify client that UI related background work is finished.
         * (i.e. Slice is fully loaded.)
         *
         * @param controller Controller that contains background work
         */
        void onBlockerWorkFinished(BasePreferenceController controller);
    }

    /**
     * Used for {@link BasePreferenceController} to decide whether it is ui blocker.
     * If it is, entire UI will be invisible for a certain period until controller
     * invokes {@link UiBlockListener}
     *
     * This won't block UI thread however has similar side effect. Please use it if you
     * want to avoid janky animation(i.e. new preference is added in the middle of page).
     *
     * This must be used in {@link BasePreferenceController}
     */
    public interface UiBlocker {
    }

    /**
     * Set the metrics category of the parent fragment.
     *
     * Called by DashboardFragment#onAttach
     */
    public void setMetricsCategory(int metricsCategory) {
        mMetricsCategory = metricsCategory;
    }

    /**
     * @return the metrics category of the parent fragment.
     */
    protected int getMetricsCategory() {
        return mMetricsCategory;
    }
}
