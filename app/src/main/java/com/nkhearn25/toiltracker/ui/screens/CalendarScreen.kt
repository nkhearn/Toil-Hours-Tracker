package com.nkhearn25.toiltracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nkhearn25.toiltracker.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun CalendarScreen(
    config: com.nkhearn25.toiltracker.ToilTrackerLogic.Config,
    metrics: Map<String, Any>,
    onDateSelected: (LocalDate) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", tint = Slate400)
            }
            Text(
                text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate200
            )
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", tint = Slate400)
            }
        }

        // Days of week header
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(
                    text = day.uppercase(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CalendarGrid(currentMonth, config, metrics, onDateSelected)
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    config: com.nkhearn25.toiltracker.ToilTrackerLogic.Config,
    metrics: Map<String, Any>,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstDayOfMonth = currentMonth.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 (Mon) to 7 (Sun)
    val daysInMonth = currentMonth.lengthOfMonth()

    val today = LocalDate.now()
    val adjustmentsList = (metrics["adjustments_list"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?: emptyList()
    val defaultWeek = config.default_week ?: emptyMap()
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Empty slots for days before the first day of the month
        items(firstDayOfWeek - 1) {
            Box(modifier = Modifier.height(56.dp))
        }

        items(daysInMonth) { day ->
            val date = currentMonth.atDay(day + 1)
            val dateStr = date.toString()
            val isToday = date == today

            val dayName = daysOfWeek[date.dayOfWeek.value - 1]
            val baseHours = defaultWeek[dayName] ?: 0.0
            val adj = adjustmentsList.find { it["date"] == dateStr }
            val adjHours = (adj?.get("adjustment") as? Double) ?: 0.0
            val totalHours = baseHours + adjHours
            val hasAdj = adj != null

            Card(
                modifier = Modifier
                    .height(56.dp)
                    .clickable { onDateSelected(date) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isToday) Slate700 else Color.Transparent
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = (day + 1).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasAdj) Blue500 else Slate200
                    )
                    Text(
                        text = "${totalHours}h",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (hasAdj) Blue500 else Slate400
                    )
                }
            }
        }
    }
}
