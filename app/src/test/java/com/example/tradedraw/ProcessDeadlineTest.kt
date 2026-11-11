package com.example.tradedraw

import org.junit.Assert.*
import org.junit.Test

class ProcessDeadlineTest {
    @Test fun completedProcessReturnsNormally() {
        val process = ProcessBuilder("sh", "-c", "exit 0").start()
        try {
            assertTrue(awaitProcessExit(process, 1000L))
            assertEquals(0, process.exitValue())
        } finally { process.destroy() }
    }
    @Test fun hungProcessHasBoundedWait() {
        val process = ProcessBuilder("sh", "-c", "sleep 2").start()
        try {
            assertFalse(awaitProcessExit(process, 20L))
        } finally { process.destroy() }
    }
}
