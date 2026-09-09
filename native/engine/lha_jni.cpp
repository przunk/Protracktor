// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// JNI for the LHA archive reader. Three functions and no state.
//
// **The whole native footprint of the UnExoticA catalogue.** It is a separate file from
// `player_oboe.cpp` for exactly that reason: `docs/PLAN_UNEXOTICA.md` promises the feature can be
// taken out again if ExoticA say no, and a promise like that is only as good as the number of
// places the code touches. Deleting this file, `native/lhasa/lha_extract.*`, one Kotlin file and
// two lines elsewhere removes it completely -- and the `lhasa` target stays, because the `.ym`
// decoder needs it for a different reason (`docs/PLAN_FORMATS.md` §8).

#include "lha_extract.h"

#include <jni.h>

#include <string>
#include <vector>

namespace {

std::string toString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_com_przunk_protracktor_net_Lha_nativeList(JNIEnv *env, jclass, jbyteArray archive) {
    const jsize length = env->GetArrayLength(archive);
    std::vector<char> bytes(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(archive, 0, length, reinterpret_cast<jbyte *>(bytes.data()));

    const auto names = protracktor::lhaList(bytes.data(), bytes.size());

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result =
        env->NewObjectArray(static_cast<jsize>(names.size()), stringClass, nullptr);
    for (std::size_t i = 0; i < names.size(); ++i) {
        jstring name = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), name);
        // Released as we go. A big archive is a few hundred members and the default local frame
        // holds sixteen references; leaving them all live is a warning in logcat at best.
        env->DeleteLocalRef(name);
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_przunk_protracktor_net_Lha_nativeExtract(JNIEnv *env, jclass, jbyteArray archive,
                                                  jstring member) {
    const jsize length = env->GetArrayLength(archive);
    std::vector<char> bytes(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(archive, 0, length, reinterpret_cast<jbyte *>(bytes.data()));

    const auto found = protracktor::lhaExtract(bytes.data(), bytes.size(), toString(env, member));
    // Null rather than an empty array: "no such member" and "a member of zero bytes" are different
    // answers and the caller acts differently on them.
    if (found.empty()) return nullptr;

    jbyteArray result = env->NewByteArray(static_cast<jsize>(found.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(found.size()),
                            reinterpret_cast<const jbyte *>(found.data()));
    return result;
}

}  // extern "C"
