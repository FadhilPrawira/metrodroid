package au.id.micolous.metrodroid.card.bank_jakarta_jakcard

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationFactory
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationMutableCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Card
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.card.iso7816.ISO7816TLV
import au.id.micolous.metrodroid.card.iso7816.ISOEOFException
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.bank_jakarta_jakcard.JakcardTransitData
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.ui.ListItemRecursive
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Implements the JakCard (Bank DKI) ISO 7816 application.
 *
 * The card number comes from the purse information (tag B0) in the FCI of the SELECT response.
 * The balance uses one proprietary instruction (CLA 0x90, INS 0x4C), and the transactions are
 * stored in two record files: a combined log of payments and top-ups, and a top-up only log
 * that keeps older top-ups after they were overwritten in the combined log.
 */
@Serializable
data class JakcardApplication(
    override val generic: ISO7816ApplicationCapsule,
    // Raw response of 90 4C 00 00 04. Holds the balance.
    val balanceData: ImmutableByteArray,
    // Raw records of the combined payment and top-up log (SFI 4), latest first.
    val log: List<ImmutableByteArray> = emptyList(),
    // Raw records of the top-up only log (SFI 5), latest first.
    val topupLog: List<ImmutableByteArray> = emptyList()
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    // Purse information (tag B0) of the FCI; null if the FCI does not have it.
    val purseInfo: ImmutableByteArray?
        get() = generic.appFci?.let { ISO7816TLV.findBERTLV(it, TAG_PURSE_INFO, false) }

    val serial: String?
        get() = purseInfo?.takeIf { it.size >= IDEP_OFFSET + IDEP_LEN }
            ?.getHexString(IDEP_OFFSET, IDEP_LEN)?.chunked(4)?.joinToString(" ")

    override fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? =
        JakcardTransitData.FACTORY.parseTransitIdentity(this)

    override fun parseTransitData(card: ISO7816Card): TransitData? =
        JakcardTransitData.FACTORY.parseTransitData(this)

    override val rawData: List<ListItemInterface>?
        get() = listOf(ListItemRecursive.collapsedValue("90 4C (balance)", balanceData.toHexDump())) +
                log.mapIndexed { i, item ->
                    ListItemRecursive.collapsedValue("log ${i + 1}", item.toHexDump())
                } +
                topupLog.mapIndexed { i, item ->
                    ListItemRecursive.collapsedValue("top-up log ${i + 1}", item.toHexDump())
                }

    companion object {

        // Tag used for log messages.
        private const val TAG = "JakcardApplication"

        // Type name under which this application is serialized in card dumps.
        const val TYPE = "jakcard"

        // Application ID of JakCard; the last three bytes are "NJC" in ASCII.
        private val APP_NAME = listOf(ImmutableByteArray.fromHex("A0000005714E4A43"))

        // Tag of the purse information inside the FCI.
        private val TAG_PURSE_INFO = ImmutableByteArray.fromHex("B0")

        // Offset of the card number (IDEP) in the purse information.
        const val IDEP_OFFSET = 4

        // Length of the card number (IDEP) in the purse information.
        const val IDEP_LEN = 8

        // Proprietary instruction: get the balance.
        private const val INS_GET_BALANCE: Byte = 0x4C

        // Length of the balance response.
        private const val BALANCE_LEN = 4

        // File (SFI) of the combined payment and top-up log.
        private const val SFI_LOG = 4

        // File (SFI) of the top-up only log.
        private const val SFI_TOPUP_LOG = 5

        // Size of one log record; a shorter read would cut off the transaction time.
        const val RECORD_LEN = 0x2E

        // Upper limit of records to read per file; the known cards hold 12 in the combined log.
        private const val LOG_MAX_RECORDS = 16

        val FACTORY: ISO7816ApplicationFactory = object : ISO7816ApplicationFactory {
            override val typeMap: Map<String, KSerializer<out ISO7816Application>>
                get() = mapOf(TYPE to serializer())

            override val applicationNames: Collection<ImmutableByteArray>
                get() = APP_NAME

            override fun dumpTag(
                protocol: ISO7816Protocol,
                capsule: ISO7816ApplicationMutableCapsule,
                feedbackInterface: TagReaderFeedbackInterface,
                presentAids: List<ImmutableByteArray?>
            ): List<ISO7816Application>? {
                val balanceData: ImmutableByteArray

                try {
                    feedbackInterface.updateProgressBar(0, 2)
                    balanceData = protocol.sendRequest(
                        ISO7816Protocol.CLASS_90, INS_GET_BALANCE,
                        0.toByte(), 0.toByte(), BALANCE_LEN.toByte()
                    )

                    if (balanceData.size < BALANCE_LEN) {
                        Log.w(TAG, "Unexpected response length, not a JakCard")
                        return null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Got exception $e")
                    return null
                }

                feedbackInterface.updateProgressBar(1, 2)
                val log = readLog(protocol, SFI_LOG)
                feedbackInterface.updateProgressBar(2, 2)
                val topupLog = readLog(protocol, SFI_TOPUP_LOG)

                return listOf(JakcardApplication(capsule.freeze(), balanceData, log, topupLog))
            }

            // Reads the records of a file until the card says there are no more.
            private fun readLog(protocol: ISO7816Protocol, sfi: Int): List<ImmutableByteArray> {
                val records = mutableListOf<ImmutableByteArray>()
                for (n in 1..LOG_MAX_RECORDS) {
                    try {
                        records.add(protocol.readRecord(sfi, n.toByte(), RECORD_LEN.toByte()) ?: break)
                    } catch (e: ISOEOFException) {
                        // The card answers "record not found" (6A 83) after the last record.
                        break
                    }
                }
                return records
            }
        }
    }
}
