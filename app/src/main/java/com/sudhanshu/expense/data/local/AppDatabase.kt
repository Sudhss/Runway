package com.sudhanshu.expense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sudhanshu.expense.data.model.CategoryBudgetEntity
import com.sudhanshu.expense.data.model.ExpenseEntity
import com.sudhanshu.expense.data.model.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryBudgetEntity::class,
        UserProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "runway_expense_db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateDefaultData(
                            database.categoryBudgetDao(),
                            database.userProfileDao()
                        )
                    }
                }
            }

            private suspend fun populateDefaultData(
                categoryDao: CategoryBudgetDao,
                userProfileDao: UserProfileDao
            ) {
                val defaultCategories = listOf(
                    CategoryBudgetEntity("Food & Dining", 10000.0, "restaurant", "#FF5252"),
                    CategoryBudgetEntity("Travel & Auto", 4000.0, "directions_car", "#FFAB40"),
                    CategoryBudgetEntity("Housing & Rent", 17000.0, "home", "#448AFF"),
                    CategoryBudgetEntity("Health & Supplements", 2000.0, "fitness_center", "#69F0AE"),
                    CategoryBudgetEntity("Tech & Utilities", 3000.0, "computer", "#E040FB"),
                    CategoryBudgetEntity("Family & Debt", 100000.0, "payments", "#00E676"),
                    CategoryBudgetEntity("Shopping & Misc", 4000.0, "shopping_bag", "#FFD740")
                )
                categoryDao.insertAll(defaultCategories)

                userProfileDao.saveProfile(
                    UserProfileEntity(
                        id = 1,
                        currentNetWorth = 13877.0,
                        monthlyCap = 35000.0,
                        apiKey = "",
                        apiProvider = "GROQ"
                    )
                )
            }
        }
    }
}
