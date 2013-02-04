/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import com.androzic.library.R;
import com.androzic.util.easing.Easing;
import com.androzic.util.easing.QuinticInOut;

public class CompassView extends View
{
	private static final int MAX_WIDTH = 1000;
	private int width = 220;
	private float scale = 1;

	private static final float INSTRUMENTAL_ERROR = 1.f;
	private static final float NEEDLE_PRECISION = 0.01f;
	private static final long AZIMUTH_ANIMATION_DURATION = 4000;
	private static final long PITCH_ANIMATION_DURATION = 10000;

	private Bitmap compassArrow;

	private Paint borderPaint;
	private Paint scalePaint;
	private Paint textPaint;
	private RectF rect30;
	private RectF rect10;
	private RectF rect5;

	private float azimuth;
	private float pitch;

	private boolean isSmooth;
	private boolean rotateFace;

	private Easing animation;

	private long azimuthAnimationStart;
	private float azimuthAnimationDuration;
	private float azimuthAnimationReference;
	private float azimuthRealTime;
	private float azimuthTurn;

	private long pitchAnimationStart;
	private float pitchAnimationDuration;
	private float pitchAnimationReference;
	private float pitchRealTime;
	private float pitchTurn;

	public CompassView(Context context)
	{
		super(context);
		initialize();
	}

	public CompassView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initialize();
	}

	public CompassView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initialize();
	}

	private void initialize()
	{
		borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		borderPaint.setStyle(Style.STROKE);
		borderPaint.setColor(Color.DKGRAY);
		borderPaint.setStrokeWidth(5);
		scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		scalePaint.setStyle(Style.FILL);
		scalePaint.setColor(Color.LTGRAY);
		scalePaint.setStrokeWidth(1);
		textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
		textPaint.setAntiAlias(true);
		textPaint.setStrokeWidth(1);
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setTextAlign(Align.CENTER);
		textPaint.setTextSize(35);
		textPaint.setTypeface(Typeface.DEFAULT);
		textPaint.setColor(Color.LTGRAY);

		compassArrow = BitmapFactory.decodeResource(getResources(), R.drawable.compass_needle);

		azimuth = 0;
		pitch = 0;
		azimuthRealTime = 0;
		pitchRealTime = 0;
		animation = new QuinticInOut();

		isSmooth = true;
		rotateFace = false;

		setSaveEnabled(true);
	}

	public void setSmothing(boolean smoothing)
	{
		isSmooth = smoothing;
	}


	public void setFaceRotation(boolean rotateFace)
	{
		this.rotateFace = rotateFace;
	}

	public void setAzimuth(float azimuth)
	{
		if (Math.abs(azimuth - azimuthRealTime) < INSTRUMENTAL_ERROR)
			return;

		this.azimuth = azimuth;

		if (isSmooth)
		{
			calcAzimuthRotation();
		}
		else
		{
			azimuthRealTime = azimuth;
			postInvalidate();
		}
	}

	private void calcAzimuthRotation()
	{
		if (azimuth == azimuthRealTime)
			return;

		float t = azimuth - azimuthRealTime;
		float absT = Math.abs(t);

		if (absT < NEEDLE_PRECISION)
		{
			azimuthRealTime = azimuth;
			azimuthAnimationStart = 0;
			postInvalidate();
			return;
		}
		
		if (absT > 180)
		{
			t = t - Math.signum(t) * 360;
			absT = Math.abs(t);
		}

		long sysTime = (long) (System.nanoTime() * 10E-6);
		float time = sysTime - azimuthAnimationStart;

		// We were not moving
		if (azimuthAnimationStart == 0)
		{
			time = 0;
			azimuthAnimationStart = sysTime;
			azimuthAnimationDuration = AZIMUTH_ANIMATION_DURATION;
			azimuthTurn = t;
			azimuthAnimationReference = azimuthRealTime;
		}
		// We need to change direction
		else if (Math.signum(t) != Math.signum(azimuthTurn))
		{
			// We are accelerating
			if (time < azimuthAnimationDuration / 2)
			{
				// Start deceleration 
				float tt = time;
				time = azimuthAnimationDuration - time;
				azimuthTurn *= time / azimuthAnimationDuration;
				azimuthAnimationDuration = tt;
				azimuthAnimationStart = (long) (sysTime - time);
				azimuthAnimationReference = azimuthRealTime;
			}
			// We were decelerating
			else if (time > azimuthAnimationDuration)
			{
				azimuthAnimationStart = 0;
				calcAzimuthRotation();
				return;
			}
		}
		else
		{
			float t2 = azimuth - azimuthAnimationReference;
			if (Math.abs(t2) > 180)
				t2 = t2 - Math.signum(t2) * 360;
			float r = t2 / azimuthTurn;

			if (r > 1.)
			{
				azimuthTurn = t2;
			}
		}

		if (time > azimuthAnimationDuration)
			time = azimuthAnimationDuration;
		
		if (Math.abs(azimuthTurn) > NEEDLE_PRECISION)
			azimuthRealTime = animation.ease(time, azimuthAnimationReference, azimuthTurn, azimuthAnimationDuration);
		if (azimuthRealTime < 0)
		{
			azimuthRealTime += 360.;
			azimuthAnimationReference += 360.;
		}
		else if (azimuthRealTime >= 360.)
		{
			azimuthRealTime -= 360.;
			azimuthAnimationReference -= 360.;
		}
		if (time >= azimuthAnimationDuration)
			azimuthAnimationStart = 0;

		postInvalidate();
	}

	public void setPitch(float pitch)
	{
		if (Math.abs(pitch - pitchRealTime) < INSTRUMENTAL_ERROR)
			return;

		this.pitch = pitch;

		if (isSmooth)
		{
			calcPitchRotation();
		}
		else
		{
			pitchRealTime = pitch;
			postInvalidate();
		}
	}

	private void calcPitchRotation()
	{
		if (pitch == pitchRealTime)
			return;

		float t = pitch - pitchRealTime;
		float absT = Math.abs(t);

		if (absT < NEEDLE_PRECISION)
		{
			pitchRealTime = pitch;
			pitchAnimationStart = 0;
			postInvalidate();
			return;
		}
		
		if (absT > 180)
		{
			t = t - Math.signum(t) * 360;
			absT = Math.abs(t);
		}

		long sysTime = (long) (System.nanoTime() * 10E-6);
		float time = sysTime - pitchAnimationStart;

		// We were not moving
		if (pitchAnimationStart == 0)
		{
			time = 0;
			pitchAnimationStart = sysTime;
			pitchAnimationDuration = PITCH_ANIMATION_DURATION;
			pitchTurn = t;
			pitchAnimationReference = pitchRealTime;
		}
		// We need to change direction
		else if (Math.signum(t) != Math.signum(pitchTurn))
		{
			// We are accelerating
			if (time < pitchAnimationDuration / 2)
			{
				// Start deceleration 
				float tt = time;
				time = pitchAnimationDuration - time;
				pitchTurn *= time / pitchAnimationDuration;
				pitchAnimationDuration = tt;
				pitchAnimationStart = (long) (sysTime - time);
				pitchAnimationReference = pitchRealTime;
			}
			// We were decelerating
			else if (time > pitchAnimationDuration)
			{
				pitchAnimationStart = 0;
				calcAzimuthRotation();
				return;
			}
		}
		else
		{
			float t2 = pitch - pitchAnimationReference;
			if (Math.abs(t2) > 180)
				t2 = t2 - Math.signum(t2) * 360;
			float r = t2 / pitchTurn;

			if (r > 1.)
			{
				pitchTurn = t2;
			}
		}

		if (time > pitchAnimationDuration)
			time = pitchAnimationDuration;
		
		if (Math.abs(pitchTurn) > NEEDLE_PRECISION)
			pitchRealTime = animation.ease(time, pitchAnimationReference, pitchTurn, pitchAnimationDuration);
		if (time >= pitchAnimationDuration)
			pitchAnimationStart = 0;

		postInvalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		// int sw = MeasureSpec.getSize(widthMeasureSpec);
		// int sh = MeasureSpec.getSize(heightMeasureSpec);

		int w = getMeasurement(widthMeasureSpec, MAX_WIDTH);
		int h = getMeasurement(heightMeasureSpec, MAX_WIDTH);

		if (w > h)
		{
			width = h / 2 - 20;
		}
		else
		{
			width = w / 2 - 20;
		}
		scale = width / 220.0f;

		setMeasuredDimension(w, h);
	}

	private int getMeasurement(int measureSpec, int preferred)
	{
		int specSize = MeasureSpec.getSize(measureSpec);
		int measurement = 0;

		switch (MeasureSpec.getMode(measureSpec))
		{
			case MeasureSpec.EXACTLY:
				// This means the width of this view has been given.
				measurement = specSize;
				break;
			case MeasureSpec.AT_MOST:
				// Take the minimum of the preferred size and what we were told to be.
				measurement = Math.min(preferred, specSize);
				break;
			default:
				measurement = preferred;
				break;
		}

		return measurement;
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		int cx = getWidth();
		int cy = getHeight();

		canvas.translate(cx / 2, cy / 2);
		canvas.scale(1, (90 - Math.abs(pitchRealTime)) / 90);
		canvas.drawCircle(0, 0, width + 10 * scale, borderPaint);

		if (rotateFace)
			canvas.rotate(-azimuthRealTime);

		// scale
		for (int i = 72; i > 0; i--)
		{
			if (i % 2 == 1)
				canvas.drawRect(rect5, scalePaint);
			if (i % 6 == 0)
			{
				canvas.drawRect(rect30, scalePaint);
				if (i % 18 == 0)
				{
					String cd = i == 72 ? "N" : i == 54 ? "E" : i == 36 ? "S" : "W";
					canvas.drawText(cd, 0, -width + 80 * scale, textPaint);
				}
				else
				{
					canvas.drawText(Integer.toString((72 - i) / 2), 0, -width + 80 * scale, textPaint);
				}
			}
			else if (i % 2 == 0)
			{
				canvas.drawRect(rect10, scalePaint);
			}
			canvas.rotate(5);
		}
		
		if (! rotateFace)
			canvas.rotate(-azimuthRealTime);

		canvas.drawBitmap(compassArrow, -compassArrow.getWidth() / 2, -compassArrow.getHeight() / 2, null);

		if (isSmooth)
		{
			calcAzimuthRotation();
			calcPitchRotation();
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);

		if (w == 0 || h == 0)
			return;

		textPaint.setTextSize(35 * scale);

		rect30 = new RectF(-5, width, +5, width - 45 * scale);
		rect10 = new RectF(-2, width, +2, width - 30 * scale);
		rect5 = new RectF(-2, width, +2, width - 20 * scale);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		if (!(state instanceof SavedState))
		{
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState ss = (SavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());

		azimuth = ss.azimuth;
		pitch = ss.pitch;
		azimuthRealTime = ss.rtA;
		isSmooth = ss.isSmooth;
		rotateFace = ss.rotateFace;
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		Parcelable superState = super.onSaveInstanceState();
		SavedState ss = new SavedState(superState);

		ss.azimuth = azimuth;
		ss.pitch = pitch;
		ss.rtA = azimuthRealTime;
		ss.isSmooth = isSmooth;
		ss.rotateFace = rotateFace;
		
		return ss;
	}

	static class SavedState extends BaseSavedState
	{
		float azimuth;
		float pitch;
		float rtA;
		boolean isSmooth;
		boolean rotateFace;

		SavedState(Parcelable superState)
		{
			super(superState);
		}

		private SavedState(Parcel in)
		{
			super(in);
			this.azimuth = in.readFloat();
			this.pitch = in.readFloat();
			this.rtA = in.readFloat();
			this.isSmooth = in.readByte() == 1;
			this.rotateFace = in.readByte() == 1;
		}

		@Override
		public void writeToParcel(Parcel out, int flags)
		{
			super.writeToParcel(out, flags);
			out.writeFloat(this.azimuth);
			out.writeFloat(this.pitch);
			out.writeFloat(this.rtA);
			out.writeByte((byte) (this.isSmooth ? 1 : 0));
			out.writeByte((byte) (this.rotateFace ? 1 : 0));
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in)
			{
				return new SavedState(in);
			}

			public SavedState[] newArray(int size)
			{
				return new SavedState[size];
			}
		};
	}
}
