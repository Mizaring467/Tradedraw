package com.example.tradedraw

import com.example.tradedraw.core.*
import org.junit.Assert.*
import org.junit.Test

class LiveStrategyBrokerPortTest {
    @Test fun eachEvaluationUsesTheCurrentBrokerObservation() {
        var state=BrokerStateView(observedAsset="Crypto IDX",isSyntheticOrOTC=true)
        var reads=0
        val evaluator=LiveStrategyEvaluator(object:BrokerSensor { override fun getBrokerState():BrokerStateView {reads++;return state} })
        val analysis=VisionAnalysisResult(candleSecond=2,touchesSupport=true,hasBottomRejectionWick=true,tickVelocityNormalized=0.05f)
        fun evaluate()=evaluator.evaluate(AutoTradeStrategy.AUTO_ADAPTIVE,analysis,null,AutonomousSubMode.SNIPER,null)
        assertNull(evaluate().first)
        state=state.copy(observedAsset="EUR/USD",rawObservedAsset="EUR/USD",isSyntheticOrOTC=false)
        assertEquals(TradeAction.BUY,evaluate().first)
        state=state.copy(isSyntheticOrOTC=true)
        assertNull(evaluate().first)
        assertEquals(3,reads)
    }
}
