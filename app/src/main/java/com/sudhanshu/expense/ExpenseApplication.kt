package com.sudhanshu.expense

import android.app.Application
import com.sudhanshu.expense.data.local.AppDatabase
import com.sudhanshu.expense.data.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class ExpenseApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { ExpenseRepository(database) }
}
