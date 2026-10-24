package com.sudhanshu.expense.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sudhanshu.expense.data.model.CategoryBudgetEntity
import com.sudhanshu.expense.data.model.ExpenseEntity
import com.sudhanshu.expense.data.model.UserProfileEntity
import com.sudhanshu.expense.data.repository.ExpenseRepository
import com.sudhanshu.expense.data.repository.UniversalOperationResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class CategoryProgress(
    val category: CategoryBudgetEntity,
    val spent: Double,
    val left: Double,
    val progressFraction: Float
)

data class ExpenseUiState(
    val netWorth: Double = 13877.0,
    val spentThisMonth: Double = 0.0,
    val monthlyCap: Double = 35000.0,
    val safeDailySpend: Double = 0.0,
    val daysLeftInMonth: Int = 1,
    val categoryProgressList: List<CategoryProgress> = emptyList(),
    val recentTransactions: List<ExpenseEntity> = emptyList(),
    val isProcessing: Boolean = false,
    val infoBanner: String? = null,
    val errorBanner: String? = null,
    val currentProfile: UserProfileEntity = UserProfileEntity()
)

class ExpenseViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    private val _infoBanner = MutableStateFlow<String?>(null)
    private val _errorBanner = MutableStateFlow<String?>(null)

    private data class DbSnapshot(
        val profile: UserProfileEntity?,
        val monthExpenses: List<ExpenseEntity>,
        val categories: List<CategoryBudgetEntity>,
        val allExpenses: List<ExpenseEntity>
    )

    private val dbDataFlow = combine(
        repository.getUserProfile(),
        repository.getCurrentMonthExpenses(),
        repository.getCategoryBudgets(),
        repository.getAllExpenses()
    ) { profile, monthExpenses, categories, allExpenses ->
        DbSnapshot(profile, monthExpenses, categories, allExpenses)
    }

    val uiState: StateFlow<ExpenseUiState> = combine(
        dbDataFlow,
        _isProcessing,
        _infoBanner,
        _errorBanner
    ) { db, isProcessing, info, error ->
        val activeProfile = db.profile ?: UserProfileEntity()
        val totalSpent = db.monthExpenses
            .filter { it.type == ExpenseEntity.TYPE_EXPENSE }
            .sumOf { it.amount }

        val calendar = Calendar.getInstance()
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysLeft = maxOf(1, totalDays - currentDay + 1)

        val remainingBudget = maxOf(0.0, activeProfile.monthlyCap - totalSpent)
        val safeDaily = remainingBudget / daysLeft

        val progressList = db.categories.map { cat ->
            val spentInCat = db.monthExpenses
                .filter { it.category.equals(cat.categoryName, ignoreCase = true) && it.type == ExpenseEntity.TYPE_EXPENSE }
                .sumOf { it.amount }
            val left = maxOf(0.0, cat.monthlyLimit - spentInCat)
            val fraction = if (cat.monthlyLimit > 0) {
                (spentInCat / cat.monthlyLimit).coerceIn(0.0, 1.0).toFloat()
            } else {
                0f
            }
            CategoryProgress(
                category = cat,
                spent = spentInCat,
                left = left,
                progressFraction = fraction
            )
        }

        ExpenseUiState(
            netWorth = activeProfile.currentNetWorth,
            spentThisMonth = totalSpent,
            monthlyCap = activeProfile.monthlyCap,
            safeDailySpend = safeDaily,
            daysLeftInMonth = daysLeft,
            categoryProgressList = progressList,
            recentTransactions = db.allExpenses,
            isProcessing = isProcessing,
            infoBanner = info,
            errorBanner = error,
            currentProfile = activeProfile
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExpenseUiState()
    )

    fun processUniversalInput(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _isProcessing.value = true
            _infoBanner.value = null
            _errorBanner.value = null

            val result = repository.processUniversalInput(input)
            result.onSuccess { op ->
                when (op) {
                    is UniversalOperationResult.TransactionLogged -> {
                        val sign = if (op.expense.type == ExpenseEntity.TYPE_INCOME) "+" else "-"
                        _infoBanner.value = "Logged ${sign}₹${op.expense.amount.toInt()} (${op.expense.merchant}) under ${op.expense.category}"
                    }
                    is UniversalOperationResult.CategoryLimitUpdated -> {
                        _infoBanner.value = "Updated ${op.categoryName} limit to ₹${op.newLimit.toInt()}"
                    }
                    is UniversalOperationResult.CategoryAdded -> {
                        _infoBanner.value = "Added category '${op.categoryName}' (Limit: ₹${op.limit.toInt()})"
                    }
                    is UniversalOperationResult.CategoryDeleted -> {
                        _infoBanner.value = "Deleted category '${op.categoryName}'"
                    }
                    is UniversalOperationResult.MonthlyCapUpdated -> {
                        _infoBanner.value = "Updated Monthly Spend Cap to ₹${op.newCap.toInt()}"
                    }
                    is UniversalOperationResult.NetWorthUpdated -> {
                        _infoBanner.value = "Updated Liquid Net Worth to ₹${op.newNetWorth.toInt()}"
                    }
                }
            }.onFailure { err ->
                _errorBanner.value = "Failed: ${err.message}"
            }

            _isProcessing.value = false
        }
    }

    fun updateCategoryLimit(categoryName: String, newLimit: Double) {
        viewModelScope.launch {
            repository.updateCategoryLimit(categoryName, newLimit)
            _infoBanner.value = "Updated $categoryName limit to ₹${newLimit.toInt()}"
        }
    }

    fun addCategory(categoryName: String, monthlyLimit: Double) {
        viewModelScope.launch {
            repository.addCategory(categoryName, monthlyLimit)
            _infoBanner.value = "Added category '$categoryName' (Limit: ₹${monthlyLimit.toInt()})"
        }
    }

    fun deleteCategory(categoryName: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryName)
            _infoBanner.value = "Deleted category '$categoryName'"
        }
    }

    fun deleteTransaction(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            _infoBanner.value = "Deleted transaction of ₹${expense.amount.toInt()}"
        }
    }

    fun updateNetWorth(newNetWorth: Double) {
        viewModelScope.launch {
            repository.updateNetWorth(newNetWorth)
        }
    }

    fun updateMonthlyCap(newCap: Double) {
        viewModelScope.launch {
            repository.updateMonthlyCap(newCap)
        }
    }

    fun updateApiSettings(apiKey: String, provider: String) {
        viewModelScope.launch {
            repository.updateApiSettings(apiKey, provider)
            _infoBanner.value = "AI Settings Updated ($provider)"
        }
    }

    fun dismissBanners() {
        _infoBanner.value = null
        _errorBanner.value = null
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
                return ExpenseViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
