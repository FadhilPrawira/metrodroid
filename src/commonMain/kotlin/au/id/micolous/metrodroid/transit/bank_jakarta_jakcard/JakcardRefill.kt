package au.id.micolous.metrodroid.transit.bank_jakarta_jakcard

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

/**
 * A top-up of a JakCard, shown as a trip with a negative fare.
 */
@Parcelize
class JakcardRefill(
    override val startTimestamp: Timestamp?,
    private val amount: Int,
    private val balanceAfter: Int,
    private val sequence: Long,
    private val terminal: String,
    private val counter: Long
) : Trip() {

    override val fare: TransitCurrency
        get() = TransitCurrency.IDR(-amount)

    override val mode: Mode
        get() = Mode.TICKET_MACHINE

    override val machineID: String?
        get() = terminal

    override fun getRawFields(level: TransitData.RawLevel): String? =
        if (level == TransitData.RawLevel.ALL) "balance=$balanceAfter sequence=$sequence counter=$counter" else null
}
