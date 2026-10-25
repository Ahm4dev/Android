package com.auditcafe.wallet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "BANK",      // BANK, CASH, SAVING, GOLD
    val currency: String = "EGP",
    val initialBalance: Double = 0.0,
    val last4: String = "",
    val archived: Boolean = false
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,               // مفتاح (cat_xxx) للتصنيفات الافتراضية أو اسم حر
    val isIncome: Boolean = false,
    val icon: String = "📦",
    val monthlyBudget: Double = 0.0
)

@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("hash")]
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,               // EXPENSE, INCOME, TRANSFER
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long? = null,
    val toAmount: Double? = null,   // للتحويل بين عملتين مختلفتين
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val date: Long,
    val hash: String? = null,
    val source: String = "MANUAL",  // MANUAL, IMPORT, SMS_OK, SMS_AUTO, NOTIF_OK, NOTIF_AUTO, RECURRING
    val attachment: String? = null  // اسم ملف صورة الفاتورة داخل filesDir/receipts
)

@Entity(
    tableName = "suggested",
    indices = [Index(value = ["hash"], unique = true), Index("date"), Index("status")]
)
data class Suggested(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val bank: String,
    val body: String,
    val amount: Double,
    val currency: String,
    val kind: String,               // DEBIT, CREDIT, TRANSFER, ATM
    val last4: String?,
    val merchant: String,
    val date: Long,
    val balanceAfter: Double?,
    val hash: String,
    val accountId: Long?,
    val categoryId: Long?,
    val source: String = "SMS",
    val status: String = "PENDING"  // PENDING, ACCEPTED, IGNORED
)

@Entity(tableName = "merchant_map")
data class MerchantMap(
    @PrimaryKey val key: String,
    val categoryId: Long,
    val hits: Int = 1
)

@Entity(tableName = "bank_rules")
data class BankRuleRow(
    @PrimaryKey val name: String,
    val sender: String = "",
    val custom: Boolean = false,
    val trusted: Boolean = false,
    val debitWords: String = "",
    val creditWords: String = ""
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amount: Double,
    val paid: Double = 0.0,
    val currency: String = "EGP",
    val lent: Boolean = true,       // true: أنا أقرضت (يدين لي) / false: اقترضت (أدين له)
    val date: Long,
    val dueDate: Long? = null,
    val note: String = ""
)

@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "EXPENSE",   // EXPENSE, INCOME
    val amount: Double,
    val accountId: Long,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val period: String = "MONTHLY", // DAILY, WEEKLY, MONTHLY, YEARLY
    val nextDate: Long,
    val anchorDay: Int = 1,
    val active: Boolean = true
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val saved: Double = 0.0,
    val currency: String = "EGP",
    val icon: String = "🎯"
)

@Entity(tableName = "rates")
data class Rate(
    @PrimaryKey val currency: String,
    val perUnit: Double             // قيمة وحدة واحدة من العملة بالعملة الأساسية
)
