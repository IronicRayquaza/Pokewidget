package com.pokewidgets.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pokewidgets.app.catalog.CatalogRepository

/**
 * A remote sprite, animated if the source is a GIF.
 *
 * [FilterQuality.None] is not a detail — it is the difference between crisp 8-bit art and
 * a blurry smear. Every sprite surface in this app renders with nearest-neighbour
 * sampling, exactly like the widget does.
 */
@Composable
fun SpriteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

/**
 * A Pokémon's box icon for the picker grid, served from the bundled offline pack.
 *
 * 1114 of the 1345 catalogued Pokémon have one; the rest — mostly Gen 9 and rarer
 * alternate forms — fall back to [fallbackUrl] so the grid never shows a hole.
 */
@Composable
fun PokemonIcon(
    pokemonId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    fallbackUrl: String? = null,
) {
    val context = LocalContext.current
    var bitmap by remember(pokemonId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loaded by remember(pokemonId) { mutableStateOf(false) }

    LaunchedEffect(pokemonId) {
        bitmap = CatalogRepository.get(context).icon(pokemonId)
        loaded = true
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val local = bitmap
        when {
            local != null -> Image(
                bitmap = local.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )

            loaded && fallbackUrl != null -> SpriteImage(
                url = fallbackUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )

            loaded -> Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
