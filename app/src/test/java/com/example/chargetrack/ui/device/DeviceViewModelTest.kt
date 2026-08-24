package com.example.chargetrack.ui.device

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.device.DeviceProfileRepository
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: DeviceProfileRepository
    private lateinit var viewModel: DeviceViewModel

    private val now = Instant.now()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        repository = DeviceProfileRepository(database)
        viewModel = DeviceViewModel(repository, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `01 - UI state loads device profile and reference specs from Room`() = runTest {
        database.deviceProfileDao().insertOrUpdate(
            DeviceProfileEntity(
                id = "profile-1",
                manufacturer = "vivo",
                brand = "iQOO",
                model = "iQOO 15",
                device = "I2501",
                product = "PD2505",
                androidVersion = "16",
                sdkInt = 36,
                buildFingerprint = "vivo/PD2505/iQOO15",
                buildDisplay = "PD2505_16.0.1",
                buildIncremental = "12345",
                originOsBuildLabel = "PD2505",
                typicalCapacityMah = 7000,
                ratedCapacityMah = 6830,
                typicalEnergyWh = 26.25,
                ratedEnergyWh = 25.62,
                wiredReferenceW = 100,
                wirelessReferenceW = 40,
                nickname = "Test Device",
                createdAt = now,
                updatedAt = now,
            )
        )

        advanceTimeBy(100)
        val state = viewModel.uiState.first { it.profile != null }

        assertNotNull(state.profile)
        assertEquals("iQOO 15", state.profile?.model)
        assertEquals(7000, state.profile?.typicalCapacityMah)
        assertEquals(100, state.profile?.wiredReferenceW)
        assertEquals("Test Device", state.editNickname)
    }

    @Test
    fun `02 - saveUserMetadata persists updated nickname and notes`() = runTest {
        database.deviceProfileDao().insertOrUpdate(
            DeviceProfileEntity(
                id = "profile-1",
                manufacturer = "vivo",
                brand = "iQOO",
                model = "iQOO 15",
                device = "I2501",
                product = "PD2505",
                androidVersion = "16",
                sdkInt = 36,
                buildFingerprint = "vivo/PD2505/iQOO15",
                buildDisplay = "PD2505_16.0.1",
                buildIncremental = "12345",
                originOsBuildLabel = "PD2505",
                typicalCapacityMah = 7000,
                ratedCapacityMah = 6830,
                typicalEnergyWh = 26.25,
                ratedEnergyWh = 25.62,
                wiredReferenceW = 100,
                wirelessReferenceW = 40,
                nickname = "Old Nickname",
                notes = "Old Notes",
                createdAt = now,
                updatedAt = now,
            )
        )

        advanceTimeBy(100)
        viewModel.onNicknameChange("My Primary iQOO 15")
        viewModel.onRamStorageChange("16GB + 512GB")
        viewModel.onNotesChange("Updated launch unit notes")
        viewModel.saveUserMetadata()
        advanceTimeBy(100)

        val updated = database.deviceProfileDao().getProfile()
        assertNotNull(updated)
        assertEquals("My Primary iQOO 15", updated?.nickname)
        assertEquals("16GB + 512GB", updated?.ramStorageVariant)
        assertEquals("Updated launch unit notes", updated?.notes)
    }

    @Test
    fun `03 - UI state exposes list of saved charging setups`() = runTest {
        database.chargingSetupDao().insert(
            ChargingSetupEntity(
                id = "setup-1",
                chargerBrand = "iQOO",
                chargerModel = "100W FlashCharge Brick",
                advertisedWattageW = 100,
                protocol = "FlashCharge",
                isOfficialCharger = true,
                cableBrand = "iQOO",
                cableModel = "Stock 6A",
                isOfficialCable = true,
                chargingType = ChargingType.WIRED,
                chargingMode = ChargingMode.FLASH_CHARGE,
                notes = null,
                createdAt = now,
                isTemplate = true,
            )
        )

        advanceTimeBy(100)
        val state = viewModel.uiState.first { it.savedSetups.isNotEmpty() }

        assertEquals(1, state.savedSetups.size)
        assertEquals("100W FlashCharge Brick", state.savedSetups.first().chargerModel)
        assertEquals(100, state.savedSetups.first().advertisedWattageW)
    }
}
