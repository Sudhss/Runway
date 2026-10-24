package com.sudhanshu.expense.data.local

import androidx.room.*
import com.sudhanshu.expense.data.model.CategoryBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun getAllBudgets(): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets")
    suspend fun getAllBudgetsSnapshot(): List<CategoryBudgetEntity>

    @Query("SELECT * FROM category_budgets WHERE categoryName = :name LIMIT 1")
    suspend fun getBudgetByName(name: String): CategoryBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(budget: CategoryBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(budgets: List<CategoryBudgetEntity>)

    @Query("UPDATE category_budgets SET monthlyLimit = :newLimit WHERE categoryName = :categoryName")
    suspend fun updateLimit(categoryName: String, newLimit: Double)

    @Query("DELETE FROM category_budgets WHERE categoryName = :categoryName")
    suspend fun deleteByName(categoryName: String)

    @Delete
    suspend fun delete(budget: CategoryBudgetEntity)
}
