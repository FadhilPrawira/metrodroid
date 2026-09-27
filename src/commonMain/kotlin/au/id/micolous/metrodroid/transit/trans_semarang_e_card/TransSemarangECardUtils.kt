package au.id.micolous.metrodroid.transit.trans_semarang_e_card

import au.id.micolous.metrodroid.util.ImmutableByteArray

object TransSemarangECardUtils {
    /**
     * Standard MIFARE Classic "value block" format:
     * bytes 0-3: value (LE), 4-7: ~value, 8-11: value again, 12-15: addr/~addr/addr/~addr
     */
    fun isValidValueBlock(data: ImmutableByteArray): Boolean {
        if (data.size != 16) return false
        for (i in 0..3) {
            val a = data[i].toInt() and 0xFF
            val b = data[i + 4].toInt() and 0xFF
            val c = data[i + 8].toInt() and 0xFF
            if (a != c || (a xor b) != 0xFF) return false
        }
        return true
    }

    /** Extracts printable ASCII from a raw block, trimming padding bytes. */
    fun parseAscii(data: ImmutableByteArray): String {
        val sb = StringBuilder()
        for (i in 0 until data.size) {
            val b = data[i].toInt() and 0xFF
            if (b in 0x20..0x7E) sb.append(b.toChar())
        }
        return sb.toString().trim()
    }
}