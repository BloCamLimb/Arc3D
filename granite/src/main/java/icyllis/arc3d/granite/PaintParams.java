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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Parameters used for shading.
 */
//TODO currently we don't handle advanced blending
public final class PaintParams {

    /**
     * The final blender combining paint color does not depend on dst.
     */
    public static final int DST_USAGE_NONE = 0x0;
    /**
     * The final blender depends on dst or pipeline has clip shader.
     * This does not take into account the coverage of the renderer, as it may be
     * coverage anti-aliasing (analytic XX) or masking (raster text, coverage mask).
     */
    public static final int DST_USAGE_DEPENDS_ON_DST = 0x1;
    /**
     * Additionally, cannot use hardware blending and requires dst read in shader.
     */
    public static final int DST_USAGE_DST_READ_REQUIRED = 0x2;

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
    // Skip primitive color space transform
    private boolean mSkipColorTransform;

    public PaintParams() {
    }

    public PaintParams set(@NonNull Paint paint,
                           @Nullable Blender primitiveBlender,
                           boolean skipColorTransform,
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
        mSkipColorTransform = skipColorTransform;
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
     * Returns if the paint can be simplified to a solid color,
     * and stores the solid color. The color will be transformed to the
     * target's color space and premultiplied.
     */
    public float @Nullable [] getSolidColor(KeyContext keyContext) {
        if (mShader == null && mPrimitiveBlender == null) {
            // see handlePaintAlpha()
            // this is just this.color but has already been transformed into destination space
            float[] outColor = keyContext.paintColor.clone();
            // premul
            for (int i = 0; i < 3; i++) {
                outColor[i] *= outColor[3];
            }
            if (mColorFilter != null) {
                // color filter is applied is the destination space
                mColorFilter.filterColor4f(outColor, outColor, keyContext.dstInfo.colorSpace());
            }
            return outColor;
        }
        return null;
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

    private static boolean blend_depends_on_dst(BlendMode bm,
                                                boolean srcIsOpaque) {
        if (bm == null) {
            return true;
        }
        if (bm == BlendMode.SRC || bm == BlendMode.CLEAR) {
            // src and clear blending never depends on dst
            return false;
        }
        if (bm == BlendMode.SRC_OVER || bm == BlendMode.DST_OUT) {
            // src-over depends on dst if src is transparent (a != 1)
            // dst-out simplifies to clear if a == 1
            return !srcIsOpaque;
        }
        return true;
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

    private boolean appendPaintColorToKey(KeyContext keyContext) {
        if (mShader != null) {
            FragmentHelpers.appendToKey(
                    keyContext,
                    mShader
            );
            return mShader.isOpaque();
        } else {
            FragmentHelpers.appendRGBOpaquePaintColorBlock(
                    keyContext
            );
            return true; // rgb1, always opaque
        }
    }

    private boolean handlePrimitiveColor(KeyContext keyContext) {
        if (mPrimitiveBlender != null) {

            //TODO handle skipping
            BlendMode primBlend = mPrimitiveBlender.asBlendMode();

            /*ColorSpace dstCS;
            if (primBlend == BlendMode.DST && (mSkipColorTransform || ((dstCS = keyContext.targetInfo().colorSpace()) == null || dstCS.isSrgb()))) {

            }*/

            keyContext.addBlock(FragmentStage.kBlend_BuiltinStageID);

            // src
            boolean srcIsOpaque = appendPaintColorToKey(
                    keyContext
            );

            // dst
            FragmentHelpers.appendPrimitiveColorBlock(
                    keyContext
            );

            // blend
            FragmentHelpers.appendToKey(
                    keyContext,
                    mPrimitiveBlender
            );

            if (srcIsOpaque && primBlend != null) {
                // If the input paint/shader is opaque, the result is only opaque if the primitive blend
                // mode is kSrc or kSrcOver. All other modes can introduce transparency.
                return primBlend == BlendMode.SRC || primBlend == BlendMode.SRC_OVER;
            }

            // If the input was already transparent, or if it's a runtime/complex blend mode,
            // the result cannot be considered opaque.
            return false;
        } else {
            // If no primitive blending is required, simply add the paint color.
            return appendPaintColorToKey(
                    keyContext
            );
        }
    }

    private boolean handlePaintAlpha(KeyContext keyContext) {
        if (mShader == null && mPrimitiveBlender == null) {
            // If there is no shader and no primitive blending the input to the colorFilter stage
            // is just the premultiplied paint color.
            float[] paintColor = keyContext.paintColor;
            // this is just paint color but has already been transformed into destination space
            float a = paintColor[3];
            assert a == mColor[3];
            FragmentHelpers.appendSolidColorShaderBlock(
                    keyContext,
                    // but remember to premultiply
                    paintColor[0] * a, paintColor[1] * a, paintColor[2] * a, a
            );
            return a == 1.0f;
        }

        if (mColor[3] != 1.0f) {
            keyContext.addBlock(FragmentStage.kBlend_BuiltinStageID);

            // src
            handlePrimitiveColor(
                    keyContext
            );

            // dst
            FragmentHelpers.appendAlphaOnlyPaintColorBlock(
                    keyContext
            );

            // blend
            FragmentHelpers.appendFixedBlendMode(
                    keyContext,
                    BlendMode.SRC_IN
            );

            // The result is guaranteed to be non-opaque because we're blending with mColor's alpha.
            return false;
        } else {
            return handlePrimitiveColor(
                    keyContext
            );
        }
    }

    private boolean handleColorFilter(KeyContext keyContext) {
        if (mColorFilter != null) {
            keyContext.addBlock(FragmentStage.kCompose_BuiltinStageID);

            boolean srcIsOpaque = handlePaintAlpha(
                    keyContext
            );

            FragmentHelpers.appendToKey(
                    keyContext,
                    mColorFilter
            );

            return srcIsOpaque && mColorFilter.isAlphaUnchanged();
        } else {
            return handlePaintAlpha(
                    keyContext
            );
        }
    }

    private boolean handleDithering(KeyContext keyContext) {
        int dstCT = keyContext.dstInfo.colorType();
        if (shouldDither(dstCT)) {
            float ditherRange = getDitherRange(dstCT);
            if (ditherRange != 0) {
                keyContext.addBlock(FragmentStage.kCompose_BuiltinStageID);

                boolean srcIsOpaque = handleColorFilter(
                        keyContext
                );

                FragmentHelpers.appendDitherShaderBlock(
                        keyContext,
                        ditherRange
                );
                return srcIsOpaque;
            }
        }

        return handleColorFilter(
                keyContext
        );
    }

    /**
     * Returns dst usage flags.
     */
    public int toKey(KeyContext keyContext,
                     float @Nullable [] stepSolidColor,
                     @Nullable Shader clipShader) {
        // (Optional) Root 0 source color
        // paint color or shader -> primitive color -> paint alpha -> color filter -> dither
        boolean srcIsOpaque;
        if (stepSolidColor == null) {
            srcIsOpaque = handleDithering(
                    keyContext
            );
        } else {
            srcIsOpaque = stepSolidColor[3] == 1.0f;
        }

        boolean dependsOnDst = clipShader != null;

        // Root 1 final blender
        //TODO custom blender and advanced blending is not supported yet
        BlendMode finalBlend = getFinalBlendMode();
        FragmentHelpers.appendFixedBlendMode(
                keyContext,
                finalBlend
        );
        dependsOnDst |= blend_depends_on_dst(finalBlend, srcIsOpaque);

        // (Optional) Root 2 clipping

        // at least there's final blender
        assert !keyContext.paintParamsKeyBuilder.isEmpty();

        return dependsOnDst ? DST_USAGE_DEPENDS_ON_DST : 0;
    }
}
