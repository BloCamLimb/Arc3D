/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.sketch.BlendMode;
import icyllis.arc3d.sketch.Blender;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.sketch.effects.ColorFilter;
import icyllis.arc3d.sketch.shaders.Shader;
import icyllis.arc3d.engine.KeyBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Parameters used for shading.
 */
//TODO currently we don't handle advanced blending
public final class PaintParams {

    // color components using non-premultiplied alpha
    private final float[] mColor = new float[4];
    // A nullptr mPrimitiveBlender means there's no primitive color blending and it is skipped.
    // In the case where there is primitive blending, the primitive color is the source color and
    // the dest is the paint's color (or the paint's shader's computed color).
    private @Nullable Blender mPrimitiveBlender;
    @RawPtr
    private @Nullable Shader mShader;
    private @Nullable ColorFilter mColorFilter;
    // A nullptr here means SrcOver blending
    private @Nullable Blender mFinalBlender;
    private boolean mDither;

    public PaintParams() {
    }

    public PaintParams set(@NonNull Paint paint,
                           @Nullable Blender primitiveBlender,
                           boolean ignoreShader) {
        paint.getColor4f(mColor);
        mPrimitiveBlender = primitiveBlender;
        mShader = ignoreShader ? null : paint.getShader();
        if (mShader != null) {
            float origA = mColor[3];
            if (mShader.getConstantColor(mColor) != null) {
                // shader color is always modulated by paint's alpha, see handlePaintAlpha()
                mColor[3] *= origA;
                mShader = null;
            }
        }
        mColorFilter = paint.getColorFilter();
        mFinalBlender = paint.getBlender();
        mDither = paint.isDither();
        // antialias flag is already handled
        return this;
    }

    public void reset() {
        mPrimitiveBlender = null;
        mShader = null;
        mColorFilter = null;
        mFinalBlender = null;
    }

    /**
     * Returns the paint color, in sRGB space, non-premultiplied.
     * This is not a copy, don't modify.
     */
    public float[] getColor() {
        return mColor;
    }

    @RawPtr
    public @Nullable Shader getShader() {
        return mShader;
    }

    @RawPtr
    public @Nullable ColorFilter getColorFilter() {
        return mColorFilter;
    }

    @RawPtr
    public @Nullable Blender getFinalBlender() {
        return mFinalBlender;
    }

    @RawPtr
    public @Nullable Blender getPrimitiveBlender() {
        return mPrimitiveBlender;
    }

    @NonNull
    public BlendMode getFinalBlendMode() {
        BlendMode blendMode = mFinalBlender != null
                ? mFinalBlender.asBlendMode()
                : BlendMode.SRC_OVER;
        return blendMode != null ? blendMode : BlendMode.SRC;
    }

    /**
     * Map into destination color space, in color and result color are non-premultiplied.
     */
    public static void prepareColorForDst(float[] color,
                                          ImageInfo dstInfo) {
        ColorSpace dstCS = dstInfo.colorSpace();
        if (dstCS != null && !dstCS.isSrgb()) {
            ColorSpace.connect(ColorSpace.get(ColorSpace.Named.SRGB), dstCS)
                    .transform(color);
        }
    }

    /**
     * Returns true if the paint can be simplified to a solid color,
     * and stores the solid color.
     */
    public boolean isSolidColor() {
        return getSolidColor(null, null);
    }

    /**
     * Returns true if the paint can be simplified to a solid color,
     * and stores the solid color. The color will be transformed to the
     * target's color space and premultiplied.
     */
    public boolean getSolidColor(ImageInfo dstInfo, float @Nullable [] outColor) {
        if (mShader == null && mPrimitiveBlender == null) {
            if (outColor != null) {
                System.arraycopy(mColor, 0, outColor, 0, 4);
                // color transform
                prepareColorForDst(outColor, dstInfo);
                // premul
                for (int i = 0; i < 3; i++) {
                    outColor[i] *= outColor[3];
                }
                if (mColorFilter != null) {
                    // color filter is applied is destination space
                    mColorFilter.filterColor4f(outColor, outColor, dstInfo.colorSpace());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Similar to {@link #getSolidColor(ImageInfo, float[])}, without a PaintParams instance.
     */
    public static boolean getSolidColor(Paint paint, ImageInfo dstInfo, float[] outColor) {
        if (paint.getShader() == null) {
            if (outColor != null) {
                paint.getColor4f(outColor);
                // color transform
                prepareColorForDst(outColor, dstInfo);
                // premul
                for (int i = 0; i < 3; i++) {
                    outColor[i] *= outColor[3];
                }
                var colorFilter = paint.getColorFilter();
                if (colorFilter != null) {
                    // color filter is applied is destination space
                    colorFilter.filterColor4f(outColor, outColor, dstInfo.colorSpace());
                }
            }
            return true;
        }
        return false;
    }

    private boolean shouldDither(int dstCT) {
        if (!mDither) {
            return false;
        }

        if (dstCT == ColorInfo.CT_UNKNOWN) {
            return false;
        }

        if (dstCT == ColorInfo.CT_BGR_565) {
            // always dither bits per channel < 8
            return true;
        }

        return mShader != null && !mShader.isConstant();
    }

    // Only dither UNorm targets
    private static float getDitherRange(int dstCT) {
        // We use 1 / (2^bitdepth-1) as the range since each channel can hold 2^bitdepth values
        return switch (dstCT) {
            case ColorInfo.CT_BGR_565 -> 1 / 31.f; // 5-bit
            case ColorInfo.CT_ALPHA_8,
                 ColorInfo.CT_GRAY_8,
                 ColorInfo.CT_GRAY_ALPHA_88,
                 ColorInfo.CT_R_8,
                 ColorInfo.CT_RG_88,
                 ColorInfo.CT_RGB_888,
                 ColorInfo.CT_RGBX_8888,
                 ColorInfo.CT_RGBA_8888,
                 ColorInfo.CT_ABGR_8888,
                 ColorInfo.CT_RGBA_8888_SRGB,
                 ColorInfo.CT_BGRA_8888,
                 ColorInfo.CT_ARGB_8888 -> 1 / 255.f; // 8-bit
            case ColorInfo.CT_RGBA_1010102,
                 ColorInfo.CT_BGRA_1010102 -> 1 / 1023.f; // 10-bit
            case ColorInfo.CT_ALPHA_16,
                 ColorInfo.CT_R_16,
                 ColorInfo.CT_RG_1616,
                 ColorInfo.CT_RGBA_16161616 -> 1 / 32767.f; // 16-bit
            case ColorInfo.CT_UNKNOWN,
                 ColorInfo.CT_ALPHA_F16,
                 ColorInfo.CT_R_F16,
                 ColorInfo.CT_RG_F16,
                 ColorInfo.CT_RGBA_F16,
                 ColorInfo.CT_RGBA_F16_CLAMPED,
                 ColorInfo.CT_RGBA_F32 -> 0.f; // no dithering
            default -> throw new AssertionError(dstCT);
        };
    }

    private void appendPaintColorToKey(KeyContext keyContext,
                                       KeyBuilder keyBuilder,
                                       UniformDataGatherer uniformDataGatherer,
                                       TextureDataGatherer textureDataGatherer) {
        if (mShader != null) {
            FragmentHelpers.appendToKey(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer,
                    mShader
            );
        } else {
            FragmentHelpers.appendRGBOpaquePaintColorBlock(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );
        }
    }

    private void handlePrimitiveColor(KeyContext keyContext,
                                      KeyBuilder keyBuilder,
                                      UniformDataGatherer uniformDataGatherer,
                                      TextureDataGatherer textureDataGatherer) {
        if (mPrimitiveBlender != null) {
            keyBuilder.addInt(FragmentStage.kBlend_BuiltinStageID);

            // src
            appendPaintColorToKey(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );

            // dst
            FragmentHelpers.appendPrimitiveColorBlock(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );

            // blend
            FragmentHelpers.appendToKey(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer,
                    mPrimitiveBlender
            );
        } else {
            appendPaintColorToKey(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );
        }
    }

    private void handlePaintAlpha(KeyContext keyContext,
                                  KeyBuilder keyBuilder,
                                  UniformDataGatherer uniformDataGatherer,
                                  TextureDataGatherer textureDataGatherer) {
        if (mShader == null && mPrimitiveBlender == null) {
            // If there is no shader and no primitive blending the input to the colorFilter stage
            // is just the premultiplied paint color.
            float[] paintColor = keyContext.getPaintColor();
            // this is just paint color but has already been transformed into destination space
            float a = paintColor[3];
            FragmentHelpers.appendSolidColorShaderBlock(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer,
                    // but remember to premultiply
                    paintColor[0] * a, paintColor[1] * a, paintColor[2] * a, a
            );
            return;
        }

        if (mColor[3] != 1.0f) {
            keyBuilder.addInt(FragmentStage.kBlend_BuiltinStageID);

            // src
            handlePrimitiveColor(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );

            // dst
            FragmentHelpers.appendAlphaOnlyPaintColorBlock(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );

            // blend
            FragmentHelpers.appendFixedBlendMode(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer,
                    BlendMode.SRC_IN
            );
        } else {
            handlePrimitiveColor(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );
        }
    }

    private void handleColorFilter(KeyContext keyContext,
                                   KeyBuilder keyBuilder,
                                   UniformDataGatherer uniformDataGatherer,
                                   TextureDataGatherer textureDataGatherer) {
        if (mColorFilter != null) {
            keyBuilder.addInt(FragmentStage.kCompose_BuiltinStageID);

            handlePaintAlpha(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );

            FragmentHelpers.appendToKey(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer,
                    mColorFilter
            );
        } else {
            handlePaintAlpha(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );
        }
    }

    private void handleDithering(KeyContext keyContext,
                                 KeyBuilder keyBuilder,
                                 UniformDataGatherer uniformDataGatherer,
                                 TextureDataGatherer textureDataGatherer) {
        int dstCT = keyContext.targetInfo().colorType();
        if (shouldDither(dstCT)) {
            float ditherRange = getDitherRange(dstCT);
            if (ditherRange != 0) {
                keyBuilder.addInt(FragmentStage.kCompose_BuiltinStageID);

                handleColorFilter(
                        keyContext,
                        keyBuilder,
                        uniformDataGatherer,
                        textureDataGatherer
                );

                FragmentHelpers.appendDitherShaderBlock(
                        keyContext,
                        keyBuilder,
                        uniformDataGatherer,
                        textureDataGatherer,
                        ditherRange
                );
                return;
            }
        }

        handleColorFilter(
                keyContext,
                keyBuilder,
                uniformDataGatherer,
                textureDataGatherer
        );
    }

    public void appendToKey(KeyContext keyContext,
                            KeyBuilder keyBuilder,
                            UniformDataGatherer uniformDataGatherer,
                            TextureDataGatherer textureDataGatherer,
                            boolean useStepSolidColor) {
        //TODO

        // Optional Root 0 source color
        if (!useStepSolidColor) {
            handleDithering(
                    keyContext,
                    keyBuilder,
                    uniformDataGatherer,
                    textureDataGatherer
            );
        }

        // Root 1 final blender
        BlendMode finalBlend = getFinalBlendMode();
        FragmentHelpers.appendFixedBlendMode(
                keyContext, keyBuilder,
                uniformDataGatherer, textureDataGatherer,
                finalBlend
        );
    }
}
