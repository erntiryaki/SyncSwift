package net.syncswift.clipboard

object ContentDetector {
    private val URL_REGEX = Regex("""^(https?://[^\s/$.?#].[^\s]*)$""", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex("""^(\+?\d{1,4}[-.\s]?)?(\(?\d{3}\)?[-.\s]?)?[\d\s-]{7,14}$""")
    private val HEX_COLOR_REGEX = Regex("""^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")

    fun detect(rawText: String): Pair<ClipType, SmartAction?> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return ClipType.TEXT to null

        // 1. URL Detection
        if (URL_REGEX.matches(trimmed) || ((trimmed.startsWith("http://") || trimmed.startsWith("https://")) && !trimmed.contains("\n"))) {
            return ClipType.URL to SmartAction.OpenUrl(trimmed)
        }

        // 2. Color Hex Detection
        val hexMatch = HEX_COLOR_REGEX.matchEntire(trimmed)
        if (hexMatch != null) {
            val rawHex = hexMatch.groupValues[1]
            val color = try {
                when (rawHex.length) {
                    3 -> {
                        val r = rawHex[0].toString().repeat(2).toInt(16)
                        val g = rawHex[1].toString().repeat(2).toInt(16)
                        val b = rawHex[2].toString().repeat(2).toInt(16)
                        ColorInfo("#$rawHex", r, g, b)
                    }
                    6, 8 -> {
                        val r = rawHex.substring(0, 2).toInt(16)
                        val g = rawHex.substring(2, 4).toInt(16)
                        val b = rawHex.substring(4, 6).toInt(16)
                        ColorInfo("#${rawHex.take(6)}", r, g, b)
                    }
                    else -> null
                }
            } catch (_: Exception) { null }

            if (color != null) {
                return ClipType.COLOR to SmartAction.ColorAction(color)
            }
        }

        // 3. Phone Number Detection
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length in 10..15 && PHONE_REGEX.matches(trimmed) && !trimmed.contains("\n")) {
            return ClipType.PHONE to SmartAction.PhoneAction(digitsOnly, trimmed)
        }

        // 4. Code / Terminal Detection
        val lines = trimmed.lines()
        val isCode = lines.size >= 2 && (
            trimmed.contains("fun ") || trimmed.contains("val ") || trimmed.contains("var ") ||
            trimmed.contains("class ") || trimmed.contains("function ") || trimmed.contains("const ") ||
            trimmed.contains("import ") || trimmed.contains("public ") || trimmed.contains("def ") ||
            (trimmed.contains("{") && trimmed.contains("}")) || trimmed.contains("git ") || trimmed.contains("npm ")
        )
        if (isCode) {
            return ClipType.CODE to SmartAction.CodeAction(trimmed.take(300), lines.size)
        }

        return ClipType.TEXT to null
    }
}
