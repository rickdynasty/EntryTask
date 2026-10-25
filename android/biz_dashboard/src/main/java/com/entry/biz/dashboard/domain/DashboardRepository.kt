package com.entry.biz.dashboard.domain

/** Native四个根页的只读投影，不暴露JSON、凭证或平台View。 */
data class ServiceEntry(val title: String, val icon: String, val category: String = "", val badge: String = "")
data class Promotion(val title: String, val subtitle: String, val art: String)
data class Product(val name: String, val ratio: String, val period: String, val risk: String)
data class DashboardPage(
    val tab: Int,
    val services: List<ServiceEntry> = emptyList(),
    val quick: List<ServiceEntry> = emptyList(),
    val shortcuts: List<ServiceEntry> = emptyList(),
    val promotions: List<Promotion> = emptyList(),
    val categories: List<ServiceEntry> = emptyList(),
    val products: List<Product> = emptyList(),
    val market: String = "",
    val welcome: String = "",
    val counts: List<String> = emptyList(),
    val total: String = "0.00",
    val income: String = "0.00",
    val slogan: String = "",
    val notice: String = "",
    val loanLimit: String = "",
    val loanRate: String = "",
)
interface DashboardRepository {
    suspend fun load(tab: Int, category: String?): DashboardPage
}
