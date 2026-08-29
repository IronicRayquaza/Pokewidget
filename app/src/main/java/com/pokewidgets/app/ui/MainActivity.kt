package com.pokewidgets.app.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokewidgets.app.ui.screens.MainScreen
import com.pokewidgets.app.ui.theme.PokeWidgetTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PokeWidgetTheme {
                Surface {
                    val viewModel: MainViewModel = viewModel()
                    val state by viewModel.state.collectAsState()

                    // A widget can be placed or removed from the home screen while the
                    // app sits in the background, so re-read on every resume.
                    LifecycleResumeEffect(Unit) {
                        viewModel.refreshPlaced()
                        viewModel.refreshCacheSize()
                        onPauseOrDispose { }
                    }

                    val editLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                    ) {
                        viewModel.refreshPlaced()
                    }

                    MainScreen(
                        state = state,
                        onQuery = viewModel::setQuery,
                        onGeneration = viewModel::setGeneration,
                        onAnimatedOnly = viewModel::setAnimatedOnly,
                        onOpenDetail = viewModel::openDetail,
                        onCloseDetail = viewModel::closeDetail,
                        onPin = viewModel::requestPin,
                        onEditWidget = { widgetId ->
                            editLauncher.launch(
                                Intent(this@MainActivity, WidgetConfigActivity::class.java)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                            )
                        },
                        onClearCache = viewModel::clearCache,
                        onPlayCry = viewModel::playCry,
                    )
                }
            }
        }
    }
}
