package com.nkhearn25.toiltracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nkhearn25.toiltracker.ui.theme.*

@Composable
fun DashboardScreen(
    metrics: Map<String, Any>,
    onSetupClick: () -> Unit
) {
    val balance = metrics["balance"] as? Double ?: 0.0
    val forecast = metrics["forecast_balance"] as? Double ?: 0.0
    val actualWorked = metrics["actual_worked"] as? Double ?: 0.0
    val expectedContracted = metrics["expected_contracted"] as? Double ?: 0.0
    val daysElapsed = (metrics["days_elapsed"] as? Long)?.toDouble() ?: 0.0
    val cycleSpan = (metrics["cycle_span_days"] as? Long)?.toDouble() ?: 1.0
    val progressPct = (daysElapsed / cycleSpan * 100).toInt().coerceIn(0, 100)

    val startDate = metrics["start_date"] as? String ?: ""
    val endDate = metrics["end_date"] as? String ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⏱️ Toil Tracker",
                style = MaterialTheme.typography.titleLarge,
                color = Slate200
            )
            IconButton(onClick = onSetupClick) {
                Text("⚙️", fontSize = 20.sp)
            }
        }

        // Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RUNNING BALANCE (TO TODAY)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )

                val balanceColor = when {
                    balance > 0 -> Emerald400
                    balance < 0 -> Rose500
                    else -> Slate200
                }
                val balanceSign = if (balance > 0) "+" else ""

                Text(
                    text = "$balanceSign${balance}h",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = balanceColor
                )

                val label = when {
                    balance > 0 -> "Overtime Accumulated"
                    balance < 0 -> "Under Hours / Owed"
                    else -> "Perfect Balanced Curve"
                }
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (balance != 0.0) balanceColor else Slate400,
                    fontWeight = FontWeight.Bold
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Forecasted Year End:", style = MaterialTheme.typography.bodySmall, color = Slate400)
                    val forecastSign = if (forecast > 0) "+" else ""
                    val forecastColor = when {
                        forecast > 0 -> Emerald400
                        forecast < 0 -> Rose400
                        else -> Slate200
                    }
                    Text(
                        text = "$forecastSign${forecast}h",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = forecastColor
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(label = "Worked", value = "${actualWorked}h")
                    StatItem(label = "Contracted", value = "${expectedContracted}h")
                    StatItem(label = "Progress", value = "$progressPct%")
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)

                Text(
                    text = "Tracking Cycle: $startDate to $endDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Slate400, fontWeight = FontWeight.Bold)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Slate200)
    }
}
