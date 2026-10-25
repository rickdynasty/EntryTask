package com.entry.biz.dashboard.domain.pages

/** 各域有独立模型；公共契约仅覆盖确实共享的金额、菜单和身份。 */
data class MoneyValue(val text: String)

data class MenuEntry(val id: String, val title: String, val icon: String, val badge: String = "")

data class Banner(val id: String, val title: String, val subtitle: String, val art: String)

data class Category(val id: String, val title: String)

data class ProductBrief(
    val id: String,
    val title: String,
    val term: String,
    val risk: String,
    val ratio: String = "",
    val indicator: String = "",
    val minimum: String = "",
    val distribution: String = "",
    val riskText: String = "",
)

data class ProductPage(val items: List<ProductBrief>, val total: Int)

data class AssetOverview(val total: MoneyValue, val income: MoneyValue)

data class HomeContent(
    val placeholder: String,
    val quick: List<MenuEntry>,
    val banners: List<Banner>,
    val services: List<MenuEntry> = emptyList(),
    val shortcuts: List<MenuEntry> = emptyList(),
    val categories: List<Category> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)

/** 首页运营卡片只携带展示数据，点击由宿主统一接入演示说明。 */
data class HomeTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val art: String,
    val tone: String,
)

data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val style: String,
    val entries: List<HomeTile>,
)

data class WealthContent(
    val services: List<MenuEntry>,
    val categories: List<Category>,
    val markets: List<Pair<String, String>>,
)

data class TierProgress(
    val current: MoneyValue,
    val threshold: MoneyValue,
    val top: Boolean,
    val asOf: String,
    val fraction: Float?,
)

data class Identity(
    val nickname: String,
    val level: Int,
    val tierName: String,
    val carbon: String,
    val counts: List<Int>,
    val progress: TierProgress,
)

data class MemberContent(val benefits: List<MenuEntry>)

data class CheckinDay(val date: String, val checked: Boolean)

data class CheckinStatus(
    val date: String,
    val checked: Boolean,
    val consecutive: Int,
    val energy: Int,
    val awarded: Int,
    val already: Boolean,
    val calendar: List<CheckinDay>,
    val rule: String,
)

data class CreditBill(val hasCard: Boolean, val unpaid: MoneyValue?, val dueDate: String?)

data class LoanOffer(val limit: MoneyValue, val rate: String)

data class FinancialOverview(
    val month: String,
    val income: MoneyValue,
    val expense: MoneyValue,
    val bill: CreditBill,
    val loan: LoanOffer,
    val incomeShare: Float?,
)

interface HomeRepository {
    suspend fun products(category: String): ProductPage

    suspend fun home(): HomeContent

    suspend fun unread(): Int
}

interface WealthRepository {
    suspend fun wealth(): WealthContent

    suspend fun products(category: String): ProductPage

    suspend fun assets(): AssetOverview
}

interface MemberRepository {
    suspend fun member(): MemberContent

    suspend fun identity(member: Boolean): Identity

    suspend fun checkin(): CheckinStatus

    suspend fun submit(date: String, key: String): CheckinStatus
}

interface ProfileRepository {
    suspend fun identity(member: Boolean): Identity

    suspend fun assets(): AssetOverview

    suspend fun financial(): FinancialOverview
}

/** 组装根复用同一数据实现；VM仍只依赖领域接口。 */
interface RootPageRepositorySet :
    HomeRepository, WealthRepository, MemberRepository, ProfileRepository
