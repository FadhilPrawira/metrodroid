package au.id.micolous.metrodroid.transit.trans_semarang_e_card

import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.card.classic.ClassicCardTransitFactory
import au.id.micolous.metrodroid.card.classic.ClassicSector
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.*
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.ui.ListItemInterface

/**
 * Trans Semarang E-Card on MIFARE Classic 1K.
 *
 * Reverse-engineered manually from card dumps (default keys FF FF FF FF FF FF).
 * Transaction history / refill history are NOT YET understood - only balance
 * and cardholder biodata are parsed so far.
 */
@Parcelize
data class TransSemarangECardTransitData(
    private val mSerial: String,
    val purse: TransSemarangECardPurse?,
    private val cardholderName: String?,
    private val cardholderPlaceOfBirth: String?,
    private val cardholderBirthDate: String?,
    private val cardholderGender: String?,
    private val cardholderPhoneNumber: String?,
    private val cardNumber: Long
) : TransitData() {

    override val balance: TransitCurrency? get() = purse?.balance
    override val serialNumber get() = mSerial
    override val cardName get() = NAME
    override val trips: List<Trip>? get() = null
    override val subscriptions: List<Subscription> get() = emptyList()

    override val info: List<ListItemInterface>?
        get() = listOfNotNull(
            ListItem(R.string.card_number, cardNumber.toString()),
            cardholderName?.let { ListItem(R.string.card_holders_name, it) },
            cardholderPlaceOfBirth?.let { ListItem(R.string.place_of_birth, it) },
            cardholderBirthDate?.let { ListItem(R.string.date_of_birth, it) },
            cardholderGender?.let { ListItem(R.string.gender, it) },
            cardholderPhoneNumber?.let { ListItem(R.string.phone_number, it) }
        )

    private constructor(card: ClassicCard) : this(
        mSerial = getSerial(card),
        purse = TransSemarangECardPurse.parse(card),
        cardholderName = safeParseAscii(card, 3, 0),
        cardholderPlaceOfBirth = safeParseAscii(card, 3, 1),
        cardholderBirthDate = safeParseAscii(card, 3, 2),
        cardholderGender = safeParseAscii(card, 4, 0),
        cardholderPhoneNumber = safeParseAscii(card, 4, 1),
        cardNumber = getCardNumber(card)
    )

    companion object {
        private const val NAME = "Trans Semarang"

        private val CARD_INFO = CardInfo(
            name = NAME,
            locationId = R.string.location_semarang,
            region = TransitRegion.INDONESIA,
            cardType = CardType.MifareClassic,
            keysRequired = false,
            preview = false)

        private fun getSerial(card: ClassicCard): String =
            card[0, 0].data.getHexString(0, 4).uppercase()

        /**
         * Printed card number: the 4 UID bytes read as little-endian unsigned,
         * in decimal. E.g. UID 2B D7 7C D1 -> 0xD17CD72B -> 3514619691.
         */
        private fun getCardNumber(card: ClassicCard): Long {
            val uid = card[0, 0].data
            var value = 0L
            for (i in 3 downTo 0) {
                value = (value shl 8) or (uid[i].toLong() and 0xFF)
            }
            return value
        }

        private fun safeParseAscii(card: ClassicCard, sector: Int, block: Int): String? {
            val value = try {
                TransSemarangECardUtils.parseAscii(card[sector, block].data)
            } catch (e: Exception) {
                return null
            }
            return value.ifBlank { null }
        }

        val FACTORY: ClassicCardTransitFactory = object : ClassicCardTransitFactory {
            override fun parseTransitIdentity(card: ClassicCard) =
                TransitIdentity(NAME, getSerial(card))

            override fun parseTransitData(card: ClassicCard) = TransSemarangECardTransitData(card)

            override fun earlyCheck(sectors: List<ClassicSector>): Boolean {
                return try {
                    TransSemarangECardUtils.isValidValueBlock(sectors[2][1].data)
                } catch (e: Exception) {
                    false
                }
            }

            override val allCards get() = listOf(CARD_INFO)
        }
    }
}