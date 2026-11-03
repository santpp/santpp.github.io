package app.rawapp.develop.dng

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds a tiny uncompressed Bayer DNG in memory, laid out the way DngCreator does. */
class TestDng(private val order: ByteOrder) {
    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    private val entries = ArrayList<Entry>()

    fun shorts(tag: Int, vararg v: Int) = add(tag, 3, v.size) { b -> v.forEach { b.putShort(it.toShort()) } }
    fun longs(tag: Int, vararg v: Int) = add(tag, 4, v.size) { b -> v.forEach { b.putInt(it) } }
    fun bytes(tag: Int, vararg v: Int) = add(tag, 1, v.size) { b -> v.forEach { b.put(it.toByte()) } }
    fun undefined(tag: Int, data: ByteArray) = add(tag, 7, data.size) { it.put(data) }
    fun rationals(tag: Int, vararg v: Double) = add(tag, 5, v.size) { b -> v.forEach { b.putInt((it * 10000).toInt()); b.putInt(10000) } }
    fun srationals(tag: Int, vararg v: Double) = add(tag, 10, v.size) { b -> v.forEach { b.putInt((it * 10000).toInt()); b.putInt(10000) } }

    private fun add(tag: Int, type: Int, count: Int, write: (ByteBuffer) -> Unit): TestDng {
        val size = count * TiffEntry.typeSize(type)
        val b = ByteBuffer.allocate(size).order(order)
        write(b)
        entries += Entry(tag, type, count, b.array())
        return this
    }

    /** [pixels] are written as the single strip; StripOffsets/ByteCounts are filled in. */
    fun build(pixels: ShortArray): ByteArray {
        val pixelBytes = ByteBuffer.allocate(pixels.size * 2).order(order).apply { pixels.forEach { putShort(it) } }.array()
        val all = (entries + Entry(273, 4, 1, ByteArray(4)) + Entry(279, 4, 1, int(pixelBytes.size))).sortedBy { it.tag }
        val ifdSize = 2 + all.size * 12 + 4
        var dataOffset = 8 + ifdSize
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(8).order(order)
        header.put(if (order == ByteOrder.LITTLE_ENDIAN) "II".toByteArray() else "MM".toByteArray())
        header.putShort(42).putInt(8)
        out.write(header.array())

        val extra = ByteArrayOutputStream()
        val ifd = ByteBuffer.allocate(ifdSize).order(order).putShort(all.size.toShort())
        val stripOffset = dataOffset + all.filter { it.data.size > 4 }.sumOf { it.data.size + (it.data.size and 1) }
        for (e in all) {
            val data = if (e.tag == 273) int(stripOffset) else e.data
            ifd.putShort(e.tag.toShort()).putShort(e.type.toShort()).putInt(e.count)
            if (data.size <= 4) {
                ifd.put(data.copyOf(4))
            } else {
                ifd.putInt(dataOffset)
                extra.write(data)
                if (data.size and 1 == 1) extra.write(0)
                dataOffset += data.size + (data.size and 1)
            }
        }
        ifd.putInt(0)
        out.write(ifd.array())
        out.write(extra.toByteArray())
        out.write(pixelBytes)
        return out.toByteArray()
    }

    private fun int(v: Int) = ByteBuffer.allocate(4).order(order).putInt(v).array()
}
