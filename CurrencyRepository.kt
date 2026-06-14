package com.example.currencyconverter.repository

import com.example.currencyconverter.network.RetrofitClient

class CurrencyRepository private constructor() {
    private var cachedRates: Map<String, Double> = emptyMap()
    private var cachedBase: String = ""
    private var lastSyncTimeMillis: Long = 0L

    suspend fun refreshRates(base: String): Result<Map<String, Double>> {
        return runCatching {
            val response = RetrofitClient.api.getLatestRates(base)
            cachedBase = response.base
            cachedRates = response.rates
            lastSyncTimeMillis = System.currentTimeMillis()
            cachedRates
        }
    }

    fun getCachedRate(base: String, target: String): Double? {
        if (cachedRates.isEmpty()) return null
        if (cachedBase == base) return cachedRates[target]

        val inBase = cachedRates[base] ?: return null
        val inTarget = cachedRates[target] ?: return null
        return inTarget / inBase
    }

    fun getLastSyncTimeMillis(): Long = lastSyncTimeMillis

    companion object {
        @Volatile
        private var instance: CurrencyRepository? = null

        fun getInstance(): CurrencyRepository {
            return instance ?: synchronized(this) {
                instance ?: CurrencyRepository().also { instance = it }
            }
        }
    }
}
