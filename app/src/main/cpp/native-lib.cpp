#include <jni.h>
#include <string>
#include <aaudio/AAudio.h>
#include <vector>

// Buffer kecil untuk low latency (FPS Game optimized)
#define SAMPLES_PER_FRAME 128 

AAudioStream *stream = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_rom1v_sndcpy_AudioCaptureService_nativeStartCapture(JNIEnv *env, jobject thiz) {
    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2); // Stereo
    AAudioStreamBuilder_setSampleRate(builder, 48000);

    // Buka stream
    AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStream_requestStart(stream);
    
    AAudioStreamBuilder_delete(builder);
}

// Fungsi untuk ambil data audio (akan dikirim ke socket)
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_rom1v_sndcpy_AudioCaptureService_nativeReadAudio(JNIEnv *env, jobject thiz) {
    int16_t buffer[SAMPLES_PER_FRAME * 2];
    int32_t framesRead = AAudioStream_read(stream, buffer, SAMPLES_PER_FRAME, 0);

    if (framesRead > 0) {
        jbyteArray result = env->NewByteArray(framesRead * 2 * sizeof(int16_t));
        env->SetByteArrayRegion(result, 0, framesRead * 2 * sizeof(int16_t), (jbyte*)buffer);
        return result;
    }
    return nullptr;
}
