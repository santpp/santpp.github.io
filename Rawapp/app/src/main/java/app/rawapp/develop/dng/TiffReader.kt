package app.rawapp.develop.dng

import java.nio.ByteBuffer
import java.nio.ByteOrder

class DngFormatException(message: String) : Exception(message)

/** One TIFF directory entry, with its values decoded as doubles. */
class TiffEntry(val tag: Int, val type: Int, val count: Int, private val valueOffset: Int, private val buffer: ByteBuffer) {

    fun doubles(): DoubleArray = DoubleArray(count) { i -> valueAt(i) }

    fun ints(): IntArray = IntArray(count) { i -> valueAt(i).toInt() }

    fun int(): Int = valueAt(0).toInt()

    fun bytes(): ByteArray = ByteArray(count * typeSize(type)) { i -> buffer.get(dataOffset() + i) }

    private fun dataOffset(): Int = if (count * typeSize(type) <= 4) valueOffset else buffer.getInt(valueOffset)

    private fun valueAt(i: Int): Double {
        val base = dataOffset() + i * typeSize(type)
        return when (type) {
            TYPE_BYTE, TYPE_UNDEFINED -> (buffer.get(base).toInt() and 0xFF).toDouble()
            TYPE_SHORT -> (buffer.getShort(base).toInt() and 0xFFFF).toDouble()
            TYPE_LONG -> (buffer.getInt(base).toLong() and 0xFFFFFFFFL).toDouble()
            TYPE_SLONG -> buffer.getInt(base).toDouble()
            TYPE_RATIONAL -> (buffer.getInt(base).toLong() and 0xFFFFFFFFL).toDouble() /
                (buffer.getInt(base + 4).toLong() and 0xFFFFFFFFL).toDouble()
            TYPE_SRATIONAL -> buffer.getInt(base).toDouble() / buffer.getInt(base + 4).toDouble()
            TYPE_FLOAT -> buffer.getFloat(base).toDouble()
            TYPE_DOUBLE -> buffer.getDouble(base)
            else -> throw DngFormatException("Tipo TIFF no soportado: $type (etiqueta $tag)")
        }
    }

    companion object {
        const val TYPE_BYTE = 1
        const val TYPE_SHORT = 3
        const val TYPE_LONG = 4
        const val TYPE_RATIONAL = 5
        const val TYPE_UNDEFINED = 7
        const val TYPE_SLONG = 9
        const val TYPE_SRATIONAL = 10
        const val TYPE_FLOAT = 11
        const val TYPE_DOUBLE = 12

        fun typeSize(type: Int): Int = when (type) {
            TYPE_BYTE, TYPE_UNDEFINED, 2, 6 -> 1
            TYPE_SHORT, 8 -> 2
            TYPE_LONG, TYPE_SLONG, TYPE_FLOAT -> 4
            TYPE_RATIONAL, TYPE_SRATIONAL, TYPE_DOUBLE -> 8
            else -> throw DngFormatException("Tipo TIFF desconocido: $type")
        }
    }
}

class TiffDirectory(val entries: Map<Int, TiffEntry>) {
    operator fun get(tag: Int): TiffEntry? = entries[tag]
    fun require(tag: Int): TiffEntry = entries[tag] ?: throw DngFormatException("Falta la etiqueta DNG $tag")
}

/** Minimal TIFF reader: byte order, IFD chain and SubIFDs. */
class TiffReader(bytes: ByteArray) {
    val buffer: ByteBuffer = ByteBuffer.wrap(bytes).also { buf ->
        if (bytes.size < 8) throw DngFormatException("Archivo demasiado corto")
        buf.order(
            when (String(bytes, 0, 2, Charsets.US_ASCII)) {
                "II" -> ByteOrder.LITTLE_ENDIAN
                "MM" -> ByteOrder.BIG_ENDIAN
                else -> throw DngFormatException("No es un archivo TIFF/DNG")
            },
        )
        if (buf.getShort(2).toInt() != 42) throw DngFormatException("Cabecera TIFF inválida")
    }

    /** All directories: the main chain plus any SubIFDs, in file order. */
    fun directories(): List<TiffDirectory> {
        val result = ArrayList<TiffDirectory>()
        var offset = buffer.getInt(4)
        val visited = HashSet<Int>()
        while (offset != 0 && visited.add(offset)) {
            val dir = readDirectory(offset)
            result += dir
            dir[TAG_SUB_IFDS]?.ints()?.forEach { result += readDirectory(it) }
            offset = buffer.getInt(offset + 2 + 12 * (buffer.getShort(offset).toInt() and 0xFFFF))
        }
        return result
    }

    private fun readDirectory(offset: Int): TiffDirectory {
        if (offset < 8 || offset + 2 > buffer.limit()) throw DngFormatException("IFD fuera del archivo")
        val count = buffer.getShort(offset).toInt() and 0xFFFF
        val entries = HashMap<Int, TiffEntry>(count)
        for (i in 0 until count) {
            val e = offset + 2 + i * 12
            val tag = buffer.getShort(e).toInt() and 0xFFFF
            entries[tag] = TiffEntry(tag, buffer.getShort(e + 2).toInt() and 0xFFFF, buffer.getInt(e + 4), e + 8, buffer)
        }
        return TiffDirectory(entries)
    }

    companion object {
        const val TAG_SUB_IFDS = 330
    }
}
