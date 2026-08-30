package com.pokewidgets.app.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokewidgets.app.ui.screens.ConfigScreen
import com.pokewidgets.app.ui.screens.PokemonPicker
import com.pokewidgets.app.ui.screens.SpriteSetPickerScreen
import com.pokewidgets.app.ui.theme.Paper
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
        // The app is light on every screen, so the system bars are asked for dark icons
        // once here rather than being left to follow the device theme and disappear into
        // the cream stock on a phone set to dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
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
                Surface(color = Paper) {
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
    var step by remember { mutableStateOf(ConfigStep.SETTINGS) }

    LaunchedEffect(widgetId) { viewModel.load(widgetId) }

    when (step) {
        ConfigStep.POKEMON -> PokemonPicker(
            all = state.allPokemon,
            selectedId = state.config.pokemonId,
            onSelect = {
                viewModel.selectPokemon(it)
                step = ConfigStep.SETTINGS
            },
            onBack = { step = ConfigStep.SETTINGS },
        )

        ConfigStep.SPRITE_SET -> SpriteSetPickerScreen(
            pokemonName = state.entry?.displayName ?: "this Pokémon",
            sets = state.availableSetPreviews,
            selectedSetId = state.config.setId,
            onSelect = viewModel::selectSet,
            onBack = { step = ConfigStep.SETTINGS },
        )

        ConfigStep.SETTINGS -> ConfigScreen(
            state = state,
            onPickPokemon = { step = ConfigStep.POKEMON },
            onPickSet = { step = ConfigStep.SPRITE_SET },
            onUpdate = viewModel::update,
            onSave = { viewModel.save(onDone) },
        )
    }
}

/**
 * Where the configuration flow currently is.
 *
 * Both pickers are full pages rather than sheets or inline strips. Picking a Pokémon or
 * a sprite set means comparing a lot of similar-looking things, and neither comparison
 * fits in a row or survives a container that dismisses on a downward flick.
 */
private enum class ConfigStep { SETTINGS, POKEMON, SPRITE_SET }
