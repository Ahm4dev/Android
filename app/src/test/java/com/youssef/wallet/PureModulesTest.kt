package com.youssef.wallet

import com.youssef.wallet.ai.CatInfo
import com.youssef.wallet.ai.NlQuery
import com.youssef.wallet.ai.NlResult
import com.youssef.wallet.ai.SplitMath
import com.youssef.wallet.ai.SubscriptionDetector
import com.youssef.wallet.ai.TextKey
import com.youssef.wallet.ai.TxRec
import com.youssef.wallet.data.PinStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PureModulesTest {
    private val cats = listOf(
        CatInfo(1, "cat_food", "طعام", false), CatInfo(2, "cat_transport", "مواصلات", false),
        CatInfo(3, "cat_salary", "راتب", true), CatInfo(4, "cat_rent", "إيجار", false)
    )

    @Test fun nlFoodLastMonth() =
        assertEquals(NlResult("EXPENSE", "LAST", 1, ""), NlQuery.parse("كم صرفت على المطاعم الشهر الماضي", cats))

    @Test fun nlEnglishUber() =
        assertEquals(NlResult("EXPENSE", "MONTH", 2, ""), NlQuery.parse("how much did I spend on uber this month", cats))

    @Test fun nlIncomeYear() {
        val r = NlQuery.parse("دخلي من الراتب هذه السنة", cats)!!
        assertEquals("INCOME", r.type); assertEquals("YEAR", r.period); assertEquals(3L, r.categoryId)
    }

    @Test fun nlMerchant() {
        val r = NlQuery.parse("صرفت كام على نتفليكس", cats)!!
        assertEquals("نتفليكس", r.text); assertEquals(0L, r.categoryId)
    }

    @Test fun subscriptionMonthly() {
        val day = 86_400_000L
        val now = 100 * day
        val l = (0..3).map { TxRec("Netflix", 199.0, now - (3 - it) * 30 * day, 1, 9) }
        val h = SubscriptionDetector.detect(l, emptySet(), now)
        assertEquals(1, h.size); assertEquals("MONTHLY", h[0].period)
        assertTrue(SubscriptionDetector.detect(l, setOf(TextKey.merchantKey("Netflix")), now).isEmpty())
    }

    @Test fun irregularNotSubscription() {
        val day = 86_400_000L
        val now = 100 * day
        val l = listOf(TxRec("Cafe", 50.0, now - 40 * day, 1, null), TxRec("Cafe", 90.0, now - 20 * day, 1, null), TxRec("Cafe", 30.0, now - 3 * day, 1, null))
        assertTrue(SubscriptionDetector.detect(l, emptySet(), now).isEmpty())
    }

    @Test fun splitKeepsTotal() {
        val (mine, each) = SplitMath.split(100.0, 0.0, 3)
        assertEquals(33.34, mine, 0.001); assertEquals(33.33, each, 0.001)
        assertEquals(100.0, mine + each * 2, 0.001)
        val (m2, e2) = SplitMath.split(200.0, 10.0, 4)
        assertEquals(55.0, m2, 0.001); assertEquals(55.0, e2, 0.001)
    }

    @Test fun niceRound() {
        assertEquals(60.0, TextKey.niceRound(55.0), 0.001)
        assertEquals(1050.0, TextKey.niceRound(1010.0), 0.001)
        assertEquals(2100.0, TextKey.niceRound(2001.0), 0.001)
        assertEquals(0.0, TextKey.niceRound(0.0), 0.001)
    }

    @Test fun pinStore() {
        val h = PinStore.create("1234")
        assertTrue(PinStore.verify("1234", h)); assertFalse(PinStore.verify("1235", h)); assertFalse(PinStore.verify("1234", "bad"))
        assertFalse(PinStore.create("1234") == h) // salt عشوائي
    }
}
