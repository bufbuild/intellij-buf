package com.github.bufbuild.intellij.model

import com.google.gson.Gson

data class BufLintIssue(
    val path: String,
    val start_line: Int,
    val end_line: Int,
    val start_column: Int,
    val end_column: Int,
    val type: String,
    val message: String,
) {
    companion object {
        fun fromJSON(text: String): BufLintIssue? {
            return try {
                Gson().fromJson(text, BufLintIssue::class.java)
            } catch (th: Throwable) {
                null
            }
        }
    }
}
