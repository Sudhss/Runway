package com.sudhanshu.expense.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val merchant: String,
    val note: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = TYPE_EXPENSE // "EXPENSE" or "INCOME"
) {
    companion object {
        const val TYPE_EXPENSE = "EXPENSE"
        const val TYPE_INCOME = "INCOME"
    }
}
