package au.id.micolous.metrodroid.card.bni_tapcash

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
import au.id.micolous.metrodroid.transit.bni_tapcash.BniTapcashTransitData
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.ui.ListItemRecursive
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Implements the BNI TapCash ISO 7816 application.
 *
 * Uses one proprietary instruction (CLA 0x90, INS 0x32) both for the purse data (balance and
 * card number) and for the transaction log, which has 16 slots of 16 bytes.
 */
@Serializable
data class BniTapcashApplication(
    override val generic: ISO7816ApplicationCapsule,
    // Raw response of 90 32 03 00 00. Holds the balance and the card number.
    val purse: ImmutableByteArray,
    // Raw log records (RECORD_LEN bytes each); empty slots are included.
    val records: List<ImmutableByteArray> = emptyList()
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    val serial: String
        get() = purse.getHexString(CAN_OFFSET, CAN_LEN).chunked(4).joinToString(" ")

    override fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? =
        BniTapcashTransitData.FACTORY.parseTransitIdentity(this)

    override fun parseTransitData(card: ISO7816Card): TransitData? =
        BniTapcashTransitData.FACTORY.parseTransitData(this)

    override val rawData: List<ListItemInterface>?
        get() = listOf(ListItemRecursive.collapsedValue("90 32 (purse)", purse.toHexDump())) +
                records.mapIndexed { i, item ->
                    ListItemRecursive.collapsedValue("record $i", item.toHexDump())
                }

    companion object {

        // Tag used for log messages.
        private const val TAG = "BniTapcashApplication"

        // Type name under which this application is serialized in card dumps.
        const val TYPE = "bni_tapcash"

        // Application ID selected before sending the proprietary commands; it contains "BNI".
        private val APP_NAME = listOf(ImmutableByteArray.fromHex("A000424E49100001"))

        // Proprietary instruction: read the purse data or one log record.
        private const val INS_GET_DATA: Byte = 0x32

        // P1 of every proprietary command; other values are rejected by the card.
        private const val GET_DATA_P1: Byte = 0x03

        // Minimum number of bytes needed to extract the card number.
        private const val PURSE_MIN_LEN = 16

        // Offset of the card number (CAN) in the purse response.
        const val CAN_OFFSET = 8

        // Length of the card number (CAN) in the purse response.
        const val CAN_LEN = 8

        // Number of slots in the transaction log.
        private const val LOG_SLOTS = 16

        // Size of one log record.
        const val RECORD_LEN = 16

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
                val purse: ImmutableByteArray

                try {
                    feedbackInterface.updateProgressBar(0, LOG_SLOTS + 1)
                    purse = protocol.sendRequest(
                        ISO7816Protocol.CLASS_90, INS_GET_DATA,
                        GET_DATA_P1, 0.toByte(), 0.toByte()
                    )

                    if (purse.size < PURSE_MIN_LEN) {
                        Log.w(TAG, "Unexpected response length, not a BNI TapCash card")
                        return null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Got exception $e")
                    return null
                }

                val records = mutableListOf<ImmutableByteArray>()
                for (slot in 0 until LOG_SLOTS) {
                    feedbackInterface.updateProgressBar(slot + 1, LOG_SLOTS + 1)
                    try {
                        records.add(
                            protocol.sendRequest(
                                ISO7816Protocol.CLASS_90, INS_GET_DATA,
                                GET_DATA_P1, 0.toByte(), RECORD_LEN.toByte(),
                                // The slot number is sent as the command data.
                                ImmutableByteArray.of(slot.toByte())
                            )
                        )
                    } catch (e: ISO7816Exception) {
                        Log.w(TAG, "record $slot stopped", e)
                        break
                    }
                }

                return listOf(
                    BniTapcashApplication(
                        capsule.freeze(),
                        purse,
                        records
                    )
                )
            }
        }
    }
}