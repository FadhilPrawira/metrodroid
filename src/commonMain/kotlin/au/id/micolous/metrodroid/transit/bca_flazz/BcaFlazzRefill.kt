package au.id.micolous.metrodroid.transit.bca_flazz

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

/**
 * A top-up of a BCA Flazz card, shown as a trip with a negative fare.
 */
@Parcelize
class BcaFlazzRefill(
    override val startTimestamp: Timestamp?,
    private val amount: Int,
    private val balanceAfter: Int,
    private val type: Int,
    // ID of the terminal that made the top-up.
    override val machineID: String?,
    private val unknown: String
) : Trip() {

    override val fare: TransitCurrency
        get() = TransitCurrency.IDR(-amount)

    override val mode: Mode
        get() = Mode.TICKET_MACHINE

    override fun getRawFields(level: TransitData.RawLevel): String? =
        if (level == TransitData.RawLevel.ALL)
            "balance=$balanceAfter type=0x${type.toString(16).padStart(2, '0')} unknown=$unknown"
        else null
}