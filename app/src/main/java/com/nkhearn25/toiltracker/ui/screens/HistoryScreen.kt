package com.nkhearn25.toiltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nkhearn25.toiltracker.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    metrics: Map<String, Any>,
    onDelete: (String) -> Unit
) {
    val adjustments = (metrics["adjustments_list"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?: emptyList()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val displayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "📜 Past Schedule Deviations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (adjustments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No overrides or time offsets applied.", color = Slate400)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(adjustments) { item ->
                    val dateStr = item["date"] as String
                    val date = LocalDate.parse(dateStr, formatter)
                    val adjustment = item["adjustment"] as Double
                    val note = item["note"] as String
                    val dayName = daysOfWeek[date.dayOfWeek.value - 1]

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${date.format(displayFormatter)} ($dayName)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate200
                                )
                                if (note.isNotEmpty()) {
                                    Text(
                                        text = "\"$note\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val color = if (adjustment >= 0) Emerald400 else Rose400
                                val sign = if (adjustment >= 0) "+" else ""
                                Text(
                                    text = "$sign${adjustment}h",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                IconButton(onClick = { onDelete(dateStr) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Slate400)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
