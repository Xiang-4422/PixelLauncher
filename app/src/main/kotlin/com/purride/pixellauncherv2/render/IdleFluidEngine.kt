package com.purride.pixellauncherv2.render

import de.pirckheimer_gymnasium.jbox2d.collision.shapes.ChainShape
import de.pirckheimer_gymnasium.jbox2d.collision.shapes.PolygonShape
import de.pirckheimer_gymnasium.jbox2d.common.Vec2
import de.pirckheimer_gymnasium.jbox2d.dynamics.BodyDef
import de.pirckheimer_gymnasium.jbox2d.dynamics.World
import de.pirckheimer_gymnasium.jbox2d.particle.ParticleDef
import de.pirckheimer_gymnasium.jbox2d.particle.ParticleGroupDef
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

data class IdleFluidState(
    val width: Int = 0,
    val height: Int = 0,
    val targetLitCount: Int = 0,
    val coverageField: FloatArray = FloatArray(0),
    val litMask: BooleanArray = BooleanArray(0),
    val disturbanceX: Float = 0f,
    val disturbanceY: Float = 0f,
    val disturbanceUntilUptimeMs: Long = 0L,
    val lastUpdateUptimeMs: Long = 0L,
)

class IdleFluidEngine(
    private val tuning: IdleFluidTuning = IdleFluidTuning.default(),
    seed: Int = 0x51A9E,
) {

    private val random = Random(seed)
    private var simulation: FluidSimulation? = null
    private var coverageBufferA: FloatArray = FloatArray(0)
    private var coverageBufferB: FloatArray = FloatArray(0)
    private var maskBufferA: BooleanArray = BooleanArray(0)
    private var maskBufferB: BooleanArray = BooleanArray(0)
    private var useCoverageBufferA = false
    private var useMaskBufferA = false
    private var coverageBufferResizeCount = 0
    private var maskBufferResizeCount = 0
    private var coverageKernelRadiusCells = -1
    private var coverageKernelSide = 0
    private var coverageKernelWeights: FloatArray = FloatArray(0)

    fun syncToBattery(
        state: IdleFluidState,
        batteryLevel: Int,
        simulationWidth: Int,
        simulationHeight: Int,
        gravityX: Float,
        gravityY: Float,
        nowUptimeMs: Long,
    ): IdleFluidState {
        val width = simulationWidth.coerceAtLeast(1)
        val height = simulationHeight.coerceAtLeast(1)
        val targetLitCount = targetLitCount(batteryLevel, width, height)
        val targetVisibleCellCount = targetVisibleCellCount(batteryLevel, width, height)
        val simulation = ensureSimulation(width, height)
        val world = simulation.world
        val simulationWidth = simulation.width
        val simulationHeight = simulation.height
        val gravityDirection = resolveGravityDirection(gravityX, gravityY)
        val shouldReinitialize = state.width != width ||
            state.height != height ||
            world.getParticleCount() == 0

        when {
            targetLitCount <= 0 -> clearParticles(world)
            shouldReinitialize -> initializeFluidBlock(
                world = world,
                width = simulationWidth,
                height = simulationHeight,
                targetCount = targetLitCount,
                gravityDirection = gravityDirection,
            )

            else -> reconcileParticleCount(
                world = world,
                width = simulationWidth,
                height = simulationHeight,
                targetCount = targetLitCount,
                gravityDirection = gravityDirection,
            )
        }

        val coverageField = RenderPerfLogger.measure("idle.physics.buildCoverageField") {
            buildCoverageField(
                world = world,
                width = width,
                height = height,
            )
        }
        val litMask = RenderPerfLogger.measure("idle.physics.buildLitMask") {
            buildLitMask(
                coverageField = coverageField,
                previousMask = state.litMask,
                previousCoverageField = state.coverageField,
                targetVisibleCellCount = targetVisibleCellCount,
            )
        }

        return state.copy(
            width = width,
            height = height,
            targetLitCount = targetLitCount,
            coverageField = coverageField,
            litMask = litMask,
            lastUpdateUptimeMs = if (state.lastUpdateUptimeMs == 0L) nowUptimeMs else state.lastUpdateUptimeMs,
        )
    }

    fun applyDisturbance(
        state: IdleFluidState,
        accelX: Float,
        accelY: Float,
        nowUptimeMs: Long,
    ): IdleFluidState {
        val magnitude = sqrt((accelX * accelX) + (accelY * accelY))
        if (magnitude < tuning.minDisturbanceAccel) {
            return state
        }
        val normalizedX = accelX / magnitude
        val normalizedY = accelY / magnitude
        val disturbanceRange = (tuning.maxUsefulLinearAccel - tuning.minDisturbanceAccel).coerceAtLeast(1e-4f)
        val scaledMagnitude = ((magnitude - tuning.minDisturbanceAccel) / disturbanceRange)
            .coerceIn(0f, 1f) * tuning.maxDisturbanceAcceleration

        return state.copy(
            disturbanceX = normalizedX * scaledMagnitude,
            disturbanceY = normalizedY * scaledMagnitude,
            disturbanceUntilUptimeMs = nowUptimeMs + tuning.disturbanceDurationMs,
        )
    }

    fun step(
        state: IdleFluidState,
        simulationWidth: Int,
        simulationHeight: Int,
        gravityX: Float,
        gravityY: Float,
        deltaSeconds: Float,
        nowUptimeMs: Long,
    ): IdleFluidState {
        val width = simulationWidth.coerceAtLeast(1)
        val height = simulationHeight.coerceAtLeast(1)
        if (width <= 0 || height <= 0) {
            return state.copy(lastUpdateUptimeMs = nowUptimeMs)
        }

        val simulation = ensureSimulation(width, height)
        val world = simulation.world
        val simulationWidth = simulation.width
        val simulationHeight = simulation.height
        val gravityDirection = resolveGravityDirection(gravityX, gravityY)
        val targetLitCount = state.targetLitCount.coerceAtLeast(0)
        val targetVisibleCellCount = (targetLitCount / 2).coerceIn(0, width * height)

        if (world.getParticleCount() != targetLitCount) {
            reconcileParticleCount(
                world = world,
                width = simulationWidth,
                height = simulationHeight,
                targetCount = targetLitCount,
                gravityDirection = gravityDirection,
            )
        }

        val disturbance = currentDisturbance(state, nowUptimeMs)
        val gravityScale = resolveGravityScale(height)
        val scaledGravityX = gravityX * gravityScale
        val scaledGravityY = gravityY * gravityScale
        val effectiveGravityX = scaledGravityX + disturbance.x
        val effectiveGravityY = scaledGravityY + disturbance.y
        world.setGravity(Vec2(effectiveGravityX, effectiveGravityY))
        world.setParticleDamping(
            dynamicDamping(
                gravityMagnitude = sqrt((scaledGravityX * scaledGravityX) + (scaledGravityY * scaledGravityY)),
                disturbanceMagnitude = sqrt((disturbance.x * disturbance.x) + (disturbance.y * disturbance.y)),
            ),
        )
        RenderPerfLogger.measure("idle.physics.worldStep") {
            world.step(
                deltaSeconds.coerceIn(tuning.minStepSeconds, tuning.maxStepSeconds),
                tuning.worldVelocityIterations,
                tuning.worldPositionIterations,
            )
        }

        val coverageField = RenderPerfLogger.measure("idle.physics.buildCoverageField") {
            buildCoverageField(
                world = world,
                width = width,
                height = height,
            )
        }
        val litMask = RenderPerfLogger.measure("idle.physics.buildLitMask") {
            buildLitMask(
                coverageField = coverageField,
                previousMask = state.litMask,
                previousCoverageField = state.coverageField,
                targetVisibleCellCount = targetVisibleCellCount,
            )
        }
        val disturbanceExpired = nowUptimeMs >= state.disturbanceUntilUptimeMs

        return state.copy(
            width = width,
            height = height,
            targetLitCount = targetLitCount,
            coverageField = coverageField,
            litMask = litMask,
            disturbanceX = if (disturbanceExpired) 0f else state.disturbanceX,
            disturbanceY = if (disturbanceExpired) 0f else state.disturbanceY,
            disturbanceUntilUptimeMs = if (disturbanceExpired) 0L else state.disturbanceUntilUptimeMs,
            lastUpdateUptimeMs = nowUptimeMs,
        )
    }

    fun targetLitCount(
        batteryLevel: Int,
        simulationWidth: Int,
        simulationHeight: Int,
    ): Int {
        val width = simulationWidth.coerceAtLeast(1)
        val height = simulationHeight.coerceAtLeast(1)
        val totalCells = width * height
        val level = batteryLevel.coerceIn(0, 100)
        return ((totalCells * level) / 100).coerceIn(0, totalCells)
    }

    fun targetVisibleCellCount(
        batteryLevel: Int,
        simulationWidth: Int,
        simulationHeight: Int,
    ): Int {
        val width = simulationWidth.coerceAtLeast(1)
        val height = simulationHeight.coerceAtLeast(1)
        val totalCells = width * height
        val level = batteryLevel.coerceIn(0, 100)
        return ((totalCells * level) / 200).coerceIn(0, totalCells)
    }

    internal fun resolveGravityScaleForHeight(logicalHeight: Int): Float {
        return resolveGravityScale(logicalHeight.coerceAtLeast(1))
    }

    internal fun dynamicDampingForTesting(gravityMagnitude: Float, disturbanceMagnitude: Float): Float {
        return dynamicDamping(gravityMagnitude, disturbanceMagnitude)
    }

    internal fun buildLitMaskForTesting(
        coverageField: FloatArray,
        previousMask: BooleanArray,
        previousCoverageField: FloatArray,
        targetVisibleCellCount: Int,
    ): BooleanArray {
        return buildLitMask(
            coverageField = coverageField,
            previousMask = previousMask,
            previousCoverageField = previousCoverageField,
            targetVisibleCellCount = targetVisibleCellCount,
        )
    }

    internal fun coverageBufferResizeCountForTesting(): Int = coverageBufferResizeCount

    internal fun maskBufferResizeCountForTesting(): Int = maskBufferResizeCount

    private fun ensureSimulation(width: Int, height: Int): FluidSimulation {
        val safeWidth = width.coerceAtLeast(tuning.minSimulationSize)
        val safeHeight = height.coerceAtLeast(tuning.minSimulationSize)
        val existing = simulation
        if (existing != null && existing.width == safeWidth && existing.height == safeHeight) {
            return existing
        }
        val created = createSimulation(safeWidth, safeHeight)
        simulation = created
        return created
    }

    private fun createSimulation(width: Int, height: Int): FluidSimulation {
        val world = World(Vec2(0f, tuning.defaultGravityY))
        world.setParticleRadius(tuning.particleRadius)
        world.setParticleDensity(tuning.particleDensity)
        world.setParticleGravityScale(1f)
        world.setParticleDamping(tuning.baseParticleDamping)
        world.setParticleMaxCount((width * height).coerceAtLeast(1))
        createContainerBounds(
            world = world,
            width = width,
            height = height,
        )
        return FluidSimulation(
            world = world,
            width = width,
            height = height,
        )
    }

    private fun createContainerBounds(
        world: World,
        width: Int,
        height: Int,
    ) {
        val body = world.createBody(BodyDef())
        val bounds = ChainShape()
        val minX = -0.5f
        val minY = -0.5f
        val maxX = width.toFloat() - 0.5f
        val maxY = height.toFloat() - 0.5f
        bounds.createLoop(
            arrayOf(
                Vec2(minX, minY),
                Vec2(maxX, minY),
                Vec2(maxX, maxY),
                Vec2(minX, maxY),
            ),
            4,
        )
        body.createFixture(bounds, 0f)
    }

    private fun resolveGravityDirection(gravityX: Float, gravityY: Float): Vec2 {
        val magnitude = sqrt((gravityX * gravityX) + (gravityY * gravityY))
        if (magnitude < tuning.minGravityMagnitude) {
            return Vec2(0f, 1f)
        }
        return Vec2(gravityX / magnitude, gravityY / magnitude)
    }

    private fun resolveGravityScale(logicalHeight: Int): Float {
        val safeHeight = logicalHeight.coerceAtLeast(1)
        val baseline = tuning.gravityScaleReferenceHeight.coerceAtLeast(1f)
        return (safeHeight.toFloat() / baseline).coerceIn(tuning.minGravityScale, tuning.maxGravityScale)
    }

    private fun currentDisturbance(state: IdleFluidState, nowUptimeMs: Long): Vec2 {
        if (state.disturbanceUntilUptimeMs <= nowUptimeMs) {
            return Vec2()
        }
        val duration = tuning.disturbanceDurationMs.coerceAtLeast(1L)
        val remainingRatio = ((state.disturbanceUntilUptimeMs - nowUptimeMs).toFloat() / duration.toFloat())
            .coerceIn(0f, 1f)
        return Vec2(
            state.disturbanceX * remainingRatio,
            state.disturbanceY * remainingRatio,
        )
    }

    private fun initializeFluidBlock(
        world: World,
        width: Int,
        height: Int,
        targetCount: Int,
        gravityDirection: Vec2,
    ) {
        clearParticles(world)
        if (targetCount <= 0) {
            return
        }

        val bounds = innerBounds(width, height)
        val fillFraction = (targetCount.toFloat() / (width * height).toFloat()).coerceIn(0f, 1f)
        val polygon = PolygonShape()
        val centerX: Float
        val centerY: Float
        val halfWidth: Float
        val halfHeight: Float

        if (abs(gravityDirection.y) >= abs(gravityDirection.x)) {
            val availableHeight = bounds.maxY - bounds.minY
            val slabHeight = resolveFluidThickness(
                desiredThickness = availableHeight * fillFraction,
                availableThickness = availableHeight,
            )
            centerX = (bounds.minX + bounds.maxX) * 0.5f
            centerY = if (gravityDirection.y >= 0f) {
                bounds.maxY - (slabHeight * 0.5f)
            } else {
                bounds.minY + (slabHeight * 0.5f)
            }
            halfWidth = resolveHalfExtent(bounds.maxX - bounds.minX)
            halfHeight = resolveHalfExtent(slabHeight)
        } else {
            val availableWidth = bounds.maxX - bounds.minX
            val slabWidth = resolveFluidThickness(
                desiredThickness = availableWidth * fillFraction,
                availableThickness = availableWidth,
            )
            centerX = if (gravityDirection.x >= 0f) {
                bounds.maxX - (slabWidth * 0.5f)
            } else {
                bounds.minX + (slabWidth * 0.5f)
            }
            centerY = (bounds.minY + bounds.maxY) * 0.5f
            halfWidth = resolveHalfExtent(slabWidth)
            halfHeight = resolveHalfExtent(bounds.maxY - bounds.minY)
        }

        polygon.setAsBox(
            halfWidth,
            halfHeight,
            Vec2(centerX, centerY),
            0f,
        )
        val groupDef = ParticleGroupDef().apply {
            shape = polygon
            linearVelocity.set(0f, 0f)
            angularVelocity = 0f
        }
        world.createParticleGroup(groupDef)
        reconcileParticleCount(
            world = world,
            width = width,
            height = height,
            targetCount = targetCount,
            gravityDirection = gravityDirection,
        )
    }

    private fun clearParticles(world: World) {
        while (world.getParticleCount() > 0) {
            world.destroyParticle(world.getParticleCount() - 1, false)
        }
    }

    private fun reconcileParticleCount(
        world: World,
        width: Int,
        height: Int,
        targetCount: Int,
        gravityDirection: Vec2,
    ) {
        val safeTarget = targetCount.coerceIn(0, width * height)
        val current = world.getParticleCount().coerceAtLeast(0)
        when {
            safeTarget <= 0 -> clearParticles(world)
            current <= 0 -> initializeFluidBlock(
                world = world,
                width = width,
                height = height,
                targetCount = safeTarget,
                gravityDirection = gravityDirection,
            )

            current < safeTarget -> addParticlesNearFreeSurface(
                world = world,
                width = width,
                height = height,
                addCount = safeTarget - current,
                gravityDirection = gravityDirection,
            )

            current > safeTarget -> removeParticlesNearFreeSurface(
                world = world,
                removeCount = current - safeTarget,
                gravityDirection = gravityDirection,
            )
        }
    }

    private fun addParticlesNearFreeSurface(
        world: World,
        width: Int,
        height: Int,
        addCount: Int,
        gravityDirection: Vec2,
    ) {
        if (addCount <= 0) {
            return
        }
        val surfaceSamples = collectFreeSurfaceSamples(world, gravityDirection)
        if (surfaceSamples.isEmpty()) {
            initializeFluidBlock(
                world = world,
                width = width,
                height = height,
                targetCount = world.getParticleCount() + addCount,
                gravityDirection = gravityDirection,
            )
            return
        }

        val bounds = innerBounds(width, height)
        val particleDef = ParticleDef()
        val orthogonalX = -gravityDirection.y
        val orthogonalY = gravityDirection.x
        repeat(addCount) {
            val sample = surfaceSamples[random.nextInt(surfaceSamples.size)]
            val lateralOffset = (random.nextFloat() - 0.5f) * 2f * tuning.surfaceLateralJitter
            val outwardOffset = tuning.surfaceSpawnOffset + (random.nextFloat() * tuning.surfaceSpawnJitter)
            val spawnX = clamp(
                sample.x - (gravityDirection.x * outwardOffset) + (orthogonalX * lateralOffset),
                bounds.minX,
                bounds.maxX,
            )
            val spawnY = clamp(
                sample.y - (gravityDirection.y * outwardOffset) + (orthogonalY * lateralOffset),
                bounds.minY,
                bounds.maxY,
            )
            particleDef.position.set(spawnX, spawnY)
            particleDef.velocity.set(0f, 0f)
            world.createParticle(particleDef)
        }
    }

    private fun removeParticlesNearFreeSurface(
        world: World,
        removeCount: Int,
        gravityDirection: Vec2,
    ) {
        if (removeCount <= 0) {
            return
        }
        val surfaceSamples = collectFreeSurfaceSamples(world, gravityDirection)
        if (surfaceSamples.isEmpty()) {
            clearParticles(world)
            return
        }

        surfaceSamples
            .sortedBy { it.projection }
            .take(removeCount)
            .map { it.index }
            .sortedDescending()
            .forEach { index ->
                world.destroyParticle(index, false)
            }
    }

    private fun collectFreeSurfaceSamples(
        world: World,
        gravityDirection: Vec2,
    ): List<ParticleSample> {
        val positions = world.getParticlePositionBuffer()
        val count = world.getParticleCount().coerceAtMost(positions.size)
        if (count <= 0) {
            return emptyList()
        }

        val samples = ArrayList<ParticleSample>(count)
        var minProjection = Float.POSITIVE_INFINITY
        for (index in 0 until count) {
            val position = positions[index] ?: continue
            val projection = (position.x * gravityDirection.x) + (position.y * gravityDirection.y)
            minProjection = minProjection.coerceAtMost(projection)
            samples.add(
                ParticleSample(
                    index = index,
                    x = position.x,
                    y = position.y,
                    projection = projection,
                ),
            )
        }

        val threshold = minProjection + tuning.freeSurfaceBandThickness
        return samples.filter { it.projection <= threshold }
    }

    private fun buildCoverageField(
        world: World,
        width: Int,
        height: Int,
    ): FloatArray {
        val size = width * height
        ensureCoverageBufferCapacity(size)
        ensureCoverageKernel()
        useCoverageBufferA = !useCoverageBufferA
        val coverageField = if (useCoverageBufferA) coverageBufferA else coverageBufferB
        coverageField.fill(0f)
        val positions = world.getParticlePositionBuffer()
        val count = world.getParticleCount().coerceAtMost(positions.size)
        for (index in 0 until count) {
            val position = positions[index] ?: continue
            splatCoverage(
                coverageField = coverageField,
                width = width,
                height = height,
                centerX = position.x,
                centerY = position.y,
            )
        }
        return coverageField
    }

    private fun splatCoverage(
        coverageField: FloatArray,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
    ) {
        val centerCellX = centerX.roundToInt()
        val centerCellY = centerY.roundToInt()
        val radius = coverageKernelRadiusCells
        val side = coverageKernelSide
        for (kernelY in 0 until side) {
            val y = centerCellY + kernelY - radius
            if (y !in 0 until height) {
                continue
            }
            for (kernelX in 0 until side) {
                val x = centerCellX + kernelX - radius
                if (x !in 0 until width) {
                    continue
                }
                val weight = coverageKernelWeights[(kernelY * side) + kernelX]
                if (weight <= 0f) {
                    continue
                }
                coverageField[(y * width) + x] += weight
            }
        }
    }

    private fun buildLitMask(
        coverageField: FloatArray,
        previousMask: BooleanArray,
        previousCoverageField: FloatArray,
        targetVisibleCellCount: Int,
    ): BooleanArray {
        ensureMaskBufferCapacity(coverageField.size)
        useMaskBufferA = !useMaskBufferA
        val mask = if (useMaskBufferA) maskBufferA else maskBufferB
        val stablePreviousMask = if (previousMask.size == coverageField.size) {
            previousMask
        } else {
            BooleanArray(coverageField.size)
        }
        val stablePreviousCoverage = if (previousCoverageField.size == coverageField.size) {
            previousCoverageField
        } else {
            FloatArray(coverageField.size)
        }
        val targetCount = targetVisibleCellCount.coerceIn(0, coverageField.size)
        if (targetCount <= 0) {
            mask.fill(false)
            return mask
        }
        if (targetCount >= coverageField.size) {
            mask.fill(true)
            return mask
        }
        val retentionBias = tuning.trendDeltaThreshold * 4f
        val order = Array(coverageField.size) { index -> index }
        order.sortWith { left, right ->
            val leftScore = coverageField[left] + if (stablePreviousMask[left]) retentionBias else 0f
            val rightScore = coverageField[right] + if (stablePreviousMask[right]) retentionBias else 0f
            when {
                leftScore > rightScore -> -1
                leftScore < rightScore -> 1
                stablePreviousCoverage[left] > stablePreviousCoverage[right] -> -1
                stablePreviousCoverage[left] < stablePreviousCoverage[right] -> 1
                else -> left.compareTo(right)
            }
        }
        mask.fill(false)
        for (index in 0 until targetCount) {
            mask[order[index]] = true
        }
        return mask
    }

    private fun ensureCoverageBufferCapacity(size: Int) {
        if (coverageBufferA.size == size && coverageBufferB.size == size) {
            return
        }
        coverageBufferA = FloatArray(size)
        coverageBufferB = FloatArray(size)
        useCoverageBufferA = false
        coverageBufferResizeCount += 1
    }

    private fun ensureMaskBufferCapacity(size: Int) {
        if (maskBufferA.size == size && maskBufferB.size == size) {
            return
        }
        maskBufferA = BooleanArray(size)
        maskBufferB = BooleanArray(size)
        useMaskBufferA = false
        maskBufferResizeCount += 1
    }

    private fun ensureCoverageKernel() {
        val radiusCells = ceil(tuning.coverageRadiusPx).toInt().coerceAtLeast(0)
        if (coverageKernelRadiusCells == radiusCells && coverageKernelWeights.isNotEmpty()) {
            return
        }
        val side = (radiusCells * 2) + 1
        val radius = tuning.coverageRadiusPx
        val radiusSquared = radius * radius
        val weights = FloatArray(side * side)
        for (kernelY in 0 until side) {
            val dy = (kernelY - radiusCells).toFloat()
            for (kernelX in 0 until side) {
                val dx = (kernelX - radiusCells).toFloat()
                val distanceSquared = (dx * dx) + (dy * dy)
                if (distanceSquared > radiusSquared) {
                    continue
                }
                val normalized = 1f - (sqrt(distanceSquared) / radius.coerceAtLeast(1e-4f))
                weights[(kernelY * side) + kernelX] = normalized * normalized
            }
        }
        coverageKernelRadiusCells = radiusCells
        coverageKernelSide = side
        coverageKernelWeights = weights
    }

    private fun innerBounds(width: Int, height: Int): ContainerBounds {
        return ContainerBounds(
            minX = -0.5f + tuning.particleRadius,
            maxX = width.toFloat() - 0.5f - tuning.particleRadius,
            minY = -0.5f + tuning.particleRadius,
            maxY = height.toFloat() - 0.5f - tuning.particleRadius,
        )
    }

    private fun resolveFluidThickness(
        desiredThickness: Float,
        availableThickness: Float,
    ): Float {
        if (availableThickness <= 0f) {
            return tuning.minHalfExtent * 2f
        }
        if (availableThickness <= tuning.minFluidThickness) {
            return availableThickness
        }
        return desiredThickness.coerceIn(tuning.minFluidThickness, availableThickness)
    }

    private fun resolveHalfExtent(totalExtent: Float): Float {
        val availableHalfExtent = (totalExtent * 0.5f).coerceAtLeast(0f)
        if (availableHalfExtent <= tuning.minHalfExtent) {
            return availableHalfExtent
        }
        return availableHalfExtent.coerceAtLeast(tuning.minHalfExtent)
    }

    private fun dynamicDamping(
        gravityMagnitude: Float,
        disturbanceMagnitude: Float,
    ): Float {
        val gravityFactor = (gravityMagnitude / tuning.earthGravity).coerceIn(0f, 1f)
        val disturbanceCap = tuning.maxDisturbanceAcceleration.coerceAtLeast(1e-4f)
        val disturbanceFactor = (disturbanceMagnitude / disturbanceCap).coerceIn(0f, 1f)
        return (tuning.baseParticleDamping +
            (gravityFactor * tuning.gravityContribution) +
            (disturbanceFactor * tuning.disturbanceContribution))
            .coerceIn(tuning.minParticleDamping, tuning.maxParticleDamping)
    }

    private fun clamp(value: Float, minValue: Float, maxValue: Float): Float {
        return value.coerceIn(minValue, maxValue)
    }

    private data class FluidSimulation(
        val world: World,
        val width: Int,
        val height: Int,
    )

    private data class ParticleSample(
        val index: Int,
        val x: Float,
        val y: Float,
        val projection: Float,
    )

    private data class ContainerBounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
    )

    companion object {
        const val disturbanceDurationMs: Long = IdleFluidTuning.defaultDisturbanceDurationMs
    }
}
