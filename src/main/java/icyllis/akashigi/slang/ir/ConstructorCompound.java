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

import icyllis.akashigi.slang.ConstantFolder;

import javax.annotation.Nonnull;

/**
 * Represents a vector or matrix that is composed of other expressions, such as
 * `float3(pos.xy, 1)` or `float3x3(a.xyz, b.xyz, 0, 0, 1)`
 * <p>
 * These can contain a mix of scalars and aggregates. The total number of scalar values inside the
 * constructor must always match the type's scalar count. (e.g. `pos.xy` consumes two scalars.)
 * The inner values must have the same component type as the vector/matrix.
 */
public final class ConstructorCompound extends AnyConstructor {

    private ConstructorCompound(int position, Type type, Expression[] arguments) {
        super(position, ExpressionKind.kConstructorCompound, type, arguments);
    }

    @Nonnull
    public static Expression make(int position, Type type, Expression[] arguments) {
        int n = 0;
        // All the arguments must have matching component type.
        for (Expression arg : arguments) {
            Type argType = arg.getType();
            assert (argType.isScalar() || argType.isVector() || argType.isMatrix()) &&
                    (argType.getComponentType().matches(type.getComponentType()));
            n += argType.getComponents();
        }
        // The scalar count of the combined argument list must match the composite type's scalar count.
        assert type.getComponents() == n;

        // No-op compound constructors (containing a single argument of the same type) are eliminated.
        // (Even though this is a "compound constructor," we let scalars pass through here; it's
        // harmless to allow and simplifies call sites which need to narrow a vector and may sometimes
        // end up with a scalar.)
        if (arguments.length == 1) {
            Expression arg = arguments[0];
            if (type.isScalar()) {
                // A scalar "compound type" with a single scalar argument is a no-op and can be eliminated.
                // (Pedantically, this isn't a compound at all, but it's harmless to allow and simplifies
                // call sites which need to narrow a vector and may sometimes end up with a scalar.)
                assert (arg.getType().matches(type));
                arg.mPosition = position;
                return arg;
            }
            if (type.isVector() && arg.getType().matches(type)) {
                // A vector compound constructor containing a single argument of matching type can trivially
                // be eliminated.
                arg.mPosition = position;
                return arg;
            }
            // This is a meaningful single-argument compound constructor (e.g. vector-from-matrix,
            // matrix-from-vector).
        }
        // Beyond this point, the type must be a vector or matrix.
        assert (type.isVector() || type.isMatrix());

        // Replace constant variables with their corresponding values, so `float2(one, two)` can
        // compile down to `float2(1.0, 2.0)` (the latter is a compile-time constant).
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = ConstantFolder.makeConstantValueForVariable(position, arguments[i]);
        }

        return new ConstructorCompound(position, type, arguments);
    }

    @Nonnull
    @Override
    public Expression clone(int position) {
        return new ConstructorCompound(position, getType(), cloneArguments());
    }
}