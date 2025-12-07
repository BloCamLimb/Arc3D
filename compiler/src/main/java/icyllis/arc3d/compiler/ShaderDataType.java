/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.compiler;

import icyllis.arc3d.compiler.tree.Type;
import org.jspecify.annotations.NonNull;

/**
 * Types of shader-language-specific boxed variables we can create, shared constants.
 * <p>
 * Types are represented as uint8_t (or int8_t) enum constants, without using
 * a {@link icyllis.arc3d.compiler.tree.Type} object.
 */
public final class ShaderDataType {

    public static final byte
            kVoid = 0,
            kBool = 1,
            kBool2 = 2,
            kBool3 = 3,
            kBool4 = 4,
            kMin16Int = 5,
            kMin16Int2 = 6,
            kMin16Int3 = 7,
            kMin16Int4 = 8,
            kMin16UInt = 9,
            kMin16UInt2 = 10,
            kMin16UInt3 = 11,
            kMin16UInt4 = 12,
            kInt = 13,
            kInt2 = 14,
            kInt3 = 15,
            kInt4 = 16,
            kUInt = 17,
            kUInt2 = 18,
            kUInt3 = 19,
            kUInt4 = 20,
            kMin16Float = 21,
            kMin16Float2 = 22,
            kMin16Float3 = 23,
            kMin16Float4 = 24,
            kFloat = 25,
            kFloat2 = 26,
            kFloat3 = 27,
            kFloat4 = 28,
            kMin16Float2x2 = 29,
            kMin16Float2x3 = 30,
            kMin16Float2x4 = 31,
            kMin16Float3x2 = 32,
            kMin16Float3x3 = 33,
            kMin16Float3x4 = 34,
            kMin16Float4x2 = 35,
            kMin16Float4x3 = 36,
            kMin16Float4x4 = 37,
            kFloat2x2 = 38,
            kFloat2x3 = 39,
            kFloat2x4 = 40,
            kFloat3x2 = 41,
            kFloat3x3 = 42,
            kFloat3x4 = 43,
            kFloat4x2 = 44,
            kFloat4x3 = 45,
            kFloat4x4 = 46,
            kImage1D = 47,
            kImage2D = 48,
            kImage3D = 49,
            kImageCube = 50,
            kImageBuffer = 51,
            kImage1DArray = 52,
            kImage2DArray = 53,
            kImageCubeArray = 54,
            kImage2DMS = 55,
            kImage2DMSArray = 56,
            kSubpassInput = 57,
            kSubpassInputMS = 58,
            kTexture1D = 59,
            kTexture2D = 60,
            kTexture3D = 61,
            kTextureCube = 62,
            kTextureBuffer = 63,
            kTexture1DArray = 64,
            kTexture2DArray = 65,
            kTextureCubeArray = 66,
            kTexture2DMS = 67,
            kTexture2DMSArray = 68,
            kSampler1D = 69,
            kSampler2D = 70,
            kSampler3D = 71,
            kSamplerCube = 72,
            kSamplerBuffer = 73,
            kSampler1DArray = 74,
            kSampler2DArray = 75,
            kSamplerCubeArray = 76,
            kSampler2DMS = 77,
            kSampler2DMSArray = 78,
            kSampler1DShadow = 79,
            kSampler2DShadow = 80,
            kSamplerCubeShadow = 81,
            kSampler1DArrayShadow = 82,
            kSampler2DArrayShadow = 83,
            kSamplerCubeArrayShadow = 84;
    public static final byte kLast = kSamplerCubeArrayShadow;

    // Debug tool.
    public static boolean checkType(byte type) {
        return type >= 0 && type <= kLast;
    }

    /**
     * Is the shading language type float (including vectors/matrices)?
     */
    public static boolean isFloatType(byte type) {
        switch (type) {
            case kMin16Float:
            case kMin16Float2:
            case kMin16Float3:
            case kMin16Float4:
            case kFloat:
            case kFloat2:
            case kFloat3:
            case kFloat4:
            case kMin16Float2x2:
            case kMin16Float2x3:
            case kMin16Float2x4:
            case kMin16Float3x2:
            case kMin16Float3x3:
            case kMin16Float3x4:
            case kMin16Float4x2:
            case kMin16Float4x3:
            case kMin16Float4x4:
            case kFloat2x2:
            case kFloat2x3:
            case kFloat2x4:
            case kFloat3x2:
            case kFloat3x3:
            case kFloat3x4:
            case kFloat4x2:
            case kFloat4x3:
            case kFloat4x4:
                return true;

            default:
                return false;
        }
    }

    /**
     * Is the shading language type integral (including vectors)?
     */
    public static boolean isIntegralType(byte type) {
        switch (type) {
            case kMin16Int:
            case kMin16Int2:
            case kMin16Int3:
            case kMin16Int4:
            case kMin16UInt:
            case kMin16UInt2:
            case kMin16UInt3:
            case kMin16UInt4:
            case kInt:
            case kInt2:
            case kInt3:
            case kInt4:
            case kUInt:
            case kUInt2:
            case kUInt3:
            case kUInt4:
                return true;

            default:
                return false;
        }
    }

    /**
     * Is the shading language type boolean (including vectors)?
     */
    public static boolean isBooleanType(byte type) {
        switch (type) {
            case kBool:
            case kBool2:
            case kBool3:
            case kBool4:
                return true;

            default:
                return false;
        }
    }

    /**
     * Is the shading language type supported as a uniform block member.
     */
    public static boolean canBeUniformValue(byte type) {
        return isFloatType(type) || isIntegralType(type);
    }

    /**
     * If the type represents scalar/vector/matrix, return the number of rows, else -1.
     */
    public static int rowCount(byte type) {
        switch (type) {
            case kBool:
            case kMin16Int:
            case kMin16UInt:
            case kInt:
            case kUInt:
            case kMin16Float:
            case kFloat:
                return 1;

            case kBool2:
            case kMin16Int2:
            case kMin16UInt2:
            case kInt2:
            case kUInt2:
            case kMin16Float2:
            case kFloat2:
            case kMin16Float2x2:
            case kMin16Float3x2:
            case kMin16Float4x2:
            case kFloat2x2:
            case kFloat3x2:
            case kFloat4x2:
                return 2;

            case kBool3:
            case kMin16Int3:
            case kMin16UInt3:
            case kInt3:
            case kUInt3:
            case kMin16Float3:
            case kFloat3:
            case kMin16Float2x3:
            case kMin16Float3x3:
            case kMin16Float4x3:
            case kFloat2x3:
            case kFloat3x3:
            case kFloat4x3:
                return 3;

            case kBool4:
            case kMin16Int4:
            case kMin16UInt4:
            case kInt4:
            case kUInt4:
            case kMin16Float4:
            case kFloat4:
            case kMin16Float2x4:
            case kMin16Float3x4:
            case kMin16Float4x4:
            case kFloat2x4:
            case kFloat3x4:
            case kFloat4x4:
                return 4;

            default:
                return -1;
        }
    }

    /**
     * If the type represents a square matrix, return its order; otherwise, -1.
     */
    public static int matrixOrder(byte type) {
        switch (type) {
            case kMin16Float2x2:
            case kFloat2x2:
                return 2;

            case kMin16Float3x3:
            case kFloat3x3:
                return 3;

            case kMin16Float4x4:
            case kFloat4x4:
                return 4;

            default:
                return -1;
        }
    }

    public static boolean isMatrixType(byte type) {
        switch (type) {
            case kMin16Float2x2:
            case kMin16Float2x3:
            case kMin16Float2x4:
            case kMin16Float3x2:
            case kMin16Float3x3:
            case kMin16Float3x4:
            case kMin16Float4x2:
            case kMin16Float4x3:
            case kMin16Float4x4:
            case kFloat2x2:
            case kFloat2x3:
            case kFloat2x4:
            case kFloat3x2:
            case kFloat3x3:
            case kFloat3x4:
            case kFloat4x2:
            case kFloat4x3:
            case kFloat4x4:
                return true;

            default:
                return false;
        }
    }

    /**
     * Returns the number of locations take up by a given Type.
     * <p>
     * 16-bit scalar and vector types, and 32-bit scalar and vector types
     * consume a single location.
     * <p>
     * n x m 16- or 32-bit matrix types consume n consecutive locations.
     * <p>
     * Returns 0 for opaque types.
     */
    public static int locationCount(byte type) {
        switch (type) {
            case kBool:
            case kBool2:
            case kBool3:
            case kBool4:
            case kMin16Int:
            case kMin16Int2:
            case kMin16Int3:
            case kMin16Int4:
            case kMin16UInt:
            case kMin16UInt2:
            case kMin16UInt3:
            case kMin16UInt4:
            case kInt:
            case kInt2:
            case kInt3:
            case kInt4:
            case kUInt:
            case kUInt2:
            case kUInt3:
            case kUInt4:
            case kMin16Float:
            case kMin16Float2:
            case kMin16Float3:
            case kMin16Float4:
            case kFloat:
            case kFloat2:
            case kFloat3:
            case kFloat4:
                return 1;

            case kMin16Float2x2:
            case kMin16Float2x3:
            case kMin16Float2x4:
            case kFloat2x2:
            case kFloat2x3:
            case kFloat2x4:
                return 2;

            case kMin16Float3x2:
            case kMin16Float3x3:
            case kMin16Float3x4:
            case kFloat3x2:
            case kFloat3x3:
            case kFloat3x4:
                return 3;

            case kMin16Float4x2:
            case kMin16Float4x3:
            case kMin16Float4x4:
            case kFloat4x2:
            case kFloat4x3:
            case kFloat4x4:
                return 4;

            default:
                return 0;
        }
    }

    public static Type toType(byte type, BuiltinTypes types) {
        return switch (type) {
            case kVoid -> types.mVoid;
            case kBool -> types.mBool;
            case kBool2 -> types.mBool2;
            case kBool3 -> types.mBool3;
            case kBool4 -> types.mBool4;
            case kMin16Int -> types.mMin16Int;
            case kMin16Int2 -> types.mMin16Int2;
            case kMin16Int3 -> types.mMin16Int3;
            case kMin16Int4 -> types.mMin16Int4;
            case kMin16UInt -> types.mMin16UInt;
            case kMin16UInt2 -> types.mMin16UInt2;
            case kMin16UInt3 -> types.mMin16UInt3;
            case kMin16UInt4 -> types.mMin16UInt4;
            case kInt -> types.mInt;
            case kInt2 -> types.mInt2;
            case kInt3 -> types.mInt3;
            case kInt4 -> types.mInt4;
            case kUInt -> types.mUInt;
            case kUInt2 -> types.mUInt2;
            case kUInt3 -> types.mUInt3;
            case kUInt4 -> types.mUInt4;
            case kMin16Float -> types.mMin16Float;
            case kMin16Float2 -> types.mMin16Float2;
            case kMin16Float3 -> types.mMin16Float3;
            case kMin16Float4 -> types.mMin16Float4;
            case kFloat -> types.mFloat;
            case kFloat2 -> types.mFloat2;
            case kFloat3 -> types.mFloat3;
            case kFloat4 -> types.mFloat4;
            case kMin16Float2x2 -> types.mMin16Float2x2;
            case kMin16Float2x3 -> types.mMin16Float2x3;
            case kMin16Float2x4 -> types.mMin16Float2x4;
            case kMin16Float3x2 -> types.mMin16Float3x2;
            case kMin16Float3x3 -> types.mMin16Float3x3;
            case kMin16Float3x4 -> types.mMin16Float3x4;
            case kMin16Float4x2 -> types.mMin16Float4x2;
            case kMin16Float4x3 -> types.mMin16Float4x3;
            case kMin16Float4x4 -> types.mMin16Float4x4;
            case kFloat2x2 -> types.mFloat2x2;
            case kFloat2x3 -> types.mFloat2x3;
            case kFloat2x4 -> types.mFloat2x4;
            case kFloat3x2 -> types.mFloat3x2;
            case kFloat3x3 -> types.mFloat3x3;
            case kFloat3x4 -> types.mFloat3x4;
            case kFloat4x2 -> types.mFloat4x2;
            case kFloat4x3 -> types.mFloat4x3;
            case kFloat4x4 -> types.mFloat4x4;
            case kImage1D -> types.mImage1D;
            case kImage2D -> types.mImage2D;
            case kImage3D -> types.mImage3D;
            case kImageCube -> types.mImageCube;
            case kImageBuffer -> types.mImageBuffer;
            case kImage1DArray -> types.mImage1DArray;
            case kImage2DArray -> types.mImage2DArray;
            case kImageCubeArray -> types.mImageCubeArray;
            case kImage2DMS -> types.mImage2DMS;
            case kImage2DMSArray -> types.mImage2DMSArray;
            case kSubpassInput -> types.mSubpassInput;
            case kSubpassInputMS -> types.mSubpassInputMS;
            case kTexture1D -> types.mTexture1D;
            case kTexture2D -> types.mTexture2D;
            case kTexture3D -> types.mTexture3D;
            case kTextureCube -> types.mTextureCube;
            case kTextureBuffer -> types.mTextureBuffer;
            case kTexture1DArray -> types.mTexture1DArray;
            case kTexture2DArray -> types.mTexture2DArray;
            case kTextureCubeArray -> types.mTextureCubeArray;
            case kTexture2DMS -> types.mTexture2DMS;
            case kTexture2DMSArray -> types.mTexture2DMSArray;
            case kSampler1D -> types.mSampler1D;
            case kSampler2D -> types.mSampler2D;
            case kSampler3D -> types.mSampler3D;
            case kSamplerCube -> types.mSamplerCube;
            case kSamplerBuffer -> types.mSamplerBuffer;
            case kSampler1DArray -> types.mSampler1DArray;
            case kSampler2DArray -> types.mSampler2DArray;
            case kSamplerCubeArray -> types.mSamplerCubeArray;
            case kSampler2DMS -> types.mSampler2DMS;
            case kSampler2DMSArray -> types.mSampler2DMSArray;
            case kSampler1DShadow -> types.mSampler1DShadow;
            case kSampler2DShadow -> types.mSampler2DShadow;
            case kSamplerCubeShadow -> types.mSamplerCubeShadow;
            case kSampler1DArrayShadow -> types.mSampler1DArrayShadow;
            case kSampler2DArrayShadow -> types.mSampler2DArrayShadow;
            case kSamplerCubeArrayShadow -> types.mSamplerCubeArrayShadow;
            default -> types.mInvalid;
        };
    }

    @NonNull
    public static String typeString(byte type) {
        return switch (type) {
            case kVoid -> "void";
            case kBool -> "bool";
            case kBool2 -> "bool2";
            case kBool3 -> "bool3";
            case kBool4 -> "bool4";
            case kMin16Int -> "min16int";
            case kMin16Int2 -> "min16int2";
            case kMin16Int3 -> "min16int3";
            case kMin16Int4 -> "min16int4";
            case kMin16UInt -> "min16uint";
            case kMin16UInt2 -> "min16uint2";
            case kMin16UInt3 -> "min16uint3";
            case kMin16UInt4 -> "min16uint4";
            case kInt -> "int";
            case kInt2 -> "int2";
            case kInt3 -> "int3";
            case kInt4 -> "int4";
            case kUInt -> "uint";
            case kUInt2 -> "uint2";
            case kUInt3 -> "uint3";
            case kUInt4 -> "uint4";
            case kMin16Float -> "min16float";
            case kMin16Float2 -> "min16float2";
            case kMin16Float3 -> "min16float3";
            case kMin16Float4 -> "min16float4";
            case kFloat -> "float";
            case kFloat2 -> "float2";
            case kFloat3 -> "float3";
            case kFloat4 -> "float4";
            case kMin16Float2x2 -> "min16float2x2";
            case kMin16Float2x3 -> "min16float2x3";
            case kMin16Float2x4 -> "min16float2x4";
            case kMin16Float3x2 -> "min16float3x2";
            case kMin16Float3x3 -> "min16float3x3";
            case kMin16Float3x4 -> "min16float3x4";
            case kMin16Float4x2 -> "min16float4x2";
            case kMin16Float4x3 -> "min16float4x3";
            case kMin16Float4x4 -> "min16float4x4";
            case kFloat2x2 -> "float2x2";
            case kFloat2x3 -> "float2x3";
            case kFloat2x4 -> "float2x4";
            case kFloat3x2 -> "float3x2";
            case kFloat3x3 -> "float3x3";
            case kFloat3x4 -> "float3x4";
            case kFloat4x2 -> "float4x2";
            case kFloat4x3 -> "float4x3";
            case kFloat4x4 -> "float4x4";
            case kImage1D -> "image1D";
            case kImage2D -> "image2D";
            case kImage3D -> "image3D";
            case kImageCube -> "imageCube";
            case kImageBuffer -> "imageBuffer";
            case kImage1DArray -> "image1DArray";
            case kImage2DArray -> "image2DArray";
            case kImageCubeArray -> "imageCubeArray";
            case kImage2DMS -> "image2DMS";
            case kImage2DMSArray -> "image2DMSArray";
            case kSubpassInput -> "subpassInput";
            case kSubpassInputMS -> "subpassInputMS";
            case kTexture1D -> "texture1D";
            case kTexture2D -> "texture2D";
            case kTexture3D -> "texture3D";
            case kTextureCube -> "textureCube";
            case kTextureBuffer -> "textureBuffer";
            case kTexture1DArray -> "texture1DArray";
            case kTexture2DArray -> "texture2DArray";
            case kTextureCubeArray -> "textureCubeArray";
            case kTexture2DMS -> "texture2DMS";
            case kTexture2DMSArray -> "texture2DMSArray";
            case kSampler1D -> "sampler1D";
            case kSampler2D -> "sampler2D";
            case kSampler3D -> "sampler3D";
            case kSamplerCube -> "samplerCube";
            case kSamplerBuffer -> "samplerBuffer";
            case kSampler1DArray -> "sampler1DArray";
            case kSampler2DArray -> "sampler2DArray";
            case kSamplerCubeArray -> "samplerCubeArray";
            case kSampler2DMS -> "sampler2DMS";
            case kSampler2DMSArray -> "sampler2DMSArray";
            case kSampler1DShadow -> "sampler1DShadow";
            case kSampler2DShadow -> "sampler2DShadow";
            case kSamplerCubeShadow -> "samplerCubeShadow";
            case kSampler1DArrayShadow -> "sampler1DArrayShadow";
            case kSampler2DArrayShadow -> "sampler2DArrayShadow";
            case kSamplerCubeArrayShadow -> "samplerCubeArrayShadow";
            default -> throw new IllegalArgumentException(String.valueOf(type));
        };
    }
}
