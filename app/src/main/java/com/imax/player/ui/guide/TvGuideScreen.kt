package com.imax.player.ui.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.imax.player.R
import com.imax.player.core.catchup.CatchupUrlResolver
import com.imax.player.core.designsystem.theme.ImaxColors
import com.imax.player.core.model.Channel
import com.imax.player.core.model.Playlist
import com.imax.player.core.model.PlaylistType
import com.imax.player.data.parser.EpgProgram
import com.imax.player.data.repository.ContentRepository
import com.imax.player.data.repository.EpgRepository
import com.imax.player.data.repository.PlaylistRepository
import com.imax.player.ui.components.ImaxDrawer
import com.imax.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GUIDE_WINDOW_MS = 4L * 60 * 60 * 1000
private const val GUIDE_SHIFT_MS = 2L * 60 * 60 * 1000
private const val GUIDE_DAY_MS = 24L * 60 * 60 * 1000
private const val GUIDE_SLOT_MS = 30L * 60 * 1000

data class GuideRow(
    val channel: Channel,
    val programs: List<EpgProgram>
)

data class GuidePlaybackRequest(
    val url: String,
    val title: String,
    val channelId: Long,
    val group: String
)

enum class GuideStatus {
    PROGRAM_NOT_STARTED,
    CATCHUP_UNAVAILABLE,
    NO_EPG_MATCH,
    NO_CHANNELS
}

data class TvGuideState(
    val playlist: Playlist? = null,
    val rows: List<GuideRow> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val windowStart: Long = guideWindowStart(System.currentTimeMillis()),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val status: GuideStatus? = null,
    val playbackRequest: GuidePlaybackRequest? = null
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository,
    private val epgRepository: EpgRepository,
    private val catchupUrlResolver: CatchupUrlResolver
) : ViewModel() {
    private val _state = MutableStateFlow(TvGuideState())
    val state: StateFlow<TvGuideState> = _state.asStateFlow()
    private var allChannels: List<Channel> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist == null) {
                    allChannels = emptyList()
                    _state.value = TvGuideState(isLoading = false)
                    return@collectLatest
                }

                val channels = contentRepository.getChannels(playlist.id).first()
                val groups = contentRepository.getChannelGroups(playlist.id).first()
                allChannels = channels
                _state.update {
                    it.copy(
                        playlist = playlist,
                        groups = groups,
                        selectedGroup = it.selectedGroup?.takeIf(groups::contains),
                        isLoading = true
                    )
                }
                loadGuide(syncWhenEmpty = true)
            }
        }
    }

    fun selectGroup(group: String?) {
        _state.update { it.copy(selectedGroup = group) }
        loadGuide(syncWhenEmpty = false)
    }

    fun shiftWindow(deltaMs: Long) {
        _state.update { current -> current.copy(windowStart = current.windowStart + deltaMs) }
        loadGuide(syncWhenEmpty = false)
    }

    fun goToNow() {
        _state.update { it.copy(windowStart = guideWindowStart(System.currentTimeMillis())) }
        loadGuide(syncWhenEmpty = false)
    }

    fun refresh() {
        loadGuide(syncWhenEmpty = true, forceSync = true)
    }

    fun playChannel(channel: Channel) {
        _state.update {
            it.copy(
                playbackRequest = GuidePlaybackRequest(
                    url = channel.streamUrl,
                    title = channel.name,
                    channelId = channel.id,
                    group = channel.groupTitle
                ),
                status = null
            )
        }
    }

    fun playProgram(channel: Channel, program: EpgProgram) {
        val now = System.currentTimeMillis()
        if (program.startTime > now) {
            _state.update { it.copy(status = GuideStatus.PROGRAM_NOT_STARTED) }
            return
        }
        if (program.endTime > now) {
            playChannel(channel)
            return
        }

        val playlist = _state.value.playlist ?: return
        val durationMinutes = ((program.endTime - program.startTime + 59_999L) / 60_000L)
            .coerceAtLeast(1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val catchupUrl = catchupUrlResolver.resolve(
            channel = channel,
            startTimeMs = program.startTime,
            durationMins = durationMinutes,
            serverUrl = playlist.serverUrl.takeIf { playlist.type == PlaylistType.XTREAM_CODES }.orEmpty(),
            username = playlist.username.takeIf { playlist.type == PlaylistType.XTREAM_CODES }.orEmpty(),
            password = playlist.password.takeIf { playlist.type == PlaylistType.XTREAM_CODES }.orEmpty()
        )
        if (catchupUrl == null) {
            _state.update { it.copy(status = GuideStatus.CATCHUP_UNAVAILABLE) }
            return
        }

        _state.update {
            it.copy(
                playbackRequest = GuidePlaybackRequest(
                    url = catchupUrl,
                    title = "${channel.name} • ${program.title}",
                    channelId = channel.id,
                    group = channel.groupTitle
                ),
                status = null
            )
        }
    }

    fun consumePlaybackRequest() {
        _state.update { it.copy(playbackRequest = null) }
    }

    private fun loadGuide(syncWhenEmpty: Boolean, forceSync: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val snapshot = _state.value
            val playlist = snapshot.playlist ?: return@launch
            val channels = snapshot.selectedGroup
                ?.let { group -> allChannels.filter { it.groupTitle == group } }
                ?: allChannels
            _state.update { it.copy(isLoading = true, isSyncing = forceSync, status = null) }

            var programs = withContext(Dispatchers.IO) {
                epgRepository.getGuideProgramsForChannels(
                    channels = channels,
                    windowStart = snapshot.windowStart,
                    windowEnd = snapshot.windowStart + GUIDE_WINDOW_MS
                )
            }
            if (forceSync || (syncWhenEmpty && programs.values.all(List<EpgProgram>::isEmpty))) {
                playlistRepository.ensureEpgSynced(playlist, forceRefresh = forceSync)
                programs = withContext(Dispatchers.IO) {
                    epgRepository.getGuideProgramsForChannels(
                        channels = channels,
                        windowStart = snapshot.windowStart,
                        windowEnd = snapshot.windowStart + GUIDE_WINDOW_MS
                    )
                }
            }

            _state.update {
                it.copy(
                    rows = channels.map { channel -> GuideRow(channel, programs[channel.id].orEmpty()) },
                    isLoading = false,
                    isSyncing = false,
                    status = when {
                        channels.isEmpty() -> GuideStatus.NO_CHANNELS
                        programs.values.all(List<EpgProgram>::isEmpty) -> GuideStatus.NO_EPG_MATCH
                        else -> null
                    }
                )
            }
        }
    }
}

@Composable
fun TvGuideScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String) -> Unit,
    viewModel: TvGuideViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var drawerExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.playbackRequest) {
        state.playbackRequest?.let { request ->
            viewModel.consumePlaybackRequest()
            onPlayChannel(request.url, request.title, request.channelId, request.group)
        }
    }

    val content: @Composable () -> Unit = {
        GuideContent(
            state = state,
            isTv = isTv,
            onSelectGroup = viewModel::selectGroup,
            onShiftWindow = viewModel::shiftWindow,
            onNow = viewModel::goToNow,
            onRefresh = viewModel::refresh,
            onChannelClick = viewModel::playChannel,
            onProgramClick = viewModel::playProgram
        )
    }

    if (isTv) {
        ImaxDrawer(
            isExpanded = drawerExpanded,
            selectedRoute = Routes.TV_GUIDE,
            isTv = true,
            onToggle = { drawerExpanded = !drawerExpanded },
            onNavigate = onNavigate
        ) { content() }
    } else {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideContent(
    state: TvGuideState,
    isTv: Boolean,
    onSelectGroup: (String?) -> Unit,
    onShiftWindow: (Long) -> Unit,
    onNow: () -> Unit,
    onRefresh: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    onProgramClick: (Channel, EpgProgram) -> Unit
) {
    val horizontalScroll = rememberScrollState()
    val channelWidth = if (isTv) 210.dp else 124.dp
    val rowHeight = if (isTv) 82.dp else 70.dp
    val minuteWidth = if (isTv) 4.5.dp else 3.dp
    val timelineWidth = minuteWidth * (GUIDE_WINDOW_MS / 60_000L).toFloat()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val zone = remember { ZoneId.systemDefault() }
    val windowEnd = state.windowStart + GUIDE_WINDOW_MS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImaxColors.Background)
            .padding(horizontal = if (isTv) 24.dp else 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tv_guide),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ImaxColors.TextPrimary
                )
                Text(
                    text = dateFormatter.format(Instant.ofEpochMilli(state.windowStart).atZone(zone)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImaxColors.TextSecondary
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = !state.isSyncing) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }

        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            item {
                OutlinedButton(onClick = { onShiftWindow(-GUIDE_DAY_MS) }) {
                    Text(stringResource(R.string.guide_previous_day))
                }
            }
            item {
                OutlinedButton(onClick = { onShiftWindow(-GUIDE_SHIFT_MS) }) {
                    Text(stringResource(R.string.guide_previous_two_hours))
                }
            }
            item { Button(onClick = onNow) { Text(stringResource(R.string.guide_now)) } }
            item {
                OutlinedButton(onClick = { onShiftWindow(GUIDE_SHIFT_MS) }) {
                    Text(stringResource(R.string.guide_next_two_hours))
                }
            }
            item {
                OutlinedButton(onClick = { onShiftWindow(GUIDE_DAY_MS) }) {
                    Text(stringResource(R.string.guide_next_day))
                }
            }
        }

        LazyRow(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedGroup == null,
                    onClick = { onSelectGroup(null) },
                    label = { Text(stringResource(R.string.category_all)) }
                )
            }
            items(state.groups) { group ->
                FilterChip(
                    selected = state.selectedGroup == group,
                    onClick = { onSelectGroup(group) },
                    label = { Text(group, maxLines = 1) }
                )
            }
        }

        state.status?.let { status ->
            val message = when (status) {
                GuideStatus.PROGRAM_NOT_STARTED -> stringResource(R.string.guide_program_not_started)
                GuideStatus.CATCHUP_UNAVAILABLE -> stringResource(R.string.guide_catchup_unavailable)
                GuideStatus.NO_EPG_MATCH -> stringResource(R.string.guide_no_epg_match)
                GuideStatus.NO_CHANNELS -> stringResource(R.string.guide_no_channels)
            }
            Text(
                text = message,
                color = ImaxColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (state.isLoading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ImaxColors.Primary)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stickyHeader {
                Row(modifier = Modifier.background(ImaxColors.Background)) {
                    Box(
                        modifier = Modifier.width(channelWidth).height(44.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(stringResource(R.string.channels), color = ImaxColors.TextSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .horizontalScroll(horizontalScroll)
                    ) {
                        Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
                            guideTicks(state.windowStart, windowEnd).forEach { tick ->
                                val x = minuteWidth * ((tick - state.windowStart) / 60_000f)
                                Text(
                                    text = timeFormatter.format(Instant.ofEpochMilli(tick).atZone(zone)),
                                    color = ImaxColors.TextSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.offset(x = x).padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            items(state.rows, key = { row -> row.channel.id }) { row ->
                Row(modifier = Modifier.height(rowHeight)) {
                    GuideChannelCell(
                        channel = row.channel,
                        width = channelWidth,
                        onClick = { onChannelClick(row.channel) }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ImaxColors.Surface)
                            .horizontalScroll(horizontalScroll)
                    ) {
                        Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
                            row.programs.forEach { program ->
                                val layout = guideProgramLayout(program, state.windowStart, windowEnd)
                                    ?: return@forEach
                                GuideProgramCell(
                                    program = program,
                                    catchupAvailable = row.channel.catchupSource.isNotBlank() ||
                                        (state.playlist?.type == PlaylistType.XTREAM_CODES && row.channel.streamId > 0),
                                    modifier = Modifier
                                        .offset(x = minuteWidth * layout.offsetMinutes)
                                        .width((minuteWidth * layout.durationMinutes).coerceAtLeast(1.dp))
                                        .fillMaxHeight()
                                        .padding(2.dp),
                                    onClick = { onProgramClick(row.channel, program) }
                                )
                            }
                            val now = System.currentTimeMillis()
                            if (now in state.windowStart until windowEnd) {
                                val x = minuteWidth * ((now - state.windowStart) / 60_000f)
                                Box(
                                    modifier = Modifier
                                        .offset(x = x)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(ImaxColors.Primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChannelCell(channel: Channel, width: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) ImaxColors.FocusBorder else ImaxColors.CardBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        color = if (focused) ImaxColors.SurfaceElevated else ImaxColors.CardBackground,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LiveTv,
                contentDescription = null,
                tint = ImaxColors.Primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                channel.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = ImaxColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun GuideProgramCell(
    program: EpgProgram,
    catchupAvailable: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isCurrent = program.startTime <= now && program.endTime > now
    val isPast = program.endTime <= now
    var focused by remember { mutableStateOf(false) }
    val color = when {
        focused -> ImaxColors.SurfaceElevated
        isCurrent -> ImaxColors.Primary.copy(alpha = 0.28f)
        isPast -> ImaxColors.SurfaceVariant.copy(alpha = 0.55f)
        else -> ImaxColors.SurfaceVariant
    }

    Surface(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) ImaxColors.FocusBorder else ImaxColors.CardBorder,
                RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick),
        color = color,
        shape = RoundedCornerShape(7.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = program.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = ImaxColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
            if (isPast && catchupAvailable) {
                Text(
                    text = stringResource(R.string.guide_catchup),
                    color = ImaxColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

data class GuideProgramLayout(val offsetMinutes: Float, val durationMinutes: Float)

internal fun guideWindowStart(nowMs: Long): Long =
    (nowMs / GUIDE_SLOT_MS) * GUIDE_SLOT_MS - GUIDE_SLOT_MS

internal fun guideProgramLayout(
    program: EpgProgram,
    windowStart: Long,
    windowEnd: Long
): GuideProgramLayout? {
    val clippedStart = maxOf(program.startTime, windowStart)
    val clippedEnd = minOf(program.endTime, windowEnd)
    if (clippedEnd <= clippedStart) return null
    return GuideProgramLayout(
        offsetMinutes = (clippedStart - windowStart) / 60_000f,
        durationMinutes = (clippedEnd - clippedStart) / 60_000f
    )
}

private fun guideTicks(windowStart: Long, windowEnd: Long): List<Long> = buildList {
    var tick = ((windowStart + GUIDE_SLOT_MS - 1) / GUIDE_SLOT_MS) * GUIDE_SLOT_MS
    while (tick < windowEnd) {
        add(tick)
        tick += GUIDE_SLOT_MS
    }
}
