package au.id.micolous.metrodroid.transit.tapcash

import au.id.micolous.metrodroid.util.ImmutableByteArray

/**
 * Parsed TapCash purse data.
 *
 * Structure is taken from the response to:
 *
 *     90 32 03 00 00
 *
 * The status word (90 00) must already have been removed before parsing.
 */
data class TapCashPurse(
    val version: Int,
    val status: Int,
    val balance: Int,
    val autoLoadAmount: Int,
    val can: String,
    val csn: String,
    val expiryDays: Int,
    val creationDays: Int,
    val lastCreditTransactionTrp: Long,
    val creditHeader: String,
    val logFileCount: Int,
    val issuerDataLength: Int,
    val lastTransactionTrp: Long
) {
    companion object {
        private const val MIN_LENGTH = 46

        fun parse(data: ImmutableByteArray): TapCashPurse? {
            if (data.size < MIN_LENGTH) {
                return null
            }

            return TapCashPurse(
                version = u8(data[0]),
                status = u8(data[1]),

                // Offset 2..4: signed big-endian 24-bit
                balance = signed24(
                    data[2],
                    data[3],
                    data[4]
                ),

                // Offset 5..7: signed big-endian 24-bit
                autoLoadAmount = signed24(
                    data[5],
                    data[6],
                    data[7]
                ),

                // Offset 8..15
                can = hex(data, 8, 8),

                // Offset 16..23
                csn = hex(data, 16, 8),

                // Offset 24..25
                expiryDays = unsigned16(
                    data[24],
                    data[25]
                ),

                // Offset 26..27
                creationDays = unsigned16(
                    data[26],
                    data[27]
                ),

                // Offset 28..31
                lastCreditTransactionTrp = unsigned32(
                    data[28],
                    data[29],
                    data[30],
                    data[31]
                ),

                // Offset 32..39
                creditHeader = hex(data, 32, 8),

                // Offset 40
                logFileCount = u8(data[40]),

                // Offset 41
                issuerDataLength = u8(data[41]),

                // Offset 42..45
                lastTransactionTrp = unsigned32(
                    data[42],
                    data[43],
                    data[44],
                    data[45]
                )
            )
        }

        private fun u8(value: Byte): Int =
            value.toInt() and 0xff

        private fun unsigned16(
            b0: Byte,
            b1: Byte
        ): Int =
            (u8(b0) shl 8) or
                    u8(b1)

        private fun signed24(
            b0: Byte,
            b1: Byte,
            b2: Byte
        ): Int {
            var value =
                (u8(b0) shl 16) or
                        (u8(b1) shl 8) or
                        u8(b2)

            // Sign-extend 24-bit value to Int.
            if ((value and 0x800000) != 0) {
                value = value or -0x1000000
            }

            return value
        }

        private fun unsigned32(
            b0: Byte,
            b1: Byte,
            b2: Byte,
            b3: Byte
        ): Long =
            (u8(b0).toLong() shl 24) or
                    (u8(b1).toLong() shl 16) or
                    (u8(b2).toLong() shl 8) or
                    u8(b3).toLong()

        private fun hex(
            data: ImmutableByteArray,
            offset: Int,
            length: Int
        ): String {
            val out = StringBuilder(length * 2)

            for (i in offset until offset + length) {
                out.append(
                    (data[i].toInt() and 0xff)
                        .toString(16)
                        .padStart(2, '0')
                        .uppercase()
                )
            }

            return out.toString()
        }
    }
}