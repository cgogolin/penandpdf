package com.artifex.mupdfdemo;

import android.os.Bundle;
import android.app.Activity;

public class SettingsActivity extends Activity {
    final static String PREF_USE_STYLUS = "pref_use_stylus";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
}
