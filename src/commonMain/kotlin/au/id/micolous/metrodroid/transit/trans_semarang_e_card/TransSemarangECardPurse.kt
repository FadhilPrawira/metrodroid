package au.id.micolous.metrodroid.transit.trans_semarang_e_card

import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.multi.Parcelable
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.transit.TransitCurrency

@Parcelize
class TransSemarangECardPurse(
    val value: Int
) : Parcelable {
    val balance: TransitCurrency
        get() = TransitCurrency.IDR(value)

    companion object {
        internal fun parse(card: ClassicCard): TransSemarangECardPurse? {
            val block = card[2, 1].data
            if (!TransSemarangECardUtils.isValidValueBlock(block)) return null
            return TransSemarangECardPurse(block.byteArrayToIntReversed(0, 4))
        }
    }
}