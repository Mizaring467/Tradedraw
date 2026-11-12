package com.example.tradedraw
import org.junit.Assert.*
import org.junit.Test
class BrokerCatalogTest {
    @Test fun onlyIntegratedBrokerCanStart() {
        assertTrue(BrokerCatalog.canStart("binomo"))
        for (id in listOf("quotex", "pocket_option", "iq_option", "unknown", "", null)) assertFalse(BrokerCatalog.canStart(id))
    }
    @Test fun savedProfileIdentifiersAreUnique() {
        assertEquals(BrokerCatalog.profiles.size, BrokerCatalog.profiles.map { it.id }.toSet().size)
    }
}
