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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.granite.shading.GraphicsPipelineBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A draw pass represents a render pass, with limited and sorted draw commands.
 * <p>
 * Created immutable.
 *
 * @see GraphicsPipelineBuilder
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class DrawPass implements AutoCloseable {

    /**
     * Depth buffer is 16-bit, ensure no overflow.
     * The theoretic max for this value is 65535, but we see markedly better
     * performance with smaller values
     */
    public static final int MAX_RENDER_STEPS = (1 << 12);
    /**
     * An invalid index for {@link UniformTracker}, also for pipeline index.
     *
     * @see #MAX_RENDER_STEPS
     */
    public static final int INVALID_INDEX = MAX_RENDER_STEPS + 1;

    private final DrawCommandList mCommandList;

    private final Rect2i mBounds;
    private final int mDepthStencilFlags;

    private final ObjectArrayList<GraphicsPipelineDesc> mPipelineDescs;
    private final ObjectArrayList<SamplerDesc> mSamplerDescs;

    private final ObjectArrayList<@SharedPtr ImageProxyView> mTexturesViews;

    private volatile @SharedPtr GraphicsPipeline[] mPipelines;
    private volatile @RawPtr Sampler[] mSamplers;

    DrawPass(DrawCommandList commandList, Rect2i bounds, int depthStencilFlags,
                     ObjectArrayList<GraphicsPipelineDesc> pipelineDescs,
                     ObjectArrayList<SamplerDesc> samplerDescs,
                     ObjectArrayList<@SharedPtr ImageProxyView> texturesViews) {
        mCommandList = commandList;
        mBounds = bounds;
        mDepthStencilFlags = depthStencilFlags;
        mPipelineDescs = pipelineDescs;
        mSamplerDescs = samplerDescs;
        mTexturesViews = texturesViews;
    }

    public Rect2ic getBounds() {
        return mBounds;
    }

    public int getDepthStencilFlags() {
        return mDepthStencilFlags;
    }

    public DrawCommandList getCommandList() {
        return mCommandList;
    }

    public boolean prepare(ResourceProvider resourceProvider,
                           RenderPassDesc renderPassDesc) {
        @SharedPtr GraphicsPipeline[] pipelines = new GraphicsPipeline[mPipelineDescs.size()];
        try {
            for (int i = 0; i < mPipelineDescs.size(); i++) {
                @SharedPtr
                var pipeline = resourceProvider.findOrCreateGraphicsPipeline(
                        mPipelineDescs.get(i),
                        renderPassDesc
                );
                if (pipeline == null) {
                    return false;
                }
                pipelines[i] = pipeline;
            }
        } finally {
            // We must release the objects that have already been created.
            mPipelines = pipelines;
            // The DrawPass may be long-lived on a Recording and we no longer need the GraphicPipelineDescs
            // once we've created pipelines, so we drop the storage for them here.
            mPipelineDescs.clear();
        }

        if (!mSamplerDescs.isEmpty()) {
            @RawPtr Sampler[] samplers = new Sampler[mSamplerDescs.size()];
            try {
                for (int i = 0; i < mSamplerDescs.size(); i++) {
                    @RawPtr
                    var sampler = resourceProvider.findOrCreateCompatibleSampler(
                            mSamplerDescs.get(i)
                    );
                    if (sampler == null) {
                        return false;
                    }
                    samplers[i] = sampler;
                }
            } finally {
                mSamplers = samplers;
                // The DrawPass may be long-lived on a Recording and we no longer need the SamplerDescs
                // once we've created Samplers, so we drop the storage for them here.
                mSamplerDescs.clear();
            }
        }

        return true;
    }

    public boolean execute(CommandBuffer commandBuffer) {
        for (var pipeline : mPipelines) {
            commandBuffer.trackResource(RefCnt.create(pipeline));
        }
        for (int i = 0; i < mTexturesViews.size(); i++) {
            commandBuffer.trackCommandBufferResource(mTexturesViews.get(i).getProxy().refImage());
        }
        var cmdList = getCommandList();
        var p = cmdList.mPrimitives.elements();
        int i = 0;
        var oa = cmdList.mPointers.elements();
        int oi = 0;
        int lim = cmdList.mPrimitives.size();
        while (i < lim) {
            switch (p[i++]) {
                case DrawCommandList.CMD_BIND_GRAPHICS_PIPELINE -> {
                    int pipelineIndex = p[i];
                    if (!commandBuffer.bindGraphicsPipeline(mPipelines[pipelineIndex])) {
                        return false;
                    }
                    i += 1;
                }
                case DrawCommandList.CMD_DRAW -> {
                    int vertexCount = p[i];
                    int baseVertex = p[i + 1];
                    commandBuffer.draw(vertexCount, baseVertex);
                    i += 2;
                }
                case DrawCommandList.CMD_DRAW_INDEXED -> {
                    int indexCount = p[i];
                    int baseIndex = p[i + 1];
                    int baseVertex = p[i + 2];
                    commandBuffer.drawIndexed(indexCount, baseIndex, baseVertex);
                    i += 3;
                }
                case DrawCommandList.CMD_DRAW_INSTANCED -> {
                    int instanceCount = p[i];
                    int baseInstance = p[i + 1];
                    int vertexCount = p[i + 2];
                    int baseVertex = p[i + 3];
                    commandBuffer.drawInstanced(instanceCount, baseInstance, vertexCount, baseVertex);
                    i += 4;
                }
                case DrawCommandList.CMD_DRAW_INDEXED_INSTANCED -> {
                    int indexCount = p[i];
                    int baseIndex = p[i + 1];
                    int instanceCount = p[i + 2];
                    int baseInstance = p[i + 3];
                    int baseVertex = p[i + 4];
                    commandBuffer.drawIndexedInstanced(indexCount, baseIndex, instanceCount, baseInstance, baseVertex);
                    i += 5;
                }
                case DrawCommandList.CMD_BIND_INDEX_BUFFER -> {
                    int indexType = p[i];
                    long offset = p[i + 1];
                    commandBuffer.bindIndexBuffer(indexType, (Buffer) oa[oi++], offset);
                    i += 2;
                }
                case DrawCommandList.CMD_BIND_VERTEX_BUFFER -> {
                    int binding = p[i];
                    long offset = p[i + 1];
                    commandBuffer.bindVertexBuffer(binding, (Buffer) oa[oi++], offset);
                    i += 2;
                }
                case DrawCommandList.CMD_SET_SCISSOR -> {
                    int x = p[i];
                    int y = p[i + 1];
                    int width = p[i + 2];
                    int height = p[i + 3];
                    commandBuffer.setScissor(x, y, width, height);
                    i += 4;
                }
                case DrawCommandList.CMD_BIND_UNIFORM_BUFFER -> {
                    int binding = p[i];
                    int offset = p[i + 1];
                    int size = p[i + 2];
                    commandBuffer.bindUniformBuffer(binding, (Buffer) oa[oi++], offset, size);
                    i += 3;
                }
                case DrawCommandList.CMD_BIND_TEXTURES -> {
                    int numBindings = p[i++];
                    for (int binding = 0; binding < numBindings; binding++) {
                        @RawPtr
                        var textureView = mTexturesViews.get(p[i]);
                        @RawPtr
                        var sampler = mSamplers[p[i + 1]];
                        commandBuffer.bindTextureSampler(binding,
                                textureView.getProxy().getImage(),
                                sampler,
                                textureView.getSwizzle());
                        i += 2;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void close() {
        if (mPipelines != null) {
            for (int i = 0; i < mPipelines.length; i++) {
                mPipelines[i] = RefCnt.move(mPipelines[i]);
            }
        }
        mTexturesViews.forEach(ImageProxyView::close);
        mTexturesViews.clear();
    }

}
