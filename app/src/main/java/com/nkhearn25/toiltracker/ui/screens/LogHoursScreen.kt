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
import java.time.LocalDate

@Composable
fun LogHoursScreen(
    initialDate: LocalDate,
    config: com.nkhearn25.toiltracker.ToilTrackerLogic.Config,
    onSave: (String, Double, String) -> Unit
) {
    var date by remember { mutableStateOf(initialDate) }
    var adjMode by remember { mutableStateOf("set") } // "set" or "offset"
    var hoursInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val dayName = daysOfWeek[date.dayOfWeek.value - 1]
    val dayDefault = config.default_week?.get(dayName) ?: 0.0

    LaunchedEffect(date, adjMode) {
        if (adjMode == "set" && hoursInput.isEmpty()) {
            hoursInput = dayDefault.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "📅 Custom Calendar Adjustments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Date Picker (simplified as Text for now, in real app would use DatePickerDialog)
        OutlinedTextField(
            value = date.toString(),
            onValueChange = { },
            label = { Text("Target Date") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Standard hours scheduled on ${dayName}s: ${dayDefault}h",
            style = MaterialTheme.typography.labelSmall,
            color = Blue500
        )

        // Adjustment Type
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adjustment Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate400)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { adjMode = "set" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (adjMode == "set") Blue600 else Slate800)
                ) {
                    Text("Overwrite Day")
                }
                Button(
                    onClick = { adjMode = "offset" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (adjMode == "offset") Blue600 else Slate800)
                ) {
                    Text("Overtime / Offset")
                }
            }
        }

        OutlinedTextField(
            value = hoursInput,
            onValueChange = { hoursInput = it },
            label = {
                Text(if (adjMode == "set") "Total Hours Actually Worked" else "Offset Amount (+ or -)")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = noteInput,
            onValueChange = { noteInput = it },
            label = { Text("Note / Reason") },
            placeholder = { Text("e.g. Went home early, Extra shift") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val inputVal = hoursInput.toDoubleOrNull() ?: 0.0
                val offsetVal = if (adjMode == "set") inputVal - dayDefault else inputVal
                onSave(date.toString(), offsetVal, noteInput)
                noteInput = ""
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("💾 Save Calendar Entry", fontWeight = FontWeight.Bold)
        }
    }
}
