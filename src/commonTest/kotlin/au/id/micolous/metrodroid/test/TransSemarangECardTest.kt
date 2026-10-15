/*
 * TransSemarangECardTest.kt
 *
 * Test parser Trans Semarang E-Card
 */

package au.id.micolous.metrodroid.test

import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.serializers.JsonKotlinFormat
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.trans_semarang_e_card.TransSemarangECardTransitData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class TransSemarangECardTest :
    CardReaderWithAssetDumpsTest<JsonKotlinFormat>(JsonKotlinFormat) {


    private fun checkCard(
        path: String,
        expectedBalance: Int,
        expectedSerial: String,
    ) {

        val card = loadCard<ClassicCard>(path)

        // Pastikan MIFARE Classic berhasil dibaca
        assertNotNull(
            card.mifareClassic,
            "MIFARE Classic data should exist"
        )


        // Test identitas kartu
        val identity =
            card.parseTransitIdentity()

        assertNotNull(
            identity,
            "Transit identity should not be null"
        )

        assertEquals(
            expected = "Trans Semarang",
            actual = identity.name
        )


        // Test parser utama
        val data =
            card.parseTransitData()


        assertTrue(
            actual = data is TransSemarangECardTransitData,
            message = "Card must be parsed as TransSemarangECardTransitData"
        )


        val transData =
            data as TransSemarangECardTransitData


        // Test saldo
        assertEquals(
            expected = TransitCurrency.IDR(expectedBalance),
            actual = transData.balances?.first()?.balance
        )


        // Test UID / serial
        assertEquals(
            expected = expectedSerial,
            actual = transData.serialNumber
        )

        // Test additional data
        assertNotNull(
            transData.info
        )
    }


    @Test
    fun testStudentCard() {

        checkCard(
            path = "trans_semarang_e_card/trans_semarang_student.json",

            expectedBalance = 13000,

            expectedSerial = "D2EE172E",
        )
    }


    @Test
    fun testGeneralCard1() {

        checkCard(
            path = "trans_semarang_e_card/trans_semarang_general_card1.json",

            expectedBalance = 3500,

            expectedSerial = "B186230C",
        )
    }

    @Test
    fun testGeneralCard2() {

        checkCard(
            path = "trans_semarang_e_card/trans_semarang_general_card2.json",

            expectedBalance = 10000,

            expectedSerial = "2BD77CD1",
        )
    }
}