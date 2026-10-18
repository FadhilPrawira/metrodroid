package au.id.micolous.metrodroid.transit.mandiri_emoney

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.mandiri_emoney.MandiriEmoneyApplication
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.time.MetroTimeZone
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.CardTransitFactory
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.TransitRegion
import au.id.micolous.metrodroid.transit.Trip
import au.id.micolous.metrodroid.util.ImmutableByteArray

@Parcelize
class MandiriEmoneyTransitData(
    override val serialNumber: String?,
    private val mBalance: Int,
    private val mTrips: List<Trip>
) : TransitData() {

    public override val balance: TransitCurrency
        get() = TransitCurrency.IDR(mBalance)

    override val cardName: String
        get() = Localizer.localizeString(R.string.card_name_mandiri_emoney)

    override val trips: List<Trip>
        get() = mTrips

    constructor(app: MandiriEmoneyApplication) : this(
        serialNumber = app.serial,
        // Little-endian, already in Rupiah (no scaling).
        mBalance = app.balance.byteArrayToIntReversed(0, 4),
        mTrips = parseHistory(app.history)
    )

    companion object {
        private val TZ = MetroTimeZone.JAKARTA
        private const val RECORD_LEN = 24
        private const val TYPE_TOPUP = 0x0100
        private const val TYPE_PAYMENT = 0x0120

        val CARD_INFO = CardInfo(
            name = R.string.card_name_mandiri_emoney,
            locationId = R.string.location_indonesia,
            cardType = CardType.ISO7816,
            region = TransitRegion.INDONESIA,
            // Card number, balance and transaction history (new-generation cards only).
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


        private fun parseHistory(pages: List<ImmutableByteArray>): List<Trip> =
            pages.flatMap { page ->
                (0 until page.size / RECORD_LEN).map {
                    page.sliceOffLen(
                        it * RECORD_LEN,
                        RECORD_LEN
                    )
                }
            }
                .distinct()          // the youngest record appears twice (on page 0 and the last page)
                .mapNotNull { parseRecord(it) }
                .sortedWith(Trip.Comparator())   // latest one first, just like with other cards

        private fun parseRecord(r: ImmutableByteArray): Trip? {
            val month = r.convertBCDtoInteger(1, 1)
            if (month !in 1..12) return null

            val ts = TimestampFull(
                TZ,
                2000 + r.convertBCDtoInteger(2, 1),
                month - 1,                       // the constructor expects a 0-based month
                r.convertBCDtoInteger(0, 1),
                r.convertBCDtoInteger(3, 1),
                r.convertBCDtoInteger(4, 1),
                r.convertBCDtoInteger(5, 1)
            )

            val type = r.byteArrayToInt(14, 2)                // big-endian
            val amount = r.byteArrayToIntReversed(16, 4)      // little-endian
            val balanceAfter = r.byteArrayToIntReversed(20, 4)
            val unknown = r.getHexString(6, 8)                // bytes 6..13, meaning not known yet

            return if (type == TYPE_TOPUP) MandiriEmoneyRefill(ts, amount, balanceAfter, unknown)
            else MandiriEmoneyTrip(ts, amount, balanceAfter, unknown)
        }
    }
}