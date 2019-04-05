/*
 * Copyright (C) 2019 The Evolution X Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.content.SharedPreferences;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;

import com.android.settings.R;
import com.android.internal.util.pixys.PixysUtils;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;

public class RecentsSettings extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {

    private static final String RECENTS_CLEAR_ALL_LOCATION = "recents_clear_all_location";
    private static final String RECENTS_COMPONENT_TYPE = "recents_component";
    private static final String IMMERSIVE_RECENTS = "immersive_recents"; 

    private ListPreference mRecentsClearAllLocation;
    private SwitchPreference mRecentsClearAll;
    private ListPreference mRecentsComponentType;
    private ListPreference mImmersiveRecents; 

    private SharedPreferences mPreferences; 
    private Context mContext;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.recents_settings);

        ContentResolver resolver = getActivity().getContentResolver();
        mContext = getActivity().getApplicationContext();

        // clear all recents
        mRecentsClearAllLocation = (ListPreference) findPreference(RECENTS_CLEAR_ALL_LOCATION);
        int location = Settings.System.getIntForUser(resolver,
                Settings.System.RECENTS_CLEAR_ALL_LOCATION, 3, UserHandle.USER_CURRENT);
        mRecentsClearAllLocation.setValue(String.valueOf(location));
        mRecentsClearAllLocation.setSummary(mRecentsClearAllLocation.getEntry());
        mRecentsClearAllLocation.setOnPreferenceChangeListener(this);

        // recents component type
        mRecentsComponentType = (ListPreference) findPreference(RECENTS_COMPONENT_TYPE);
        int type = Settings.System.getInt(resolver,
                Settings.System.RECENTS_COMPONENT, 0);
        mRecentsComponentType.setValue(String.valueOf(type));
        mRecentsComponentType.setSummary(mRecentsComponentType.getEntry());
        mRecentsComponentType.setOnPreferenceChangeListener(this);

        mImmersiveRecents = (ListPreference) findPreference(IMMERSIVE_RECENTS); 
        mImmersiveRecents.setValue(String.valueOf(Settings.System.getIntForUser( 
                resolver, Settings.System.IMMERSIVE_RECENTS, 0, UserHandle.USER_CURRENT))); 
        mImmersiveRecents.setSummary(mImmersiveRecents.getEntry()); 
        mImmersiveRecents.setOnPreferenceChangeListener(this); 
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mRecentsClearAllLocation) {
            int location = Integer.valueOf((String) objValue);
            int index = mRecentsClearAllLocation.findIndexOfValue((String) objValue);
            Settings.System.putIntForUser(getActivity().getContentResolver(),
                    Settings.System.RECENTS_CLEAR_ALL_LOCATION, location, UserHandle.USER_CURRENT);
            mRecentsClearAllLocation.setSummary(mRecentsClearAllLocation.getEntries()[index]);
            return true;
        } else if (preference == mRecentsComponentType) {
            int type = Integer.valueOf((String) objValue);
            int index = mRecentsComponentType.findIndexOfValue((String) objValue);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.RECENTS_COMPONENT, type);
            mRecentsComponentType.setSummary(mRecentsComponentType.getEntries()[index]);
            if (type == 1) { // Disable swipe up gesture, if oreo type selected
               Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.SWIPE_UP_TO_SWITCH_APPS_ENABLED, 0);
            }
            PixysUtils.showSystemUiRestartDialog(getContext());
            return true;
        } else if (preference == mImmersiveRecents) {
            Settings.System.putIntForUser(resolver, Settings.System.IMMERSIVE_RECENTS,
                    Integer.parseInt((String) newValue), UserHandle.USER_CURRENT);
            mImmersiveRecents.setValue((String) newValue);
            mImmersiveRecents.setSummary(mImmersiveRecents.getEntry());
            mPreferences = mContext.getSharedPreferences("recent_settings", Activity.MODE_PRIVATE);
            if (!mPreferences.getBoolean("first_info_shown", false) && newValue != null) {
                getActivity().getSharedPreferences("recent_settings", Activity.MODE_PRIVATE)
                        .edit()
                        .putBoolean("first_info_shown", true)
                        .commit();
                openAOSPFirstTimeWarning();
            }
            return true;
        }
        return false;
    }

    private void openAOSPFirstTimeWarning() { 
        new AlertDialog.Builder(getActivity()) 
                .setTitle(getResources().getString(R.string.aosp_first_time_title)) 
                .setMessage(getResources().getString(R.string.aosp_first_time_message)) 
                .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() { 
                        public void onClick(DialogInterface dialog, int whichButton) { 
                        } 
                }).show(); 
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CUSTOM_SETTINGS;
    }
}
