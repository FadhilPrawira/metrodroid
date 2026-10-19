package au.id.micolous.metrodroid.transit.bank_jakarta_jakcard

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.bank_jakarta_jakcard.JakcardApplication
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.time.MetroTimeZone
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.CardTransitFactory
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.TransitRegion
import au.id.micolous.metrodroid.transit.Trip
import au.id.micolous.metrodroid.util.ImmutableByteArray

@Parcelize
class JakcardTransitData(
    override val serialNumber: String?,
    private val mBalance: Int,
    private val mTrips: List<Trip>
) : TransitData() {

    public override val balance: TransitCurrency
        get() = TransitCurrency.IDR(mBalance)

    override val cardName: String
        get() = Localizer.localizeString(R.string.card_name_bank_jakarta_jakcard)

    override val trips: List<Trip>
        get() = mTrips

    constructor(app: JakcardApplication) : this(
        serialNumber = app.serial,
        // Unsigned big-endian, already in Rupiah (no scaling).
        mBalance = app.balanceData.byteArrayToInt(0, BALANCE_LEN),
        // The top-up log keeps top-ups that were already overwritten in the combined log, and
        // repeats the newer ones; the sequence number tells them apart.
        mTrips = (app.log + app.topupLog)
            .filter { it.size >= RECORD_MIN_LEN }
            .distinctBy { it.byteArrayToLong(RECORD_SEQUENCE_OFFSET, RECORD_SEQUENCE_LEN) }
            .map { parseRecord(it) }
            .sortedWith(Trip.Comparator())   // latest one first, just like with other cards
    )

    companion object {
        // Time zone of the transaction timestamps (the cards store local time without a zone).
        private val TZ = MetroTimeZone.JAKARTA

        // Length of the balance in the balance response.
        private const val BALANCE_LEN = 4

        // Number of bytes needed to read the last field (transaction time) of a log record.
        private const val RECORD_MIN_LEN = 33

        // Offset of the transaction type in a log record.
        private const val RECORD_TYPE_OFFSET = 0

        // Transaction type of a top-up; other values are payments (1).
        private const val RECORD_TYPE_TOPUP = 2

        // Offset of the balance after the transaction in a log record.
        private const val RECORD_BALANCE_OFFSET = 2

        // Length of the balance after the transaction in a log record.
        private const val RECORD_BALANCE_LEN = 4

        // Offset of the sequence number in a log record; it counts every transaction of the card.
        private const val RECORD_SEQUENCE_OFFSET = 6

        // Length of the sequence number in a log record.
        private const val RECORD_SEQUENCE_LEN = 4

        // Offset of the amount in a log record.
        private const val RECORD_AMOUNT_OFFSET = 10

        // Length of the amount in a log record.
        private const val RECORD_AMOUNT_LEN = 4

        // Offset of the terminal ID in a log record.
        private const val RECORD_TERMINAL_OFFSET = 14

        // Length of the terminal ID in a log record.
        private const val RECORD_TERMINAL_LEN = 8

        // Offset of the counter in a log record; probably counted by the terminal.
        private const val RECORD_COUNTER_OFFSET = 22

        // Length of the counter in a log record.
        private const val RECORD_COUNTER_LEN = 4

        // Offset of the date (YYYYMMDD, BCD) in a log record.
        private const val RECORD_DATE_OFFSET = 26

        // Length of the date in a log record.
        private const val RECORD_DATE_LEN = 4

        // Offset of the time (HHMMSS, BCD) in a log record.
        private const val RECORD_TIME_OFFSET = 30

        // Length of the time in a log record.
        private const val RECORD_TIME_LEN = 3

        val CARD_INFO = CardInfo(
            name = R.string.card_name_bank_jakarta_jakcard,
            locationId = R.string.location_jakarta,
            cardType = CardType.ISO7816,
            region = TransitRegion.INDONESIA,
            // Card number, balance and transaction history.
            preview = true,
            // The AID is not registered in the iOS Info.plist.
            iOSSupported = false
        )

        val FACTORY: CardTransitFactory<JakcardApplication> =
            object : CardTransitFactory<JakcardApplication> {
                override val allCards = listOf(CARD_INFO)

                override fun parseTransitIdentity(card: JakcardApplication) =
                    TransitIdentity(Localizer.localizeString(R.string.card_name_bank_jakarta_jakcard), card.serial)

                override fun parseTransitData(card: JakcardApplication) =
                    JakcardTransitData(card)

                override fun check(card: JakcardApplication) = true
            }

        private fun parseTimestamp(r: ImmutableByteArray): TimestampFull? {
            val date = r.convertBCDtoInteger(RECORD_DATE_OFFSET, RECORD_DATE_LEN)
            val time = r.convertBCDtoInteger(RECORD_TIME_OFFSET, RECORD_TIME_LEN)
            // An unset date means the record has no usable time.
            if (date == 0) return null
            // The constructor counts months from 0.
            return TimestampFull(
                TZ, date / 10000, (date / 100) % 100 - 1, date % 100,
                time / 10000, (time / 100) % 100, time % 100
            )
        }

        private fun parseRecord(r: ImmutableByteArray): Trip {
            val type = r.byteArrayToInt(RECORD_TYPE_OFFSET, 1)
            val amount = r.byteArrayToInt(RECORD_AMOUNT_OFFSET, RECORD_AMOUNT_LEN)
            val balanceAfter = r.byteArrayToInt(RECORD_BALANCE_OFFSET, RECORD_BALANCE_LEN)
            val sequence = r.byteArrayToLong(RECORD_SEQUENCE_OFFSET, RECORD_SEQUENCE_LEN)
            val terminal = r.getHexString(RECORD_TERMINAL_OFFSET, RECORD_TERMINAL_LEN)
            val counter = r.byteArrayToLong(RECORD_COUNTER_OFFSET, RECORD_COUNTER_LEN)
            val ts = parseTimestamp(r)

            return if (type == RECORD_TYPE_TOPUP)
                JakcardRefill(ts, amount, balanceAfter, sequence, terminal, counter)
            else
                JakcardTrip(ts, amount, balanceAfter, sequence, terminal, counter)
        }
    }
}