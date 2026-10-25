package com.entry.biz.dashboard.data.pages

import com.entry.biz.dashboard.domain.pages.*
import java.math.BigDecimal
import java.text.DecimalFormat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** 四域契约共享一个会话资产缓存；不存在第二套持久账本。 */
class RootPageRepositories(private val api: NativeApiClient) : RootPageRepositorySet {
    private val assetLock = Mutex()
    private var assetCache: Pair<Long, AssetOverview>? = null

    private fun rows(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }

    private fun menu(data: JSONObject, key: String) =
        rows(data.getJSONArray(key)).map {
            MenuEntry(
                it.getString("id"),
                it.getString("title"),
                it.getString("icon"),
                it.optString("badge"),
            )
        }

    private fun money(value: JSONObject) =
        MoneyValue(
            DecimalFormat("#,##0.00")
                .format(
                    BigDecimal(value.getString("amountMinor"))
                        .movePointLeft(value.getInt("minorUnit"))
                )
        )

    private fun fraction(a: JSONObject, b: JSONObject, share: Boolean = false): Float? {
        val x = BigDecimal(a.getString("amountMinor")).movePointLeft(a.getInt("minorUnit"))
        val y = BigDecimal(b.getString("amountMinor")).movePointLeft(b.getInt("minorUnit"))
        val denominator = if (share) x + y else y
        return if (denominator.signum() <= 0) null
        else x.divide(denominator, 5, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    }

    override suspend fun home() =
        api.request("home.content").let { data ->
            HomeContent(
                data.getString("searchPlaceholder"),
                menu(data, "quickServices"),
                rows(data.getJSONArray("banners")).map {
                    Banner(
                        it.getString("id"),
                        it.getString("title"),
                        it.getString("subtitle"),
                        it.getString("assetKey"),
                    )
                },
                menu(data, "services"),
                menu(data, "shortcuts"),
                rows(data.getJSONArray("categories")).map {
                    Category(it.getString("id"), it.getString("title"))
                },
                rows(data.getJSONArray("sections")).map { section ->
                    HomeSection(
                        section.getString("id"),
                        section.getString("title"),
                        section.getString("subtitle"),
                        section.getString("style"),
                        rows(section.getJSONArray("entries")).map {
                            HomeTile(
                                it.getString("id"),
                                it.getString("title"),
                                it.getString("subtitle"),
                                it.getString("icon"),
                                it.getString("assetKey"),
                                it.getString("tone"),
                            )
                        },
                    )
                },
            )
        }

    override suspend fun unread() = api.request("message.unread").getInt("unreadCount")

    override suspend fun wealth() =
        api.request("wealth.content").let { data ->
            WealthContent(
                menu(data, "services"),
                rows(data.getJSONArray("categories")).map {
                    Category(it.getString("id"), it.getString("title"))
                },
                rows(data.getJSONArray("marketItems")).map {
                    it.getString("id") to it.getString("title")
                },
            )
        }

    override suspend fun products(category: String) =
        api.request("product.list", mapOf("category" to category, "page" to "1", "pageSize" to "3"))
            .let { data ->
                ProductPage(
                    rows(data.getJSONArray("items")).take(3).map {
                        ProductBrief(
                            it.getString("id"),
                            it.getString("name"),
                            it.getString("term"),
                            it.getString("riskLevel"),
                            BigDecimal(it.getString("ratio"))
                                .multiply(BigDecimal(100))
                                .stripTrailingZeros()
                                .toPlainString() + "%",
                            it.getString("indicatorLabel"),
                            it.getString("minimum"),
                            it.getString("distribution"),
                            it.getString("riskText"),
                        )
                    },
                    data.getInt("total"),
                )
            }

    override suspend fun assets(): AssetOverview =
        assetLock.withLock {
            val epoch = api.epoch()
            assetCache?.takeIf { it.first == epoch }?.second
                ?: api.request("asset.summary")
                    .let {
                        AssetOverview(
                            money(it.getJSONObject("total")),
                            money(it.getJSONObject("income")),
                        )
                    }
                    .also { assetCache = epoch to it }
        }

    override suspend fun member() =
        MemberContent(menu(api.request("member.content"), "benefitEntries"))

    override suspend fun identity(member: Boolean): Identity =
        api.request(if (member) "member.summary" else "profile.summary").let { data ->
            val p = data.getJSONObject("tierProgress")
            Identity(
                data.getString("nickname"),
                data.getInt("level"),
                data.getString("tierName"),
                data.getString("carbonLabel"),
                listOf("bankCards", "todos", "coupons", "points", "energy").map { data.getInt(it) },
                TierProgress(
                    money(p.getJSONObject("current")),
                    money(p.getJSONObject("nextThreshold")),
                    p.getBoolean("isTopTier"),
                    p.getString("asOf"),
                    if (p.getBoolean("isTopTier")) 1f
                    else fraction(p.getJSONObject("current"), p.getJSONObject("nextThreshold")),
                ),
            )
        }

    private fun status(data: JSONObject) =
        CheckinStatus(
            data.getString("businessDate"),
            data.getBoolean("checkedToday"),
            data.getInt("consecutiveDays"),
            data.getInt("energy"),
            data.getInt("awardedEnergy"),
            data.getBoolean("alreadyChecked"),
            rows(data.getJSONArray("calendar")).map {
                CheckinDay(it.getString("date"), it.getBoolean("checked"))
            },
            data.getString("rewardRule"),
        )

    override suspend fun checkin() = status(api.request("checkin.status"))

    override suspend fun submit(date: String, key: String) =
        status(
            api.request(
                "checkin.submit",
                body = JSONObject().put("expectedBusinessDate", date).put("idempotencyKey", key),
            )
        )

    override suspend fun financial(): FinancialOverview =
        api.request("profile.financialOverview").let { data ->
            val b = data.getJSONObject("creditBill")
            val l = data.getJSONObject("loanOffer")
            check(l.getBoolean("marketingOnly"))
            FinancialOverview(
                data.getString("month"),
                money(data.getJSONObject("monthlyIncome")),
                money(data.getJSONObject("monthlyExpense")),
                CreditBill(
                    b.getBoolean("hasCard"),
                    b.optJSONObject("unpaid")?.let(::money),
                    b.optString("dueDate").takeUnless { it == "null" || it.isEmpty() },
                ),
                LoanOffer(
                    money(l.getJSONObject("limit")),
                    BigDecimal(l.getString("annualRate"))
                        .multiply(BigDecimal(100))
                        .stripTrailingZeros()
                        .toPlainString() + "%",
                ),
                fraction(
                    data.getJSONObject("monthlyIncome"),
                    data.getJSONObject("monthlyExpense"),
                    true,
                ),
            )
        }
}
