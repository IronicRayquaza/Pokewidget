package com.pokewidgets.app.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokewidgets.app.ui.screens.ConfigScreen
import com.pokewidgets.app.ui.screens.PokemonPicker
import com.pokewidgets.app.ui.theme.PokeWidgetTheme

/**
 * The widget's configuration screen.
 *
 * Launched two ways: by the launcher when a widget is dropped on the home screen, and
 * from inside the app to edit one that already exists. In the first case the launcher
 * waits for `RESULT_OK` carrying the widget id — anything else and it silently throws
 * the widget away, which is why [finishWithResult] is the only exit path.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Pre-set the cancelled result, so backing out of configuration correctly
        // removes the half-placed widget rather than leaving a blank one behind.
        setResult(Activity.RESULT_CANCELED, resultIntent())

        setContent {
            PokeWidgetTheme {
                Surface {
                    ConfigFlow(widgetId) { finishWithResult() }
                }
            }
        }
    }

    private fun finishWithResult() {
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@Composable
fun ConfigFlow(widgetId: Int, onDone: () -> Unit) {
    val viewModel: ConfigViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var pickingPokemon by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(widgetId) { viewModel.load(widgetId) }

    if (pickingPokemon) {
        PokemonPicker(
            all = state.allPokemon,
            selectedId = state.config.pokemonId,
            onSelect = {
                viewModel.selectPokemon(it)
                pickingPokemon = false
            },
            onBack = { pickingPokemon = false },
        )
    } else {
        ConfigScreen(
            state = state,
            onPickPokemon = { pickingPokemon = true },
            onSelectSet = viewModel::selectSet,
            onUpdate = viewModel::update,
            onSave = { viewModel.save(onDone) },
        )
    }
}
