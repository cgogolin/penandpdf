package com.artifex.mupdfdemo;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import android.preference.ListPreference;

//public static class SettingsFragment extends PreferenceFragment {
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        ListPreference prefInkColor = (ListPreference) findPreference("pref_ink_color");
        ListPreference prefHighlightColor = (ListPreference) findPreference("pref_highlight_color");
        ListPreference prefUnderlineColor = (ListPreference) findPreference("pref_underline_color");
        
        
        prefInkColor.setEntries(ColorPalette.getColorNames());
        prefInkColor.setEntryValues(ColorPalette.getColorNumbers());
        prefHighlightColor.setEntries(ColorPalette.getColorNames());
        prefHighlightColor.setEntryValues(ColorPalette.getColorNumbers());
        prefUnderlineColor.setEntries(ColorPalette.getColorNames());
        prefUnderlineColor.setEntryValues(ColorPalette.getColorNumbers());
    }
}
