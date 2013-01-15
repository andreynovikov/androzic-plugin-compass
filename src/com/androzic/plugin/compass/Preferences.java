package com.androzic.plugin.compass;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.androzic.ui.SeekbarPreference;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);
	}

    @Override
	public void onResume()
    {
        super.onResume();
        initSummaries(getPreferenceScreen());
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
	public void onPause()
    {
    	super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        Preference pref = findPreference(key);
       	setPrefSummary(pref);
    }

    private void setPrefSummary(Preference pref)
	{
        if (pref instanceof ListPreference)
        {
	        CharSequence summary = ((ListPreference) pref).getEntry();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
        else if (pref instanceof EditTextPreference)
        {
	        CharSequence summary = ((EditTextPreference) pref).getText();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
        else if (pref instanceof SeekbarPreference)
        {
	        CharSequence summary = ((SeekbarPreference) pref).getText();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
	}

	private void initSummaries(PreferenceGroup preference)
    {
    	for (int i=preference.getPreferenceCount()-1; i>=0; i--)
    	{
    		Preference pref = preference.getPreference(i);
           	setPrefSummary(pref);

    		if (pref instanceof PreferenceGroup || pref instanceof PreferenceScreen)
            {
    			initSummaries((PreferenceGroup) pref);
            }
    	}
    }
}
