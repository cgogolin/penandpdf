package com.cgogolin.penandpdf;

import android.os.Bundle;
import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

public class SettingsActivity extends android.support.v7.app.AppCompatActivity {
    final static String PREF_USE_STYLUS = "pref_use_stylus";
    final static String PREF_SCROLL_VERTICAL = "pref_scroll_vertical";
    final static String PREF_SCROLL_CONTINUOUS = "pref_scroll_continuous";
    final static String PREF_FIT_WIDTH = "pref_fit_width";
    final static String PREF_INK_THICKNESS = "pref_ink_thickness";
    final static String PREF_ERASER_THICKNESS = "pref_eraser_thickness";
    final static String PREF_INK_COLOR = "pref_ink_color";
    final static String PREF_HIGHLIGHT_COLOR = "pref_highlight_color";
    final static String PREF_UNDERLINE_COLOR = "pref_underline_color";
    final static String PREF_STRIKEOUT_COLOR = "pref_strikeout_color";
    final static String PREF_TEXTANNOTICON_COLOR = "pref_textannoticon_color";
    
    final static String PREF_SAVE_ON_DESTROY = "pref_save_on_destroy";
    final static String PREF_SAVE_ON_STOP = "pref_save_on_stop";
    final static String PREF_SMART_TEXT_SELECTION = "pref_smart_text_selection";
    final static String PREF_KEEP_SCREEN_ON = "keep_screen_on";

	final static String PREF_EXPERIMENTAL_MODE = "experimental_mode";
	
    final static String SHARED_PREFERENCES_STRING = "PenAndPDF";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings);
        Toolbar myToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        String title = getString(R.string.app_name);
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
		if(actionBar != null){
			actionBar.setTitle(title);
//			actionBar.setSubtitle(subtitle);
		}
        
        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
//                .replace(android.R.id.content, new SettingsFragment())
            .add(R.id.scroll_view, new SettingsFragment())
            .commit();
    }

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
	}
}
