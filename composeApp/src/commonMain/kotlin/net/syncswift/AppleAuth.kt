package net.syncswift

import androidx.compose.runtime.Composable

data class AppleUserData(
    val idToken: String,
    val email: String,
    val userIdentifier: String = "",
    val givenName: String? = null,
    val familyName: String? = null
)

@Composable
expect fun rememberAppleAuthLauncher(
    onSuccess: (userData: AppleUserData) -> Unit,
    onError: (message: String) -> Unit
): () -> Unit

object SmartNameExtractor {
    fun normalizeAscii(str: String): String {
        return str.lowercase()
            .replace("ç", "c")
            .replace("ğ", "g")
            .replace("ı", "i")
            .replace("ö", "o")
            .replace("ş", "s")
            .replace("ü", "u")
            .replace("İ", "i")
    }

    fun prettifyFirstName(raw: String): String {
        val lower = raw.lowercase().trim()
        return when (lower) {
            "mirac" -> "Miraç"
            "baris" -> "Barış"
            "cagri" -> "Çağrı"
            "yagiz" -> "Yağız"
            "yagmur" -> "Yağmur"
            "bugra" -> "Buğra"
            "sukru" -> "Şükrü"
            "sukran" -> "Şükran"
            "ozgur" -> "Özgür"
            "ozan" -> "Ozan"
            "ismail" -> "İsmail"
            "ilker" -> "İlker"
            "irem" -> "İrem"
            "ipek" -> "İpek"
            "inanc" -> "İnanç"
            "gokhan" -> "Gökhan"
            "gorkem" -> "Görkem"
            "canberk" -> "Canberk"
            "eren" -> "Eren"
            "mert" -> "Mert"
            "burak" -> "Burak"
            "ahmet" -> "Ahmet"
            "mehmet" -> "Mehmet"
            "ali" -> "Ali"
            "veli" -> "Veli"
            "can" -> "Can"
            "cem" -> "Cem"
            "kaan" -> "Kaan"
            "emre" -> "Emre"
            "onur" -> "Onur"
            "tolga" -> "Tolga"
            else -> lower.replaceFirstChar { it.uppercase() }
        }
    }

    fun prettifySurname(raw: String): String {
        val lower = raw.lowercase().trim()
        return when (lower) {
            "tiryaki" -> "Tiryaki"
            "yilmaz" -> "Yılmaz"
            "celik" -> "Çelik"
            "ozdemir" -> "Özdemir"
            "kilic" -> "Kılıç"
            "sahin" -> "Şahin"
            "ozturk" -> "Öztürk"
            "yildirim" -> "Yıldırım"
            "yildiz" -> "Yıldız"
            "simsek" -> "Şimşek"
            "aydin" -> "Aydın"
            "ozcan" -> "Özcan"
            "ozkan" -> "Özkan"
            "erdogan" -> "Erdoğan"
            "colak" -> "Çolak"
            "avci" -> "Avcı"
            "akgul" -> "Akgül"
            "bozkurt" -> "Bozkurt"
            "demir" -> "Demir"
            "kaya" -> "Kaya"
            "arslan" -> "Arslan"
            "dogan" -> "Doğan"
            "aslan" -> "Aslan"
            "cetin" -> "Çetin"
            "kara" -> "Kara"
            "koc" -> "Koç"
            "kurt" -> "Kurt"
            "polat" -> "Polat"
            "korkmaz" -> "Korkmaz"
            "yavuz" -> "Yavuz"
            "aksoy" -> "Aksoy"
            "duran" -> "Duran"
            "keskin" -> "Keskin"
            else -> lower.replaceFirstChar { it.uppercase() }
        }
    }

    fun extract(
        email: String,
        hintFirstName: String = "",
        hintLastName: String = ""
    ): Pair<String, String> {
        var first = hintFirstName.trim()
        var last = hintLastName.trim()

        // Clean out invalid hints that look like email handles with digits
        if (first.any { it.isDigit() } || first.contains("@")) first = ""
        if (last.any { it.isDigit() } || last.contains("@")) last = ""

        if (first.isNotBlank() && last.isNotBlank()) {
            return Pair(first, last)
        }

        val rawEmail = email.trim().lowercase()
        val prefix = if (rawEmail.contains("@") && !rawEmail.contains("@privaterelay") && !rawEmail.endsWith("@apple.id")) {
            rawEmail.substringBefore("@")
        } else {
            ""
        }
        val cleanPrefix = prefix.replace(Regex("""\d+"""), "").trim()

        // If hintFirstName is valid but last is missing, try to resolve last name from email prefix
        if (first.isNotBlank() && last.isBlank() && cleanPrefix.isNotBlank()) {
            val normFirst = normalizeAscii(first)
            if (cleanPrefix.startsWith(normFirst) && cleanPrefix.length > normFirst.length) {
                val rem = cleanPrefix.substring(normFirst.length)
                if (rem.length >= 2) {
                    last = prettifySurname(rem)
                    return Pair(first, last)
                }
            }
        }

        // Split by punctuation delimiters like '.', '_', '-', '+'
        if (cleanPrefix.isNotBlank()) {
            val parts = cleanPrefix.split(".", "_", "-", "+").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val f = if (first.isNotBlank()) first else prettifyFirstName(parts.first())
                val l = if (last.isNotBlank()) last else parts.drop(1).joinToString(" ") { prettifySurname(it) }
                return Pair(f, l)
            }
        }

        // Check compound known surnames
        val knownSurnames = listOf(
            "tiryaki", "yilmaz", "demir", "kaya", "celik", "aydin", "ozdemir",
            "arslan", "dogan", "kilic", "aslan", "cetin", "kara", "koc",
            "kurt", "ozkan", "simsek", "polat", "korkmaz", "yildirim", "yildiz",
            "sahin", "ozturk", "erdogan", "yavuz", "colak", "aksoy", "duran",
            "keskin", "tekir", "ozcan", "sarikaya", "avci", "bozkurt", "akgul",
            "peters", "smith", "johnson", "brown", "miller", "davis", "wilson"
        )
        if (cleanPrefix.isNotBlank()) {
            for (s in knownSurnames) {
                if (cleanPrefix.endsWith(s) && cleanPrefix.length > s.length) {
                    val rawFirst = cleanPrefix.substring(0, cleanPrefix.length - s.length)
                    val f = if (first.isNotBlank()) first else prettifyFirstName(rawFirst)
                    val l = prettifySurname(s)
                    return Pair(f, l)
                }
            }
        }

        if (first.isBlank() && cleanPrefix.isNotBlank()) {
            first = prettifyFirstName(cleanPrefix)
        }

        return Pair(first, last)
    }
}

