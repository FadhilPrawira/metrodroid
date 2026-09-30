package au.id.micolous.metrodroid.card.tapcash

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationFactory
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationMutableCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer

object TapCashApplicationFactory : ISO7816ApplicationFactory {

    /**
     * BNI TapCash application AID.
     *
     * Captured from the physical card:
     *
     * 00 A4 04 00 08 A0 00 42 4E 49 10 00 01
     *
     * AID:
     *
     * A0 00 42 4E 49 10 00 01
     */
    override val applicationNames: Collection<ImmutableByteArray> =
        listOf(
            ImmutableByteArray.of(
                0xA0.toByte(),
                0x00.toByte(),
                0x42.toByte(),
                0x4E.toByte(),
                0x49.toByte(),
                0x10.toByte(),
                0x00.toByte(),
                0x01.toByte()
            )
        )

    override fun dumpTag(
        protocol: ISO7816Protocol,
        capsule: ISO7816ApplicationMutableCapsule,
        feedbackInterface: TagReaderFeedbackInterface,
        presentAids: List<ImmutableByteArray?>
    ): List<ISO7816Application>? {

        val application = TapCashApplication.dumpTag(
            protocol,
            capsule,
            feedbackInterface
        ) ?: return null

        return listOf(application)
    }

    override val typeMap:
            Map<String, KSerializer<out ISO7816Application>> =
        mapOf(
            TapCashApplication.TYPE to
                    TapCashApplication.serializer()
        )
}