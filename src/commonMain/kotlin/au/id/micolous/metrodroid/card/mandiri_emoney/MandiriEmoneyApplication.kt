package au.id.micolous.metrodroid.card.mandiri_emoney

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationFactory
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationMutableCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Card
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.mandiri_emoney.MandiriEmoneyTransitData
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Implements the e-Money Mandiri ISO 7816 application.
 *
 * Uses proprietary instructions: INS 0xB3 (card info) and INS 0xB5 (balance).
 */
@Serializable
data class MandiriEmoneyApplication(
    override val generic: ISO7816ApplicationCapsule,
    // Raw response of INS=B3. The first 8 bytes are the card number.
    val cardInfo: ImmutableByteArray,
    // Raw response of INS=B5. The first 4 bytes are the balance (little-endian).
    val balance: ImmutableByteArray
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    val serial: String
        get() = cardInfo.getHexString(0, CARD_NUMBER_LEN).chunked(4).joinToString(" ")

    override fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? =
        TransitIdentity(MandiriEmoneyTransitData.NAME, serial)

    override fun parseTransitData(card: ISO7816Card): TransitData? =
        MandiriEmoneyTransitData(this)

    companion object {
        private const val TAG = "MandiriEmoneyApplication"
        const val TYPE = "mandiri_emoney"

        private val APP_NAME = listOf(ImmutableByteArray.fromHex("0000000000000001"))

        private const val INS_GET_CARD_INFO: Byte = 0xB3.toByte()
        private const val INS_GET_BALANCE: Byte = 0xB5.toByte()
        private const val CARD_INFO_RESP_LEN: Byte = 0x3F
        private const val BALANCE_RESP_LEN: Byte = 0x0A

        // Minimum number of bytes needed to extract the card number and the balance.
        private const val CARD_NUMBER_LEN = 8
        private const val BALANCE_MIN_LEN = 4

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
                        0.toByte(), 0.toByte(), CARD_INFO_RESP_LEN)

                    feedbackInterface.updateProgressBar(1, 2)
                    balance = protocol.sendRequest(
                        ISO7816Protocol.CLASS_ISO7816, INS_GET_BALANCE,
                        0.toByte(), 0.toByte(), BALANCE_RESP_LEN)

                    // The AID is very generic; make sure this really looks like e-Money Mandiri.
                    if (cardInfo.size < CARD_NUMBER_LEN || balance.size < BALANCE_MIN_LEN) {
                        Log.w(TAG, "Unexpected response length, not an e-Money Mandiri card")
                        return null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Got exception $e")
                    return null
                }

                return listOf(MandiriEmoneyApplication(capsule.freeze(), cardInfo, balance))
            }
        }
    }
}