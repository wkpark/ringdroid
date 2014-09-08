package com.ringdroid.soundfile;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * A native MP3 {@link Decoder} based on libmad. Decodes
 * stereo input to mono.
 * @author mzechner
 *
 */
public class NativeMP3Decoder implements Decoder
{
    static {
        System.loadLibrary("mad_jni");
    }

        /** the handle to the native mp3 decoder **/
        private int handle;

        /** the float buffer used to read in the samples **/
        private FloatBuffer buffer;

        /** the short buffer **/
        private ShortBuffer shortBuffer;

        /**
         * Constructor, sets the file to decode. The file is given in absolute terms! FIXME
         *
         * @param file The file.
         */




        public NativeMP3Decoder( String file )
        {
                handle = openFile( file );
                if( handle == -1 )
                        throw new IllegalArgumentException( "Couldn't open file '" + file + "'" );
        }

        private native int openFile( String file );

        /**
         * Reads in numSamples float PCM samples to the provided direct FloatBuffer using the
         * handle retrievable via {@link NativeMP3Decoder.getHandle()}. This is for
         * people who know what they do. Returns the number of samples actually read.
         *
         * @param handle The handle
         * @param buffer The direct FloatBuffer
         * @param numSamples The number of samples to read
         * @return The number of samples read.
         */
        public native int readSamples( int handle, FloatBuffer buffer, int numSamples );

        /**
         * Reads in numSamples 16-bit signed PCM samples to the provided direct FloatBuffer using the
         * handle retrievable via {@link NativeMP3Decoder.getHandle()}. This is for
         * people who know what they do. Returns the number of samples actually read.
         *
         * @param handle The handle
         * @param buffer The direct FloatBuffer
         * @param numSamples The number of samples to read
         * @return The number of samples read.
         */
        public native int readSamples( int handle, ShortBuffer buffer, int numSamples );

        private native void closeFile( int handle );

        private native float getProgress( int handle );



        // 1/50초에 해당하는 파형값을 가져옴
        public native int readSamplesAll( int handle );

        /**
         * @return The handle retrieved from the native side.
         */
        public int getHandle( )
        {
                return handle;
        }

        /**
         * {@inheritDoc}
         */
        public int readSamples(float[] samples)
        {
                if( buffer == null || buffer.capacity() != samples.length )
                {
                        ByteBuffer byteBuffer = ByteBuffer.allocateDirect( samples.length * Float.SIZE / 8 );
                        byteBuffer.order(ByteOrder.nativeOrder());
                        buffer = byteBuffer.asFloatBuffer();
                }

                int readSamples = readSamples( handle, buffer, samples.length );
                if( readSamples == 0 )
                {
                        closeFile( handle );
                        return 0;
                }

                buffer.position(0);
                buffer.get( samples );

                return samples.length;
        }

        public float getProgress()
        {
        	
        	return getProgress(handle);
        }

        /**
         * {@inheritDoc}
         */
        public int readSamples(short[] samples)
        {
                if( shortBuffer == null || shortBuffer.capacity() != samples.length )
                {
                        ByteBuffer byteBuffer = ByteBuffer.allocateDirect( samples.length * Short.SIZE / 8 );
                        byteBuffer.order(ByteOrder.nativeOrder());
                        shortBuffer = byteBuffer.asShortBuffer();
                }

                int readSamples = readSamples( handle, shortBuffer, samples.length );
                if( readSamples == 0 )
                {
                        closeFile( handle );
                        return 0;
                }

                shortBuffer.position(0);
                shortBuffer.get( samples );

                return samples.length;
        }

        /**
         * {@inheritDoc}
         */
        public void dispose( )
        {
                closeFile(handle);
        }
}
