/**
 * from https://github.com/thasmin/android-mp3decoders
 *
 */
package com.ringdroid.soundfile;

public class Mpg123Decoder {
    static {
        System.loadLibrary("mpg123_jni");
        Mpg123Decoder.init();
    }

    private static native int init();
    private static native String getErrorMessage(int error);
    private static native long openFile(String filename);
    private static native void delete(long handle);
    private static native int readSamples(long handle, short[] buffer, int offset, int numSamples);
    private static native int skipSamples(long handle, int numSamples);
    private static native int seek(long handle, float offsetInSeconds);
    private static native float getPosition(long handle);
    private static native long getPositionInFrames(long handle);
    private static native int getNumChannels(long handle);
    private static native int getRate(long handle);
    private static native int getBitRate(long handle);
    private static native int getSampleRate(long handle);
    private static native long getNumFrames(long handle);
    private static native int getFrameLen(long handle);
    private static native float getDuration(long handle);
    private static native int getFramesPerSecond(long handle);
    private static native int getSamplesPerFrame(long handle);
    private static native int readNextFrame(long handle);
    private static native int decodeFrame(long handle);
    private static native int readSamplesAll(long handle);

    long _handle = 0;
    public Mpg123Decoder(String filename) {
        _handle = openFile(filename);
        if (_handle == -1)
            throw new IllegalArgumentException( "Couldn't open file '" + filename + "'" );
    }

    public void close() {
        if (_handle != 0)
            delete(_handle);
    }

    public int readSamples(short[] buffer, int offset, int numSamples) {
        return readSamples(_handle, buffer, offset, numSamples);
    }
    public int skipSamples(int numSamples) { return skipSamples(_handle, numSamples); }
    public int seek(float offset) { return seek(_handle, offset); }
    public float getPosition() { return getPosition(_handle); }
    public int getNumChannels() { return getNumChannels(_handle); }
    public int getRate() { return getRate(_handle); }
    public int getBitRate() { return getBitRate(_handle); }
    public int getSampleRate() { return getSampleRate(_handle); }
    public float getDuration() { return getDuration(_handle); }
    public int getFrameLen() { return getFrameLen(_handle); }
    public float getFramesPerSecond() { return getFramesPerSecond(_handle); }
    public int getSamplesPerFrame() { return getSamplesPerFrame(_handle); }
    public int readNextFrame() { return readNextFrame(_handle); }
    public int decodeFrame() { return decodeFrame(_handle); }
    public int readSamplesAll() { return readSamplesAll(_handle); }
}
