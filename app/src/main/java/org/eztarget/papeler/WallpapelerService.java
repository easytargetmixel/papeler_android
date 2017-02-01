package org.eztarget.papeler;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

/**
 * Created by michelsievers on 23/01/2017.
 */

public class WallpapelerService extends WallpaperService {

    private static final String TAG = WallpapelerService.class.getSimpleName();

    private static final long MAX_TOUCH_AGE_MILLIS = 3L * 1000L;

    private static final float MAX_TOUCH_AGE_FLOATIES = (float) MAX_TOUCH_AGE_MILLIS;

    private static final int DENSITY = 5;

    private static final boolean VERBOSE = false;

    @Override
    public Engine onCreateEngine() {
        Log.d(TAG, "onCreateEngine()");
        return new Pengine();
    }

    private class Pengine extends Engine {


        private final Handler mHandler = new Handler();

        private final Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
//                if (VERBOSE) {
//                    Log.d(TAG, "mDrawRunnable.run()");
//                }
                draw();
            }

        };

//        private List<Drop> mPoints;

        private Drop mDrop = new Drop("", -1000f, -1000f);

        private Paint mPaint = new Paint();

        private int mWidth;

        private int mHeight;

        private float mSpread;

        private float mSpreadHalf;

        private boolean mVisible = true;

        private boolean mTouching = false;

        private long mLastTouchMillis;

        private boolean mDrawing = false;

        private long mFirstTouchMillis;

        private int mMaxNumber;

        Pengine() {

            if (VERBOSE) {
                Log.d(TAG, "Pengine()");
            }

            final SharedPreferences prefs;
            prefs = PreferenceManager.getDefaultSharedPreferences(WallpapelerService.this);

            mMaxNumber = Integer.valueOf(prefs.getString("numberOfCircles", "4"));
//            mHandleTouch = prefs.getBoolean("touch", false);

            mPaint.setAntiAlias(true);
            mPaint.setColor(Color.WHITE);
//            mPaint.setStyle(Paint.Style.STROKE);
//            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setStrokeWidth(1f);
            mHandler.post(mDrawRunnable);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;

            if (VERBOSE) {
                Log.d(TAG, "Pengine.onVisibilityChanged(" + mVisible + ")");
            }

            if (mVisible) {
                mHandler.post(mDrawRunnable);
            } else {
                mHandler.removeCallbacks(mDrawRunnable);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);

            Log.d(TAG, "Pengine.onSurfaceDestroyed(" + mVisible + ")");

            mVisible = false;
            mHandler.removeCallbacks(mDrawRunnable);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            mWidth = width;
            mSpread = mWidth * 0.1f;
            mSpreadHalf = mSpread * 0.5f;
            mHeight = height;
//            mPaint.setStrokeWidth(mWidth * 0.01f);

            if (VERBOSE) {
                Log.d(TAG, "New Surface: " + format + ": " + mWidth + " x " + mHeight);
            }

            super.onSurfaceChanged(holder, format, mWidth, mHeight);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                if (mFirstTouchMillis < 100L) {
                    mFirstTouchMillis = System.currentTimeMillis();
                }

                mTouching = true;
                mLastTouchMillis = System.currentTimeMillis();

                scheduleDraw();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mTouching = false;
                return;
            }

            mDrop.x = event.getX();
            mDrop.y = event.getY();

            super.onTouchEvent(event);
        }

        private void draw() {

            if (mDrawing) {
                return;
            }

            mDrawing = true;

            if (VERBOSE) {
                Log.d(TAG, "Pengine.draw()");
            }

            final SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            final long now = System.currentTimeMillis();
//            mPaint.setColor((int) (now - mFirstTouchMillis));
            mPaint.setAlpha((int) (100 * getAgeFactor()));

            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    drawDrop(canvas);
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
                mDrawing = false;
            }

            scheduleDraw();
        }

        private void scheduleDraw() {

            if (VERBOSE) {
                Log.d(TAG, "Pengine.scheduleDraw()");
            }

            mHandler.removeCallbacks(mDrawRunnable);

            if (mVisible && getAgeFactor() > 0.1f) {
                mHandler.postDelayed(mDrawRunnable, 30L);
            }
        }

        private float getAgeFactor() {

            if (mTouching) {
                return 1f;
            }

            final long ageMillis =  System.currentTimeMillis() - mLastTouchMillis;

            if (ageMillis >= MAX_TOUCH_AGE_MILLIS) {
                if (VERBOSE) {
                    Log.d(TAG, "Pengine.getAgeFactor() -> 0");
                }
                return 0f;
            } else {
                if (VERBOSE) {
                    Log.d(TAG, "Pengine.getAgeFactor() -> " + (1f - (ageMillis / MAX_TOUCH_AGE_FLOATIES)));
                }
                return 1f - (ageMillis / MAX_TOUCH_AGE_FLOATIES);
            }
        }

        private void drawDrop(Canvas canvas) {
//            canvas.drawCircle(
//                    mDrop.x,
//                    mDrop.y,
//                    (float) Math.random() * mWidth * 0.05f,
//                    mPaint
//            );

//            Log.d(TAG, "drawDrop " + mDrop.x + ", " + mDrop.y);

//            final float spread = (float) Math.random() * mWidth * 0.1f;
            float xDistance;
            float yDistance;
            for (int i = 0; i < DENSITY; i++) {
                xDistance = ((float) Math.random() * mSpread);
                yDistance = ((float) Math.random() * mSpread) * (xDistance / mSpread);
                canvas.drawPoint(
                        mDrop.x + xDistance - mSpreadHalf,
                        mDrop.y + yDistance - mSpreadHalf,
                        mPaint
                );
            }
        }

        // Surface view requires that all elements are drawn completely
//        private void drawCircles(Canvas canvas) {
////            canvas.drawColor(Color.BLACK);
//            if (mPoints.size() > 100) {
//                mPoints.clear();
//            }
//
//            for (final Drop point : mPoints) {
//                canvas.drawCircle(
//                        point.x,
//                        point.y,
//                        (float) Math.random() * mWidth * 0.1f,
//                        mPaint
//                );
//            }
//        }
    }
}