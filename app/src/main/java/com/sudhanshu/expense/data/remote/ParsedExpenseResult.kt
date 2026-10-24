package com.sudhanshu.expense.data.remote

import com.google.gson.annotations.SerializedName

data class ParsedExpenseResult(
    @SerializedName("action") val action: String = ACTION_LOG_TRANSACTION,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("category") val category: String = "Shopping & Misc",
    @SerializedName("merchant") val merchant: String = "General",
    @SerializedName("note") val note: String = "",
    @SerializedName("type") val type: String = "EXPENSE"
) {
    companion object {
        const val ACTION_LOG_TRANSACTION = "LOG_TRANSACTION"
        const val ACTION_UPDATE_CATEGORY_LIMIT = "UPDATE_CATEGORY_LIMIT"
        const val ACTION_UPDATE_MONTHLY_CAP = "UPDATE_MONTHLY_CAP"
        const val ACTION_UPDATE_NET_WORTH = "UPDATE_NET_WORTH"
        const val ACTION_ADD_CATEGORY = "ADD_CATEGORY"
        const val ACTION_DELETE_CATEGORY = "DELETE_CATEGORY"
    }
}
