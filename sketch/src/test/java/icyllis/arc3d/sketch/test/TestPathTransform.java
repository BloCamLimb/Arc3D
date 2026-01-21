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

package icyllis.arc3d.sketch.test;

import icyllis.arc3d.core.*;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Path;
import icyllis.arc3d.sketch.PathBuilder;

public class TestPathTransform {

    //TODO
    public static void main(String[] args) {
        Path path = new PathBuilder()
        .moveTo(20, 0)
        .lineTo(20, 160)
        .cubicTo(160, 130, 120, 100, 190, 60)
                .build();

        Matrix matrix = new Matrix();
        matrix.setScale(0.4f, 0.6f);
        matrix.preShear(0.7f, 0);
        matrix.preRotate(MathUtil.PI_O_6);

        path.transform(matrix);
        path.forEach(TestPathUtils.PRINTER);

        TestPathUtils.writePath(path, true, "test_path_transformed.png");
    }
}
