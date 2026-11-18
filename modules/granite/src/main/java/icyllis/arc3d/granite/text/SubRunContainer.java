/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2026 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.*;
import icyllis.arc3d.granite.GraniteDevice;
import icyllis.arc3d.sketch.*;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * A SubRun represents a method to draw a subregion of a GlyphRun, where
 * GlyphRun represents the shaped text (positioned glyphs) and a strike.
 * SubRun is the basic unit that is ready for GPU task generation, except
 * that it does not contain the current transformation matrix.
 */
public class SubRunContainer {

    @Contract(pure = true)
    public static boolean isDirect(float approximateDeviceTextSize,
                                   Paint paint, Matrixc matrix) {
        //TODO try to avoid use direct sub runs for animations
        return 0 < approximateDeviceTextSize &&
                approximateDeviceTextSize <= Glyph.kMaxTextSizeForMask &&
                !matrix.hasPerspective();
    }

    @Contract(pure = true)
    public static boolean isDirect(float approximateDeviceTextSize) {
        return 0 < approximateDeviceTextSize &&
                approximateDeviceTextSize <= Glyph.kMaxTextSizeForMask;
    }

    /**
     * A set of buffer views to compute sub runs. Source is read-only,
     * so we don't own its memory for the initial run.
     * <p>
     * Glyphs are typeface-specific glyph IDs; positions are pairs of X/Y positions
     * relative to baseline (origin). Source and rejected positions are in local space,
     * accepted positions are in sub run's space, with bearing applied.
     */
    static class Buffers {

        int[] mSourceGlyphs;
        int mSourceGlyphOffset;

        float[] mSourcePositions;
        int mSourcePositionOffset;

        int mSourceGlyphCount;

        final int[] mAcceptedGlyphs;
        final float[] mAcceptedPositions;
        // Glyph::Format
        final byte[] mAcceptedFormats;

        int mAcceptedGlyphCount;

        final int[] mRejectedGlyphs;
        final float[] mRejectedPositions;

        int mRejectedGlyphCount;

        Buffers(int maxGlyphRunSize) {
            mAcceptedGlyphs = new int[maxGlyphRunSize];
            mAcceptedPositions = new float[maxGlyphRunSize * 2];
            mAcceptedFormats = new byte[maxGlyphRunSize];
            mRejectedGlyphs = new int[maxGlyphRunSize];
            mRejectedPositions = new float[maxGlyphRunSize * 2];
        }

        // let source points to glyph run buffer
        void setSource(@NonNull GlyphRun glyphRun) {
            mSourceGlyphs = glyphRun.mGlyphs;
            mSourceGlyphOffset = glyphRun.mGlyphOffset;
            mSourcePositions = glyphRun.mPositions;
            mSourcePositionOffset = glyphRun.mPositionOffset;
            mSourceGlyphCount = glyphRun.mGlyphCount;
        }

        // let source points to rejected buffer
        void setSourceToRejected() {
            mSourceGlyphs = mRejectedGlyphs;
            mSourceGlyphOffset = 0;
            mSourcePositions = mRejectedPositions;
            mSourcePositionOffset = 0;
            mSourceGlyphCount = mRejectedGlyphCount;
        }
    }

    static Rect2f prepare_for_direct_mask_drawing(Strike strike,
                                                  Matrixc creationMatrix,
                                                  Buffers buffers) {
        // Build up the mapping from source space to sub run space. Add the rounding constant,
        // so we just need to floor to get the device result.
        int acceptedSize = 0,
                rejectedSize = 0;
        var runBounds = Rect2f.makeInfiniteInverted();

        float subpixelRoundingX = strike.getSubpixelRoundingX();
        float subpixelRoundingY = strike.getSubpixelRoundingY();
        int subpixelFieldMaskX = strike.getSubpixelFieldMaskX();
        int subpixelFieldMaskY = strike.getSubpixelFieldMaskY();

        var bounds = new Rect2f();
        var mappedPos = new float[2];
        strike.lock();
        try {
            for (int i = buffers.mSourceGlyphOffset, j = buffers.mSourcePositionOffset,
                 e = buffers.mSourceGlyphOffset + buffers.mSourceGlyphCount; i < e; i += 1, j += 2) {
                float posX = buffers.mSourcePositions[j];
                float posY = buffers.mSourcePositions[j + 1];
                if (!Float.isFinite(posX) || !Float.isFinite(posY)) {
                    continue;
                }
                int glyphID = buffers.mSourceGlyphs[i];
                creationMatrix.mapPoints(
                        buffers.mSourcePositions, j,
                        mappedPos, 0, 1
                );
                mappedPos[0] += subpixelRoundingX;
                mappedPos[1] += subpixelRoundingY;
                int packedGlyphID = Glyph.packGlyphID(glyphID, mappedPos[0], mappedPos[1],
                        subpixelFieldMaskX, subpixelFieldMaskY);
                var glyph = strike.digestFor(Glyph.kDirectMask, packedGlyphID);
                switch (glyph.actionFor(Glyph.kDirectMask)) {
                    case Glyph.kAccept_Action -> {
                        float roundedPosX = (float) Math.floor(mappedPos[0]);
                        float roundedPosY = (float) Math.floor(mappedPos[1]);
                        glyph.getBounds(bounds);
                        bounds.offset(roundedPosX, roundedPosY);
                        runBounds.joinNoCheck(bounds);
                        buffers.mAcceptedGlyphs[acceptedSize] = packedGlyphID;
                        // accepted buffer index starts from zero, it's safe to use OR
                        buffers.mAcceptedPositions[acceptedSize << 1] = bounds.x();
                        buffers.mAcceptedPositions[(acceptedSize << 1) | 1] = bounds.y();
                        buffers.mAcceptedFormats[acceptedSize] = glyph.getMaskFormat();
                        acceptedSize++;
                    }
                    case Glyph.kReject_Action -> {
                        buffers.mRejectedGlyphs[rejectedSize] = glyphID;
                        // rejected buffer index starts from zero, it's safe to use OR
                        buffers.mRejectedPositions[rejectedSize << 1] = posX;
                        buffers.mRejectedPositions[(rejectedSize << 1) | 1] = posY;
                        rejectedSize++;
                    }
                    case Glyph.kDrop_Action -> {
                        // empty glyphs are dropped
                    }
                }
            }
        } finally {
            strike.unlock();
        }

        buffers.mAcceptedGlyphCount = acceptedSize;
        buffers.mRejectedGlyphCount = rejectedSize;
        return runBounds;
    }

    static Rect2f prepare_for_transformed_mask_drawing(Strike strike,
                                                       Matrixc creationMatrix,
                                                       Buffers buffers) {
        int acceptedSize = 0,
                rejectedSize = 0;
        var runBounds = Rect2f.makeInfiniteInverted();
        var bounds = new Rect2f();
        var mappedPos = new float[2];
        strike.lock();
        try {
            for (int i = buffers.mSourceGlyphOffset, j = buffers.mSourcePositionOffset,
                 e = buffers.mSourceGlyphOffset + buffers.mSourceGlyphCount; i < e; i += 1, j += 2) {
                float posX = buffers.mSourcePositions[j];
                float posY = buffers.mSourcePositions[j + 1];
                if (!Float.isFinite(posX) || !Float.isFinite(posY)) {
                    continue;
                }
                int glyphID = buffers.mSourceGlyphs[i];
                var glyph = strike.digestFor(Glyph.kTransformedMask, glyphID);
                switch (glyph.actionFor(Glyph.kTransformedMask)) {
                    case Glyph.kAccept_Action -> {
                        creationMatrix.mapPoints(
                                buffers.mSourcePositions, j,
                                mappedPos, 0, 1
                        );
                        glyph.getBounds(bounds);
                        bounds.offset(mappedPos[0], mappedPos[1]);
                        runBounds.joinNoCheck(bounds);
                        buffers.mAcceptedGlyphs[acceptedSize] = glyphID;
                        // accepted buffer index starts from zero, it's safe to use OR
                        buffers.mAcceptedPositions[acceptedSize << 1] = bounds.x();
                        buffers.mAcceptedPositions[(acceptedSize << 1) | 1] = bounds.y();
                        buffers.mAcceptedFormats[acceptedSize] = glyph.getMaskFormat();
                        acceptedSize++;
                    }
                    case Glyph.kReject_Action -> {
                        buffers.mRejectedGlyphs[rejectedSize] = glyphID;
                        // rejected buffer index starts from zero, it's safe to use OR
                        buffers.mRejectedPositions[rejectedSize << 1] = posX;
                        buffers.mRejectedPositions[(rejectedSize << 1) | 1] = posY;
                        rejectedSize++;
                    }
                    case Glyph.kDrop_Action -> {
                        // empty glyphs are dropped
                    }
                }
            }
        } finally {
            strike.unlock();
        }

        buffers.mAcceptedGlyphCount = acceptedSize;
        buffers.mRejectedGlyphCount = rejectedSize;
        return runBounds;
    }

    static float find_max_glyph_dimension(Strike strike, int[] glyphs,
                                          int glyphOffset, int glyphCount) {
        strike.lock();
        try {
            float maxDimension = 0;
            for (int i = glyphOffset, e = glyphOffset + glyphCount; i < e; i++) {
                var glyph = strike.digestFor(Glyph.kTransformedMask, glyphs[i]);
                maxDimension = Math.max(maxDimension, glyph.getMaxDimension());
            }
            return maxDimension;
        } finally {
            strike.unlock();
        }
    }

    @NonNull
    public static SubRunContainer make(
            @NonNull GlyphRunList glyphRunList,
            @NonNull Matrixc positionMatrix,
            @NonNull Paint runPaint,
            @NonNull GlyphStrikeCache strikeCache
    ) {
        SubRunContainer container = new SubRunContainer(positionMatrix);

        Buffers buffers = new Buffers(
                glyphRunList.maxGlyphRunSize()
        );

        float glyphRunListX = glyphRunList.getSourceBounds().centerX();
        float glyphRunListY = glyphRunList.getSourceBounds().centerY();

        var strikeDesc = new StrikeDesc.Lookup();

        // Handle all the runs in the glyphRunList
        for (int i = 0; i < glyphRunList.mGlyphRunCount; i++) {
            var glyphRun = glyphRunList.mGlyphRuns[i];
            var runFont = glyphRun.font();

            float approximateDeviceFontSize =
                    runFont.approximateTransformedFontSize(positionMatrix,
                            glyphRunListX, glyphRunListY);
            if (approximateDeviceFontSize <= 0 || !Float.isFinite(approximateDeviceFontSize)) {
                // Drop infinity, NaN, negative, zero, singular matrix
                continue;
            }

            buffers.setSource(glyphRun);

            // Atlas mask cases - SDF and direct mask
            // Only consider using direct or SDF drawing if not drawing hairlines and not too big.
            if ((runPaint.getStyle() != Paint.STROKE || runPaint.getStrokeWidth() != 0) &&
                    approximateDeviceFontSize <= Glyph.kMaxTextSizeForMask) {
                //TODO SDF case

                // Direct Mask case
                // Handle all the directly mapped mask subruns.
                if (buffers.mSourceGlyphCount > 0 && !positionMatrix.hasPerspective()) {
                    strikeDesc.updateForMask(
                            runFont, runPaint, positionMatrix
                    );

                    Strike strike = strikeDesc.findOrCreateStrike();

                    var creationBounds = prepare_for_direct_mask_drawing(
                            strike, positionMatrix, buffers
                    );
                    buffers.setSourceToRejected();

                    if (buffers.mAcceptedGlyphCount > 0) {
                        byte[] masks = buffers.mAcceptedFormats;
                        int prevFormat = BakedGlyph.chooseMaskFormat(masks[0]);
                        int prevIndex = 0;
                        for (int index = 1; index <= buffers.mAcceptedGlyphCount; index++) {
                            int format = index < buffers.mAcceptedGlyphCount
                                    ? BakedGlyph.chooseMaskFormat(masks[index])
                                    : -1; // invalid value as sentinel
                            if (prevFormat != format) {
                                container.append(
                                        new DirectMaskSubRun(
                                                strikeDesc,
                                                positionMatrix, creationBounds,
                                                prevFormat,
                                                buffers.mAcceptedGlyphs,
                                                prevIndex,
                                                buffers.mAcceptedPositions,
                                                prevIndex * 2,
                                                index - prevIndex,
                                                strikeCache
                                        )
                                );
                                prevFormat = format;
                                prevIndex = index;
                            }
                        }
                    }
                }
            }

            //TODO path case

            // Drawing of last resort case
            // Draw all the rest of the rejected glyphs from above. This scales out of the atlas to
            // the screen, so quality will suffer. This mainly handles large color or perspective
            // color not handled by Drawables.
            if (buffers.mSourceGlyphCount > 0 &&
                    approximateDeviceFontSize > MathUtil.PATH_TOLERANCE) {
                // Creation matrix will be changed below to meet the following criteria:
                // * No perspective - the font scaler and the strikes can't handle perspective masks.
                // * Fits atlas - creationMatrix will be conditioned so that the maximum glyph
                //   dimension for this run will be < kMaxBilerpAtlasDimension.
                Matrix creationMatrix = new Matrix(positionMatrix);
                // We want to reuse the subrun that scales to the range 0.5 to 1.5 relative to the
                // creation matrix.
                float minScaleFactor;
                float maxScaleFactor;

                if (creationMatrix.hasPerspective()) {
                    float maxAreaScale = creationMatrix.differentialAreaScale(
                            glyphRunListX, glyphRunListY
                    );
                    float perspectiveFactor = 1;
                    if (Float.isFinite(maxAreaScale) && maxAreaScale > MathUtil.PATH_TOLERANCE) {
                        perspectiveFactor = (float) Math.sqrt(maxAreaScale);
                    }

                    // Masks can not be created in perspective. Create a non-perspective font with a
                    // scale that will support the perspective keystoning.
                    creationMatrix.setScale(perspectiveFactor, perspectiveFactor);

                    minScaleFactor = 0.5f * perspectiveFactor;
                    maxScaleFactor = 1.5f * perspectiveFactor;
                } else {
                    float scale = creationMatrix.getMaxScale();

                    minScaleFactor = 0.5f * scale;
                    maxScaleFactor = 1.5f * scale;
                }

                // Condition the creationMatrix so that glyphs fit in the atlas.
                // The number of iterations is limited to 2, then the font size is limited to 16,908,804.
                int maxIter = 2;
                while (maxIter-- != 0) {
                    strikeDesc.updateForMask(
                            runFont, runPaint, creationMatrix
                    );
                    Strike gaugingStrike = strikeDesc.findOrCreateStrike();
                    float maxDimension = find_max_glyph_dimension(gaugingStrike,
                            buffers.mSourceGlyphs, buffers.mSourceGlyphOffset, buffers.mSourceGlyphCount);
                    if (maxDimension <= Glyph.kMaxBilerpAtlasDimension) {
                        break;
                    }
                    float reductionFactor = Glyph.kMaxBilerpAtlasDimension / maxDimension;
                    creationMatrix.postScale(reductionFactor, reductionFactor);
                    // If the metric is too large, then we remove the upper limit,
                    // because it will eventually fall back onto the same path
                    maxScaleFactor = Float.POSITIVE_INFINITY;
                }

                // Draw using the creationMatrix.
                strikeDesc.updateForMask(
                        runFont, runPaint, creationMatrix
                );

                Strike strike = strikeDesc.findOrCreateStrike();

                var creationBounds = prepare_for_transformed_mask_drawing(
                        strike, creationMatrix, buffers
                );
                buffers.setSourceToRejected();

                if (buffers.mAcceptedGlyphCount > 0) {
                    byte[] masks = buffers.mAcceptedFormats;
                    int prevFormat = BakedGlyph.chooseMaskFormat(masks[0]);
                    int prevIndex = 0;
                    for (int index = 1; index <= buffers.mAcceptedGlyphCount; index++) {
                        int format = index < buffers.mAcceptedGlyphCount
                                ? BakedGlyph.chooseMaskFormat(masks[index])
                                : -1; // invalid value as sentinel
                        if (prevFormat != format) {
                            container.append(
                                    new TransformedMaskSubRun(
                                            strikeDesc,
                                            creationMatrix,
                                            creationBounds,
                                            prevFormat,
                                            buffers.mAcceptedGlyphs,
                                            prevIndex,
                                            buffers.mAcceptedPositions,
                                            prevIndex * 2,
                                            index - prevIndex,
                                            minScaleFactor,
                                            maxScaleFactor,
                                            strikeCache
                                    )
                            );
                            prevFormat = format;
                            prevIndex = index;
                        }
                    }
                }
            }
        }

        return container;
    }

    private final Matrix mInitialPositionMatrix;

    private SubRun mHead;
    private SubRun mTail;

    public SubRunContainer(Matrixc initialPositionMatrix) {
        mInitialPositionMatrix = new Matrix(initialPositionMatrix);
    }

    public void draw(float originX, float originY,
                     Paint paint, GraniteDevice device) {
        for (var subRun = mHead; subRun != null; subRun = subRun.mNext) {
            subRun.draw(originX, originY, paint, device);
        }
    }

    public Matrixc initialPosition() {
        return mInitialPositionMatrix;
    }

    public boolean isEmpty() {
        return mHead == null;
    }

    @Contract(pure = true)
    public boolean canReuse(@NonNull Paint paint, @NonNull Matrixc positionMatrix,
                            float glyphRunListX, float glyphRunListY) {
        for (var subRun = mHead; subRun != null; subRun = subRun.mNext) {
            if (!subRun.canReuse(paint, positionMatrix, glyphRunListX, glyphRunListY)) {
                return false;
            }
        }
        return true;
    }

    public long getMemorySize() {
        long size = 16 + 8 + 8 + 56 + 8;
        for (var subRun = mHead; subRun != null; subRun = subRun.mNext) {
            size += subRun.getMemorySize();
        }
        return size;
    }

    void append(SubRun entry) {
        SubRun tail = mTail;
        mTail = entry;
        if (tail == null) {
            mHead = entry;
        } else {
            tail.mNext = entry;
        }
    }
}
