package com.xldev.happytalky.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptivePortalFormParserTest {
    @Test
    fun parsesSimpleTermsForm() {
        val page =
            CaptivePortalFormParser.parse(
                html =
                    """
                    <html>
                      <head><title>Guest Wi-Fi</title></head>
                      <body>
                        <form method="post" action="/login">
                          <input type="hidden" name="token" value="abc">
                          <label><input type="checkbox" name="terms" value="yes">I agree to the terms</label>
                          <button type="submit" name="action" value="accept">Go online</button>
                        </form>
                      </body>
                    </html>
                    """.trimIndent(),
                baseUrl =
                    "http://portal.example/start",
            )

        val form =
            assertNotNull(
                page.form
            ).let {
                page.form!!
            }

        assertEquals(
            "POST",
            form.method,
        )
        assertEquals(
            "http://portal.example/login",
            form.actionUrl,
        )
        assertTrue(
            form.fields.contains(
                "token" to "abc"
            )
        )
        assertTrue(
            form.fields.contains(
                "terms" to "yes"
            )
        )
        assertTrue(
            form.fields.contains(
                "action" to "accept"
            )
        )
        assertTrue(
            form.unsupportedFields
                .isEmpty()
        )
    }

    @Test
    fun refusesPortalThatNeedsEmail() {
        val page =
            CaptivePortalFormParser.parse(
                html =
                    """
                    <form method="post" action="/login">
                      <input type="email" name="email">
                      <button type="submit">Continue</button>
                    </form>
                    """.trimIndent(),
                baseUrl =
                    "http://portal.example/",
            )

        val form =
            page.form!!

        assertEquals(
            listOf("email:email"),
            form.unsupportedFields,
        )
    }

    @Test
    fun supportsAcceptLinkWithoutForm() {
        val page =
            CaptivePortalFormParser.parse(
                html =
                    """
                    <html><body>
                      <a href="/accepted">Continue online</a>
                    </body></html>
                    """.trimIndent(),
                baseUrl =
                    "http://portal.example/start",
            )

        val form =
            page.form!!

        assertEquals(
            "GET",
            form.method,
        )
        assertEquals(
            "http://portal.example/accepted",
            form.actionUrl,
        )
    }

    @Test
    fun resolvesMetaRefresh() {
        val page =
            CaptivePortalFormParser.parse(
                html =
                    """
                    <html>
                      <head>
                        <meta http-equiv="refresh" content="0; url=/portal">
                      </head>
                    </html>
                    """.trimIndent(),
                baseUrl =
                    "http://gateway.example/start",
            )

        assertEquals(
            "http://gateway.example/portal",
            page.metaRefreshUrl,
        )
    }

    @Test
    fun javascriptOnlyPortalIsNotPretendedSupported() {
        val page =
            CaptivePortalFormParser.parse(
                html =
                    """
                    <html><body>
                      <script>launchPortal()</script>
                    </body></html>
                    """.trimIndent(),
                baseUrl =
                    "http://portal.example/",
            )

        assertNull(
            page.form
        )
    }
    @Test
    fun sendsOnlyPreferredAcceptSubmitControl() {
        val page =
            CaptivePortalFormParser.parse(
                html =
                    """
                    <form method="post" action="/decision">
                      <input type="hidden" name="token" value="abc">
                      <button type="submit" name="decision" value="decline">Decline</button>
                      <button type="submit" name="decision" value="accept">Accept & connect</button>
                    </form>
                    """.trimIndent(),
                baseUrl =
                    "http://portal.example/start",
            )

        val form =
            page.form!!

        assertTrue(
            form.fields.contains(
                "decision" to "accept"
            )
        )
        assertTrue(
            !form.fields.contains(
                "decision" to "decline"
            )
        )
    }

    @Test
    fun recognisesSwedishAcceptAction() {
        val page =
            CaptivePortalFormParser.parse(
                html =
                    """
                    <form method="post" action="/wifi">
                      <label><input type="checkbox" name="villkor" value="1">Jag godkänner villkoren</label>
                      <button type="submit" name="action" value="ok">Godkänn och anslut</button>
                    </form>
                    """.trimIndent(),
                baseUrl =
                    "http://portal.example/start",
            )

        val form =
            page.form!!

        assertTrue(
            form.unsupportedFields
                .isEmpty()
        )
        assertEquals(
            "Godkänn och anslut",
            form.submitLabel,
        )
        assertTrue(
            form.fields.contains(
                "villkor" to "1"
            )
        )
    }

}
