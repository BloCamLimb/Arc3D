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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.engine.SamplerDesc;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;

import java.util.function.ToIntFunction;

/**
 * This class serves two purposes: it collects texture view and sampler information for
 * a Draw; and it provides backing storage and deduplication for all texture data
 * within a DrawPass.
 * <p>
 * Since at most one step performs shading, fragment textures (providing color) come first,
 * followed by geometry textures (providing coverage, rewind for each step).
 */
public final class TextureDataGatherer implements AutoCloseable {

    private final Object2IntOpenHashMap<@RawPtr ImageProxyView> mTextureToIndex = new Object2IntOpenHashMap<>();
    private ObjectArrayList<@SharedPtr ImageProxyView> mIndexToTexture = new ObjectArrayList<>();
    private final ToIntFunction<@RawPtr ImageProxyView> mTextureAccumulator = (@RawPtr ImageProxyView texture) -> {
        int index = mIndexToTexture.size();
        // persist the proxy ref, since ImageProxyView represents a single owner, just create new one
        mIndexToTexture.add(new ImageProxyView(texture));
        return index;
    };

    private final Object2IntOpenHashMap<SamplerDesc> mSamplerToIndex = new Object2IntOpenHashMap<>();
    private ObjectArrayList<SamplerDesc> mIndexToSampler = new ObjectArrayList<>();
    private final ToIntFunction<SamplerDesc> mSamplerAccumulator = sampler -> {
        int index = mIndexToSampler.size();
        mIndexToSampler.add(sampler);
        return index;
    };

    final IntArrayList mTextureData = new IntArrayList();
    int mPaintTextureCount;

    public void add(@RawPtr @NonNull ImageProxyView textureView, @NonNull SamplerDesc samplerDesc) {
        int textureIndex = mTextureToIndex.computeIfAbsent(textureView, mTextureAccumulator);
        int samplerIndex = mSamplerToIndex.computeIfAbsent(samplerDesc, mSamplerAccumulator);
        mTextureData.add(textureIndex);
        mTextureData.add(samplerIndex);
    }

    public void resetForDraw() {
        mTextureData.clear();
        mPaintTextureCount = 0;
    }

    public void mark() {
        mPaintTextureCount = mTextureData.size();
    }

    /**
     * Rewind to collect data for another step using the same paint data.
     */
    public void rewindToMark() {
        mTextureData.size(mPaintTextureCount);
    }

    /**
     * Returns a copied int array representing the texture binding.
     * Holds repeated texture index and sampler index.
     */
    public int @NonNull [] finish(boolean includePaintBlock) {
        int off;
        if (includePaintBlock || (off = mPaintTextureCount) == 0) {
            // intended behavior: if empty, this returns a singleton instead of a new empty array
            return mTextureData.toIntArray();
        } else {
            // toIntArray for a slice
            int len = mTextureData.size() - off;
            assert len >= 0 && len % 2 == 0;
            if (len == 0) return IntArrays.EMPTY_ARRAY;
            int[] ret = new int[len];
            mTextureData.getElements(off, ret, 0, len);
            return ret;
        }
    }

    public void resetCache() {
        // intended behavior: only ArrayLists shrink, HashMaps will not shrink
        mTextureToIndex.clear();
        if (mIndexToTexture != null) {
            mIndexToTexture.forEach(ImageProxyView::close);
        }
        mIndexToTexture = new ObjectArrayList<>();

        mSamplerToIndex.clear();
        mIndexToSampler = new ObjectArrayList<>();
    }

    public ObjectArrayList<@SharedPtr ImageProxyView> detachTextureViews() {
        var res = mIndexToTexture;
        mIndexToTexture = null;
        return res;
    }

    public ObjectArrayList<SamplerDesc> detachSamplerDescs() {
        var res = mIndexToSampler;
        mIndexToSampler = null;
        return res;
    }

    @Override
    public void close() {
        if (mIndexToTexture != null) {
            mIndexToTexture.forEach(ImageProxyView::close);
        }
        mIndexToTexture = null;
    }
}
