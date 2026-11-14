package com.example.tradedraw

import com.example.tradedraw.core.*
import org.junit.Assert.*
import org.junit.Test

class RiskPortsIsolationTest {
    @Test fun injectedBalanceBrokerAndHudReachTheirConsumersWithoutAppStatics() {
        var observation=BrokerStateView(balance=40_000_000.0,isDemoAccount=true,observedAsset="Crypto IDX",observedOrderAmount=4000.0,isSyntheticOrOTC=true)
        var updates=0
        val hud=object:HudPort {
            override fun updateHUDView(force:Boolean) { updates++ }
            override fun showManualFallbackAlert(action:TradeAction,durationMs:Long) {}
            override fun restoreHudTouchable() {}
            override fun triggerClickAnimation(x:Float,y:Float) {}
        }
        val sensor=object:BrokerSensor { override fun getBrokerState()=observation }
        val balance=object:BalanceSource { override fun read()=BalanceReading(40_000_000.0,System.currentTimeMillis(),true) }
        val risk=RiskManager(balanceSource=balance,brokerSensor=sensor,hudPort=hud)
        risk.resetStreakOnly()
        assertEquals(40_000_000.0,risk.sessionStartBalance,0.001)
        assertEquals(4000.0,risk.getEffectiveStake(),0.001)
        assertFalse(risk.canExecuteTrade(AutonomousSubMode.SNIPER,1f).first)
        observation=observation.copy(observedAsset="EUR/USD",observedOrderAmount=5000.0,isSyntheticOrOTC=false)
        assertEquals(5000.0,risk.getEffectiveStake(),0.001)
        assertTrue(risk.canExecuteTrade(AutonomousSubMode.SNIPER,1f).first)
        risk.recordTradeVoid()
        assertTrue(updates>0)
    }
}
