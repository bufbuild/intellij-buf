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
    return (officialProtobufFile?.isInstance(this) ?: false) || (kanroProtobufFile?.isInstance(this) ?: false)
}

fun protobufLanguage(): Language? {
    return officialProtobufLanguage ?: kanroProtobufLanguage
}