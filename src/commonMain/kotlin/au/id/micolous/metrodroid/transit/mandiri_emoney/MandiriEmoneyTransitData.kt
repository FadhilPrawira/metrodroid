package au.id.micolous.metrodroid.transit.mandiri_emoney

import au.id.micolous.metrodroid.card.mandiri_emoney.MandiriEmoneyApplication
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.multi.R

@Parcelize
class MandiriEmoneyTransitData(
    override val serialNumber: String?,
    private val mBalance: Int
) : TransitData() {

    public override val balance: TransitCurrency
        get() = TransitCurrency.IDR(mBalance)

    override val cardName: String
        get() = Localizer.localizeString(R.string.card_name_mandiri_emoney)

    constructor(app: MandiriEmoneyApplication) : this(
        serialNumber = app.serial,
        // Little-endian, already in Rupiah (no scaling).
        mBalance = app.balance.byteArrayToIntReversed(0, 4)
    )

    companion object {
        val NAME = R.string.card_name_mandiri_emoney
    }
}