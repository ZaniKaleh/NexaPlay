package com.nexaplay

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : AppCompatActivity() {
    private data class Track(
        val uri: String,
        val title: String,
        val displayName: String,
        val extension: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val isVideo: Boolean
    )

    private lateinit var tabs: TabLayout
    private lateinit var playerView: PlayerView
    private lateinit var nowPlayingText: TextView
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var playButton: MaterialButton
    private lateinit var repeatButton: MaterialButton
    private lateinit var adapter: TrackAdapter

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var currentTabIsVideo = true
    private var currentRepeatMode = 0
    private var currentMediaIsVideo = false

    private var videos: List<Track> = emptyList()
    private var audios: List<Track> = emptyList()

    private val scanExecutor = Executors.newSingleThreadExecutor()
    private lateinit var audioExtensions: Set<String>
    private lateinit var videoExtensions: Set<String>
    private lateinit var pythonEngine: com.chaquo.python.PyObject

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshLibrary()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackControls()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNowPlaying()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackControls()
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(
                this@MainActivity,
                "Media tidak dapat diputar. Codec file mungkin tidak didukung perangkat.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializePython()
        buildInterface()
        connectPlaybackService()
        requestPermissionsIfNeeded()
    }

    private fun initializePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        pythonEngine = Python.getInstance().getModule("nexaplay_engine")
        audioExtensions = pythonEngine.callAttr("supported_extensions", "audio")
            .asList().map { it.toString() }.toSet()
        videoExtensions = pythonEngine.callAttr("supported_extensions", "video")
            .asList().map { it.toString() }.toSet()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(8))
            setBackgroundColor(Color.rgb(11, 16, 32))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "NexaPlay"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val refreshButton = makeButton("Pindai")
        refreshButton.setOnClickListener { requestPermissionsIfNeeded() }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(refreshButton, LinearLayout.LayoutParams(dp(104), dp(42)))
        root.addView(header)

        statusText = TextView(this).apply {
            text = "Pemutar media lokal"
            textSize = 12f
            setTextColor(Color.rgb(154, 168, 196))
            setPadding(0, dp(2), 0, dp(10))
        }
        root.addView(statusText)

        tabs = TabLayout(this).apply {
            setTabTextColors(Color.rgb(154, 168, 196), Color.WHITE)
            setSelectedTabIndicatorColor(Color.rgb(40, 200, 245))
            setBackgroundColor(Color.rgb(20, 27, 45))
            addTab(newTab().setText("Video"))
            addTab(newTab().setText("Musik"))
        }
        root.addView(tabs, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ))

        playerView = PlayerView(this).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(playerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(202)
        ).apply { topMargin = dp(10) })

        nowPlayingText = TextView(this).apply {
            text = "Belum ada media diputar"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
            setPadding(dp(4), dp(10), dp(4), dp(8))
        }
        root.addView(nowPlayingText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }
        val previousButton = makeButton("|◀").apply {
            setOnClickListener { controller?.seekToPreviousMediaItem() }
        }
        playButton = makeButton("Play").apply {
            setOnClickListener { togglePlayback() }
        }
        val nextButton = makeButton("▶|").apply {
            setOnClickListener { controller?.seekToNextMediaItem() }
        }
        repeatButton = makeButton("Repeat: Off").apply {
            setOnClickListener { cycleRepeatMode() }
        }

        controls.addView(previousButton, buttonParams(0.8f))
        controls.addView(playButton, buttonParams(1.0f))
        controls.addView(nextButton, buttonParams(0.8f))
        controls.addView(repeatButton, buttonParams(1.7f))
        root.addView(controls)

        emptyText = TextView(this).apply {
            text = "Belum ada media. Tekan Pindai setelah memberikan izin akses media."
            textSize = 14f
            setTextColor(Color.rgb(154, 168, 196))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        root.addView(emptyText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        adapter = TrackAdapter { position -> playSelectedTrack(position) }
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTabIsVideo = tab.position == 0
                playerView.visibility = if (currentTabIsVideo && currentMediaIsVideo) View.VISIBLE else View.GONE
                showCurrentLibrary()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        setContentView(root)
    }

    private fun connectPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val mediaController = future.get()
                controller = mediaController
                mediaController.addListener(playerListener)
                playerView.player = mediaController
                mediaController.repeatMode = toMedia3RepeatMode(currentRepeatMode)
                updateNowPlaying()
                updatePlaybackControls()
            } catch (exception: Exception) {
                Toast.makeText(this, "Pemutar gagal dimulai: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            refreshLibrary()
        }
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasVideoPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshLibrary() {
        if (!hasAudioPermission() && !hasVideoPermission()) {
            statusText.text = "Izin media belum diberikan. Tekan Pindai untuk mencoba lagi."
            videos = emptyList()
            audios = emptyList()
            showCurrentLibrary()
            return
        }

        statusText.text = "Sedang memindai media lokal…"
        scanExecutor.execute {
            val foundVideos = if (hasVideoPermission()) queryMedia(isVideo = true) else emptyList()
            val foundAudios = if (hasAudioPermission()) queryMedia(isVideo = false) else emptyList()

            runOnUiThread {
                videos = foundVideos
                audios = foundAudios
                statusText.text = "${audios.size} musik • ${videos.size} video ditemukan"
                showCurrentLibrary()
            }
        }
    }

    private fun queryMedia(isVideo: Boolean): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
        } else {
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE
        )
        val allowedExtensions = if (isVideo) videoExtensions else audioExtensions
        val results = ArrayList<Track>()
        val sortOrder = "${MediaStore.MediaColumns.TITLE} COLLATE NOCASE ASC"

        try {
            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor: Cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameColumn) ?: continue
                    val extension = "." + displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (extension !in allowedExtensions) continue

                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() }
                        ?: displayName
                    val duration = if (cursor.isNull(durationColumn)) 0L else cursor.getLong(durationColumn)
                    val size = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn)
                    val itemUri = ContentUris.withAppendedId(collection, id).toString()

                    results.add(
                        Track(
                            uri = itemUri,
                            title = title,
                            displayName = displayName,
                            extension = extension,
                            durationMs = duration,
                            sizeBytes = size,
                            isVideo = isVideo
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        return results
    }

    private fun showCurrentLibrary() {
        val items = if (currentTabIsVideo) videos else audios
        adapter.submitList(items)
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (currentTabIsVideo) {
            "Video belum ditemukan. Pastikan izin Video diberikan dan file berformat MP4 atau MKV."
        } else {
            "Musik belum ditemukan. Pastikan izin Audio diberikan dan file berformat MP3, AAC, M4A, FLAC, atau WAV."
        }
    }

    private fun playSelectedTrack(position: Int) {
        val mediaController = controller ?: run {
            Toast.makeText(this, "Pemutar sedang disiapkan. Coba lagi sebentar.", Toast.LENGTH_SHORT).show()
            return
        }
        val queue = if (currentTabIsVideo) videos else audios
        if (position !in queue.indices) return

        currentMediaIsVideo = queue[position].isVideo
        playerView.visibility = if (currentMediaIsVideo) View.VISIBLE else View.GONE

        val mediaItems = queue.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setDisplayTitle(track.displayName)
                .build()
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.uri)
                .setMediaMetadata(metadata)
                .build()
        }

        try {
            mediaController.setMediaItems(mediaItems, position, 0L)
            mediaController.repeatMode = toMedia3RepeatMode(currentRepeatMode)
            mediaController.prepare()
            mediaController.play()
            updateNowPlaying()
            updatePlaybackControls()
        } catch (exception: Exception) {
            Toast.makeText(this, "Tidak bisa membuka file: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun togglePlayback() {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount == 0) {
            val queue = if (currentTabIsVideo) videos else audios
            if (queue.isNotEmpty()) playSelectedTrack(0)
            return
        }
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
        updatePlaybackControls()
    }

    private fun cycleRepeatMode() {
        currentRepeatMode = pythonEngine.callAttr("next_repeat_mode", currentRepeatMode).toInt()
        controller?.repeatMode = toMedia3RepeatMode(currentRepeatMode)
        repeatButton.text = pythonEngine.callAttr("repeat_label", currentRepeatMode).toString()
    }

    private fun toMedia3RepeatMode(mode: Int): Int {
        return when (mode) {
            1 -> Player.REPEAT_MODE_ONE
            2 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun updateNowPlaying() {
        val title = controller?.currentMediaItem?.mediaMetadata?.title?.toString()
        nowPlayingText.text = title?.takeIf { it.isNotBlank() } ?: "Belum ada media diputar"
        updatePlaybackControls()
    }

    private fun updatePlaybackControls() {
        playButton.text = if (controller?.isPlaying == true) "Pause" else "Play"
        repeatButton.text = pythonEngine.callAttr("repeat_label", currentRepeatMode).toString()
    }

    private fun makeButton(label: String): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(29, 39, 64))
            cornerRadius = dp(13)
            insetTop = dp(2)
            insetBottom = dp(2)
            minHeight = dp(42)
            minimumHeight = dp(42)
            setPadding(dp(6), 0, dp(6), 0)
        }
    }

    private fun buttonParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(44), weight).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "Durasi tidak diketahui"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "Ukuran tidak diketahui"
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }

    override fun onDestroy() {
        playerView.player = null
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private inner class TrackAdapter(
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {
        private var items: List<Track> = emptyList()

        fun submitList(newItems: List<Track>) {
            items = newItems.toList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(11), dp(14), dp(11))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(20, 27, 45))
                    cornerRadius = dp(12).toFloat()
                }
            }
            val title = TextView(parent.context).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val details = TextView(parent.context).apply {
                textSize = 11f
                setTextColor(Color.rgb(154, 168, 196))
                maxLines = 1
            }
            row.addView(title)
            row.addView(details)
            val holder = TrackViewHolder(row, title, details)
            row.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            return holder
        }

        override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
            val track = items[position]
            holder.title.text = track.title
            holder.details.text = "${track.extension.removePrefix(".").uppercase(Locale.ROOT)} • ${formatDuration(track.durationMs)} • ${formatSize(track.sizeBytes)}"
            holder.itemView.setOnClickListener {
                val safePosition = holder.bindingAdapterPosition
                if (safePosition != RecyclerView.NO_POSITION) onItemClick(safePosition)
            }
        }

        override fun getItemCount(): Int = items.size

        inner class TrackViewHolder(
            view: View,
            val title: TextView,
            val details: TextView
        ) : RecyclerView.ViewHolder(view)
    }
}
