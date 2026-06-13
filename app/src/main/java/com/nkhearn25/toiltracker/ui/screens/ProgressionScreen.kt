package com.nkhearn25.toiltracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nkhearn25.toiltracker.ui.theme.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.patrykandpatrick.vico.compose.chart.line.lineSpec

@Composable
fun ProgressionScreen(metrics: Map<String, Any>) {
    @Suppress("UNCHECKED_CAST")
    val chartData = metrics["chart_data"] as? List<Map<String, Any>> ?: emptyList()

    val workedEntries = chartData.mapIndexed { index, map ->
        entryOf(index.toFloat(), (map["worked"] as Double).toFloat())
    }
    val contractedEntries = chartData.mapIndexed { index, map ->
        entryOf(index.toFloat(), (map["contracted"] as Double).toFloat())
    }

    val modelProducer = remember { ChartEntryModelProducer(listOf(workedEntries, contractedEntries)) }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val displayFormatter = DateTimeFormatter.ofPattern("dd MMM")

    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val index = value.toInt().coerceIn(0, chartData.size - 1)
        val dateStr = chartData[index]["date"] as String
        LocalDate.parse(dateStr, formatter).format(displayFormatter)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "📈 Performance Curve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = "Continuous view of work progression vs. the contractual baseline curve.", style = MaterialTheme.typography.bodySmall, color = Slate400)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Chart(
                    chart = lineChart(
                        lines = listOf(
                            lineSpec(
                                lineColor = Emerald400,
                            ),
                            lineSpec(
                                lineColor = Blue500,
                            )
                        )
                    ),
                    chartModelProducer = modelProducer,
                    startAxis = rememberStartAxis(
                        label = textComponent(color = Slate400),
                    ),
                    bottomAxis = rememberBottomAxis(
                        label = textComponent(color = Slate400),
                        valueFormatter = bottomAxisValueFormatter
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(color = Emerald400, label = "Actual Worked")
            LegendItem(color = Blue500, label = "Contract Baseline")
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate400)
    }
}

// Vico component helpers
@Composable
fun textComponent(color: Color) = com.patrykandpatrick.vico.compose.component.textComponent(color = color)
