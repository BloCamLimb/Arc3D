/*
 * Akashi GI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Akashi GI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Akashi GI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Akashi GI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.akashigi.slang.ir;

import icyllis.akashigi.slang.ThreadContext;
import icyllis.akashigi.slang.analysis.NodeVisitor;

import javax.annotation.Nonnull;

/**
 * A constant value. These can contain ints, floats, or booleans.
 */
public final class Literal extends Expression {

    private final double mValue;

    private Literal(int position, double value, Type type) {
        super(position, type);
        mValue = value;
    }

    @Nonnull
    public static Literal makeFloat(int position, float value) {
        return new Literal(position, value, ThreadContext.getInstance().getTypes().mFloat);
    }

    @Nonnull
    public static Literal makeFloat(int position, float value, Type type) {
        if (type.isFloat()) {
            return new Literal(position, value, type);
        }
        throw new IllegalArgumentException();
    }

    @Nonnull
    public static Literal makeInteger(int position, long value) {
        return new Literal(position, value, ThreadContext.getInstance().getTypes().mInt);
    }

    @Nonnull
    public static Literal makeInteger(int position, long value, Type type) {
        if (type.isInteger() && value >= type.getMinValue() && value <= type.getMaxValue()) {
            return new Literal(position, value, type);
        }
        throw new IllegalArgumentException();
    }

    @Nonnull
    public static Literal makeBoolean(int position, boolean value) {
        return new Literal(position, value ? 1 : 0, ThreadContext.getInstance().getTypes().mBool);
    }

    @Nonnull
    public static Literal makeBoolean(int position, boolean value, Type type) {
        if (type.isBoolean()) {
            return new Literal(position, value ? 1 : 0, type);
        }
        throw new IllegalArgumentException();
    }

    @Nonnull
    public static Literal make(int position, double value, Type type) {
        if (type.isFloat()) {
            return makeFloat(position, (float) value, type);
        }
        if (type.isInteger()) {
            return makeInteger(position, (long) value, type);
        }
        if (type.isBoolean()) {
            return makeBoolean(position, value != 0, type);
        }
        throw new IllegalArgumentException();
    }

    @Override
    public ExpressionKind getKind() {
        return ExpressionKind.LITERAL;
    }

    @Override
    public boolean accept(@Nonnull NodeVisitor visitor) {
        return visitor.visitLiteral(this);
    }

    @Override
    public boolean isLiteral() {
        return true;
    }

    public float getFloatValue() {
        assert (getType().isFloat());
        return (float) mValue;
    }

    public long getIntegerValue() {
        assert (getType().isInteger());
        return (long) mValue;
    }

    public boolean getBooleanValue() {
        assert (getType().isBoolean());
        return mValue != 0;
    }

    public double getValue() {
        return mValue;
    }

    @Nonnull
    @Override
    public Expression clone(int position) {
        return new Literal(position, mValue, getType());
    }

    @Nonnull
    @Override
    public String toString(int parentPrecedence) {
        if (getType().isFloat()) {
            return String.valueOf(getFloatValue());
        }
        if (getType().isInteger()) {
            return String.valueOf(getIntegerValue());
        }
        assert (getType().isBoolean());
        return String.valueOf(getBooleanValue());
    }
}
