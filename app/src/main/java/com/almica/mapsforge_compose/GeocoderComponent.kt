package com.almica.mapsforge_compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeocoderComponent(
    results: List<GeocoderResult>,
    onQueryChange: (String) -> Unit,
    onResultSelected: (GeocoderResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Debounce query changes to avoid hitting the geocoder API too frequently
    LaunchedEffect(query) {
        if (query.length >= 3) {
            delay(500.milliseconds)
            onQueryChange(query)
        } else if (query.isEmpty()) {
            onQueryChange("")
        }
    }

    SearchBar(
        query = query,
        onQueryChange = { query = it },
        onSearch = {
            onQueryChange(it)
            keyboardController?.hide()
        },
        active = active,
        onActiveChange = {
            active = it
            if (!it) {
                onClose()
                keyboardController?.hide()
            }
        },
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_hint)
            )
        },
        trailingIcon = {
            if (active) {
                IconButton(
                    onClick = {
                        if (query.isNotEmpty()) {
                            query = ""
                        } else {
                            active = false
                            onClose()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (active) 0.dp else 16.dp)
    ) {
        if (results.isEmpty() && query.length >= 3) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(results) { result ->
                    Column {
                        ListItem(
                            headlineContent = { Text(result.displayAddress) },
                            supportingContent = {
                                Text(
                                    text = "${result.latLong.latitude}, ${result.latLong.longitude}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.clickable {
                                active = false
                                keyboardController?.hide()
                                onResultSelected(result)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
