package au.id.micolous.metrodroid.transit.bank_negara_indonesia_tapcash

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.bank_negara_indonesia_tapcash.BniTapcashApplication
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
class BniTapcashTransitData(
    override val serialNumber: String?,
    private val mBalance: Int,
    private val mTrips: List<Trip>
) : TransitData() {

    public override val balance: TransitCurrency
        get() = TransitCurrency.IDR(mBalance)

    override val cardName: String
        get() = Localizer.localizeString(R.string.card_name_bni_tapcash)

    override val trips: List<Trip>
        get() = mTrips

    constructor(app: BniTapcashApplication) : this(
        serialNumber = app.serial,
        // Signed big-endian, already in Rupiah (no scaling).
        mBalance = parseAmount(app.purse, BALANCE_OFFSET),
        mTrips = app.records.mapNotNull { parseRecord(it) }
            .sortedWith(Trip.Comparator())   // latest one first, just like with other cards
    )

    companion object {
        // Time zone of the transaction timestamps (the cards store local time without a zone).
        private val TZ = MetroTimeZone.JAKARTA

        // The cards count seconds of local time since 1 January 1995.
        private val EPOCH = Epoch.local(1995, TZ)

        // Number of seconds in a day.
        private const val SECONDS_PER_DAY = 86400

        // Offset of the balance in the purse response.
        private const val BALANCE_OFFSET = 2

        // Length of an amount field (balance and transaction amount).
        private const val AMOUNT_LEN = 3

        // Sign bit of a 24-bit amount.
        private const val AMOUNT_SIGN_BIT = 0x800000

        // Modulus of a 24-bit amount.
        private const val AMOUNT_MODULUS = 0x1000000

        // Offset of the transaction type in a log record.
        private const val RECORD_TYPE_OFFSET = 0

        // Offset of the amount in a log record; negative for a payment, positive for a top-up.
        private const val RECORD_AMOUNT_OFFSET = 1

        // Offset of the timestamp in a log record.
        private const val RECORD_TIME_OFFSET = 4

        // Length of the timestamp in a log record.
        private const val RECORD_TIME_LEN = 4

        // Offset of the balance after the transaction in a log record.
        private const val RECORD_BALANCE_OFFSET = 8

        // Length of the balance after the transaction in a log record.
        private const val RECORD_BALANCE_LEN = 4

        // Offset of the machine ID in a log record (eight decimal digits, two per byte).
        private const val RECORD_MACHINE_ID_OFFSET = 12

        // Length of the machine ID in a log record.
        private const val RECORD_MACHINE_ID_LEN = 4

        // Number of digits in each half of the displayed machine ID.
        private const val MACHINE_ID_GROUP_LEN = 4

        val CARD_INFO = CardInfo(
            name = R.string.card_name_bni_tapcash,
            locationId = R.string.location_indonesia,
            cardType = CardType.ISO7816,
            region = TransitRegion.INDONESIA,
            // Card number, balance and transaction history.
            preview = true,
            // The AID is not registered in the iOS Info.plist.
            iOSSupported = false,
            imageId = R.drawable.bank_negara_indonesia_tapcash_card
        )

        val FACTORY: CardTransitFactory<BniTapcashApplication> =
            object : CardTransitFactory<BniTapcashApplication> {
                override val allCards = listOf(CARD_INFO)

                override fun parseTransitIdentity(card: BniTapcashApplication) =
                    TransitIdentity(
                        Localizer.localizeString(R.string.card_name_bni_tapcash),
                        card.serial
                    )

                override fun parseTransitData(card: BniTapcashApplication) =
                    BniTapcashTransitData(card)

                override fun check(card: BniTapcashApplication) = true
            }

        /**
         * Formats a machine ID as two groups of four digits, for example `4142-3935`.
         *
         * Returns null when the ID is all zeros, which is what most top-ups carry.
         */
        internal fun formatMachineId(id: String): String? =
            if (id.all { it == '0' }) null
            else "${id.take(MACHINE_ID_GROUP_LEN)}-${id.drop(MACHINE_ID_GROUP_LEN)}"

        private fun parseAmount(r: ImmutableByteArray, offset: Int): Int {
            val raw = r.byteArrayToInt(offset, AMOUNT_LEN)
            return if (raw >= AMOUNT_SIGN_BIT) raw - AMOUNT_MODULUS else raw
        }

        private fun parseRecord(r: ImmutableByteArray): Trip? {
            // Unused slots are all zeros.
            if (r.size < BniTapcashApplication.RECORD_LEN || r.isAllZero()) return null

            val type = r.byteArrayToInt(RECORD_TYPE_OFFSET, 1)
            val amount = parseAmount(r, RECORD_AMOUNT_OFFSET)
            val seconds = r.byteArrayToLong(RECORD_TIME_OFFSET, RECORD_TIME_LEN)
            val ts: TimestampFull = EPOCH.daySecond(
                (seconds / SECONDS_PER_DAY).toInt(),
                (seconds % SECONDS_PER_DAY).toInt()
            )
            val balanceAfter = r.byteArrayToInt(RECORD_BALANCE_OFFSET, RECORD_BALANCE_LEN)
            val machineId = r.getHexString(RECORD_MACHINE_ID_OFFSET, RECORD_MACHINE_ID_LEN)

            return if (amount > 0)
                BniTapcashRefill(ts, amount, balanceAfter, type, machineId)
            else
                BniTapcashTrip(ts, -amount, balanceAfter, type, machineId)
        }
    }
}
