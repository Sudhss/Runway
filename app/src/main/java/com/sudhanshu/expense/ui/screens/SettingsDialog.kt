package com.sudhanshu.expense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sudhanshu.expense.data.model.UserProfileEntity
import com.sudhanshu.expense.ui.theme.*

@Composable
fun SettingsDialog(
    profile: UserProfileEntity,
    onDismiss: () -> Unit,
    onSaveNetWorth: (Double) -> Unit,
    onSaveMonthlyCap: (Double) -> Unit,
    onSaveApiSettings: (String, String) -> Unit
) {
    var netWorthText by remember { mutableStateOf(profile.currentNetWorth.toInt().toString()) }
    var monthlyCapText by remember { mutableStateOf(profile.monthlyCap.toInt().toString()) }
    var apiKeyText by remember { mutableStateOf(profile.apiKey) }
    var selectedProvider by remember { mutableStateOf(profile.apiProvider) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Financial & AI Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = netWorthText,
                    onValueChange = { netWorthText = it },
                    label = { Text("Current Liquid Net Worth (₹)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = monthlyCapText,
                    onValueChange = { monthlyCapText = it },
                    label = { Text("Monthly Disposable Cap (₹)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Free LLM Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedProvider == "GROQ",
                        onClick = { selectedProvider = "GROQ" },
                        label = { Text("Groq (Llama 3.1)") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = selectedProvider == "GEMINI",
                        onClick = { selectedProvider = "GEMINI" },
                        label = { Text("Gemini Flash") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text("API Key (Optional)") },
                    placeholder = { Text("Leave blank to use offline parser") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            netWorthText.toDoubleOrNull()?.let { onSaveNetWorth(it) }
                            monthlyCapText.toDoubleOrNull()?.let { onSaveMonthlyCap(it) }
                            onSaveApiSettings(apiKeyText.trim(), selectedProvider)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("Save Changes", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
