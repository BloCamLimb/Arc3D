/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.core.shaders;

import icyllis.arc3d.core.*;

public final class LocalMatrixShader extends RefCnt implements Shader {

    @SharedPtr
    private final Shader mBase;
    private final Matrix mLocalMatrix;

    LocalMatrixShader(@SharedPtr Shader base, Matrix localMatrix) {
        localMatrix.getType(); // Precache so reads are threadsafe.
        mBase = base;
        mLocalMatrix = localMatrix;
    }

    @Override
    protected void deallocate() {
        mBase.unref();
    }

    @Override
    public boolean isOpaque() {
        return mBase.isOpaque();
    }

    @Override
    public boolean isConstant() {
        return mBase.isConstant();
    }

    @RawPtr
    public Shader getBase() {
        return mBase;
    }

    public Matrixc getLocalMatrix() {
        return mLocalMatrix;
    }
}
