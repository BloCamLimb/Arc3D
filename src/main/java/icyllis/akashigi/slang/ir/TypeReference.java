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

import javax.annotation.Nonnull;

/**
 * Represents an identifier referring to a type. This is an intermediate value: TypeReferences are
 * always eventually replaced by Constructors in valid programs.
 */
public final class TypeReference extends Expression {

    private final Type mValue;

    private TypeReference(int position, Type value, Type type) {
        super(position, ExpressionKind.kTypeReference, type);
        mValue = value;
    }

    @Nonnull
    public static Expression make(int position, Type value) {
        ThreadContext context = ThreadContext.getInstance();
        return new TypeReference(position, value, context.getTypes().mInvalid);
    }

    public Type getValue() {
        return mValue;
    }

    @Nonnull
    @Override
    public Expression clone(int position) {
        return new TypeReference(position, mValue, getType());
    }

    @Nonnull
    @Override
    public String toString(int parentPrecedence) {
        return mValue.getName();
    }
}