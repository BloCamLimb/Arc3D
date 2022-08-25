/*
 * The Arctic Graphics Engine.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arctic is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arctic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arctic. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arctic.engine.shading;

import icyllis.arctic.core.SLType;
import icyllis.arctic.engine.*;
import icyllis.arctic.engine.shading.ProgramDataManager.UniformHandle;

import javax.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Abstract class that builds uniforms.
 * <p>
 * The uniform blocks are generally defined as:
 * <pre><code>
 * // Anonymous block
 * layout(std140, binding = 0) uniform CommonBlock {
 *     layout(offset = 0) vec4 u_OrthoProj;
 *     layout(offset = 16) vec4 u_ModelView0;
 *     layout(offset = 32) mat3 u_ModelView1;
 *     layout(offset = 80) vec4 u_DstTextureCoords;
 * }
 * layout(std140, binding = 1) uniform EffectBlock {
 *     // per-effect uniforms...
 * }</code></pre>
 * Per-effect uniforms are updated more frequently (generally, each draw op).
 */
public abstract class UniformHandler {

    public static final String NO_MANGLE_PREFIX = "u_";

    /**
     * Builtin uniforms are in Common Block.
     */
    public static final String ORTHOPROJ_NAME = "u_OrthoProj";
    public static final String MODELVIEW0_NAME = "u_ModelView0";
    public static final String MODELVIEW1_NAME = "u_ModelView1";
    public static final String DSTTEXTURECOORDS_NAME = "u_DstTextureCoords";

    public static class UniformInfo {

        public ShaderVar mVariable;
        public int mVisibility;
        public Processor mOwner;
        public String mRawName;

        /**
         * The offset using std140 layout, only valid for non-opaque types.
         */
        public int mOffset;

        public UniformInfo() {
        }
    }

    /**
     * Marks an integer as an opaque handle to a sampler resource.
     */
    @Retention(RetentionPolicy.SOURCE)
    public @interface SamplerHandle {
    }

    /**
     * The bindings for the main descriptor set.
     */
    public static final int COMMON_BINDING = 0;
    public static final int EFFECT_BINDING = 1; // will use Push Constants if possible

    public static final String COMMON_BLOCK_NAME = "CommonBlock";
    public static final String EFFECT_BLOCK_NAME = "EffectBlock";

    protected final ProgramBuilder mProgramBuilder;

    protected final int mBinding;
    protected final String mBlockName;

    protected UniformHandler(ProgramBuilder programBuilder,
                             int binding,
                             String blockName) {
        mProgramBuilder = programBuilder;
        mBinding = binding;
        mBlockName = blockName;
    }

    /**
     * Add a uniform variable to the current program, that has visibility in one or more shaders.
     * visibility is a bitfield of ShaderFlag values indicating from which shaders the uniform
     * should be accessible. At least one bit must be set. Geometry shader uniforms are not
     * supported at this time. The actual uniform name will be mangled. The final uniform name
     * can be retrieved by {@link #getUniformName(int)} with the UniformHandle. Use the
     * {@link #addUniformArray(Processor, int, byte, String, int)} variant to add an array of
     * uniforms.
     *
     * @param owner      the raw ptr to owner, may be null
     * @param visibility combination of ShaderFlags
     * @param type       see {@link SLType}
     * @param name       the raw name (pre-mangling), cannot be null or empty
     * @return UniformHandle
     */
    @UniformHandle
    public final int addUniform(Processor owner,
                                int visibility,
                                byte type,
                                String name) {
        assert (name != null && !name.isEmpty());
        assert (visibility != 0);
        assert (SLType.checkSLType(type));
        assert (!SLType.isCombinedSamplerType(type));
        boolean mangleName = !name.startsWith(NO_MANGLE_PREFIX);
        return internalAddUniformArray(owner, visibility, type, name, mangleName, ShaderVar.NonArray);
    }

    /**
     * @param owner      the raw ptr to owner, may be null
     * @param visibility combination of ShaderFlags
     * @param type       see {@link SLType}
     * @param name       the raw name (pre-mangling), cannot be null or empty
     * @param arrayCount the number of elements, cannot be zero
     * @return UniformHandle
     */
    @UniformHandle
    public final int addUniformArray(Processor owner,
                                     int visibility,
                                     byte type,
                                     String name,
                                     int arrayCount) {
        assert (name != null && !name.isEmpty());
        assert (visibility != 0);
        assert (SLType.checkSLType(type));
        assert (!SLType.isCombinedSamplerType(type));
        assert (arrayCount >= 1);
        boolean mangleName = !name.startsWith(NO_MANGLE_PREFIX);
        return internalAddUniformArray(owner, visibility, type, name, mangleName, arrayCount);
    }

    /**
     * @param handle UniformHandle
     */
    public abstract ShaderVar getUniformVariable(@UniformHandle int handle);

    /**
     * Shortcut for getUniformVariable(handle).getName()
     *
     * @param handle UniformHandle
     */
    public final String getUniformName(@UniformHandle int handle) {
        return getUniformVariable(handle).getName();
    }

    public abstract int numUniforms();

    public abstract UniformInfo uniform(int index);

    // Looks up a uniform that was added by 'owner' with the given 'rawName' (pre-mangling).
    // If there is no such uniform, null is returned.
    @Nullable
    public final ShaderVar getUniformMapping(Processor owner, String rawName) {
        for (int i = numUniforms() - 1; i >= 0; i--) {
            final UniformInfo u = uniform(i);
            if (u.mOwner == owner && u.mRawName.equals(rawName)) {
                return u.mVariable;
            }
        }
        return null;
    }

    // Like getUniformMapping(), but if the uniform is found it also marks it as accessible in
    // the vertex shader.
    @Nullable
    public final ShaderVar liftUniformToVertexShader(Processor owner, String rawName) {
        for (int i = numUniforms() - 1; i >= 0; i--) {
            final UniformInfo u = uniform(i);
            if (u.mOwner == owner && u.mRawName.equals(rawName)) {
                u.mVisibility |= EngineTypes.Vertex_ShaderFlag;
                return u.mVariable;
            }
        }
        // Uniform not found; it's better to return a void variable than to assert because sample
        // matrices that are uniform are treated the same for most of the code. When the sample
        // matrix expression can't be found as a uniform, we can infer it's a constant.
        return null;
    }

    @UniformHandle
    protected abstract int internalAddUniformArray(Processor owner,
                                                   int visibility,
                                                   byte type,
                                                   String name,
                                                   boolean mangleName,
                                                   int arrayCount);

    @SamplerHandle
    protected abstract int addSampler(BackendFormat backendFormat,
                                      int samplerState,
                                      short swizzle,
                                      String name);

    protected abstract String samplerVariable(@SamplerHandle int handle);

    protected abstract short samplerSwizzle(@SamplerHandle int handle);

    @SamplerHandle
    protected int addInputSampler(short swizzle, String name) {
        throw new UnsupportedOperationException();
    }

    protected String inputSamplerVariable(@SamplerHandle int handle) {
        throw new UnsupportedOperationException();
    }

    protected short inputSamplerSwizzle(@SamplerHandle int handle) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param visibility one of ShaderFlags
     */
    protected abstract void appendUniformDecls(int visibility, StringBuilder out);

    /**
     * Returns the base alignment mask in bytes taken up in UBO for SLTypes.
     *
     * @param type     see {@link SLType}
     * @param nonArray true for a single scalar or vector, false for an array of scalars or vectors
     * @param std430   true for std430 layout, false for std140 layout
     * @return base alignment mask
     */
    public static int getAlignmentMask(byte type, boolean nonArray, boolean std430) {
        switch (type) {
            case SLType.Bool:   // fall through
            case SLType.Int:    // fall through
            case SLType.UInt:   // fall through
            case SLType.Float:  // fall through
                return std430 || nonArray ? Float.BYTES - 1 : 4 * Float.BYTES - 1; // N - 1
            case SLType.BVec2:  // fall through
            case SLType.IVec2:  // fall through
            case SLType.UVec2:  // fall through
            case SLType.Vec2:   // fall through
                return std430 || nonArray ? 2 * Float.BYTES - 1 : 4 * Float.BYTES - 1; // 2N - 1
            case SLType.BVec3:  // fall through
            case SLType.BVec4:  // fall through
            case SLType.IVec3:  // fall through
            case SLType.IVec4:  // fall through
            case SLType.UVec3:  // fall through
            case SLType.UVec4:  // fall through
            case SLType.Vec3:   // fall through
            case SLType.Vec4:   // fall through
            case SLType.Mat3:   // fall through
            case SLType.Mat4:   // fall through
                return 4 * Float.BYTES - 1; // 4N - 1
            case SLType.Mat2:
                return std430 ? 2 * Float.BYTES - 1 : 4 * Float.BYTES - 1; // as an array of Vec2

            // This query is only valid for certain types.
            case SLType.Void:
            case SLType.Sampler2D:
            case SLType.Texture2D:
            case SLType.Sampler:
            case SLType.SubpassInput:
                throw new IllegalStateException(String.valueOf(type));
        }
        throw new IllegalArgumentException(String.valueOf(type));
    }

    /**
     * Returns the size in bytes taken up in UBO for SLTypes.
     * This includes paddings between components, but does not include paddings at the end of the element.
     *
     * @param type   see {@link SLType}
     * @param std430 true for std430 layout, false for std140 layout
     * @return size in bytes
     * @see UniformDataManager
     */
    public static int getSize(byte type, boolean std430) {
        switch (type) {
            case SLType.Float:
                return Float.BYTES;
            case SLType.Vec2:
                return 2 * Float.BYTES;
            case SLType.Vec3:
                return 3 * Float.BYTES;
            case SLType.Vec4:
                return 4 * Float.BYTES;
            case SLType.Bool:   // fall through
            case SLType.Int:    // fall through
            case SLType.UInt:
                return Integer.BYTES;
            case SLType.BVec2:  // fall through
            case SLType.IVec2:  // fall through
            case SLType.UVec2:
                return 2 * Integer.BYTES;
            case SLType.BVec3:  // fall through
            case SLType.IVec3:  // fall through
            case SLType.UVec3:
                return 3 * Integer.BYTES;
            case SLType.BVec4:  // fall through
            case SLType.IVec4:  // fall through
            case SLType.UVec4:
                return 4 * Integer.BYTES;
            case SLType.Mat2:
                return std430 ? 2 * 2 * Float.BYTES : 2 * 4 * Float.BYTES;
            case SLType.Mat3:
                return 3 * 4 * Float.BYTES;
            case SLType.Mat4:
                return 4 * 4 * Float.BYTES;

            // This query is only valid for certain types.
            case SLType.Void:
            case SLType.Sampler2D:
            case SLType.Texture2D:
            case SLType.Sampler:
            case SLType.SubpassInput:
                throw new IllegalStateException(String.valueOf(type));
        }
        throw new IllegalArgumentException(String.valueOf(type));
    }

    /**
     * Given the current offset into the UBO data, calculate the offset for the uniform we're trying to
     * add taking into consideration all alignment requirements. Use aligned offset plus
     * {@link #getAlignedSize(byte, int, boolean)} to get the offset to the end of the new uniform.
     *
     * @param offset     the current offset
     * @param type       see {@link SLType}
     * @param arrayCount see {@link ShaderVar}
     * @param std430     true for std430 layout, false for std140 layout
     * @return the aligned offset for the new uniform
     */
    public static int getAlignedOffset(int offset,
                                       byte type,
                                       int arrayCount,
                                       boolean std430) {
        assert (SLType.checkSLType(type));
        assert (arrayCount == ShaderVar.NonArray) || (arrayCount >= 1);
        int alignmentMask = getAlignmentMask(type, arrayCount == ShaderVar.NonArray, std430);
        return (offset + alignmentMask) & ~alignmentMask;
    }

    /**
     * @see UniformDataManager
     */
    public static int getAlignedSize(byte type,
                                     int arrayCount,
                                     boolean std430) {
        assert (SLType.checkSLType(type));
        assert (arrayCount == ShaderVar.NonArray) || (arrayCount >= 1);
        if (arrayCount == ShaderVar.NonArray) {
            return getSize(type, std430);
        } else {
            final int elementSize;
            if (std430) {
                elementSize = getSize(type, true);
            } else { // std140, round up to Vec4
                // currently, values greater than 16 are already multiples of 16, so just use max
                elementSize = Math.max(getSize(type, false), 4 * Float.BYTES);
            }
            assert ((elementSize & (4 * Float.BYTES - 1)) == 0);
            return elementSize * arrayCount;
        }
    }
}