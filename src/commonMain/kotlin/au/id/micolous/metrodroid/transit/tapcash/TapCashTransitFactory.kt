package au.id.micolous.metrodroid.transit.tapcash

import au.id.micolous.metrodroid.card.tapcash.TapCashApplication
import au.id.micolous.metrodroid.transit.CardTransitFactory
import au.id.micolous.metrodroid.transit.TransitIdentity

private const val NAME = "BNI TapCash"

object TapCashTransitFactory : CardTransitFactory<TapCashApplication> {

    override fun check(card: TapCashApplication): Boolean {
        return TapCashPurse.parse(card.getPurse()) != null
    }

    override fun parseTransitIdentity(
        card: TapCashApplication
    ): TransitIdentity {
        val purse = TapCashPurse.parse(card.getPurse())

        return TransitIdentity(
            NAME,
            purse?.can
        )
    }

    override fun parseTransitData(
        card: TapCashApplication
    ): TapCashTransitData {
        val purse = TapCashPurse.parse(card.getPurse())
            ?: throw IllegalArgumentException("Invalid TapCash purse data")

        return TapCashTransitData(
            mBalance = purse.balance,
            mSerialNumber = purse.can
        )
    }
}