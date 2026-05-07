package com.victoryfairy.server.kbo.collector

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.victoryfairy.server.config.AppProperties
import org.springframework.stereotype.Component

@Component
class KboSchedulePageClient(
    private val properties: AppProperties,
) {
    private val scheduleURL = "https://www.koreabaseball.com/Schedule/Schedule.aspx"

    fun fetchScheduleTableHtml(season: Int, month: Int, seriesType: KboSeriesType): String {
        require(month in 1..12) { "월 값은 1부터 12 사이여야 합니다." }
        Playwright.create().use { playwright ->
            playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true)).use { browser ->
                browser.newPage().use { page ->
                    val timeoutMillis = properties.kbo.refresh.timeoutSeconds.coerceAtLeast(1) * 1_000.0
                    page.setDefaultTimeout(timeoutMillis)
                    page.setDefaultNavigationTimeout(timeoutMillis)
                    page.navigate(scheduleURL)
                    page.waitForSelector("#tblScheduleList")
                    page.selectAndSettle("#ddlYear", season.toString())
                    page.selectAndSettle("#ddlMonth", month.toString().padStart(2, '0'))
                    page.selectAndSettle("#ddlSeries", seriesType.optionValue)
                    page.waitForSelector("#tblScheduleList")
                    return page.locator("#tblScheduleList").evaluate("element => element.outerHTML") as String
                }
            }
        }
    }

    private fun Page.selectAndSettle(selector: String, value: String) {
        locator(selector).selectOption(value)
        runCatching { waitForLoadState(LoadState.NETWORKIDLE, Page.WaitForLoadStateOptions().setTimeout(5_000.0)) }
        waitForTimeout(properties.kbo.scrapedDev.requestDelayMs.toDouble())
    }
}
