package io.recebimento.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

object CurrencyFormatter {
    
    private val formatador: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale("pt", "BR")).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        DecimalFormat("#,###,##0.00", symbols)
    }
    
    private val formatadorSemDecimal: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale("pt", "BR")).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        DecimalFormat("#,###,##0", symbols)
    }

    /**
     * Formata um valor Double para moeda brasileira
     * Ex: 110602.47 -> "110.602,47"
     */
    fun formatarMoeda(valor: Double): String {
        return formatador.format(valor)
    }

    /**
     * Formata um valor Double para moeda brasileira com R$
     * Ex: 110602.47 -> "R$ 110.602,47"
     */
    fun formatarMoedaComSimbolo(valor: Double): String {
        return "R$ ${formatador.format(valor)}"
    }

    /**
     * Formata um valor Double sem casas decimais
     * Ex: 110602.47 -> "110.602"
     */
    fun formatarNumero(valor: Double): String {
        return formatadorSemDecimal.format(valor)
    }

    /**
     * Formata um valor Double como porcentagem
     * Ex: 14.99 -> "14,99%"
     */
    fun formatarPorcentagem(valor: Double): String {
        return "${formatador.format(valor)}%"
    }

    /**
     * Formata um valor Double com 2 casas decimais
     * Ex: 14.9 -> "14,90"
     */
    fun formatarDecimal(valor: Double): String {
        return formatador.format(valor)
    }

    /**
     * Formata um inteiro com separador de milhar
     * Ex: 198 -> "198", 1000 -> "1.000", 1000000 -> "1.000.000"
     */
    fun formatarInteiro(valor: Int): String {
        return formatadorSemDecimal.format(valor.toDouble())
    }

    /**
     * Formata um long com separador de milhar
     */
    fun formatarLong(valor: Long): String {
        return formatadorSemDecimal.format(valor.toDouble())
    }
}