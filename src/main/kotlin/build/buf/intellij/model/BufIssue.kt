package build.buf.intellij.model

import com.google.gson.Gson

data class BufIssue(
    val path: String,
    val start_line: Int,
    val end_line: Int,
    val start_column: Int,
    val end_column: Int,
    val type: String,
    val message: String,
) {
    val isCompileError: Boolean
        get() = type == "COMPILE"

    companion object {
        fun fromJSON(text: String): BufIssue? {
            return try {
                Gson().fromJson(text, BufIssue::class.java)
            } catch (th: Throwable) {
                null
            }
        }
    }
}
