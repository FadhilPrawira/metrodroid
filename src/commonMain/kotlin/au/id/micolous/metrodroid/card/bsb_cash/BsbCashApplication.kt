package au.id.micolous.metrodroid.card.bsb_cash

import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationMutableCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Card
import au.id.micolous.metrodroid.card.iso7816.ISO7816Exception
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.bsb_cash.BsbCashTransitData
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.ui.ListItemRecursive
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.Serializable

/**
 * Implements BSB Cash (Bank Sumsel Babel).
 *
 * The card answers its commands without any application selected, so it has no AID. It has to
 * be probed before an application is selected by AID, in the same way as CEPAS. The purse data
 * is laid out like a CEPAS purse. Only data readable without authentication is read.
 */
@Serializable
data class BsbCashApplication(
    override val generic: ISO7816ApplicationCapsule,
    // Raw response of 90 32 03 00 0A: the purse data. It holds the balance and the card number.
    val purse: ImmutableByteArray
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    val serial: String
        get() = purse.getHexString(CARD_NUMBER_OFFSET, CARD_NUMBER_LEN).chunked(4).joinToString(" ")

    override fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? =
        BsbCashTransitData.FACTORY.parseTransitIdentity(this)

    override fun parseTransitData(card: ISO7816Card): TransitData? =
        BsbCashTransitData.FACTORY.parseTransitData(this)

    override val rawData: List<ListItemInterface>?
        get() = listOf(ListItemRecursive.collapsedValue("90 32 (purse)", purse.toHexDump()))

    companion object {

        // Tag used for log messages.
        private const val TAG = "BsbCashApplication"

        // Type name under which this application is serialized in card dumps.
        const val TYPE = "bsb_cash"

        // Standard GET CHALLENGE instruction.
        private const val INS_GET_CHALLENGE: Byte = 0x84.toByte()

        // Length of the challenge the card answers with; the official app ignores its value too.
        private const val CHALLENGE_LEN: Byte = 0x08

        // Proprietary instruction that returns the purse data.
        private const val INS_READ_PURSE: Byte = 0x32

        // P1 of the read purse command.
        private const val READ_PURSE_P1: Byte = 0x03

        // Fixed prefix of the data of the read purse command, as sent by the official app.
        private val READ_PURSE_PREFIX = ImmutableByteArray.fromHex("1202")

        // The official app fills the rest of the data with random bytes. The card accepts any
        // value, so a fixed one (the one that was verified on a physical card) is used.
        private val READ_PURSE_NONCE = ImmutableByteArray.fromHex("1122334455667788")

        // Offset of the card number (CAN) in the purse data.
        const val CARD_NUMBER_OFFSET = 8

        // Length of the card number in the purse data.
        const val CARD_NUMBER_LEN = 8

        // The card number starts with this prefix.
        private val CARD_NUMBER_PREFIX = ImmutableByteArray.fromHex("7546")

        // Offset of the maximum balance in the purse data.
        const val MAX_BALANCE_OFFSET = 78

        // Length of the maximum balance in the purse data.
        const val MAX_BALANCE_LEN = 3

        // Number of bytes needed to read the last field (maximum balance) of the purse data.
        private const val PURSE_MIN_LEN = MAX_BALANCE_OFFSET + MAX_BALANCE_LEN

        /**
         * Probes for BSB Cash. Returns null when the card in the field is not a BSB Cash card.
         */
        fun dumpTag(
            protocol: ISO7816Protocol,
            capsule: ISO7816ApplicationMutableCapsule
        ): BsbCashApplication? {
            val purse: ImmutableByteArray

            try {
                // The official app sends this first; the answer is not used.
                protocol.sendRequest(
                    ISO7816Protocol.CLASS_ISO7816, INS_GET_CHALLENGE,
                    0.toByte(), 0.toByte(), CHALLENGE_LEN
                )
                purse = protocol.sendRequest(
                    ISO7816Protocol.CLASS_90, INS_READ_PURSE,
                    READ_PURSE_P1, 0.toByte(), 0.toByte(),
                    READ_PURSE_PREFIX + READ_PURSE_NONCE
                )
            } catch (e: ISO7816Exception) {
                Log.d(TAG, "Not a BSB Cash card [$e] -- this is expected for other ISO7816 cards")
                return null
            }

            // Other cards could answer the commands too; check that this looks like BSB Cash.
            if (purse.size < PURSE_MIN_LEN ||
                purse.sliceOffLen(CARD_NUMBER_OFFSET, CARD_NUMBER_PREFIX.size) != CARD_NUMBER_PREFIX
            ) {
                Log.d(TAG, "Unexpected purse data, not a BSB Cash card")
                return null
            }

            return BsbCashApplication(capsule.freeze(), purse)
        }
    }
}