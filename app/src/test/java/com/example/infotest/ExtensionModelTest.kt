package com.example.infotest

import com.example.infotest.utils.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire format of [ExtensionModel]: the backend contract depends on the
 * `@SerialName` keys and on the [serializer] configuration (nulls omitted, unknown keys ignored).
 */
class ExtensionModelTest {

    private val contact = Contact(
        name = "Alice",
        phoneNumber = "10086",
        updateTime = 1L,
        lastTimeContacted = 2L,
        timesContacted = 3
    )

    private val app = App(
        name = "InfoTest",
        packageName = "com.example.infotest",
        versionCode = "11",
        obtainTime = 1L,
        appType = "user",
        installTime = 2L,
        updateTime = 3L,
        appVersion = "2.0.1"
    )

    private val callRecord = CallRecords(
        id = 1L,
        number = "10010",
        date = 2L,
        duration = 3L,
        features = 0,
        type = 1,
        countryIso = "CN",
        geocodedLocation = "Shanghai"
    )

    @Test
    fun encode_emptyModel_omitsNullMembers() {
        // explicitNulls = false -> an all-null model must serialize to an empty object
        assertEquals("{}", serializer.encodeToString(ExtensionModel.serializer(), ExtensionModel()))
    }

    @Test
    fun encode_topLevelFields_useSnakeCaseNames() {
        val json = serializer.encodeToString(
            ExtensionModel.serializer(),
            ExtensionModel(contacts = listOf(contact), apps = listOf(app), callLogs = listOf(callRecord))
        )
        assertTrue(json.contains("\"address_book\""))
        assertTrue(json.contains("\"app_list\""))
        assertTrue(json.contains("\"call_log\""))
        assertFalse(json.contains("\"contacts\""))
        assertFalse(json.contains("\"callLogs\""))
    }

    @Test
    fun encode_nestedFields_useSnakeCaseNames() {
        val json = serializer.encodeToString(ExtensionModel.serializer(), ExtensionModel(contacts = listOf(contact)))
        assertTrue(json.contains("\"contact_display_name\""))
        assertTrue(json.contains("\"last_time_contacted\""))
        assertTrue(json.contains("\"times_contacted\""))
        assertFalse(json.contains("\"phoneNumber\""))
    }

    @Test
    fun roundTrip_fullModel_keepsValues() {
        val model = ExtensionModel(
            contacts = listOf(contact),
            apps = listOf(app),
            sms = listOf(Sms("10086", "hi", 0, 1, null, -1, 100L, 1, "com.android.mms")),
            callLogs = listOf(callRecord)
        )
        val json = serializer.encodeToString(ExtensionModel.serializer(), model)
        assertEquals(model, serializer.decodeFromString(ExtensionModel.serializer(), json))
    }

    @Test
    fun decode_photoFields_areReadFromSerialNames() {
        val json = """
            {"photoInfos":[{"name":"a","author":"b","height":"1","width":"2","latitude":null,
            "longitude":null,"date":null,"createTime":"c","saveTime":"s","takeTime":"t",
            "orientation":"0","xResolution":"1","yResolution":"1","gps_altitude":"12.5",
            "gpsProcessingMethod":"m","lensMake":"lm","lensModel":"lmo","focalLength":"f",
            "flash":"fl","software":"sw","model":"m"}]}
        """.trimIndent()
        val model = serializer.decodeFromString(ExtensionModel.serializer(), json)
        assertEquals("12.5", model.photos?.single()?.altitude)
        assertNull(model.photos?.single()?.latitude)
        assertNull(model.device)
    }

    @Test
    fun decode_unknownKeys_areIgnored() {
        val model = serializer.decodeFromString(
            ExtensionModel.serializer(),
            """{"unknown_key":123,"address_book":[{"contact_display_name":"Bob","number":"1",
               "up_time":1,"last_time_contacted":2,"times_contacted":3}],"future_field":{"a":1}}"""
        )
        assertEquals("Bob", model.contacts?.single()?.name)
        assertNull(model.apps)
    }

    @Test
    fun decode_absentOptionalFields_useDefaults() {
        val json = """{"call_log":[{"id":1,"number":"10010","date":2,"duration":3,
            "features":0,"type":1,"countryIso":"CN","geocodedLocation":"Shanghai"}]}"""
        val record = serializer.decodeFromString(ExtensionModel.serializer(), json).callLogs?.single()
        assertEquals("10010", record?.number)
        assertNull(record?.caller)
        assertNull(record?.latitude)
        assertNull(record?.longitude)
    }
}
