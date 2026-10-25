package com.example.infotest.utils

import android.content.Context
import androidx.collection.ObjectList
import androidx.collection.emptyObjectList
import androidx.collection.objectListOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@RunWith(AndroidJUnit4::class)
class GeneralUtilsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // region then

    @Test
    fun then_conditionTrue_returnsValue() {
        assertEquals("value", true then "value")
    }

    @Test
    fun then_conditionFalse_returnsNull() {
        assertNull(false then "value")
    }

    // endregion

    // region toInt / toBoolean

    @Test
    fun toInt_nullValue_returnsNull() {
        assertNull((null as Boolean?).toInt())
    }

    @Test
    fun toInt_trueValue_returnsOne() {
        assertEquals(1, true.toInt())
    }

    @Test
    fun toInt_falseValue_returnsZero() {
        assertEquals(0, false.toInt())
    }

    @Test
    fun toBoolean_nullValue_returnsNull() {
        assertNull((null as Int?).toBoolean())
    }

    @Test
    fun toBoolean_positiveValue_returnsTrue() {
        assertEquals(true, 1.toBoolean())
    }

    @Test
    fun toBoolean_zeroValue_returnsFalse() {
        assertEquals(false, 0.toBoolean())
    }

    @Test
    fun toBoolean_negativeValue_returnsFalse() {
        assertEquals(false, (-3).toBoolean())
    }

    // endregion

    // region emitException

    @Test
    fun emitException_unexpectedException_isSwallowed() {
        val executed = booleanArrayOf(false)
        emitException {
            executed[0] = true
            throw IllegalStateException("boom")
        }
        assertTrue(executed[0])
    }

    @Test
    fun emitException_listedException_isRethrown() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            emitException(IllegalArgumentException::class.java) {
                throw IllegalArgumentException("expected")
            }
        }
        assertEquals("expected", error.message)
    }

    @Test
    fun emitException_unlistedException_isSwallowed() {
        emitException(IllegalArgumentException::class.java) {
            throw IllegalStateException("must be swallowed")
        }
    }

    // endregion

    // region catchReturn family

    @Test
    fun catchReturn_value_returnsValue() {
        val value: () -> String? = { "v" }
        assertEquals("v", value.catchReturn("default"))
    }

    @Test
    fun catchReturn_nullValue_returnsDefault() {
        val nullValue: () -> String? = { null }
        assertEquals("default", nullValue.catchReturn("default"))
    }

    @Test
    fun catchReturn_throwingBlock_returnsDefault() {
        val throwing: () -> String? = { throw IllegalStateException("boom") }
        assertEquals("default", throwing.catchReturn("default"))
    }

    @Test
    fun catchReturn_listedException_isRethrown() {
        val listed: () -> String? = { throw IllegalArgumentException("boom") }
        assertThrows(IllegalArgumentException::class.java) {
            listed.catchReturn("default", IllegalArgumentException::class.java)
        }
    }

    @Test
    fun catchReturnNull_value_returnsValue() {
        val value: () -> String? = { "v" }
        assertEquals("v", value.catchReturnNull())
    }

    @Test
    fun catchReturnNull_throwingBlock_returnsNull() {
        val throwing: () -> String? = { throw IllegalStateException("boom") }
        assertNull(throwing.catchReturnNull())
    }

    @Test
    fun catchReturnNull_throwingBlock_returnsFallback() {
        val throwing: () -> String? = { throw IllegalStateException("boom") }
        assertEquals("fallback", throwing.catchReturnNull("fallback"))
    }

    @Test
    fun catchReturnNull_listedException_isRethrown() {
        val listed: () -> String? = { throw IllegalArgumentException("boom") }
        assertThrows(IllegalArgumentException::class.java) {
            listed.catchReturnNull("fallback", IllegalArgumentException::class.java)
        }
    }

    @Test
    fun catchEmpty_list_returnsValue() {
        val listValue: () -> List<String>? = { listOf("a") }
        assertEquals(listOf("a"), listValue.catchEmpty())
    }

    @Test
    fun catchEmpty_throwingList_returnsEmptyList() {
        val throwing: () -> List<String>? = { throw IllegalStateException("boom") }
        assertTrue(throwing.catchEmpty().isEmpty())
    }

    @Test
    fun catchEmpty_throwingArray_returnsEmptyArray() {
        val throwing: () -> Array<String>? = { throw IllegalStateException("boom") }
        assertEquals(0, throwing.catchEmpty().size)
    }

    @Test
    fun catchEmpty_throwingObjectList_returnsEmptyObjectList() {
        val throwing: () -> ObjectList<Int>? = { throw IllegalStateException("boom") }
        assertEquals(0, throwing.catchEmpty().size)
    }

    @Test
    fun catchEmpty_throwingString_returnsEmptyString() {
        val throwing: () -> String? = { throw IllegalStateException("boom") }
        assertEquals("", throwing.catchEmpty())
    }

    @Test
    fun catchZero_throwingBlocks_returnZero() {
        val throwingInt: () -> Int? = { throw IllegalStateException("boom") }
        val throwingLong: () -> Long? = { throw IllegalStateException("boom") }
        val throwingDouble: () -> Double? = { throw IllegalStateException("boom") }

        assertEquals(0, throwingInt.catchZero())
        assertEquals(0L, throwingLong.catchZero())
        assertEquals(0.0, throwingDouble.catchZero(), 0.0)
    }

    @Test
    fun catchFalse_throwingBlock_returnsFalse() {
        val throwing: () -> Boolean? = { throw IllegalStateException("boom") }
        assertFalse(throwing.catchFalse())
    }

    @Test
    fun catchFalse_trueValue_returnsTrue() {
        assertTrue({ true }.catchFalse())
    }

    @Test
    fun applyEmitException_throwingBlock_keepsReceiver() {
        val result = StringBuilder("keep").applyEmitException {
            append("!")
            throw IllegalStateException("boom")
        }
        assertEquals("keep!", result.toString())
    }

    // endregion

    // region javaType / paramTypes

    @Test
    fun javaType_primitiveValue_returnsPrimitiveClass() {
        assertEquals(Int::class.javaPrimitiveType, (1 as Any?).javaType)
        assertEquals(Long::class.javaPrimitiveType, (1L as Any?).javaType)
        assertEquals(Double::class.javaPrimitiveType, (1.0 as Any?).javaType)
        assertEquals(Boolean::class.javaPrimitiveType, (true as Any?).javaType)
    }

    @Test
    fun javaType_referenceValue_returnsRuntimeClass() {
        assertEquals(String::class.java, ("s" as Any?).javaType)
    }

    @Test
    fun javaType_nullValue_returnsNull() {
        assertNull((null as Any?).javaType)
    }

    @Test
    fun paramTypes_nullElement_mapsToStringClass() {
        val params: List<Any?> = listOf(1, 2L, "a", null, false)
        assertEquals(
            listOf(
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            ),
            params.paramTypes()
        )
    }

    // endregion

    // region ObjectList extensions

    @Test
    fun objectListAll_allElementsMatch_returnsTrue() {
        assertTrue(objectListOf(1, 2, 3).all { it > 0 })
    }

    @Test
    fun objectListAll_elementDoesNotMatch_returnsFalse() {
        assertFalse(objectListOf(1, 0, 3).all { it > 0 })
    }

    @Test
    fun objectListAll_emptyList_returnsTrue() {
        assertTrue(emptyObjectList<Int>().all { false })
    }

    @Test
    fun objectListMapNotNull_nullResults_areDropped() {
        val mapped = objectListOf(1, 2, 3).mapNotNull { if (it > 1) it * 2 else null }
        assertEquals(2, mapped.size)
        assertEquals(4, mapped[0])
        assertEquals(6, mapped[1])
    }

    @Test
    fun objectListFirstNotNullOfOrNull_matchExists_returnsFirstMatch() {
        assertEquals("two", objectListOf(1, 2, 3).firstNotNullOfOrNull { if (it == 2) "two" else null })
    }

    @Test
    fun objectListFirstNotNullOfOrNull_noMatch_returnsNull() {
        assertNull(objectListOf(1, 2, 3).firstNotNullOfOrNull<Int, String> { null })
    }

    @Test
    fun iterable_asObjectList_keepsElements() {
        val objectList = listOf(1, 2, 3).asObjectList()
        assertEquals(3, objectList.size)
        assertEquals(2, objectList[1])
    }

    @Test
    fun iterable_asMutableObjectList_allowsAdd() {
        val mutable = sequenceOf("a", "b").asIterable().asMutableObjectList()
        mutable.add("c")
        assertEquals(3, mutable.size)
    }

    // endregion

    // region reflection / misc helpers

    @Test
    fun getClassOrNull_existingClass_returnsClass() {
        assertNotNull(getClassOrNull("java.lang.String"))
    }

    @Test
    fun getClassOrNull_missingClass_returnsNull() {
        assertNull(getClassOrNull("com.example.does.not.Exist"))
    }

    @Test
    fun getMessageDigestInstanceOrNull_knownAlgorithm_returnsInstance() {
        assertNotNull(getMessageDigestInstanceOrNull("SHA-256"))
    }

    @Test
    fun getMessageDigestInstanceOrNull_unknownAlgorithm_returnsNull() {
        assertNull(getMessageDigestInstanceOrNull("definitely-not-an-algorithm"))
    }

    @Test
    fun toUUIDorNull_validUuid_returnsUuid() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000".toUUIDorNull()
        assertEquals("550e8400-e29b-41d4-a716-446655440000", uuid?.toString())
    }

    @Test
    fun toUUIDorNull_invalidUuid_returnsNull() {
        assertNull("not-a-uuid".toUUIDorNull())
        assertNull("".toUUIDorNull())
        assertNull((null as String?).toUUIDorNull())
    }

    @Test
    fun reflectionHelpers_privateMembers_areAccessible() {
        val sample = Sample.create(7)

        val secretField = Sample::class.java.getAccessibleField("secret", true)
        assertNotNull(secretField)
        assertEquals(7, secretField!!.get(sample))
        assertEquals("HI bob", Sample::class.java.getDeclaredAccessibleMethod("shout", String::class.java)
            .invoke(sample, "bob"))
        assertTrue(Sample::class.java.hasMethod(true, "shout", String::class.java))
        assertFalse(Sample::class.java.hasMethod(false, "shout", String::class.java))
        assertFalse(Sample::class.java.hasMethod(true, "missing"))
        assertNotNull(Sample::class.java.getAccessibleConstructor(true, Int::class.java))
    }

    @Test
    fun getAccessibleMethod_publicMethod_returnsMethod() {
        assertEquals(3, String::class.java.getAccessibleMethod("length").invoke("abc"))
    }

    private class Sample private constructor(private val secret: Int) {
        private fun shout(name: String) = "HI $name"

        companion object {
            fun create(value: Int) = Sample(value)
        }
    }

    // endregion

    // region file helpers

    @Test
    fun anyExist_existingPath_returnsTrue() {
        val dir = Files.createTempDirectory("anyExist").toFile()
        try {
            Files.createFile(dir.toPath().resolve("child"))
            assertTrue(arrayOf(dir.absolutePath).anyExist())
            assertTrue(arrayOf(dir.absolutePath).anyExist(subPath = arrayOf("child")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun anyExist_missingPath_returnsFalse() {
        val dir = Files.createTempDirectory("anyExist").toFile()
        try {
            assertFalse(arrayOf(dir.absolutePath).anyExist(subPath = arrayOf("missing")))
            assertFalse(arrayOf(dir.absolutePath + "-missing").anyExist())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun anyExist_nullArray_returnsFalse() {
        assertFalse((null as Array<String>?).anyExist())
    }

    @Test
    fun execCommand_missingBinary_returnsNull() {
        // passing arguments makes ProcessBuilder start the binary directly (no shell), so a bogus name always fails
        assertNull(execCommand("definitely-not-a-real-command-xyz", "arg"))
        assertNull(execCommandSize("definitely-not-a-real-command-xyz", "arg"))
    }

    // endregion

    // region sdk / device helpers (Robolectric)

    @Test
    fun buildBetween_sdkWithinRange_returnsTrue() {
        assertTrue(buildBetween(24, 37))
    }

    @Test
    fun buildBetween_sdkOutsideRange_returnsFalse() {
        assertFalse(buildBetween(1, 0))
    }

    @Test
    fun sdkFlags_recentSdk_areEnabled() {
        assertTrue(atLeastM)
        assertTrue(atLeastN)
        assertTrue(atLeastQ)
    }

    @Test
    fun isColorOS_nonOppoDevice_returnsFalse() {
        assertFalse(isColorOS())
    }

    @Test
    fun unwrapToAnyOf_plainContext_returnsItself() {
        assertEquals(context, context.unwrapToAnyOf(Context::class.java))
    }

    @Test
    fun findActivity_nonActivityContext_returnsNull() {
        assertNull(context.findActivity())
    }

    @Test
    fun getSystemProperty_missingProperty_returnsDefault() {
        assertEquals("fallback", getSystemProperty("definitely.not.a.property", "fallback"))
    }

    // endregion
}
