package com.nkhearn25.toiltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nkhearn25.toiltracker.ui.ToilTrackerApp
import com.nkhearn25.toiltracker.ui.theme.ToilTrackerTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ToilTrackerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logic = ToilTrackerLogic(this)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ToilTrackerViewModel(logic) as T
            }
        })[ToilTrackerViewModel::class.java]

        setContent {
            ToilTrackerTheme {
                ToilTrackerApp(viewModel)
            }
        }
    }
}
