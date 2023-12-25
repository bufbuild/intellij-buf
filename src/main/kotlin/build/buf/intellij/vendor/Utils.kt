// Copyright 2022-2023 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buf.intellij.vendor

import com.intellij.lang.Language
import com.intellij.psi.PsiFile

private val officialProtobufFile by lazy {
    try {
        Class.forName("com.intellij.protobuf.lang.psi.PbFile")
    } catch (e: ClassNotFoundException) {
        null
    }
}

private val officialProtobufLanguage: Language? by lazy {
    try {
        val clazz = Class.forName("com.intellij.protobuf.lang.PbLanguage")
        clazz.getField("INSTANCE").get(null) as Language
    } catch (e: ClassNotFoundException) {
        null
    }
}

private val kanroProtobufFile by lazy {
    try {
        Class.forName("io.kanro.idea.plugin.protobuf.lang.psi.ProtobufFile")
    } catch (e: ClassNotFoundException) {
        null
    }
}

private val kanroProtobufLanguage: Language? by lazy {
    try {
        val clazz = Class.forName("io.kanro.idea.plugin.protobuf.lang.ProtobufLanguage")
        clazz.kotlin.objectInstance as Language
    } catch (e: ClassNotFoundException) {
        null
    }
}

fun PsiFile.isProtobufFile(): Boolean {
    return (kanroProtobufFile?.isInstance(this) ?: false) || (officialProtobufFile?.isInstance(this) ?: false)
}

fun protobufLanguage(): Language? {
    return kanroProtobufLanguage ?: officialProtobufLanguage
}