// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/** The digit that closes a code of the EAN and UPC family, for the digits before it. */
private fun closingDigit(digits: String): Int {
    val sum = digits.reversed().withIndex().sumOf { (place, digit) -> (digit - '0') * if (place % 2 == 0) 3 else 1 }
    return (10 - sum % 10) % 10
}

/**
 * A code as Open Food Facts keeps it, from the way it was read or typed, as the API reads it: EAN-8 and
 * EAN-13 as they are, UPC-A with the zero that makes it an EAN-13, and an EAN-8 written out to 13 or a
 * code written out to 14 digits without what pads them. Null for anything else, and for a code whose
 * last digit does not close it, which is what a slip of a finger or a bad read gives.
 */
fun productCodeOf(typed: String): String? {
    var code = typed.filterNot { it.isWhitespace() || it == '-' }
    if (code.isEmpty() || code.any { it !in '0'..'9' }) return null
    if (code.length == 14 && code.startsWith('0')) code = code.drop(1)
    if (code.length == 12) code = "0$code"
    if (code.length == 13 && code.startsWith("00000")) code = code.drop(5)
    if (code.length != 8 && code.length != 13) return null
    return code.takeIf { closingDigit(it.dropLast(1)) == it.last() - '0' }
}

/** What a person types when they type a code: digits, with whatever they read between them. */
fun looksLikeProductCode(typed: String): Boolean = typed.isNotBlank() && typed.all { it in '0'..'9' || it.isWhitespace() || it == '-' }

private val WORD = Regex("[\\p{L}\\p{N}]+")
private val WRITTEN_IN_FEW_SIGNS = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]+")

/** Whether a search of products can look for what is typed, as the API reads it: a word of three signs, of two in a script that writes words that short. */
fun canSearchProducts(typed: String): Boolean = WORD.findAll(typed).any { word ->
    val signs = word.value.codePointCount(0, word.value.length)
    signs >= 3 || (signs >= 2 && WRITTEN_IN_FEW_SIGNS.matches(word.value))
}
