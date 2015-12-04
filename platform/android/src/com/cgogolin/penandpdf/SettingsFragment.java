package com.cgogolin.penandpdf;

import android.util.TypedValue;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.widget.ListView;
import android.view.View;
import android.view.ViewGroup;
import android.preference.ListPreference;
import android.os.Bundle;
import android.view.LayoutInflater;


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

        ListPreference prefInkColor = (ListPreference) findPreference(SettingsActivity.PREF_INK_COLOR);
        ListPreference prefHighlightColor = (ListPreference) findPreference(SettingsActivity.PREF_HIGHLIGHT_COLOR);
        ListPreference prefUnderlineColor = (ListPreference) findPreference(SettingsActivity.PREF_UNDERLINE_COLOR);
        ListPreference prefStrikeOutColor = (ListPreference) findPreference(SettingsActivity.PREF_STRIKEOUT_COLOR);
        ListPreference prefTextAnnotIconColor = (ListPreference) findPreference(SettingsActivity.PREF_TEXTANNOTICON_COLOR);    
        
        prefInkColor.setEntries(ColorPalette.getColorNames());
        prefInkColor.setEntryValues(ColorPalette.getColorNumbers());
        prefHighlightColor.setEntries(ColorPalette.getColorNames());
        prefHighlightColor.setEntryValues(ColorPalette.getColorNumbers());
        prefUnderlineColor.setEntries(ColorPalette.getColorNames());
        prefUnderlineColor.setEntryValues(ColorPalette.getColorNumbers());
        prefStrikeOutColor.setEntries(ColorPalette.getColorNames());
        prefStrikeOutColor.setEntryValues(ColorPalette.getColorNumbers());
        prefTextAnnotIconColor.setEntries(ColorPalette.getColorNames());
        prefTextAnnotIconColor.setEntryValues(ColorPalette.getColorNumbers());
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if(view != null) {
            ListView listView = (ListView) view.findViewById(android.R.id.list);
            if(listView != null){
                TypedValue tv = new TypedValue();
                if(getActivity().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                    int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
                    listView.setPadding(0, actionBarHeight, 0, 0);
                    listView.setClipToPadding(false);   
                }
            }
        }
        return view;
      // android:paddingTop="?attr/actionBarSize"
      //       android:clipToPadding="false"
    }
    

	// 	//Hack to work around a bug in how theme are apllied in PreferenceFragement, also see:
	// 	//http://stackoverflow.com/questions/2615528/preferenceactivity-and-theme-not-applying
	// @Override
	// public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
	// 									 Preference preference) {
	// 	super.onPreferenceTreeClick(preferenceScreen, preference);
	// 	if (preference != null) {
	// 		if (preference instanceof PreferenceScreen) {
	// 			if (((PreferenceScreen) preference).getDialog() != null) {
	// 				((PreferenceScreen) preference)
    //                     .getDialog()
    //                     .getWindow()
    //                     .getDecorView()
    //                     .setBackgroundDrawable(
	// 						getActivity()
	// 						.getWindow()
	// 						.getDecorView()
	// 						.getBackground()
	// 						.getConstantState()
	// 						.newDrawable()
	// 										   );
	// 			}
	// 		}
	// 		if (preference instanceof ListPreference) {
	// 			if (((ListPreference) preference).getDialog() != null) {
	// 				((ListPreference) preference)
    //                     .getDialog()
    //                     .getWindow()
    //                     .getDecorView()
    //                     .setBackgroundDrawable(
	// 						getActivity()
	// 						.getWindow()
	// 						.getDecorView()
	// 						.getBackground()
	// 						.getConstantState()
	// 						.newDrawable()
	// 										   );
	// 			}
	// 		}
	// 	}
	// 	return false;
	// }
}
