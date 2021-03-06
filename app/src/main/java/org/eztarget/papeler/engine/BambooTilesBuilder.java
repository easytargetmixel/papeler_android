package org.eztarget.papeler.engine;

import java.util.Random;

/**
 * Created by michelsievers on 19.04.17.
 */

public class BambooTilesBuilder implements BeingBuilder {

    private float mTileSize;

    private boolean mDrawLines = true; //new Random().nextBoolean();

    public BambooTilesBuilder(final float canvasSize) {
        mTileSize = canvasSize / (5f + (float) new Random().nextInt(10));
    }

    @Override
    public Being build(float x, float y) {
        return new BambooTile(mTileSize, x, y, mDrawLines);
    }

    @Override
    public int getRecommendedAlpha() {
        return mDrawLines ? 24 : 64;
    }

    @Override
    public int getRecommendedMaxNumber() {
        return 36;
    }
}
