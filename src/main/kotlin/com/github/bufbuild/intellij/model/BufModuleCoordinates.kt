package com.github.bufbuild.intellij.model

data class BufModuleCoordinates(
    val remote: String,
    val owner: String,
    val repository: String,
    val commit: String,
)
