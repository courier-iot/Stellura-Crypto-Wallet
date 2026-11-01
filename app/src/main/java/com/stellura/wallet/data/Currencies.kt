package com.stellura.wallet.data

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** 75 most-used fiat currencies. CAD is the default. */
object Currencies {
    val all: List<Pair<String, String>> = listOf(
        "CAD" to "Canadian Dollar", "USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound",
        "JPY" to "Japanese Yen", "CNY" to "Chinese Yuan", "AUD" to "Australian Dollar", "CHF" to "Swiss Franc",
        "HKD" to "Hong Kong Dollar", "SGD" to "Singapore Dollar", "SEK" to "Swedish Krona", "KRW" to "South Korean Won",
        "NOK" to "Norwegian Krone", "NZD" to "New Zealand Dollar", "INR" to "Indian Rupee", "MXN" to "Mexican Peso",
        "TWD" to "New Taiwan Dollar", "ZAR" to "South African Rand", "BRL" to "Brazilian Real", "DKK" to "Danish Krone",
        "PLN" to "Polish Złoty", "THB" to "Thai Baht", "ILS" to "Israeli New Shekel", "IDR" to "Indonesian Rupiah",
        "CZK" to "Czech Koruna", "AED" to "UAE Dirham", "TRY" to "Turkish Lira", "HUF" to "Hungarian Forint",
        "CLP" to "Chilean Peso", "SAR" to "Saudi Riyal", "PHP" to "Philippine Peso", "MYR" to "Malaysian Ringgit",
        "COP" to "Colombian Peso", "RUB" to "Russian Ruble", "RON" to "Romanian Leu", "PEN" to "Peruvian Sol",
        "BHD" to "Bahraini Dinar", "BGN" to "Bulgarian Lev", "ARS" to "Argentine Peso", "KWD" to "Kuwaiti Dinar",
        "VND" to "Vietnamese Đồng", "NGN" to "Nigerian Naira", "EGP" to "Egyptian Pound", "PKR" to "Pakistani Rupee",
        "BDT" to "Bangladeshi Taka", "UAH" to "Ukrainian Hryvnia", "KZT" to "Kazakhstani Tenge", "QAR" to "Qatari Riyal",
        "MAD" to "Moroccan Dirham", "OMR" to "Omani Rial", "JOD" to "Jordanian Dinar", "LKR" to "Sri Lankan Rupee",
        "KES" to "Kenyan Shilling", "GHS" to "Ghanaian Cedi", "ISK" to "Icelandic Króna", "DOP" to "Dominican Peso",
        "CRC" to "Costa Rican Colón", "UYU" to "Uruguayan Peso", "GTQ" to "Guatemalan Quetzal", "TZS" to "Tanzanian Shilling",
        "UGX" to "Ugandan Shilling", "RSD" to "Serbian Dinar", "BOB" to "Bolivian Boliviano", "PYG" to "Paraguayan Guaraní",
        "JMD" to "Jamaican Dollar", "TTD" to "Trinidad & Tobago Dollar", "XOF" to "West African CFA Franc",
        "XAF" to "Central African CFA Franc", "ETB" to "Ethiopian Birr", "NPR" to "Nepalese Rupee", "AZN" to "Azerbaijani Manat",
        "GEL" to "Georgian Lari", "BYN" to "Belarusian Ruble", "MUR" to "Mauritian Rupee", "MMK" to "Myanmar Kyat",
    )

    fun format(amount: Double, code: String, hidden: Boolean = false): String {
        if (hidden) return "••••••"
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        runCatching { nf.currency = Currency.getInstance(code) }
        val abs = kotlin.math.abs(amount)
        nf.maximumFractionDigits = when {
            abs >= 1000 -> 0
            abs >= 1 -> 2
            abs >= 0.01 -> 4
            else -> 6
        }
        nf.minimumFractionDigits = minOf(2, nf.maximumFractionDigits)
        return nf.format(amount)
    }
}
