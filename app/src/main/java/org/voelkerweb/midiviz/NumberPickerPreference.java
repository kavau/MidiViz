package org.voelkerweb.midiviz;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

/**
 * A subclass of Preference that allows the user to select an integer number.
 */
// TODO: probably easier to inherit from EditTextPreference?
public class NumberPickerPreference extends DialogPreference
{
    private static final String TAG = "NumberPickerPreference";
    private static final int DEFAULT_VALUE = 0;

    // TODO: these should be parameters. We use 250 for the MAX_VALUE because this number picker
    // may be used for BPM.
    private static final int MIN_VALUE = 1;
    private static final int MAX_VALUE = 250;

    EditText editText;
    int mCurrentValue;

    public NumberPickerPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        setDialogLayoutResource(R.layout.numberpicker_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
    }

    @Override
    protected void onBindDialogView(View view)
    {
        super.onBindDialogView(view);
        Log.d(TAG, "Current value is " + mCurrentValue);
        editText = (EditText) view.findViewById(R.id.numberPickerValue);
        if (editText == null) {
            Log.e(TAG, "Cannot find EditText view!");
        } else {
            Log.e(TAG, "About to call setText");
            editText.setText(Integer.toString(mCurrentValue));
        }
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue)
    {
        Log.d(TAG, "In onSetInitialValue");
        if (restorePersistedValue) {
            mCurrentValue = this.getPersistedInt(DEFAULT_VALUE);
            Log.d(TAG, "Restored persisted value: " + mCurrentValue);
        } else {
            mCurrentValue = (Integer) defaultValue;
            persistInt(mCurrentValue);
            Log.d(TAG, "Set default value: " + mCurrentValue);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index)
    {
        int defaultValue = a.getInteger(index, DEFAULT_VALUE);
        Log.d(TAG, "onGetDefaultValue: " + defaultValue);
        return defaultValue;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult)
    {
        Log.d(TAG, "In onDialogClosed");
        if (positiveResult) {
            if (editText == null) {
                Log.e(TAG, "EditText view not available!");
            } else {
                Log.d(TAG, "getText: " + editText.getText());
                try {
                    int newValue = Integer.parseInt(editText.getText().toString());
                    if (newValue >= MIN_VALUE && newValue <= MAX_VALUE) {
                        Log.d(TAG, "Persisting " + newValue);
                        persistInt(newValue);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException: " + e);
                }
            }
        }
    }

    // TODO: save and restore state!
    // See http://developer.android.com/guide/topics/ui/settings.html
}
