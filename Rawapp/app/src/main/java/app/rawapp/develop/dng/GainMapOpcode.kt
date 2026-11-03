package app.rawapp.develop.dng

import app.rawapp.develop.CfaPattern
import app.rawapp.develop.GainMap
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val OPCODE_GAIN_MAP = 9

private class PhaseMap(val top: Int, val left: Int, val rows: Int, val columns: Int, val gains: FloatArray)

/**
 * Rebuilds the lens-shading [GainMap] from a DNG OpcodeList2. DngCreator
 * stores one GainMap opcode per Bayer phase; they are merged into
 * interleaved (R, G even row, G odd row, B) samples.
 */
fun gainMapFromOpcodeList(data: ByteArray, cfa: CfaPattern): GainMap? {
    val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
    val phases = ArrayList<PhaseMap>()
    repeat(buf.int) {
        val id = buf.int
        buf.int // DNG version
        buf.int // flags
        val size = buf.int
        val start = buf.position()
        if (id == OPCODE_GAIN_MAP) phases += readGainMap(buf)
        buf.position(start + size)
    }
    if (phases.size != 4) return null
    val rows = phases[0].rows
    val columns = phases[0].columns
    if (phases.any { it.rows != rows || it.columns != columns }) return null

    val gains = FloatArray(rows * columns * 4)
    for (phase in phases) {
        val channel = channelIndex(phase.left and 1, phase.top and 1, cfa)
        for (i in 0 until rows * columns) gains[i * 4 + channel] = phase.gains[i]
    }
    return GainMap(columns, rows, gains)
}

private fun readGainMap(buf: ByteBuffer): PhaseMap {
    val top = buf.int
    val left = buf.int
    repeat(2) { buf.int } // bottom, right
    repeat(4) { buf.int } // plane, planes, rowPitch, colPitch
    val rows = buf.int
    val columns = buf.int
    repeat(4) { buf.double } // spacing and origin
    val mapPlanes = buf.int
    val gains = FloatArray(rows * columns) { i ->
        val value = buf.float
        repeat(mapPlanes - 1) { buf.float }
        value
    }
    return PhaseMap(top, left, rows, columns, gains)
}

/** Index into (R, G even row, G odd row, B) for the photosite at parity (x, y). */
internal fun channelIndex(x: Int, y: Int, cfa: CfaPattern): Int {
    val dx = (x xor cfa.redX) and 1
    val dy = (y xor cfa.redY) and 1
    return when {
        dx == 0 && dy == 0 -> 0
        dx == 1 && dy == 1 -> 3
        y and 1 == 0 -> 1
        else -> 2
    }
}
