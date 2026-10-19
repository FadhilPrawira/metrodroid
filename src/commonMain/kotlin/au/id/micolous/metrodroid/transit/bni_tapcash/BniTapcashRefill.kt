package au.id.micolous.metrodroid.transit.bni_tapcash

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

/**
 * A top-up of a BNI TapCash card, shown as a trip with a negative fare.
 */
@Parcelize
class BniTapcashRefill(
    override val startTimestamp: Timestamp?,
    private val amount: Int,
    private val type: Int,
    private val userData: String
) : Trip() {

    override val fare: TransitCurrency
        get() = TransitCurrency.IDR(-amount)

    override val mode: Mode
        get() = Mode.TICKET_MACHINE

    override fun getRawFields(level: TransitData.RawLevel): String? =
        if (level == TransitData.RawLevel.ALL) "type=$type userData=$userData" else null
}