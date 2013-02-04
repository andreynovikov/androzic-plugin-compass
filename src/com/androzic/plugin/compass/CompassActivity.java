/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.plugin.compass;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.WindowManager;

public class CompassActivity extends Activity implements SensorEventListener, OnSharedPreferenceChangeListener
{
	private SensorManager sensorManager = null;

	private float[] magneticValues;
	private float[] accelerometerValues;

	private float[] matrixR;
	private float[] matrixI;
	private float[] matrixValues;
	private float[] matrixRemappedR;

	private int x;
	private int y;

	private float azimuth = 0.0f;
	private float pitch = 0.0f;
	private float roll = 0.0f;

	private CompassView compassView;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.act_compass);

		matrixR = new float[9];
		matrixI = new float[9];
		matrixValues = new float[3];
		matrixRemappedR = new float[9];

		x = SensorManager.AXIS_X;
		y = SensorManager.AXIS_Y;

		compassView = (CompassView) findViewById(R.id.compass);

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (sensorManager != null)
		{
			Sensor acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			Sensor mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			if (acc != null && mag != null)
			{
				sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
				sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
			}
		}

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_compass_smooth));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_compass_rotateface));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_compass_disableorientation));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		switch (display.getOrientation())
		{
			case Surface.ROTATION_0:
				x = SensorManager.AXIS_X;
				y = SensorManager.AXIS_Y;
				break;
			case Surface.ROTATION_90:
				x = SensorManager.AXIS_Y;
				y = SensorManager.AXIS_MINUS_X;
				break;
			case Surface.ROTATION_180:
				x = SensorManager.AXIS_X;
				y = SensorManager.AXIS_MINUS_Y;
				break;
			case Surface.ROTATION_270:
				x = SensorManager.AXIS_MINUS_Y;
				y = SensorManager.AXIS_MINUS_X;
				break;
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (sensorManager != null)
		{
			sensorManager.unregisterListener(this);
			sensorManager = null;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
			return;

		switch (event.sensor.getType())
		{
			case Sensor.TYPE_MAGNETIC_FIELD:
				magneticValues = event.values.clone();
				break;
			case Sensor.TYPE_ACCELEROMETER:
				accelerometerValues = event.values.clone();
				break;
		}

		if (magneticValues != null && accelerometerValues != null)
		{
			if (SensorManager.getRotationMatrix(matrixR, matrixI, accelerometerValues, magneticValues))
			{
				if (SensorManager.remapCoordinateSystem(matrixR, x, y, matrixRemappedR))
				{
					SensorManager.getOrientation(matrixRemappedR, matrixValues);

					azimuth = (float) Math.toDegrees(matrixValues[0]);
					pitch = (float) Math.toDegrees(matrixValues[1]);
					roll = (float) Math.toDegrees(matrixValues[2]);
					
					// Sometimes azimuth becomes negative
					if (azimuth < 0)
						azimuth += 360.;
					while (azimuth > 360.)
						azimuth -= 360.;

					compassView.setAzimuth(azimuth);
					compassView.setPitch(pitch);
				}
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.preferences, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuPreferences:
				startActivity(new Intent(this, Preferences.class));
				return true;
		}
		return false;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key.equals(getString(R.string.pref_compass_smooth)))
		{
			compassView.setSmothing(sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_smooth)));
		}
		if (key.equals(getString(R.string.pref_compass_rotateface)))
		{
			compassView.setFaceRotation(sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_rotateface)));
		}
		if (key.equals(getString(R.string.pref_compass_disableorientation)))
		{
			if (sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_disableorientation)))
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			else
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		}
	}
}
