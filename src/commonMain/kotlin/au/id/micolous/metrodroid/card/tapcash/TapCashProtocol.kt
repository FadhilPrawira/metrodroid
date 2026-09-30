/*
 * TapCashProtocol.kt
 *
 * Protocol implementation for BNI TapCash.
 */

package au.id.micolous.metrodroid.card.tapcash

import au.id.micolous.metrodroid.card.iso7816.ISO7816Exception
import au.id.micolous.metrodroid.card.iso7816.ISO7816Protocol
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.util.ImmutableByteArray

class TapCashProtocol(
    private val mTagTech: ISO7816Protocol
) {

    companion object {
        private const val TAG = "TapCashProtocol"

        private const val PURSE_ID = 3
    }

    /**
     * Reads the TapCash purse.
     *
     * APDU:
     *
     * 90 32 03 00 00
     */
    fun getPurse(): ImmutableByteArray? {
        return try {
            mTagTech.sendRequest(
                ISO7816Protocol.CLASS_90,
                0x32.toByte(),
                PURSE_ID.toByte(),
                0.toByte(),
                0.toByte()
            ).ifEmpty { null }
        } catch (ex: ISO7816Exception) {
            Log.w(TAG, "Error reading TapCash purse", ex)
            null
        }
    }

    /**
     * Reads one TapCash transaction record.
     *
     * APDU:
     *
     * 90 32 03 00 01 XX 10
     *
     * where XX is the record index.
     */
    fun getRecord(index: Int): ImmutableByteArray? {
        require(index in 0..255)

        return try {
            mTagTech.sendRequest(
                ISO7816Protocol.CLASS_90,
                0x32.toByte(),
                PURSE_ID.toByte(),
                0.toByte(),
                0x10.toByte(),
                ImmutableByteArray.of(
                    0x01.toByte(),
                    index.toByte()
                )
            ).ifEmpty { null }
        } catch (ex: ISO7816Exception) {
            Log.w(TAG, "Error reading TapCash record $index", ex)
            null
        }
    }
}