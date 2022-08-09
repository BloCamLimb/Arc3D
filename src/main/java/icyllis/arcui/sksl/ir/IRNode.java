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

package icyllis.arcui.sksl.ir;

import javax.annotation.Nonnull;

/**
 * Represents a node in the intermediate representation (IR) tree. The IR is a fully-resolved
 * version of the program (all types determined, everything validated), ready for code generation.
 */
public abstract class IRNode {

    // position of this element within the program being compiled, for error reporting purposes
    public final int mStart;
    public final int mEnd;

    protected final int mKind;

    protected IRNode(int start, int end, int kind) {
        assert (start <= end);
        assert (start <= 0xFFFFFF);
        mStart = start;
        mEnd = end;
        mKind = kind;
    }

    public int getLine(String source) {
        if (mStart == -1 || source == null) {
            return -1;
        }
        // we allow the offset to equal the length, because that's where TK_END_OF_FILE is reported
        int offset = Math.min(mStart, source.length());
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + description() + "}";
    }

    /**
     * Describes this intermediate representation.
     */
    @Nonnull
    public abstract String description();
}