/*
 * Copyright (C) 2018 The Potato Open Sauce Project
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

package com.extra.settings.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference.OnPreferenceChangeListener;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Color;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.PreferenceFragment;
import android.util.Log;

import com.android.settings.R;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.wrapper.OverlayManagerWrapper;
import com.android.settings.wrapper.OverlayManagerWrapper.OverlayInfo;
import com.extra.settings.preferences.CustomSeekBarPreference;
import com.extra.settings.preferences.SecureSettingSwitchPreference;

import java.util.ArrayList;
import java.util.List;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

public class ThemeFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener, Indexable {

    private static final String KEY_ACCENT_PICKER = "accent_picker";
    private Preference mSystemThemeColor;
    private Fragment mCurrentFragment = this;
    private OverlayManagerWrapper mOverlayService;
    private PackageManager mPackageManager;

    private static final String QS_PANEL_ALPHA = "qs_panel_alpha";
    private static final String QS_PANEL_COLOR = "qs_panel_color";

    private Handler mHandler;

    private CustomSeekBarPreference mQsPanelAlpha;
    private ColorPickerPreference mQsPanelColor;
    private int mQsPanelAlphaValue;
    private boolean mChangeQsPanelAlpha = true;

    private static final String SYSUI_ROUNDED_SIZE = "sysui_rounded_size";
    private static final String SYSUI_ROUNDED_CONTENT_PADDING = "sysui_rounded_content_padding";
    private static final String SYSUI_ROUNDED_FWVALS = "sysui_rounded_fwvals";

    private CustomSeekBarPreference mCornerRadius;
    private CustomSeekBarPreference mContentPadding;
    private SecureSettingSwitchPreference mRoundedFwvals;

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mQsPanelAlpha) {
            mQsPanelAlphaValue = (Integer) newValue;
            if (!mChangeQsPanelAlpha)
                return true;
            mChangeQsPanelAlpha = false;
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.QS_PANEL_BG_ALPHA, mQsPanelAlphaValue,
                    UserHandle.USER_CURRENT);
            mHandler.postDelayed(() -> {
                    Settings.System.putIntForUser(getContentResolver(),
                            Settings.System.QS_PANEL_BG_ALPHA, mQsPanelAlphaValue,
                            UserHandle.USER_CURRENT);
                    mChangeQsPanelAlpha = true;
                }, 1000);
        } else if (preference == mQsPanelColor) {
            int bgColor = (Integer) newValue;
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.QS_PANEL_BG_COLOR, bgColor,
                    UserHandle.USER_CURRENT);
        } else if (preference == mCornerRadius) {
            Settings.Secure.putInt(getContext().getContentResolver(), Settings.Secure.SYSUI_ROUNDED_SIZE,
                    ((int) newValue) * 1);
        } else if (preference == mContentPadding) {
            Settings.Secure.putInt(getContext().getContentResolver(), Settings.Secure.SYSUI_ROUNDED_CONTENT_PADDING,
                    ((int) newValue) * 1);
        } else if (preference == mRoundedFwvals) {
            restoreCorners();
        }
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.theme);

        Resources res = null;
        Context ctx = getContext();
        float density = Resources.getSystem().getDisplayMetrics().density;

        try {
            res = ctx.getPackageManager().getResourcesForApplication("com.android.systemui");
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        // Rounded Corner Radius
        mCornerRadius = (CustomSeekBarPreference) findPreference(SYSUI_ROUNDED_SIZE);
        mCornerRadius.setOnPreferenceChangeListener(this);
        int resourceIdRadius = res.getIdentifier("com.android.systemui:dimen/rounded_corner_radius", null, null);
        int cornerRadius = Settings.Secure.getInt(ctx.getContentResolver(), Settings.Secure.SYSUI_ROUNDED_SIZE,
                (int) (res.getDimension(resourceIdRadius) / density));
        mCornerRadius.setValue(cornerRadius / 1);

        // Rounded Content Padding
        mContentPadding = (CustomSeekBarPreference) findPreference(SYSUI_ROUNDED_CONTENT_PADDING);
        mContentPadding.setOnPreferenceChangeListener(this);
        int resourceIdPadding = res.getIdentifier("com.android.systemui:dimen/rounded_corner_content_padding", null,
                null);
        int contentPadding = Settings.Secure.getInt(ctx.getContentResolver(),
                Settings.Secure.SYSUI_ROUNDED_CONTENT_PADDING,
                (int) (res.getDimension(resourceIdPadding) / density));
        mContentPadding.setValue(contentPadding / 1);

        // Rounded use Framework Values
        mRoundedFwvals = (SecureSettingSwitchPreference) findPreference(SYSUI_ROUNDED_FWVALS);
        mRoundedFwvals.setOnPreferenceChangeListener(this);

        mSystemThemeColor = (Preference) findPreference(KEY_ACCENT_PICKER);

        // OMS and PMS setup
        mOverlayService = ServiceManager.getService(Context.OVERLAY_SERVICE) != null ? new OverlayManagerWrapper()
                : null;
        mPackageManager = getActivity().getPackageManager();
        String currentPkg = getTheme();
        CharSequence label = null;
        try {
            label = mPackageManager.getApplicationInfo(currentPkg, 0).loadLabel(mPackageManager);
        } catch (PackageManager.NameNotFoundException e) {
            label = currentPkg;
        }
        mSystemThemeColor.setSummary(label);
        mHandler = new Handler();
        setupQsPrefs();
    }

    private void restoreCorners() {
        Resources res = null;
        float density = Resources.getSystem().getDisplayMetrics().density;

        try {
            res = getContext().getPackageManager().getResourcesForApplication("com.android.systemui");
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        int resourceIdRadius = res.getIdentifier("com.android.systemui:dimen/rounded_corner_radius", null, null);
        int resourceIdPadding = res.getIdentifier("com.android.systemui:dimen/rounded_corner_content_padding", null,
                null);
        mCornerRadius.setValue((int) (res.getDimension(resourceIdRadius) / density));
        mContentPadding.setValue((int) (res.getDimension(resourceIdPadding) / density));
    }

    private void setupQsPrefs() {
        mQsPanelAlpha = (CustomSeekBarPreference) findPreference(QS_PANEL_ALPHA);
        int qsPanelAlpha = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.QS_PANEL_BG_ALPHA, 255, UserHandle.USER_CURRENT);
        mQsPanelAlpha.setValue(qsPanelAlpha);
        mQsPanelAlpha.setOnPreferenceChangeListener(this);

        mQsPanelColor = (ColorPickerPreference) findPreference(QS_PANEL_COLOR);
        int QsColor = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.QS_PANEL_BG_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mQsPanelColor.setNewPreviewColor(QsColor);
        mQsPanelColor.setOnPreferenceChangeListener(this);
    }

    public void updateEnableState() {
        if (mSystemThemeColor == null) {
            return;
        }
        mSystemThemeColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AccentPicker.show(mCurrentFragment, preference);
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateEnableState();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider() {

        @Override
        public List<SearchIndexableResource> getXmlResourcesToIndex(Context context, boolean enabled) {
            List<SearchIndexableResource> indexables = new ArrayList<>();
            SearchIndexableResource indexable = new SearchIndexableResource(context);
            indexable.xmlResId = R.xml.theme;
            indexables.add(indexable);
            return indexables;
        }

        @Override
        public List<String> getNonIndexableKeys(Context context) {
            List<String> keys = super.getNonIndexableKeys(context);
            return keys;
        }
    };

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CUSTOM_SETTINGS;
    }

    private String getTheme() {
        List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android", UserHandle.myUserId());
        for (int i = 0, size = infos.size(); i < size; i++) {
            if (infos.get(i).isEnabled() && isTheme(infos.get(i))) {
                return infos.get(i).packageName;
            }
        }
        return null;
    }

    private boolean isTheme(OverlayInfo oi) {
        if (!OverlayInfo.CATEGORY_THEME.equals(oi.category)) {
            return false;
        }
        try {
            PackageInfo pi = mPackageManager.getPackageInfo(oi.packageName, 0);
            return pi != null && !pi.isStaticOverlayPackage();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
