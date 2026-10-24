package au.id.micolous.metrodroid.transit.bank_jakarta_jakcard

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

/**
 * A payment made with a JakCard.
 *
 * The card does not say whether a payment was for a train, a bus or a shop, so the mode
 * is [Trip.Mode.OTHER].
 */
@Parcelize
class JakcardTrip(
    override val startTimestamp: Timestamp?,
    private val amount: Int,
    private val balanceAfter: Int,
    private val sequence: Long,
    private val terminal: String,
    private val counter: Long
) : Trip() {

    override val fare: TransitCurrency
        get() = TransitCurrency.IDR(amount)

    override val mode: Mode
        get() = Mode.OTHER

    override val machineID: String?
        get() = terminal

    override fun getRawFields(level: TransitData.RawLevel): String? =
        if (level == TransitData.RawLevel.ALL) "balance=$balanceAfter sequence=$sequence counter=$counter" else null
}
