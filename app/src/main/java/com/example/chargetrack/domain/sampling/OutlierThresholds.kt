package com.example.chargetrack.domain.sampling

/**
 * Physical bounds used to evaluate whether a raw battery measurement is an anomaly.
 *
 * Exceeding these bounds flags the sample with [com.example.chargetrack.domain.enums.QualityFlag.OUTLIER],
 * but the raw value is NEVER discarded or altered.
 */
data class OutlierThresholds(
    /** Minimum reasonable cell voltage in millivolts (default: 2,500 mV = 2.5V). */
    val minVoltageMv: Int = 2500,
    /** Maximum reasonable cell voltage in millivolts (default: 5,000 mV = 5.0V). */
    val maxVoltageMv: Int = 5000,
    /** Maximum reasonable charging current in microamperes (default: 30,000,000 µA = 30A for dual-cell/FlashCharge). */
    val maxCurrentNowUa: Int = 30_000_000,
    /** Minimum reasonable temperature in tenths of a degree Celsius (default: -200 = -20°C). */
    val minTemperatureDeciC: Int = -200,
    /** Maximum reasonable temperature in tenths of a degree Celsius (default: 800 = 80°C). */
    val maxTemperatureDeciC: Int = 800,
)
