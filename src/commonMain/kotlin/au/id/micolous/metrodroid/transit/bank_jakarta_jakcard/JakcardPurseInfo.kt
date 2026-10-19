package au.id.micolous.metrodroid.transit.bank_jakarta_jakcard

import au.id.micolous.metrodroid.card.ksx6924.KSX6924PurseInfoDefaultResolver
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.time.Daystamp
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray
import au.id.micolous.metrodroid.util.hexString

/**
 * Purse information (tag B0 of the FCI) as defined by the Indonesian national e-money
 * interoperability standard. It is 47 bytes long and holds no personal data.
 */
class JakcardPurseInfo(private val data: ImmutableByteArray) {

    // Payment terms of the card; 0x00 is prepaid.
    private val cardType: Byte
        get() = data[TYPE_OFFSET]

    // Encryption algorithm of the card; 0x10 is 3-DES.
    private val algorithm: Byte
        get() = data[ALGORITHM_OFFSET]

    // Version of the encryption key.
    private val version: Byte
        get() = data[VERSION_OFFSET]

    // Version of the key set.
    private val setVersion: Byte
        get() = data[SET_VERSION_OFFSET]

    // Authentication ID (IDTR), stored as BCD.
    private val idtr: ImmutableByteArray
        get() = data.sliceOffLen(IDTR_OFFSET, IDTR_LEN)

    // Code of the national interoperability scheme.
    private val interop: ImmutableByteArray
        get() = data.sliceOffLen(INTEROP_OFFSET, INTEROP_LEN)

    // Date of issue, stored as BCD (YYYYMMDD).
    private val issueDate: Daystamp?
        get() = parseDate(ISSUE_DATE_OFFSET)

    // Expiry date, stored as BCD (YYYYMMDD).
    private val expiryDate: Daystamp?
        get() = parseDate(EXPIRY_DATE_OFFSET)

    // Card holder type; 0x01 is an adult.
    private val userCode: Byte
        get() = data[USER_CODE_OFFSET]

    // Discount type; 0x00 is no discount.
    private val discount: Byte
        get() = data[DISCOUNT_OFFSET]

    // Maximum balance of the card in Rupiah (unsigned, big-endian).
    private val maxBalance: Long
        get() = data.byteArrayToLong(BALANCE_MAX_OFFSET, BALANCE_MAX_LEN)

    // Code of the branch that issued the card.
    private val branch: ImmutableByteArray
        get() = data.sliceOffLen(BRANCH_OFFSET, BRANCH_LEN)

    // Deposit of the card; not seen with any value other than 0.
    private val deposit: Long
        get() = data.byteArrayToLong(DEPOSIT_OFFSET, DEPOSIT_LEN)

    // Bank identification number.
    private val binId: ImmutableByteArray
        get() = data.sliceOffLen(BIN_ID_OFFSET, BIN_ID_LEN)

    // Issuer ID.
    private val issuerId: ImmutableByteArray
        get() = data.sliceOffLen(ISSUER_ID_OFFSET, ISSUER_ID_LEN)

    // Province code of the issuer.
    private val provinceCode: ImmutableByteArray
        get() = data.sliceOffLen(PROVINCE_OFFSET, PROVINCE_LEN)

    // Reserved for future use.
    private val rfu: Byte
        get() = data[RFU_OFFSET]

    val info: List<ListItem>
        get() = listOf(
            ListItem(R.string.cardtype_header, KSX6924PurseInfoDefaultResolver.resolveCardType(cardType)),
            ListItem(R.string.crypto_algo_header, KSX6924PurseInfoDefaultResolver.resolveCryptoAlgo(algorithm)),
            ListItem(R.string.encryption_key_version, version.hexString),
            ListItem(R.string.jakcard_key_set_version, setVersion.hexString),
            ListItem(R.string.authentication_id, idtr.getHexString()),
            ListItem(R.string.jakcard_interop_code, interop.getHexString()),
            ListItem(R.string.issue_date, issueDate?.format()),
            ListItem(R.string.expiry_date, expiryDate?.format()),
            ListItem(R.string.ticket_type, userCode.hexString),
            ListItem(R.string.discount_type, discount.hexString),
            ListItem(R.string.maximum_balance, TransitCurrency.IDR(maxBalance.toInt()).formatCurrencyString(true)),
            ListItem(R.string.branch_code, branch.getHexString()),
            ListItem(R.string.jakcard_deposit, deposit.toString()),
            ListItem(R.string.jakcard_bin_id, binId.getHexString()),
            ListItem(R.string.card_issuer, issuerId.getHexString()),
            ListItem(R.string.jakcard_province_code, provinceCode.getHexString()),
            ListItem(R.string.rfu, rfu.hexString)
        )

    private fun parseDate(offset: Int): Daystamp? {
        val date = data.convertBCDtoInteger(offset, DATE_LEN)
        // An unset date means the card has none.
        if (date == 0) return null
        // The constructor counts months from 0.
        return Daystamp(date / 10000, (date / 100) % 100 - 1, date % 100)
    }

    companion object {
        // Offset of the payment terms.
        private const val TYPE_OFFSET = 0

        // Offset of the encryption algorithm.
        private const val ALGORITHM_OFFSET = 1

        // Offset of the encryption key version.
        private const val VERSION_OFFSET = 2

        // Offset of the key set version.
        private const val SET_VERSION_OFFSET = 3

        // Offset of the authentication ID.
        private const val IDTR_OFFSET = 12

        // Length of the authentication ID.
        private const val IDTR_LEN = 2

        // Offset of the interoperability scheme code.
        private const val INTEROP_OFFSET = 14

        // Length of the interoperability scheme code.
        private const val INTEROP_LEN = 3

        // Offset of the date of issue.
        private const val ISSUE_DATE_OFFSET = 17

        // Offset of the expiry date.
        private const val EXPIRY_DATE_OFFSET = 21

        // Length of a date (YYYYMMDD in BCD).
        private const val DATE_LEN = 4

        // Offset of the card holder type.
        private const val USER_CODE_OFFSET = 25

        // Offset of the discount type.
        private const val DISCOUNT_OFFSET = 26

        // Offset of the maximum balance.
        private const val BALANCE_MAX_OFFSET = 27

        // Length of the maximum balance.
        private const val BALANCE_MAX_LEN = 4

        // Offset of the branch code.
        private const val BRANCH_OFFSET = 31

        // Length of the branch code.
        private const val BRANCH_LEN = 2

        // Offset of the deposit.
        private const val DEPOSIT_OFFSET = 33

        // Length of the deposit.
        private const val DEPOSIT_LEN = 4

        // Offset of the bank identification number.
        private const val BIN_ID_OFFSET = 37

        // Length of the bank identification number.
        private const val BIN_ID_LEN = 3

        // Offset of the issuer ID.
        private const val ISSUER_ID_OFFSET = 40

        // Length of the issuer ID.
        private const val ISSUER_ID_LEN = 3

        // Offset of the province code.
        private const val PROVINCE_OFFSET = 43

        // Length of the province code.
        private const val PROVINCE_LEN = 3

        // Offset of the reserved byte.
        private const val RFU_OFFSET = 46
    }
}