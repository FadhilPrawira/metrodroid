package au.id.micolous.metrodroid.transit.bank_sumsel_babel_bsb_cash

import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.time.Epoch
import au.id.micolous.metrodroid.time.MetroTimeZone
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.time.TimestampFormatter
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.ui.HeaderListItem
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.util.ImmutableByteArray

/**
 * Purse data of BSB Cash (response of `90 32 03 00 0A ...`). The layout follows the CEPAS purse.
 * Multi-byte numbers are big-endian.
 */
class BsbCashPurse(private val data: ImmutableByteArray) {

    private val appletVersion: Byte
        get() = data[APPLET_VERSION_OFFSET]

    private val status: Byte
        get() = data[STATUS_OFFSET]

    // Signed, like the balance of a CEPAS purse. Already in Rupiah.
    val balance: Int
        get() = data.getBitsFromBufferSigned(BALANCE_OFFSET * 8, BALANCE_LEN * 8)

    // Amount of the automatic top-up. The content of this slot is not verified.
    private val autoLoadAmount: Int
        get() = data.getBitsFromBufferSigned(AUTO_LOAD_OFFSET * 8, AUTO_LOAD_LEN * 8)

    // Card Application Number, the number printed on the card.
    private val can: ImmutableByteArray
        get() = data.sliceOffLen(CAN_OFFSET, CAN_LEN)

    // Card number grouped by 4 digits.
    val serial: String
        get() = can.getHexString().chunked(4).joinToString(" ")

    private val csn: ImmutableByteArray
        get() = data.sliceOffLen(CSN_OFFSET, CSN_LEN)

    private val expiryDate: Timestamp
        get() = EPOCH.days(data.byteArrayToInt(EXPIRY_DATE_OFFSET, DATE_LEN))

    private val creationDate: Timestamp
        get() = EPOCH.days(data.byteArrayToInt(CREATION_DATE_OFFSET, DATE_LEN))

    private val lastCreditTrp: Int
        get() = data.byteArrayToInt(LAST_CREDIT_TRP_OFFSET, TRP_LEN)

    private val lastCreditHeader: ImmutableByteArray
        get() = data.sliceOffLen(LAST_CREDIT_HEADER_OFFSET, LAST_CREDIT_HEADER_LEN)

    private val logRecordCount: Int
        get() = data[LOG_RECORD_COUNT_OFFSET].toInt() and 0xff

    private val lastTrp: Int
        get() = data.byteArrayToInt(LAST_TRP_OFFSET, TRP_LEN)

    private val lastRecord: ImmutableByteArray
        get() = data.sliceOffLen(LAST_RECORD_OFFSET, LAST_RECORD_LEN)

    // Length of the issuer specific data (32 on the scanned card).
    private val issuerDataLength: Int
        get() = data[ISSUER_DATA_LENGTH_OFFSET].toInt() and 0xff

    // Issuer specific data, it holds the bad debt counter, key set index and maximum balance.
    private val issuerData: ImmutableByteArray
        get() = data.sliceOffLenSafe(ISSUER_DATA_OFFSET, issuerDataLength) ?: ImmutableByteArray.empty()

    private val badDebtCounter: Int
        get() = data[BAD_DEBT_COUNTER_OFFSET].toInt() and 0xff

    private val keySetIndex: Int
        get() = data[KEY_SET_INDEX_OFFSET].toInt() and 0xff

    // Unsigned, 1E 84 80 is 2,000,000.
    val maxBalance: Int
        get() = data.byteArrayToInt(MAX_BALANCE_OFFSET, MAX_BALANCE_LEN)

    private val debitOptions: Byte
        get() = data[DEBIT_OPTIONS_OFFSET]

    private val certificate: ImmutableByteArray
        get() = data.sliceOffLen(CERTIFICATE_OFFSET, CERTIFICATE_LEN)

    private val counterData: ImmutableByteArray
        get() = data.sliceOffLen(COUNTER_DATA_OFFSET, COUNTER_DATA_LEN)

    val info: List<ListItemInterface>
        get() = listOf(
            ListItem(R.string.cepas_version, appletVersion.toString()),
            ListItem(R.string.cepas_purse_status, status.toString()),
            ListItem(R.string.cepas_purse_balance,
                TransitCurrency.IDR(balance).formatCurrencyString(true)),
            ListItem(R.string.maximum_balance,
                TransitCurrency.IDR(maxBalance).formatCurrencyString(true)),
            ListItem(R.string.cepas_purse_creation_date,
                TimestampFormatter.longDateFormat(creationDate)),
            ListItem(R.string.expiry_date, TimestampFormatter.longDateFormat(expiryDate)),
            ListItem(R.string.cepas_autoload_amount, autoLoadAmount.toString()),
            ListItem(R.string.cepas_can, can.toHexDump()),
            ListItem(R.string.cepas_csn, csn.toHexDump()),
            ListItem(R.string.bsb_cash_key_set_index, keySetIndex.toString()),
            ListItem(R.string.bsb_cash_bad_debt_counter, badDebtCounter.toString()),

            HeaderListItem(R.string.cepas_last_txn_info),
            ListItem(R.string.cepas_trp, lastTrp.toString()),
            ListItem(R.string.cepas_credit_trp, lastCreditTrp.toString()),
            ListItem(R.string.cepas_credit_header, lastCreditHeader.toHexDump()),
            ListItem(R.string.bsb_cash_last_txn_record, lastRecord.toHexDump()),
            ListItem(R.string.cepas_debit_options, debitOptions.toString()),
            ListItem(R.string.bsb_cash_signed_certificate, certificate.toHexDump()),
            ListItem(R.string.bsb_cash_counter_data, counterData.toHexDump()),

            HeaderListItem(R.string.cepas_other_purse_info),
            ListItem(R.string.cepas_logfile_record_count, logRecordCount.toString()),
            ListItem(R.string.cepas_issuer_data_length, issuerDataLength.toString()),
            ListItem(R.string.cepas_issuer_data, issuerData.toHexDump())
        )

    companion object {
        // ASSUMPTION (not verified against the official app): dates are counted in days from
        // 1995-01-01, as in CEPAS. A scanned card gives creation 2022-03-18 and expiry
        // 2030-05-22, which is a plausible 8 year validity. The creation date also falls after
        // the launch of BSB Cash on 2017-04-06, as expected.
        private val EPOCH = Epoch.local(1995, MetroTimeZone.JAKARTA)

        // Offset of the applet version.
        private const val APPLET_VERSION_OFFSET = 0

        // Offset of the purse status.
        private const val STATUS_OFFSET = 1

        // Offset of the balance.
        private const val BALANCE_OFFSET = 2

        // Length of the balance.
        private const val BALANCE_LEN = 3

        // Offset of the automatic top-up amount.
        private const val AUTO_LOAD_OFFSET = 5

        // Length of the automatic top-up amount.
        private const val AUTO_LOAD_LEN = 3

        // Offset of the card number (CAN).
        private const val CAN_OFFSET = 8

        // Length of the card number (CAN).
        private const val CAN_LEN = 8

        // The card number starts with this prefix.
        private val CAN_PREFIX = ImmutableByteArray.fromHex("7546")

        // Offset of the card serial number (CSN).
        private const val CSN_OFFSET = 16

        // Length of the card serial number (CSN).
        private const val CSN_LEN = 8

        // Offset of the purse expiry date.
        private const val EXPIRY_DATE_OFFSET = 24

        // Offset of the purse creation date.
        private const val CREATION_DATE_OFFSET = 26

        // Length of a date field.
        private const val DATE_LEN = 2

        // Offset of the transaction record pointer (TRP) of the last credit.
        private const val LAST_CREDIT_TRP_OFFSET = 28

        // Length of a transaction record pointer (TRP).
        private const val TRP_LEN = 4

        // Offset of the header of the last credit transaction.
        private const val LAST_CREDIT_HEADER_OFFSET = 32

        // Length of the header of the last credit transaction.
        private const val LAST_CREDIT_HEADER_LEN = 8

        // Offset of the number of records in the transaction log.
        private const val LOG_RECORD_COUNT_OFFSET = 40

        // Offset of the transaction record pointer (TRP) of the last transaction.
        private const val LAST_TRP_OFFSET = 42

        // Offset of the last transaction record.
        private const val LAST_RECORD_OFFSET = 46

        // Length of the last transaction record.
        private const val LAST_RECORD_LEN = 16

        // Offset of the length of the issuer specific data.
        private const val ISSUER_DATA_LENGTH_OFFSET = 41

        // Offset of the issuer specific data, right after the last transaction record.
        private const val ISSUER_DATA_OFFSET = 62

        // Offset of the bad debt counter.
        private const val BAD_DEBT_COUNTER_OFFSET = 63

        // Offset of the key set index.
        private const val KEY_SET_INDEX_OFFSET = 71

        // Offset of the maximum balance.
        private const val MAX_BALANCE_OFFSET = 78

        // Length of the maximum balance.
        private const val MAX_BALANCE_LEN = 3

        // Offset of the debit options of the last transaction.
        private const val DEBIT_OPTIONS_OFFSET = 94

        // Offset of the signed certificate of the last transaction.
        private const val CERTIFICATE_OFFSET = 95

        // Length of the signed certificate.
        private const val CERTIFICATE_LEN = 8

        // Offset of the counter data.
        private const val COUNTER_DATA_OFFSET = 103

        // Length of the counter data.
        private const val COUNTER_DATA_LEN = 8

        // Number of bytes needed to read every field (the counter data is the last one).
        private const val MIN_LEN = COUNTER_DATA_OFFSET + COUNTER_DATA_LEN

        /**
         * Checks that [data] is long enough and holds a card number of this scheme, so that other
         * cards answering the same commands are not mistaken for BSB Cash.
         */
        fun isValid(data: ImmutableByteArray) =
            data.size >= MIN_LEN && data.sliceOffLen(CAN_OFFSET, CAN_PREFIX.size) == CAN_PREFIX
    }
}
