/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ringdroid.soundfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.ArrayList;

import android.util.Log;

/**
 * Mpg123MP3 represents an MP3 file by doing a "cheap" scan of the file,
 * parsing the frame headers only and getting an extremely rough estimate
 * of the volume level of each frame.
 * 
 * TODO: Useful unit tests might be to look for sync in various places:
 * FF FA
 * FF FB
 * 00 FF FA
 * FF FF FA
 * ([ 00 ] * 12) FF FA
 * ([ 00 ] * 13) FF FA
 */
public class Mpg123MP3 extends CheapSoundFile {
    private static String TAG = "Mpg123MP3";

    public static Factory getFactory() {
        return new Factory() {
            public CheapSoundFile create() {
                return new Mpg123MP3();
            }
            public String[] getSupportedExtensions() {
                return new String[] { "mp3" };
            }
        };
    }

    // Member variables representing frame data
    private int mNumFrames;
    private int[] mFrameOffsets;
    private int[] mFrameLens;
    private int[] mFrameGains;
    private int mFileSize;
    private int mAvgBitRate;
    private int mGlobalSampleRate;
    private int mGlobalChannels;

    // Member variables used during initialization
    private int mMaxFrames;
    private int mBitrateSum;
    private int mMinGain;
    private int mMaxGain;

    // Samples Per Frame. will be recalculated by decoder
    private int mSamplesPerFrame = 1152;

    public Mpg123MP3() {
    }

    public int getNumFrames() {
        return mNumFrames;
    }

    public int[] getFrameOffsets() {
        return mFrameOffsets;
    }

    public int getSamplesPerFrame() {
        return mSamplesPerFrame;
    }

    public int[] getFrameLens() {
        return mFrameLens;
    }

    public int[] getFrameGains() {
        return mFrameGains;
    }

    public int getFileSizeBytes() {
        return mFileSize;        
    }

    public int getAvgBitrateKbps() {
        return mAvgBitRate;
    }

    public int getSampleRate() {
        return mGlobalSampleRate;
    }

    public int getChannels() {
        return mGlobalChannels;
    }

    public String getFiletype() {
        return "MP3";
    }

    /**
     * MP3 supports seeking into the middle of the file, no header needed,
     * so this method is supported to hear exactly what a "cut" of the file
     * sounds like without needing to actually save a file to disk first.
     */
    public int getSeekableFrameOffset(int frame) {
        if (frame <= 0) {
            return 0;
        } else if (frame >= mNumFrames) {
            return mFileSize;
        } else {
            return mFrameOffsets[frame];
        }
    }

    public void ReadFile(File inputFile)
            throws java.io.FileNotFoundException,
            java.io.IOException {
        super.ReadFile(inputFile);

        Mpg123Decoder decoder = new Mpg123Decoder( inputFile.getAbsolutePath() );

        mNumFrames = 0;
        mMaxFrames = 64;  // This will grow as needed
        mFrameOffsets = new int[mMaxFrames];
        mFrameLens = new int[mMaxFrames];
        mFrameGains = new int[mMaxFrames];
        mBitrateSum = 0;
        mMinGain = 255;
        mMaxGain = 0;

        // No need to handle filesizes larger than can fit in a 32-bit int
        mFileSize = (int)mInputFile.length();

        int pos = 0;

        while (true) {
            if (mProgressListener != null) {
                boolean keepGoing = mProgressListener.reportProgress(
                    pos * 1.0 / mFileSize);
                if (!keepGoing) {
                    break;
                }
            }

            int ret = decoder.readNextFrame();
            if (ret < 0)
                break;
            ret = decoder.decodeFrame();
            int gain = decoder.readSamplesAll();
            if (gain < 0)
                break;

            // The third byte has the bitrate and samplerate
            int bitRate = decoder.getBitRate();
            int sampleRate = decoder.getSampleRate();

            // From here on we assume the frame is good
            mGlobalSampleRate = sampleRate;
            mGlobalChannels = decoder.getNumChannels();

            mBitrateSum += bitRate;

            int frameLen = decoder.getFrameLen();
            Log.d(TAG, "pos = " + pos);
            Log.d(TAG, "frameLen = " + frameLen);
            mFrameOffsets[mNumFrames] = pos;
            mFrameLens[mNumFrames] = frameLen;
            mFrameGains[mNumFrames] = gain;
            if (gain < mMinGain)
                mMinGain = gain;
            if (gain > mMaxGain)
                mMaxGain = gain;

            pos += frameLen;

            mNumFrames++;
            Log.d(TAG, "Gain[" + mNumFrames + "] => " + gain);
            if (mNumFrames == mMaxFrames) {
                // We need to grow our arrays.  Rather than naively
                // doubling the array each time, we estimate the exact
                // number of frames we need and add 10% padding.  In
                // practice this seems to work quite well, only one
                // resize is ever needed, however to avoid pathological
                // cases we make sure to always double the size at a minimum.

                mAvgBitRate = mBitrateSum / mNumFrames;
                int totalFramesGuess =
                    ((mFileSize / mAvgBitRate) * sampleRate) / 144000;
                int newMaxFrames = totalFramesGuess * 11 / 10;
                if (newMaxFrames < mMaxFrames * 2)
                    newMaxFrames = mMaxFrames * 2;

                int[] newOffsets = new int[newMaxFrames];
                int[] newLens = new int[newMaxFrames];
                int[] newGains = new int[newMaxFrames];
                for (int i = 0; i < mNumFrames; i++) {
                    newOffsets[i] = mFrameOffsets[i];
                    newLens[i] = mFrameLens[i];
                    newGains[i] = mFrameGains[i];
                }
                mFrameOffsets = newOffsets;
                mFrameLens = newLens;
                mFrameGains = newGains;
                mMaxFrames = newMaxFrames;
            }
        }

        // We're done reading the file, do some postprocessing
        if (mNumFrames > 0)
            mAvgBitRate = mBitrateSum / mNumFrames;
        else
            mAvgBitRate = 0;

        mSamplesPerFrame = decoder.getSamplesPerFrame();

        for (int i = 0; i < mFrameGains.length; i++) {
            Log.d(TAG, "[" + i + "] => " + mFrameGains[i]);
        }
    }

    public void WriteFile(File outputFile, int startFrame, int numFrames)
            throws java.io.IOException {
        outputFile.createNewFile();
        FileInputStream in = new FileInputStream(mInputFile);
        FileOutputStream out = new FileOutputStream(outputFile);
        int maxFrameLen = 0;
        for (int i = 0; i < numFrames; i++) {
            if (mFrameLens[startFrame + i] > maxFrameLen)
                maxFrameLen = mFrameLens[startFrame + i];
        }
        byte[] buffer = new byte[maxFrameLen];
        int pos = 0;
        for (int i = 0; i < numFrames; i++) {
            int skip = mFrameOffsets[startFrame + i] - pos;
            int len = mFrameLens[startFrame + i];
            if (skip > 0) {
                in.skip(skip);
                pos += skip;
            }
            in.read(buffer, 0, len);
            out.write(buffer, 0, len);
            pos += len;
        }
        in.close();
        out.close();
    }
};
