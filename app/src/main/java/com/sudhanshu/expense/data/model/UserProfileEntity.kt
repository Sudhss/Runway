package com.sudhanshu.expense.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val currentNetWorth: Double = 13877.0,
    val monthlyCap: Double = 35000.0,
    val apiKey: String = "",
    val apiProvider: String = "GROQ" // "GROQ" or "GEMINI"
)
