package fi.hsl.gtfsrt2hfp.utils

import java.io.Reader

fun Reader.readString(maxChars: Int): String {
    use {
        val chars = CharArray(maxChars)
        val nChars = read(chars, 0, maxChars)

        return String(chars, 0, nChars)
    }
}