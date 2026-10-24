package com.sudhanshu.expense.data.local

import androidx.room.*
import com.sudhanshu.expense.data.model.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET currentNetWorth = :newBalance WHERE id = 1")
    suspend fun updateNetWorth(newBalance: Double)

    @Query("UPDATE user_profile SET monthlyCap = :newCap WHERE id = 1")
    suspend fun updateMonthlyCap(newCap: Double)

    @Query("UPDATE user_profile SET apiKey = :newKey, apiProvider = :provider WHERE id = 1")
    suspend fun updateApiSettings(newKey: String, provider: String)
}
