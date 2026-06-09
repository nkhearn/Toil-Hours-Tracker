package com.nkhearn25.toiltracker

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var logic: ToilTrackerLogic
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logic = ToilTrackerLogic(this)
        webView = findViewById(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "NativeApp")

        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun getData(): String {
            val config = logic.loadData()
            val metrics = logic.calculateMetrics(config)
            return gson.toJson(mapOf(
                "config" to config,
                "metrics" to metrics
            ))
        }

        @JavascriptInterface
        fun updateConfig(contractHours: Double, startDate: String, endMonth: Int, endDay: Int, defaultWeekJson: String): String {
            val config = logic.loadData()
            config.contract_hours = contractHours
            config.start_date = startDate
            config.year_end_month = endMonth
            config.year_end_day = endDay

            val type = object : TypeToken<MutableMap<String, Double>>() {}.type
            config.default_week = gson.fromJson(defaultWeekJson, type)

            logic.saveData(config)
            val metrics = logic.calculateMetrics(config)
            return gson.toJson(mapOf("status" to "success", "metrics" to metrics))
        }

        @JavascriptInterface
        fun saveAdjustment(date: String, offset: Double, note: String): String {
            val config = logic.loadData()
            if (offset == 0.0) {
                config.adjustments.remove(date)
            } else {
                config.adjustments[date] = ToilTrackerLogic.Adjustment(offset, note)
            }
            logic.saveData(config)
            val metrics = logic.calculateMetrics(config)
            return gson.toJson(mapOf("status" to "success", "metrics" to metrics))
        }

        @JavascriptInterface
        fun deleteAdjustment(date: String): String {
            val config = logic.loadData()
            config.adjustments.remove(date)
            logic.saveData(config)
            val metrics = logic.calculateMetrics(config)
            return gson.toJson(mapOf("status" to "success", "metrics" to metrics))
        }
    }
}
