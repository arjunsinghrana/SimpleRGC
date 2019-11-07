package simplecolocalization.services.colocalizer

import ij.gui.Roi

/**
 * The representation of a positioned cell is a set of points on a
 * two-dimensional coordinate system belonging which form the cell.
 */
class PositionedCell(val points: Set<Pair<Int, Int>>) {

    private val center: Pair<Int, Int>

    init {
        //TODO: (Do we even need this, Measure does this anyway)
        var xSum = 0
        var ySum = 0
        points.forEach { point ->
            xSum += point.first
            ySum += point.second
        }
        center = Pair(xSum / points.size, ySum / points.size)
    }

    companion object {
        fun fromRoi(roi: Roi): PositionedCell {
            return PositionedCell(roi.containedPoints.map { point ->
                Pair(point.x, point.y)
            }.toSet())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PositionedCell

        if (points != other.points) return false

        return true
    }

    override fun hashCode(): Int {
        return points.hashCode()
    }
}
