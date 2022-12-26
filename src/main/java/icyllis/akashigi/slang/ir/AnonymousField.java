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

import javax.annotation.Nonnull;

/**
 * A symbol which should be interpreted as a field access. Fields are added to the symbolTable
 * whenever a bare reference to an identifier should refer to a struct field; in GLSL, this is the
 * result of declaring anonymous interface blocks.
 */
public final class AnonymousField extends Symbol {

    private final Variable mContainer;
    private final int mFieldIndex;

    public AnonymousField(int position, Variable container, int fieldIndex) {
        super(position, SymbolKind.kAnonymousField,
                container.getType().getFields()[fieldIndex].name());
        mContainer = container;
        mFieldIndex = fieldIndex;
    }

    @Nonnull
    @Override
    public Type getType() {
        return mContainer.getType().getFields()[mFieldIndex].type();
    }

    public int getFieldIndex() {
        return mFieldIndex;
    }

    public Variable getContainer() {
        return mContainer;
    }

    @Nonnull
    @Override
    public String toString() {
        return mContainer.toString() + "." + getName();
    }
}