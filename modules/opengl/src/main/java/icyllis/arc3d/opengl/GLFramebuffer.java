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

package icyllis.arc3d.opengl;

import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Framebuffer;
import icyllis.arc3d.engine.FramebufferDesc;
import org.jspecify.annotations.Nullable;

import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D_MULTISAMPLE;

public final class GLFramebuffer extends Framebuffer {

    private int mRenderFramebuffer;
    private int mResolveFramebuffer;

    private GLFramebuffer(GLDevice device,
                          int renderFramebuffer,
                          int resolveFramebuffer) {
        super(device);
        mRenderFramebuffer = renderFramebuffer;
        mResolveFramebuffer = resolveFramebuffer;
    }

    private static void attachColorAttachment(GLInterface gl, int index, GLImage image) {
        if (image instanceof GLRenderbuffer renderbuffer) {
            gl.glFramebufferRenderbuffer(GL_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0 + index,
                    GL_RENDERBUFFER,
                    renderbuffer.getHandle());
        } else {
            GLTexture texture = (GLTexture) image;
            switch (texture.getTarget()) {
                case GL_TEXTURE_2D, GL_TEXTURE_2D_MULTISAMPLE -> {
                    gl.glFramebufferTexture2D(GL_FRAMEBUFFER,
                            GL_COLOR_ATTACHMENT0 + index,
                            texture.getTarget(),
                            texture.getHandle(),
                            0);
                }
                default -> throw new UnsupportedOperationException();
            }
        }
    }

    @Nullable
    @SharedPtr
    public static GLFramebuffer make(GLDevice device,
                                     FramebufferDesc desc) {

        assert device.isOnExecutingThread();

        GLInterface gl = device.getGL();

        final int numColorAttachments;
        boolean hasColorAttachments = false;
        boolean hasColorResolveAttachments = desc.mColorResolveAttachment.mAttachment != null;
        numColorAttachments = desc.mColorAttachments.length;
        for (int i = 0; i < numColorAttachments; i++) {
            var attachmentDesc = desc.mColorAttachments[i];
            hasColorAttachments |= attachmentDesc.mAttachment != null;
        }

        // There's an NVIDIA driver bug that creating framebuffer via DSA with attachments of
        // different dimensions will report GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS_EXT.
        // The workaround is to use traditional glGen* and glBind* (validate).
        // see https://forums.developer.nvidia.com/t/framebuffer-incomplete-when-attaching-color-buffers-of-different-sizes-with-dsa/211550
        int[] framebuffers = new int[2];
        int numFramebuffers = hasColorResolveAttachments ? 2 : 1;
        gl.glGenFramebuffers(numFramebuffers, framebuffers);
        final int renderFramebuffer = framebuffers[0];
        if (renderFramebuffer == 0) {
            return null;
        }

        // If we are using multisampling we will create two FBOs. We render to one and then resolve to
        // the texture bound to the other.
        final int resolveFramebuffer;
        if (hasColorResolveAttachments) {
            resolveFramebuffer = framebuffers[1];
            if (resolveFramebuffer == 0) {
                gl.glDeleteFramebuffers(1, framebuffers);
                return null;
            }
        } else {
            resolveFramebuffer = renderFramebuffer;
        }

        gl.glBindFramebuffer(GL_FRAMEBUFFER, renderFramebuffer);
        if (hasColorAttachments) {
            //TODO bind other that 2D, and apply arraySlice
            int[] drawBuffers = new int[numColorAttachments];
            // attachment is physically indexed, but draw buffer is a logical index, i.e., layout(location = N)
            int currentAttachment = 0;
            for (int index = 0; index < numColorAttachments; index++) {
                var attachmentDesc = desc.mColorAttachments[index];
                if (attachmentDesc.mAttachment == null) {
                    // unused slot
                    drawBuffers[index] = GL_NONE;
                    continue;
                }
                GLImage attachmentImage = (GLImage) attachmentDesc.mAttachment.get();
                assert attachmentImage != null;
                attachColorAttachment(gl,
                        currentAttachment,
                        attachmentImage);
                drawBuffers[index] = GL_COLOR_ATTACHMENT0 + currentAttachment;
                currentAttachment++;
            }
            gl.glDrawBuffers(numColorAttachments, drawBuffers);
        }
        if (desc.mDepthStencilAttachment.mAttachment != null) {
            GLRenderbuffer attachmentImage = (GLRenderbuffer) desc.mDepthStencilAttachment.mAttachment.get();
            assert attachmentImage != null;
            //TODO attach depth texture besides renderbuffer
            int attachmentPoint;
            if (GLUtil.glFormatIsPackedDepthStencil(attachmentImage.getGLFormat())) {
                attachmentPoint = GL_DEPTH_STENCIL_ATTACHMENT;
            } else if (GLUtil.glFormatDepthBits(attachmentImage.getGLFormat()) > 0) {
                attachmentPoint = GL_DEPTH_ATTACHMENT;
            } else {
                assert GLUtil.glFormatStencilBits(attachmentImage.getGLFormat()) > 0;
                attachmentPoint = GL_STENCIL_ATTACHMENT;
            }
            gl.glFramebufferRenderbuffer(GL_FRAMEBUFFER,
                    attachmentPoint,
                    GL_RENDERBUFFER,
                    attachmentImage.getHandle());
        }
        if (!device.getCaps().skipErrorChecks()) {
            int status = gl.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                gl.glDeleteFramebuffers(numFramebuffers, framebuffers);
                return null;
            }
        }

        if (hasColorResolveAttachments) {
            assert numColorAttachments == 1;
            gl.glBindFramebuffer(GL_FRAMEBUFFER, resolveFramebuffer);
            int[] drawBuffers = new int[numColorAttachments];
            GLImage resolveAttachment = (GLImage) desc.mColorResolveAttachment.mAttachment.get();
            assert resolveAttachment != null;
            attachColorAttachment(gl,
                    0,
                    resolveAttachment);
            drawBuffers[0] = GL_COLOR_ATTACHMENT0;
            gl.glDrawBuffers(numColorAttachments, drawBuffers);
            if (!device.getCaps().skipErrorChecks()) {
                int status = gl.glCheckFramebufferStatus(GL_FRAMEBUFFER);
                if (status != GL_FRAMEBUFFER_COMPLETE) {
                    gl.glDeleteFramebuffers(numFramebuffers, framebuffers);
                    return null;
                }
            }
        }

        return new GLFramebuffer(device, renderFramebuffer, resolveFramebuffer);
    }

    public int getRenderFramebuffer() {
        return mRenderFramebuffer;
    }

    public int getResolveFramebuffer() {
        return mResolveFramebuffer;
    }

    @Override
    protected void deallocate() {
        GLDevice device = (GLDevice) getDevice();
        assert device.isOnExecutingThread();
        int[] framebuffers = new int[2];
        int numFramebuffers = 0;
        if (mRenderFramebuffer != 0) {
            framebuffers[numFramebuffers++] = mRenderFramebuffer;
        }
        if (mRenderFramebuffer != mResolveFramebuffer) {
            assert (mResolveFramebuffer != 0);
            framebuffers[numFramebuffers++] = mResolveFramebuffer;
        }
        if (numFramebuffers > 0) {
            device.getGL().glDeleteFramebuffers(numFramebuffers, framebuffers);
        }
        mRenderFramebuffer = 0;
        mResolveFramebuffer = 0;
    }
}
