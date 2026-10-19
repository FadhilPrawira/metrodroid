package au.id.micolous.metrodroid.transit.bca_flazz

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.bca_flazz.BcaFlazzApplication
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.time.Epoch
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
class BcaFlazzTransitData(
    override val serialNumber: String?,
    private val mBalance: Int,
    private val mTrips: List<Trip>
) : TransitData() {

    public override val balance: TransitCurrency
        get() = TransitCurrency.IDR(mBalance)

    override val cardName: String
        get() = Localizer.localizeString(R.string.card_name_bca_flazz)

    override val trips: List<Trip>
        get() = mTrips

    constructor(app: BcaFlazzApplication) : this(
        serialNumber = app.serial,
        // Big-endian, already in Rupiah (no scaling).
        mBalance = app.balance.byteArrayToInt(BALANCE_OFFSET, BALANCE_LEN),
        mTrips = parseTrips(app)
    )

    companion object {
        // Time zone of the transaction timestamps (the cards store local time without a zone).
        private val TZ = MetroTimeZone.JAKARTA

        // The cards count seconds of local time since 1 January 1980.
        private val EPOCH = Epoch.local(1980, TZ)

        // Number of seconds in a day.
        private const val SECONDS_PER_DAY = 86400

        // Offset of the balance in the balance response.
        private const val BALANCE_OFFSET = 2

        // Length of the balance in the balance response.
        private const val BALANCE_LEN = 3

        // Length of an amount field (transaction amount and balance in a record).
        private const val AMOUNT_LEN = 3

        // Sign bit of a 24-bit amount.
        private const val AMOUNT_SIGN_BIT = 0x800000

        // Modulus of a 24-bit amount.
        private const val AMOUNT_MODULUS = 0x1000000

        // Length of a timestamp field.
        private const val TIME_LEN = 4

        // Length of the binary prefix in front of the terminal ID in a record.
        private const val TERMINAL_PREFIX_LEN = 6

        // Length of the ASCII terminal ID in a record.
        private const val TERMINAL_LEN = 8

        // Length of the trailing bytes of a record whose meaning is not known yet.
        private const val TRAILER_LEN = 2

        // Record type of the first top-up in the legacy payment log (initial value).
        private const val TYPE_INITIAL_VALUE = 0x44

        // Offset of the record type in a legacy payment slot.
        private const val PAYMENT_TYPE_OFFSET = 0

        // Offset of the balance after the payment in a legacy payment slot.
        private const val PAYMENT_BALANCE_OFFSET = 1

        // Length of the balance after the payment in a legacy payment slot.
        private const val PAYMENT_BALANCE_LEN = 4

        // Offset of the (unsigned) amount in a legacy payment slot.
        private const val PAYMENT_AMOUNT_OFFSET = 6

        // Offset of the bytes of a legacy payment slot whose meaning is not known yet.
        private const val PAYMENT_UNKNOWN_OFFSET = 11

        // Length of the bytes of a legacy payment slot whose meaning is not known yet.
        private const val PAYMENT_UNKNOWN_LEN = 8

        // Offset of the terminal ID in a legacy payment slot.
        private const val PAYMENT_TERMINAL_OFFSET = 30

        // Offset of the timestamp in a legacy payment slot.
        private const val PAYMENT_TIME_OFFSET = 38

        // Record type of a sale. Every other type adds value: 0x14 is a top-up and 0x34 is the
        // first top-up (activation) of the card.
        private const val TYPE_SALE = 0x04

        // Offset of the record type in a record.
        private const val RECORD_TYPE_OFFSET = 0

        // Offset of the amount in a record; negative (two's complement) for a sale.
        private const val RECORD_AMOUNT_OFFSET = 1

        // Offset of the timestamp in a record.
        private const val RECORD_TIME_OFFSET = 4

        // Offset of the terminal ID prefix in a record.
        private const val RECORD_TERMINAL_PREFIX_OFFSET = 8

        // Offset of the terminal ID in a record.
        private const val RECORD_TERMINAL_OFFSET = 14

        // Offset of the balance before the transaction in a record.
        private const val RECORD_BALANCE_BEFORE_OFFSET = 22

        // Offset of the trailing bytes in a record.
        private const val RECORD_TRAILER_OFFSET = 25

        val CARD_INFO = CardInfo(
            name = R.string.card_name_bca_flazz,
            locationId = R.string.location_indonesia,
            cardType = CardType.ISO7816,
            region = TransitRegion.INDONESIA,
            // Card number, balance and transaction history.
            preview = true,
            // The AID is not registered in the iOS Info.plist.
            iOSSupported = false
        )

        val FACTORY: CardTransitFactory<BcaFlazzApplication> =
            object : CardTransitFactory<BcaFlazzApplication> {
                override val allCards = listOf(CARD_INFO)

                override fun parseTransitIdentity(card: BcaFlazzApplication) =
                    TransitIdentity(
                        Localizer.localizeString(R.string.card_name_bca_flazz),
                        card.serial
                    )

                override fun parseTransitData(card: BcaFlazzApplication) =
                    BcaFlazzTransitData(card)

                override fun check(card: BcaFlazzApplication) = true
            }

        private fun parseTimestamp(r: ImmutableByteArray, offset: Int): TimestampFull {
            val seconds = r.byteArrayToLong(offset, TIME_LEN)
            return EPOCH.daySecond(
                (seconds / SECONDS_PER_DAY).toInt(),
                (seconds % SECONDS_PER_DAY).toInt()
            )
        }

        private fun parseTerminal(r: ImmutableByteArray, offset: Int): String? =
            r.sliceOffLen(offset, TERMINAL_LEN).readASCII().ifBlank { null }

        // The legacy payment log can hold payments that the record command does not return.
        // Both sources describe the same payment when the timestamps are equal.
        private fun parseTrips(app: BcaFlazzApplication): List<Trip> {
            val trips = app.records.mapNotNull { parseRecord(it) }
            val known = trips.mapNotNull { it.startTimestamp }.toSet()
            val legacy = app.payments.mapNotNull { parsePayment(it) }
                .filter { it.startTimestamp !in known }
            return (trips + legacy).sortedWith(Trip.Comparator())  // latest one first
        }

        private fun parsePayment(r: ImmutableByteArray): Trip? {
            // Unused slots are all zeros.
            if (r.size < BcaFlazzApplication.PAYMENT_SLOT_LEN || r.isAllZero()) return null

            val type = r.byteArrayToInt(PAYMENT_TYPE_OFFSET, 1)
            val ts = parseTimestamp(r, PAYMENT_TIME_OFFSET)
            val amount = r.byteArrayToInt(PAYMENT_AMOUNT_OFFSET, AMOUNT_LEN)
            val balanceAfter = r.byteArrayToInt(PAYMENT_BALANCE_OFFSET, PAYMENT_BALANCE_LEN)
            val terminal = parseTerminal(r, PAYMENT_TERMINAL_OFFSET)
            val unknown = r.getHexString(PAYMENT_UNKNOWN_OFFSET, PAYMENT_UNKNOWN_LEN)

            return if (type == TYPE_INITIAL_VALUE)
                BcaFlazzRefill(ts, amount, balanceAfter, type, terminal, unknown)
            else
            // Unknown types are still shown as payments, so the user can see the type code.
                BcaFlazzTrip(ts, amount, balanceAfter, type, terminal, unknown)
        }

        private fun parseRecord(r: ImmutableByteArray): Trip? {
            // Unused records are all zeros.
            if (r.size < BcaFlazzApplication.RECORD_LEN || r.isAllZero()) return null

            val type = r.byteArrayToInt(RECORD_TYPE_OFFSET, 1)
            // Signed: negative for a sale, positive for a top-up.
            var amount = r.byteArrayToInt(RECORD_AMOUNT_OFFSET, AMOUNT_LEN)
            if (amount >= AMOUNT_SIGN_BIT) amount -= AMOUNT_MODULUS
            // The card stores the balance before the transaction, not after it.
            val balanceAfter = r.byteArrayToInt(RECORD_BALANCE_BEFORE_OFFSET, AMOUNT_LEN) + amount
            val ts = parseTimestamp(r, RECORD_TIME_OFFSET)
            val terminal = parseTerminal(r, RECORD_TERMINAL_OFFSET)
            val unknown = r.getHexString(RECORD_TERMINAL_PREFIX_OFFSET, TERMINAL_PREFIX_LEN) +
                    " " + r.getHexString(RECORD_TRAILER_OFFSET, TRAILER_LEN)

            return if (type == TYPE_SALE)
                BcaFlazzTrip(ts, -amount, balanceAfter, type, terminal, unknown)
            else
                BcaFlazzRefill(ts, amount, balanceAfter, type, terminal, unknown)
        }
    }
}