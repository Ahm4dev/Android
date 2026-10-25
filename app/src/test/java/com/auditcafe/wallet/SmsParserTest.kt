package com.auditcafe.wallet

import com.auditcafe.wallet.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SmsParserTest {
    private val now = Calendar.getInstance().apply { set(2026, 9, 25, 13, 0, 0) }.timeInMillis
    private fun p(s: String) = SmsParser.parse(s, now, null)

    @Test fun englishDebit() {
        val r = p("CIB: Your card ending 1234 was debited EGP 1,250.50 at AMAZON on 12/10/2026 14:35. Available balance EGP 8,400.00")!!
        assertEquals("DEBIT", r.kind); assertEquals(1250.5, r.amount, 0.001); assertEquals("EGP", r.currency)
        assertEquals("1234", r.last4); assertEquals("AMAZON", r.merchant); assertEquals(8400.0, r.balance!!, 0.001)
    }

    @Test fun arabicDebitMerchant() {
        val r = p("تم خصم مبلغ 250.00 جنيه من بطاقتك المنتهية ب 5678 لدى كارفور بتاريخ 2026-10-20. الرصيد المتاح 12,300.75 جنيه")!!
        assertEquals("DEBIT", r.kind); assertEquals(250.0, r.amount, 0.001)
        assertEquals("5678", r.last4); assertEquals("كارفور", r.merchant); assertEquals(12300.75, r.balance!!, 0.001)
    }

    @Test fun credit() {
        val r = p("Your a/c XX4321 is credited with EGP 15,000.00 salary. Avail bal: EGP 20,000.00")!!
        assertEquals("CREDIT", r.kind); assertEquals(15000.0, r.amount, 0.001); assertEquals("4321", r.last4)
    }

    @Test fun atm() {
        val r = p("ATM withdrawal of EGP 2,000 from card **9876. Balance EGP 3,000")!!
        assertEquals("ATM", r.kind); assertEquals(2000.0, r.amount, 0.001); assertEquals("9876", r.last4)
    }

    @Test fun arabicIndicDigitsTransfer() {
        val r = p("٢٥٠ جنيه تم تحويلها الى حساب 1111 عبر انستاباي. رصيدك ١٠٠٠ جنيه")!!
        assertEquals("TRANSFER", r.kind); assertEquals(250.0, r.amount, 0.001); assertEquals(1000.0, r.balance!!, 0.001)
    }

    @Test fun otpAndPromoIgnored() {
        assertNull(p("Your OTP is 123456. Do not share it"))
        assertNull(p("عرض خاص! خصم 50% على كل المنتجات"))
    }

    @Test fun merchantNotPolluted() {
        assertEquals("STARBUCKS", p("تم شراء بمبلغ 85.5 EGP من STARBUCKS بالبطاقة المنتهية 4455")!!.merchant)
        assertEquals("فودافون", p("تم خصم 1200 جنيه من حسابك لدى فودافون بتاريخ 2026-10-24 الرصيد 800 جنيه")!!.merchant)
    }

    @Test fun foreignCurrency() {
        val r = p("Purchase of USD 49.99 at NETFLIX.COM card x1010. Balance USD 200")!!
        assertEquals("USD", r.currency); assertEquals(49.99, r.amount, 0.001)
    }

    @Test fun balanceIsNotAmount() {
        val r = p("تم خصم من بطاقتك 99 جنيه. الرصيد 500")!!
        assertEquals(99.0, r.amount, 0.001); assertEquals(500.0, r.balance!!, 0.001)
        assertNotNull(r)
    }
}
