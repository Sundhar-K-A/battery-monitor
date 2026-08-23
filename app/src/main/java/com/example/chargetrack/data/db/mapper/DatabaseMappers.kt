package com.example.chargetrack.data.db.mapper

import com.example.chargetrack.data.db.entity.BatterySampleEntity
import com.example.chargetrack.data.db.entity.ChargeTransitionEntity
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.DeviceProfile
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest

// ── DeviceProfile ─────────────────────────────────────────────────────────────
fun DeviceProfile.toEntity(isOnboardingComplete: Boolean = false): DeviceProfileEntity =
    DeviceProfileEntity(
        id = id,
        manufacturer = manufacturer,
        brand = brand,
        model = model,
        device = device,
        product = product,
        androidVersion = androidVersion,
        sdkInt = sdkInt,
        buildFingerprint = buildFingerprint ?: "",
        buildDisplay = "",
        buildIncremental = "",
        originOsBuildLabel = originOsBuildLabel,
        typicalCapacityMah = typicalCapacityMah,
        ratedCapacityMah = ratedCapacityMah,
        typicalEnergyWh = typicalEnergyWh,
        ratedEnergyWh = ratedEnergyWh,
        wiredReferenceW = wiredReferenceW,
        wirelessReferenceW = wirelessReferenceW,
        nickname = nickname,
        purchaseDate = purchaseDate,
        firstUseDate = firstUseDate,
        ramStorageVariant = ramStorageVariant,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isOnboardingComplete = isOnboardingComplete,
    )

fun DeviceProfileEntity.toDomain(): DeviceProfile =
    DeviceProfile(
        id = id,
        manufacturer = manufacturer,
        brand = brand,
        model = model,
        device = device,
        product = product,
        androidVersion = androidVersion,
        sdkInt = sdkInt,
        originOsBuildLabel = originOsBuildLabel,
        buildFingerprint = buildFingerprint.ifEmpty { null },
        typicalCapacityMah = typicalCapacityMah,
        ratedCapacityMah = ratedCapacityMah,
        typicalEnergyWh = typicalEnergyWh,
        ratedEnergyWh = ratedEnergyWh,
        wiredReferenceW = wiredReferenceW,
        wirelessReferenceW = wirelessReferenceW,
        nickname = nickname,
        purchaseDate = purchaseDate,
        firstUseDate = firstUseDate,
        ramStorageVariant = ramStorageVariant,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// ── SoftwareSnapshot ──────────────────────────────────────────────────────────
fun SoftwareSnapshot.toEntity(): SoftwareSnapshotEntity =
    SoftwareSnapshotEntity(
        id = id,
        capturedAt = capturedAt,
        androidVersion = androidVersion,
        sdkInt = sdkInt,
        originOsVersion = originOsVersion,
        buildFingerprint = buildFingerprint,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
    )

fun SoftwareSnapshotEntity.toDomain(): SoftwareSnapshot =
    SoftwareSnapshot(
        id = id,
        capturedAt = capturedAt,
        androidVersion = androidVersion,
        sdkInt = sdkInt,
        originOsVersion = originOsVersion,
        buildFingerprint = buildFingerprint,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
    )

// ── ChargingSetup ─────────────────────────────────────────────────────────────
fun ChargingSetup.toEntity(isTemplate: Boolean = false): ChargingSetupEntity =
    ChargingSetupEntity(
        id = id,
        chargerBrand = chargerBrand,
        chargerModel = chargerModel,
        advertisedWattageW = advertisedWattageW,
        protocol = protocol,
        isOfficialCharger = isOfficialCharger,
        cableBrand = cableBrand,
        cableModel = cableModel,
        isOfficialCable = isOfficialCable,
        chargingType = chargingType,
        chargingMode = chargingMode,
        notes = notes,
        createdAt = createdAt,
        isTemplate = isTemplate,
    )

fun ChargingSetupEntity.toDomain(): ChargingSetup =
    ChargingSetup(
        id = id,
        chargerBrand = chargerBrand,
        chargerModel = chargerModel,
        advertisedWattageW = advertisedWattageW,
        protocol = protocol,
        isOfficialCharger = isOfficialCharger,
        cableBrand = cableBrand,
        cableModel = cableModel,
        isOfficialCable = isOfficialCable,
        chargingType = chargingType,
        chargingMode = chargingMode,
        notes = notes,
        createdAt = createdAt,
        isFrozen = true, // Any setup retrieved from database attached to a session is immutable
    )

// ── ChargingSession ───────────────────────────────────────────────────────────
fun ChargingSession.toEntity(): ChargingSessionEntity =
    ChargingSessionEntity(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        startPercent = startPercent,
        endPercent = endPercent,
        chargingSetupId = chargingSetupId,
        softwareSnapshotId = softwareSnapshotId,
        testType = testType,
        userNotes = userNotes,
        endReason = endReason,
    )

fun ChargingSessionEntity.toDomain(): ChargingSession =
    ChargingSession(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        startPercent = startPercent,
        endPercent = endPercent,
        chargingSetupId = chargingSetupId,
        softwareSnapshotId = softwareSnapshotId,
        testType = testType,
        userNotes = userNotes,
        endReason = endReason,
    )

// ── BatterySample ─────────────────────────────────────────────────────────────
fun BatterySample.toEntity(): BatterySampleEntity =
    BatterySampleEntity(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        elapsedMs = elapsedMs,
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa,
        currentAverageUa = currentAverageUa,
        chargeCounterUah = chargeCounterUah,
        energyCounterNwh = energyCounterNwh,
        temperatureDeciC = temperatureDeciC,
        batteryStatus = batteryStatus,
        pluggedType = pluggedType,
        cycleCount = cycleCount,
        derivedPowerUw = derivedPowerUw,
        qualityFlags = qualityFlags,
    )

fun BatterySampleEntity.toDomain(): BatterySample =
    BatterySample(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        elapsedMs = elapsedMs,
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa,
        currentAverageUa = currentAverageUa,
        chargeCounterUah = chargeCounterUah,
        energyCounterNwh = energyCounterNwh,
        temperatureDeciC = temperatureDeciC,
        batteryStatus = batteryStatus,
        pluggedType = pluggedType,
        cycleCount = cycleCount,
        derivedPowerUw = derivedPowerUw,
        qualityFlags = qualityFlags,
    )

// ── ChargeTransition ──────────────────────────────────────────────────────────
fun ChargeTransition.toEntity(): ChargeTransitionEntity =
    ChargeTransitionEntity(
        id = id,
        sessionId = sessionId,
        fromPercent = fromPercent,
        toPercent = toPercent,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        averagePowerUw = averagePowerUw,
        medianPowerUw = medianPowerUw,
        peakPowerUw = peakPowerUw,
        averageTemperatureDeciC = averageTemperatureDeciC,
        maxTemperatureDeciC = maxTemperatureDeciC,
        sampleCount = sampleCount,
        quality = quality,
    )

fun ChargeTransitionEntity.toDomain(): ChargeTransition =
    ChargeTransition(
        id = id,
        sessionId = sessionId,
        fromPercent = fromPercent,
        toPercent = toPercent,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        averagePowerUw = averagePowerUw,
        medianPowerUw = medianPowerUw,
        peakPowerUw = peakPowerUw,
        averageTemperatureDeciC = averageTemperatureDeciC,
        maxTemperatureDeciC = maxTemperatureDeciC,
        sampleCount = sampleCount,
        quality = quality,
    )

// ── StandardTest ──────────────────────────────────────────────────────────────
fun StandardTest.toEntity(): StandardTestEntity =
    StandardTestEntity(
        id = id,
        sessionId = sessionId,
        targetStartPercent = targetStartPercent,
        targetEndPercent = targetEndPercent,
        isBaseline = isBaseline,
        baselineSetAt = baselineSetAt,
        comparisonGroupKey = comparisonGroupKey,
        validity = validity,
        invalidationReason = invalidationReason,
        benchmarkStartedElapsedMs = benchmarkStartedElapsedMs,
        benchmarkEndedElapsedMs = benchmarkEndedElapsedMs,
    )

fun StandardTestEntity.toDomain(): StandardTest =
    StandardTest(
        id = id,
        sessionId = sessionId,
        targetStartPercent = targetStartPercent,
        targetEndPercent = targetEndPercent,
        isBaseline = isBaseline,
        baselineSetAt = baselineSetAt,
        comparisonGroupKey = comparisonGroupKey,
        validity = validity,
        invalidationReason = invalidationReason,
        benchmarkStartedElapsedMs = benchmarkStartedElapsedMs,
        benchmarkEndedElapsedMs = benchmarkEndedElapsedMs,
    )
