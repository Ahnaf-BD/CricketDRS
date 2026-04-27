package com.ahnaf.cricketdrs

import android.util.Log

/**
 * Kalman Filter for 2D ball tracking.
 * State vector: [x, y, vx, vy] - position + velocity in normalised screen coords.
 * Camera is at the non-striker's end looking down the pitch.
 * Ball travels top→bottom (y increases) then bounces bottom→top (y decreases).
 */
class BallTracker {

    private var state = FloatArray(4) { 0f }
    private var P = Array(4) { i -> FloatArray(4) { j -> if (i == j) 1f else 0f } }
    private val Q = Array(4) { i -> FloatArray(4) { j -> if (i == j) 0.001f else 0f } }
    private val R = Array(2) { i -> FloatArray(2) { j -> if (i == j) 0.01f else 0f } }
    private val dt = 1f

    private var isInitialised = false
    private var missedFrames = 0
    val maxMissedFrames = 10

    // Bounce detection
    var hasBounced = false
        private set
    private var prevVy = 0f

    fun update(detection: DetectionResult?): FloatArray? {
        if (detection == null) {
            missedFrames++
            if (!isInitialised || missedFrames > maxMissedFrames) return null
            return predict()
        }

        missedFrames = 0
        val mx = detection.x + detection.width  / 2f
        val my = detection.y + detection.height / 2f

        if (!isInitialised) {
            state[0] = mx
            state[1] = my
            state[2] = 0f
            state[3] = 0f
            isInitialised = true
            prevVy = 0f
            return floatArrayOf(mx, my)
        }

        val predicted = predict()

        val innovation = floatArrayOf(
            mx - predicted[0],
            my - predicted[1]
        )

        val S = Array(2) { i -> FloatArray(2) { j -> P[i][j] + R[i][j] } }

        val detS = S[0][0] * S[1][1] - S[0][1] * S[1][0]
        val sInv = Array(2) { FloatArray(2) }
        sInv[0][0] =  S[1][1] / detS
        sInv[0][1] = -S[0][1] / detS
        sInv[1][0] = -S[1][0] / detS
        sInv[1][1] =  S[0][0] / detS

        val K = Array(4) { i ->
            FloatArray(2) { j -> P[i][0] * sInv[0][j] + P[i][1] * sInv[1][j] }
        }

        for (i in 0..3) {
            state[i] += K[i][0] * innovation[0] + K[i][1] * innovation[1]
        }

        val oldP = Array(4) { i -> P[i].clone() }
        for (i in 0..3) {
            for (j in 0..3) {
                P[i][j] = oldP[i][j] - K[i][0] * oldP[0][j] - K[i][1] * oldP[1][j]
            }
        }

        // Bounce detection: vy was positive (ball falling, y increasing)
        // and is now negative (ball rising, y decreasing)
        if (!hasBounced && prevVy > 0.005f && state[3] < -0.005f) {
            hasBounced = true
            Log.d("CricketDRS", "Bounce detected at y=${state[1]}, x=${state[0]}")
        }
        prevVy = state[3]

        return floatArrayOf(state[0], state[1])
    }

    fun predict(): FloatArray {
        val F = arrayOf(
            floatArrayOf(1f, 0f, dt, 0f),
            floatArrayOf(0f, 1f, 0f, dt),
            floatArrayOf(0f, 0f, 1f,  0f),
            floatArrayOf(0f, 0f, 0f,  1f)
        )

        val newState = FloatArray(4)
        for (i in 0..3) for (j in 0..3) newState[i] += F[i][j] * state[j]
        state = newState

        val FP = Array(4) { FloatArray(4) }
        for (i in 0..3) for (j in 0..3) for (k in 0..3) FP[i][j] += F[i][k] * P[k][j]

        val FT = Array(4) { i -> FloatArray(4) { j -> F[j][i] } }
        val FPFt = Array(4) { FloatArray(4) }
        for (i in 0..3) {
            for (j in 0..3) {
                for (k in 0..3) FPFt[i][j] += FP[i][k] * FT[k][j]
                FPFt[i][j] += Q[i][j]
            }
        }
        P = FPFt

        return floatArrayOf(state[0], state[1])
    }

    /**
     * Extrapolate N frames into the future for trajectory arc drawing.
     * Gravity in screen space always acts downward (+y direction).
     * Before bounce: ball falling (vy > 0), gravity accelerates it downward.
     * After bounce:  ball rising (vy < 0), gravity decelerates it and pulls back down.
     * Both cases use the same positive gravity - the sign of vy handles the rest.
     */
    fun extrapolate(frames: Int): List<FloatArray> {
        val positions = mutableListOf<FloatArray>()
        var px = state[0]
        var py = state[1]
        val vx = state[2] * 1.5f
        val vy = state[3] * 2.0f
        val gravity = 0.0015f  // positive = downward in screen space

        for (i in 1..frames) {
            px += vx
            py += vy + (gravity * i)
            positions.add(floatArrayOf(px, py))
        }
        return positions
    }

    fun getVelocity() = floatArrayOf(state[2], state[3])

    fun reset() {
        state = FloatArray(4) { 0f }
        P = Array(4) { i -> FloatArray(4) { j -> if (i == j) 1f else 0f } }
        isInitialised = false
        missedFrames  = 0
        hasBounced    = false
        prevVy        = 0f
    }
}