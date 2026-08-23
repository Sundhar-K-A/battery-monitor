package com.example.chargetrack.domain.device

/** Shared test fixture helpers for BuildInfo fakes. */
internal object BuildInfoFakes {

    /** Canonical iQOO 15 BuildInfo — matches the device catalogue. */
    fun iqoo15(
        brand: String = "iQOO",
        model: String = "iQOO 15",
        manufacturer: String = "vivo",
        device: String = "iQOO15",
        product: String = "iQOO15",
        androidVersionRelease: String = "16",
        sdkInt: Int = 36,
        buildFingerprint: String = "vivo/iQOO15/iQOO15:16/B.001/20260101:user/release-keys",
        buildDisplay: String = "OriginOS 5.0.1_20260101",
        buildIncremental: String = "B.001",
    ) = BuildInfo(
        manufacturer = manufacturer,
        brand = brand,
        model = model,
        device = device,
        product = product,
        androidVersionRelease = androidVersionRelease,
        sdkInt = sdkInt,
        buildFingerprint = buildFingerprint,
        buildDisplay = buildDisplay,
        buildIncremental = buildIncremental
    )

    /** Generic non-iQOO device. */
    fun generic(
        brand: String = "samsung",
        model: String = "Galaxy S25",
        manufacturer: String = "samsung",
        device: String = "r7q",
        product: String = "dm3q",
        androidVersionRelease: String = "16",
        sdkInt: Int = 36,
        buildDisplay: String = "S925BXXS1AXE3",
        buildIncremental: String = "S925BXXS1AXE3",
    ) = BuildInfo(
        manufacturer = manufacturer,
        brand = brand,
        model = model,
        device = device,
        product = product,
        androidVersionRelease = androidVersionRelease,
        sdkInt = sdkInt,
        buildFingerprint = "samsung/dm3q/r7q:16/AP1A/S925BXXS1AXE3:user/release-keys",
        buildDisplay = buildDisplay,
        buildIncremental = buildIncremental
    )
}
