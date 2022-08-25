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

package icyllis.arctic.opengl;

import icyllis.arctic.core.SharedPtr;
import icyllis.arctic.engine.GeometryProcessor;
import icyllis.arctic.engine.shading.VertexShaderBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;

import static icyllis.arctic.engine.EngineTypes.*;
import static icyllis.arctic.opengl.GLCore.*;

/**
 * This class manages a GPU program and records per-program information. It also records the vertex
 * and instance attribute layouts that are to be used with the program.
 * <p>
 * This class represents an OpenGL vertex array object. It manages the lifetime of the vertex array
 * and is used to track the state of the vertex array to avoid redundant GL calls.
 */
public class GLPipeline extends GLManagedResource {

    private static final int INVALID_BINDING = -1;

    private int mProgram;
    private int mVertexArray;

    private final int mVertexBinding;
    private final int mInstanceBinding;

    private final int mVertexStride;
    private final int mInstanceStride;

    private int mVertexBufferUniqueID;
    private int mInstanceBufferUniqueID;
    private int mIndexBufferUniqueID;

    private GLPipeline(@Nonnull GLServer server,
                       int program,
                       int vertexArray,
                       int vertexBinding,
                       int instanceBinding,
                       int vertexStride,
                       int instanceStride) {
        super(server);
        assert (vertexArray != 0);
        assert (vertexBinding == INVALID_BINDING || vertexStride > 0);
        assert (instanceBinding == INVALID_BINDING || instanceStride > 0);
        mProgram = program;
        mVertexArray = vertexArray;
        mVertexBinding = vertexBinding;
        mInstanceBinding = instanceBinding;
        mVertexStride = vertexStride;
        mInstanceStride = instanceStride;
    }

    @Nullable
    @SharedPtr
    public static GLPipeline make(@Nonnull GLServer server,
                                  @Nonnull GeometryProcessor geomProc,
                                  int program) {
        int vertexArray = glCreateVertexArrays();
        if (vertexArray == 0) {
            return null;
        }

        int vertexBinding = INVALID_BINDING;
        int instanceBinding = INVALID_BINDING;

        int bindingIndex = 0;
        int attribIndex = 0;
        if (geomProc.hasVertexAttributes()) {
            vertexBinding = bindingIndex++;
            attribIndex = setVertexFormat(geomProc.vertexAttributes(),
                    vertexArray,
                    attribIndex,
                    vertexBinding);
            glVertexArrayBindingDivisor(vertexArray,
                    vertexBinding,
                    0); // per-vertex
        }

        if (geomProc.hasInstanceAttributes()) {
            instanceBinding = bindingIndex;
            setVertexFormat(geomProc.instanceAttributes(),
                    vertexArray,
                    attribIndex,
                    instanceBinding);
            glVertexArrayBindingDivisor(vertexArray,
                    instanceBinding,
                    1); // per-instance
        }

        return new GLPipeline(server,
                program,
                vertexArray,
                vertexBinding,
                instanceBinding,
                geomProc.vertexStride(),
                geomProc.instanceStride());
    }

    /**
     * See {@link VertexShaderBuilder} to see how we bind these on server side.
     */
    private static int setVertexFormat(@Nonnull Iterator<GeometryProcessor.Attribute> attrs,
                                       int vertexArray,
                                       int location,
                                       int binding) {
        while (attrs.hasNext()) {
            var attr = attrs.next();
            // may contain matrix that takes up multiple locations
            int locationSize = attr.locationSize();
            assert (locationSize > 0);
            int offset = attr.offset();
            for (int i = 0; i < locationSize; i++) {
                glEnableVertexArrayAttrib(vertexArray, location);
                glVertexArrayAttribBinding(vertexArray, location, binding);
                setAttribFormat(attr.srcType(), vertexArray, location, offset);
                location++;
                offset += attr.stepSize();
            }
        }
        return location;
    }

    private static void setAttribFormat(int attribType, int vertexArray, int location, int offset) {
        switch (attribType) {
            case Float_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 1, GL_FLOAT, /*normalized*/false, offset);
            case Float2_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 2, GL_FLOAT, /*normalized*/false, offset);
            case Float3_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 3, GL_FLOAT, /*normalized*/false, offset);
            case Float4_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 4, GL_FLOAT, /*normalized*/false, offset);
            case Half_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 1, GL_HALF_FLOAT, /*normalized*/false, offset);
            case Half2_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 2, GL_HALF_FLOAT, /*normalized*/false, offset);
            case Half4_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 4, GL_HALF_FLOAT, /*normalized*/false, offset);
            case Int2_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 2, GL_INT, offset);
            case Int3_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 3, GL_INT, offset);
            case Int4_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 4, GL_INT, offset);
            case Byte_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 1, GL_BYTE, offset);
            case Byte2_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 2, GL_BYTE, offset);
            case Byte4_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 4, GL_BYTE, offset);
            case UByte_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 1, GL_UNSIGNED_BYTE, offset);
            case UByte2_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 2, GL_UNSIGNED_BYTE, offset);
            case UByte4_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 4, GL_UNSIGNED_BYTE, offset);
            case UByte_norm_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 1, GL_UNSIGNED_BYTE, /*normalized*/true, offset);
            case UByte4_norm_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 4, GL_UNSIGNED_BYTE, /*normalized*/true, offset);
            case Short2_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 2, GL_SHORT, offset);
            case Short4_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 4, GL_SHORT, offset);
            case UShort2_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 2, GL_UNSIGNED_SHORT, offset);
            case UShort2_norm_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 2, GL_UNSIGNED_SHORT, /*normalized*/true, offset);
            case Int_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 1, GL_INT, offset);
            case UInt_VertexAttribType ->
                    glVertexArrayAttribIFormat(vertexArray, location, 1, GL_UNSIGNED_INT, offset);
            case UShort_norm_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 1, GL_UNSIGNED_SHORT, /*normalized*/true, offset);
            case UShort4_norm_VertexAttribType ->
                    glVertexArrayAttribFormat(vertexArray, location, 4, GL_UNSIGNED_SHORT, /*normalized*/true, offset);
            default -> throw new IllegalStateException();
        }
    }

    @Override
    public void onFree() {
        if (mProgram != 0) {
            glDeleteProgram(mProgram);
            mProgram = 0;
        }
        if (mVertexArray != 0) {
            glDeleteVertexArrays(mVertexArray);
            mVertexArray = 0;
        }
    }

    public void drop() {
        mProgram = 0;
        mVertexArray = 0;
    }

    public int getProgram() {
        return mProgram;
    }

    public int getVertexArray() {
        return mVertexArray;
    }

    /**
     * Set the buffer that stores the attribute data.
     * <p>
     * The stride, the distance to the next vertex data, in bytes, is determined in constructor.
     *
     * @param buffer the vertex buffer object, raw ptr
     * @param offset first vertex data to the head of the buffer, in bytes
     */
    public void bindVertexBuffer(@Nonnull GLBuffer buffer, long offset) {
        if (mVertexArray == 0) {
            return;
        }
        assert (mVertexBinding != INVALID_BINDING && mVertexStride > 0);
        if (mVertexBufferUniqueID != buffer.getUniqueID()) {
            glVertexArrayVertexBuffer(mVertexArray, mVertexBinding, buffer.getBuffer(), offset, mVertexStride);
            mVertexBufferUniqueID = buffer.getUniqueID();
        }
    }

    /**
     * Set the buffer that stores the attribute data.
     * <p>
     * The stride, the distance to the next instance data, in bytes, is determined in constructor.
     *
     * @param buffer the vertex buffer object, raw ptr
     * @param offset first instance data to the head of the buffer, in bytes
     */
    public void bindInstanceBuffer(@Nonnull GLBuffer buffer, long offset) {
        if (mVertexArray == 0) {
            return;
        }
        assert (mInstanceBinding != INVALID_BINDING && mInstanceStride > 0);
        if (mInstanceBufferUniqueID != buffer.getUniqueID()) {
            glVertexArrayVertexBuffer(mVertexArray, mInstanceBinding, buffer.getBuffer(), offset, mInstanceStride);
            mInstanceBufferUniqueID = buffer.getUniqueID();
        }
    }

    /**
     * Set element buffer (index buffer).
     *
     * @param buffer the element buffer object, raw ptr
     */
    public void bindIndexBuffer(@Nonnull GLBuffer buffer) {
        if (mVertexArray == 0) {
            return;
        }
        if (mIndexBufferUniqueID != buffer.getUniqueID()) {
            glVertexArrayElementBuffer(mVertexArray, buffer.getBuffer());
            mIndexBufferUniqueID = buffer.getUniqueID();
        }
    }
}