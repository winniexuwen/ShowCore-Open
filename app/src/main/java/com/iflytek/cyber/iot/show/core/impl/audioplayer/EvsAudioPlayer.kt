package com.iflytek.cyber.iot.show.core.impl.audioplayer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.util.Util
import com.iflytek.cyber.evs.sdk.agent.AudioPlayer

class EvsAudioPlayer private constructor(context: Context) : AudioPlayer() {
    companion object {
        private const val TAG = "EvsAudioPlayer"

        private var instance: EvsAudioPlayer? = null

        fun get(context: Context?): EvsAudioPlayer {
            instance?.let {
                return it
            } ?: run {
                val player = EvsAudioPlayer(context!!)
                instance = player
                return player
            }
        }
    }

    private var playbackPlayer: AudioPlayerInstance? = null
    private var ttsPlayer: AudioPlayerInstance? = null
    private var ringPlayer: AudioPlayerInstance? = null

    private var currentResourceMediaType = C.TYPE_OTHER
    private var currenntPlaybackType: String? = null
    private var currentPlayingUrl: String? = null
    private var currentPlayResourceId: String? = null

    private var simpleMediaChangedListener: SimpleMediaChangedListener? = null

    private var currentTtsText: String? = null

    private inner class ImplListener : AudioPlayerInstance.Listener {
        override fun onPlayerPositionUpdated(
            player: AudioPlayerInstance,
            type: String,
            position: Long
        ) {
            onPositionUpdated(type, player.resourceId ?: "", position)
        }

        private var playWhenReady = false
        private var lastPlayState = -1

        override fun onPlayerStateChanged(
            player: AudioPlayerInstance,
            type: String,
            playWhenReady: Boolean,
            playbackState: Int
        ) {
            Log.d(TAG, "onPlayerStateChanged($type, $playWhenReady, $playbackState)")
            val isPlayingChanged = this.playWhenReady != playWhenReady
            this.playWhenReady = playWhenReady

            when (playbackState) {
                Player.STATE_ENDED -> {
                    player.isStarted = false
                    if (playWhenReady)
                        onCompleted(type, player.resourceId ?: "")
                }
                Player.STATE_BUFFERING -> {
//                    if (player.isStarted && lastPlayState == Player.STATE_READY) {
//                        onPaused(type, player.resourceId ?: "")
//                    }
                    simpleMediaChangedListener?.onBuffering(type, player.resourceId ?: "")
                }
                Player.STATE_IDLE -> {
                    player.isStarted = false
//                    if (!playWhenReady) {
                    onStopped(type, player.resourceId ?: "")
//                    }
                }
                Player.STATE_READY -> {
                    if (lastPlayState == Player.STATE_BUFFERING) {
                        if (playWhenReady) {
                            if (!player.isStarted) {
                                player.isStarted = true
                                onStarted(type, player.resourceId ?: "")
                            } else {
                                onResumed(type, player.resourceId ?: "")
                            }
                        }
                    } else if (lastPlayState == Player.STATE_READY) {
                        if (isPlayingChanged) {
                            if (playWhenReady) {
                                onResumed(type, player.resourceId ?: "")
                            } else {
                                onPaused(type, player.resourceId ?: "")
                            }
                        }
                    }
                }
            }

            lastPlayState = playbackState
        }

        override fun onPlayerError(
            player: AudioPlayerInstance,
            type: String,
            error: ExoPlaybackException?
        ) {
            val errorCode: String = when (error?.type) {
                ExoPlaybackException.TYPE_UNEXPECTED -> {
                    MEDIA_ERROR_UNKNOWN
                }
                ExoPlaybackException.TYPE_SOURCE -> {
                    MEDIA_ERROR_INVALID_REQUEST
                }
                ExoPlaybackException.TYPE_REMOTE -> {
                    MEDIA_ERROR_SERVICE_UNAVAILABLE
                }
                ExoPlaybackException.TYPE_RENDERER -> {
                    MEDIA_ERROR_INTERNAL_SERVER_ERROR
                }
                ExoPlaybackException.TYPE_OUT_OF_MEMORY -> {
                    MEDIA_ERROR_INTERNAL_DEVICE_ERROR
                }
                else -> {
                    MEDIA_ERROR_UNKNOWN
                }
            }
            onError(type, player.resourceId ?: "", errorCode)
        }
    }

    private fun initPlayers(context: Context) {
        playbackPlayer?.destroy()
        ttsPlayer?.destroy()
        ringPlayer?.destroy()

        playbackPlayer = AudioPlayerInstance(context, TYPE_PLAYBACK)
        ttsPlayer = AudioPlayerInstance(context, TYPE_TTS)
        ringPlayer = AudioPlayerInstance(context, TYPE_RING)

        playbackPlayer?.setListener(ImplListener())
        ttsPlayer?.setListener(ImplListener())
        ringPlayer?.setListener(ImplListener())
    }

    init {
        initPlayers(context)
    }

    fun getCurrentResourceMediaPlayerType(): Int {
        return currentResourceMediaType
    }

    private fun getPlayer(type: String?): AudioPlayerInstance? {
        return when (type) {
            TYPE_TTS -> ttsPlayer
            TYPE_PLAYBACK -> playbackPlayer
            TYPE_RING -> ringPlayer
            else -> null
        }
    }

    fun getCurrentTtsText(): String? {
        return currentTtsText
    }

    fun setSimpleMediaChangedListener(simpleMediaChangedListener: SimpleMediaChangedListener?) {
        this.simpleMediaChangedListener = simpleMediaChangedListener
    }

    override fun onTtsText(text: String) {
        super.onTtsText(text)
        currentTtsText = text
    }

    override fun play(type: String, resourceId: String, url: String): Boolean {
        Log.d(TAG, "try to play $url on $type player")
        val player = getPlayer(type)
        if (type == TYPE_PLAYBACK) {
            val uri = Uri.parse(url)
            currentResourceMediaType = Util.inferContentType(uri.lastPathSegment ?: "")
            currentPlayResourceId = resourceId
            currenntPlaybackType = type
            currentPlayingUrl = url
        }
        player?.let {
            it.resourceId = resourceId
            it.play(url)
            it.isStarted = false
            //onStarted(type, resourceId)
            //onPositionUpdated(type, resourceId, 0)
            return true
        } ?: run {
            return false
        }
    }

    override fun resume(type: String): Boolean {
        val player = getPlayer(type)

        /**
         * 如果之前播放的是流媒体资源(.m3u8)，则重新播放，而不是恢复播放
         */
        if (currentResourceMediaType == C.TYPE_HLS && currentPlayResourceId != null &&
            currentPlayingUrl != null && currenntPlaybackType != null
        ) {
            player?.let {
                it.resourceId = currentPlayResourceId
                it.play(currentPlayingUrl!!)
                it.isStarted = false
                onStarted(type, currentPlayResourceId!!)
                onPositionUpdated(type, currentPlayResourceId!!, 0)
                return true
            } ?: run {
                return false
            }
        } else {
            player?.resume() ?: run {
                return false
            }
        }
        return true
    }

    override fun pause(type: String): Boolean {
        val player = getPlayer(type)
        player?.pause()?.let {
            onPaused(type, player.resourceId ?: "")
        } ?: run {
            return false
        }
        return true
    }

    override fun stop(type: String): Boolean {
        val player = getPlayer(type)
        player?.stop()?.let {
            onStopped(type, player.resourceId ?: "")
        } ?: run {
            return false
        }
        return true
    }

    fun quit(type: String): Boolean {
        val player = getPlayer(type)
        player?.stop()?.let {
            onQuit(type, player.resourceId ?: "")
        } ?: run {
            return false
        }
        return true
    }

    fun playTtsFile(path: String) {
        ttsPlayer?.playTtsFile(path)
    }

    override fun seekTo(type: String, offset: Long): Boolean {
        super.seekTo(type, offset)
        val player = getPlayer(type)
        if (type == TYPE_PLAYBACK && currentResourceMediaType == C.TYPE_HLS) { //如果是流媒体资源（.m3u8），则不用设置进度
            return true
        }
        player?.let {
            it.seekTo(offset)
            return true
        } ?: run {
            return false
        }
    }

    override fun getOffset(type: String): Long {
        return getPlayer(type)?.getOffset() ?: 0
    }

    override fun getDuration(type: String): Long {
        return getPlayer(type)?.getDuration() ?: 0
    }

    override fun moveToForegroundIfAvailable(type: String): Boolean {
        getPlayer(type)?.run {
            synchronized(volGrowFlag) {
                volGrowFlag = true
            }

            isBackground = false

            Handler(getLooper()).post {
                setVolume(1f)
            }

            return true
        } ?: run {
            return false
        }
    }

    override fun moveToBackground(type: String): Boolean {
        getPlayer(type)?.run {
            synchronized(volGrowFlag) {
                isBackground = true

                volGrowFlag = false

                Handler(getLooper()).post {
                    setVolume(AudioPlayerInstance.BACKGROUND_VOLUME)
                }
            }
            return true
        } ?: run {
            return false
        }
    }

    fun getPlayerPlaybackState(type: String): Int? {
        return getPlayer(type)?.getPlaybackState()
    }

    abstract class SimpleMediaChangedListener : MediaStateChangedListener {
        override fun onStarted(player: AudioPlayer, type: String, resourceId: String) {
        }

        override fun onPaused(player: AudioPlayer, type: String, resourceId: String) {
        }

        override fun onPositionUpdated(
            player: AudioPlayer,
            type: String,
            resourceId: String,
            position: Long
        ) {
        }

        override fun onError(
            player: AudioPlayer,
            type: String,
            resourceId: String,
            errorCode: String
        ) {
        }

        override fun onResumed(player: AudioPlayer, type: String, resourceId: String) {
        }

        override fun onStopped(player: AudioPlayer, type: String, resourceId: String) {
        }

        override fun onCompleted(player: AudioPlayer, type: String, resourceId: String) {
        }

        open fun onBuffering(type: String, resourceId: String) {

        }
    }
}
