package com.cgogolin.penandpdf;

/**
 * Taken from:
 *
 * https://stackoverflow.com/questions/13496099/any-simple-way-to-require-an-edittextpreference-value-to-not-be-blank-in-android
 *
 */

import android.content.Context;
import android.util.AttributeSet;
import android.preference.EditTextPreference;
//import android.support.annotation.RequiresApi;



public class NonEmptyEditTextPreference extends EditTextPreference {

//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public NonEmptyEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NonEmptyEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NonEmptyEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NonEmptyEditTextPreference(Context context) {
        super(context);
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        boolean valid = this.getEditText().getText().toString().length() > 0;
        super.onDialogClosed(valid);
    }

}
