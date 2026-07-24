package icu.nd4y.netswitcher.share

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Type4EmulatorTest {

    private val message = byteArrayOf(0xD1.toByte(), 0x01, 0x03, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())

    private fun armed(): Type4Emulator = Type4Emulator().apply {
        ndefFile = Type4Emulator.ndefFileFor(message)
    }

    private fun ByteArray.sw(): Pair<Int, Int> =
        (this[size - 2].toInt() and 0xFF) to (this[size - 1].toInt() and 0xFF)

    private fun ByteArray.body(): ByteArray = copyOfRange(0, size - 2)

    @Test
    fun `full read sequence returns the message`() {
        val e = armed()

        // SELECT NDEF application by AID (with a trailing Le byte).
        val selectApp = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01, 0x00,
        )
        assertEquals(0x90 to 0x00, e.process(selectApp).sw())

        // SELECT + READ capability container.
        assertEquals(0x90 to 0x00, e.process(byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)).sw())
        val cc = e.process(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F))
        assertEquals(0x90 to 0x00, cc.sw())
        assertArrayEquals(Type4Emulator.CC_FILE, cc.body())

        // SELECT NDEF file.
        assertEquals(0x90 to 0x00, e.process(byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)).sw())

        // Read the 2-byte NLEN — must equal the message length.
        val nlen = e.process(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02))
        assertEquals(0x90 to 0x00, nlen.sw())
        val declared = ((nlen.body()[0].toInt() and 0xFF) shl 8) or (nlen.body()[1].toInt() and 0xFF)
        assertEquals(message.size, declared)
        assertFalse(e.servedContent) // reading the length header is not the content

        // Read the message body at offset 2.
        val content = e.process(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, message.size.toByte()))
        assertEquals(0x90 to 0x00, content.sw())
        assertArrayEquals(message, content.body())
        assertTrue(e.servedContent)
    }

    @Test
    fun `select application fails when nothing is armed`() {
        val e = Type4Emulator()
        val selectApp = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01, 0x00,
        )
        assertEquals(0x6A to 0x82, e.process(selectApp).sw())
    }

    @Test
    fun `reading before selecting a file is refused`() {
        val e = armed()
        assertEquals(0x6A to 0x82, e.process(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F)).sw())
    }

    @Test
    fun `unknown instruction is rejected`() {
        val e = armed()
        assertEquals(0x6D to 0x00, e.process(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x00)).sw())
    }
}
