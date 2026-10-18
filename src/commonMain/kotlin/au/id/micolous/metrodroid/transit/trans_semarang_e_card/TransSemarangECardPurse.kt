package au.id.micolous.metrodroid.transit.trans_semarang_e_card

import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.multi.Parcelable
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.transit.TransitCurrency

@Parcelize
class TransSemarangECardPurse(
    val value: Int
) : Parcelable {
    // Already in Rupiah (no scaling).
    val balance: TransitCurrency
        get() = TransitCurrency.IDR(value)

    companion object {
        // Sector holding the balance as a MIFARE value block.
        internal const val BALANCE_SECTOR = 2

        // Block (within the sector) holding the balance.
        internal const val BALANCE_BLOCK = 1

        // Offset of the little-endian balance inside the value block.
        private const val BALANCE_OFFSET = 0

        // Length of the balance in bytes.
        private const val BALANCE_LEN = 4

        internal fun parse(card: ClassicCard): TransSemarangECardPurse? {
            val block = card[BALANCE_SECTOR, BALANCE_BLOCK].data
            if (!TransSemarangECardUtils.isValidValueBlock(block)) return null
            return TransSemarangECardPurse(
                block.byteArrayToIntReversed(BALANCE_OFFSET, BALANCE_LEN)
            )
        }
    }
}