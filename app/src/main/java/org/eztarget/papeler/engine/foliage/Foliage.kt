package org.eztarget.papeler.engine.foliage

import org.eztarget.papeler.old_engine.Being
import org.w3c.dom.Node
import kotlin.math.*
import kotlin.random.Random

class Foliage {

    var maxAge = 240
    var nodesAddedPerRoundLimit = 8
    var neighbourGravity: Double
    var preferredNeighbourDistance = 0.0
    var maxPushDistance: Double
    private lateinit var firstNode: FoliageNode

    constructor(worldSize: Double) {
        val nodeRadius = worldSize / 600.0
        neighbourGravity = nodeRadius * NODE_RADIUS_TO_GRAVITY_RATIO
        maxPushDistance = worldSize * WORLD_SIZE_TO_PUSH_DISTANCE_RATIO
    }

    fun initNodesInCircles(
            x: Double,
            y: Double,
            numOfInitialNodes: Int,
            worldSize: Double,
            maxJitter: Double,
            randomNumberGenerator: Random = Random.Default
    ) {
        val nodeDensity = randomNumberGenerator.nextInt(
                from = MIN_NODE_DENSITY,
                until = MAX_NODE_DENSITY
        )
        val numberOfCircles = randomNumberGenerator.nextInt(
                from = MIN_NUM_OF_INIT_CIRCLES,
                until = MAX_NUM_OF_INIT_CIRCLES
        )
        val numOfNodesPerCircle = numOfInitialNodes / numberOfCircles
        val minCircleRadius = worldSize * WORLD_SIZE_TO_MIN_CIRCLE_RADIUS_RATIO
        val maxCircleRadius = worldSize * WORLD_SIZE_TO_MAX_CIRCLE_RADIUS_RATIO

        var lastNode: FoliageNode? = null
        for (circleIndex in 0 until numberOfCircles) {
            val xJitter = jitter(maxJitter, randomNumberGenerator)
            val circleCenterX = x + xJitter //* 10.0
            val yJitter = jitter(maxJitter, randomNumberGenerator)
            val circleCenterY = y + yJitter //* 10.0
            val radius = randomNumberGenerator.nextDouble(
                    from = minCircleRadius,
                    until = maxCircleRadius
            )
            val squeezeFactor = randomNumberGenerator.nextDouble(
                    from = MIN_CIRCLE_SQUEEZE_FACTOR,
                    until = MAX_CIRCLE_SQUEEZE_FACTOR
            )

            for (relativeNodeIndex in 0 until numOfNodesPerCircle) {
                val currentNode = FoliageNode()

                val angleOfNode = TWO_PI
                    * ((relativeNodeIndex + 1).toDouble() / numOfNodesPerCircle.toDouble())

                val nodeX = (circleCenterX
                        + cos(angleOfNode).toFloat() * radius * squeezeFactor
                        + jitter(maxJitter, randomNumberGenerator))
                val nodeY = (circleCenterY
                        + sin(angleOfNode).toFloat() * radius
                        + jitter(maxJitter, randomNumberGenerator))

                if (lastNode == null) {
                    firstNode = currentNode
                } else {
                    lastNode.nextNode = currentNode

                    val absoluteNodeIndex = (circleIndex * numOfNodesPerCircle) + relativeNodeIndex
                    if (absoluteNodeIndex == numOfInitialNodes - 1) {
                        preferredNeighbourDistance = node.distance(lastNode)
                        currentNode.nextNode = firstNode
                        return
                    }
                }

                lastNode = currentNode

//                if (firstNode == null) {
//                    firstNode = node
//                    lastNode = node
//                } else if (nodeIndex  == numOfInitialNodes - 1) {
//                    preferredNeighbourDistance = node.distance(lastNode!!)
//                    lastNode!!.mNext = node
//                    node.mNext = mFirstNode
//                } else {
//                    lastNode!!.mNext = node
//                    lastNode = node
//                }

            }
        }
    }

    companion object {
        private val tag = Foliage::class.java.simpleName
        private const val TWO_PI = PI * 2.0
        private const val MIN_NODE_DENSITY = 1
        private const val MAX_NODE_DENSITY = 11
        private const val NODE_RADIUS_TO_GRAVITY_RATIO = 0.5
        private const val WORLD_SIZE_TO_PUSH_DISTANCE_RATIO = 0.3
        private const val WORLD_SIZE_TO_JITTER_RATIO = 0.001
        private const val MIN_NUM_OF_INIT_CIRCLES = 1
        private const val MAX_NUM_OF_INIT_CIRCLES = 6
        private const val WORLD_SIZE_TO_MIN_CIRCLE_RADIUS_RATIO = 0.01
        private const val WORLD_SIZE_TO_MAX_CIRCLE_RADIUS_RATIO = 0.06
        private const val MIN_CIRCLE_SQUEEZE_FACTOR = 0.66
        private const val MAX_CIRCLE_SQUEEZE_FACTOR = MIN_CIRCLE_SQUEEZE_FACTOR * 2.0

        private fun jitter(maxValue: Double, randomNumberGenerator: Random): Double {
            return randomNumberGenerator.nextDouble(from = -maxValue, until = maxValue)
        }
    }
}