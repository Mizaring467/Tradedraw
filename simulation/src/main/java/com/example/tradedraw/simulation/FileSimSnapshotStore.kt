package com.example.tradedraw.simulation

import com.example.tradedraw.core.TradeAction
import java.io.*
import java.util.zip.CRC32

/** A checksummed snapshot is fsynced, then replaced by a same-directory rename (API24 compatible). */
class FileSimSnapshotStore(private val file: File) : SimSnapshotStore {
    @Synchronized override fun load(): SimSnapshot? {
        if (!file.exists()) return null
        DataInputStream(FileInputStream(file)).use { input ->
            check(input.readInt() == 0x54445331) { "SIM_STATE_HEADER" }
            val size = input.readInt(); require(size in 1..64_000_000)
            val bytes = ByteArray(size); input.readFully(bytes)
            check(input.readLong() == CRC32().apply { update(bytes) }.value && input.read() == -1) { "SIM_STATE_CHECKSUM" }
            DataInputStream(ByteArrayInputStream(bytes)).use { r ->
                fun count() = r.readInt().also { require(it in 0..1_000_000) }
                fun meta() = SimMetadata(r.readUTF(), r.readUTF(), r.readFloat(), r.readUTF(), r.readUTF())
                val initial = r.readDouble(); val payout = r.readDouble(); val balance = r.readDouble()
                val open = List(count()) { OpenSimulatedTrade(r.readUTF(), r.readUTF(), TradeAction.valueOf(r.readUTF()),
                    r.readDouble(), r.readDouble(), r.readLong(), r.readUTF(), meta()) }
                val settled = List(count()) { SettledSimulatedTrade(r.readUTF(), r.readUTF(), TradeAction.valueOf(r.readUTF()),
                    r.readDouble(), r.readDouble(), r.readDouble(), r.readUTF(), r.readLong(), r.readLong(),
                    r.readDouble(), r.readDouble(), r.readUTF(), meta()) }
                val ack = List(count()) { r.readUTF() }.toSet()
                val ticks = List(count()) { r.readUTF() to r.readLong() }.toMap()
                check(r.read() == -1)
                return SimSnapshot(initial, payout, balance, open, settled, ack, ticks)
            }
        }
    }
    @Synchronized override fun save(snapshot: SimSnapshot) {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { w ->
                fun meta(m: SimMetadata) { w.writeUTF(m.strategy); w.writeUTF(m.submode); w.writeFloat(m.confidence); w.writeUTF(m.trend); w.writeUTF(m.reason) }
                w.writeDouble(snapshot.initialBalance); w.writeDouble(snapshot.payoutRate); w.writeDouble(snapshot.balance)
                w.writeInt(snapshot.open.size)
                snapshot.open.forEach { o -> w.writeUTF(o.tradeId); w.writeUTF(o.decisionId); w.writeUTF(o.action.name); w.writeDouble(o.entryPrice); w.writeDouble(o.stake); w.writeLong(o.entryTimestampMs); w.writeUTF(o.asset); meta(o.metadata) }
                w.writeInt(snapshot.settled.size)
                snapshot.settled.forEach { s -> w.writeUTF(s.tradeId); w.writeUTF(s.decisionId); w.writeUTF(s.action.name); w.writeDouble(s.entryPrice); w.writeDouble(s.exitPrice); w.writeDouble(s.stake); w.writeUTF(s.result); w.writeLong(s.entryTimestampMs); w.writeLong(s.exitTimestampMs); w.writeDouble(s.baseBalance); w.writeDouble(s.settledBalance); w.writeUTF(s.asset); meta(s.metadata) }
                w.writeInt(snapshot.journaled.size); snapshot.journaled.sorted().forEach { w.writeUTF(it) }
                w.writeInt(snapshot.watermarks.size); snapshot.watermarks.toSortedMap().forEach { (a,t) -> w.writeUTF(a); w.writeLong(t) }
            }
        }.toByteArray()
        check(file.parentFile?.let { it.isDirectory || it.mkdirs() } != false)
        val tmp = File(file.path + ".new")
        FileOutputStream(tmp).use { stream ->
            val w = DataOutputStream(stream); w.writeInt(0x54445331); w.writeInt(bytes.size); w.write(bytes)
            w.writeLong(CRC32().apply { update(bytes) }.value); w.flush(); stream.fd.sync()
        }
        check(tmp.renameTo(file)) { "SIM_STATE_RENAME_FAILED" }
    }
}
