package com.sudhanshu.expense.data.repository

import com.sudhanshu.expense.data.local.AppDatabase
import com.sudhanshu.expense.data.model.CategoryBudgetEntity
import com.sudhanshu.expense.data.model.ExpenseEntity
import com.sudhanshu.expense.data.model.UserProfileEntity
import com.sudhanshu.expense.data.remote.LlmParserService
import com.sudhanshu.expense.data.remote.ParsedExpenseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

sealed class UniversalOperationResult {
    data class TransactionLogged(val expense: ExpenseEntity) : UniversalOperationResult()
    data class CategoryLimitUpdated(val categoryName: String, val newLimit: Double) : UniversalOperationResult()
    data class CategoryAdded(val categoryName: String, val limit: Double) : UniversalOperationResult()
    data class CategoryDeleted(val categoryName: String) : UniversalOperationResult()
    data class MonthlyCapUpdated(val newCap: Double) : UniversalOperationResult()
    data class NetWorthUpdated(val newNetWorth: Double) : UniversalOperationResult()
}

class ExpenseRepository(
    private val database: AppDatabase,
    private val llmService: LlmParserService = LlmParserService()
) {
    private val expenseDao = database.expenseDao()
    private val categoryDao = database.categoryBudgetDao()
    private val profileDao = database.userProfileDao()

    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    fun getCategoryBudgets(): Flow<List<CategoryBudgetEntity>> = categoryDao.getAllBudgets()

    fun getUserProfile(): Flow<UserProfileEntity?> = profileDao.getUserProfile()

    fun getCurrentMonthExpenses(): Flow<List<ExpenseEntity>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return expenseDao.getExpensesSince(calendar.timeInMillis)
    }

    suspend fun processUniversalInput(input: String): Result<UniversalOperationResult> {
        val profile = profileDao.getUserProfile().firstOrNull() ?: UserProfileEntity()
        val allCategories = categoryDao.getAllBudgetsSnapshot()
        val categoryNames = allCategories.map { it.categoryName }

        val parseResult = llmService.parseTransaction(
            input = input,
            apiKey = profile.apiKey,
            provider = profile.apiProvider,
            knownCategories = categoryNames
        )

        return parseResult.mapCatching { parsed ->
            when (parsed.action) {
                ParsedExpenseResult.ACTION_UPDATE_CATEGORY_LIMIT -> {
                    val matched = findMatchingCategory(parsed.category, allCategories)
                    val targetName = matched?.categoryName ?: parsed.category.trim().replaceFirstChar { it.uppercase() }
                    if (matched != null) {
                        categoryDao.updateLimit(targetName, parsed.amount)
                    } else {
                        // Dynamically create the category with the new limit!
                        val newCat = CategoryBudgetEntity(
                            categoryName = targetName,
                            monthlyLimit = parsed.amount,
                            iconName = pickIconForCategory(targetName),
                            colorHex = pickColorForCategory(targetName)
                        )
                        categoryDao.insertOrUpdate(newCat)
                    }
                    UniversalOperationResult.CategoryLimitUpdated(targetName, parsed.amount)
                }

                ParsedExpenseResult.ACTION_UPDATE_MONTHLY_CAP -> {
                    profileDao.updateMonthlyCap(parsed.amount)
                    UniversalOperationResult.MonthlyCapUpdated(parsed.amount)
                }

                ParsedExpenseResult.ACTION_UPDATE_NET_WORTH -> {
                    profileDao.updateNetWorth(parsed.amount)
                    UniversalOperationResult.NetWorthUpdated(parsed.amount)
                }

                ParsedExpenseResult.ACTION_ADD_CATEGORY -> {
                    val name = parsed.category.trim().replaceFirstChar { it.uppercase() }
                    val newCat = CategoryBudgetEntity(
                        categoryName = name,
                        monthlyLimit = parsed.amount,
                        iconName = pickIconForCategory(name),
                        colorHex = pickColorForCategory(name)
                    )
                    categoryDao.insertOrUpdate(newCat)
                    UniversalOperationResult.CategoryAdded(name, parsed.amount)
                }

                ParsedExpenseResult.ACTION_DELETE_CATEGORY -> {
                    val matched = findMatchingCategory(parsed.category, allCategories)
                    val targetName = matched?.categoryName ?: parsed.category.trim()
                    categoryDao.deleteByName(targetName)
                    UniversalOperationResult.CategoryDeleted(targetName)
                }

                else -> {
                    // ACTION_LOG_TRANSACTION: Ensure category exists or dynamically create it!
                    val matched = findMatchingCategory(parsed.category, allCategories)
                    val finalCategoryName = if (matched != null) {
                        matched.categoryName
                    } else {
                        val name = parsed.category.trim().replaceFirstChar { it.uppercase() }
                        val newCat = CategoryBudgetEntity(
                            categoryName = name,
                            monthlyLimit = 0.0,
                            iconName = pickIconForCategory(name),
                            colorHex = pickColorForCategory(name)
                        )
                        categoryDao.insertOrUpdate(newCat)
                        name
                    }

                    val entity = ExpenseEntity(
                        amount = parsed.amount,
                        category = finalCategoryName,
                        merchant = parsed.merchant,
                        note = parsed.note,
                        type = parsed.type,
                        timestamp = System.currentTimeMillis()
                    )
                    expenseDao.insertExpense(entity)

                    // Auto-adjust Net Worth
                    val delta = if (parsed.type == ExpenseEntity.TYPE_INCOME) parsed.amount else -parsed.amount
                    val updatedNetWorth = profile.currentNetWorth + delta
                    profileDao.updateNetWorth(updatedNetWorth)

                    UniversalOperationResult.TransactionLogged(entity)
                }
            }
        }
    }

    private fun findMatchingCategory(query: String, list: List<CategoryBudgetEntity>): CategoryBudgetEntity? {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) return null

        // 1. Exact match
        list.find { it.categoryName.equals(cleanQuery, ignoreCase = true) }?.let { return it }

        // 2. Starts with / contains
        list.find { it.categoryName.lowercase().startsWith(cleanQuery) }?.let { return it }
        list.find { it.categoryName.lowercase().contains(cleanQuery) }?.let { return it }
        list.find { cleanQuery.contains(it.categoryName.lowercase()) }?.let { return it }

        // 3. Keyword heuristic
        if (cleanQuery.contains("food") || cleanQuery.contains("dining") || cleanQuery.contains("eat")) {
            list.find { it.categoryName.contains("Food", ignoreCase = true) }?.let { return it }
        }
        if (cleanQuery.contains("travel") || cleanQuery.contains("auto") || cleanQuery.contains("cab") || cleanQuery.contains("car")) {
            list.find { it.categoryName.contains("Travel", ignoreCase = true) }?.let { return it }
        }
        if (cleanQuery.contains("rent") || cleanQuery.contains("housing") || cleanQuery.contains("flat") || cleanQuery.contains("home")) {
            list.find { it.categoryName.contains("Rent", ignoreCase = true) || it.categoryName.contains("Housing", ignoreCase = true) }?.let { return it }
        }
        if (cleanQuery.contains("health") || cleanQuery.contains("med") || cleanQuery.contains("supplement") || cleanQuery.contains("gym") || cleanQuery.contains("fitness")) {
            list.find { it.categoryName.contains("Health", ignoreCase = true) || it.categoryName.contains("Gym", ignoreCase = true) }?.let { return it }
        }
        if (cleanQuery.contains("tech") || cleanQuery.contains("utility") || cleanQuery.contains("bill") || cleanQuery.contains("wifi")) {
            list.find { it.categoryName.contains("Tech", ignoreCase = true) }?.let { return it }
        }
        if (cleanQuery.contains("debt") || cleanQuery.contains("family") || cleanQuery.contains("loan") || cleanQuery.contains("emi")) {
            list.find { it.categoryName.contains("Family", ignoreCase = true) || it.categoryName.contains("Debt", ignoreCase = true) }?.let { return it }
        }

        return null
    }

    suspend fun updateCategoryLimit(categoryName: String, newLimit: Double) {
        categoryDao.updateLimit(categoryName, newLimit)
    }

    suspend fun addCategory(categoryName: String, monthlyLimit: Double) {
        val name = categoryName.trim().replaceFirstChar { it.uppercase() }
        val newCat = CategoryBudgetEntity(
            categoryName = name,
            monthlyLimit = monthlyLimit,
            iconName = pickIconForCategory(name),
            colorHex = pickColorForCategory(name)
        )
        categoryDao.insertOrUpdate(newCat)
    }

    suspend fun deleteCategory(categoryName: String) {
        categoryDao.deleteByName(categoryName)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
        val profile = profileDao.getUserProfile().firstOrNull() ?: return
        val delta = if (expense.type == ExpenseEntity.TYPE_INCOME) -expense.amount else expense.amount
        profileDao.updateNetWorth(profile.currentNetWorth + delta)
    }

    suspend fun updateNetWorth(newNetWorth: Double) {
        profileDao.updateNetWorth(newNetWorth)
    }

    suspend fun updateMonthlyCap(newCap: Double) {
        profileDao.updateMonthlyCap(newCap)
    }

    suspend fun updateApiSettings(apiKey: String, provider: String) {
        profileDao.updateApiSettings(apiKey, provider)
    }

    companion object {
        fun pickIconForCategory(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.contains("food") || lower.contains("dine") || lower.contains("restaurant") || lower.contains("cafe") -> "restaurant"
                lower.contains("travel") || lower.contains("auto") || lower.contains("car") || lower.contains("cab") || lower.contains("fuel") -> "directions_car"
                lower.contains("rent") || lower.contains("house") || lower.contains("home") || lower.contains("flat") -> "home"
                lower.contains("gym") || lower.contains("fitness") || lower.contains("sport") || lower.contains("workout") -> "fitness_center"
                lower.contains("tech") || lower.contains("code") || lower.contains("computer") || lower.contains("soft") -> "computer"
                lower.contains("debt") || lower.contains("loan") || lower.contains("family") || lower.contains("emi") -> "payments"
                lower.contains("book") || lower.contains("learn") || lower.contains("study") || lower.contains("course") -> "menu_book"
                lower.contains("game") || lower.contains("gaming") || lower.contains("steam") || lower.contains("play") -> "sports_esports"
                lower.contains("movie") || lower.contains("cinema") || lower.contains("entertain") || lower.contains("netflix") -> "movie"
                lower.contains("health") || lower.contains("med") || lower.contains("doctor") || lower.contains("pharmacy") -> "medical_services"
                else -> "shopping_bag"
            }
        }

        fun pickColorForCategory(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.contains("food") -> "#FF9800"
                lower.contains("travel") -> "#00E5FF"
                lower.contains("rent") || lower.contains("house") -> "#AB47BC"
                lower.contains("gym") || lower.contains("fitness") -> "#00E676"
                lower.contains("tech") -> "#2979FF"
                lower.contains("debt") || lower.contains("family") -> "#FF5252"
                lower.contains("book") || lower.contains("learn") -> "#FFD600"
                lower.contains("game") -> "#E040FB"
                lower.contains("movie") -> "#FF4081"
                lower.contains("health") || lower.contains("med") -> "#26A69A"
                else -> "#78909C"
            }
        }
    }
}
