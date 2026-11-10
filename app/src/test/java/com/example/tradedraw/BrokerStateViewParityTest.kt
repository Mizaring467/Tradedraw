package com.example.tradedraw

import com.example.tradedraw.core.AssetClassification
import com.example.tradedraw.core.AssetClassifier
import com.example.tradedraw.core.BrokerSensor
import com.example.tradedraw.core.BrokerStateView
import org.junit.Assert.*
import org.junit.Test

class BrokerStateViewParityTest {

    @Test
    fun brokerStateViewDefaultProperties() {
        val state = BrokerStateView(
            balance = 1_500_000.0,
            lastBalanceReadMs = 123456L,
            isDemoAccount = true,
            observedAsset = "Crypto IDX",
            observedPayout = 0.82,
            observedOrderAmount = 4000.0,
            isSyntheticOrOTC = true
        )

        assertEquals(1_500_000.0, state.balance, 0.001)
        assertEquals(123456L, state.lastBalanceReadMs)
        assertTrue(state.isDemoAccount)
        assertEquals("Crypto IDX", state.observedAsset)
        assertEquals(0.82, state.observedPayout!!, 0.001)
        assertEquals(4000.0, state.observedOrderAmount, 0.001)
        assertTrue(state.isSyntheticOrOTC)
    }

    @Test
    fun autoTradeAccessibilityServiceProvidesBrokerSensor() {
        val sensor: BrokerSensor = AutoTradeAccessibilityService
        val state = sensor.getBrokerState()

        assertNotNull(state)
        assertEquals(AutoTradeAccessibilityService.isDemoAccount, state.isDemoAccount)
        assertEquals(AutoTradeAccessibilityService.latestObservedAsset.ifBlank { "Crypto IDX" }, state.observedAsset)
    }

    @Test
    fun assetClassifierParity() {
        assertEquals(AssetClassification.SYNTHETIC_OTC, AssetClassifier.classifyAsset("Crypto IDX"))
        assertEquals(AssetClassification.SYNTHETIC_OTC, AssetClassifier.classifyAsset("EUR/USD OTC"))
        assertEquals(AssetClassification.FOREX_REAL, AssetClassifier.classifyAsset("EUR/USD"))
        assertEquals(AssetClassification.FOREX_REAL, AssetClassifier.classifyAsset("GBP/JPY"))
        assertEquals(AssetClassification.UNKNOWN, AssetClassifier.classifyAsset(""))
        assertEquals(AssetClassification.UNKNOWN, AssetClassifier.classifyAsset("XYZ123"))

        // Parity with AutoTradeAccessibilityService.classifyAsset
        assertEquals(
            AutoTradeAccessibilityService.classifyAsset("Crypto IDX"),
            AssetClassifier.classifyAsset("Crypto IDX")
        )
        assertEquals(
            AutoTradeAccessibilityService.classifyAsset("EUR/USD"),
            AssetClassifier.classifyAsset("EUR/USD")
        )
    }

    @Test
    fun riskManagerWithCustomBrokerSensorParity() {
        val fakeSensor = object : BrokerSensor {
            override fun getBrokerState(): BrokerStateView = BrokerStateView(
                balance = 2_000_000.0,
                lastBalanceReadMs = System.currentTimeMillis(),
                isDemoAccount = true,
                observedAsset = "Crypto IDX",
                observedPayout = 0.83,
                observedOrderAmount = 5000.0,
                isSyntheticOrOTC = true
            )
        }

        val riskManager = RiskManager(brokerSensor = fakeSensor)
        val stake = riskManager.getEffectiveStake()
        assertEquals(5000.0, stake, 0.001)
    }
}
