/*
 * TapCashApplication.kt
 *
 * BNI TapCash ISO7816 application.
 */

package au.id.micolous.metrodroid.card.tapcash

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.ISO7816Application
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816ApplicationMutableCapsule
import au.id.micolous.metrodroid.card.iso7816.ISO7816Card
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.tapcash.TapCashTransitFactory
import au.id.micolous.metrodroid.ui.ListItemInterface
import au.id.micolous.metrodroid.ui.ListItemRecursive
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.Serializable

@Serializable
data class TapCashApplication(
    override val generic: ISO7816ApplicationCapsule,
    private val purse: ImmutableByteArray,
    private val records: Map<Int, ImmutableByteArray>
) : ISO7816Application() {

    override val type: String
        get() = TYPE

    override val rawData: List<ListItemInterface>?
        get() =
            listOf(
                ListItemRecursive.collapsedValue(
                    "TapCash Purse",
                    purse.toHexDump()
                )
            ) +
                    records.map { (index, data) ->
                        ListItemRecursive.collapsedValue(
                            "TapCash Record $index",
                            data.toHexDump()
                        )
                    }

    fun getPurse(): ImmutableByteArray = purse

    fun getRecord(index: Int): ImmutableByteArray? =
        records[index]

    fun getRecords(): Map<Int, ImmutableByteArray> =
        records

    companion object {

        const val TYPE = "tapcash"

        private const val MAX_RECORDS = 16

        fun dumpTag(
            iso7816Tag: ISO7816Protocol,
            capsule: ISO7816ApplicationMutableCapsule,
            feedbackInterface: TagReaderFeedbackInterface
        ): TapCashApplication? {

            val protocol = TapCashProtocol(iso7816Tag)

            /*
             * Read purse.
             *
             * If this succeeds, we consider this a TapCash card.
             */
            val purse = protocol.getPurse()
                ?: return null

            feedbackInterface.updateStatusText(
                "Reading TapCash"
            )

            val records =
                mutableMapOf<Int, ImmutableByteArray>()

            /*
             * Read transaction records.
             *
             * For the first implementation we intentionally
             * read a fixed number of records.
             */
            for (index in 0 until MAX_RECORDS) {

                val record = protocol.getRecord(index)

                if (record != null) {
                    records[index] = record
                }

                feedbackInterface.updateProgressBar(
                    index + 1,
                    MAX_RECORDS
                )
            }

            return TapCashApplication(
                generic = capsule.freeze(),
                purse = purse,
                records = records
            )
        }
    }

    override fun parseTransitIdentity(
        card: ISO7816Card
    ): TransitIdentity? {
        return if (TapCashTransitFactory.check(this)) {
            TapCashTransitFactory.parseTransitIdentity(this)
        } else {
            null
        }
    }

    override fun parseTransitData(
        card: ISO7816Card
    ): TransitData? {
        return if (TapCashTransitFactory.check(this)) {
            TapCashTransitFactory.parseTransitData(this)
        } else {
            null
        }
    }
}