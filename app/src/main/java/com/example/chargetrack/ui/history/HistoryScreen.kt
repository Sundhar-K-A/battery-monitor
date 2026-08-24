package com.example.chargetrack.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chargetrack.domain.history.HistorySortOption
import com.example.chargetrack.ui.history.components.HistoryFilterChips
import com.example.chargetrack.ui.history.components.HistorySessionCard

private val ScreenBackground = Color(0xFF000000)
private val AmberAccent = Color(0xFFFFB300)
private val SubtitleColor = Color(0xFF8C9BAE)
private val RedWarning = Color(0xFFEF5350)
private val DialogBackground = Color(0xFF161B24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSortMenuOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.importSessionFromStream(inputStream)
            }
        }
    }

    LaunchedEffect(uiState.importStatusMessage) {
        uiState.importStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearImportStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Charging History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!uiState.isLoading) {
                            Text(
                                text = "${uiState.totalCount} session${if (uiState.totalCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SubtitleColor,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Import session JSON", tint = Color.White)
                    }
                    IconButton(onClick = { isSortMenuOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort sessions")
                    }
                    DropdownMenu(
                        expanded = isSortMenuOpen,
                        onDismissRequest = { isSortMenuOpen = false },
                    ) {
                        HistorySortOption.entries.forEach { sortOpt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        sortOpt.label,
                                        fontWeight = if (uiState.filter.sortBy == sortOpt) FontWeight.Bold else FontWeight.Normal,
                                        color = if (uiState.filter.sortBy == sortOpt) AmberAccent else Color.White,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(sortOpt)
                                    isSortMenuOpen = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        containerColor = ScreenBackground,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Bar
            HistoryFilterChips(
                filter = uiState.filter,
                availableSetups = uiState.availableSetups,
                isFiltered = uiState.isFiltered,
                onToggleCanonical2080 = { viewModel.toggleCanonical2080() },
                onToggleStandardTestOnly = { viewModel.toggleStandardTestOnly() },
                onSelectDateOption = { viewModel.setDateOption(it) },
                onSelectChargingType = { viewModel.setChargingType(it) },
                onSelectSetupId = { viewModel.setChargingSetup(it) },
                onResetFilters = { viewModel.resetFilters() },
            )

            Spacer(Modifier.height(8.dp))

            // Content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AmberAccent)
                }
            } else if (uiState.sessions.isEmpty()) {
                EmptyHistoryView(
                    isFiltered = uiState.isFiltered,
                    onResetFilters = { viewModel.resetFilters() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.sessions, key = { it.sessionId }) { item ->
                        HistorySessionCard(
                            item = item,
                            onClick = { onNavigateToSummary(item.sessionId) },
                            onDeleteClick = { viewModel.requestDeleteSession(item.sessionId) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (uiState.isDeleteConfirmDialogOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteDialog() },
                title = {
                    Text("Delete Charging Session?", fontWeight = FontWeight.Bold, color = Color.White)
                },
                text = {
                    Text(
                        "This will permanently delete this session record and its raw telemetry samples from the database. Reusable charging setups will not be affected.",
                        color = SubtitleColor,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDeleteSession() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedWarning,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.dismissDeleteDialog() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = DialogBackground,
            )
        }

        // Duplicate Session Resolution Dialog
        uiState.pendingDuplicatePayload?.let { payload ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissDuplicateDialog() },
                title = {
                    Text("Duplicate Session Detected", fontWeight = FontWeight.Bold, color = Color.White)
                },
                text = {
                    Column {
                        Text(
                            "A session with ID '${payload.session.id.take(8)}...' already exists in your local history.",
                            color = SubtitleColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "How would you like to handle this imported session?",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { viewModel.resolveDuplicateImport(com.example.chargetrack.domain.export.DuplicateStrategy.ASSIGN_NEW_ID) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
                        ) {
                            Text("Import as Copy (New ID)", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.resolveDuplicateImport(com.example.chargetrack.domain.export.DuplicateStrategy.OVERWRITE) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RedWarning, contentColor = Color.White),
                        ) {
                            Text("Overwrite Existing Record", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.dismissDuplicateDialog() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) {
                            Text("Cancel / Skip")
                        }
                    }
                },
                containerColor = DialogBackground,
            )
        }
    }
}

@Composable
private fun EmptyHistoryView(
    isFiltered: Boolean,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.HistoryToggleOff,
            contentDescription = null,
            tint = SubtitleColor.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isFiltered) "No matching charging sessions" else "No charging sessions yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isFiltered) "Try adjusting or resetting your active filters" else "Complete a charging session to view it in history",
            style = MaterialTheme.typography.bodyMedium,
            color = SubtitleColor,
            textAlign = TextAlign.Center,
        )
        if (isFiltered) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onResetFilters,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberAccent,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Reset Filters", fontWeight = FontWeight.Bold)
            }
        }
    }
}
