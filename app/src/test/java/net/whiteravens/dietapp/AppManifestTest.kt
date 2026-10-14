// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** What the app lets out of the phone and whom it trusts, as its manifest and the resources it names say it. */
class AppManifestTest {
    private fun xml(path: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path))

    private fun Document.elements(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    private fun Element.elements(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    private val application = xml("src/main/AndroidManifest.xml").elements("application").single()

    @Test
    fun `nothing the app keeps goes into a backup or onto another device`() {
        assertEquals("false", application.getAttributeNS(ANDROID, "allowBackup"))
        assertEquals("false", application.getAttributeNS(ANDROID, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ANDROID, "dataExtractionRules"))

        val rules = xml("src/main/res/xml/data_extraction_rules.xml")
        for (kind in listOf("cloud-backup", "device-transfer")) {
            val section = rules.elements(kind).single()
            val excluded = section.elements("exclude").filter { it.getAttribute("path") == "." }.map { it.getAttribute("domain") }

            assertEquals(kind, DOMAINS, excluded.toSet())
            assertEquals(kind, emptyList<Element>(), section.elements("include"))
        }
    }

    @Test
    fun `a release build talks HTTPS only and trusts the authorities of the system and of the user`() {
        assertEquals("@xml/network_security_config", application.getAttributeNS(ANDROID, "networkSecurityConfig"))

        val release = xml("src/main/res/xml/network_security_config.xml")
        assertEquals("false", release.elements("base-config").single().getAttribute("cleartextTrafficPermitted"))
        assertEquals(listOf("system", "user"), release.elements("certificates").map { it.getAttribute("src") })
        assertEquals(emptyList<Element>(), release.elements("domain-config"))
    }

    @Test
    fun `a debug build also talks plain http and trusts what a release build trusts`() {
        val debug = xml("src/debug/res/xml/network_security_config.xml")

        assertEquals("true", debug.elements("base-config").single().getAttribute("cleartextTrafficPermitted"))
        assertEquals(listOf("system", "user"), debug.elements("certificates").map { it.getAttribute("src") })
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"

        /** Every place an app's data lives, in the words of the data extraction rules. */
        val DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
