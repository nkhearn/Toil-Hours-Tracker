package com.nkhearn25.toiltracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nkhearn25.toiltracker.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogHoursScreen(
    initialDate: LocalDate,
    config: com.nkhearn25.toiltracker.ToilTrackerLogic.Config,
    onSave: (String, Double, String) -> Unit
) {
    var date by remember { mutableStateOf(initialDate) }
    var adjMode by remember { mutableStateOf("set") } // "set" or "offset"
    var hoursInput by remember { mutableStateOf(TextFieldValue("")) }
    var noteInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val dayName = daysOfWeek[date.dayOfWeek.value - 1]
    val dayDefault = config.default_week?.get(dayName) ?: 0.0

    LaunchedEffect(date, adjMode) {
        if (adjMode == "set" && hoursInput.text.isEmpty()) {
            hoursInput = TextFieldValue(dayDefault.toString())
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

        // Date Picker
        OutlinedTextField(
            value = date.toString(),
            onValueChange = { },
            label = { Text("Target Date") },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                }
            }
        )

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

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
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hoursInput = hoursInput.copy(selection = TextRange(0, hoursInput.text.length))
                    }
                }
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
                val inputVal = hoursInput.text.toDoubleOrNull() ?: 0.0
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
