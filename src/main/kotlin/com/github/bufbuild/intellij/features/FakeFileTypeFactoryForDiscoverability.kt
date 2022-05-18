package com.github.bufbuild.intellij.features

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.protobuf.lang.PbFileType

/**
 * Trick FileTypeFactoryExtractor into suggesting our plugin without a conflict
 *
 * @see https://github.com/JetBrains/intellij-plugin-verifier/blob/master/intellij-feature-extractor/src/main/java/com/jetbrains/intellij/feature/extractor/extractor/FileTypeFactoryExtractor.kt
 *
 */
class FakeFileTypeFactoryForDiscoverability : FileTypeFactory() {
    override fun createFileTypes(consumer: FileTypeConsumer) {
        // make sure it's not affecting production
        if (System.currentTimeMillis() > 0) {
            return
        }
        consumer.consume(PbFileType.INSTANCE, "proto")
    }
}
