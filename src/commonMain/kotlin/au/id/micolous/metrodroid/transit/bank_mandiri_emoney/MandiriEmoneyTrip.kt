package au.id.micolous.metrodroid.transit.bank_mandiri_emoney

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

@Parcelize
class MandiriEmoneyTrip(
    override val startTimestamp: TimestampFull?,
    private val amount: Int,
    private val balanceAfter: Int,
    private val type: Int,
    private val unknown: String
) : Trip() {
    override val fare: TransitCurrency
        get() = TransitCurrency.IDR(amount)

    // The merchant may be a toll road, public transport, a minimarket EDC terminal
    // or a parking operator, so the mode cannot be determined.
    override val mode: Mode
        get() = Mode.OTHER

    override fun getRawFields(level: TransitData.RawLevel): String? =
        if (level == TransitData.RawLevel.ALL)
            "balance=$balanceAfter type=0x${type.toString(16).padStart(4, '0')} unknown=$unknown"
        else null
}
