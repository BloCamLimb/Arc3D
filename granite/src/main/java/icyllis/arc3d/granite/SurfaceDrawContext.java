/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2025 BloCamLimb <pocamelards@gmail.com>
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
import icyllis.arc3d.granite.task.DrawTask;
import icyllis.arc3d.granite.task.ImageUploadTask;
import icyllis.arc3d.granite.task.RenderPassTask;
import icyllis.arc3d.granite.task.Task;
import icyllis.arc3d.granite.task.TaskList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import icyllis.arc3d.engine.Engine.*;

import java.util.Arrays;

/**
 * Used by {@link GraniteDevice}, this class records draw commands into a specific Surface,
 * via a general task graph representing GPU work and their inter-dependencies.
 */
public final class SurfaceDrawContext implements AutoCloseable {

    private final ImageInfo mImageInfo;

    @SharedPtr
    private final ImageProxyView mReadView;
    private final short mWriteSwizzle;

    private TaskList mDrawTaskList;

    private int mNumSteps;

    private final ObjectArrayList<@SharedPtr ImageUploadTask> mPendingUploads =
            new ObjectArrayList<>();

    // Load and store information for the current pending draws.
    private byte mPendingLoadOp = LoadOp.kLoad;
    private final float[] mPendingClearColor = new float[4];

    private final Object2IntOpenHashMap<GraphicsPipelineDesc> mPipelineToIndex = new Object2IntOpenHashMap<>();
    private ObjectArrayList<GraphicsPipelineDesc> mIndexToPipeline = new ObjectArrayList<>();

    private final GraphicsPipelineDesc mLookupDesc = new GraphicsPipelineDesc();

    private final Rect2f mPassBounds = Rect2f.makeInfiniteInverted();
    private int mDepthStencilFlags = DepthStencilFlags.kNone;
    private final ObjectArrayList<SortKey> mSortKeys = new ObjectArrayList<>();

    // This is responsible for collecting textures of fragment stages and geometry steps,
    // as well as providing caching and deduplication, so it's here.
    private final TextureDataGatherer mTextureDataGatherer;

    private final UniformDataCache mGeometryUniformDataCache;
    private final UniformDataCache mFragmentUniformDataCache;

    // temp
    private final UniformDataGatherer mUniformDataGatherer;
    private final KeyBuilder mPaintParamsKeyBuilder;

    private final float[] mProjection;

    private SurfaceDrawContext(@SharedPtr ImageProxyView readView,
                               short writeSwizzle,
                               ImageInfo imageInfo) {
        mReadView = readView;
        mWriteSwizzle = writeSwizzle;
        mImageInfo = imageInfo;
        mDrawTaskList = new TaskList();
        mPipelineToIndex.defaultReturnValue(-1);

        mTextureDataGatherer = new TextureDataGatherer();
        mGeometryUniformDataCache = new UniformDataCache();
        mFragmentUniformDataCache = new UniformDataCache();

        mUniformDataGatherer = new UniformDataGatherer(UniformDataGatherer.Std140Layout);
        mPaintParamsKeyBuilder = new KeyBuilder();

        int surfaceHeight = readView.getHeight();
        int surfaceOrigin = readView.getOrigin();
        float projX = 2.0f / readView.getWidth();
        float projY = -1.0f;
        float projZ = 2.0f / surfaceHeight;
        float projW = -1.0f;
        if (surfaceOrigin == SurfaceOrigin.kLowerLeft) {
            projZ = -projZ;
            projW = -projW;
        }
        mProjection = new float[]{projX, projY, projZ, projW};
    }

    //TODO currently we don't handle MSAA
    @Nullable
    public static SurfaceDrawContext make(
            RecordingContext context,
            @SharedPtr ImageProxyView targetView,
            ImageInfo deviceInfo) {
        if (targetView == null) {
            return null;
        }
        if (context == null || context.isDiscarded()) {
            targetView.close();
            return null;
        }
        if (deviceInfo.alphaType() != ColorInfo.AT_OPAQUE &&
                deviceInfo.alphaType() != ColorInfo.AT_PREMUL) {
            // we only render to premultiplied alpha type
            targetView.close();
            return null;
        }
        if (!targetView.getProxy().getDesc().isRenderable()) {
            targetView.close();
            return null;
        }

        // Accept an approximate-fit surface, but make sure it's at least as large as the device's
        // logical size.
        // TODO: validate that the color type and alpha type are compatible with the target's info
        assert targetView.getWidth() >= deviceInfo.width() &&
                targetView.getHeight() >= deviceInfo.height();

        short writeSwizzle = context.getCaps().getWriteSwizzle(
                targetView.getProxy().getDesc(), deviceInfo.colorType());

        return new SurfaceDrawContext(
                targetView,
                writeSwizzle,
                deviceInfo);
    }

    /**
     * Destructs this context.
     */
    @Override
    public void close() {
        mReadView.close();
        mDrawTaskList.close();
        mPendingUploads.forEach(RefCnt::unref);
        mPendingUploads.clear();
        mTextureDataGatherer.close();
        mGeometryUniformDataCache.close();
        mFragmentUniformDataCache.close();
        mUniformDataGatherer.close();
    }

    /**
     * @return raw ptr to the read view
     */
    @RawPtr
    public ImageProxyView getReadView() {
        return mReadView;
    }

    public ImageInfo getImageInfo() {
        return mImageInfo;
    }

    /**
     * @see ColorInfo.ColorType
     */
    public int getColorType() {
        return mImageInfo.colorType();
    }

    /**
     * @see ColorInfo.AlphaType
     */
    public int getAlphaType() {
        return mImageInfo.alphaType();
    }

    public int getWidth() {
        return mReadView.getWidth();
    }

    public int getHeight() {
        return mReadView.getHeight();
    }

    public boolean isMipmapped() {
        return mReadView.isMipmapped();
    }

    /**
     * Read view and write view have the same origin.
     *
     * @see SurfaceOrigin
     */
    public int getOrigin() {
        return mReadView.getOrigin();
    }

    /**
     * @see Swizzle
     */
    public short getReadSwizzle() {
        return mReadView.getSwizzle();
    }

    /**
     * @param clearColor premultiplied RGBA color, null means (0,0,0,0)
     */
    public void clear(float @Nullable[] clearColor) {
        reset(LoadOp.kClear, clearColor);
    }

    public void discard() {
        reset(LoadOp.kDiscard, null);
    }

    public int numPendingSteps() {
        return mNumSteps;
    }

    /**
     *
     * @param draw will transfer the ownership of Draw
     * @param paintParams Paint params, null implies depth-only draw (i.e. clipping mask).
     * @param keyContext
     */
    public void recordDraw(@NonNull Draw draw,
                           @Nullable PaintParams paintParams,
                           KeyContext keyContext) {
        assert !draw.mDrawBounds.isEmpty();
        assert new Rect2i(0, 0, mImageInfo.width(), mImageInfo.height()).contains(draw.mScissorRect);
        assert ((draw.mRenderer.depthStencilFlags() & DepthStencilFlags.kStencil) == 0 ||
                DrawOrder.getStencilIndex(draw.mDrawOrder) != DrawOrder.MIN_VALUE);

        // we need to persist the Draw so we can write the vertex data later
        // the matrix is a view to Device or ClipStack, so clone it first
        draw.mTransform = draw.mTransform.clone();


        for (int stepIndex = 0; stepIndex < draw.mRenderer.numSteps(); stepIndex++) {
            var step = draw.mRenderer.step(stepIndex);

            mPaintParamsKeyBuilder.clear();
            boolean useStepSolidColor = false;

            mTextureDataGatherer.resetForDraw();

            // collect fragment data and pipeline key
            mUniformDataGatherer.reset();
            if (step.performsShading() && paintParams != null) {
                useStepSolidColor = step.handlesSolidColor() && paintParams.isSolidColor();
                // Add paint fragment stages, final blender, clip shader,
                // if this is the step that performs shading, and not a depth-only draw
                keyContext.reset(useStepSolidColor ? null : paintParams.getColor());
                paintParams.appendToKey(keyContext,
                        mPaintParamsKeyBuilder,
                        mUniformDataGatherer,
                        mTextureDataGatherer,
                        useStepSolidColor);
                assert !mPaintParamsKeyBuilder.isEmpty();
                if (useStepSolidColor) {
                    float[] tmpSolidColor = new float[4];
                    boolean res = paintParams.getSolidColor(mImageInfo, tmpSolidColor);
                    assert res;
                    draw.mSolidColor = tmpSolidColor;
                }
            }
            var fragmentUniformIndex = mFragmentUniformDataCache.insert(mUniformDataGatherer.finish());

            int pipelineIndex = mPipelineToIndex.getInt(
                    mLookupDesc.set(step, mPaintParamsKeyBuilder, useStepSolidColor));
            if (pipelineIndex < 0) {
                pipelineIndex = mIndexToPipeline.size();
                var finalDesc = mLookupDesc.copy();
                mIndexToPipeline.add(finalDesc); // store immutable descriptors
                mPipelineToIndex.put(finalDesc, pipelineIndex);
            }

            // collect geometry data
            mUniformDataGatherer.reset();
            // first add the 2D orthographic projection
            mUniformDataGatherer.write4f(mProjection);
            step.writeUniformsAndTextures(draw, mUniformDataGatherer, mTextureDataGatherer,
                    mLookupDesc.mayRequireLocalCoords());
            var geometryUniformIndex = mGeometryUniformDataCache.insert(mUniformDataGatherer.finish());

            // geometry texture samplers and then fragment texture samplers
            // we build shader code and set binding points in this order as well
            var textures = mTextureDataGatherer.finish(true);

            mSortKeys.add(new SortKey(
                    draw,
                    stepIndex,
                    pipelineIndex,
                    geometryUniformIndex,
                    fragmentUniformIndex,
                    textures
            ));
        }

        mPassBounds.joinNoCheck(draw.mDrawBounds);
        mDepthStencilFlags |= draw.mRenderer.depthStencilFlags();

        mNumSteps += draw.mRenderer.numSteps();
    }

    public boolean recordUpload(RecordingContext context,
                                @SharedPtr ImageProxy imageProxy,
                                int srcColorType, int srcAlphaType, ColorSpace srcColorSpace,
                                int dstColorType, int dstAlphaType, ColorSpace dstColorSpace,
                                ImageUploadTask.MipLevel[] levels, Rect2ic dstRect,
                                ImageUploadTask.UploadCondition condition) {
        assert new Rect2i(0, 0, imageProxy.getWidth(), imageProxy.getHeight()).contains(dstRect);
        @SharedPtr
        ImageUploadTask uploadTask = ImageUploadTask.make(
                context,
                imageProxy, // move
                srcColorType, srcAlphaType, srcColorSpace,
                dstColorType, dstAlphaType, dstColorSpace,
                levels,
                dstRect,
                condition
        );
        if (uploadTask == null) {
            return false;
        }
        mPendingUploads.add(uploadTask); // move
        return true;
    }

    public void recordDependency(@SharedPtr Task task) {
        assert task != null;
        // Adding `task` to the current DrawTask directly means that it will execute after any previous
        // dependent tasks and after any previous calls to flush(), but everything else that's being
        // collected on the DrawContext will execute after `task` once the next flush() is performed.
        mDrawTaskList.appendTask(task);
    }

    /**
     * Backing store's width/height may not equal to device's width/height,
     * currently we use the backing dimensions for scissor and viewport.
     * All the parameters are raw pointers and read-only.
     * <p>
     * The first uniform variable in geometry block must be a projection vector,
     * see {@link PipelineBuilder}.
     */
    @Nullable
    private DrawPass snapDrawPass(@NonNull RecordingContext recordingContext) {
        var bufferManager = recordingContext.getDynamicBufferManager();

        var commandList = new DrawCommandList();

        var textureTracker = new TextureTracker();

        try (var geometryUniformTracker = new UniformTracker(bufferManager, mGeometryUniformDataCache);
             var fragmentUniformTracker = new UniformTracker(bufferManager, mFragmentUniformDataCache);
             var drawWriter = new MeshDrawWriter(bufferManager, commandList)) {

            if (bufferManager.hasMappingFailed()) {
                return null;
            }

            int surfaceHeight = mReadView.getHeight();
            int surfaceOrigin = mReadView.getOrigin();

            // BM Quicksort - unstable
            mSortKeys.unstableSort(null);

            Rect2i deviceBounds = new Rect2i(0, 0, mImageInfo.width(), mImageInfo.height());
            Rect2i currentScissor = new Rect2i(deviceBounds);
            int lastPipelineIndex = DrawPass.INVALID_INDEX;

            commandList.setScissor(currentScissor, surfaceHeight, surfaceOrigin);

            for (int i = 0, e = mSortKeys.size(); i < e; i++) {
                var key = mSortKeys.get(i);
                var draw = key.draw();
                var step = key.step();
                int pipelineIndex = key.pipelineIndex();

                boolean pipelineStateChange = pipelineIndex != lastPipelineIndex;

                boolean scissorChange = step.getScissor(draw, currentScissor, deviceBounds);
                boolean geometryBindingChange = geometryUniformTracker.writeUniforms(
                        key.geometryUniformIndex()
                );
                boolean fragmentBindingChange = fragmentUniformTracker.writeUniforms(
                        key.fragmentUniformIndex()
                );
                boolean textureBindingChange = textureTracker.setCurrentTextures(key.textures());

                boolean dynamicStateChange = scissorChange ||
                        geometryBindingChange ||
                        fragmentBindingChange ||
                        textureBindingChange;

                if (pipelineStateChange) {
                    drawWriter.newPipelineState(
                            step.vertexBinding(),
                            step.instanceBinding(),
                            step.vertexStride(),
                            step.instanceStride()
                    );
                } else if (dynamicStateChange) {
                    drawWriter.newDynamicState();
                }

                // Make state changes before accumulating new draw data
                if (pipelineStateChange) {
                    commandList.bindGraphicsPipeline(pipelineIndex);
                    lastPipelineIndex = pipelineIndex;
                }
                if (dynamicStateChange) {
                    if (scissorChange) {
                        commandList.setScissor(currentScissor, surfaceHeight, surfaceOrigin);
                    }
                    if (geometryBindingChange) {
                        geometryUniformTracker.bindUniforms(
                                DrawPass.GEOMETRY_UNIFORM_BLOCK_BINDING,
                                commandList
                        );
                    }
                    if (fragmentBindingChange) {
                        fragmentUniformTracker.bindUniforms(
                                DrawPass.FRAGMENT_UNIFORM_BLOCK_BINDING,
                                commandList
                        );
                    }
                    if (textureBindingChange) {
                        textureTracker.bindTextures(commandList);
                    }
                }

                var pipelineDesc = mIndexToPipeline.get(pipelineIndex);

                step.writeMesh(drawWriter, draw, draw.mSolidColor,
                        pipelineDesc.mayRequireLocalCoords());

                if (bufferManager.hasMappingFailed()) {
                    return null;
                }
            }
            // Finish recording draw calls for any collected data at the end of the loop
            drawWriter.flush();
            commandList.finish();

            var bounds = new Rect2i();
            mPassBounds.roundOut(bounds);
            var pipelines = mIndexToPipeline;
            mIndexToPipeline = null;

            return new DrawPass(commandList, bounds,
                    mDepthStencilFlags,
                    pipelines,
                    mTextureDataGatherer.detachSamplerDescs(),
                    mTextureDataGatherer.detachTextureViews());
        } finally {
            // Now that there is content drawn to the target, that content must be loaded on any subsequent
            // render pass.
            reset(LoadOp.kLoad, null);
        }
    }

    public void flush(RecordingContext context) {
        if (!mPendingUploads.isEmpty()) {
            mDrawTaskList.appendTasks(mPendingUploads);
            // The appendTasks() steals the collected upload instances, automatically resetting this list
            assert mPendingUploads.isEmpty();
        }

        assert mSortKeys.size() == mNumSteps;
        if (mNumSteps == 0 && mPendingLoadOp != LoadOp.kClear) {
            // Nothing will be rasterized to the target that warrants a RenderPassTask, but we preserve
            // any added uploads or compute tasks since those could also affect the target w/o
            // rasterizing anything directly.
            return;
        }

        byte pendingLoadOp = mPendingLoadOp;

        DrawPass pass = snapDrawPass(context);

        if (pass != null) {
            RenderPassTask renderPassTask = RenderPassTask.make(
                    context,
                    pass,
                    mReadView.refProxy(),
                    null,
                    pendingLoadOp,
                    StoreOp.kStore,
                    mPendingClearColor
            );
            mDrawTaskList.appendTask(renderPassTask);
        }
    }

    @Nullable
    @SharedPtr
    public DrawTask snapDrawTask(RecordingContext context) {
        flush(context);

        if (mDrawTaskList.isEmpty()) {
            return null;
        }

        DrawTask task = new DrawTask(mReadView.refProxy(), mDrawTaskList);
        mDrawTaskList = new TaskList();
        return task;
    }

    /**
     * @param clearColor premultiplied RGBA color, if loadOp is Clear
     */
    private void reset(byte loadOp, @Size(4) float @Nullable [] clearColor) {
        mPendingLoadOp = loadOp;
        if (loadOp == LoadOp.kClear) {
            if (clearColor != null) {
                System.arraycopy(clearColor, 0, mPendingClearColor, 0, 4);
            } else {
                Arrays.fill(mPendingClearColor, 0.0f);
            }
        }

        mSortKeys.clear();

        mNumSteps = 0;
        mDepthStencilFlags = DepthStencilFlags.kNone;

        mPassBounds.setInfiniteInverted();

        mTextureDataGatherer.resetCache();
        mGeometryUniformDataCache.reset();
        mFragmentUniformDataCache.reset();

        // intended behavior: only ArrayLists shrink, HashMaps will not shrink
        mPipelineToIndex.clear();
        mIndexToPipeline = new ObjectArrayList<>();
    }

    /**
     * The sorting is used to minimize state change.
     * <p>
     * Sorting order:
     * painter's order, stencil disjoint set index,
     * render step index, pipeline index, geometry uniform index,
     * fragment uniform index, texture and sampler binding
     *
     * @param orderKey 32-16 painter's order, 16-0 stencil disjoint set index
     */
    public record SortKey(Draw draw, int orderKey, long pipelineKey, int[] textures) implements Comparable<SortKey> {

        public static final int PAINTERS_ORDER_OFFSET = 32;
        public static final int PAINTERS_ORDER_MASK = (1 << 16) - 1;

        public static final int STENCIL_INDEX_OFFSET = 16;
        public static final int STENCIL_INDEX_MASK = (1 << 16) - 1;

        static {
            //noinspection ConstantValue
            assert DrawOrder.PAINTERS_ORDER_SHIFT == PAINTERS_ORDER_OFFSET &&
                    DrawOrder.STENCIL_INDEX_SHIFT == STENCIL_INDEX_OFFSET;
        }

        // 52-50 step, 50-34 pipeline, 34-17 geometry uniform, 17-0 fragment uniform
        public static final int STEP_INDEX_OFFSET = 50;
        public static final int STEP_INDEX_MASK = (1 << 2) - 1;

        public static final int PIPELINE_INDEX_OFFSET = 34;
        public static final int PIPELINE_INDEX_MASK = (1 << 16) - 1;

        // requires one extra bit to represent invalid index
        public static final int GEOMETRY_UNIFORM_INDEX_OFFSET = 17;
        public static final int GEOMETRY_UNIFORM_INDEX_MASK = (1 << 17) - 1;
        public static final int FRAGMENT_UNIFORM_INDEX_OFFSET = 0;
        public static final int FRAGMENT_UNIFORM_INDEX_MASK = (1 << 17) - 1;

        public SortKey(Draw draw,
                       int stepIndex,
                       int pipelineIndex,
                       int geometryUniformIndex,
                       int fragmentUniformIndex,
                       int[] textures) {
            this(draw,
                    // the 16-48 bits are just we want
                    (int) (draw.mDrawOrder >>> DrawOrder.STENCIL_INDEX_SHIFT),
                    ((long) stepIndex << STEP_INDEX_OFFSET) |
                            ((long) pipelineIndex << PIPELINE_INDEX_OFFSET) |
                            ((long) geometryUniformIndex << GEOMETRY_UNIFORM_INDEX_OFFSET) |
                            ((long) fragmentUniformIndex << FRAGMENT_UNIFORM_INDEX_OFFSET),
                    textures);
            assert (stepIndex & STEP_INDEX_MASK) == stepIndex;
            assert pipelineIndex >= 0;
            assert textures.length > 0 || textures == IntArrays.EMPTY_ARRAY;
        }

        public GeometryStep step() {
            return draw.mRenderer.step(
                    (int) ((pipelineKey >>> STEP_INDEX_OFFSET) & STEP_INDEX_MASK));
        }

        public int pipelineIndex() {
            return (int) ((pipelineKey >>> PIPELINE_INDEX_OFFSET) & PIPELINE_INDEX_MASK);
        }

        public int geometryUniformIndex() {
            return (int) ((pipelineKey >>> GEOMETRY_UNIFORM_INDEX_OFFSET) & GEOMETRY_UNIFORM_INDEX_MASK);
        }

        public int fragmentUniformIndex() {
            return (int) ((pipelineKey >>> FRAGMENT_UNIFORM_INDEX_OFFSET) & FRAGMENT_UNIFORM_INDEX_MASK);
        }

        @Override
        public int compareTo(@NonNull SortKey o) {
            int res = Integer.compareUnsigned(orderKey, o.orderKey);
            if (res != 0) return res;
            res = Long.compareUnsigned(pipelineKey, o.pipelineKey);
            if (res != 0) return res;
            return Arrays.compare(textures, o.textures);
        }
    }
}
