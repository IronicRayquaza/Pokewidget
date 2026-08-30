package com.pokewidgets.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.ui.theme.Chalk
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.ui.theme.InkSoft
import com.pokewidgets.app.ui.theme.Lime
import com.pokewidgets.app.ui.theme.PokeRed
import com.pokewidgets.app.ui.theme.onColorFor
import com.pokewidgets.app.ui.theme.sticker
import com.pokewidgets.app.ui.theme.typeColor
import com.pokewidgets.app.ui.theme.Card as CardColor

/**
 * The app's controls, all cut from the same outlined-sticker stock.
 *
 * Material's own Button, FilterChip and TextField are not used here: each draws its own
 * container, elevation and indicator, and re-skinning three component families into one
 * shape is more code — and more ways for them to drift apart — than building the single
 * shape the design actually has.
 */

/** How far a raised surface floats above its own shadow. */
private val RAISED = 4.dp

/**
 * The smallest a tappable control is allowed to get.
 *
 * Applied as a minimum rather than a fixed height so a chip still hugs its label
 * horizontally; only the axis that would otherwise fall short is padded out.
 */
private val MIN_TARGET = 44.dp

/**
 * A button that physically presses into the page.
 *
 * One gesture, read three ways: the surface slides down into its own shadow, the shadow
 * shortens to meet it, and they arrive together. That is what a raised sticker does when
 * it is pushed, and it is legible at a glance in a way an opacity change on a flat
 * rectangle is not.
 */
@Composable
fun PokeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = Lime,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val lift by animateDpAsState(
        targetValue = if (pressed && enabled) 0.dp else RAISED,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonLift",
    )
    val sink = RAISED - lift
    val content = if (enabled) onColorFor(container) else InkSoft

    Box(
        modifier
            .offset { IntOffset(0, sink.roundToPx()) }
            .defaultMinSize(minHeight = MIN_TARGET)
            .sticker(
                shape = MaterialTheme.shapes.medium,
                fill = if (enabled) container else Chalk,
                lift = lift,
            )
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A square outlined button carrying one icon — back arrows, the settings cog. */
@Composable
fun PokeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = CardColor,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(44.dp)
            .pressScale(interactions)
            .sticker(shape = MaterialTheme.shapes.small, fill = container, lift = 3.dp)
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = onColorFor(container),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A selectable filter pill.
 *
 * Selection is carried by the fill *and* by the outline thickening, so it survives a
 * greyscale screenshot and a colour-blind viewer. Colour is never the only signal.
 */
@Composable
fun PokeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedColor: Color = Lime,
) {
    val interactions = remember { MutableInteractionSource() }
    val fill = if (selected) selectedColor else CardColor
    val content = onColorFor(fill)
    Row(
        modifier
            .pressScale(interactions)
            .defaultMinSize(minHeight = MIN_TARGET)
            .sticker(
                shape = RoundedCornerShape(percent = 50),
                fill = fill,
                borderWidth = if (selected) 3.dp else 2.dp,
                lift = if (selected) 3.dp else 2.dp,
            )
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            // The outline weight and the fill both carry selection visually; this is the
            // same fact for a screen reader, which sees neither.
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** The dex-number tag from the corner of a Pokédex page. */
@Composable
fun DexBadge(number: Int, modifier: Modifier = Modifier, container: Color = CardColor) {
    Box(
        modifier
            .sticker(shape = MaterialTheme.shapes.small, fill = container, lift = 3.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            "#" + number.toString().padStart(3, '0'),
            style = MaterialTheme.typography.labelLarge,
            color = onColorFor(container),
        )
    }
}

/** A Pokémon's elemental type, in that type's own colour. */
@Composable
fun TypeChip(type: String, modifier: Modifier = Modifier) {
    val fill = typeColor(type)
    Box(
        modifier
            .sticker(
                shape = RoundedCornerShape(percent = 50),
                fill = fill,
                borderWidth = 2.dp,
                lift = 2.dp,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(type.uppercase(), style = MaterialTheme.typography.labelSmall, color = onColorFor(fill))
    }
}

/**
 * The search field.
 *
 * A `BasicTextField` rather than an `OutlinedTextField` because Material's version draws
 * its own container, label and indicator line, none of which can be removed — only
 * recoloured to transparent, which leaves behind the layout padding of a component that
 * is no longer visible.
 */
@Composable
fun PokeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .sticker(shape = RoundedCornerShape(percent = 50), fill = CardColor, lift = 3.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = InkSoft,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
            )
        }
        // A clear affordance, not decoration: emptying a long query by hand re-filters
        // 1,345 entries on every keystroke, and one tap does it once.
        if (value.isNotEmpty()) {
            PokeIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Clear search",
                onClick = { onValueChange("") },
                container = Chalk,
            )
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/**
 * The page header. Not a Material `TopAppBar`: this one is part of the page rather than a
 * bar above it, so it carries no elevation and no surface of its own.
 */
@Composable
fun PokeHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            PokeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/** A label above a group of settings. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(PokeRed, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

/** Body copy that explains a control, or the absence of one. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = InkSoft) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/**
 * What a list says when it has nothing in it.
 *
 * Always names what was looked for and offers the way out, rather than only reporting the
 * emptiness — an empty state with no next step is a dead end.
 */
@Composable
fun EmptyHint(
    title: String,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp)
            // The grid empties out as the user types, with no other announcement that
            // the results are gone.
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
