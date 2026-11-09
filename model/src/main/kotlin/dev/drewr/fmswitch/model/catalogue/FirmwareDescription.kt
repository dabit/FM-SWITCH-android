// Ported from FM-SWITCH App/Catalogue/FirmwareDescription.swift.
package dev.drewr.fmswitch.model.catalogue

object FirmwareDescription {
    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
    )

    fun runs(text: String): List<Run> {
        val result = mutableListOf<Run>()
        var plain = StringBuilder()

        fun addPlain() {
            if (plain.isNotEmpty()) {
                result += Run(plain.toString())
                plain = StringBuilder()
            }
        }

        fun addStyled(
            value: String,
            bold: Boolean = false,
            italic: Boolean = false,
            code: Boolean = false,
        ) {
            addPlain()
            result += Run(value, bold, italic, code)
        }

        var index = 0
        while (index < text.length) {
            val marker =
                when {
                    text.startsWith("**", index) -> "**"
                    text.startsWith("__", index) -> "__"
                    text[index] == '*' -> "*"
                    text[index] == '_' -> "_"
                    text[index] == '`' -> "`"
                    text[index] == '[' -> "["
                    else -> null
                }
            if (marker == null) {
                plain.append(text[index++])
                continue
            }
            if (marker == "[") {
                val closeText = text.indexOf(']', index + 1)
                val openUrl = closeText.takeIf { it >= 0 }?.plus(1)
                val closeUrl = openUrl?.takeIf { text.startsWith("(", it) }?.let { text.indexOf(')', it + 1) }
                if (closeText >= 0 && closeUrl != null && closeUrl >= 0) {
                    addStyled(text.substring(index + 1, closeText))
                    index = closeUrl + 1
                } else {
                    plain.append(text[index++])
                }
                continue
            }
            val close = text.indexOf(marker, index + marker.length)
            if (close < 0) {
                plain.append(text[index++])
                continue
            }
            val value = text.substring(index + marker.length, close)
            when (marker) {
                "**", "__" -> addStyled(value, bold = true)
                "*", "_" -> addStyled(value, italic = true)
                else -> addStyled(value, code = true)
            }
            index = close + marker.length
        }
        addPlain()
        return result
    }

    fun plain(text: String) = runs(text).joinToString(separator = "") { it.text }
}
