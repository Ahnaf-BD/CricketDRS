package com.ahnaf.cricketdrs

enum class LBWDecision {
    HIT, UMPIRES_CALL, MISS, INSUFFICIENT_DATA
}

/**
 * Stump region in normalised screen coordinates.
 * Camera angle: non-striker's end, looking down pitch.
 * Stumps appear in the centre-middle of the frame.
 *
 * x      = normalised centre x of the three stumps
 * y      = normalised top of the stump rectangle
 * width  = normalised total width across all three stumps
 * height = normalised stump height in frame
 */
data class StumpRegion(
    val x: Float      = 0.47f,   // centre x
    val y: Float      = 0.33f,   // top of stumps
    val width: Float  = 0.08f,
    val height: Float = 0.10f
)

data class PredictionResult(
    val decision:   LBWDecision,
    val confidence: Float,
    val impactX:    Float?,  // normalised x where trajectory crossed stump plane
    val impactY:    Float?   // normalised y (will equal stump centre y)
)

class StumpPredictor(
    private val stumps: StumpRegion = StumpRegion()
) {
    // Umpire's Call margin around stump edges (normalised)
    private val callMargin = 0.02f

    /**
     * Predict LBW outcome from a trajectory list of [x, y] normalised positions.
     *
     * The ball travels from bottom of screen (high y) toward the stumps (low y)
     * after bouncing. We find where the trajectory crosses the stump y-plane
     * and check whether the x-position at that crossing is within stump width.
     */
    fun predict(trajectory: List<FloatArray>): PredictionResult {
        if (trajectory.size < 5)
            return PredictionResult(LBWDecision.INSUFFICIENT_DATA, 0f, null, null)

        val stumpCentreY = stumps.y + stumps.height / 2f
        val stumpLeft    = stumps.x - stumps.width  / 2f
        val stumpRight   = stumps.x + stumps.width  / 2f

        // Find where rising trajectory crosses the stump y-plane.
        // Ball is rising after bounce: p1[1] > p2[1] (y decreasing frame to frame).
        // We want the first crossing where p1[1] >= stumpCentreY > p2[1].
        var impactX: Float? = null

        for (i in 0 until trajectory.size - 1) {
            val p1 = trajectory[i]
            val p2 = trajectory[i + 1]

            // Ball rising: y decreasing. Crossing when p1 is below and p2 is above.
            if (p1[1] >= stumpCentreY && p2[1] < stumpCentreY) {
                val t = (stumpCentreY - p1[1]) / (p2[1] - p1[1])
                impactX = p1[0] + t * (p2[0] - p1[0])
                break
            }
        }

        // Also handle full toss: ball falling (y increasing) and crosses stump plane
        // downward. p1[1] <= stumpCentreY and p2[1] > stumpCentreY.
        if (impactX == null) {
            for (i in 0 until trajectory.size - 1) {
                val p1 = trajectory[i]
                val p2 = trajectory[i + 1]

                if (p1[1] <= stumpCentreY && p2[1] > stumpCentreY) {
                    val t = (stumpCentreY - p1[1]) / (p2[1] - p1[1])
                    impactX = p1[0] + t * (p2[0] - p1[0])
                    break
                }
            }
        }

        if (impactX == null)
            return PredictionResult(LBWDecision.INSUFFICIENT_DATA, 0f, null, null)

        return when {
            // Direct hit - within stump width
            impactX in stumpLeft..stumpRight -> {
                // Confidence higher when impact is closer to stump centre
                val distFromCentre = kotlin.math.abs(impactX - stumps.x) / (stumps.width / 2f)
                val confidence = (1f - distFromCentre).coerceIn(0.5f, 1f)
                PredictionResult(LBWDecision.HIT, confidence, impactX, stumpCentreY)
            }

            // Umpire's Call - just outside stump edges
            impactX in (stumpLeft - callMargin)..(stumpRight + callMargin) ->
                PredictionResult(LBWDecision.UMPIRES_CALL, 0.5f, impactX, stumpCentreY)

            // Clear miss
            else ->
                PredictionResult(LBWDecision.MISS, 0.85f, impactX, stumpCentreY)
        }
    }
}