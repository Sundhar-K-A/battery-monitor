package com.example.chargetrack.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var viewModel: SettingsViewModel

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

        viewModel = SettingsViewModel(database, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `01 - UI state reports session count from database`() = runTest {
        database.chargingSetupDao().insert(
            ChargingSetupEntity("setup-1", "iQOO", "100W", 100, "FlashCharge", true, "iQOO", "Stock", true, ChargingType.WIRED, ChargingMode.FLASH_CHARGE, null, now, false)
        )
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity("snap-1", now, "16", 36, "PD2505", "fingerprint", "1.0", 1)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity("s1", now.minusSeconds(100), now, 20, 80, "setup-1", "snap-1", TestType.STANDARD, null, SessionEndReason.USER_STOPPED)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity("s2", now.minusSeconds(200), now.minusSeconds(100), 20, 50, "setup-1", "snap-1", TestType.FREE_FORM, null, SessionEndReason.UNPLUGGED)
        )

        advanceTimeBy(100)
        val state = viewModel.uiState.first { it.sessionCount > 0 }

        assertEquals(2, state.sessionCount)
        assertEquals("1.0", state.appVersion)
        assertEquals(1, state.appBuildCode)
    }

    @Test
    fun `02 - confirmResetDatabase clears all database records and shows status message`() = runTest {
        database.chargingSetupDao().insert(
            ChargingSetupEntity("setup-1", "iQOO", "100W", 100, "FlashCharge", true, "iQOO", "Stock", true, ChargingType.WIRED, ChargingMode.FLASH_CHARGE, null, now, false)
        )
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity("snap-1", now, "16", 36, "PD2505", "fingerprint", "1.0", 1)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity("s1", now, now, 20, 80, "setup-1", "snap-1", TestType.STANDARD, null, SessionEndReason.USER_STOPPED)
        )

        advanceTimeBy(100)
        viewModel.openResetDialog()
        val openState = viewModel.uiState.first { it.isResetDialogOpen }
        assertTrue(openState.isResetDialogOpen)

        viewModel.confirmResetDatabase()
        advanceTimeBy(100)

        val state = viewModel.uiState.first { !it.isResetDialogOpen && it.sessionCount == 0 }
        assertFalse(state.isResetDialogOpen)
        assertEquals(0, state.sessionCount)
        assertEquals("All session and telemetry records cleared.", state.statusMessage)
    }
}
