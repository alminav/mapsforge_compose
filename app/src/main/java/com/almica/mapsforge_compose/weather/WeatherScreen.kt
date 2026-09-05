package com.almica.mapsforge_compose.weather

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import timber.log.Timber
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.almica.mapsforge_compose.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import com.almica.mapsforge_compose.charts.Const
import com.almica.mapsforge_compose.charts.RamaniTheme

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
    latitude: Double? = null,
    longitude: Double? = null,
    viewModel: WeatherViewModel = viewModel()
) {
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            Timber.i("WeatherScreen: $latitude, $longitude")
            viewModel.loadWeather(latitude, longitude)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    WeatherScreenContent(
        uiState = uiState,
        modifier = modifier,
        onRefresh = { viewModel.loadWeather(latitude, longitude) }
    )
}

private fun getCardinalDirectionIndex(degrees: Double): Int {
    // Normalize degrees to [0, 360)
    val normalizedDegrees = (degrees % 360 + 360) % 360
    // Divide into 16 sectors (22.5 degrees each)
    // Adding 11.25 shifts the sectors so that N is centered around 0
    return (((normalizedDegrees + 11.25) % 360) / 22.5).toInt() % 16
}

@Composable
fun WeatherScreenContent(
    uiState: WeatherUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit
) {
    Box(
        modifier = modifier.padding(0.dp)
    ) {
        Crossfade(targetState = uiState, label = "WeatherStateTransition") { state ->
            when (state) {
                is WeatherUiState.Loading -> {
                    // Show a placeholder shell with a spinner when loading for the first time
                    val loadingDesc = stringResource(R.string.loading_weather)
                    Box(contentAlignment = Alignment.Center) {
                        WeatherPlaceholder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { contentDescription = loadingDesc },
                            strokeWidth = 3.dp
                        )
                    }
                }

                is WeatherUiState.Error -> {
                    val errorMessage =
                        state.message ?: state.messageResId?.let { stringResource(it) } ?: ""
                    Text(
                        text = stringResource(R.string.weather_error_prefix, errorMessage),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                is WeatherUiState.Success -> {
                    Box {
                        WeatherDisplay(weather = state.weather, onRefresh = onRefresh)
                        Timber.i("WeatherUiState: isLoading: ${state.isLoading} weather: ${state.weather}")
                        // Smoothly fade in/out the background loading indicator
                        val loadingDesc = stringResource(R.string.loading_weather)
                        AnimatedVisibility(
                            visible = state.isLoading,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .semantics { contentDescription = loadingDesc },
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDisplay(weather: WeatherResponse, modifier: Modifier = Modifier, onRefresh: () -> Unit) {
    val current = weather.current
    // Holt das passende Icon und den Text basierend auf dem Code
    val weatherInfo = mapWmoCodeToWeather(current.weather_code)

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.TopEnd).padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.current_weather),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(5.dp))

                val locale = LocalLocale.current.platformLocale
                val formattedTime = remember(current.time, locale) {
                    try {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                        val localDateTime = LocalDateTime.parse(current.time, formatter)
//                    val zonedDateTime = localDateTime.atZone(ZoneId.of("UTC"))
//                        .withZoneSameInstant(ZoneId.systemDefault())
                        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", locale))
                    } catch (e: Exception) {
                        current.time.replace("T", " ")
                    }
                }

                val coordinates = String.format(Locale.ENGLISH, " %s %.2f° %.2f°", Const.UC_POSITION, weather.latitude, weather.longitude)
                Text(
                    text = "$formattedTime $coordinates",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Wetter-Icon groß anzeigen
                Icon(
                    imageVector = weatherInfo.icon,
                    contentDescription = stringResource(weatherInfo.descriptionResId),
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Textbeschreibung (z. B. "Leicht bewölkt")
                Text(
                    text = stringResource(weatherInfo.descriptionResId),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${current.temperature_2m}°C",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherDetailItem(label = stringResource(R.string.humidity), value = "${current.humidity}%")
                    WeatherDetailItem(label = stringResource(R.string.wind), value = "${current.wind_speed_10m} km/h")
                    val directionIndex = getCardinalDirectionIndex(current.wind_direction_10m)
                    val directions = stringArrayResource(id = R.array.wind_directions)
                    WeatherDetailItem(label = stringResource(R.string.direction), value = directions[directionIndex])
                }

                weather.hourly?.let { hourly ->
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                    /*
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Stündliche Vorhersage",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                     */
                    HourlyForecastList(hourly = hourly)
                }

                weather.daily?.let { daily ->
                    if (daily.sunrise.isNotEmpty() && daily.sunset.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 32.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WeatherDetailItem(
                                label = stringResource(R.string.sunrise),
                                value = daily.sunrise[0].split("T").lastOrNull() ?: "--:--"
                            )
                            WeatherDetailItem(
                                label = stringResource(R.string.sunset),
                                value = daily.sunset[0].split("T").lastOrNull() ?: "--:--"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )

                    DailyForecastList(daily = daily)
                }
            }
        }
    }
}

@Composable
fun HourlyForecastList(hourly: HourlyWeather) {
    val locale = LocalLocale.current.platformLocale
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val inputFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm") }
    val now = remember { LocalDateTime.now() }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        // Filter for indices that are from the current hour onwards, up to 24 items
        val nowTruncated = now.withMinute(0).withSecond(0).withNano(0)

        var next24HoursIndices = hourly.time.indices.filter { i ->
            try {
                val time = LocalDateTime.parse(hourly.time[i], inputFormatter)
                !time.isBefore(nowTruncated) && time.isBefore(nowTruncated.plusHours(25))
            } catch (e: Exception) {
                false
            }
        }

        // Fallback: If filtering resulted in empty list (e.g. timezone mismatch), just show the first 24 items
        if (next24HoursIndices.isEmpty() && hourly.time.isNotEmpty()) {
            next24HoursIndices = hourly.time.indices.take(24)
        }

        itemsIndexed(next24HoursIndices.toList()) { _, originalIndex ->
            //Timber.i("originalIndex: $originalIndex")
            val timeString = try {
                val time = LocalDateTime.parse(hourly.time[originalIndex], inputFormatter)
                time.format(timeFormatter)
            } catch (e: Exception) {
                hourly.time[originalIndex].split("T").lastOrNull() ?: "--:--"
            }

            HourlyForecastItem(
                time = timeString,
                temperature = hourly.temperature_2m[originalIndex],
                weatherCode = hourly.weather_code[originalIndex]
            )
        }
    }
}

@Composable
fun DailyForecastList(daily: DailyWeather) {
    val locale = LocalLocale.current.platformLocale
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val inputFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        daily.time.forEachIndexed { index, dateString ->
            val dayName = try {
                val date = java.time.LocalDate.parse(dateString, inputFormatter)
                date.format(dayFormatter)
            } catch (e: Exception) {
                dateString
            }

            DailyForecastItem(
                day = dayName,
                maxTemp = daily.temperature_2m_max?.getOrNull(index),
                minTemp = daily.temperature_2m_min?.getOrNull(index),
                weatherCode = daily.weather_code?.getOrNull(index) ?: 0
            )
        }
    }
}

@Composable
fun DailyForecastItem(day: String, maxTemp: Double?, minTemp: Double?, weatherCode: Int) {
    val weatherInfo = mapWmoCodeToWeather(weatherCode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = day,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Icon(
            imageVector = weatherInfo.icon,
            contentDescription = stringResource(weatherInfo.descriptionResId),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Row(
            modifier = Modifier.weight(1.5f),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${maxTemp?.toInt() ?: "--"}°",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${minTemp?.toInt() ?: "--"}°",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun HourlyForecastItem(time: String, temperature: Double, weatherCode: Int) {
    val weatherInfo = mapWmoCodeToWeather(weatherCode)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = time,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            imageVector = weatherInfo.icon,
            contentDescription = stringResource(weatherInfo.descriptionResId),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${temperature.toInt()}°",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun WeatherPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.loading_weather),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            // Large Icon Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            // Temperature Placeholder
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(40.dp, 12.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.size(30.dp, 14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherScreenPreview() {
    val now = LocalDateTime.now()
    val datePart = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
    RamaniTheme {
        WeatherScreenContent(
            uiState = WeatherUiState.Success(
                weather = WeatherResponse(
                    current = CurrentWeather(
                        time = "${datePart}T12:00",
                        temperature_2m = 22.5,
                        wind_speed_10m = 12.0,
                        wind_direction_10m = 180.0,
                        weather_code = 1, // Leicht bewölkt
                        humidity = 45
                    ),
                    hourly = HourlyWeather(
                        time = List(24) { String.format(Locale.US, "%sT%02d:00", datePart, it) },
                        temperature_2m = List(24) { 15.0 + it / 2.0 },
                        weather_code = List(24) { if (it % 3 == 0) 0 else 1 }
                    )
                )
            ),
            onRefresh = {}
        )
    }
}
