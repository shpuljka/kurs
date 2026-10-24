package io.github.shpuljka.kurs.data

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Currency
import java.util.Locale

/** Country without location permission: network → SIM → system locale, spec.md §3. */
object CountryDetector {

    fun country(context: Context): String? {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return sequenceOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            .map { it?.uppercase(Locale.ROOT) }
            .firstOrNull { !it.isNullOrBlank() && it.length == 2 }
    }

    fun currencyOf(country: String): String? =
        runCatching { Currency.getInstance(Locale.Builder().setRegion(country).build()).currencyCode }.getOrNull()
}
