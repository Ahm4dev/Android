package com.auditcafe.wallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [Account::class, Category::class, Txn::class, MerchantMap::class,
        Goal::class, Rate::class, Recurring::class, Debt::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun txns(): TxnDao
    abstract fun merchants(): MerchantDao
    abstract fun goals(): GoalDao
    abstract fun rates(): RateDao
    abstract fun recurring(): RecurringDao
    abstract fun debts(): DebtDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        /** حذف جداول ميزة قراءة الرسائل (الحركات المقترحة وقواعد البنوك). بقية البيانات لا تتأثر. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `suggested`")
                db.execSQL("DROP TABLE IF EXISTS `bank_rules`")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`person` TEXT NOT NULL, `amount` REAL NOT NULL, `paid` REAL NOT NULL, `currency` TEXT NOT NULL, " +
                        "`lent` INTEGER NOT NULL, `date` INTEGER NOT NULL, `dueDate` INTEGER, `note` TEXT NOT NULL)"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `attachment` TEXT")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `amount` REAL NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER, " +
                        "`merchant` TEXT NOT NULL, `note` TEXT NOT NULL, `period` TEXT NOT NULL, `nextDate` INTEGER NOT NULL, " +
                        "`anchorDay` INTEGER NOT NULL, `active` INTEGER NOT NULL)"
                )
            }
        }

        fun get(ctx: Context): AppDatabase =
            inst ?: synchronized(this) {
                inst ?: build(ctx.applicationContext).also { inst = it }
            }

        fun dbFile(ctx: Context): File = ctx.getDatabasePath("wallet.db")

        /** يُستعمل قبل استبدال ملف القاعدة عند الاستعادة. */
        fun closeAndReset() {
            synchronized(this) { inst?.close(); inst = null }
        }

        private fun build(c: Context): AppDatabase =
            Room.databaseBuilder(c, AppDatabase::class.java, "wallet.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build()

        private fun seed(db: SupportSQLiteDatabase) {
            val exp = listOf(
                "cat_food" to "🍽️", "cat_transport" to "🚗", "cat_bills" to "🧾", "cat_rent" to "🏠",
                "cat_shopping" to "🛒", "cat_health" to "💊", "cat_fun" to "🎬", "cat_edu" to "🎓",
                "cat_telecom" to "📱", "cat_clothes" to "👕", "cat_gifts" to "🎁", "cat_travel" to "✈️",
                "cat_family" to "👨‍👩‍👧", "cat_cash" to "🏧", "cat_transfer" to "🔁", "cat_other_exp" to "📦"
            )
            val inc = listOf(
                "cat_salary" to "💼", "cat_bonus" to "🎉", "cat_refund" to "↩️", "cat_other_inc" to "💰"
            )
            exp.forEach {
                db.execSQL("INSERT INTO categories(name,isIncome,icon,monthlyBudget) VALUES (?,0,?,0)", arrayOf<Any>(it.first, it.second))
            }
            inc.forEach {
                db.execSQL("INSERT INTO categories(name,isIncome,icon,monthlyBudget) VALUES (?,1,?,0)", arrayOf<Any>(it.first, it.second))
            }
            db.execSQL("INSERT INTO accounts(name,type,currency,initialBalance,last4,archived) VALUES ('acc_cash','CASH','EGP',0,'',0)")
        }
    }
}
