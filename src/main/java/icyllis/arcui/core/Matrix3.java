/*
 * Arc UI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arc UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcui.core;

import icyllis.arcui.engine.DataUtils;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;

import static icyllis.arcui.core.MathUtil.*;

/**
 * Represents a 3x3 row-major matrix.
 */
//TODO type mask and others are WIP
@SuppressWarnings({"unused", "deprecation"})
public class Matrix3 implements Cloneable {

    public static final long OFFSET;

    static {
        try {
            OFFSET = DataUtils.UNSAFE.objectFieldOffset(Matrix3.class.getDeclaredField("m11"));
        } catch (Exception e) {
            throw new UnsupportedOperationException("No OFFSET", e);
        }
    }

    /**
     * TypeMask
     * <p>
     * Enum of bit fields for mask returned by getType().
     * Used to identify the complexity of Matrix3, to optimize performance.
     */
    public static final int
            Identity_Mask = 0,          // identity; all bits clear
            Translate_Mask = 0x01,      // translation
            Scale_Mask = 0x02,          // scale
            Affine_Mask = 0x04,         // shear or rotate
            Perspective_Mask = 0x08;    // perspective
    /**
     * Set if the matrix will map a rectangle to another rectangle. This
     * can be true if the matrix is scale-only, or rotates a multiple of
     * 90 degrees.
     * <p>
     * This bit will be set on identity matrices
     */
    private static final int AxisAligned_Mask = 0x10;
    /**
     * Set if the perspective bit is valid even though the rest of
     * the matrix is Unknown.
     */
    private static final int OnlyPerspectiveValid_Mask = 0x40;
    private static final int Unknown_Mask = 0x80;

    // sequential matrix elements, m(ij) (row, column)
    // directly using primitives will be faster than array in Java
    // [m11 m12 m13]
    // [m21 m22 m23]
    // [m31 m32 m33] <- [m31 m32] represents the origin
    public float m11;
    public float m12;
    public float m13;
    public float m21;
    public float m22;
    public float m23;
    public float m31;
    public float m32;
    public float m33;

    private int mTypeMask;

    /**
     * Create a zero matrix.
     */
    public Matrix3() {
    }

    /**
     * Create a new identity matrix.
     *
     * @return an identity matrix
     */
    @Nonnull
    public static Matrix3 identity() {
        final Matrix3 mat = new Matrix3();
        mat.m11 = mat.m22 = mat.m33 = 1.0f;
        return mat;
    }

    /**
     * Returns scale factor multiplied by x-axis input, contributing to x-axis output.
     * With mapPoints(), scales Point along the x-axis.
     *
     * @return horizontal scale factor
     */
    public float getScaleX() {
        return m11;
    }

    /**
     * Returns scale factor multiplied by y-axis input, contributing to y-axis output.
     * With mapPoints(), scales Point along the y-axis.
     *
     * @return vertical scale factor
     */
    public float getScaleY() {
        return m22;
    }

    /**
     * Returns scale factor multiplied by x-axis input, contributing to y-axis output.
     * With mapPoints(), shears Point along the y-axis.
     * Shearing both axes can rotate Point.
     *
     * @return vertical shear factor
     */
    public float getShearY() {
        return m12;
    }

    /**
     * Returns scale factor multiplied by y-axis input, contributing to x-axis output.
     * With mapPoints(), shears Point along the x-axis.
     * Shearing both axes can rotate Point.
     *
     * @return horizontal shear factor
     */
    public float getShearX() {
        return m21;
    }

    /**
     * Returns translation contributing to x-axis output.
     * With mapPoints(), moves Point along the x-axis.
     *
     * @return horizontal translation factor
     */
    public float getTranslateX() {
        return m31;
    }

    /**
     * Returns translation contributing to y-axis output.
     * With mapPoints(), moves Point along the y-axis.
     *
     * @return vertical translation factor
     */
    public float getTranslateY() {
        return m32;
    }

    /**
     * Returns factor scaling input x-axis relative to input y-axis.
     *
     * @return input x-axis perspective factor
     */
    public float getPerspX() {
        return m13;
    }

    /**
     * Returns factor scaling input y-axis relative to input x-axis.
     *
     * @return input y-axis perspective factor
     */
    public float getPerspY() {
        return m23;
    }

    /**
     * Returns a bit field describing the transformations the matrix may
     * perform. The bit field is computed conservatively, so it may include
     * false positives. For example, when Perspective_Mask is set, all
     * other bits are set.
     *
     * @return Identity_Mask, or combinations of: Translate_Mask, Scale_Mask,
     * Affine_Mask, Perspective_Mask
     */
    public int getType() {
        if ((mTypeMask & Unknown_Mask) != 0) {
            mTypeMask = computeTypeMask();
        }
        // only return the public masks
        return (mTypeMask & 0xF);
    }

    /**
     * Returns true if this matrix is identity.
     *
     * @return {@code true} if this matrix is identity.
     */
    public boolean isIdentity() {
        return getType() == Identity_Mask;
    }

    /**
     * Returns whether this matrix at most scales and translates.
     *
     * @return {@code true} if this matrix is scales, translates, or both.
     */
    public boolean isScaleTranslate() {
        return (getType() & ~(Scale_Mask | Translate_Mask)) == 0;
    }

    /**
     * Returns whether this matrix is identity, or translates.
     *
     * @return {@code true} if this matrix is identity, or translates
     */
    public boolean isTranslate() {
        return (getType() & ~(Translate_Mask)) == 0;
    }

    /**
     * Returns whether this matrix transforms rect to another rect. If true, this matrix is identity,
     * or/and scales, or mirrors on axes. In all cases, this matrix is affine and may also have translation.
     *
     * @return true if this matrix transform one rect into another
     */
    public boolean isAxisAligned() {
        if ((mTypeMask & Unknown_Mask) != 0) {
            mTypeMask = computeTypeMask();
        }
        return (mTypeMask & AxisAligned_Mask) != 0;
    }

    /**
     * Returns whether the this matrix contains perspective elements.
     *
     * @return true if this matrix is in most general form
     */
    public boolean hasPerspective() {
        if ((mTypeMask & Unknown_Mask) != 0 &&
                (mTypeMask & OnlyPerspectiveValid_Mask) == 0) {
            mTypeMask = computePerspectiveTypeMask();
        }
        return (mTypeMask & Perspective_Mask) != 0;
    }

    /**
     * Pre-multiply this matrix by the given matrix.
     * (mat3 * this)
     *
     * @param mat the matrix to multiply
     */
    public void preMultiply(@Nonnull Matrix3 mat) {
        final float f11 = mat.m11 * m11 + mat.m12 * m21 + mat.m13 * m31;
        final float f12 = mat.m11 * m12 + mat.m12 * m22 + mat.m13 * m32;
        final float f13 = mat.m11 * m13 + mat.m12 * m23 + mat.m13 * m33;
        final float f21 = mat.m21 * m11 + mat.m22 * m21 + mat.m23 * m31;
        final float f22 = mat.m21 * m12 + mat.m22 * m22 + mat.m23 * m32;
        final float f23 = mat.m21 * m13 + mat.m22 * m23 + mat.m23 * m33;
        final float f31 = mat.m31 * m11 + mat.m32 * m21 + mat.m33 * m31;
        final float f32 = mat.m31 * m12 + mat.m32 * m22 + mat.m33 * m32;
        final float f33 = mat.m31 * m13 + mat.m32 * m23 + mat.m33 * m33;
        m11 = f11;
        m12 = f12;
        m13 = f13;
        m21 = f21;
        m22 = f22;
        m23 = f23;
        m31 = f31;
        m32 = f32;
        m33 = f33;
    }

    /**
     * Post-multiply this matrix by the given matrix.
     * (this * mat3)
     *
     * @param mat the matrix to multiply
     */
    public void postMultiply(@Nonnull Matrix3 mat) {
        final float f11 = m11 * mat.m11 + m12 * mat.m21 + m13 * mat.m31;
        final float f12 = m11 * mat.m12 + m12 * mat.m22 + m13 * mat.m32;
        final float f13 = m11 * mat.m13 + m12 * mat.m23 + m13 * mat.m33;
        final float f21 = m21 * mat.m11 + m22 * mat.m21 + m23 * mat.m31;
        final float f22 = m21 * mat.m12 + m22 * mat.m22 + m23 * mat.m32;
        final float f23 = m21 * mat.m13 + m22 * mat.m23 + m23 * mat.m33;
        final float f31 = m31 * mat.m11 + m32 * mat.m21 + m33 * mat.m31;
        final float f32 = m31 * mat.m12 + m32 * mat.m22 + m33 * mat.m32;
        final float f33 = m31 * mat.m13 + m32 * mat.m23 + m33 * mat.m33;
        m11 = f11;
        m12 = f12;
        m13 = f13;
        m21 = f21;
        m22 = f22;
        m23 = f23;
        m31 = f31;
        m32 = f32;
        m33 = f33;
    }

    /**
     * Sets this matrix to identity.
     */
    public void setIdentity() {
        m11 = 1;
        m12 = 0;
        m13 = 0;
        m21 = 0;
        m22 = 1;
        m23 = 0;
        m31 = 0;
        m32 = 0;
        m33 = 1;
        mTypeMask = Identity_Mask | AxisAligned_Mask;
    }

    /**
     * Set this matrix elements to be given matrix.
     *
     * @param mat the matrix to copy from
     */
    public void set(@Nonnull Matrix3 mat) {
        m11 = mat.m11;
        m12 = mat.m12;
        m13 = mat.m13;
        m21 = mat.m21;
        m22 = mat.m22;
        m23 = mat.m23;
        m31 = mat.m31;
        m32 = mat.m32;
        m33 = mat.m33;
        mTypeMask = mat.mTypeMask;
    }

    /**
     * Get this matrix data, store them into an address (UNSAFE).
     * The data matches std140 layout so it is not tightly packed.
     * NOTE: This method does not perform memory security checks.
     *
     * @param p the pointer of the array to store
     */
    public void putAligned(long p) {
        final Unsafe unsafe = DataUtils.UNSAFE;
        final long offset = OFFSET;
        unsafe.putFloat(null, p, m11);
        unsafe.putLong(null, p + 4, unsafe.getLong(this, offset + 4));
        unsafe.putFloat(null, p + 16, m21); // 4N
        unsafe.putLong(null, p + 20, unsafe.getLong(this, offset + 16));
        unsafe.putFloat(null, p + 32, m31); // 4N
        unsafe.putLong(null, p + 36, unsafe.getLong(this, offset + 28));
    }

    /**
     * Similar to {@link #putAligned(long)}, but also compare the memory.
     *
     * @param p the pointer of the array to store
     * @return true if data is changed
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public boolean compareAndPutAligned(long p) {
        final Unsafe unsafe = DataUtils.UNSAFE;
        final long offset = OFFSET;
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            long src = offset + i * 12, dst = p + i * 16;
            int val0 = unsafe.getInt(this, src);
            if (changed || val0 != unsafe.getInt(null, dst)) {
                unsafe.putInt(null, dst, val0);
                changed = true;
            }
            long val1 = unsafe.getLong(this, src + 4);
            if (changed || val1 != unsafe.getLong(null, dst + 4)) {
                unsafe.putLong(null, dst + 4, val1);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Compute the determinant of this matrix.
     *
     * @return the determinant of this matrix
     */
    public float determinant() {
        return (m11 * m22 - m12 * m21) * m33 +
                (m13 * m21 - m11 * m23) * m32 +
                (m12 * m23 - m13 * m22) * m31;
    }

    /**
     * Compute the trace of this matrix.
     *
     * @return the trace of this matrix
     */
    public float trace() {
        return m11 + m22 + m33;
    }

    /**
     * Compute the transpose of this matrix.
     */
    public void transpose() {
        float t = m21;
        m21 = m12;
        m12 = t;
        t = m31;
        m31 = m13;
        m13 = t;
        t = m32;
        m32 = m23;
        m23 = t;
    }

    /**
     * Compute the inverse of this matrix. This matrix will be inverted
     * if it is invertible, otherwise it keeps the same as before.
     *
     * @return {@code true} if this matrix is invertible.
     */
    public boolean invert() {
        return invert(this);
    }

    /**
     * Compute the inverse of this matrix. The matrix will be inverted
     * if this matrix is invertible, otherwise it keeps the same as before.
     *
     * @param mat the destination matrix
     * @return {@code true} if this matrix is invertible.
     */
    public boolean invert(@Nonnull Matrix3 mat) {
        float a = m11 * m22 - m12 * m21;
        float b = m13 * m21 - m11 * m23;
        float c = m12 * m23 - m13 * m22;
        // calc the determinant
        float det = a * m33 + b * m32 + c * m31;
        if (approxZero(det)) {
            return false;
        }
        // calc algebraic cofactor and transpose
        det = 1.0f / det;
        float f11 = (m22 * m33 - m32 * m23) * det; // 11
        float f12 = (m32 * m13 - m12 * m33) * det; // -21
        float f21 = (m31 * m23 - m21 * m33) * det; // -12
        float f22 = (m11 * m33 - m31 * m13) * det; // 22
        float f31 = (m21 * m32 - m31 * m22) * det; // 13
        float f32 = (m31 * m12 - m11 * m32) * det; // -23
        m11 = f11;
        m12 = f12;
        m13 = c * det;
        m21 = f21;
        m22 = f22;
        m23 = b * det;
        m31 = f31;
        m32 = f32;
        m33 = a * det;
        return true;
    }

    /**
     * Translates this matrix by given changes. This is equivalent to
     * pre-multiplying by a translation matrix.
     *
     * @param dx the x-component of the translation
     * @param dy the y-component of the translation
     */
    public void preTranslate(float dx, float dy) {
        m31 += dx * m11 + dy * m21;
        m32 += dx * m12 + dy * m22;
        m33 += dx * m13 + dy * m23;
    }

    /**
     * Post-translates this matrix by given changes. This is equivalent to
     * post-multiplying by a translation matrix.
     *
     * @param dx the x-component of the translation
     * @param dy the y-component of the translation
     */
    public void postTranslate(float dx, float dy) {
        m11 += dx * m13;
        m12 += dy * m13;
        m21 += dx * m23;
        m22 += dy * m23;
        m31 += dx * m33;
        m32 += dy * m33;
    }

    /**
     * Map a rectangle points in the X-Y plane to get the maximum bounds.
     *
     * @param r the rectangle to transform
     */
    public void mapRect(@Nonnull RectF r) {
        float x1 = m11 * r.left + m21 * r.top + m31;
        float y1 = m12 * r.left + m22 * r.top + m32;
        float x2 = m11 * r.right + m21 * r.top + m31;
        float y2 = m12 * r.right + m22 * r.top + m32;
        float x3 = m11 * r.left + m21 * r.bottom + m31;
        float y3 = m12 * r.left + m22 * r.bottom + m32;
        float x4 = m11 * r.right + m21 * r.bottom + m31;
        float y4 = m12 * r.right + m22 * r.bottom + m32;
        if (hasPerspective()) {
            // project
            float w = 1.0f / (m13 * r.left + m23 * r.top + m33);
            x1 *= w;
            y1 *= w;
            w = 1.0f / (m13 * r.right + m23 * r.top + m33);
            x2 *= w;
            y2 *= w;
            w = 1.0f / (m13 * r.left + m23 * r.bottom + m33);
            x3 *= w;
            y3 *= w;
            w = 1.0f / (m13 * r.right + m23 * r.bottom + m33);
            x4 *= w;
            y4 *= w;
        }
        r.left = min(x1, x2, x3, x4);
        r.top = min(y1, y2, y3, y4);
        r.right = max(x1, x2, x3, x4);
        r.bottom = max(y1, y2, y3, y4);
    }

    /**
     * Map a rectangle points in the X-Y plane to get the maximum bounds.
     *
     * @param out the round values
     */
    public void mapRect(@Nonnull RectF r, @Nonnull Rect out) {
        mapRect(r.left, r.top, r.right, r.bottom, out);
    }

    /**
     * Map a rectangle points in the X-Y plane to get the maximum bounds.
     *
     * @param out the round values
     */
    public void mapRect(@Nonnull Rect r, @Nonnull Rect out) {
        mapRect(r.left, r.top, r.right, r.bottom, out);
    }

    /**
     * Map a rectangle points in the X-Y plane to get the maximum bounds.
     *
     * @param out the round values
     */
    public void mapRect(float l, float t, float r, float b, @Nonnull Rect out) {
        float x1 = m11 * l + m21 * t + m31;
        float y1 = m12 * l + m22 * t + m32;
        float x2 = m11 * r + m21 * t + m31;
        float y2 = m12 * r + m22 * t + m32;
        float x3 = m11 * l + m21 * b + m31;
        float y3 = m12 * l + m22 * b + m32;
        float x4 = m11 * r + m21 * b + m31;
        float y4 = m12 * r + m22 * b + m32;
        if (hasPerspective()) {
            // project
            float w = 1.0f / (m13 * l + m23 * t + m33);
            x1 *= w;
            y1 *= w;
            w = 1.0f / (m13 * r + m23 * t + m33);
            x2 *= w;
            y2 *= w;
            w = 1.0f / (m13 * l + m23 * b + m33);
            x3 *= w;
            y3 *= w;
            w = 1.0f / (m13 * r + m23 * b + m33);
            x4 *= w;
            y4 *= w;
        }
        out.left = Math.round(min(x1, x2, x3, x4));
        out.top = Math.round(min(y1, y2, y3, y4));
        out.right = Math.round(max(x1, x2, x3, x4));
        out.bottom = Math.round(max(y1, y2, y3, y4));
    }

    /**
     * Map a rectangle points in the X-Y plane to get the maximum bounds.
     *
     * @param result the round out values
     */
    public void mapRectOut(@Nonnull Rect r, @Nonnull Rect result) {
        mapRectOut(r.left, r.top, r.right, r.bottom, result);
    }

    /**
     * Map a rectangle points in the X-Y plane to get the maximum bounds.
     *
     * @param result the round out values
     */
    public void mapRectOut(float l, float t, float r, float b, @Nonnull Rect result) {
        float x1 = m11 * l + m21 * t + m31;
        float y1 = m12 * l + m22 * t + m32;
        float x2 = m11 * r + m21 * t + m31;
        float y2 = m12 * r + m22 * t + m32;
        float x3 = m11 * l + m21 * b + m31;
        float y3 = m12 * l + m22 * b + m32;
        float x4 = m11 * r + m21 * b + m31;
        float y4 = m12 * r + m22 * b + m32;
        if (hasPerspective()) {
            // project
            float w = 1.0f / (m13 * l + m23 * t + m33);
            x1 *= w;
            y1 *= w;
            w = 1.0f / (m13 * r + m23 * t + m33);
            x2 *= w;
            y2 *= w;
            w = 1.0f / (m13 * l + m23 * b + m33);
            x3 *= w;
            y3 *= w;
            w = 1.0f / (m13 * r + m23 * b + m33);
            x4 *= w;
            y4 *= w;
        }
        result.left = (int) Math.floor(min(x1, x2, x3, x4));
        result.top = (int) Math.floor(min(y1, y2, y3, y4));
        result.right = (int) Math.ceil(max(x1, x2, x3, x4));
        result.bottom = (int) Math.ceil(max(y1, y2, y3, y4));
    }

    public void mapPoint(float[] p) {
        float x1 = m11 * p[0] + m21 * p[1] + m31;
        float y1 = m12 * p[0] + m22 * p[1] + m32;
        // project
        float w = 1.0f / (m13 * p[0] + m23 * p[1] + m33);
        x1 *= w;
        y1 *= w;
        p[0] = x1;
        p[1] = y1;
    }

    /**
     * If the last column of the matrix is [0, 0, not_one]^T, we will treat the matrix as if it
     * is in perspective, even though it stills behaves like its affine. If we divide everything
     * by the not_one value, then it will behave the same, but will be treated as affine,
     * and therefore faster (e.g. clients can forward-difference calculations).
     */
    public void normalizePerspective() {
        if (m33 != 1 && m33 != 0 && m13 == 0 && m23 == 0) {
            float inv = 1.0f / m33;
            m11 *= inv;
            m12 *= inv;
            m21 *= inv;
            m22 *= inv;
            m31 *= inv;
            m32 *= inv;
            m33 = 1.0f;
        }
    }

    public boolean equal(@Nonnull Matrix4 mat) {
        return m11 == mat.m11 &&
                m12 == mat.m12 &&
                m13 == mat.m13 &&
                m21 == mat.m21 &&
                m22 == mat.m22 &&
                m23 == mat.m23 &&
                m31 == mat.m31 &&
                m32 == mat.m32 &&
                m33 == mat.m33;
    }

    /**
     * Returns true if this matrix is exactly equal to the given matrix.
     * In this case, -0.f is not equal to 0.f, NaN is equal to NaN.
     *
     * @param m the matrix to compare.
     * @return {@code true} if this matrix is equivalent to other matrix.
     */
    public boolean equals(@Nonnull Matrix3 m) {
        if (this == m) return true;
        if (Float.floatToIntBits(m.m11) != Float.floatToIntBits(m11)) return false;
        if (Float.floatToIntBits(m.m12) != Float.floatToIntBits(m12)) return false;
        if (Float.floatToIntBits(m.m13) != Float.floatToIntBits(m13)) return false;
        if (Float.floatToIntBits(m.m21) != Float.floatToIntBits(m21)) return false;
        if (Float.floatToIntBits(m.m22) != Float.floatToIntBits(m22)) return false;
        if (Float.floatToIntBits(m.m23) != Float.floatToIntBits(m23)) return false;
        if (Float.floatToIntBits(m.m31) != Float.floatToIntBits(m31)) return false;
        if (Float.floatToIntBits(m.m32) != Float.floatToIntBits(m32)) return false;
        return Float.floatToIntBits(m.m33) == Float.floatToIntBits(m33);
    }

    private int computeTypeMask() {
        int mask = 0;

        if (m13 != 0 || m23 != 0 || m33 != 1) {
            // Once it is determined that this is a perspective transform,
            // all other flags are moot as far as optimizations are concerned.
            return Translate_Mask |
                    Scale_Mask |
                    Affine_Mask |
                    Perspective_Mask;
        }

        if (m31 != 0 || m32 != 0) {
            mask |= Translate_Mask;
        }

        boolean shearX = m21 != 0;
        boolean shearY = m12 != 0;

        if (shearX || shearY) {
            // The shear components may be scale-inducing, unless we are dealing
            // with a pure rotation.  Testing for a pure rotation is expensive,
            // so we opt for being conservative by always setting the scale bit.
            // along with affine.
            // By doing this, we are also ensuring that matrices have the same
            // type masks as their inverses.
            mask |= Affine_Mask | Scale_Mask;

            // For axis aligned, in the affine case, we only need check that
            // the primary diagonal is all zeros and that the secondary diagonal
            // is all non-zero.
            if (shearX && shearY && m11 == 0 && m22 == 0) {
                mask |= AxisAligned_Mask;
            }
        } else {
            // Only test for scale explicitly if not affine, since affine sets the
            // scale bit.
            if (m11 != 1 || m22 != 1) {
                mask |= Scale_Mask;
            }

            // Not affine, therefore we already know secondary diagonal is
            // all zeros, so we just need to check that primary diagonal is
            // all non-zero.

            // record if the (p)rimary diagonal is all non-zero
            if (m11 != 0 && m22 != 0) {
                mask |= AxisAligned_Mask;
            }
        }

        return mask;
    }

    private int computePerspectiveTypeMask() {
        if (m13 != 0 || m23 != 0 || m33 != 1) {
            // If this is a perspective transform, we return true for all other
            // transform flags - this does not disable any optimizations, respects
            // the rule that the type mask must be conservative, and speeds up
            // type mask computation.
            return Translate_Mask |
                    Scale_Mask |
                    Affine_Mask |
                    Perspective_Mask;
        }

        return OnlyPerspectiveValid_Mask | Unknown_Mask;
    }

    /**
     * Returns whether this matrix is exactly equal to some other object.
     *
     * @param o the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj values.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Matrix3 m = (Matrix3) o;

        if (Float.floatToIntBits(m.m11) != Float.floatToIntBits(m11)) return false;
        if (Float.floatToIntBits(m.m12) != Float.floatToIntBits(m12)) return false;
        if (Float.floatToIntBits(m.m13) != Float.floatToIntBits(m13)) return false;
        if (Float.floatToIntBits(m.m21) != Float.floatToIntBits(m21)) return false;
        if (Float.floatToIntBits(m.m22) != Float.floatToIntBits(m22)) return false;
        if (Float.floatToIntBits(m.m23) != Float.floatToIntBits(m23)) return false;
        if (Float.floatToIntBits(m.m31) != Float.floatToIntBits(m31)) return false;
        if (Float.floatToIntBits(m.m32) != Float.floatToIntBits(m32)) return false;
        return Float.floatToIntBits(m.m33) == Float.floatToIntBits(m33);
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(m11);
        result = 31 * result + Float.floatToIntBits(m12);
        result = 31 * result + Float.floatToIntBits(m13);
        result = 31 * result + Float.floatToIntBits(m21);
        result = 31 * result + Float.floatToIntBits(m22);
        result = 31 * result + Float.floatToIntBits(m23);
        result = 31 * result + Float.floatToIntBits(m31);
        result = 31 * result + Float.floatToIntBits(m32);
        result = 31 * result + Float.floatToIntBits(m33);
        return result;
    }

    @Override
    public String toString() {
        return String.format("""
                        Matrix3:
                        %10.5f %10.5f %10.5f
                        %10.5f %10.5f %10.5f
                        %10.5f %10.5f %10.5f
                        """,
                m11, m12, m13,
                m21, m22, m23,
                m31, m32, m33);
    }

    /**
     * @return a deep copy of this matrix
     */
    @Nonnull
    @Override
    public Matrix3 clone() {
        try {
            return (Matrix3) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
