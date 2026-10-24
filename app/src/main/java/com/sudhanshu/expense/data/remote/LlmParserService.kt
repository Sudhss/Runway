package com.sudhanshu.expense.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LlmParserService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildSystemPrompt(knownCategories: List<String>): String {
        val catList = if (knownCategories.isNotEmpty()) {
            knownCategories.joinToString(", ") { "\"$it\"" }
        } else {
            "\"Food & Dining\", \"Travel & Auto\", \"Housing & Rent\", \"Health & Supplements\", \"Tech & Utilities\", \"Family & Debt\", \"Shopping & Misc\""
        }

        return """
            You are an intelligent financial assistant and expense parser for an Indian tech professional.
            Analyze the user's input, which could be an expense/income transaction OR a budget configuration command.

            If the user is requesting to change or set a budget limit, net worth, or category:
            - "change food limit to 5000" / "set food limit to 5000" -> action: "UPDATE_CATEGORY_LIMIT", category: "Food & Dining" (pick the matching category from known categories or specified name), amount: 5000.0
            - "set monthly cap to 40000" / "change monthly budget to 40000" -> action: "UPDATE_MONTHLY_CAP", amount: 40000.0
            - "set net worth to 50000" / "update net worth to 50000" -> action: "UPDATE_NET_WORTH", amount: 50000.0
            - "add category gym with limit 2500" -> action: "ADD_CATEGORY", category: "Gym", amount: 2500.0
            - "delete category gym" -> action: "DELETE_CATEGORY", category: "Gym"

            If the user is describing a financial transaction (spend or income):
            - action: "LOG_TRANSACTION"
            - amount: number (e.g. 350.0)
            - category: Pick the best fitting category from the user's known categories: [$catList]. If none fits, create a concise 1-2 word category name (e.g. "Gym", "Books", "Gaming").
            - merchant: merchant/store/app name (e.g. "Swiggy", "Uber", "Amazon", "Apollo", "Cult.fit", or "General")
            - note: short description
            - type: "EXPENSE" or "INCOME"

            Respond with ONLY a strict JSON object:
            {
              "action": "LOG_TRANSACTION" | "UPDATE_CATEGORY_LIMIT" | "UPDATE_MONTHLY_CAP" | "UPDATE_NET_WORTH" | "ADD_CATEGORY" | "DELETE_CATEGORY",
              "amount": number,
              "category": string,
              "merchant": string,
              "note": string,
              "type": "EXPENSE" | "INCOME"
            }
        """.trimIndent()
    }

    suspend fun parseTransaction(
        input: String,
        apiKey: String,
        provider: String,
        knownCategories: List<String> = emptyList()
    ): Result<ParsedExpenseResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.success(fallbackLocalParse(input, knownCategories))
        }

        try {
            val parsedResult = if (provider.equals("GEMINI", ignoreCase = true)) {
                callGemini(input, apiKey, knownCategories)
            } else {
                callGroq(input, apiKey, knownCategories)
            }
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e("LlmParserService", "LLM call failed, using local heuristic: ${e.message}")
            Result.success(fallbackLocalParse(input, knownCategories))
        }
    }

    private fun callGroq(input: String, apiKey: String, knownCategories: List<String>): ParsedExpenseResult {
        val prompt = buildSystemPrompt(knownCategories)
        val payload = JsonObject().apply {
            addProperty("model", "llama-3.1-8b-instant")
            addProperty("temperature", 0.1)
            val messages = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", prompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", input)
                })
            }
            add("messages", messages)
            add("response_format", JsonObject().apply {
                addProperty("type", "json_object")
            })
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Groq HTTP error: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")
        val json = gson.fromJson(responseBody, JsonObject::class.java)
        val content = json.getAsJsonArray("choices")
            .get(0).asJsonObject
            .getAsJsonObject("message")
            .get("content").asString

        return cleanAndParseJson(content)
    }

    private fun callGemini(input: String, apiKey: String, knownCategories: List<String>): ParsedExpenseResult {
        val prompt = buildSystemPrompt(knownCategories)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val payload = JsonObject().apply {
            val contents = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    val parts = com.google.gson.JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", "$prompt\n\nInput to parse: \"$input\"")
                        })
                    }
                    add("parts", parts)
                })
            }
            add("contents", contents)
            add("generationConfig", JsonObject().apply {
                addProperty("response_mime_type", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Gemini HTTP error: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")
        val json = gson.fromJson(responseBody, JsonObject::class.java)
        val content = json.getAsJsonArray("candidates")
            .get(0).asJsonObject
            .getAsJsonObject("content")
            .getAsJsonArray("parts")
            .get(0).asJsonObject
            .get("text").asString

        return cleanAndParseJson(content)
    }

    private fun cleanAndParseJson(raw: String): ParsedExpenseResult {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").trim()
        }
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }

        return gson.fromJson(clean, ParsedExpenseResult::class.java)
    }

    /**
     * Resilient offline heuristic parser when no API key is provided or offline.
     * Accurately parses commands (e.g. "change food limit to 5000", "set monthly cap to 40000")
     * as well as standard expense transactions.
     */
    fun fallbackLocalParse(input: String, knownCategories: List<String> = emptyList()): ParsedExpenseResult {
        val trimmed = input.trim()

        // 1. Delete Category command: e.g. "delete category gym", "remove category books"
        val deleteCatPattern = Pattern.compile("(?i)^(?:delete|remove)\\s+category\\s+([a-z0-9 &+\\-_]+)$")
        val deleteCatMatcher = deleteCatPattern.matcher(trimmed)
        if (deleteCatMatcher.find()) {
            val catName = deleteCatMatcher.group(1)?.trim() ?: ""
            return ParsedExpenseResult(
                action = ParsedExpenseResult.ACTION_DELETE_CATEGORY,
                category = catName
            )
        }

        // 2. Add Category command: e.g. "add category gym with limit 2500", "new category gaming limit 3000", "add category books"
        val addCatPattern = Pattern.compile("(?i)^(?:add|create|new)\\s+category\\s+([a-z0-9 &+\\-_]+?)(?:\\s+(?:with\\s+)?(?:limit|budget|cap)\\s+(?:of\\s+|to\\s+)?(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]+)?))?$")
        val addCatMatcher = addCatPattern.matcher(trimmed)
        if (addCatMatcher.find()) {
            val catName = addCatMatcher.group(1)?.trim() ?: ""
            val limitStr = addCatMatcher.group(2)
            val limit = limitStr?.toDoubleOrNull() ?: 0.0
            return ParsedExpenseResult(
                action = ParsedExpenseResult.ACTION_ADD_CATEGORY,
                category = catName.replaceFirstChar { it.uppercase() },
                amount = limit
            )
        }

        // 3. Monthly Cap command: e.g. "set monthly cap to 40000", "change monthly budget to 35000", "monthly cap 40000"
        val monthlyCapPattern = Pattern.compile("(?i)(?:change|set|update)?\\s*(?:monthly|month|overall|total)\\s+(?:cap|budget|limit|spend)\\s+(?:to|=|is)?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]+)?)")
        val monthlyCapMatcher = monthlyCapPattern.matcher(trimmed)
        if (monthlyCapMatcher.find()) {
            val cap = monthlyCapMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            return ParsedExpenseResult(
                action = ParsedExpenseResult.ACTION_UPDATE_MONTHLY_CAP,
                amount = cap
            )
        }

        // 4. Net Worth command: e.g. "set net worth to 50000", "update liquid net worth to 25000", "net worth 13877"
        val netWorthPattern = Pattern.compile("(?i)(?:change|set|update)?\\s*(?:current\\s+)?(?:liquid\\s+)?net\\s*worth\\s+(?:to|=|is)?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]+)?)")
        val netWorthMatcher = netWorthPattern.matcher(trimmed)
        if (netWorthMatcher.find()) {
            val nw = netWorthMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            return ParsedExpenseResult(
                action = ParsedExpenseResult.ACTION_UPDATE_NET_WORTH,
                amount = nw
            )
        }

        // 5. Category Limit command:
        // "change food limit to 5000"
        // "set travel budget to 3000"
        // "update food limit 5000"
        val catLimitPattern1 = Pattern.compile("(?i)^(?:change|set|update|make)\\s+(?:the\\s+)?([a-z0-9 &+\\-_]+?)\\s+(?:budget|limit|cap)\\s+(?:to|=|is)?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]+)?)$")
        val m1 = catLimitPattern1.matcher(trimmed)
        if (m1.find()) {
            val catQuery = m1.group(1)?.trim() ?: ""
            val amt = m1.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedExpenseResult(
                action = ParsedExpenseResult.ACTION_UPDATE_CATEGORY_LIMIT,
                category = catQuery,
                amount = amt
            )
        }

        // "food limit 5000" or "food limit to 5000"
        val catLimitPattern2 = Pattern.compile("(?i)^([a-z0-9 &+\\-_]+?)\\s+(?:budget|limit)\\s+(?:to|=|is)?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]+)?)$")
        val m2 = catLimitPattern2.matcher(trimmed)
        if (m2.find()) {
            val catQuery = m2.group(1)?.trim() ?: ""
            val amt = m2.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedExpenseResult(
                action = ParsedExpenseResult.ACTION_UPDATE_CATEGORY_LIMIT,
                category = catQuery,
                amount = amt
            )
        }

        // 6. Normal Transaction Parser
        val lower = trimmed.lowercase()

        // Extract amount
        val numberPattern = Pattern.compile("(?i)(?:rs\\.?|inr|₹|\\s|^)\\s*([0-9]+(?:\\.[0-9]+)?)")
        val matcher = numberPattern.matcher(trimmed)
        val amount = if (matcher.find()) {
            matcher.group(1)?.toDoubleOrNull() ?: 100.0
        } else {
            100.0
        }

        // Detect Income vs Expense
        val isIncome = lower.contains("stipend") || lower.contains("salary") ||
                lower.contains("credited") || lower.contains("received") || lower.contains("refund")
        val type = if (isIncome) "INCOME" else "EXPENSE"

        // Dynamic category matching against existing user categories
        val matchedExistingCategory = knownCategories.find {
            lower.contains(it.lowercase())
        }

        val category: String
        val merchant: String

        if (matchedExistingCategory != null) {
            category = matchedExistingCategory
            merchant = "General"
        } else {
            when {
                lower.contains("swiggy") -> {
                    category = "Food & Dining"
                    merchant = "Swiggy"
                }
                lower.contains("zomato") -> {
                    category = "Food & Dining"
                    merchant = "Zomato"
                }
                lower.contains("uber") -> {
                    category = "Travel & Auto"
                    merchant = "Uber"
                }
                lower.contains("ola") -> {
                    category = "Travel & Auto"
                    merchant = "Ola"
                }
                lower.contains("rapido") || lower.contains("auto") || lower.contains("cab") || lower.contains("metro") || lower.contains("petrol") -> {
                    category = "Travel & Auto"
                    merchant = "Transport"
                }
                lower.contains("zepto") || lower.contains("blinkit") || lower.contains("instamart") -> {
                    category = "Food & Dining"
                    merchant = "Quick Commerce"
                }
                lower.contains("gym") || lower.contains("protein") || lower.contains("cult") || lower.contains("workout") -> {
                    category = "Gym & Fitness"
                    merchant = "Fitness"
                }
                lower.contains("book") || lower.contains("course") || lower.contains("udemy") -> {
                    category = "Books & Learning"
                    merchant = "Education"
                }
                lower.contains("rent") || lower.contains("flat") || lower.contains("maintenance") -> {
                    category = "Housing & Rent"
                    merchant = "Landlord"
                }
                lower.contains("doctor") || lower.contains("medicine") || lower.contains("apollo") || lower.contains("1mg") -> {
                    category = "Health & Supplements"
                    merchant = "Pharmacy"
                }
                lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") || lower.contains("zara") -> {
                    category = "Shopping & Misc"
                    merchant = "E-Commerce"
                }
                lower.contains("netflix") || lower.contains("spotify") || lower.contains("movie") || lower.contains("pvr") -> {
                    category = "Entertainment"
                    merchant = "Entertainment"
                }
                lower.contains("wifi") || lower.contains("electricity") || lower.contains("recharge") || lower.contains("jio") || lower.contains("airtel") -> {
                    category = "Tech & Utilities"
                    merchant = "Utility"
                }
                lower.contains("emi") || lower.contains("debt") || lower.contains("loan") -> {
                    category = "Family & Debt"
                    merchant = "Finance"
                }
                lower.contains("food") || lower.contains("dinner") || lower.contains("lunch") || lower.contains("breakfast") || lower.contains("chai") || lower.contains("coffee") -> {
                    category = "Food & Dining"
                    merchant = "Food & Dining"
                }
                else -> {
                    category = "Shopping & Misc"
                    merchant = "General"
                }
            }
        }

        return ParsedExpenseResult(
            action = ParsedExpenseResult.ACTION_LOG_TRANSACTION,
            amount = amount,
            category = category,
            merchant = merchant,
            note = trimmed,
            type = type
        )
    }
}
