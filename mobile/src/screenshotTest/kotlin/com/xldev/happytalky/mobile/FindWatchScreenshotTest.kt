package com.xldev.happytalky.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.xldev.happytalky.core.ProximityBand
import com.xldev.happytalky.core.ProximityReading
import com.xldev.happytalky.core.ProximityTrend

@PreviewTest
@Preview(
    name = "Find Watch nearby",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun FindWatchNearbyScreenshot() {
    HappyTalkyPhoneTheme(
        darkTheme = false
    ) {
        FindWatchScreen(
            state =
                FindWatchUiState(
                    watchReady = true,
                    searching = false,
                    reading =
                        ProximityReading(
                            rawRssi = -63,
                            filteredRssi =
                                -64.2,
                            signalScore = 65,
                            band =
                                ProximityBand
                                    .CLOSE,
                            trend =
                                ProximityTrend
                                    .GETTING_CLOSER,
                            timestampMs = 1L
                        )
                ),
            onClose = {},
            onRetry = {}
        )
    }
}

@PreviewTest
@Preview(
    name = "Find Watch searching dark",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun FindWatchSearchingDarkScreenshot() {
    HappyTalkyPhoneTheme(
        darkTheme = true
    ) {
        FindWatchScreen(
            state =
                FindWatchUiState(
                    watchReady = true,
                    searching = true
                ),
            onClose = {},
            onRetry = {}
        )
    }
}
