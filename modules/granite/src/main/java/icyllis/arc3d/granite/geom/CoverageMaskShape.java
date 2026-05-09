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

package icyllis.arc3d.granite.geom;

import icyllis.arc3d.core.Rect2f;

/**
 * CoverageMaskShape represents a shape for which per-pixel coverage data comes from a
 * texture. This excludes font glyphs that are rendered to a persistent atlas, as those are
 * represented by the SubRunData geometry type.
 * <p>
 * Coverage masks are defined relative to an intermediate coordinate space between the final
 * device space and the original geometry and shading's local space. For atlases and simple cases
 * this intermediate space is pixel-aligned with the final device space, meaning only an integer
 * translation is necessary to align the mask with where the original geometry would have been
 * rendered into the device. In complex cases, the remaining transform may include rotation, skew,
 * or even perspective that has to be applied after some filter effect.
 * <p>
 * Regardless, the DrawParams that records the CoverageMaskShape stores this remaining transform as
 * the "local-to-device" tranform, i.e. "local" refers to the mask's coordinate space. The
 * CoverageMaskShape stores the original local-to-device inverse so that it can reconstruct coords
 * for shading. Like other Geometry types, the bounds() returned by CoverageMaskShape are relative
 * to its local space, so they are identical to its mask size.
 */
public class CoverageMaskShape {

    public void getBounds(Rect2f dest) {

    }
}
