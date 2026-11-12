package com.example.tradedraw

/** Availability is explicit: package detection is not a broker integration. */
data class HomeBrokerProfile(val id: String, val label: String, val available: Boolean)
object BrokerCatalog {
    val profiles = listOf(
        HomeBrokerProfile("binomo", "Binomo", true),
        HomeBrokerProfile("quotex", "Quotex", false),
        HomeBrokerProfile("pocket_option", "Pocket Option", false),
        HomeBrokerProfile("iq_option", "IQ Option", false)
    )
    fun find(id: String?): HomeBrokerProfile? = profiles.find { it.id == id }
    fun canStart(id: String?): Boolean = find(id)?.available == true
}
