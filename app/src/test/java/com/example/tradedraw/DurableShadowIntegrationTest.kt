package com.example.tradedraw

import com.example.tradedraw.core.*
import com.example.tradedraw.simulation.*
import com.example.tradedraw.persistence.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DurableShadowIntegrationTest {
    private val clock = ManualSimClock(10_000L)
    private fun request(id: String) = OrderRequest(TradeAction.BUY, 0f, 0f, "d-$id", "t-$id", mode = AutoTradeMode.SHADOW)
    private fun tick(asset: String = "A", price: Double = 100.0, ts: Long = clock.nowMs()) = MarketTick(asset, price, ts)
    private fun open(p: ShadowSimulation, id: String, asset: String = "A") {
        p.open(request(id), tick(asset), SimMetadata(reason = "quoted, reason")) { assertTrue(it.toString(), it is OrderReceipt.Success) }
    }
    @Test fun normalEntryTicksSettlementOutboxReadAndRestartAreExactlyOnce() {
        val root = Files.createTempDirectory("shadow-flow").toFile()
        try {
            File(root, "shadow_journal.csv").writeText(ShadowJournal.HEADER_V1 + "1,old,S,M,BUY,0.8,1,SIDEWAYS,1,old-id,\"historic\"\n")
            val historical = File(root, "shadow_journal.csv").readBytes()
            fun pipeline(): ShadowSimulation = ShadowSimulation(SimulatedOrderGateway(SimLedger(store = FileSimSnapshotStore(File(root, "ledger"))), clock), SimulationResultFileJournal(root))
            var p = pipeline(); assertFalse(p.enabled)
            p.open(request("off"), tick()) { assertTrue(it is OrderReceipt.Vetoed) }
            p.enabled = true; open(p,"win"); open(p,"loss","B"); open(p,"tie","C")
            open(p,"win") // retry before restart
            assertEquals(3, p.gateway.getPendingTradesCount())
            p = pipeline() // before expiry: opens recover, activation does not
            assertFalse(p.enabled); assertEquals(3,p.gateway.getPendingTradesCount())
            assertTrue(p.onTick(tick("Z",999.0,80_000L)).isEmpty())
            assertTrue(p.onTick(tick("A",101.0,69_999L)).isEmpty())
            assertEquals(1,p.onTick(tick("A",101.0,70_000L)).size)
            assertTrue(p.onTick(tick("A",90.0,70_000L)).isEmpty()) // duplicate timestamp conflict ignored
            assertTrue(p.onTick(tick("A",90.0,69_000L)).isEmpty())
            p.onTick(tick("B",99.0,71_000L)); p.onTick(tick("C",100.0,72_000L))
            assertEquals(998.2,p.gateway.simLedger.currentBalance,1e-8)
            assertEquals(3,p.gateway.simLedger.totalTrades)
            val reader = ShadowJournalFilesReader(listOf(root))
            val rows = reader.readEntries()
            assertEquals(1, rows.count { it.schemaVersion == 1 })
            assertEquals(setOf("WIN","LOSS","TIE"),rows.filter { it.schemaVersion == 2 }.map { it.simResult }.toSet())
            assertTrue(rows.filter { it.schemaVersion == 2 }.all { it.reason.contains("tradeId=t-") && it.reason.contains("asset=") })
            p = pipeline(); p.flushResults(); p.onTick(tick("A",102.0,80_000L))
            assertEquals(4,reader.readEntries().size)
            assertEquals(998.2,p.gateway.simLedger.currentBalance,1e-8)
            assertArrayEquals(historical,File(root,"shadow_journal.csv").readBytes())
            assertEquals(ShadowJournal.HEADER_V2.trim(),File(root,SimulationResultFileJournal.FILE_NAME).readLines().first())
        } finally { root.deleteRecursively() }
    }
    @Test fun journalFailureAndCrashAfterWritingBeforeAcknowledgementRecoverWithoutDuplicate() {
        val root=Files.createTempDirectory("shadow-outbox").toFile()
        try {
            val backing=FileSimSnapshotStore(File(root,"state")); var failAck=false
            val store=object: SimSnapshotStore {
                override fun load()=backing.load()
                override fun save(snapshot: SimSnapshot) {
                    if(failAck && snapshot.journaled.isNotEmpty()) error("ack disk failure")
                    backing.save(snapshot)
                }
            }
            val journal=SimulationResultFileJournal(root); var failJournal=true
            val p=ShadowSimulation(SimulatedOrderGateway(SimLedger(store=store),clock),SimulationResultJournal { if(failJournal) false else journal.append(it) })
            p.enabled=true; open(p,"one"); p.onTick(tick(price=101.0,ts=70_000L))
            assertEquals(1008.2,p.gateway.simLedger.currentBalance,1e-8)
            assertEquals(1,p.gateway.simLedger.pendingJournal().size)
            assertNotNull(p.lastError)
            failJournal=false; failAck=true; p.flushResults()
            assertEquals(1,ShadowJournalFilesReader(listOf(root)).readEntries().size)
            val recovered=ShadowSimulation(SimulatedOrderGateway(SimLedger(store=backing),clock),journal)
            recovered.flushResults(); recovered.flushResults()
            assertNull(recovered.lastError)
            assertTrue(recovered.gateway.simLedger.pendingJournal().isEmpty())
            assertEquals(1,ShadowJournalFilesReader(listOf(root)).readEntries().size)
            assertEquals(1008.2,recovered.gateway.simLedger.currentBalance,1e-8)
        } finally { root.deleteRecursively() }
    }
    @Test fun failedSettlementBeforeAndAfterDurableCommitNeverDuplicatesBalance() {
        val backing=MemorySimSnapshotStore(); var before=false; var after=false
        val store=object: SimSnapshotStore {
            override fun load()=backing.load()
            override fun save(snapshot:SimSnapshot) {
                if(before && snapshot.settled.isNotEmpty()) error("before commit")
                backing.save(snapshot)
                if(after && snapshot.settled.isNotEmpty()) error("after commit")
            }
        }
        val l=SimLedger(store=store); l.openTrade("t","d",TradeAction.BUY,100.0,10.0,10_000L,"A")
        before=true
        try { l.onTick("A",101.0,70_000L); fail() } catch(_:IllegalStateException) { }
        assertEquals(1000.0,l.currentBalance,0.0); assertEquals(1,l.getOpenTrades().size)
        before=false; after=true
        try { l.onTick("A",101.0,70_000L); fail() } catch(_:IllegalStateException) { }
        assertEquals(1008.2,l.currentBalance,1e-8)
        val recovered=SimLedger(store=backing)
        assertTrue(recovered.onTick("A",102.0,71_000L).isEmpty())
        assertEquals(1,recovered.pendingJournal().size)
        assertEquals(1008.2,recovered.currentBalance,1e-8)
    }
    @Test fun multipleOrdersReserveFundsIdsConflictAndCorruptStorageFailsClosed() {
        val l=SimLedger(initialBalance=20.0)
        l.openTrade("t1","d1",TradeAction.BUY,100.0,10.0,0,"A")
        l.openTrade("t2","d2",TradeAction.BUY,100.0,10.0,0,"A")
        try { l.openTrade("t3","d3",TradeAction.BUY,100.0,10.0,0,"A"); fail() } catch(_:IllegalArgumentException) { }
        try { l.openTrade("different","d1",TradeAction.BUY,100.0,10.0,0,"A"); fail() } catch(_:IllegalArgumentException) { }
        assertEquals(2,l.onTick("A",101.0,60_000L).size)
        assertEquals(36.4,l.currentBalance,1e-8)
        val root=Files.createTempDirectory("shadow-corrupt").toFile()
        try {
            File(root,"state").writeText("corrupt")
            try { SimLedger(store=FileSimSnapshotStore(File(root,"state"))); fail() } catch(_:Exception) { }
        } finally { root.deleteRecursively() }
    }
    @Test fun outOfOrderAndAbsentTicksDoNotInventAnExpiryPrice() {
        val p=ShadowSimulation(SimulatedOrderGateway(simClock=clock),SimulationResultJournal { true }); p.enabled=true
        open(p,"order")
        p.onTick(tick(ts=69_000L)); p.onTick(tick(price=90.0,ts=65_000L))
        p.enabled=false; p.flushResults()
        assertEquals(1,p.gateway.getPendingTradesCount())
        assertEquals(0,p.gateway.simLedger.totalTrades)
        assertEquals(1,p.onTick(tick(price=101.0,ts=90_000L)).size) // delayed but first accepted post-expiry
        assertEquals(90_000L,p.gateway.simLedger.getSettledTrades().single().exitTimestampMs)
    }
}
