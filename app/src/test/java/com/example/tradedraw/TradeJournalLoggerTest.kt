package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Pruebas de persistencia del journal de auditoría.
 *
 * Usa un Context falso mínimo: TradeJournalLogger solo necesita getExternalFilesDir y filesDir
 * para decidir dónde escribir, así que no hace falta Robolectric ni un dispositivo.
 */
class TradeJournalLoggerTest {

    @Before
    fun setUp() {
        // El logger es un singleton: la última firma sobrevive entre casos de la misma JVM.
        TradeJournalLogger.resetIdempotencyStateForTests()
    }

    /** Context falso que devuelve un directorio temporal como almacenamiento externo. */
    private class FakeContext(private val root: File?, private val internalRoot: File) : android.content.ContextWrapper(null) {
        override fun getExternalFilesDir(type: String?): File? = root
        override fun getFilesDir(): File = internalRoot
    }

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "tradedraw_test_$name").apply {
            deleteRecursively()
            mkdirs()
        }

    @Test
    fun testLogTrade_writesHeaderAndRow() {
        val root = tempDir("journal_ok")
        val ctx = FakeContext(root, tempDir("journal_ok_int"))

        val ok = TradeJournalLogger.logTrade(
            context = ctx,
            strategy = "AUTO_ADAPTIVE",
            submode = "CONSERVATIVE",
            action = "BUY",
            confidence = 0.85f,
            priceY = 1234f,
            stake = 1.0,
            baseBalance = 500.0,
            result = "WIN",
            settledBalance = 501.9,
            durationSec = 62,
            reason = "test determinista"
        )

        assertTrue("La escritura debe reportarse como exitosa", ok)
        val file = File(root, "TradeDraw_Audits/trade_journal.csv")
        assertTrue("El CSV debe existir", file.exists())
        val lines = file.readLines()
        assertEquals("Debe haber cabecera + 1 fila", 2, lines.size)
        assertTrue("La cabecera debe empezar por timestamp", lines[0].startsWith("timestamp,iso_date"))
        assertTrue("La fila debe contener el resultado", lines[1].contains(",WIN,"))
        assertTrue("La fila debe contener la acción", lines[1].contains(",BUY,"))
    }

    @Test
    fun testLogTrade_appendsInsteadOfTruncating() {
        val root = tempDir("journal_append")
        val ctx = FakeContext(root, tempDir("journal_append_int"))

        repeat(3) { i ->
            TradeJournalLogger.logTrade(
                context = ctx,
                strategy = "S", submode = "M", action = "SELL", confidence = 0.5f,
                priceY = 100f, stake = 1.0, baseBalance = 10.0, result = "LOSS",
                settledBalance = 9.0, durationSec = 62, reason = "fila $i",
                // Cada iteración es un trade distinto: la guarda de idempotencia exige el timestamp.
                tradeStartMs = 1_000_000L + i
            )
        }

        val lines = File(root, "TradeDraw_Audits/trade_journal.csv").readLines()
        assertEquals("Cabecera única + 3 filas (no debe truncar)", 4, lines.size)
        assertEquals("La cabecera solo aparece una vez", 1, lines.count { it.startsWith("timestamp,iso_date") })
    }

    @Test
    fun testLogTrade_fallsBackToInternalStorageWhenExternalIsNull() {
        // getExternalFilesDir(null) == null es la causa (a) del fallo de telemetría investigado.
        val internal = tempDir("journal_fallback_int")
        val ctx = FakeContext(null, internal)

        val ok = TradeJournalLogger.logTrade(
            context = ctx,
            strategy = "S", submode = "M", action = "BUY", confidence = 0.5f,
            priceY = 100f, stake = 1.0, baseBalance = 10.0, result = "WIN",
            settledBalance = 11.0, durationSec = 62, reason = "fallback interno"
        )

        assertTrue("Debe escribir pese a no haber almacenamiento externo", ok)
        val file = File(internal, "TradeDraw_Audits/trade_journal.csv")
        assertTrue("El CSV debe existir en el almacenamiento interno", file.exists())
        assertTrue("La fila debe estar persistida", file.readText().contains("fallback interno"))
    }

    @Test
    fun testLogTrade_createsNestedAuditsDirectory() {
        val root = tempDir("journal_mkdirs")
        val ctx = FakeContext(root, tempDir("journal_mkdirs_int"))

        val ok = TradeJournalLogger.logTrade(
            context = ctx,
            strategy = "S", submode = "M", action = "BUY", confidence = 0.5f,
            priceY = 1f, stake = 1.0, baseBalance = 1.0, result = "TIE",
            settledBalance = 1.0, durationSec = 62, reason = "mkdir"
        )

        assertTrue(ok)
        assertTrue("Debe crear TradeDraw_Audits recursivamente", File(root, "TradeDraw_Audits").isDirectory)
    }

    @Test
    fun testLastWriteAgeMs_isMinusOneBeforeAnyWrite() {
        // Tras un arranque limpio el contador no debe mentir: -1 = nunca se ha escrito.
        val age = TradeJournalLogger.lastWriteAgeMs
        assertFalse("La antigüedad debe ser >= -1 (nunca negativa salvo el centinela)", age < -1L)
    }

    @Test
    fun testLogTrade_rejectsDuplicateOfSameTradeAndAction() {
        // Regresión exacta del doble registro observado (00:44:03 / 00:44:04, mismo balance y resultado):
        // la liquidación se disparó dos veces para el MISMO pendingTradeStartTime y escribió dos filas.
        val root = tempDir("journal_idem")
        val ctx = FakeContext(root, tempDir("journal_idem_int"))
        val tradeStartMs = 1_789_623_843_624L

        fun log(result: String) = TradeJournalLogger.logTrade(
            context = ctx, strategy = "S", submode = "YOLO", action = "BUY", confidence = 0.98f,
            priceY = 641.87f, stake = 1.0, baseBalance = 40_080_808.96, result = result,
            settledBalance = 40_162_810.88, durationSec = 62,
            reason = "SALDO (+) Ganancia acreditada", tradeStartMs = tradeStartMs
        )

        assertTrue("La primera liquidación debe persistirse", log("WIN"))
        assertFalse("El reintento del MISMO trade y acción debe rechazarse", log("WIN"))

        val lines = File(root, "TradeDraw_Audits/trade_journal.csv").readLines()
        assertEquals("Cabecera + 1 sola fila pese al doble disparo", 2, lines.size)
        assertEquals("Una única fila WIN en el journal", 1, lines.count { it.contains(",WIN,") })
        assertTrue("El rechazo debe quedar contabilizado", TradeJournalLogger.duplicateRejections >= 1L)
    }

    @Test
    fun testLogTrade_allowsDifferentTradeTimestamp() {
        // La guarda NO debe bloquear trades distintos: solo el mismo trade + misma acción.
        val root = tempDir("journal_idem_distinto")
        val ctx = FakeContext(root, tempDir("journal_idem_distinto_int"))

        fun log(startMs: Long) = TradeJournalLogger.logTrade(
            context = ctx, strategy = "S", submode = "YOLO", action = "SELL", confidence = 0.9f,
            priceY = 100f, stake = 1.0, baseBalance = 10.0, result = "LOSS", settledBalance = 9.0,
            durationSec = 62, reason = "perdida", tradeStartMs = startMs
        )

        assertTrue(log(1_000L))
        assertFalse("Reintento del mismo trade: rechazado", log(1_000L))
        assertTrue("Trade nuevo (timestamp distinto): debe registrarse", log(62_000L))

        val lines = File(root, "TradeDraw_Audits/trade_journal.csv").readLines()
        assertEquals("Cabecera + 2 trades distintos", 3, lines.size)
    }

    @Test
    fun testLogTrade_writeFailureDoesNotBlockRetry() {
        // Cinturón y tirantes: si la escritura falla, la firma NO debe quedar fijada; de lo contrario
        // el reintento del mismo trade se rechazaría como "duplicado" y la evidencia se perdería.
        val internal = tempDir("journal_retry_int")
        val ctx = FakeContext(null, internal)

        // 1er intento: impedimos la escritura (TradeDraw_Audits es un FICHERO, no un directorio)
        File(internal, "TradeDraw_Audits").writeText("bloqueador")
        val primero = TradeJournalLogger.logTrade(
            context = ctx, strategy = "S", submode = "M", action = "BUY", confidence = 0.5f,
            priceY = 1f, stake = 1.0, baseBalance = 1.0, result = "WIN", settledBalance = 2.0,
            durationSec = 62, reason = "intento bloqueado", tradeStartMs = 7_777_000L
        )
        assertFalse("La escritura debe fallar si el directorio no puede crearse", primero)

        // 2º intento del MISMO trade, ahora con el camino libre: debe poder persistirse
        File(internal, "TradeDraw_Audits").delete()
        val segundo = TradeJournalLogger.logTrade(
            context = ctx, strategy = "S", submode = "M", action = "BUY", confidence = 0.5f,
            priceY = 1f, stake = 1.0, baseBalance = 1.0, result = "WIN", settledBalance = 2.0,
            durationSec = 62, reason = "reintento exitoso", tradeStartMs = 7_777_000L
        )
        assertTrue("El reintento tras un fallo de escritura debe permitirse", segundo)

        val file = File(internal, "TradeDraw_Audits/trade_journal.csv")
        assertTrue("La evidencia del reintento debe existir", file.readText().contains("reintento exitoso"))
    }

    @Test
    fun testLogTrade_withoutTradeTimestampDoesNotBlockDifferentTrades() {
        // Contrato de la guarda: EXIGE el timestamp del trade. Sin él no hay forma de distinguir un
        // reintento de liquidación (duplicado) de un trade nuevo con la misma acción y resultado, así
        // que la guarda se desactiva y NO puede bloquear escrituras legítimas consecutivas.
        // Esto es lo que impide que la idempotencia pierda evidencia legítima.
        val root = tempDir("journal_sin_ts")
        val ctx = FakeContext(root, tempDir("journal_sin_ts_int"))

        fun log() = TradeJournalLogger.logTrade(
            context = ctx, strategy = "S", submode = "M", action = "SELL", confidence = 0.5f,
            priceY = 100f, stake = 1.0, baseBalance = 10.0, result = "LOSS",
            settledBalance = 9.0, durationSec = 62, reason = "sin timestamp", tradeStartMs = 0L
        )

        assertTrue("Sin timestamp la escritura no debe bloquearse", log())
        assertTrue("Sin timestamp una segunda escritura legítima tampoco", log())

        val lines = File(root, "TradeDraw_Audits/trade_journal.csv").readLines()
        assertEquals("Cabecera + 2 filas", 3, lines.size)
    }

    @Test
    fun testLogTrade_recordsTieResult() {
        // Punto 4 del objetivo: los TIE (orden no procesada / cancelación preventiva) deben quedar en el journal.
        val root = tempDir("journal_tie")
        val ctx = FakeContext(root, tempDir("journal_tie_int"))

        val ok = TradeJournalLogger.logTrade(
            context = ctx, strategy = "AUTO_ADAPTIVE", submode = "YOLO", action = "BUY", confidence = 0.98f,
            priceY = 806.00f, stake = 1.0, baseBalance = 44_348_810.24, result = "TIE",
            settledBalance = 44_348_810.24, durationSec = 58,
            reason = "ORDEN NO PROCESADA (Diff=0.0 -> Saldo inalterado)", tradeStartMs = 1_789_095_183_071L
        )

        assertTrue("El TIE debe persistirse como evidencia", ok)
        val line = File(root, "TradeDraw_Audits/trade_journal.csv").readLines()[1]
        assertTrue("El resultado TIE debe estar en el CSV", line.contains(",TIE,"))
        assertTrue("El balance liquidado no debe alterar el equity en un TIE",
            line.contains(",TIE,44348810.24,"))
    }

    @Test
    fun testLogTrade_rotatesJournalWhenOverSizeLimit() {
        // Punto 3: el journal crece sin límite en el dispositivo. Al pasar de 2 MB debe rotar
        // a trade_journal_<timestamp>.csv y empezar uno nuevo con cabecera.
        val root = tempDir("journal_rotate")
        val ctx = FakeContext(root, tempDir("journal_rotate_int"))
        val dir = File(root, "TradeDraw_Audits").apply { mkdirs() }
        val active = File(dir, "trade_journal.csv")

        // Journal activo por encima del umbral de 2 MB, con cabecera + relleno
        val relleno = StringBuilder("timestamp,iso_date\n")
        while (relleno.length < 2_200_000) relleno.append("1,2026-09-17T00:00:00\n")
        active.writeText(relleno.toString())
        assertTrue("El journal de prueba debe superar los 2 MB", active.length() > 2L * 1024L * 1024L)

        val ok = TradeJournalLogger.logTrade(
            context = ctx, strategy = "S", submode = "M", action = "BUY", confidence = 0.5f,
            priceY = 1f, stake = 1.0, baseBalance = 1.0, result = "WIN", settledBalance = 2.0,
            durationSec = 62, reason = "tras rotacion", tradeStartMs = 999_000L
        )

        assertTrue("La escritura debe seguir siendo exitosa tras rotar", ok)
        val rotated = dir.listFiles { f -> f.name.startsWith("trade_journal_") && f.name != "trade_journal.csv" }
        assertTrue("Debe existir un journal rotado", rotated != null && rotated.isNotEmpty())
        assertTrue("El rotado conserva la evidencia (>2MB)", rotated!!.first().length() > 2L * 1024L * 1024L)
        assertEquals("El journal activo se reinicia con cabecera + 1 fila", 2, active.readLines().size)
        assertTrue("El journal activo empieza por la cabecera", active.readLines()[0].startsWith("timestamp,iso_date"))
    }
}