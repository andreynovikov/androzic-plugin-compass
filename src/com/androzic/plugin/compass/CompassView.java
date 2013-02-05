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
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.androzic.library.R;
import com.androzic.util.easing.Easing;
import com.androzic.util.easing.QuinticInOut;

public class CompassView extends SurfaceView implements SurfaceHolder.Callback
{
	/** The thread that actually draws the animation */
	private CompassThread thread;
	
	private boolean isSmooth;
	private boolean rotateFace;
	
	private float azimuth;
	private float pitch;

	class CompassThread extends Thread
	{
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

		/** Handle to the surface manager object we interact with */
		private SurfaceHolder surfaceHolder;
		/** Indicate whether the surface has been created & is ready to draw */
		private boolean mRun = false;

		private int compassWidth = 220;
		private float scale = 1;
		private int canvasWidth;
		private int canvasHeight;

		public CompassThread(SurfaceHolder surfaceHolder)
		{
			// get handles to some important objects
			this.surfaceHolder = surfaceHolder;

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

			azimuthRealTime = 0;
			pitchRealTime = 0;
			animation = new QuinticInOut();
		}

		public void setSmothing(boolean smoothing)
		{
			synchronized (surfaceHolder)
			{
				isSmooth = smoothing;
			}
		}

		public void setFaceRotation(boolean rotateFace)
		{
			synchronized (surfaceHolder)
			{
				CompassView.this.rotateFace = rotateFace;
			}
		}

		public void setAzimuth(float azimuth)
		{
			synchronized (surfaceHolder)
			{
				if (Math.abs(azimuth - azimuthRealTime) < INSTRUMENTAL_ERROR)
					return;
	
				CompassView.this.azimuth = azimuth;
	
				if (isSmooth)
					calcAzimuthRotation();
				else
					azimuthRealTime = azimuth;
			}
		}

		public void setPitch(float pitch)
		{
			synchronized (surfaceHolder)
			{
				if (Math.abs(pitch - pitchRealTime) < INSTRUMENTAL_ERROR)
					return;
	
				CompassView.this.pitch = pitch;
	
				if (isSmooth)
					calcPitchRotation();
				else
					pitchRealTime = pitch;
			}
		}

		/**
		 * Used to signal the thread whether it should be running or not.
		 * Passing true allows the thread to run; passing false will shut it
		 * down if it's already running. Calling start() after this was most
		 * recently called with false will result in an immediate shutdown.
		 * 
		 * @param b
		 *            true to run, false to shut down
		 */
		public void setRunning(boolean b)
		{
			mRun = b;
		}

		@Override
		public void run()
		{
			while (mRun)
			{
				Canvas c = null;
				try
				{
					c = surfaceHolder.lockCanvas(null);
					synchronized (surfaceHolder)
					{
						if (isSmooth)
						{
							calcAzimuthRotation();
							calcPitchRotation();
						}
						if (c != null)
							doDraw(c);
					}
				}
				finally
				{
					// do this in a finally so that if an exception is thrown
					// during the above, we don't leave the Surface in an
					// inconsistent state
					if (c != null)
					{
						surfaceHolder.unlockCanvasAndPost(c);
					}
				}
			}
		}

		/* Callback invoked when the surface dimensions change. */
		public void setSurfaceSize(int width, int height)
		{
			// synchronized to make sure these all change atomically
			synchronized (surfaceHolder)
			{
				canvasWidth = width;
				canvasHeight = height;

				if (width > height)
				{
					compassWidth = height / 2 - 20;
				}
				else
				{
					compassWidth = width / 2 - 20;
				}
				scale = compassWidth / 220.0f;

				textPaint.setTextSize(35 * scale);

				rect30 = new RectF(-5, compassWidth, +5, compassWidth - 45 * scale);
				rect10 = new RectF(-2, compassWidth, +2, compassWidth - 30 * scale);
				rect5 = new RectF(-2, compassWidth, +2, compassWidth - 20 * scale);

			}
		}

		/**
		 * Draws the ship, fuel/speed bars, and background to the provided
		 * Canvas.
		 */
		private void doDraw(Canvas canvas)
		{
			canvas.drawRGB(0, 0, 0);

			canvas.translate(canvasWidth / 2, canvasHeight / 2);
			canvas.scale(1, (90 - Math.abs(pitchRealTime)) / 90);
			canvas.drawCircle(0, 0, compassWidth + 10 * scale, borderPaint);

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
						canvas.drawText(cd, 0, -compassWidth + 80 * scale, textPaint);
					}
					else
					{
						canvas.drawText(Integer.toString((72 - i) / 2), 0, -compassWidth + 80 * scale, textPaint);
					}
				}
				else if (i % 2 == 0)
				{
					canvas.drawRect(rect10, scalePaint);
				}
				canvas.rotate(5);
			}

			if (!rotateFace)
				canvas.rotate(-azimuthRealTime);

			canvas.drawBitmap(compassArrow, -compassArrow.getWidth() / 2, -compassArrow.getHeight() / 2, null);
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
		}
	}

	public CompassView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		isSmooth = true;
		rotateFace = false;

		azimuth = 0;
		pitch = 0;
		
		// register our interest in hearing about changes to our surface
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
	}

	public CompassThread getThread()
	{
		return thread;
	}

	public void setSmothing(boolean smoothing)
	{
		if (thread != null)
			thread.setSmothing(smoothing);
		else
			isSmooth = smoothing;
	}

	public void setFaceRotation(boolean rotateFace)
	{
		if (thread != null)
			thread.setFaceRotation(rotateFace);
		else
			this.rotateFace = rotateFace;
	}

	public void surfaceCreated(SurfaceHolder holder)
	{
		// start the thread here so that we don't busy-wait in run()
		// waiting for the surface to be created
		thread = new CompassThread(holder);
		thread.setRunning(true);
		thread.start();
	}

	public void surfaceDestroyed(SurfaceHolder holder)
	{
		// we have to tell thread to shut down & wait for it to finish, or else
		// it might touch the Surface after we return and explode
		boolean retry = true;
		thread.setRunning(false);
		while (retry)
		{
			try
			{
				thread.join();
				retry = false;
			}
			catch (InterruptedException e)
			{
			}
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
        thread.setSurfaceSize(width, height);
	}

	/**
	 * Dump game state to the provided Bundle. Typically called when the
	 * Activity is being suspended.
	 * 
	 * @return Bundle with this view's state
	 */
	public Bundle saveState(Bundle map)
	{
		if (map != null)
		{
			map.putFloat("azimuth", azimuth);
			map.putFloat("pitch", pitch);
			map.putBoolean("isSmooth", isSmooth);
			map.putBoolean("rotateFace", rotateFace);
		}
		return map;
	}

	public synchronized void restoreState(Bundle savedState)
	{
		azimuth = savedState.getFloat("azimuth");
		pitch = savedState.getFloat("pitch");
		isSmooth = savedState.getBoolean("isSmooth");
		rotateFace = savedState.getBoolean("rotateFace");
	}
}
