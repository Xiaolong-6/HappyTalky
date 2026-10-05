package com.xldev.happytalky.wear

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Debug-only proof of concept for simple captive portals on Wear OS.
 *
 * This intentionally does not appear in the child-facing HappyTalky UI.
 * Launch it with:
 *
 * adb shell am start -n \
 *   com.xldev.happytalky/com.xldev.happytalky.wear.PublicWifiDebugActivity
 */
class PublicWifiDebugActivity :
    ComponentActivity() {
    private val client by lazy {
        SimpleCaptivePortalClient(
            getSystemService(
                ConnectivityManager::class.java
            )
        )
    }

    private var uiState by
        mutableStateOf(
            PortalUiState(
                headline = "Public Wi-Fi PoC",
                detail =
                    "Open Wi-Fi settings, join the public network, then return here.",
            )
        )

    private var pendingSession:
        PortalSession? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AppScaffold(
                    containerColor =
                        Color.Black,
                    contentColor =
                        Color.White,
                ) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal =
                                        18.dp
                                ),
                        contentPadding =
                            PaddingValues(
                                top = 24.dp,
                                bottom = 24.dp,
                            ),
                        horizontalAlignment =
                            Alignment
                                .CenterHorizontally,
                        verticalArrangement =
                            Arrangement
                                .spacedBy(
                                    10.dp
                                ),
                    ) {
                        item {
                            Text(
                                text =
                                    uiState
                                        .headline,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                                fontWeight =
                                    FontWeight.Bold,
                                textAlign =
                                    TextAlign.Center,
                            )
                        }

                        item {
                            Text(
                                text =
                                    uiState
                                        .detail,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                color =
                                    Color(
                                        0xFFB9C5D6
                                    ),
                                textAlign =
                                    TextAlign.Center,
                            )
                        }

                        if (
                            uiState
                                .portalHost
                                .isNotBlank()
                        ) {
                            item {
                                Text(
                                    text =
                                        uiState
                                            .portalHost,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall,
                                    color =
                                        Color(
                                            0xFF71D7FF
                                        ),
                                    textAlign =
                                        TextAlign.Center,
                                )
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    startActivity(
                                        Intent(
                                            Settings
                                                .ACTION_WIFI_SETTINGS
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        "Wi-Fi settings"
                                    )
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            )
                        }

                        item {
                            Button(
                                onClick =
                                    ::inspectPortal,
                                enabled =
                                    !uiState
                                        .busy,
                                label = {
                                    Text(
                                        if (
                                            uiState
                                                .busy
                                        ) {
                                            "Checking…"
                                        } else {
                                            "Check public Wi-Fi"
                                        }
                                    )
                                },
                                colors =
                                    ButtonDefaults
                                        .buttonColors(
                                            containerColor =
                                                Color(
                                                    0xFF176DFF
                                                )
                                        ),
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            )
                        }

                        if (
                            pendingSession !=
                                null
                        ) {
                            item {
                                Button(
                                    onClick =
                                        ::acceptPortal,
                                    enabled =
                                        !uiState
                                            .busy,
                                    label = {
                                        Text(
                                            uiState
                                                .acceptLabel
                                                .ifBlank {
                                                    "Accept & connect"
                                                }
                                        )
                                    },
                                    colors =
                                        ButtonDefaults
                                            .buttonColors(
                                                containerColor =
                                                    Color(
                                                        0xFF168D5D
                                                    )
                                            ),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),
                                )
                            }
                        }

                        item {
                            TextButton(
                                onClick = {
                                    finish()
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            ) {
                                Text(
                                    "Close"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun inspectPortal() {
        pendingSession = null
        uiState =
            PortalUiState(
                headline =
                    "Checking Wi-Fi…",
                detail =
                    "Looking for a connected Wi-Fi network that is not yet validated.",
                busy = true,
            )

        Thread {
            val result =
                runCatching {
                    client.inspect()
                }.getOrElse {
                    PortalResult(
                        headline =
                            "Probe failed",
                        detail =
                            it.message
                                ?: it
                                    .javaClass
                                    .simpleName,
                    )
                }

            runOnUiThread {
                pendingSession =
                    result.session
                uiState =
                    PortalUiState(
                        headline =
                            result
                                .headline,
                        detail =
                            result
                                .detail,
                        portalHost =
                            result
                                .portalHost,
                        acceptLabel =
                            result
                                .session
                                ?.form
                                ?.submitLabel
                                .orEmpty(),
                    )
            }
        }.start()
    }

    private fun acceptPortal() {
        val session =
            pendingSession
                ?: return

        uiState =
            uiState.copy(
                headline =
                    "Submitting…",
                detail =
                    "Sending the portal form over the Watch Wi-Fi network.",
                busy = true,
            )

        Thread {
            val result =
                runCatching {
                    client.accept(
                        session
                    )
                }.getOrElse {
                    PortalResult(
                        headline =
                            "Portal submit failed",
                        detail =
                            it.message
                                ?: it
                                    .javaClass
                                    .simpleName,
                    )
                }

            runOnUiThread {
                pendingSession =
                    result.session
                uiState =
                    PortalUiState(
                        headline =
                            result
                                .headline,
                        detail =
                            result
                                .detail,
                        portalHost =
                            result
                                .portalHost,
                        acceptLabel =
                            result
                                .session
                                ?.form
                                ?.submitLabel
                                .orEmpty(),
                    )
            }
        }.start()
    }
}

private data class PortalUiState(
    val headline: String,
    val detail: String,
    val portalHost: String = "",
    val acceptLabel: String = "",
    val busy: Boolean = false,
)

internal data class PortalSession(
    val network: Network,
    val form: ParsedPortalForm,
    val cookies: CookieManager,
)

internal data class PortalResult(
    val headline: String,
    val detail: String,
    val portalHost: String = "",
    val session: PortalSession? = null,
)

internal class SimpleCaptivePortalClient(
    private val connectivityManager:
        ConnectivityManager,
) {
    fun inspect(): PortalResult {
        val network =
            findWifiNetwork()
                ?: return PortalResult(
                    headline =
                        "No Wi-Fi network",
                    detail =
                        "Join the public Wi-Fi in system settings first, then check again.",
                )

        val capabilities =
            connectivityManager
                .getNetworkCapabilities(
                    network
                )

        if (
            capabilities
                ?.hasCapability(
                    NetworkCapabilities
                        .NET_CAPABILITY_VALIDATED
                ) == true
        ) {
            return PortalResult(
                headline =
                    "Wi-Fi already online",
                detail =
                    "Android reports this Wi-Fi network as validated.",
            )
        }

        return inspectNetwork(
            network =
                network,
            cookies =
                CookieManager(
                    null,
                    CookiePolicy.ACCEPT_ALL,
                ),
            initialUrl =
                PROBE_URL,
        )
    }

    fun accept(
        session: PortalSession
    ): PortalResult {
        if (
            session
                .form
                .unsupportedFields
                .isNotEmpty()
        ) {
            return PortalResult(
                headline =
                    "Portal needs more input",
                detail =
                    "Unsupported fields: " +
                        session
                            .form
                            .unsupportedFields
                            .joinToString(),
                portalHost =
                    hostOf(
                        session
                            .form
                            .actionUrl
                    ),
            )
        }

        val response =
            submitForm(
                network =
                    session.network,
                form =
                    session.form,
                cookies =
                    session.cookies,
            )

        val afterSubmit =
            followNavigation(
                network =
                    session.network,
                first =
                    response,
                cookies =
                    session.cookies,
            )

        if (
            afterSubmit.code == 204
        ) {
            return connected(
                session.network
            )
        }

        val verification =
            fetch(
                network =
                    session.network,
                url =
                    PROBE_URL,
                method =
                    "GET",
                body = null,
                cookies =
                    session.cookies,
            )

        if (
            verification.code == 204
        ) {
            return connected(
                session.network
            )
        }

        return inspectNetwork(
            network =
                session.network,
            cookies =
                session.cookies,
            initialUrl =
                verification
                    .finalUrl,
            firstResponse =
                verification,
        )
    }

    private fun connected(
        network: Network
    ): PortalResult {
        connectivityManager
            .reportNetworkConnectivity(
                network,
                true,
            )

        return PortalResult(
            headline = "Internet works",
            detail =
                "The Watch reached the 204 connectivity probe through this Wi-Fi network.",
        )
    }

    private fun inspectNetwork(
        network: Network,
        cookies: CookieManager,
        initialUrl: String,
        firstResponse:
            HttpResponse? = null,
    ): PortalResult {
        var response =
            firstResponse
                ?: fetch(
                    network =
                        network,
                    url =
                        initialUrl,
                    method =
                        "GET",
                    body = null,
                    cookies =
                        cookies,
                )

        repeat(
            MAX_NAVIGATION_STEPS
        ) {
            if (
                response.code ==
                    204
            ) {
                return connected(
                    network
                )
            }

            val redirect =
                response
                    .location
            if (
                response.code in
                    300..399 &&
                !redirect.isNullOrBlank()
            ) {
                response =
                    fetch(
                        network =
                            network,
                        url =
                            resolve(
                                response
                                    .finalUrl,
                                redirect
                            ),
                        method =
                            "GET",
                        body = null,
                        cookies =
                            cookies,
                    )
                return@repeat
            }

            val page =
                CaptivePortalFormParser
                    .parse(
                        html =
                            response.body,
                        baseUrl =
                            response
                                .finalUrl,
                    )

            if (
                page
                    .form !=
                    null
            ) {
                val form =
                    page.form
                val host =
                    hostOf(
                        form.actionUrl
                    )

                if (
                    form
                        .unsupportedFields
                        .isNotEmpty()
                ) {
                    return PortalResult(
                        headline =
                            "Portal detected",
                        detail =
                            "This page needs fields the PoC does not fill automatically: " +
                                form
                                    .unsupportedFields
                                    .joinToString(),
                        portalHost =
                            host,
                    )
                }

                return PortalResult(
                    headline =
                        page.title
                            .ifBlank {
                                "Portal detected"
                            },
                    detail =
                        "Simple confirmation form found. Review the portal terms on the venue page before accepting.",
                    portalHost =
                        host,
                    session =
                        PortalSession(
                            network =
                                network,
                            form =
                                form,
                            cookies =
                                cookies,
                        ),
                )
            }

            val metaRefresh =
                page
                    .metaRefreshUrl
            if (
                !metaRefresh
                    .isNullOrBlank()
            ) {
                response =
                    fetch(
                        network =
                            network,
                        url =
                            metaRefresh,
                        method =
                            "GET",
                        body = null,
                        cookies =
                            cookies,
                    )
                return@repeat
            }

            return PortalResult(
                headline =
                    "Portal not understood",
                detail =
                    "The Wi-Fi is not validated, but this PoC found no simple HTML accept form. JavaScript/SSO portals remain unsupported.",
                portalHost =
                    hostOf(
                        response
                            .finalUrl
                    ),
            )
        }

        return PortalResult(
            headline =
                "Too many redirects",
            detail =
                "The captive portal did not settle after $MAX_NAVIGATION_STEPS navigation steps.",
            portalHost =
                hostOf(
                    response
                        .finalUrl
                ),
        )
    }

    private fun submitForm(
        network: Network,
        form: ParsedPortalForm,
        cookies: CookieManager,
    ): HttpResponse {
        val encoded =
            form.fields
                .joinToString("&") {
                    encode(it.first) +
                        "=" +
                        encode(it.second)
                }

        return if (
            form.method ==
                "POST"
        ) {
            fetch(
                network =
                    network,
                url =
                    form.actionUrl,
                method =
                    "POST",
                body =
                    encoded,
                cookies =
                    cookies,
            )
        } else {
            val separator =
                if (
                    form.actionUrl
                        .contains("?")
                ) {
                    "&"
                } else {
                    "?"
                }

            fetch(
                network =
                    network,
                url =
                    if (
                        encoded
                            .isBlank()
                    ) {
                        form.actionUrl
                    } else {
                        form.actionUrl +
                            separator +
                            encoded
                    },
                method =
                    "GET",
                body = null,
                cookies =
                    cookies,
            )
        }
    }

    private fun followNavigation(
        network: Network,
        first: HttpResponse,
        cookies: CookieManager,
    ): HttpResponse {
        var response =
            first

        repeat(
            MAX_NAVIGATION_STEPS
        ) {
            val location =
                response
                    .location
            if (
                response.code !in
                    300..399 ||
                location.isNullOrBlank()
            ) {
                return response
            }

            response =
                fetch(
                    network =
                        network,
                    url =
                        resolve(
                            response
                                .finalUrl,
                            location
                        ),
                    method =
                        "GET",
                    body = null,
                    cookies =
                        cookies,
                )
        }

        return response
    }

    private fun fetch(
        network: Network,
        url: String,
        method: String,
        body: String?,
        cookies: CookieManager,
    ): HttpResponse {
        val target =
            URL(url)
        val uri =
            URI(
                target
                    .toString()
            )
        val connection =
            network
                .openConnection(
                    target
                ) as
                HttpURLConnection

        connection
            .instanceFollowRedirects =
            false
        connection.connectTimeout =
            8_000
        connection.readTimeout =
            8_000
        connection.useCaches =
            false
        connection.requestMethod =
            method
        connection.setRequestProperty(
            "User-Agent",
            "HappyTalky-Wear-CaptivePortal-PoC/1",
        )
        connection.setRequestProperty(
            "Cache-Control",
            "no-cache",
        )

        cookies
            .get(
                uri,
                emptyMap()
            )
            .forEach {
                    entry ->
                connection
                    .setRequestProperty(
                        entry.key,
                        entry.value
                            .joinToString(
                                "; "
                            ),
                    )
            }

        if (
            body != null
        ) {
            val bytes =
                body
                    .toByteArray(
                        StandardCharsets
                            .UTF_8
                    )
            connection.doOutput =
                true
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded",
            )
            connection.setFixedLengthStreamingMode(
                bytes.size
            )
            connection
                .outputStream
                .use {
                    it.write(
                        bytes
                    )
                }
        }

        val code =
            connection
                .responseCode
        val headers =
            connection
                .headerFields
                .filterKeys {
                    it != null
                }
                .mapKeys {
                    it.key!!
                }
        cookies.put(
            uri,
            headers,
        )

        val responseBody =
            readBody(
                connection,
                code,
            )
        val location =
            connection
                .getHeaderField(
                    "Location"
                )

        connection
            .disconnect()

        return HttpResponse(
            code = code,
            finalUrl =
                target
                    .toString(),
            location =
                location,
            body =
                responseBody,
        )
    }

    private fun readBody(
        connection: HttpURLConnection,
        code: Int,
    ): String {
        if (code == 204) {
            return ""
        }

        val stream =
            runCatching {
                if (
                    code >=
                        400
                ) {
                    connection
                        .errorStream
                } else {
                    connection
                        .inputStream
                }
            }.getOrNull()
                ?: return ""

        return BufferedReader(
            InputStreamReader(
                stream,
                StandardCharsets
                    .UTF_8,
            )
        ).use {
            val output =
                StringBuilder()

            while (true) {
                val line =
                    it.readLine()
                        ?: break
                output
                    .append(line)
                    .append('\n')

                if (
                    output.length >
                        MAX_BODY_CHARS
                ) {
                    break
                }
            }

            output
                .toString()
        }
    }

    private fun findWifiNetwork():
        Network? {
        val candidates =
            connectivityManager
                .allNetworks
                .mapNotNull {
                        network ->
                    val capabilities =
                        connectivityManager
                            .getNetworkCapabilities(
                                network
                            )
                            ?: return@mapNotNull null

                    if (
                        !capabilities
                            .hasTransport(
                                NetworkCapabilities
                                    .TRANSPORT_WIFI
                            )
                    ) {
                        return@mapNotNull null
                    }

                    network to
                        capabilities
                }

        return candidates
            .firstOrNull {
                it.second
                    .hasCapability(
                        NetworkCapabilities
                            .NET_CAPABILITY_CAPTIVE_PORTAL
                    )
            }
            ?.first
            ?: candidates
                .firstOrNull {
                    it.second
                        .hasCapability(
                            NetworkCapabilities
                                .NET_CAPABILITY_INTERNET
                        ) &&
                        !it.second
                            .hasCapability(
                                NetworkCapabilities
                                    .NET_CAPABILITY_VALIDATED
                            )
                }
                ?.first
            ?: candidates
                .firstOrNull()
                ?.first
    }

    private fun resolve(
        base: String,
        target: String,
    ): String =
        URL(
            URL(base),
            target
        ).toString()

    private fun hostOf(
        url: String
    ): String =
        runCatching {
            URL(url)
                .host
        }.getOrDefault(
            url
        )

    private fun encode(
        value: String
    ): String =
        URLEncoder.encode(
            value,
            StandardCharsets
                .UTF_8
                .name(),
        )

    private data class HttpResponse(
        val code: Int,
        val finalUrl: String,
        val location: String?,
        val body: String,
    )

    companion object {
        private const val PROBE_URL =
            "http://connectivitycheck.gstatic.com/generate_204"
        private const val MAX_NAVIGATION_STEPS =
            8
        private const val MAX_BODY_CHARS =
            512_000
    }
}
