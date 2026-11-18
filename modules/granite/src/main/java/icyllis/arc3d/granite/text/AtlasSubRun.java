/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2026 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.granite.text;

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2fc;
import icyllis.arc3d.engine.SamplerDesc;
import icyllis.arc3d.granite.DrawAtlas;
import icyllis.arc3d.granite.GraniteDevice;
import icyllis.arc3d.granite.MeshDrawWriter;
import icyllis.arc3d.granite.RecordingContext;
import icyllis.arc3d.sketch.Glyph;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Strike;
import icyllis.arc3d.sketch.StrikeDesc;
import org.jetbrains.annotations.Contract;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

public abstract class AtlasSubRun extends SubRun {

    final StrikeDesc mStrikeDesc;
    final BakedGlyph[] mGlyphs;
    final int mMaskFormat;
    final boolean mCanDrawDirect;
    final Matrix mCreationMatrix;
    final Rect2f mCreationBounds;
    final float[] mPositions;
    // bounds and positions are in sub run's space

    final DrawAtlas.PlotBulkUseUpdater mBulkUseUpdater;
    long mAtlasGeneration = DrawAtlas.AtlasGenerationCounter.kInvalidGeneration;

    /**
     * All params are read-only, copy will be made.
     *
     * @param acceptedGlyphs packed glyph IDs, see {@link Glyph#packGlyphID(int, float, float, int, int)}
     */
    @Contract(pure = true)
    AtlasSubRun(StrikeDesc strikeDesc,
                Matrixc creationMatrix,
                Rect2fc creationBounds,
                int maskFormat,
                int[] acceptedGlyphs,
                int acceptedGlyphOffset,
                float[] acceptedPositions,
                int acceptedPositionOffset,
                int acceptedGlyphCount,
                boolean canDrawDirect,
                boolean bilerpBorder,
                GlyphStrikeCache strikeCache) {
        assert !creationMatrix.hasPerspective();
        mMaskFormat = maskFormat;
        mCanDrawDirect = canDrawDirect;
        mCreationMatrix = new Matrix(creationMatrix);
        mCreationBounds = new Rect2f(creationBounds);
        mPositions = Arrays.copyOfRange(acceptedPositions, acceptedPositionOffset,
                acceptedPositionOffset + acceptedGlyphCount * 2);

        mStrikeDesc = strikeDesc.immutable();
        // convert CPU glyphs to GPU glyphs
        var glyphStrike = strikeCache.findOrCreateStrike(
                mStrikeDesc
        );
        var bakedGlyphs = new BakedGlyph[acceptedGlyphCount];
        for (int i = 0; i < acceptedGlyphCount; ++i) {
            bakedGlyphs[i] = glyphStrike.getGlyph(
                    acceptedGlyphs[acceptedGlyphOffset + i], bilerpBorder);
        }
        mGlyphs = bakedGlyphs;
        mBulkUseUpdater = new DrawAtlas.PlotBulkUseUpdater();
    }

    @Override
    public void draw(float originX, float originY,
                     Paint paint, GraniteDevice device) {
        device.drawAtlasSubRun(this, originX, originY, paint);
    }

    /**
     * Returns the number of visible glyphs.
     */
    public int getGlyphCount() {
        return mGlyphs.length;
    }

    /**
     * Returns the GPU mask format.
     */
    public int getMaskFormat() {
        return mMaskFormat;
    }

    /**
     * Update atlas for glyphs in the given range if needed, returns the number
     * of glyphs that are updated (may less than end-start if atlas is full).
     * If an error occurred, returns the bitwise NOT (a negative value).
     */
    // This call is not thread safe. It should only be called from a known single-threaded env.
    public int prepareGlyphs(int start, int end,
                             RecordingContext context) {
        assert 0 <= start && start <= end && end <= mGlyphs.length;

        var atlasManager = context.getAtlasProvider().getGlyphAtlasManager();
        var tokenTracker = context.getAtlasTokenTracker();

        int maskFormat = mMaskFormat;
        long currentGeneration = atlasManager.getAtlasGeneration(maskFormat);

        if (mAtlasGeneration != currentGeneration) {
            // Calculate the texture coordinates for the vertexes during first use (mAtlasGeneration
            // is set to INVALID_GENERATION) or the atlas has changed in subsequent calls...
            mBulkUseUpdater.clear();

            Strike strike = mStrikeDesc.findOrCreateStrike();

            boolean success = true;
            int glyphsUpdated = 0;
            for (int i = start; i < end; ++i) {
                var bakedGlyph = mGlyphs[i];

                if (!atlasManager.hasGlyph(maskFormat, bakedGlyph)) {
                    // do CPU rasterization if needed
                    Glyph glyph;
                    strike.lock();
                    try {
                        glyph = strike.getGlyph(bakedGlyph.getPackedID());
                        strike.prepareForImage(glyph);
                    } finally {
                        strike.unlock();
                    }
                    var res = atlasManager.addGlyphToAtlas(
                            glyph, maskFormat, bakedGlyph
                    );
                    if (res != DrawAtlas.kSuccess_Result) {
                        assert res == DrawAtlas.kFailure_Result ||
                                res == DrawAtlas.kTryAgain_Result;
                        // try again meaning the atlas is full, it's not an error, just stop here
                        success = res != DrawAtlas.kFailure_Result;
                        break;
                    }
                }

                atlasManager.addGlyphAndSetLastUseToken(mBulkUseUpdater,
                        bakedGlyph,
                        maskFormat,
                        tokenTracker.nextFlushToken());
                ++glyphsUpdated;
            }

            // Update atlas generation if there are no more glyphs to put in the atlas.
            if (success && start + glyphsUpdated == mGlyphs.length) {
                // Cannot use 'currentGeneration' above.
                // Need to get the freshest value of the atlas' generation because
                // DrawAtlas.addRect may have changed it.
                mAtlasGeneration = atlasManager.getAtlasGeneration(maskFormat);
            }

            return success ? glyphsUpdated : ~glyphsUpdated;
        } else {
            // The atlas hasn't changed, so our texture coordinates are still valid.
            if (end == mGlyphs.length) {
                // The atlas hasn't changed and the texture coordinates are all still valid. Update
                // all the plots used to the new use token.
                atlasManager.setLastUseTokenBulk(maskFormat,
                        mBulkUseUpdater,
                        tokenTracker.nextFlushToken());
            }
            return end - start;
        }
    }

    /**
     * @see icyllis.arc3d.granite.geom.RasterTextStep
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void fillInstanceData(MeshDrawWriter writer,
                                 int offset, int count,
                                 float depth) {
        writer.beginInstances(null, null, 4);
        long instanceData = writer.appendInstances(count);

        var glyphs = mGlyphs;
        var positions = mPositions;
        for (int i = offset, j = offset << 1, e = offset + count; i < e; i += 1, j += 2) {
            var glyph = glyphs[i];
            // xy pos
            MemoryUtil.memPutFloat(instanceData, positions[j]);
            MemoryUtil.memPutFloat(instanceData + 4, positions[j | 1]);
            // uv pos
            MemoryUtil.memPutShort(instanceData + 8, glyph.u1);
            MemoryUtil.memPutShort(instanceData + 10, glyph.v1);
            // size
            MemoryUtil.memPutShort(instanceData + 12, glyph.width());
            MemoryUtil.memPutShort(instanceData + 14, glyph.height());
            // painter's depth
            MemoryUtil.memPutFloat(instanceData + 16, depth);
            instanceData += 20;
        }

    }

    /**
     * @see icyllis.arc3d.granite.geom.RasterTextStep
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void fillInstanceData(MeshDrawWriter writer,
                                 int offset, int count,
                                 float offsetX, float offsetY,
                                 float depth) {
        writer.beginInstances(null, null, 4);
        long instanceData = writer.appendInstances(count);

        var glyphs = mGlyphs;
        var positions = mPositions;
        for (int i = offset, j = offset << 1, e = offset + count; i < e; i += 1, j += 2) {
            var glyph = glyphs[i];
            // xy pos
            MemoryUtil.memPutFloat(instanceData, positions[j] + offsetX);
            MemoryUtil.memPutFloat(instanceData + 4, positions[j | 1] + offsetY);
            // uv pos
            MemoryUtil.memPutShort(instanceData + 8, glyph.u1);
            MemoryUtil.memPutShort(instanceData + 10, glyph.v1);
            // size
            MemoryUtil.memPutShort(instanceData + 12, glyph.width());
            MemoryUtil.memPutShort(instanceData + 14, glyph.height());
            // painter's depth
            MemoryUtil.memPutFloat(instanceData + 16, depth);
            instanceData += 20;
        }

    }

    public Rect2fc getBounds() {
        return mCreationBounds;
    }

    /**
     * Compute sub-run-to-local matrix and sub-run-to-device with the
     * given origin. Compute filter based on local-to-device matrix and
     * origin, and return it.
     */
    @SuppressWarnings("AssertWithSideEffects")
    @Contract(mutates = "param4,param5")
    public int getMatrixAndFilter(Matrixc localToDevice,
                                  float originX, float originY,
                                  Matrix outSubRunToLocal,
                                  Matrix outSubRunToDevice) {
        // the creation matrix has no perspective
        if (mCreationMatrix.invert(outSubRunToLocal)) {
            outSubRunToLocal.postTranslate(originX, originY);
            assert !outSubRunToLocal.hasPerspective();
        } else {
            outSubRunToLocal.setIdentity();
        }
        boolean compatible = !localToDevice.hasPerspective() &&
                localToDevice.m11() == mCreationMatrix.m11() &&
                localToDevice.m12() == mCreationMatrix.m12() &&
                localToDevice.m21() == mCreationMatrix.m21() &&
                localToDevice.m22() == mCreationMatrix.m22();
        if (compatible) {
            // compatible means only the difference is translation
            float mappedOriginX = localToDevice.m11() * originX +
                    localToDevice.m21() * originY + localToDevice.m41();
            float mappedOriginY = localToDevice.m12() * originX +
                    localToDevice.m22() * originY + localToDevice.m42();
            // creation matrix has no perspective, so translate vector is its origin
            float offsetX = mappedOriginX - mCreationMatrix.getTranslateX();
            float offsetY = mappedOriginY - mCreationMatrix.getTranslateY();
            outSubRunToDevice.setTranslate(offsetX, offsetY);
            if (mCanDrawDirect &&
                    offsetX == (float) Math.floor(offsetX) &&
                    offsetY == (float) Math.floor(offsetY)) {
                // integer translate in device space
                return SamplerDesc.FILTER_NEAREST;
            }
        } else {
            outSubRunToDevice.set(localToDevice);
            outSubRunToDevice.preConcat(outSubRunToLocal);
        }
        //assert !mCanDrawDirect; // should not happen
        return SamplerDesc.FILTER_LINEAR;
    }

    @Override
    public long getMemorySize() {
        long size = 32;
        size += 8 + mBulkUseUpdater.getMemorySize();
        size += 8 + 56;
        size += 8 + 32;
        size += 16 + (long) mPositions.length * 4 + 8;
        size += 8 + mStrikeDesc.getMemorySize();
        size += 16 + (long) mGlyphs.length * 8 + 8;
        size += 8;
        size += 8;
        return size;
    }
}
