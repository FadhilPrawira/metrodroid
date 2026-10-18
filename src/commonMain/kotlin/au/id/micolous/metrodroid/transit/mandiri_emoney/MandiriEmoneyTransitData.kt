package au.id.micolous.metrodroid.transit.mandiri_emoney

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.mandiri_emoney.MandiriEmoneyApplication
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.CardTransitFactory
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.TransitRegion

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
        val CARD_INFO = CardInfo(
            name = R.string.card_name_mandiri_emoney,
            locationId = R.string.location_indonesia,
            cardType = CardType.ISO7816,
            region = TransitRegion.INDONESIA,
            // Only the card number and balance are decoded; no transaction history yet.
            preview = true,
            // The AID is not registered in the iOS Info.plist.
            iOSSupported = false
        )

        val FACTORY: CardTransitFactory<MandiriEmoneyApplication> =
            object : CardTransitFactory<MandiriEmoneyApplication> {
                override val allCards = listOf(CARD_INFO)

                override fun parseTransitIdentity(card: MandiriEmoneyApplication) =
                    TransitIdentity(
                        Localizer.localizeString(R.string.card_name_mandiri_emoney),
                        card.serial
                    )

                override fun parseTransitData(card: MandiriEmoneyApplication) =
                    MandiriEmoneyTransitData(card)

                override fun check(card: MandiriEmoneyApplication) = true
            }
    }
}