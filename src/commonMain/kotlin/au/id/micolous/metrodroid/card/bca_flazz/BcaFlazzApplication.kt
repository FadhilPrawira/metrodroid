package au.id.micolous.metrodroid.card.bca_flazz

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationFactory
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationMutableCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Card
import au.id.micolous.metrodroid.card.iso7816.ISO7816Exception
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.bca_flazz.BcaFlazzTransitData
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.ui.ListItemRecursive
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Implements the BCA Flazz (Gen 2) ISO 7816 application.
 *
 * Uses one proprietary instruction (CLA 0x90, INS 0x32) both for the purse data (balance and
 * card number) and for the transaction history, which has 32-byte records for payments and
 * top-ups. Some cards also keep older payments in a legacy payment log (SFI 4) that this
 * instruction does not return, so that file is read as well. Only data readable without
 * authentication is read.
 */
@Serializable
data class BcaFlazzApplication(
    override val generic: ISO7816ApplicationCapsule,
    // Raw response of 90 32 03 00 00. Holds the balance and the card number.
    val balance: ImmutableByteArray,
    // Raw transaction records (RECORD_LEN bytes each), latest first.
    val records: List<ImmutableByteArray> = emptyList(),
    // Raw slots of the legacy payment log (SFI 4), PAYMENT_SLOT_LEN bytes each.
    val payments: List<ImmutableByteArray> = emptyList()
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    val serial: String
        get() = balance.getHexString(CARD_NUMBER_OFFSET, CARD_NUMBER_LEN).chunked(4).joinToString(" ")

    override fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? =
        BcaFlazzTransitData.FACTORY.parseTransitIdentity(this)

    override fun parseTransitData(card: ISO7816Card): TransitData? =
        BcaFlazzTransitData.FACTORY.parseTransitData(this)

    override val rawData: List<ListItemInterface>?
        get() = listOf(ListItemRecursive.collapsedValue("90 32 (balance)", balance.toHexDump())) +
                records.mapIndexed { i, item ->
                    ListItemRecursive.collapsedValue("record $i", item.toHexDump())
                } +
                payments.mapIndexed { i, item ->
                    ListItemRecursive.collapsedValue("SFI 4 slot $i", item.toHexDump())
                }

    companion object {

        // Tag used for log messages.
        private const val TAG = "BcaFlazzApplication"

        // Type name under which this application is serialized in card dumps.
        const val TYPE = "bca_flazz"

        // Application ID selected before sending the proprietary commands.
        private val APP_NAME = listOf(ImmutableByteArray.fromHex("A0000000180F0000018001"))

        // File ID of the DF that must be selected before reading; Gen 1 cards reject it.
        private const val DF_ID = 0x0200

        // Proprietary instruction: read the purse data or one transaction record.
        private const val INS_GET_DATA: Byte = 0x32

        // P1 of every proprietary command; other values are rejected by the card.
        private const val GET_DATA_P1: Byte = 0x03

        // Minimum number of bytes needed to extract the card number.
        private const val BALANCE_MIN_LEN = 16

        // Offset of the card number (BCD) in the balance response.
        const val CARD_NUMBER_OFFSET = 8

        // Length of the card number in the balance response.
        const val CARD_NUMBER_LEN = 8

        // Size of one transaction record.
        const val RECORD_LEN = 0x20

        // The record index is sent as a single byte, so there can be at most this many records.
        private const val RECORD_MAX_COUNT = 256

        // High bit of P1 that selects a file by its short file identifier in READ BINARY.
        private const val SFI_FLAG = 0x80

        // Short file identifier of the legacy payment log.
        private const val SFI_PAYMENTS = 4

        // Number of slots in the legacy payment log; the file is 600 bytes.
        private const val PAYMENT_SLOTS = 10

        // Size of one slot of the legacy payment log.
        const val PAYMENT_SLOT_LEN = 60

        // READ BINARY counts P2 in units of 4 bytes on these cards, not in single bytes.
        private const val OFFSET_UNIT = 4

        // Reads the legacy payment log slot by slot; empty slots are included.
        private fun readPayments(
            protocol: ISO7816Protocol,
            feedbackInterface: TagReaderFeedbackInterface
        ): List<ImmutableByteArray> {
            val result = mutableListOf<ImmutableByteArray>()
            for (slot in 0 until PAYMENT_SLOTS) {
                feedbackInterface.updateProgressBar(RECORD_MAX_COUNT + slot, RECORD_MAX_COUNT + PAYMENT_SLOTS)
                try {
                    result.add(
                        protocol.sendRequest(
                            ISO7816Protocol.CLASS_ISO7816,
                            ISO7816Protocol.INSTRUCTION_ISO7816_READ_BINARY,
                            (SFI_FLAG or SFI_PAYMENTS).toByte(),
                            (slot * PAYMENT_SLOT_LEN / OFFSET_UNIT).toByte(),
                            PAYMENT_SLOT_LEN.toByte()
                        )
                    )
                } catch (e: ISO7816Exception) {
                    Log.w(TAG, "SFI $SFI_PAYMENTS slot $slot stopped", e)
                    break
                }
            }
            return result
        }

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
                val balance: ImmutableByteArray

                try {
                    feedbackInterface.updateProgressBar(0, RECORD_MAX_COUNT)
                    // Gen 2 cards accept this; Gen 1 cards answer with an error (not supported).
                    protocol.selectById(DF_ID)

                    balance = protocol.sendRequest(
                        ISO7816Protocol.CLASS_90, INS_GET_DATA,
                        GET_DATA_P1, 0.toByte(), 0.toByte()
                    )

                    // The AID prefix is shared with other cards; make sure this looks like a Flazz.
                    if (balance.size < BALANCE_MIN_LEN) {
                        Log.w(TAG, "Unexpected response length, not a BCA Flazz card")
                        return null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Got exception $e")
                    return null
                }

                // The card has no record count; it answers with an error after the last record.
                val records = mutableListOf<ImmutableByteArray>()
                for (index in 0 until RECORD_MAX_COUNT) {
                    feedbackInterface.updateProgressBar(index + 1, RECORD_MAX_COUNT)
                    try {
                        records.add(
                            protocol.sendRequest(
                                ISO7816Protocol.CLASS_90, INS_GET_DATA,
                                GET_DATA_P1, 0.toByte(), RECORD_LEN.toByte(),
                                // The record index is sent as the command data.
                                ImmutableByteArray.of(index.toByte())
                            )
                        )
                    } catch (e: ISO7816Exception) {
                        Log.d(TAG, "record $index stopped", e)
                        break
                    }
                }

                val payments = readPayments(protocol, feedbackInterface)

                return listOf(BcaFlazzApplication(capsule.freeze(), balance, records, payments))
            }
        }
    }
}