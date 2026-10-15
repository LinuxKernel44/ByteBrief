// JNI bridge between ByteBrief and llama.cpp.
//
// Design notes:
//  - No global model state: every model and session is an opaque handle owned by Java.
//  - Text crosses the JNI boundary as UTF-8 byte[] (never jstring) because JNI strings use
//    "modified UTF-8", which mishandles 4-byte sequences such as emoji.
//  - Generation is stateless per call: the KV cache is cleared and the full prompt is decoded.
//  - Cancellation is cooperative through llama's abort callback plus a check between tokens.

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

#define LOG_TAG "ByteBriefLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kBatchSize = 512;

// Must match the CODE_* constants in LlamaException.java.
constexpr int kCodeGeneric = 0;
constexpr int kCodeModelLoad = 1;
constexpr int kCodeOutOfMemory = 2;
constexpr int kCodeContextOverflow = 3;
constexpr int kCodeDecode = 4;

struct Session {
    llama_model * model = nullptr;  // not owned
    llama_context * ctx = nullptr;
    std::atomic<bool> cancel{false};
};

void throw_llama(JNIEnv * env, int code, const std::string & message) {
    jclass cls = env->FindClass("app/bytebrief/llama/LlamaException");
    if (cls == nullptr) {
        return;  // NoClassDefFoundError is already pending
    }
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;)V");
    jstring msg = env->NewStringUTF(message.c_str());
    auto ex = (jthrowable) env->NewObject(cls, ctor, code, msg);
    env->Throw(ex);
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(msg);
}

std::string to_string(JNIEnv * env, jbyteArray bytes) {
    const jsize len = env->GetArrayLength(bytes);
    std::string out((size_t) len, '\0');
    if (len > 0) {
        env->GetByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte *>(out.data()));
    }
    return out;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray((jsize) s.size());
    if (arr != nullptr && !s.empty()) {
        env->SetByteArrayRegion(arr, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
    }
    return arr;
}

void android_log_callback(ggml_log_level level, const char * text, void *) {
    int prio = ANDROID_LOG_INFO;
    if (level == GGML_LOG_LEVEL_ERROR) {
        prio = ANDROID_LOG_ERROR;
    } else if (level == GGML_LOG_LEVEL_WARN) {
        prio = ANDROID_LOG_WARN;
    } else if (level == GGML_LOG_LEVEL_DEBUG) {
        prio = ANDROID_LOG_DEBUG;
    }
    __android_log_write(prio, LOG_TAG, text);
}

bool abort_callback(void * data) {
    return static_cast<Session *>(data)->cancel.load(std::memory_order_relaxed);
}

// Length of the longest prefix of `s` that does not end inside a multi-byte UTF-8 sequence.
size_t complete_utf8_prefix(const std::string & s) {
    const size_t n = s.size();
    // A sequence is at most 4 bytes, so only the last 3 bytes can hold an incomplete lead.
    for (size_t back = 1; back <= std::min<size_t>(3, n); back++) {
        const unsigned char c = (unsigned char) s[n - back];
        if ((c & 0xC0) == 0x80) {
            continue;  // continuation byte, keep looking for its lead
        }
        size_t need = 1;
        if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        return back >= need ? n : n - back;
    }
    return n;
}

std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    std::string piece(64, '\0');
    int32_t n = llama_token_to_piece(vocab, token, piece.data(), (int32_t) piece.size(), 0, false);
    if (n < 0) {
        piece.resize((size_t) -n);
        n = llama_token_to_piece(vocab, token, piece.data(), (int32_t) piece.size(), 0, false);
    }
    piece.resize(n > 0 ? (size_t) n : 0);
    return piece;
}

bool tokenize(const llama_vocab * vocab, const std::string & text, std::vector<llama_token> & out) {
    int32_t n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, true, true);
    if (n <= 0) {
        // Non-negative result of the size probe means an empty or failed tokenization.
        out.clear();
        return n == 0 && text.empty();
    }
    out.resize((size_t) n);
    n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), out.data(), (int32_t) out.size(), true, true);
    if (n < 0) {
        return false;
    }
    out.resize((size_t) n);
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_app_bytebrief_llama_LlamaNative_init(JNIEnv * env, jclass, jstring native_lib_dir) {
    static std::once_flag once;
    const char * dir = env->GetStringUTFChars(native_lib_dir, nullptr);
    std::string dir_copy(dir);
    env->ReleaseStringUTFChars(native_lib_dir, dir);
    std::call_once(once, [&dir_copy]() {
        llama_log_set(android_log_callback, nullptr);
        ggml_backend_load_all_from_path(dir_copy.c_str());
        llama_backend_init();
        LOGI("llama backend initialized from %s", dir_copy.c_str());
    });
}

JNIEXPORT jstring JNICALL
Java_app_bytebrief_llama_LlamaNative_systemInfo(JNIEnv * env, jclass) {
    return env->NewStringUTF(llama_print_system_info());
}

JNIEXPORT jlong JNICALL
Java_app_bytebrief_llama_LlamaNative_loadModel(JNIEnv * env, jclass, jstring jpath, jint gpu_layers) {
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    std::string path_copy(path);
    env->ReleaseStringUTFChars(jpath, path);

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = gpu_layers;
    llama_model * model = llama_model_load_from_file(path_copy.c_str(), params);
    if (model == nullptr) {
        throw_llama(env, kCodeModelLoad,
                    "Failed to load model (file missing, corrupted, unsupported or out of memory)");
        return 0;
    }
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT void JNICALL
Java_app_bytebrief_llama_LlamaNative_freeModel(JNIEnv *, jclass, jlong handle) {
    if (handle != 0) {
        llama_model_free(reinterpret_cast<llama_model *>(handle));
    }
}

JNIEXPORT jlong JNICALL
Java_app_bytebrief_llama_LlamaNative_modelParams(JNIEnv *, jclass, jlong handle) {
    return (jlong) llama_model_n_params(reinterpret_cast<llama_model *>(handle));
}

JNIEXPORT jlong JNICALL
Java_app_bytebrief_llama_LlamaNative_modelSizeBytes(JNIEnv *, jclass, jlong handle) {
    return (jlong) llama_model_size(reinterpret_cast<llama_model *>(handle));
}

JNIEXPORT jint JNICALL
Java_app_bytebrief_llama_LlamaNative_modelContextTrain(JNIEnv *, jclass, jlong handle) {
    return llama_model_n_ctx_train(reinterpret_cast<llama_model *>(handle));
}

JNIEXPORT jbyteArray JNICALL
Java_app_bytebrief_llama_LlamaNative_formatChat(JNIEnv * env, jclass, jlong handle, jobjectArray roles,
                                                jobjectArray contents, jboolean add_assistant) {
    auto * model = reinterpret_cast<llama_model *>(handle);
    const jsize count = env->GetArrayLength(roles);

    std::vector<std::string> role_store((size_t) count);
    std::vector<std::string> content_store((size_t) count);
    std::vector<llama_chat_message> messages((size_t) count);
    size_t total_chars = 0;
    for (jsize i = 0; i < count; i++) {
        auto jrole = (jstring) env->GetObjectArrayElement(roles, i);
        const char * r = env->GetStringUTFChars(jrole, nullptr);
        role_store[i] = r;
        env->ReleaseStringUTFChars(jrole, r);
        env->DeleteLocalRef(jrole);

        auto jcontent = (jbyteArray) env->GetObjectArrayElement(contents, i);
        content_store[i] = to_string(env, jcontent);
        env->DeleteLocalRef(jcontent);

        messages[i] = {role_store[i].c_str(), content_store[i].c_str()};
        total_chars += content_store[i].size() + role_store[i].size();
    }

    const char * tmpl = llama_model_chat_template(model, nullptr);
    std::string buffer(2 * total_chars + 256, '\0');
    int32_t n = llama_chat_apply_template(tmpl != nullptr ? tmpl : "chatml", messages.data(), messages.size(),
                                          add_assistant, buffer.data(), (int32_t) buffer.size());
    if (n < 0) {
        LOGW("Model chat template is not supported by the built-in formatter; falling back to ChatML");
        n = llama_chat_apply_template("chatml", messages.data(), messages.size(), add_assistant,
                                      buffer.data(), (int32_t) buffer.size());
    }
    if (n > (int32_t) buffer.size()) {
        buffer.resize((size_t) n);
        n = llama_chat_apply_template(tmpl != nullptr ? tmpl : "chatml", messages.data(), messages.size(),
                                      add_assistant, buffer.data(), (int32_t) buffer.size());
    }
    if (n < 0) {
        throw_llama(env, kCodeGeneric, "Chat template could not be applied");
        return nullptr;
    }
    buffer.resize((size_t) n);
    return to_bytes(env, buffer);
}

JNIEXPORT jlong JNICALL
Java_app_bytebrief_llama_LlamaNative_createSession(JNIEnv * env, jclass, jlong model_handle, jint context_size,
                                                   jint threads) {
    auto * model = reinterpret_cast<llama_model *>(model_handle);
    auto * session = new Session();
    session->model = model;

    llama_context_params params = llama_context_default_params();
    params.n_ctx = (uint32_t) context_size;
    params.n_batch = kBatchSize;
    params.n_ubatch = kBatchSize;
    params.n_threads = threads;
    params.n_threads_batch = threads;
    params.abort_callback = abort_callback;
    params.abort_callback_data = session;

    session->ctx = llama_init_from_model(model, params);
    if (session->ctx == nullptr) {
        delete session;
        throw_llama(env, kCodeOutOfMemory,
                    "Failed to create inference context (insufficient memory or invalid parameters)");
        return 0;
    }
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_app_bytebrief_llama_LlamaNative_freeSession(JNIEnv *, jclass, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session != nullptr) {
        llama_free(session->ctx);
        delete session;
    }
}

JNIEXPORT void JNICALL
Java_app_bytebrief_llama_LlamaNative_cancel(JNIEnv *, jclass, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session != nullptr) {
        session->cancel.store(true);
    }
}

JNIEXPORT jint JNICALL
Java_app_bytebrief_llama_LlamaNative_countTokens(JNIEnv * env, jclass, jlong model_handle, jbyteArray text) {
    auto * model = reinterpret_cast<llama_model *>(model_handle);
    std::vector<llama_token> tokens;
    if (!tokenize(llama_model_get_vocab(model), to_string(env, text), tokens)) {
        return -1;
    }
    return (jint) tokens.size();
}

JNIEXPORT jint JNICALL
Java_app_bytebrief_llama_LlamaNative_generate(JNIEnv * env, jclass, jlong handle, jbyteArray jprompt,
                                              jint max_tokens, jfloat temperature, jfloat top_p, jint seed,
                                              jbyteArray jgrammar, jobject listener) {
    auto * session = reinterpret_cast<Session *>(handle);
    session->cancel.store(false);

    const llama_vocab * vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> prompt_tokens;
    if (!tokenize(vocab, to_string(env, jprompt), prompt_tokens) || prompt_tokens.empty()) {
        throw_llama(env, kCodeGeneric, "Prompt could not be tokenized");
        return 0;
    }

    const uint32_t n_ctx = llama_n_ctx(session->ctx);
    if (prompt_tokens.size() + (size_t) max_tokens > n_ctx) {
        throw_llama(env, kCodeContextOverflow,
                    "Prompt (" + std::to_string(prompt_tokens.size()) + " tokens) plus requested output ("
                            + std::to_string(max_tokens) + ") exceeds the context window ("
                            + std::to_string(n_ctx) + ")");
        return 0;
    }

    jclass listener_cls = env->GetObjectClass(listener);
    jmethodID on_piece = env->GetMethodID(listener_cls, "onPiece", "([B)Z");

    llama_memory_clear(llama_get_memory(session->ctx), true);

    // Prompt processing in fixed-size batches so that abort checks stay responsive.
    for (size_t i = 0; i < prompt_tokens.size(); i += kBatchSize) {
        const int32_t n = (int32_t) std::min<size_t>(kBatchSize, prompt_tokens.size() - i);
        const int32_t rc = llama_decode(session->ctx, llama_batch_get_one(prompt_tokens.data() + i, n));
        if (rc == 2 || session->cancel.load()) {
            return 0;  // aborted
        }
        if (rc != 0) {
            throw_llama(env, kCodeDecode, "Prompt decoding failed with code " + std::to_string(rc));
            return 0;
        }
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (jgrammar != nullptr) {
        // The grammar goes first so every later sampler only sees tokens that keep the output valid.
        const std::string grammar = to_string(env, jgrammar);
        llama_sampler * g = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (g == nullptr) {
            llama_sampler_free(sampler);
            throw_llama(env, kCodeGeneric, "Grammar could not be parsed");
            return 0;
        }
        llama_sampler_chain_add(sampler, g);
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed < 0 ? LLAMA_DEFAULT_SEED : (uint32_t) seed));
    }

    std::string pending;
    int generated = 0;
    bool keep_going = true;

    auto flush = [&](bool force) {
        const size_t len = force ? pending.size() : complete_utf8_prefix(pending);
        if (len == 0) {
            return;
        }
        jbyteArray chunk = to_bytes(env, pending.substr(0, len));
        pending.erase(0, len);
        const jboolean cont = env->CallBooleanMethod(listener, on_piece, chunk);
        env->DeleteLocalRef(chunk);
        if (env->ExceptionCheck() || !cont) {
            keep_going = false;
        }
    };

    while (keep_going && generated < max_tokens && !session->cancel.load()) {
        llama_token token = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }
        pending += token_to_piece(vocab, token);
        generated++;
        flush(false);
        if (!keep_going) {
            break;
        }

        const int32_t rc = llama_decode(session->ctx, llama_batch_get_one(&token, 1));
        if (rc == 2 || session->cancel.load()) {
            break;
        }
        if (rc != 0) {
            llama_sampler_free(sampler);
            throw_llama(env, kCodeDecode, "Token decoding failed with code " + std::to_string(rc));
            return generated;
        }
    }
    if (!env->ExceptionCheck()) {
        flush(true);
    }

    llama_sampler_free(sampler);
    return generated;
}

}  // extern "C"
