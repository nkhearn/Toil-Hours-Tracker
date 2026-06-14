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
fun SettingsScreen(
    config: com.nkhearn25.toiltracker.ToilTrackerLogic.Config,
    onSave: (Double, String, Int, Int, Map<String, Double>) -> Unit
) {
    var contractHours by remember { mutableStateOf(TextFieldValue(config.contract_hours.toString())) }
    var startDate by remember { mutableStateOf(config.start_date) }
    var endMonth by remember { mutableIntStateOf(config.year_end_month.coerceIn(1, 12)) }
    var endDay by remember { mutableStateOf(TextFieldValue(config.year_end_day.toString())) }

    var showDatePicker by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }

    val months = remember {
        java.time.Month.values().map { month ->
            month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        }
    }
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val defaultWeek = remember { mutableStateMapOf<String, TextFieldValue>().apply {
        days.forEach { day -> put(day, TextFieldValue((config.default_week?.get(day) ?: 0.0).toString())) }
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
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        contractHours = contractHours.copy(selection = TextRange(0, contractHours.text.length))
                    }
                }
        )

        OutlinedTextField(
            value = startDate,
            onValueChange = { },
            label = { Text("Cycle Custom Start Date") },
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
                initialSelectedDateMillis = LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            startDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = expandedMonth,
                onExpandedChange = { expandedMonth = !expandedMonth },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = months[endMonth - 1],
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("End Month") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                    modifier = Modifier.menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors()
                )
                ExposedDropdownMenu(
                    expanded = expandedMonth,
                    onDismissRequest = { expandedMonth = false }
                ) {
                    months.forEachIndexed { index, month ->
                        DropdownMenuItem(
                            text = { Text(month) },
                            onClick = {
                                endMonth = index + 1
                                expandedMonth = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = endDay,
                onValueChange = { endDay = it },
                label = { Text("End Day") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            endDay = endDay.copy(selection = TextRange(0, endDay.text.length))
                        }
                    }
            )
        }

        HorizontalDivider(color = Slate800)
        Text(text = "Default Standard Weekly Schedule", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate400)

        days.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { day ->
                    OutlinedTextField(
                        value = defaultWeek[day] ?: TextFieldValue("0.0"),
                        onValueChange = { defaultWeek[day] = it },
                        label = { Text(day) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    val current = defaultWeek[day] ?: TextFieldValue("0.0")
                                    defaultWeek[day] = current.copy(selection = TextRange(0, current.text.length))
                                }
                            }
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        Button(
            onClick = {
                val week = defaultWeek.mapValues { it.value.text.toDoubleOrNull() ?: 0.0 }
                onSave(
                    contractHours.text.toDoubleOrNull() ?: 21.0,
                    startDate,
                    endMonth,
                    endDay.text.toIntOrNull() ?: 1,
                    week
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("💾 Save All Settings", fontWeight = FontWeight.Bold)
        }
    }
}
