package com.won1203.lura

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.won1203.lura.databinding.FragmentPlayerBinding
import com.won1203.lura.playback.SleepPlaybackController
import com.won1203.lura.playback.SleepPlaybackRequest
import com.won1203.lura.playback.SleepPlaybackService
import com.google.common.util.concurrent.ListenableFuture

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private var playbackRequest: SleepPlaybackRequest? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackControls()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackControls()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            bindMetadata(mediaMetadata)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playbackRequest = SleepPlaybackRequest.fromBundle(arguments)
        playbackRequest?.let(::bindPlaybackRequest)

        binding.playPauseButton.setOnClickListener {
            val currentController = controller ?: return@setOnClickListener
            if (currentController.isPlaying) {
                currentController.pause()
            } else {
                currentController.play()
            }
        }
        binding.stopPlaybackButton.setOnClickListener {
            SleepPlaybackController.stop(requireContext())
            findNavController().navigate(
                R.id.alarmHistoryFragment,
                null,
                NavOptions.Builder()
                    .setPopUpTo(R.id.playerFragment, true)
                    .build()
            )
        }
    }

    override fun onStart() {
        super.onStart()
        connectController()
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun connectController() {
        val context = requireContext().applicationContext
        val token = SessionToken(context, ComponentName(context, SleepPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val resolvedController = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = resolvedController
                resolvedController.addListener(playerListener)
                bindMetadata(resolvedController.mediaMetadata)
                updatePlaybackControls()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun bindPlaybackRequest(request: SleepPlaybackRequest) {
        binding.playerTitle.text = request.title
        binding.playerCategory.text = request.categoryName
        binding.playerTags.text = formatTags(request.tags)
        binding.playerDuration.text = formatDuration(request.durationMinutes)
    }

    private fun bindMetadata(mediaMetadata: MediaMetadata) {
        mediaMetadata.title?.let { binding.playerTitle.text = it }
        mediaMetadata.artist?.let { binding.playerCategory.text = it }
        mediaMetadata.description?.let { binding.playerTags.text = it }
    }

    private fun updatePlaybackControls() {
        val currentController = controller
        val isConnected = currentController != null
        val isPlaying = currentController?.isPlaying == true

        binding.playPauseButton.isEnabled = isConnected
        binding.stopPlaybackButton.isEnabled = isConnected
        binding.playPauseButton.text = getString(
            if (isPlaying) R.string.player_pause else R.string.player_play
        )
        binding.playPauseButton.setIconResource(
            if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24
        )

        binding.playerStatus.text = when {
            !isConnected -> getString(R.string.player_status_disconnected)
            currentController?.playbackState == Player.STATE_BUFFERING -> getString(R.string.player_status_buffering)
            currentController?.playbackState == Player.STATE_ENDED -> getString(R.string.player_status_ended)
            isPlaying -> getString(R.string.player_status_playing)
            else -> getString(R.string.player_status_paused)
        }
    }

    private fun formatTags(tags: List<String>): String =
        if (tags.isEmpty()) {
            getString(R.string.player_tags_empty)
        } else {
            tags.joinToString(TAG_SEPARATOR)
        }

    private fun formatDuration(durationMinutes: Int): String =
        if (durationMinutes > 0) {
            getString(R.string.player_duration_format, durationMinutes)
        } else {
            ""
        }

    companion object {
        private const val TAG_SEPARATOR = " · "
    }
}
