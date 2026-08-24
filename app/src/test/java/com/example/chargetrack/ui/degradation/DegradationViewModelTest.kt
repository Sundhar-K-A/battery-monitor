package com.example.chargetrack.ui.degradation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.degradation.LongitudinalRepository
import com.example.chargetrack.data.health.BatteryHealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DegradationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var healthRepository: BatteryHealthRepository
    private lateinit var longitudinalRepository: LongitudinalRepository
    private lateinit var viewModel: DegradationViewModel

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

        healthRepository = BatteryHealthRepository(database)
        longitudinalRepository = LongitudinalRepository(database, healthRepository)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `01 - empty database emits Empty state`() = testScope.runTest {
        viewModel = DegradationViewModel(longitudinalRepository)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("Empty DB should emit Empty state", state is DegradationUiState.Empty)
    }
}
