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
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.ui.ListItemInterface

@Parcelize
class BsbCashTransitData(
    override val serialNumber: String?,
    private val mBalance: Int,
    private val mMaxBalance: Int
) : TransitData() {

    public override val balance: TransitCurrency
        get() = TransitCurrency.IDR(mBalance)

    override val cardName: String
        get() = Localizer.localizeString(R.string.card_name_bsb_cash)

    override val info: List<ListItemInterface>
        get() = listOf(
            ListItem(
                R.string.maximum_balance,
                TransitCurrency.IDR(mMaxBalance).formatCurrencyString(true)
            )
        )

    constructor(app: BsbCashApplication) : this(
        serialNumber = app.serial,
        // Signed big-endian like the balance of a CEPAS purse, already in Rupiah (no scaling).
        mBalance = app.purse.getBitsFromBufferSigned(BALANCE_OFFSET * 8, BALANCE_LEN * 8),
        // Unsigned big-endian; 1E 84 80 is 2,000,000.
        mMaxBalance = app.purse.byteArrayToInt(
            BsbCashApplication.MAX_BALANCE_OFFSET, BsbCashApplication.MAX_BALANCE_LEN
        )
    )

    companion object {
        // Offset of the balance in the purse data.
        private const val BALANCE_OFFSET = 2

        // Length of the balance in the purse data.
        private const val BALANCE_LEN = 3

        val CARD_INFO = CardInfo(
            name = R.string.card_name_bsb_cash,
            locationId = R.string.location_indonesia,
            cardType = CardType.ISO7816,
            region = TransitRegion.INDONESIA,
            // Card number and balance.
            preview = true,
            // Reading needs commands without an application selected, which iOS cannot send.
            iOSSupported = false
        )

        val FACTORY: CardTransitFactory<BsbCashApplication> =
            object : CardTransitFactory<BsbCashApplication> {
                override val allCards = listOf(CARD_INFO)

                override fun parseTransitIdentity(card: BsbCashApplication) =
                    TransitIdentity(Localizer.localizeString(R.string.card_name_bsb_cash), card.serial)

                override fun parseTransitData(card: BsbCashApplication) =
                    BsbCashTransitData(card)

                override fun check(card: BsbCashApplication) = true
            }
    }
}