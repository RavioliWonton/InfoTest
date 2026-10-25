package com.example.infotest.utils

import android.content.ContentResolver
import android.content.Context
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentUtilsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // region Cursor?.getSingleXxx

    @Test
    fun getSingleInt_firstRowValue_returnsValue() {
        assertEquals(42, cursorOf(42, 7).getSingleInt("value"))
    }

    @Test
    fun getSingleLong_firstRowValue_returnsValue() {
        assertEquals(42L, cursorOf(42, 7).getSingleLong("value"))
    }

    @Test
    fun getSingleDouble_firstRowValue_returnsValue() {
        assertEquals(42.0, cursorOf(42, 7).getSingleDouble("value")!!, 0.0)
    }

    @Test
    fun getSingleFloat_firstRowValue_returnsValue() {
        assertEquals(42.0f, cursorOf(42, 7).getSingleFloat("value")!!, 0.0f)
    }

    @Test
    fun getSingleShort_firstRowValue_returnsValue() {
        assertEquals(42.toShort(), cursorOf(42, 7).getSingleShort("value"))
    }

    @Test
    fun getSingleString_value_returnsText() {
        assertEquals("hello", cursorOf("hello").getSingleString("value"))
    }

    @Test
    fun getSingleBlob_value_returnsBytes() {
        val blob = byteArrayOf(1, 2, 3)
        assertArrayEquals(blob, cursorOf(blob).getSingleBlob("value"))
    }

    @Test
    fun getSingleBlob_textColumn_returnsNull() {
        assertNull(cursorOf("hello").getSingleBlob("value"))
    }

    @Test
    fun getSingleInt_nullCursor_returnsNull() {
        val cursor: Cursor? = null
        assertNull(cursor.getSingleInt("value"))
    }

    @Test
    fun getSingleString_nullCursor_returnsNull() {
        val cursor: Cursor? = null
        assertNull(cursor.getSingleString("value"))
    }

    @Test
    fun getSingleLong_nullCursor_returnsNull() {
        val cursor: Cursor? = null
        assertNull(cursor.getSingleLong("value"))
    }

    @Test
    fun getSingleDouble_nullCursor_returnsNull() {
        val cursor: Cursor? = null
        assertNull(cursor.getSingleDouble("value"))
    }

    @Test
    fun getSingleFloat_nullCursor_returnsNull() {
        val cursor: Cursor? = null
        assertNull(cursor.getSingleFloat("value"))
    }

    @Test
    fun getSingleShort_nullCursor_returnsNull() {
        val cursor: Cursor? = null
        assertNull(cursor.getSingleShort("value"))
    }

    @Test
    fun getSingleBlob_nullCursor_returnsNull() {
        val cursor: Cursor? = null
        assertNull(cursor.getSingleBlob("value"))
    }

    @Test
    fun getSingleInt_emptyCursor_returnsNull() {
        assertNull(FakeCursor(listOf("value"), emptyList()).getSingleInt("value"))
    }

    @Test
    fun getSingleInt_missingColumn_returnsNull() {
        assertNull(cursorOf(1).getSingleInt("missing"))
    }

    @Test
    fun getSingleInt_nullCellValue_returnsNull() {
        val cursor = FakeCursor(listOf("value"), listOf(mapOf("value" to null)))
        assertNull(cursor.getSingleInt("value"))
        assertNull(cursor.getSingleString("value"))
        assertNull(cursor.getSingleBlob("value"))
    }

    private fun cursorOf(vararg values: Any?) =
        FakeCursor(listOf("value"), values.map { mapOf("value" to it) })

    // endregion

    // region context based helpers (Robolectric)

    @Test
    fun getSystemDefaultLocale_defaultEnvironment_returnsEnglish() {
        val locale = context.getSystemDefaultLocale()
        assertEquals("en", locale.language)
    }

    @Test
    fun countContent_noUris_returnsZero() {
        assertEquals(0, context.countContent())
    }

    @Test
    fun countContent_emptyUri_isIgnored() {
        assertEquals(0, context.countContent(Uri.EMPTY))
    }

    @Test
    fun imageExternalUri_matchesMediaStoreAuthority() {
        assertEquals(MediaStore.AUTHORITY, imageExternalUri.authority)
    }

    // endregion

    /** Minimal in-memory [Cursor]; only the reading path used by the helpers is implemented. */
    @Suppress("DEPRECATION")
    private class FakeCursor(
        private val columns: List<String>,
        private val rows: List<Map<String, Any?>>
    ) : Cursor {
        private var position = -1

        override fun getCount() = rows.size
        override fun getPosition() = position

        override fun move(offset: Int) = moveToPosition(position + offset)
        override fun moveToPosition(position: Int): Boolean {
            if (position < 0 || position >= rows.size) {
                this.position = if (position < 0) -1 else rows.size
                return false
            }
            this.position = position
            return true
        }

        override fun moveToFirst() = moveToPosition(0)
        override fun moveToLast() = moveToPosition(rows.size - 1)
        override fun moveToNext() = moveToPosition(position + 1)
        override fun moveToPrevious() = moveToPosition(position - 1)

        override fun isFirst() = position == 0 && rows.isNotEmpty()
        override fun isLast() = position == rows.size - 1 && rows.isNotEmpty()
        override fun isBeforeFirst() = position == -1 && rows.isNotEmpty()
        override fun isAfterLast() = position == rows.size && rows.isNotEmpty()

        override fun getColumnNames() = columns.toTypedArray()
        override fun getColumnCount() = columns.size
        override fun getColumnName(columnIndex: Int) = columns[columnIndex]
        override fun getColumnIndex(columnName: String) = columns.indexOf(columnName)
        override fun getColumnIndexOrThrow(columnName: String) =
            getColumnIndex(columnName).takeIf { it >= 0 } ?: throw IllegalArgumentException(columnName)

        private fun value(columnIndex: Int): Any? =
            if (columnIndex in columns.indices && position in rows.indices)
                rows[position][columns[columnIndex]] else null

        override fun isNull(columnIndex: Int) = value(columnIndex) == null
        override fun getType(columnIndex: Int) = when (value(columnIndex)) {
            null -> Cursor.FIELD_TYPE_NULL
            is ByteArray -> Cursor.FIELD_TYPE_BLOB
            is Float, is Double -> Cursor.FIELD_TYPE_FLOAT
            is Number -> Cursor.FIELD_TYPE_INTEGER
            else -> Cursor.FIELD_TYPE_STRING
        }

        override fun getBlob(columnIndex: Int) = value(columnIndex) as? ByteArray
        override fun getString(columnIndex: Int) = value(columnIndex)?.toString()
        override fun getShort(columnIndex: Int) = (value(columnIndex) as? Number)?.toShort() ?: 0
        override fun getInt(columnIndex: Int) = (value(columnIndex) as? Number)?.toInt() ?: 0
        override fun getLong(columnIndex: Int) = (value(columnIndex) as? Number)?.toLong() ?: 0L
        override fun getFloat(columnIndex: Int) = (value(columnIndex) as? Number)?.toFloat() ?: 0f
        override fun getDouble(columnIndex: Int) = (value(columnIndex) as? Number)?.toDouble() ?: 0.0

        override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer?) =
            throw UnsupportedOperationException("not needed for these tests")

        override fun deactivate() = Unit
        override fun requery() = true
        override fun close() = Unit
        override fun isClosed() = false
        override fun getWantsAllOnMoveCalls() = false
        override fun getExtras(): Bundle? = null
        override fun setExtras(extras: Bundle?) = Unit
        override fun respond(extras: Bundle?): Bundle? = null
        override fun getNotificationUri(): Uri? = null
        override fun setNotificationUri(cr: ContentResolver?, uri: Uri?) = Unit
        override fun registerContentObserver(observer: ContentObserver?) = Unit
        override fun unregisterContentObserver(observer: ContentObserver?) = Unit
        override fun registerDataSetObserver(observer: DataSetObserver?) = Unit
        override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit
    }
}
