package com.ahnaf.cricketdrs

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class BallDetector(context: Context) {

    private var interpreter: Interpreter

    init {
        val model = loadModelFile(context, "best_float32.tflite")
        interpreter = Interpreter(model)
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detectBall(bitmap: Bitmap): DetectionResult? {
        // Resize to 640x640 (typical YOLO input)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val input = preprocessImage(resizedBitmap)

        // Example output shape: [1][NUM_BOXES][6] (x, y, w, h, conf, class)
        val output = Array(1) { Array(5) { FloatArray(8400) } }

        interpreter.run(input, output)

        return processOutput(output)
    }

    /**
     * Prepare input as a 4D float array [1][640][640][3]
     */
    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val input = Array(1) { Array(640) { Array(640) { FloatArray(3) } } }

        for (y in 0 until 640) {
            for (x in 0 until 640) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF) / 255.0f
                val g = (pixel shr 8 and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                input[0][y][x][0] = r
                input[0][y][x][1] = g
                input[0][y][x][2] = b
            }
        }

        return input
    }

    /**
     * Find the box with the highest confidence above a threshold.
     */
    private fun processOutput(output: Array<Array<FloatArray>>): DetectionResult? {
        val boxes = output[0]  // [5][8400]

        // DEBUG: Log max confidence across all boxes
        var globalMaxConf = 0.0f
        var bestIndex = -1

        for (i in 0 until 8400) {
            val conf = boxes[4][i]
            if (conf > globalMaxConf) {
                globalMaxConf = conf
                bestIndex = i
            }
        }

        Log.d("CricketDRS", "Max confidence across all boxes: $globalMaxConf")

        if (globalMaxConf < 0.3f) {  // Very low threshold for debugging
            Log.d("CricketDRS", "No detections above 0.3 - model may need retraining")
            return null
        }

        Log.d("CricketDRS", "Best box: index=$bestIndex, conf=$globalMaxConf")

        return DetectionResult(
            x = boxes[0][bestIndex],
            y = boxes[1][bestIndex],
            width = boxes[2][bestIndex],
            height = boxes[3][bestIndex],
            confidence = globalMaxConf
        )
    }


    fun close() {
        interpreter.close()
    }
}

data class DetectionResult(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float
)
