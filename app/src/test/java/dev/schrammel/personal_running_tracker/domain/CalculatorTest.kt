package dev.schrammel.personal_running_tracker.domain

import org.junit.Test
import org.junit.Assert.assertEquals

class CalculatorTest {
    @Test
    fun `meters to kilometers`() = assertEquals(1.0, Calculator.metersToKilometers(1000.0), 0.001)

    @Test
    fun `kilometers to meters`() = assertEquals(1000.0, Calculator.kilometersToMeters(1.0), 0.001)

    @Test
    fun `mps to kmh`() = assertEquals(36.0, Calculator.mpsToKmh(10.0), 0.01)

    @Test
    fun `mps to minutes per km`() = assertEquals(1.6667, Calculator.mpsToMinutesPerKilometer(10.0), 0.001)

    @Test
    fun `mps to minutes per mile`() = assertEquals(2.68224, Calculator.mpsToMinutesPerMile(10.0), 0.0001)
}