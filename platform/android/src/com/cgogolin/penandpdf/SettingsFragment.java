package com.cgogolin.penandpdf;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
//import android.preference.Preference;

import android.preference.ListPreference;

//public static class SettingsFragment extends PreferenceFragment {
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

            //This fixes onSharedPreferencesChanged
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(SettingsActivity.SHARED_PREFERENCES_STRING);
        preferenceManager.setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
        
            // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        ListPreference prefInkColor = (ListPreference) findPreference("pref_ink_color");
        ListPreference prefHighlightColor = (ListPreference) findPreference("pref_highlight_color");
        ListPreference prefUnderlineColor = (ListPreference) findPreference("pref_underline_color");
        ListPreference prefStrikeOutColor = (ListPreference) findPreference("pref_strikeout_color");    
        
        prefInkColor.setEntries(ColorPalette.getColorNames());
        prefInkColor.setEntryValues(ColorPalette.getColorNumbers());
        prefHighlightColor.setEntries(ColorPalette.getColorNames());
        prefHighlightColor.setEntryValues(ColorPalette.getColorNumbers());
        prefUnderlineColor.setEntries(ColorPalette.getColorNames());
        prefUnderlineColor.setEntryValues(ColorPalette.getColorNumbers());
        prefStrikeOutColor.setEntries(ColorPalette.getColorNames());
        prefStrikeOutColor.setEntryValues(ColorPalette.getColorNumbers());
    }
}
