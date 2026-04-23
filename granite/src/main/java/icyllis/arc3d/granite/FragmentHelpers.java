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

import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.engine.SamplerDesc;
import icyllis.arc3d.sketch.BlendMode;
import icyllis.arc3d.sketch.Blender;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.effects.BlendModeColorFilter;
import icyllis.arc3d.sketch.effects.ColorFilter;
import icyllis.arc3d.sketch.effects.ComposeColorFilter;
import icyllis.arc3d.sketch.shaders.*;
import org.jspecify.annotations.Nullable;

/**
 * Build {@link icyllis.arc3d.engine.Key PaintParamsKey} and collect
 * uniform data and texture sampler desc.
 *
 * @see ShaderCodeSource
 * @see FragmentStage
 */
public class FragmentHelpers {

    public static final ColorSpace.Rgb.TransferParameters LINEAR_TRANSFER_PARAMETERS =
            new ColorSpace.Rgb.TransferParameters(1.0, 0.0, 0.0, 0.0, 1.0);

    private static void append_transfer_function_uniform(
            ColorSpace.Rgb.TransferParameters tf,
            UniformDataGatherer uniformDataGatherer
    ) {
        // vec4 and vec4 array have the same alignment rule
        uniformDataGatherer.write4f((float) tf.g, (float) tf.a, (float) tf.b, (float) tf.c);
        uniformDataGatherer.write4f((float) tf.d, (float) tf.e, (float) tf.f, 0.0f);
    }

    /**
     * Compute color space transform parameters and add uniforms,
     * see {@link PixelUtils}.
     */
    public static void appendColorSpaceUniforms(
            @Nullable ColorSpace srcCS, @ColorInfo.AlphaType int srcAT,
            @Nullable ColorSpace dstCS, @ColorInfo.AlphaType int dstAT,
            UniformDataGatherer uniformDataGatherer
    ) {
        // Opaque outputs are treated as the same alpha type as the source input.
        if (dstAT == ColorInfo.AT_OPAQUE) {
            dstAT = srcAT;
        }

        if (srcCS == null) {
            srcCS = ColorSpace.get(ColorSpace.Named.SRGB);
        }
        if (dstCS == null) {
            dstCS = srcCS;
        }

        boolean srcXYZ = srcCS.getModel() == ColorSpace.Model.XYZ;
        boolean dstXYZ = dstCS.getModel() == ColorSpace.Model.XYZ;
        var srcRGB = srcCS.getModel() == ColorSpace.Model.RGB
                ? (ColorSpace.Rgb) srcCS : null;
        var dstRGB = dstCS.getModel() == ColorSpace.Model.RGB
                ? (ColorSpace.Rgb) dstCS : null;

        // we handle RGB space with known transfer parameters and XYZ space
        boolean csXform = (srcXYZ || (srcRGB != null && srcRGB.getTransferParameters() != null)) &&
                (dstXYZ || (dstRGB != null && dstRGB.getTransferParameters() != null)) &&
                !srcCS.equals(dstCS);

        int flags = 0;

        if (csXform || srcAT != dstAT) {
            if (srcAT == ColorInfo.AT_PREMUL) {
                flags |= PixelUtils.kColorSpaceXformFlagUnpremul;
            }
            if (srcAT != ColorInfo.AT_OPAQUE && dstAT == ColorInfo.AT_PREMUL) {
                flags |= PixelUtils.kColorSpaceXformFlagPremul;
            }
        }

        if (csXform) {
            float[] transform = ColorSpace.Connector.Rgb.computeTransform(
                    srcRGB, dstRGB, ColorSpace.RenderIntent.RELATIVE
            );
            if (transform != null) {
                flags |= PixelUtils.kColorSpaceXformFlagGamutTransform;
            }

            if (srcRGB != null && !LINEAR_TRANSFER_PARAMETERS.equals(srcRGB.getTransferParameters())) {
                flags |= PixelUtils.kColorSpaceXformFlagLinearize;
            }
            if (dstRGB != null && !LINEAR_TRANSFER_PARAMETERS.equals(dstRGB.getTransferParameters())) {
                flags |= PixelUtils.kColorSpaceXformFlagEncode;
            }

            uniformDataGatherer.write1i(flags);
            append_transfer_function_uniform(srcRGB == null ? LINEAR_TRANSFER_PARAMETERS
                    : srcRGB.getTransferParameters(), uniformDataGatherer);
            if (transform != null) {
                uniformDataGatherer.writeMatrix3f(0, transform);
            } else {
                uniformDataGatherer.writeMatrix3f(Matrix.identity());
            }
            append_transfer_function_uniform(dstRGB == null ? LINEAR_TRANSFER_PARAMETERS
                    : dstRGB.getTransferParameters(), uniformDataGatherer);
        } else {
            uniformDataGatherer.write1i(flags);
            append_transfer_function_uniform(LINEAR_TRANSFER_PARAMETERS, uniformDataGatherer);
            uniformDataGatherer.writeMatrix3f(Matrix.identity());
            append_transfer_function_uniform(LINEAR_TRANSFER_PARAMETERS, uniformDataGatherer);
        }
    }

    public static void appendSolidColorShaderBlock(
            KeyContext keyContext,
            float r, float g, float b, float a
    ) {
        keyContext.uniformDataGatherer.write4f(r, g, b, a);

        keyContext.addBlock(FragmentStage.kSolidColorShader_BuiltinStageID);
    }

    public static void appendRGBOpaquePaintColorBlock(
            KeyContext keyContext
    ) {
        keyContext.uniformDataGatherer.writePaintColor(keyContext.paintColor);

        keyContext.addBlock(FragmentStage.kRGBOpaquePaintColor_BuiltinStageID);
    }

    public static void appendAlphaOnlyPaintColorBlock(
            KeyContext keyContext
    ) {
        keyContext.uniformDataGatherer.writePaintColor(keyContext.paintColor);

        keyContext.addBlock(FragmentStage.kAlphaOnlyPaintColor_BuiltinStageID);
    }

    public static void appendDitherShaderBlock(
            KeyContext keyContext,
            float range
    ) {
        keyContext.uniformDataGatherer.write1f(range);

        keyContext.addBlock(FragmentStage.kDitherShader_BuiltinStageID);
    }

    public static void appendLocalMatrixShaderBlock(
            KeyContext keyContext,
            Matrixc localMatrix
    ) {
        Matrix inverse = new Matrix();
        localMatrix.invert(inverse);
        keyContext.uniformDataGatherer.writeMatrix3f(inverse);

        keyContext.addBlock(FragmentStage.kLocalMatrixShader_BuiltinStageID);
    }

    public static final int kCubicClampUnpremul = 0;
    public static final int kCubicClampPremul = 1;

    static {
        //noinspection ConstantValue
        assert
                SamplerDesc.ADDRESS_MODE_REPEAT          == Shader.TILE_MODE_REPEAT &&
                SamplerDesc.ADDRESS_MODE_MIRRORED_REPEAT == Shader.TILE_MODE_MIRROR &&
                SamplerDesc.ADDRESS_MODE_CLAMP_TO_EDGE   == Shader.TILE_MODE_CLAMP  &&
                SamplerDesc.ADDRESS_MODE_CLAMP_TO_BORDER == Shader.TILE_MODE_DECAL  ;
    }

    public static void appendImageShaderBlock(
            KeyContext keyContext,
            Rect2fc subset,
            int tileModeX, int tileModeY,
            SamplingOptions sampling,
            int imageWidth, int imageHeight,
            @ColorInfo.AlphaType int srcAT,
            @RawPtr ImageProxyView view
    ) {
        boolean useHwTiling = !sampling.mUseCubic &&
                subset.contains(0, 0, imageWidth, imageHeight) &&
                (keyContext.caps.clampToBorderSupport() ||
                        (tileModeX != Shader.TILE_MODE_DECAL && tileModeY != Shader.TILE_MODE_DECAL));
        int filterMode = SamplingOptions.FILTER_MODE_NEAREST; // for subset

        if (useHwTiling || !sampling.mUseCubic) {
            // cubic does not require this
            keyContext.uniformDataGatherer.write2f(1.f / imageWidth, 1.f / imageHeight);
        }
        if (useHwTiling) {
            // hardware (fast)
            keyContext.addBlock(FragmentStage.kHWImageShader_BuiltinStageID);
        } else {
            // strict subset
            assert sampling.mMipmap == SamplingOptions.MIPMAP_MODE_NONE ||
                    !keyContext.caps.clampToBorderSupport();
            keyContext.uniformDataGatherer.write4f(subset.left(), subset.top(),
                    subset.right(), subset.bottom());
            if (sampling.mUseCubic) {
                // Cubic sampling is handled in a shader, with the actual texture sampled by with
                // nearest-neighbor
                assert sampling.mFilter == SamplingOptions.FILTER_MODE_NEAREST;
                keyContext.uniformDataGatherer.writeMatrix4f(0,
                        ImageShader.makeCubicMatrix(sampling.mCubicB, sampling.mCubicC));
                keyContext.uniformDataGatherer.write1i(srcAT == ColorInfo.AT_PREMUL
                        ? kCubicClampPremul
                        : kCubicClampUnpremul);
                keyContext.addBlock(FragmentStage.kCubicImageShader_BuiltinStageID);
            } else {
                // Use linear filter if non-zero
                filterMode = sampling.mFilter;
                keyContext.uniformDataGatherer.write1i(filterMode);
                keyContext.addBlock(FragmentStage.kImageShader_BuiltinStageID);
            }
            keyContext.uniformDataGatherer.write1i(tileModeX);
            keyContext.uniformDataGatherer.write1i(tileModeY);
        }

        var samplerDesc = useHwTiling
                ? SamplerDesc.make(
                sampling.mFilter, // implicit cast
                sampling.mFilter, // implicit cast
                sampling.mMipmap, // implicit cast
                tileModeToAddressMode(tileModeX),
                tileModeToAddressMode(tileModeY),
                SamplerDesc.ADDRESS_MODE_CLAMP_TO_EDGE)
                : SamplerDesc.make(filterMode);

        keyContext.textureDataGatherer.add(view, samplerDesc);
    }

    static int tileModeToAddressMode(int tileMode) {
        return switch (tileMode) {
            case Shader.TILE_MODE_CLAMP -> SamplerDesc.ADDRESS_MODE_CLAMP_TO_EDGE;
            case Shader.TILE_MODE_REPEAT -> SamplerDesc.ADDRESS_MODE_REPEAT;
            case Shader.TILE_MODE_MIRROR -> SamplerDesc.ADDRESS_MODE_MIRRORED_REPEAT;
            case Shader.TILE_MODE_DECAL -> SamplerDesc.ADDRESS_MODE_CLAMP_TO_BORDER;
            default -> throw new AssertionError(tileMode);
        };
    }

    public static class GradientData {

        static final int kNumInternalStorageStops = 8;

        // Layout options for angular gradient.
        float mBias;
        float mScale;

        int mTileMode;
        int mNumStops;

        float[] mColors = new float[kNumInternalStorageStops * 4];
        float[] mOffsets = new float[kNumInternalStorageStops];

        Gradient1DShader mSrcShader; // raw ptr

        int mInterpolation;

        GradientData(Gradient1DShader shader,
                     float bias, float scale,
                     int tileMode,
                     int numStops,
                     float[] colors,
                     float[] offsets,
                     int interpolation) {
            mSrcShader = shader;
            mBias = bias;
            mScale = scale;
            mTileMode = tileMode;
            mNumStops = numStops;
            mInterpolation = interpolation;

            if (mNumStops <= kNumInternalStorageStops) {
                System.arraycopy(colors, 0, mColors, 0, mNumStops * 4);
                if (offsets != null) {
                    System.arraycopy(offsets, 0, mOffsets, 0, mNumStops);
                } else {
                    // uniform stops
                    for (int i = 0; i < mNumStops; ++i) {
                        mOffsets[i] = (float) i / (mNumStops - 1);
                    }
                }

                // Extend the colors and offset, if necessary, to fill out the arrays.
                // The unrolled binary search implementation assumes excess stops match the last real value.
                int last = mNumStops - 1;
                for (int i = mNumStops; i < kNumInternalStorageStops; ++i) {
                    System.arraycopy(mColors, last * 4, mColors, i * 4, 4);
                    mOffsets[i] = mOffsets[last];
                }
            }
        }
    }

    private static void append_gradient_head(
            GradientData gradientData,
            UniformDataGatherer uniformDataGatherer
    ) {
        if (gradientData.mNumStops <= GradientData.kNumInternalStorageStops) {
            if (gradientData.mNumStops <= 4) {
                // Round up to 4 stops.
                uniformDataGatherer.write4fv(
                        0, 4, gradientData.mColors
                );
                uniformDataGatherer.write4fv(
                        0, 1, gradientData.mOffsets
                );
            } else {
                //noinspection ConstantValue
                assert gradientData.mNumStops <= 8;
                // Round up to 8 stops.
                uniformDataGatherer.write4fv(
                        0, 8, gradientData.mColors
                );
                uniformDataGatherer.write4fv(
                        0, 2, gradientData.mOffsets
                );
            }
        }
    }

    private static void append_gradient_tail(
            GradientData gradientData,
            UniformDataGatherer uniformDataGatherer
    ) {
        if (gradientData.mNumStops > GradientData.kNumInternalStorageStops) {
            uniformDataGatherer.write1i(gradientData.mNumStops);
        }
        uniformDataGatherer.write1i(gradientData.mTileMode);
        uniformDataGatherer.write1i(
                GradientShader.Interpolation.getColorSpace(gradientData.mInterpolation));
        uniformDataGatherer.write1i(
                GradientShader.Interpolation.isInPremul(gradientData.mInterpolation) ? 1 : 0);
    }

    public static void appendGradientShaderBlock(
            KeyContext keyContext,
            GradientData gradData
    ) {
        //TODO texture-based gradient
        UniformDataGatherer uniformDataGatherer = keyContext.uniformDataGatherer;
        int stageID = FragmentStage.kError_BuiltinStageID;
        switch (gradData.mSrcShader.asGradient()) {
            case Shader.GRADIENT_TYPE_LINEAR -> {
                stageID = gradData.mNumStops <= 4
                        ? FragmentStage.kLinearGradientShader4_BuiltinStageID
                        : gradData.mNumStops <= 8
                        ? FragmentStage.kLinearGradientShader8_BuiltinStageID
                        : FragmentStage.kError_BuiltinStageID;
                append_gradient_head(gradData, uniformDataGatherer);
                append_gradient_tail(gradData, uniformDataGatherer);
            }
            case Shader.GRADIENT_TYPE_RADIAL -> {
                stageID = gradData.mNumStops <= 4
                        ? FragmentStage.kRadialGradientShader4_BuiltinStageID
                        : gradData.mNumStops <= 8
                        ? FragmentStage.kRadialGradientShader8_BuiltinStageID
                        : FragmentStage.kError_BuiltinStageID;
                append_gradient_head(gradData, uniformDataGatherer);
                append_gradient_tail(gradData, uniformDataGatherer);
            }
            case Shader.GRADIENT_TYPE_ANGULAR -> {
                stageID = gradData.mNumStops <= 4
                        ? FragmentStage.kAngularGradientShader4_BuiltinStageID
                        : gradData.mNumStops <= 8
                        ? FragmentStage.kAngularGradientShader8_BuiltinStageID
                        : FragmentStage.kError_BuiltinStageID;
                append_gradient_head(gradData, uniformDataGatherer);
                uniformDataGatherer.write1f(gradData.mBias);
                uniformDataGatherer.write1f(gradData.mScale);
                append_gradient_tail(gradData, uniformDataGatherer);
            }
        }
        keyContext.addBlock(stageID);
    }

    private static void append_gradient_to_key(
            KeyContext keyContext,
            Gradient1DShader shader,
            float bias, float scale
    ) {
        var colorTransformer = new Gradient1DShader.ColorTransformer(
                shader, keyContext.dstInfo.colorSpace()
        );

        GradientData data = new GradientData(
                shader,
                bias, scale,
                shader.getTileMode(),
                colorTransformer.mColorCount,
                colorTransformer.mColors,
                colorTransformer.mPositions,
                shader.getInterpolation()
        );

        // Are we interpreting premul colors? We use this later to decide if we need to inject a final
        // premultiplication step.
        boolean inputPremul = GradientShader.Interpolation.isInPremul(
                shader.getInterpolation()
        );

        switch (GradientShader.Interpolation.getColorSpace(shader.getInterpolation())) {
            case GradientShader.Interpolation.kLab_ColorSpace,
                 GradientShader.Interpolation.kOKLab_ColorSpace,
                 GradientShader.Interpolation.kOKLabGamutMap_ColorSpace,
                 GradientShader.Interpolation.kHSL_ColorSpace,
                 GradientShader.Interpolation.kHWB_ColorSpace,
                 GradientShader.Interpolation.kLCH_ColorSpace,
                 GradientShader.Interpolation.kOKLCH_ColorSpace,
                 GradientShader.Interpolation.kOKLCHGamutMap_ColorSpace ->
                // In these exotic spaces, unpremul the colors if necessary (no need to do this if
                // they're all opaque), and then convert them to the intermediate ColorSpace
                    inputPremul = false;
        }

        // Now transform from intermediate to destination color space. There are two tricky things here:
        // 1) Normally, we'd pass dstInfo to the transform effect. However, if someone is rendering to
        //    a non-color managed surface (nullptr dst color space), and they chose to interpolate in
        //    any of the exotic spaces, that transform would do nothing, and leave the colors in
        //    whatever intermediate space we chose. That could even be something like XYZ, which will
        //    produce nonsense. So, in this particular case, we break Skia's rules, and treat a null
        //    destination as sRGB.
        ColorSpace dstColorSpace = keyContext.dstInfo.colorSpace();
        if (dstColorSpace == null) {
            dstColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        }

        // 2) Alpha type: We already tweaked our idea of "inputPremul" above -- if we interpolated in a
        //    non-RGB space, then we had to unpremul the colors to get proper conversion back to RGB.
        //    Our final goal is to emit premul colors, but under certain conditions we don't need to do
        //    anything to achieve that: i.e. its interpolating already premul colors (inputPremul) or
        //    all the colors have a = 1, in which case premul is a no op. Note that this allOpaque check
        //    is more permissive than SkGradientBaseShader's isOpaque(), since we can optimize away the
        //    make-premul op for two point conical gradients (which report false for isOpaque).
        int intermediateAlphaType = inputPremul ? ColorInfo.AT_PREMUL : ColorInfo.AT_UNPREMUL;
        int dstAlphaType = ColorInfo.AT_PREMUL;

        // The gradient block and colorSpace conversion block need to be combined
        // (via the Compose block) so that the localMatrix block can treat them as
        // one child.
        keyContext.addBlock(FragmentStage.kCompose_BuiltinStageID);

        appendGradientShaderBlock(
                keyContext,
                data);

        appendColorSpaceUniforms(
                colorTransformer.mIntermediateColorSpace, intermediateAlphaType,
                dstColorSpace, dstAlphaType, keyContext.uniformDataGatherer);
        keyContext.addBlock(FragmentStage.kColorSpaceXformColorFilter_BuiltinStageID);
    }

    public static void appendBlendMode(
            KeyContext keyContext,
            BlendMode bm
    ) {
        boolean coeffs = false;
        switch (bm) {
            case CLEAR -> {
                keyContext.uniformDataGatherer.write4f(0, 0, 0, 0);
                coeffs = true;
            }
            case SRC -> {
                keyContext.uniformDataGatherer.write4f(1, 0, 0, 0);
                coeffs = true;
            }
            case DST -> {
                keyContext.uniformDataGatherer.write4f(0, 1, 0, 0);
                coeffs = true;
            }
            case SRC_OVER -> {
                keyContext.uniformDataGatherer.write4f(1, 1, 0, -1);
                coeffs = true;
            }
            case DST_OVER -> {
                keyContext.uniformDataGatherer.write4f(1, 1, -1, 0);
                coeffs = true;
            }
            case SRC_IN -> {
                keyContext.uniformDataGatherer.write4f(0, 0, 1, 0);
                coeffs = true;
            }
            case DST_IN -> {
                keyContext.uniformDataGatherer.write4f(0, 0, 0, 1);
                coeffs = true;
            }
            case SRC_OUT -> {
                keyContext.uniformDataGatherer.write4f(1, 0, -1, 0);
                coeffs = true;
            }
            case DST_OUT -> {
                keyContext.uniformDataGatherer.write4f(0, 1, 0, -1);
                coeffs = true;
            }
            case SRC_ATOP -> {
                keyContext.uniformDataGatherer.write4f(0, 1, 1, -1);
                coeffs = true;
            }
            case DST_ATOP -> {
                keyContext.uniformDataGatherer.write4f(1, 0, -1, 1);
                coeffs = true;
            }
            case XOR -> {
                keyContext.uniformDataGatherer.write4f(1, 1, -1, -1);
                coeffs = true;
            }
        }
        // For non-fixed blends, coefficient blend modes are combined into the same shader snippet.
        // The remaining advanced blends are fairly unique in their implementations.
        // To avoid having to compile all of their shader code, they are treated as fixed blend modes.
        if (coeffs) {
            keyContext.addBlock(FragmentStage.kPorterDuffBlender_BuiltinStageID);
        } else {
            appendFixedBlendMode(
                    keyContext,
                    bm
            );
        }
    }

    public static void appendFixedBlendMode(
            KeyContext keyContext,
            BlendMode bm
    ) {
        keyContext.addBlock(FragmentStage.kFirstFixedBlend_BuiltinStageID + bm.ordinal());
    }

    /*public static void appendBlendModeBlenderBlock(
            KeyContext keyContext,
            KeyBuilder keyBuilder,
            UniformDataGatherer uniformDataGatherer,
            TextureDataGatherer textureDataGatherer,
            BlendMode bm
    ) {
        uniformDataGatherer.write1i(bm.ordinal());

        keyContext.addBlock(FragmentStage.kBlendModeBlender_BuiltinStageID);
    }*/

    public static void appendPrimitiveColorBlock(
            KeyContext keyContext
    ) {
        appendColorSpaceUniforms(ColorSpace.get(ColorSpace.Named.SRGB), ColorInfo.AT_PREMUL,
                keyContext.dstInfo.colorSpace(), ColorInfo.AT_PREMUL,
                keyContext.uniformDataGatherer);

        keyContext.addBlock(FragmentStage.kPrimitiveColor_BuiltinStageID);
    }

    /**
     * Add implementation details, for the specified backend, of this Shader to the
     * provided key.
     *
     * @param keyContext backend context for key creation
     * @param shader     This function is a no-op if shader is null.
     */
    public static void appendToKey(KeyContext keyContext,
                                   @RawPtr Shader shader) {
        if (shader == null) {
            return;
        }
        if (shader instanceof LocalMatrixShader) {
            append_to_key(keyContext,
                    (LocalMatrixShader) shader);
        } else if (shader instanceof ImageShader) {
            append_to_key(keyContext,
                    (ImageShader) shader);
        } else if (shader instanceof ColorShader) {
            append_to_key(keyContext,
                    (ColorShader) shader);
        } else if (shader instanceof Gradient1DShader) {
            append_to_key(keyContext,
                    (Gradient1DShader) shader);
        } else if (shader instanceof BlendShader) {
            append_to_key(keyContext,
                    (BlendShader) shader);
        } else if (shader instanceof RRectShader) {
            append_to_key(keyContext,
                    (RRectShader) shader);
        } else if (shader instanceof EmptyShader) {
            append_to_key(keyContext,
                    (EmptyShader) shader);
        }
    }

    public static void appendToKey(KeyContext keyContext,
                                   @RawPtr ColorFilter colorFilter) {
        if (colorFilter == null) {
            return;
        }
        if (colorFilter instanceof BlendModeColorFilter) {
            append_to_key(keyContext,
                    (BlendModeColorFilter) colorFilter);
        } else if (colorFilter instanceof ComposeColorFilter) {
            append_to_key(keyContext,
                    (ComposeColorFilter) colorFilter);
        }
    }

    public static void appendToKey(KeyContext keyContext,
                                   @RawPtr Blender blender) {
        if (blender == null) {
            return;
        }
        if (blender instanceof BlendMode) {
            append_to_key(keyContext,
                    (BlendMode) blender);
        }
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr ColorShader shader) {
        float[] color = shader.getColor();
        ColorSpace dstCS = keyContext.dstInfo.colorSpace();
        if (dstCS != null && !dstCS.isSrgb()) {
            ColorSpace.connect(ColorSpace.get(ColorSpace.Named.SRGB), dstCS)
                    .transformUnclamped(color);
        }
        // premul
        for (int i = 0; i < 3; i++) {
            color[i] *= color[3];
        }
        appendSolidColorShaderBlock(
                keyContext,
                color[0], color[1], color[2], color[3]
        );
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr ImageShader shader) {
        if (!(shader.getImage() instanceof GraniteImage imageToDraw)) {
            keyContext.addBlock(FragmentStage.kError_BuiltinStageID);
            return;
        }

        @RawPtr
        ImageProxyView view = imageToDraw.getImageProxyView();
        if (view == null) {
            keyContext.addBlock(FragmentStage.kError_BuiltinStageID);
            return;
        }

        final int srcAlphaType = imageToDraw.getAlphaType();
        final int dstAlphaType = ColorInfo.AT_PREMUL;
        if (imageToDraw.isAlphaOnly()) {
            keyContext.addBlock(FragmentStage.kBlend_BuiltinStageID);

            // src, ignore color space transform
            appendImageShaderBlock(keyContext,
                    shader.getSubset(),
                    shader.getTileModeX(),
                    shader.getTileModeY(),
                    shader.getSampling(),
                    view.getWidth(),
                    view.getHeight(),
                    srcAlphaType,
                    view);

            // dst
            appendRGBOpaquePaintColorBlock(
                    keyContext
            );

            appendFixedBlendMode(
                    keyContext,
                    BlendMode.DST_IN
            );
        } else {
            keyContext.addBlock(FragmentStage.kCompose_BuiltinStageID);

            appendImageShaderBlock(keyContext,
                    shader.getSubset(),
                    shader.getTileModeX(),
                    shader.getTileModeY(),
                    shader.getSampling(),
                    view.getWidth(),
                    view.getHeight(),
                    srcAlphaType,
                    view);

            appendColorSpaceUniforms(
                    imageToDraw.getColorSpace(),
                    srcAlphaType,
                    keyContext.dstInfo.colorSpace(),
                    dstAlphaType,
                    keyContext.uniformDataGatherer);
            keyContext.addBlock(FragmentStage.kColorSpaceXformColorFilter_BuiltinStageID);
        }
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr LocalMatrixShader shader) {
        @RawPtr
        var baseShader = shader.getBase();

        var matrix = new Matrix();
        if (baseShader instanceof ImageShader imageShader) {
            if (imageShader.getImage() instanceof GraniteImage textureImage) {
                var view = textureImage.getImageProxyView();
                if (view.getOrigin() == Engine.SurfaceOrigin.kLowerLeft) {
                    matrix.setScaleTranslate(1, -1, 0, view.getHeight());
                }
            }
        } else if (baseShader instanceof Gradient1DShader gradShader) {
            var gradMatrix = gradShader.getGradientMatrix();

            boolean res = gradMatrix.invert(matrix);
            assert res;
        }

        matrix.postConcat(shader.getLocalMatrix());

        appendLocalMatrixShaderBlock(keyContext,
                matrix);

        appendToKey(keyContext,
                baseShader);
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr Gradient1DShader shader) {
        switch (shader.asGradient()) {
            case Shader.GRADIENT_TYPE_LINEAR, Shader.GRADIENT_TYPE_RADIAL -> {
                append_gradient_to_key(
                        keyContext,
                        shader,
                        0.0f,
                        0.0f
                );
            }
            case Shader.GRADIENT_TYPE_ANGULAR -> {
                var angularGrad = (AngularGradient) shader;
                append_gradient_to_key(
                        keyContext,
                        shader,
                        angularGrad.getTBias(),
                        angularGrad.getTScale()
                );
            }
        }
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr BlendShader shader) {
        keyContext.addBlock(FragmentStage.kBlend_BuiltinStageID);

        appendToKey(
                keyContext,
                shader.getSrc()
        );

        appendToKey(
                keyContext,
                shader.getDst()
        );

        appendBlendMode(
                keyContext,
                shader.getMode()
        );
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr RRectShader shader) {
        keyContext.uniformDataGatherer.write4f(
                shader.getLeft(), shader.getTop(), shader.getRight(), shader.getBottom()
        );
        keyContext.uniformDataGatherer.write4f(
                shader.getTopLeftRadius(), shader.getTopRightRadius(),
                shader.getBottomRightRadius(), shader.getBottomLeftRadius()
        );
        float smooth = shader.getSmoothRadius();
        // smooth won't be NaN
        keyContext.uniformDataGatherer.write4f(
                shader.getCenterX(), shader.getCenterY(),
                smooth, shader.isInverseFill() ? -1.0f : 1.0f
        );

        keyContext.addBlock(FragmentStage.kAnalyticRRectShader_BuiltinStageID);
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr EmptyShader shader) {
        keyContext.addBlock(FragmentStage.kPassthrough_BuiltinStageID);
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr BlendModeColorFilter colorFilter) {
        float[] blendColor = colorFilter.getColor();
        PaintParams.prepareColorForDst(blendColor, keyContext.dstInfo);
        for (int i = 0; i < 3; i++) {
            blendColor[i] *= blendColor[3];
        }

        keyContext.addBlock(FragmentStage.kBlend_BuiltinStageID);

        // src
        appendSolidColorShaderBlock(
                keyContext,
                blendColor[0], blendColor[1], blendColor[2], blendColor[3]
        );

        // dst
        keyContext.addBlock(FragmentStage.kPassthrough_BuiltinStageID);

        appendBlendMode(
                keyContext,
                colorFilter.getMode()
        );
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr ComposeColorFilter colorFilter) {
        keyContext.addBlock(FragmentStage.kCompose_BuiltinStageID);

        appendToKey(
                keyContext,
                colorFilter.getBefore()
        );

        appendToKey(
                keyContext,
                colorFilter.getAfter()
        );
    }

    private static void append_to_key(KeyContext keyContext,
                                      @RawPtr BlendMode blender) {
        appendBlendMode(
                keyContext,
                blender
        );
    }
}
