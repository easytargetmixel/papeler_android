package org.eztarget.papeler;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.widget.Toast;

import org.eztarget.papeler.engine.Being;
import org.eztarget.papeler.engine.BeingBuilder;
import org.eztarget.papeler.engine.FoliageBuilder;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by michel@easy-target.org on 23/01/2017.
 */

public class WallpaperService extends android.service.wallpaper.WallpaperService {

    private static final String TAG = WallpaperService.class.getSimpleName();

    private static final float DEFAULT_STROKE_WIDTH = 1f;

    private static final float BLACK_STROKE_WIDTH = DEFAULT_STROKE_WIDTH;

    private static final float BRIGHTNESS_BEFORE_BLACK = 0.25f;

    private static final int BRIGHTNESS_SAMPLE_SIZE = 128;

    private static final boolean VERBOSE = BuildConfig.DEBUG && false;

    private boolean mBlur = false;

    @Override
    public android.service.wallpaper.WallpaperService.Engine onCreateEngine() {
        Log.d(TAG, "onCreateEngine()");
        return new Engine();
    }

    private class Engine extends android.service.wallpaper.WallpaperService.Engine {

        private final Handler mHandler = new Handler();

        private final Runnable mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                draw();
            }

        };

        private ArrayList<Being> mBeings = new ArrayList<>();

        private Paint mPaint = new Paint();

        private Paint mBitmapPaint = new Paint();

        private int mAlphaOffset = 0;

        private int mBackgroundColor = Color.BLACK;

        private int mWidth;

        private int mHeight;

        private boolean mVisible = true;

        private boolean mIsTouching = false;

        private BeingBuilder mBeingBuilder;

        private boolean mDrawing = false;

        private long mFirstTouchMillis;

        private boolean mResetCanvasOnce = false;

        private Bitmap mBitmap;

        private Canvas mCanvas;

        private boolean mHasBackgroundImage;

        Engine() {

            if (VERBOSE) {
                Log.d(TAG, "Engine()");
            }

            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(DEFAULT_STROKE_WIDTH);

            mBitmapPaint.setColor(Color.WHITE);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            mFirstTouchMillis = 0L;

            if (VERBOSE) {
                Log.d(TAG, "Engine.onVisibilityChanged(" + mVisible + ")");
            }

            nextStep();

            final PreferenceAccess preferences = PreferenceAccess.with(getApplicationContext());

            if (preferences.getAndUnsetIsFirstTime()) {
                final String welcomeMessage = getString(R.string.main_welcome_msg);
                Toast.makeText(WallpaperService.this, welcomeMessage, Toast.LENGTH_LONG).show();

                final String secondMessage = getString(R.string.main_welcome_msg_2);

                final Toast secondToast;
                secondToast = Toast.makeText(WallpaperService.this, secondMessage, Toast.LENGTH_LONG);
                new Handler().postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                secondToast.show();
                            }
                        },
                        3000L
                );
            }

            final boolean hasBackgroundImageNow = preferences.hasBackgroundImage();
            final boolean hasNewBackgroundImage = preferences.hasNewBackgroundImage();

            if (hasBackgroundImageNow != mHasBackgroundImage || hasNewBackgroundImage) {
                mHasBackgroundImage = hasBackgroundImageNow;
                initCanvas();
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);

            Log.d(TAG, "Engine.onSurfaceDestroyed(" + mVisible + ")");

            mVisible = false;
            stopAllPerformances();
            cancelDrawSchedule();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            mWidth = width;
            mHeight = height;

            final Context context = getApplicationContext();
            mHasBackgroundImage = PreferenceAccess.with(context).hasBackgroundImage();
            initCanvas();

            mPaint.setStyle(Paint.Style.STROKE);

            final boolean canChangeAlpha = !mHasBackgroundImage;
            mBeingBuilder = new FoliageBuilder(Math.min(mWidth, mHeight), canChangeAlpha);

            if (mBeings != null) {
                stopAllPerformances();
                mBeings = new ArrayList<>();

                // Draw once to clear everything.
                mResetCanvasOnce = true;
                nextStep();
            }

            if (VERBOSE) {
                Log.d(TAG, "New Surface: " + format + ": " + mWidth + " x " + mHeight);
            }

            super.onSurfaceChanged(holder, format, mWidth, mHeight);
        }

        private void initCanvas() {
            final Context context = getApplicationContext();
            if (mHasBackgroundImage) {

                final Bitmap storedBitmap;
                try {
                    storedBitmap = BackgroundImageOpener.loadRatioPreserved(mWidth, mHeight);

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();

                    PreferenceAccess.with(context).setHasBackgroundImage(false);
                    initEmptyCanvas();
                    return;
                }

                mBitmap = storedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                mCanvas = new Canvas(mBitmap);

                PreferenceAccess.with(context).acknowledgeNewBackgroundImage();

            } else {
                initEmptyCanvas();
            }
        }

        private void initEmptyCanvas() {
            mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
            mCanvas.drawColor(mBackgroundColor);
            mHasBackgroundImage = false;
        }

        @Override
        public void onTouchEvent(MotionEvent event) {

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mIsTouching = true;
                if (mFirstTouchMillis == 0) {
                    mFirstTouchMillis = System.currentTimeMillis();
                }

                addBeing(event.getX(), event.getY());
                adjustPaint((int) event.getX(), (int) event.getY());

                mResetCanvasOnce = false;
                nextStep();

            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                mIsTouching = false;
                return;
            }

            super.onTouchEvent(event);
        }

        private void addBeing(final float x, final float y) {

            final int recommendedAlpha = mBeingBuilder.getRecommendedAlpha();

            final long ageMinutes = (System.currentTimeMillis() - mFirstTouchMillis) / 1000L / 60L;
            if (ageMinutes > 30) {
                if (mAlphaOffset > -(recommendedAlpha * 0.75)) {
                    if (mAlphaOffset > (-recommendedAlpha * 0.75)) {
                        mAlphaOffset -= 10;
                    }
                }
            }

            if (mBeings.size() > mBeingBuilder.getRecommendedMaxNumber()) {
                return;
            }

            mPaint.setAlpha(recommendedAlpha + mAlphaOffset);

            mBeings.add(mBeingBuilder.build(x, y));
        }

        private void draw() {
            if (mDrawing) {
                return;
            }

            cancelDrawSchedule();
            mDrawing = true;

            if (VERBOSE) {
                Log.d(TAG, "Engine.draw()");
            }
            final long startMillis = System.currentTimeMillis();

            final SurfaceHolder surfaceHolder = getSurfaceHolder();
            Canvas canvas = null;

            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {

                    if (mResetCanvasOnce) {
                        mResetCanvasOnce = false;
                        canvas.drawColor(mBackgroundColor);
                        mCanvas.drawColor(mBackgroundColor);

                    } else if (!mIsTouching) {
                        drawBeings();
                    }
                    canvas.drawBitmap(mBitmap, 0f, 0f, null);
                }
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
            }

            mDrawing = false;

            if (VERBOSE) {
                Log.d(
                        TAG,
                        "Engine.draw() took "
                                + (System.currentTimeMillis() - startMillis) + "ms."
                );
            }

            if (updateBeings()) {
                nextStep();
            } else {
                Log.d(TAG, "Engine.draw() not calling nextStep().");
            }
        }

        private boolean updateBeings() {
            boolean didUpdateBeings = false;
            for (int i = 0; i < mBeings.size(); i++) {

                final Being being = mBeings.get(i);
                final boolean beingGrew = being.update(mIsTouching);
                didUpdateBeings |= beingGrew;

                if (!beingGrew) {
                    mBeings.remove(i);
                    Log.d(TAG, "Engine.draw() removing Foliage " + i + ".");
                }
            }

            return didUpdateBeings;
        }

        private void drawBeings() {
            for (final Being being : mBeings) {
                being.draw(mCanvas, mPaint);
            }
        }

        private void nextStep() {

            if (VERBOSE) {
                Log.d(TAG, "Engine.nextStep()");
            }

            cancelDrawSchedule();

            if (mVisible) {
                mHandler.postDelayed(mUpdateRunnable, 20L);
            } else if (mBeings != null) {
                stopAllPerformances();
            }
        }

        private void cancelDrawSchedule() {
            mHandler.removeCallbacks(mUpdateRunnable);
        }

        private void stopAllPerformances() {
            for (final Being being : mBeings) {
                being.stopPerforming();
            }
        }

        private void adjustPaint(final int x, final int y) {

            if (mPaint.getStyle() == Paint.Style.FILL) {
                mAlphaOffset *= 0.5f;
            }

            if (mHasBackgroundImage) {
                mPaint.setColor(mBitmap.getPixel(x, y));
                mPaint.setAlpha(128);
                return;
            }

            final Random rnd = new Random();

            final int bitmapWidth = mBitmap.getWidth();
            final int bitmapHeight = mBitmap.getHeight();

            final int maxSampleDistance = Math.min(bitmapWidth, bitmapHeight) / 5;
            final int maxSampleDistanceHalf = maxSampleDistance / 2;

            double brightnessAroundPoint = 0;
            for (int i = 0; i < BRIGHTNESS_SAMPLE_SIZE; i++) {
                final int randomX = x - maxSampleDistanceHalf + rnd.nextInt(maxSampleDistance);
                final int randomY = y - maxSampleDistanceHalf + rnd.nextInt(maxSampleDistance);

                final int colorAtRandomPoint = mBitmap.getPixel(
                        (randomX > 0 && randomX < bitmapWidth) ? randomX : 0,
                        (randomY > 0 && randomY < bitmapHeight) ? randomY : 0
                );

                float hsvAtRandomPoint[] = new float[3];
                Color.colorToHSV(colorAtRandomPoint, hsvAtRandomPoint);

                brightnessAroundPoint +=
                        (Color.alpha(colorAtRandomPoint) / 256f) * hsvAtRandomPoint[2];
            }

            brightnessAroundPoint /= (double) BRIGHTNESS_SAMPLE_SIZE;

            if (VERBOSE) {
                Log.d(TAG, "adjustPaint(): brightnessAroundPoint: " + brightnessAroundPoint);
            }

            final boolean useBlack = brightnessAroundPoint > BRIGHTNESS_BEFORE_BLACK;

            if (useBlack) {
                mPaint.setColor(Color.BLACK);
                mPaint.setAlpha(64);
                mPaint.setStrokeWidth(BLACK_STROKE_WIDTH);
            } else {
                mPaint.setStrokeWidth(DEFAULT_STROKE_WIDTH);
                mPaint.setARGB(
                        mBeingBuilder.getRecommendedAlpha() + mAlphaOffset,
                        160 + rnd.nextInt(96),
                        160 + rnd.nextInt(96),
                        160 + rnd.nextInt(96)
                );
            }

            if (mBlur) {
                final float blurRadius = ((float) bitmapWidth) * 0.01f;
                final MaskFilter blur = new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL);
                mPaint.setMaskFilter(blur);
                mPaint.setAlpha(mPaint.getAlpha() + 150);
            }
        }

    }
}