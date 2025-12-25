#include <jni.h>
#include <string>
#include <aaudio/AAudio.h>
#include <vector>
#include <android/log.h>

#define TAG "sndcpy-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define SAMPLES_PER_FRAME 128 

AAudioStream *stream = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_rom1v_sndcpy_RecordService_nativeStartCapture(JNIEnv *env, jobject thiz, jobject projection) {
    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE); 
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, 48000); 

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    if (result != AAUDIO_OK) {
        LOGE("Gagal membuka AAudio stream: %s", AAudio_convertResultToText(result));
    } else {
        // Cukup log ini saja, jangan pakai AAudioStream_isMMapUsed
        LOGI("AAudio Stream Berhasil Dibuka!");
        
        AAudioStream_requestStart(stream);
    }
    AAudioStreamBuilder_delete(builder);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_rom1v_sndcpy_RecordService_nativeReadAudio(JNIEnv *env, jobject thiz) {
    if (stream == nullptr) return nullptr;

    int16_t buffer[SAMPLES_PER_FRAME * 2];
    // Tambahkan timeout kecil (misal 10ms) jangan 0, biar Java loop-nya lebih stabil
    int32_t framesRead = AAudioStream_read(stream, buffer, SAMPLES_PER_FRAME, 10000000); 

    if (framesRead > 0) {
        jbyteArray result = env->NewByteArray(framesRead * 4); // 2 channels * 2 bytes
        env->SetByteArrayRegion(result, 0, framesRead * 4, (jbyte*)buffer);
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
