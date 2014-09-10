/**
 * from http://www.badlogicgames.com/wordpress/?p=231
 * and WaveLoop source
 **/

#include <jni.h>
#include "NativeMP3Decoder.h"
#include "mad/mad.h"
#include <stdio.h>
#include <string.h>

#include <math.h>

#ifdef SHRT_MAX
#undef SHRT_MAX
#endif

#define SHRT_MAX (32767)
#define INPUT_BUFFER_SIZE	(5*8192)
#define OUTPUT_BUFFER_SIZE	8192 /* Must be an integer multiple of 4. */
 
/**
 * Struct holding the pointer to a wave file.
 */
struct MP3FileHandle
{
    int size;
    FILE* file;
    mad_stream stream;
    mad_frame frame;
    mad_synth synth;
    mad_timer_t timer;
    int leftSamples;
    int offset;
    unsigned char inputBuffer[INPUT_BUFFER_SIZE];
};
 
/** static WaveFileHandle array **/
static MP3FileHandle* handles[100];
 
/**
 * Seeks a free handle in the handles array and returns its index or -1 if no handle could be found
 */
static int findFreeHandle( )
{
    for( int i = 0; i < 100; i++ )
    {
        if( handles[i] == 0 )
            return i;
    }

    return -1;
}
 
static inline void closeHandle( MP3FileHandle* handle )
{
    fclose( handle->file );
    mad_synth_finish(&handle->synth);
    mad_frame_finish(&handle->frame);
    mad_stream_finish(&handle->stream);
    delete handle;
}
 
static inline signed short fixedToShort(mad_fixed_t Fixed)
{
    if(Fixed>=MAD_F_ONE)
        return(SHRT_MAX);
    if(Fixed<=-MAD_F_ONE)
        return(-SHRT_MAX);

    Fixed=Fixed>>(MAD_F_FRACBITS-15);
    return((signed short)Fixed);
}
 
 
JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_openFile(JNIEnv *env, jobject obj, jstring file)
{
    int index = findFreeHandle( );

    if( index == -1 )
        return -1;

    const char* fileString = env->GetStringUTFChars(file, NULL);
    FILE* fileHandle = fopen( fileString, "rb" );
    env->ReleaseStringUTFChars(file, fileString);
    if( fileHandle == 0 )
        return -1;

    MP3FileHandle* mp3Handle = new MP3FileHandle( );
    mp3Handle->file = fileHandle;
    fseek( fileHandle, 0, SEEK_END);
    mp3Handle->size = ftell( fileHandle );
    rewind( fileHandle );

    mad_stream_init(&mp3Handle->stream);
    mad_frame_init(&mp3Handle->frame);
    mad_synth_init(&mp3Handle->synth);
    mad_timer_reset(&mp3Handle->timer);

    handles[index] = mp3Handle;
    return index;
}
 
static inline int readNextFrame( MP3FileHandle* mp3 )
{
    do
    {
        if( mp3->stream.buffer == 0 || mp3->stream.error == MAD_ERROR_BUFLEN )
        {
            int inputBufferSize = 0;
            if( mp3->stream.next_frame != 0 )
            {
                int leftOver = mp3->stream.bufend - mp3->stream.next_frame;
                for( int i = 0; i < leftOver; i++ )
                    mp3->inputBuffer[i] = mp3->stream.next_frame[i];
                int readBytes = fread( mp3->inputBuffer + leftOver, 1, INPUT_BUFFER_SIZE - leftOver, mp3->file );
                if( readBytes == 0 )
                    return 0;
                inputBufferSize = leftOver + readBytes;
            }
            else
            {
                int readBytes = fread( mp3->inputBuffer, 1, INPUT_BUFFER_SIZE, mp3->file );
                if( readBytes == 0 )
                    return 0;
                inputBufferSize = readBytes;
            }

            mad_stream_buffer( &mp3->stream, mp3->inputBuffer, inputBufferSize );
            mp3->stream.error = MAD_ERROR_NONE;
        }

        if( mad_frame_decode( &mp3->frame, &mp3->stream ) )
        {
            if( mp3->stream.error == MAD_ERROR_BUFLEN ||(MAD_RECOVERABLE(mp3->stream.error)))
                continue;
            else
                return 0;
        }
        else
            break;
    } while( true );

    mad_timer_add( &mp3->timer, mp3->frame.header.duration );
    mad_synth_frame( &mp3->synth, &mp3->frame );
    mp3->leftSamples = mp3->synth.pcm.length;
    mp3->offset = 0;

    return -1;
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_readNextFrame(JNIEnv *env, jobject obj, jint handle)
{
    return readNextFrame( handles[handle] );
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_readSamples__ILjava_nio_FloatBuffer_2I(JNIEnv *env, jobject obj, jint handle, jobject buffer, jint size)
{
    MP3FileHandle* mp3 = handles[handle];
    float* target = (float*)env->GetDirectBufferAddress(buffer);

    int idx = 0;
    while( idx != size )
    {
        if( mp3->leftSamples > 0 )
        {
            for( ; idx < size && mp3->offset < mp3->synth.pcm.length; mp3->leftSamples--, mp3->offset++ )
            {
                int value = fixedToShort(mp3->synth.pcm.samples[0][mp3->offset]);

                if( MAD_NCHANNELS(&mp3->frame.header) == 2 )
                {
                    value += fixedToShort(mp3->synth.pcm.samples[1][mp3->offset]);
                    value /= 2;
                }

                target[idx++] = value / (float)SHRT_MAX;
            }
        }
        else
        {
            int result = readNextFrame( mp3 );
            if( result == 0 )
                return 0;
        }

    }
    if( idx > size )
        return 0;

    return size;
}


static int readSamples(jint handle, int & result, int scale)
{
    MP3FileHandle* mp3 = handles[handle];

    float sum = 0;
    int idx = 0;

    int size = mp3->leftSamples;
    for( ; idx < size && mp3->offset < mp3->synth.pcm.length; mp3->leftSamples--, mp3->offset++ )
    {
        int value = fixedToShort(mp3->synth.pcm.samples[0][mp3->offset]);

        if( MAD_NCHANNELS(&mp3->frame.header) == 2 )
        {
            value += fixedToShort(mp3->synth.pcm.samples[1][mp3->offset]);
            value /= 2;
        }

        sum += fabs(value / (float)SHRT_MAX);
        idx++;
    }

    result = (int)((sum / (float)size) * scale * 1.0f);

    return size;
}

static int readSamples2(jint handle, int & result, int scale)
{
    MP3FileHandle* mp3 = handles[handle];

    float sum = 0;
    int idx = 0;

    int size = mp3->leftSamples;
    for( ; idx < size && mp3->offset < mp3->synth.pcm.length; mp3->leftSamples-= 2, mp3->offset+=2 )
    {
        int value = fixedToShort(mp3->synth.pcm.samples[0][mp3->offset]);

        if( MAD_NCHANNELS(&mp3->frame.header) == 2 )
        {
            value += fixedToShort(mp3->synth.pcm.samples[1][mp3->offset]);
            value /= 2;
        }

        sum += fabs(value / (float)SHRT_MAX);
        idx++;
    }

    result = (int)((sum / (float)size) * scale * 2.0f);

    return size;
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_readSamplesAll(JNIEnv *env, jobject obj, jint handle)
{
    int oneSamples = 0;
    int size = readSamples2( handle, oneSamples , 500);
    if(size == 0)
        return -1;
    return oneSamples;
} 

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_readSamples__ILjava_nio_ShortBuffer_2I(JNIEnv *env, jobject obj, jint handle, jobject buffer, jint size)
{
    MP3FileHandle* mp3 = handles[handle];
    short* target = (short*)env->GetDirectBufferAddress(buffer);

    int idx = 0;
    while( idx != size )
    {
        if( mp3->leftSamples > 0 )
        {
            for( ; idx < size && mp3->offset < mp3->synth.pcm.length; mp3->leftSamples--, mp3->offset++ )
            {
                int value = fixedToShort(mp3->synth.pcm.samples[0][mp3->offset]);

                if( MAD_NCHANNELS(&mp3->frame.header) == 2 )
                {
                    value += fixedToShort(mp3->synth.pcm.samples[1][mp3->offset]);
                    value /= 2;
                }

                target[idx++] = value;
            }
        }
        else
        {
            int result = readNextFrame( mp3 );
            if( result == 0 )
                return 0;
        }

    }
    if( idx > size )
        return 0;

    return size;
}
 
JNIEXPORT void JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_closeFile(JNIEnv *env, jobject obj, jint handle)
{
    if( handles[handle] != 0 )
    {
        closeHandle( handles[handle] );
        handles[handle] = 0;
    }
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_getSize(JNIEnv *env, jobject obj, jint handle)
{
    return (jint) handles[handle]->size;
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_getFrameLen(JNIEnv *env, jobject obj, jint handle)
{
    MP3FileHandle* mp3 = handles[handle];

    unsigned int pad_slot, N;
    struct mad_header *header = &mp3->frame.header;

    /* from frame.c */
    /* calculate beginning of next frame */
    pad_slot = (header->flags & MAD_FLAG_PADDING) ? 1 : 0;

    if (header->layer == MAD_LAYER_I)
        N = ((12 * header->bitrate / header->samplerate) + pad_slot) * 4;
    else {
        unsigned int slots_per_frame;

        slots_per_frame = (header->layer == MAD_LAYER_III &&
                (header->flags & MAD_FLAG_LSF_EXT)) ? 72 : 144;

        N = (slots_per_frame * header->bitrate / header->samplerate) + pad_slot;
    }
    return (jint) N;
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_getBitRate(JNIEnv *env, jobject obj, jint handle)
{
    return (jint) handles[handle]->frame.header.bitrate;
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_getSampleRate(JNIEnv *env, jobject obj, jint handle)
{
    struct mad_header *header = &handles[handle]->frame.header;
    return (jint) header->samplerate;
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_getNchannels(JNIEnv *env, jobject obj, jint handle)
{
    return (jint) MAD_NCHANNELS(&handles[handle]->frame.header);
}

JNIEXPORT jlong JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_getOffset(JNIEnv *env, jobject obj, jint handle)
{
    return (jint) handles[handle]->stream.this_frame;
}

JNIEXPORT jint JNICALL Java_com_ringdroid_soundfile_NativeMP3Decoder_getSamplesPerFrame(JNIEnv *env, jobject obj, jint handle)
{
    MP3FileHandle* mp3 = handles[handle];
    struct mad_header *header = &mp3->frame.header;

    int samples_per_frame = 1152;

    if (header->layer == MAD_LAYER_I) {
        samples_per_frame = 384;
    } else if (header->layer == MAD_LAYER_III) {
        if (header->flags & MAD_FLAG_LSF_EXT || header->flags & MAD_FLAG_MPEG_2_5_EXT)
            samples_per_frame = 576;
    }

    return samples_per_frame;
}
