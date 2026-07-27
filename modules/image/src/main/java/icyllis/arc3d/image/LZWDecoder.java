/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2026 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.image;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The GIF format uses LSB first. The initial code size can vary between 2 and 8 bits, inclusive.
 */
public final class LZWDecoder {

    private static final ThreadLocal<LZWDecoder> TLS = ThreadLocal.withInitial(LZWDecoder::new);

    private static final int MAX_TABLE_SIZE = 1 << 12;

    // input data buffer
    private Decoder mInput;

    private int mInitCodeSize;
    // ClearCode = (1 << L) + 0;
    // EndOfInfo = (1 << L) + 1;
    // NewCodeIndex = (1 << L) + 2;
    private int mClearCode;
    private int mEndOfInfo;

    private int mCodeSize;
    private int mCodeMask;

    private int mTableIndex;
    private int mPrevCode;

    private final ByteBuffer mBlock = ByteBuffer.allocate(255);
    private int mInData;
    private int mInBits;

    // table
    private final int[] mPrefix = new int[MAX_TABLE_SIZE];
    private final byte[] mSuffix = new byte[MAX_TABLE_SIZE];
    private final byte[] mInitial = new byte[MAX_TABLE_SIZE];
    private final int[] mLength = new int[MAX_TABLE_SIZE];
    private final byte[] mString = new byte[MAX_TABLE_SIZE];

    /**
     * Returns thread local instance.
     */
    public static LZWDecoder getInstance() {
        return TLS.get();
    }

    /**
     * Reset the decoder with the given input data buffer.
     *
     * @param input the compressed data stream
     * @return the string table
     */
    public byte[] init(Decoder input, int initCodeSize) {
        mInput = input;
        mBlock.limit(0);
        mInData = 0;
        mInBits = 0;
        mInitCodeSize = initCodeSize;
        mClearCode = 1 << mInitCodeSize;
        mEndOfInfo = mClearCode + 1;
        initTable();
        return mString;
    }

    /**
     * Decode next string of data, which can be accessed by {@link #init(Decoder, int)} method.
     *
     * @return the length of string, or -1 on EOI
     */
    public int readString() throws IOException {
        int code = getNextCode();
        if (code == mEndOfInfo) {
            return -1;
        } else if (code == mClearCode) {
            initTable();
            code = getNextCode();
            if (code == mEndOfInfo) {
                return -1;
            }
        } else {
            final int newSuffixIndex;
            if (code < mTableIndex) {
                newSuffixIndex = code;
            } else {
                newSuffixIndex = mPrevCode;
                if (code != mTableIndex) {
                    return -1;
                }
            }

            if (mTableIndex < MAX_TABLE_SIZE) {
                int tableIndex = mTableIndex;
                int prevCode = mPrevCode;

                mPrefix[tableIndex] = prevCode;
                mSuffix[tableIndex] = mInitial[newSuffixIndex];
                mInitial[tableIndex] = mInitial[prevCode];
                mLength[tableIndex] = mLength[prevCode] + 1;

                ++mTableIndex;
                if ((mTableIndex == (1 << mCodeSize)) && (mTableIndex < MAX_TABLE_SIZE)) {
                    ++mCodeSize;
                    mCodeMask = (1 << mCodeSize) - 1;
                }
            }
        }
        // reverse
        int c = code;
        int len = mLength[c];
        for (int i = len - 1; i >= 0; i--) {
            mString[i] = mSuffix[c];
            c = mPrefix[c];
        }

        mPrevCode = code;
        return len;
    }

    private void initTable() {
        int size = 1 << mInitCodeSize;
        for (int i = 0; i < size; i++) {
            mPrefix[i] = -1;
            mSuffix[i] = (byte) i;
            mInitial[i] = (byte) i;
            mLength[i] = 1;
        }

        for (int i = size; i < MAX_TABLE_SIZE; i++) {
            mPrefix[i] = -1;
            mSuffix[i] = 0;
            mInitial[i] = 0;
            mLength[i] = 1;
        }

        mCodeSize = mInitCodeSize + 1;
        mCodeMask = (1 << mCodeSize) - 1;
        mTableIndex = size + 2;
        mPrevCode = 0;
    }

    private int getNextCode() throws IOException {
        while (mInBits < mCodeSize) {
            if (!mBlock.hasRemaining()) {
                int len = mInput.nextRawByte() & 0xFF;
                if (len > 0) {
                    mInput.readFully(mBlock.position(0).limit(len));
                    mBlock.rewind();
                } else {
                    throw new DecoderException("Unexpected block terminator while reading LZW codes");
                }
            }
            mInData |= (mBlock.get() & 0xFF) << mInBits;
            mInBits += 8;
        }
        int code = mInData & mCodeMask;
        mInBits -= mCodeSize;
        mInData >>>= mCodeSize;
        return code;
    }
}
