package com.babelmanager.app.features.generator

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.babelmanager.app.R
import com.babelmanager.app.core.security.MemorableOptions
import com.babelmanager.app.core.security.PasswordGenerator
import com.babelmanager.app.core.security.RandomOptions
import com.babelmanager.app.core.security.Separator
import com.babelmanager.app.core.security.Strength
import com.babelmanager.app.core.security.StrengthLevel
import com.babelmanager.app.core.ui.StrengthIndicator
import com.babelmanager.app.core.util.ClipboardUtil

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GeneratorSection(clipboardSeconds: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val generator = remember { PasswordGenerator() }

    var memorable by rememberSaveable { mutableStateOf(false) }
    var seed by rememberSaveable { mutableIntStateOf(0) }

    var length by rememberSaveable { mutableFloatStateOf(RandomOptions().length.toFloat()) }
    var upper by rememberSaveable { mutableStateOf(true) }
    var lower by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }

    var words by rememberSaveable { mutableFloatStateOf(6f) }
    var capitalize by rememberSaveable { mutableStateOf(true) }
    var numbers by rememberSaveable { mutableStateOf(true) }
    var separator by rememberSaveable { mutableIntStateOf(0) }

    val randomOptions = RandomOptions(length.toInt(), upper, lower, digits, symbols)
    val memorableOptions = MemorableOptions(words.toInt(), capitalize, numbers, Separator.entries[separator])

    // Gera de novo quando as opções mudam ou quando a pessoa pede.
    val password = remember(seed, memorable, randomOptions, memorableOptions) {
        if (memorable) generator.memorable(memorableOptions) else generator.random(randomOptions)
    }
    val bits = if (memorable) generator.memorableEntropyBits(memorableOptions) else generator.randomEntropyBits(randomOptions)
    val strength = Strength(bits, StrengthLevel.fromBits(bits))

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = colorize(password),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    StrengthIndicator(strength)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            ClipboardUtil.copySensitive(context, "generated", password, clipboardSeconds)
                            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.copy))
                        }
                        Button(onClick = { seed++ }) {
                            Icon(Icons.Outlined.Autorenew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.regenerate))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleButton(checked = !memorable, onCheckedChange = { memorable = false }) {
                    Text(stringResource(R.string.generator_random))
                }
                ToggleButton(checked = memorable, onCheckedChange = { memorable = true }) {
                    Text(stringResource(R.string.generator_memorable))
                }
            }

            if (!memorable) {
                SliderRow(stringResource(R.string.generator_length, length.toInt()), length, 8f..64f, 55) { length = it }
                SwitchRow(stringResource(R.string.generator_uppercase), upper) { upper = it }
                SwitchRow(stringResource(R.string.generator_lowercase), lower) { lower = it }
                SwitchRow(stringResource(R.string.generator_digits), digits) { digits = it }
                SwitchRow(stringResource(R.string.generator_symbols), symbols) { symbols = it }
            } else {
                SliderRow(stringResource(R.string.generator_words, words.toInt()), words, 3f..10f, 6) { words = it }
                SwitchRow(stringResource(R.string.generator_capitalize), capitalize) { capitalize = it }
                SwitchRow(stringResource(R.string.generator_numbers), numbers) { numbers = it }
                Text(stringResource(R.string.generator_separator), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Separator.entries.forEachIndexed { index, sep ->
                        FilterChip(
                            selected = separator == index,
                            onClick = { separator = index },
                            label = { Text(separatorLabel(sep)) },
                        )
                    }
                }
            }
        }
}

@Composable
private fun separatorLabel(separator: Separator): String = stringResource(
    when (separator) {
        Separator.Hyphen -> R.string.separator_hyphen
        Separator.Space -> R.string.separator_space
        Separator.Period -> R.string.separator_period
        Separator.Comma -> R.string.separator_comma
        Separator.Underscore -> R.string.separator_underscore
        Separator.None -> R.string.separator_none
    },
)

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Números em uma cor e símbolos em outra, para facilitar a leitura. */
@Composable
private fun colorize(password: String): AnnotatedString {
    val digitColor = MaterialTheme.colorScheme.tertiary
    val symbolColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        for (c in password) {
            when {
                c.isDigit() -> withStyle(SpanStyle(color = digitColor)) { append(c) }
                !c.isLetter() && c != ' ' -> withStyle(SpanStyle(color = symbolColor)) { append(c) }
                else -> append(c)
            }
        }
    }
}
