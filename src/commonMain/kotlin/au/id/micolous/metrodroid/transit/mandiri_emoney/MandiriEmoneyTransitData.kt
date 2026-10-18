package au.id.micolous.metrodroid.transit.mandiri_emoney

import au.id.micolous.metrodroid.card.mandiri_emoney.MandiriEmoneyApplication
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData

@Parcelize
class MandiriEmoneyTransitData(
    override val serialNumber: String?,
    private val mBalance: Int
) : TransitData() {

    public override val balance: TransitCurrency?
        get() = TransitCurrency.IDR(mBalance)

    override val cardName: String
        get() = NAME

    constructor(app: MandiriEmoneyApplication) : this(
        serialNumber = app.serial,
        // Little-endian, already in Rupiah (no scaling).
        mBalance = app.balance.byteArrayToIntReversed(0, 4)
    )

    companion object {
        // TODO: replace with a localized string once the name is decided.
        const val NAME = "e-Money Mandiri"
    }
}