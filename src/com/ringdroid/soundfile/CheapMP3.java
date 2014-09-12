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

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import android.util.Log;

/**
 * CheapMP3 represents an MP3 file by doing a "cheap" scan of the file,
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
public class CheapMP3 extends CheapSoundFile {
    private static final String TAG = "CheapMP3";
    private static final int LAYER_I = 1;
    private static final int LAYER_III = 3;

    public static Factory getFactory() {
        return new Factory() {
            public CheapSoundFile create() {
                return new CheapMP3();
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

    private int mLayer;
    private int mVersion;

    public CheapMP3() {
    }

    public int getNumFrames() {
        return mNumFrames;
    }

    public int[] getFrameOffsets() {
        return mFrameOffsets;
    }

    public int getSamplesPerFrame() {
        int samples_per_frame = 1152;

        if (mLayer == LAYER_I) {
            samples_per_frame = 384;
        } else if (mLayer == LAYER_III) {
            if (mVersion == Header.MPEG2_LSF || mVersion == Header.MPEG25_LSF)
                samples_per_frame = 576;
        }

        return samples_per_frame;
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

        FileInputStream stream = new FileInputStream(mInputFile);

        Decoder decoder = new Decoder();
        Bitstream bitstream = new Bitstream(stream);

        int pos = 0;
        int gain = 0;

        try {
            Header header = bitstream.readFrame();

            int nChannel = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
            mGlobalChannels = nChannel;

            mLayer = header.layer();
            mVersion = header.version();

            while (true) {
                if (mProgressListener != null) {
                    boolean keepGoing = mProgressListener.reportProgress(
                            pos * 1.0 / mFileSize);
                    if (!keepGoing) {
                        break;
                    }
                }

                SampleBuffer frame = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] pcm = frame.getBuffer();
                bitstream.closeFrame();

                double sum = 0.0f;
                int k = 0;
                int tmp;
                Log.d(TAG, "pcm length = " + frame.getBufferLength());
                for (int j = 0; j < frame.getBufferLength(); j++) {
                    tmp = pcm[k] > 0 ? pcm[k] : -pcm[k];
                    sum += tmp / 32767.0f;
                    k += nChannel;
                }
                gain = (int) (sum / frame.getBufferLength() * 255);
                //Log.d(TAG, "Gain[" + mNumFrames + "]=" + gain);

                // set the bitrate and samplerate
                int bitRate = header.bitrate();
                int sampleRate = header.frequency();

                mGlobalSampleRate = sampleRate;
                mBitrateSum += bitRate;

                int frameLen = header.calculate_framesize() + 4;
                Log.d(TAG, "pos = " + pos);
                Log.d(TAG, "frameLen = " + frameLen);
                mFrameOffsets[mNumFrames] = pos;
                mFrameLens[mNumFrames] = frameLen;
                mFrameGains[mNumFrames] = gain;
                if (gain < mMinGain)
                    mMinGain = gain;
                if (gain > mMaxGain)
                    mMaxGain = gain;

                mNumFrames++;
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

                header = bitstream.readFrame();
                if (header == null)
                    break;

                pos += frameLen;
            }
        } catch (BitstreamException e) {
                Log.e(TAG, "BitstreamException", e);
        } catch (DecoderException e) {
                Log.e(TAG, "DecoderException", e);
        } finally {
            if (stream != null)
                stream.close();
        }

        // We're done reading the file, do some postprocessing
        if (mNumFrames > 0)
            mAvgBitRate = mBitrateSum / mNumFrames;
        else
            mAvgBitRate = 0;
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
