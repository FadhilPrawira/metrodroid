package au.id.micolous.metrodroid.transit.tapcash

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.transit.TransitBalance
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData

@Parcelize
class TapCashTransitData(
    private val mBalance: Int,
    private val mSerialNumber: String
) : TransitData() {

    override val cardName: String
        get() = "BNI TapCash"

    override val serialNumber: String?
        get() = mSerialNumber

    override val balances: List<TransitBalance>?
        get() = listOf(
            TransitCurrency.IDR(mBalance)
        )
}