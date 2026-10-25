package com.youssef.wallet.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AccountBalance(val accountId: Long, val balance: Double)
data class CatTotal(val categoryId: Long?, val currency: String, val total: Double)
data class TypeTotal(val type: String, val currency: String, val total: Double)
data class MonthTotal(val ym: String, val type: String, val currency: String, val total: Double)
data class AmtRow(val categoryId: Long?, val amount: Double)
data class CurTotal(val currency: String, val total: Double, val n: Int)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived=0 ORDER BY id") fun all(): Flow<List<Account>>
    @Query("SELECT * FROM accounts WHERE archived=0 ORDER BY id") suspend fun list(): List<Account>
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun get(id: Long): Account?
    @Query("SELECT * FROM accounts WHERE last4=:l4 AND last4!='' AND archived=0 LIMIT 1") suspend fun byLast4(l4: String): Account?
    @Query("SELECT * FROM accounts WHERE name=:n LIMIT 1") suspend fun byName(n: String): Account?
    @Insert suspend fun insert(a: Account): Long
    @Update suspend fun update(a: Account)
    @Query("DELETE FROM accounts WHERE id=:id") suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id") fun all(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun list(): List<Category>
    @Query("SELECT * FROM categories WHERE name=:n LIMIT 1") suspend fun byName(n: String): Category?
    @Insert suspend fun insert(c: Category): Long
    @Update suspend fun update(c: Category)
    @Query("DELETE FROM categories WHERE id=:id") suspend fun delete(id: Long)
}

@Dao
interface TxnDao {
    @Insert suspend fun insert(t: Txn): Long
    @Insert suspend fun insertAll(list: List<Txn>)
    @Update suspend fun update(t: Txn)
    @Query("DELETE FROM transactions WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM transactions WHERE accountId=:id OR toAccountId=:id") suspend fun deleteByAccount(id: Long)
    @Query("UPDATE transactions SET categoryId=NULL WHERE categoryId=:id") suspend fun clearCategory(id: Long)
    @Query("SELECT COUNT(*) FROM transactions WHERE hash=:h") suspend fun hashCount(h: String): Int
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun get(id: Long): Txn?
    @Query("SELECT attachment FROM transactions WHERE accountId=:id OR toAccountId=:id") suspend fun attachmentsOfAccount(id: Long): List<String?>
    @Query("SELECT * FROM transactions WHERE type='EXPENSE' AND merchant!='' AND date >= :from ORDER BY date LIMIT 5000")
    suspend fun expensesWithMerchant(from: Long): List<Txn>
    @Query("SELECT * FROM transactions WHERE type='EXPENSE' AND date BETWEEN :from AND :to ORDER BY amount DESC LIMIT :n")
    suspend fun topExpenses(from: Long, to: Long, n: Int): List<Txn>
    @Query(
        """SELECT a.currency AS currency, SUM(t.amount) AS total, COUNT(*) AS n
           FROM transactions t JOIN accounts a ON a.id=t.accountId
           WHERE t.type=:type AND t.date BETWEEN :from AND :to
             AND (:cat=0 OR t.categoryId=:cat)
             AND (:q='' OR t.merchant LIKE '%'||:q||'%' OR t.note LIKE '%'||:q||'%')
           GROUP BY a.currency"""
    )
    suspend fun sumWhere(type: String, from: Long, to: Long, cat: Long, q: String): List<CurTotal>

    @Query("SELECT COUNT(*) FROM transactions WHERE source LIKE :other AND amount=:amount AND date BETWEEN :from AND :to")
    suspend fun similarOther(amount: Double, from: Long, to: Long, other: String): Int

    @Query(
        """SELECT * FROM transactions
           WHERE (:q='' OR merchant LIKE '%'||:q||'%' OR note LIKE '%'||:q||'%' OR CAST(amount AS TEXT) LIKE '%'||:q||'%')
             AND (:type='' OR type=:type)
             AND (:acc=0 OR accountId=:acc OR toAccountId=:acc)
             AND (:cat=0 OR categoryId=:cat)
             AND date BETWEEN :from AND :to
           ORDER BY date DESC, id DESC"""
    )
    fun paged(q: String, type: String, acc: Long, cat: Long, from: Long, to: Long): PagingSource<Int, Txn>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC LIMIT :n") fun recent(n: Int): Flow<List<Txn>>
    @Query("SELECT COUNT(*) FROM transactions") fun count(): Flow<Int>
    @Query("SELECT * FROM transactions WHERE id>:afterId ORDER BY id LIMIT :limit") suspend fun chunk(afterId: Long, limit: Int): List<Txn>

    @Query(
        """SELECT a.id AS accountId,
              a.initialBalance
              + COALESCE((SELECT SUM(amount) FROM transactions WHERE accountId=a.id AND type='INCOME'),0)
              - COALESCE((SELECT SUM(amount) FROM transactions WHERE accountId=a.id AND type IN ('EXPENSE','TRANSFER')),0)
              + COALESCE((SELECT SUM(COALESCE(toAmount,amount)) FROM transactions WHERE toAccountId=a.id AND type='TRANSFER'),0) AS balance
           FROM accounts a"""
    )
    fun balances(): Flow<List<AccountBalance>>

    @Query(
        """SELECT t.categoryId AS categoryId, a.currency AS currency, SUM(t.amount) AS total
           FROM transactions t JOIN accounts a ON a.id=t.accountId
           WHERE t.type=:type AND t.date BETWEEN :from AND :to
           GROUP BY t.categoryId, a.currency"""
    )
    fun catTotals(type: String, from: Long, to: Long): Flow<List<CatTotal>>

    @Query(
        """SELECT t.type AS type, a.currency AS currency, SUM(t.amount) AS total
           FROM transactions t JOIN accounts a ON a.id=t.accountId
           WHERE t.type IN ('EXPENSE','INCOME') AND t.date BETWEEN :from AND :to
           GROUP BY t.type, a.currency"""
    )
    fun typeTotals(from: Long, to: Long): Flow<List<TypeTotal>>

    @Query(
        """SELECT strftime('%Y-%m', t.date/1000, 'unixepoch', 'localtime') AS ym, t.type AS type,
                  a.currency AS currency, SUM(t.amount) AS total
           FROM transactions t JOIN accounts a ON a.id=t.accountId
           WHERE t.type IN ('EXPENSE','INCOME') AND t.date >= :from
           GROUP BY ym, t.type, a.currency ORDER BY ym"""
    )
    fun monthTotals(from: Long): Flow<List<MonthTotal>>

    @Query("SELECT categoryId AS categoryId, amount AS amount FROM transactions WHERE type='EXPENSE' AND date >= :from")
    suspend fun expenseRows(from: Long): List<AmtRow>

    @Query("SELECT * FROM transactions WHERE type='EXPENSE' AND date >= :from ORDER BY date DESC LIMIT 200")
    suspend fun recentExpenses(from: Long): List<Txn>
}

@Dao
interface SuggestedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(s: Suggested): Long
    @Update suspend fun update(s: Suggested)
    @Query("SELECT COUNT(*) FROM suggested WHERE hash=:h") suspend fun hashCount(h: String): Int
    @Query("SELECT * FROM suggested WHERE status='PENDING' ORDER BY date DESC") fun pending(): Flow<List<Suggested>>
    @Query("SELECT COUNT(*) FROM suggested WHERE status='PENDING'") suspend fun pendingCountNow(): Int
    @Query("SELECT COUNT(*) FROM suggested WHERE amount=:amount AND kind=:kind AND date BETWEEN :from AND :to AND source!=:source")
    suspend fun similarOther(amount: Double, kind: String, from: Long, to: Long, source: String): Int
    @Query("UPDATE suggested SET status=:s, body='' WHERE id=:id") suspend fun setStatus(id: Long, s: String)
    @Query("UPDATE suggested SET status='IGNORED', body='' WHERE status='PENDING'") suspend fun ignoreAll()
}

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchant_map WHERE `key`=:k") suspend fun get(k: String): MerchantMap?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(m: MerchantMap)
}

@Dao
interface BankRuleDao {
    @Query("SELECT * FROM bank_rules") fun all(): Flow<List<BankRuleRow>>
    @Query("SELECT * FROM bank_rules") suspend fun list(): List<BankRuleRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(r: BankRuleRow)
    @Query("DELETE FROM bank_rules WHERE name=:n") suspend fun delete(n: String)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY id") fun all(): Flow<List<Recurring>>
    @Query("SELECT id FROM recurring WHERE active=1 AND nextDate<=:now") suspend fun dueIds(now: Long): List<Long>
    @Query("SELECT COUNT(*) FROM recurring WHERE active=1 AND type='EXPENSE' AND nextDate>:now AND nextDate<=:until")
    suspend fun dueSoonCount(now: Long, until: Long): Int
    @Query("SELECT * FROM recurring WHERE id=:id") suspend fun get(id: Long): Recurring?
    @Insert suspend fun insert(r: Recurring): Long
    @Update suspend fun update(r: Recurring)
    @Query("DELETE FROM recurring WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM recurring WHERE accountId=:id") suspend fun deleteByAccount(id: Long)
    @Query("UPDATE recurring SET categoryId=NULL WHERE categoryId=:id") suspend fun clearCategory(id: Long)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY (paid>=amount), COALESCE(dueDate, 9223372036854775807), id DESC") fun all(): Flow<List<Debt>>
    @Query("SELECT COUNT(*) FROM debts WHERE paid<amount AND dueDate IS NOT NULL AND dueDate<=:until")
    suspend fun dueSoonCount(until: Long): Int
    @Insert suspend fun insert(d: Debt): Long
    @Insert suspend fun insertAll(l: List<Debt>)
    @Update suspend fun update(d: Debt)
    @Query("DELETE FROM debts WHERE id=:id") suspend fun delete(id: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id") fun all(): Flow<List<Goal>>
    @Insert suspend fun insert(g: Goal): Long
    @Update suspend fun update(g: Goal)
    @Query("DELETE FROM goals WHERE id=:id") suspend fun delete(id: Long)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates") fun all(): Flow<List<Rate>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: Rate)
    @Query("DELETE FROM rates") suspend fun clear()
}
