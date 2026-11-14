package com.example.tradedraw

import com.example.tradedraw.core.*
import org.junit.Assert.*
import org.junit.Test

class TransportPortsIntegrationTest {
    @Test fun rootReceivesHudBypassAndNeverFallsThroughAfterUncertainDelivery() {
        var bypassMs=0L;var rootCalls=0;var gestureCalls=0
        val hud=object:HudPort {
            override fun updateHUDView(force:Boolean) {}
            override fun showManualFallbackAlert(action:TradeAction,durationMs:Long) {}
            override fun restoreHudTouchable() {}
            override fun triggerClickAnimation(x:Float,y:Float) {}
            override fun isPointInsideHUD(x:Float,y:Float)=true
            override fun temporarilyBypassHUD(durationMs:Long,onBypassed:(()->Unit)?) { bypassMs=durationMs;onBypassed?.invoke() }
        }
        val root=RootTapTransport(hudPort=hud,rootCheck={true},rootTapExecutor={ _,_,_,_,callback,bypass ->
            assertTrue(bypass);rootCalls++;callback?.invoke(false,"uncertain");callback?.invoke(true,null)
        })
        val gesture=GestureTapTransport(instanceSupplier={object:OrderGestureDriver {
            override fun performOrderClickAt(x:Float,y:Float,callback:(Boolean,String?)->Unit) {gestureCalls++;callback(true,null)}
        }})
        val guard=StandardDemoAccountGuard();guard.recordAccountObservation(AccountType.DEMO)
        val result=mutableListOf<OrderReceipt>()
        DefaultOrderGateway(guard,rootTransport=root,gestureTransport=gesture).dispatch(OrderRequest(TradeAction.BUY,10f,20f)) {result+=it}
        assertEquals(350L,bypassMs);assertEquals(1,rootCalls);assertEquals(0,gestureCalls)
        assertEquals(1,result.size);assertTrue(result.single() is OrderReceipt.Failure)
    }
    @Test fun gestureDriverIsGuardedAndDisconnectHasNoAlternativeDispatch() {
        var calls=0
        var driver:OrderGestureDriver?=object:OrderGestureDriver {
            override fun performOrderClickAt(x:Float,y:Float,callback:(Boolean,String?)->Unit) {
                assertEquals(10f,x);assertEquals(20f,y);calls++;callback(true,null)
            }
        }
        val guard=StandardDemoAccountGuard();guard.recordAccountObservation(AccountType.REAL)
        val gateway=DefaultOrderGateway(guard,gestureTransport=GestureTapTransport(instanceSupplier={driver}))
        val request=OrderRequest(TradeAction.SELL,10f,20f)
        gateway.dispatch(request) {assertTrue(it is OrderReceipt.Vetoed)};assertEquals(0,calls)
        guard.recordAccountObservation(AccountType.DEMO)
        gateway.dispatch(request) {assertTrue(it is OrderReceipt.Success)};assertEquals(1,calls)
        driver=null
        gateway.dispatch(request) {assertTrue(it is OrderReceipt.Failure && !it.requiresReconciliation)}
        assertEquals(1,calls)
    }
}
