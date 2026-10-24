package au.id.micolous.metrodroid.transit.bsb_cash

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.bsb_cash.BsbCashApplication
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.CardTransitFactory
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.TransitRegion
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.util.ImmutableByteArray

@Parcelize
class BsbCashTransitData(
    // Raw purse data, parsed by BsbCashPurse.
    private val mPurse: ImmutableByteArray
) : TransitData() {

    private val purse: BsbCashPurse
        get() = BsbCashPurse(mPurse)

    override val serialNumber: String?
        get() = purse.serial

    public override val balance: TransitCurrency
        get() = TransitCurrency.IDR(purse.balance)

    override val cardName: String
        get() = Localizer.localizeString(R.string.card_name_bsb_cash)

    override val info: List<ListItemInterface>
        get() = purse.info

    companion object {
        val CARD_INFO = CardInfo(
            name = R.string.card_name_bsb_cash,
            locationId = R.string.location_indonesia,
            cardType = CardType.ISO7816,
            region = TransitRegion.INDONESIA,
            // Card number and balance.
            preview = true,
            // Reading needs commands without an application selected, which iOS cannot send.
            iOSSupported = false,
            imageId = R.drawable.bsb_cash_card
        )

        val FACTORY: CardTransitFactory<BsbCashApplication> =
            object : CardTransitFactory<BsbCashApplication> {
                override val allCards = listOf(CARD_INFO)

                override fun parseTransitIdentity(card: BsbCashApplication) =
                    TransitIdentity(
                        Localizer.localizeString(R.string.card_name_bsb_cash),
                        BsbCashPurse(card.purse).serial
                    )

                override fun parseTransitData(card: BsbCashApplication) =
                    BsbCashTransitData(card.purse)

                override fun check(card: BsbCashApplication) = true
            }
    }
}