package au.id.micolous.metrodroid.card.bank_mandiri_emoney

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
import au.id.micolous.metrodroid.transit.bank_mandiri_emoney.MandiriEmoneyTransitData
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.ui.ListItemRecursive
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Implements the e-Money Mandiri ISO 7816 application.
 *
 * Uses proprietary instructions:
 * - INS 0xB3 (card info)
 * - INS 0xB5 (balance)
 * - INS 0xD1 (transaction history, new-generation cards)
 * - INS 0xB2 (transaction history, old-generation cards)
 */
@Serializable
data class MandiriEmoneyApplication(
    override val generic: ISO7816ApplicationCapsule,
    // Raw response of INS=B3. The first 8 bytes are the card number.
    val cardInfo: ImmutableByteArray,
    // Raw response of INS=B5. The first 4 bytes are the balance (little-endian).
    val balance: ImmutableByteArray,
    val history: List<ImmutableByteArray> = emptyList()
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    val serial: String
        get() = cardInfo.getHexString(0, CARD_NUMBER_LEN).chunked(4).joinToString(" ")

    override fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? =
        MandiriEmoneyTransitData.FACTORY.parseTransitIdentity(this)

    override fun parseTransitData(card: ISO7816Card): TransitData? =
        MandiriEmoneyTransitData.FACTORY.parseTransitData(this)

    /** Generation flag, byte 18 of the card info response (0x03 = old, 0x83 = new). */
    val generation: Int?
        get() = if (cardInfo.size > GENERATION_FLAG_OFFSET)
            cardInfo[GENERATION_FLAG_OFFSET].toInt() and 0xff else null

    override val rawData: List<ListItemInterface>?
        get() {
            val isOld = generation == 0x03
            return listOf(
                ListItemRecursive.collapsedValue("B3 (card info)", cardInfo.toHexDump()),
                ListItemRecursive.collapsedValue("B5 (balance)", balance.toHexDump())
            ) + history.mapIndexed { i, item ->
                if (isOld) ListItemRecursive.collapsedValue("B2 record ${i + 1}", item.toHexDump())
                else ListItemRecursive.collapsedValue("D1 page $i", item.toHexDump())
            }
        }

    companion object {
        // Tag used for log messages.
        private const val TAG = "MandiriEmoneyApplication"

        // Type name under which this application is serialized in card dumps.
        const val TYPE = "mandiri_emoney"

        // Application ID selected before sending the proprietary commands.
        private val APP_NAME = listOf(ImmutableByteArray.fromHex("0000000000000001"))

        // Number of bytes requested (Le) from the balance command.
        private const val BALANCE_RESP_LEN: Byte = 0x0A

        // Minimum number of bytes needed to extract the balance.
        private const val BALANCE_MIN_LEN = 4

        // Number of bytes requested (Le) from the card info command.
        private const val CARD_INFO_RESP_LEN: Byte = 0x3F

        // Minimum number of bytes needed to extract the card number.
        private const val CARD_NUMBER_LEN = 8

        // Proprietary instruction: read the balance.
        private const val INS_GET_BALANCE: Byte = 0xB5.toByte()

        // Proprietary instruction: read a page of the transaction history (new-generation cards).
        private const val INS_GET_HISTORY: Byte = 0xD1.toByte()

        // Proprietary instruction: read the card info.
        private const val INS_GET_CARD_INFO: Byte = 0xB3.toByte()

        // Standard READ RECORD instruction: read one history record (old-generation cards).
        private const val INS_READ_RECORD: Byte = 0xB2.toByte()

        // Offset of the generation flag in the card info response.
        private const val GENERATION_FLAG_OFFSET = 18

        // Generation flag value of old-generation cards (APP 3.1).
        private const val GENERATION_OLD: Byte = 0x03

        // Generation flag value of new-generation cards (APP 4.0).
        private const val GENERATION_NEW: Byte = 0x83.toByte()

        // Index of the last history page on new-generation cards; the cards return an error after it.
        private const val HISTORY_LAST_PAGE = 4

        // Number of history records read on old-generation cards; the real capacity is not verified.
        private const val OLD_HISTORY_LAST_RECORD = 10

        // Number of bytes requested (Le) per history record on old-generation cards.
        private const val OLD_HISTORY_RECORD_LEN: Byte = 0x28

        private fun readHistory(
            protocol: ISO7816Protocol,
            generation: Byte?
        ): List<ImmutableByteArray> {
            val history = mutableListOf<ImmutableByteArray>()
            when (generation) {
                GENERATION_NEW -> for (page in 0..HISTORY_LAST_PAGE) {
                    try {
                        history.add(
                            protocol.sendRequest(
                                ISO7816Protocol.CLASS_ISO7816, INS_GET_HISTORY,
                                page.toByte(), 0, 0
                            )
                        )
                    } catch (e: ISO7816Exception) {
                        Log.w(TAG, "history page $page stopped", e)
                        break
                    }
                }

                GENERATION_OLD -> for (record in 1..OLD_HISTORY_LAST_RECORD) {
                    try {
                        history.add(
                            protocol.sendRequest(
                                ISO7816Protocol.CLASS_ISO7816, INS_READ_RECORD,
                                record.toByte(), 0, OLD_HISTORY_RECORD_LEN
                            )
                        )
                    } catch (e: ISO7816Exception) {
                        Log.w(TAG, "history record $record stopped", e)
                        break
                    }
                }
            }
            return history
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
                val cardInfo: ImmutableByteArray
                val balance: ImmutableByteArray

                try {
                    feedbackInterface.updateProgressBar(0, 2)
                    cardInfo = protocol.sendRequest(
                        ISO7816Protocol.CLASS_ISO7816, INS_GET_CARD_INFO,
                        0.toByte(), 0.toByte(), CARD_INFO_RESP_LEN
                    )

                    feedbackInterface.updateProgressBar(1, 2)
                    balance = protocol.sendRequest(
                        ISO7816Protocol.CLASS_ISO7816, INS_GET_BALANCE,
                        0.toByte(), 0.toByte(), BALANCE_RESP_LEN
                    )

                    // The AID is very generic; make sure this really looks like e-Money Mandiri.
                    if (cardInfo.size < CARD_NUMBER_LEN || balance.size < BALANCE_MIN_LEN) {
                        Log.w(TAG, "Unexpected response length, not an e-Money Mandiri card")
                        return null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Got exception $e")
                    return null
                }

                val generation = if (cardInfo.size > GENERATION_FLAG_OFFSET)
                    cardInfo[GENERATION_FLAG_OFFSET] else null
                val history = readHistory(protocol, generation)

                return listOf(
                    MandiriEmoneyApplication(
                        capsule.freeze(),
                        cardInfo,
                        balance,
                        history
                    )
                )
            }
        }
    }
}
