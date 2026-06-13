package com.nkhearn25.toiltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nkhearn25.toiltracker.ui.theme.*

@Composable
fun SettingsScreen(
    config: com.nkhearn25.toiltracker.ToilTrackerLogic.Config,
    onSave: (Double, String, Int, Int, Map<String, Double>) -> Unit
) {
    var contractHours by remember { mutableStateOf(config.contract_hours.toString()) }
    var startDate by remember { mutableStateOf(config.start_date) }
    var endMonth by remember { mutableStateOf(config.year_end_month) }
    var endDay by remember { mutableStateOf(config.year_end_day) }

    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val defaultWeek = remember { mutableStateMapOf<String, String>().apply {
        days.forEach { day -> put(day, (config.default_week?.get(day) ?: 0.0).toString()) }
    } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "⚙️ Base Configurations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = contractHours,
            onValueChange = { contractHours = it },
            label = { Text("Weekly Contracted Hours") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            label = { Text("Cycle Custom Start Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = endMonth.toString(),
                onValueChange = { endMonth = it.toIntOrNull() ?: 1 },
                label = { Text("End Month") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endDay.toString(),
                onValueChange = { endDay = it.toIntOrNull() ?: 1 },
                label = { Text("End Day") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = Slate800)
        Text(text = "Default Standard Weekly Schedule", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate400)

        days.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { day ->
                    OutlinedTextField(
                        value = defaultWeek[day] ?: "0.0",
                        onValueChange = { defaultWeek[day] = it },
                        label = { Text(day) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        Button(
            onClick = {
                val week = defaultWeek.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
                onSave(contractHours.toDoubleOrNull() ?: 21.0, startDate, endMonth, endDay, week)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("💾 Save All Settings", fontWeight = FontWeight.Bold)
        }
    }
}
