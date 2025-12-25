#include <jni.h>
#include <string>
#include <aaudio/AAudio.h>
#include <vector>
#include <android/log.h>

#define TAG "sndcpy-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Buffer kecil untuk low latency
#define SAMPLES_PER_FRAME 128 

AAudioStream *stream = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_rom1v_sndcpy_RecordService_nativeStartCapture(JNIEnv *env, jobject thiz, jobject projection) {
    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);

    // Konfigurasi Stream
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE); // Paksa jalur cepat
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, 48000); // Standard Android Native Rate

    // Buka stream
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    if (result != AAUDIO_OK) {
        LOGE("Gagal membuka AAudio stream: %s", AAudio_convertResultToText(result));
    } else {
        // Cek apakah dapet MMAP
        bool isMmap = AAudioStream_isMMapUsed(stream);
        LOGI("AAudio Stream Berhasil Dibuka. Jalur MMAP: %s", isMmap ? "YA" : "TIDAK (Legacy)");
        
        result = AAudioStream_requestStart(stream);
        if (result != AAUDIO_OK) {
            LOGE("Gagal memulai AAudio stream: %s", AAudio_convertResultToText(result));
        }
    }
    
    AAudioStreamBuilder_delete(builder);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_rom1v_sndcpy_RecordService_nativeReadAudio(JNIEnv *env, jobject thiz) {
    if (stream == nullptr) return nullptr;

    int16_t buffer[SAMPLES_PER_FRAME * 2];
    // Timeout 0 agar non-blocking
    int32_t framesRead = AAudioStream_read(stream, buffer, SAMPLES_PER_FRAME, 0);

    if (framesRead > 0) {
        int byteLength = framesRead * 2 * sizeof(int16_t);
        jbyteArray result = env->NewByteArray(byteLength);
        env->SetByteArrayRegion(result, 0, byteLength, (jbyte*)buffer);
        return result;
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rom1v_sndcpy_RecordService_nativeStopCapture(JNIEnv *env, jobject thiz) {
    if (stream != nullptr) {
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);
        stream = nullptr;
        LOGI("AAudio Stream ditutup.");
    }
}
