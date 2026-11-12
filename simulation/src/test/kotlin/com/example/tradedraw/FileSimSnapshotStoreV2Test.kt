package com.example.tradedraw

import com.example.tradedraw.core.TradeAction
import com.example.tradedraw.simulation.FileSimSnapshotStore
import com.example.tradedraw.simulation.OpenSimulatedTrade
import com.example.tradedraw.simulation.SimMetadata
import com.example.tradedraw.simulation.SimSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.CRC32

class FileSimSnapshotStoreV2Test {
    private fun writeLegacyV1(file: File) {
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { w ->
                w.writeDouble(1000.0)
                w.writeDouble(0.82)
                w.writeDouble(1000.0)
                w.writeInt(1)
                w.writeUTF("trade-v1")
                w.writeUTF("decision-v1")
                w.writeUTF(TradeAction.BUY.name)
                w.writeDouble(100.0)
                w.writeDouble(10.0)
                w.writeLong(1000L)
                w.writeUTF("A")
                w.writeUTF("MT_REJECTION")
                w.writeUTF("CONSERVATIVE")
                w.writeFloat(0.85f)
                w.writeUTF("UPTREND")
                w.writeUTF("legacy reason")
                w.writeInt(0)
                w.writeInt(0)
                w.writeInt(0)
            }
        }.toByteArray()
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { stream ->
            DataOutputStream(stream).use { w ->
                w.writeInt(0x54445331)
                w.writeInt(payload.size)
                w.write(payload)
                w.writeLong(CRC32().apply { update(payload) }.value)
            }
        }
    }

    @Test fun tds1OpenTradeStillLoadsWithNullPolicyContext() {
        val root = Files.createTempDirectory("tds1-compat").toFile()
        try {
            val file = File(root, "ledger.bin")
            writeLegacyV1(file)
            val open = FileSimSnapshotStore(file).load()!!.open.single()
            assertEquals("trade-v1", open.tradeId)
            assertEquals("MT_REJECTION", open.metadata.strategy)
            assertNull(open.metadata.policyContextJson)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun tds2RoundTripPreservesOpaquePolicyContext() {
        val root = Files.createTempDirectory("tds2-context").toFile()
        try {
            val file = File(root, "ledger.bin")
            val context = """{"mode":"SHADOW","currentDecisionId":"decision-v2"}"""
            val snapshot = SimSnapshot(
                initialBalance = 1000.0,
                payoutRate = 0.82,
                balance = 1000.0,
                open = listOf(
                    OpenSimulatedTrade(
                        "trade-v2",
                        "decision-v2",
                        TradeAction.SELL,
                        100.0,
                        10.0,
                        2000L,
                        "B",
                        SimMetadata(
                            strategy = "AUTO_ADAPTIVE",
                            submode = "CONSERVATIVE",
                            confidence = 0.9f,
                            trend = "DOWNTREND",
                            reason = "final reason",
                            strategyVersion = "r29c-instrumented-policy-v1",
                            policyContextJson = context
                        )
                    )
                )
            )
            val store = FileSimSnapshotStore(file)
            store.save(snapshot)
            java.io.DataInputStream(file.inputStream()).use {
                assertEquals(0x54445332, it.readInt())
            }
            val recovered = store.load()!!.open.single()
            assertEquals("r29c-instrumented-policy-v1", recovered.metadata.strategyVersion)
            assertEquals(context, recovered.metadata.policyContextJson)
        } finally {
            root.deleteRecursively()
        }
    }
}
