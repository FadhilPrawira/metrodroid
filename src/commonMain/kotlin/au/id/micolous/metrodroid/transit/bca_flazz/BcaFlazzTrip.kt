package au.id.micolous.metrodroid.transit.bca_flazz

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.Trip

/**
 * A payment made with a BCA Flazz card.
 *
 * The card does not say whether a payment was a shop purchase, parking or a toll, so the mode
 * is [Trip.Mode.OTHER].
 */
@Parcelize
class BcaFlazzTrip(
    override val startTimestamp: Timestamp?,
    private val amount: Int,
    // Null when the source of the record does not tell the balance after the payment.
    private val balanceAfter: Int?,
    private val type: Int,
    // ID of the terminal that took the payment.
    override val machineID: String?,
    private val unknown: String
) : Trip() {

    override val fare: TransitCurrency
        get() = TransitCurrency.IDR(amount)

    override val mode: Mode
        get() = Mode.OTHER

    override fun getRawFields(level: TransitData.RawLevel): String? =
        if (level == TransitData.RawLevel.ALL)
            listOfNotNull(
                balanceAfter?.let { "balance=$it" },
                "type=0x${type.toString(16).padStart(2, '0')}",
                "unknown=$unknown"
            ).joinToString(" ")
        else null
}