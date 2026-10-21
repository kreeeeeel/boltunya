package ru.boltunya.ai.research

/** Prevents accidental disclosure of contact details in a public search query. */
class ResearchPrivacy {
    fun containsContactDetails(query: String): Boolean {
        val email = query.split(' ', '\n', '\t').any { word ->
            val at = word.indexOf('@')
            at > 0 && at < word.lastIndex && word.substring(at + 1).contains('.')
        }
        if (email) return true
        val phone = StringBuilder(query.length)
        query.chars().forEach { code ->
            val character = code.toChar()
            phone.append(if (character.isDigit() || character in " +()-") character else '|')
        }
        return phone.toString().split('|').any { it.count(Char::isDigit) in 10..15 }
    }
}
