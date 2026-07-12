package dev.twosec.app.ui

import dev.twosec.app.platform.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {

    private fun app(packageName: String, label: String): InstalledApp =
        InstalledApp(packageName = packageName, label = label)

    @Test
    fun `empty query returns input list unchanged`() {
        val apps = listOf(
            app("com.a", "Alpha"),
            app("com.b", "Beta"),
        )

        assertEquals(apps, filterApps(apps, ""))
    }

    @Test
    fun `whitespace-only query returns input list unchanged`() {
        val apps = listOf(
            app("com.a", "Alpha"),
            app("com.b", "Beta"),
        )

        assertEquals(apps, filterApps(apps, "   "))
    }

    @Test
    fun `substring match is case-insensitive`() {
        val apps = listOf(
            app("com.tt", "TikTok"),
            app("com.ig", "Instagram"),
            app("com.rd", "Reddit"),
        )

        assertEquals(
            listOf(app("com.tt", "TikTok")),
            filterApps(apps, "TIK"),
        )
        assertEquals(
            listOf(app("com.tt", "TikTok")),
            filterApps(apps, "tik"),
        )
    }

    @Test
    fun `match can be anywhere in the label`() {
        val apps = listOf(
            app("com.ig", "Instagram"),
            app("com.tt", "TikTok"),
        )

        assertEquals(
            listOf(app("com.ig", "Instagram")),
            filterApps(apps, "gram"),
        )
    }

    @Test
    fun `no match returns empty list`() {
        val apps = listOf(
            app("com.a", "Alpha"),
            app("com.b", "Beta"),
        )

        assertEquals(emptyList<InstalledApp>(), filterApps(apps, "zzz"))
    }

    @Test
    fun `empty input list returns empty regardless of query`() {
        assertEquals(emptyList<InstalledApp>(), filterApps(emptyList(), "anything"))
    }

    @Test
    fun `original order is preserved`() {
        val apps = listOf(
            app("com.zoo", "Zoo"),
            app("com.alpha", "Alpha"),
            app("com.mango", "Mango"),
            app("com.kite", "Kite"),
            app("com.bronze", "Bronze"),
        )

        assertEquals(
            listOf(
                app("com.mango", "Mango"),
                app("com.bronze", "Bronze"),
            ),
            filterApps(apps, "n"),
        )
    }

    @Test
    fun `query with regex metacharacters does not break the matcher`() {
        val apps = listOf(
            app("com.regex", "a.b.c"),
            app("com.brackets", "x[1]y"),
            app("com.paren", "foo(bar)"),
            app("com.dollar", "\$cash"),
            app("com.plain", "Plain"),
        )

        assertEquals(
            listOf(app("com.regex", "a.b.c")),
            filterApps(apps, "a.b.c"),
        )
        assertEquals(
            listOf(app("com.brackets", "x[1]y")),
            filterApps(apps, "x[1]y"),
        )
        assertEquals(
            listOf(app("com.dollar", "\$cash")),
            filterApps(apps, "\$"),
        )
        assertEquals(
            listOf(app("com.paren", "foo(bar)")),
            filterApps(apps, "(bar)"),
        )
        assertEquals(
            emptyList<InstalledApp>(),
            filterApps(apps, ".*"),
        )
    }
}
