package au.id.micolous.metrodroid.transit.mandiri_emoney

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

@Parcelize
class MandiriEmoneyRefill(
    override val startTimestamp: TimestampFull?,
    private val amount: Int,
    private val balanceAfter: Int,
    private val type: Int,
    private val unknown: String
) : Trip() {
    // Top-ups are modelled as trips with a negative fare (see ClipperRefill).
    override val fare: TransitCurrency
        get() = TransitCurrency.IDR(-amount)

    override val mode: Mode
        get() = Mode.TICKET_MACHINE

    override fun getRawFields(level: TransitData.RawLevel): String? =
        if (level == TransitData.RawLevel.ALL)
            "balance=$balanceAfter type=0x${type.toString(16).padStart(4, '0')} unknown=$unknown"
        else null
}