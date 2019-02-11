/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.uamp.ui

import android.app.Fragment
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import com.example.android.uamp.AlbumArtCache
import com.example.android.uamp.MusicService
import com.example.android.uamp.R
import com.example.android.uamp.utils.LogHelper

/**
 * A class that shows the Media Queue to the user.
 */
class PlaybackControlsFragment : Fragment() {

    private var mPlayPause: ImageButton? = null
    private var mTitle: TextView? = null
    private var mSubtitle: TextView? = null
    private var mExtraInfo: TextView? = null
    private var mAlbumArt: ImageView? = null
    private var mArtUrl: String? = null
    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private val mCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            LogHelper.d(TAG, "Received playback state change to state ", state.state)
            this@PlaybackControlsFragment.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata == null) {
                return
            }
            LogHelper.d(TAG, "Received metadata state change to mediaId=",
                    metadata.description.mediaId!!,
                    " song=", metadata.description.title!!)
            this@PlaybackControlsFragment.onMetadataChanged(metadata)
        }
    }

    private val mButtonListener = View.OnClickListener { v ->
        val controller = MediaControllerCompat.getMediaController(activity)
        val stateObj = controller.playbackState
        val state = stateObj?.state ?: PlaybackStateCompat.STATE_NONE
        LogHelper.d(TAG, "Button pressed, in state $state")
        when (v.id) {
            R.id.play_pause -> {
                LogHelper.d(TAG, "Play button pressed, in state $state")
                if (state == PlaybackStateCompat.STATE_PAUSED ||
                        state == PlaybackStateCompat.STATE_STOPPED ||
                        state == PlaybackStateCompat.STATE_NONE) {
                    playMedia()
                } else if (state == PlaybackStateCompat.STATE_PLAYING ||
                        state == PlaybackStateCompat.STATE_BUFFERING ||
                        state == PlaybackStateCompat.STATE_CONNECTING) {
                    pauseMedia()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_playback_controls, container, false)

        mPlayPause = rootView.findViewById<View>(R.id.play_pause) as ImageButton
        mPlayPause!!.isEnabled = true
        mPlayPause!!.setOnClickListener(mButtonListener)

        mTitle = rootView.findViewById<View>(R.id.title) as TextView
        mSubtitle = rootView.findViewById<View>(R.id.artist) as TextView
        mExtraInfo = rootView.findViewById<View>(R.id.extra_info) as TextView
        mAlbumArt = rootView.findViewById<View>(R.id.album_art) as ImageView
        rootView.setOnClickListener {
            val intent = Intent(activity, FullScreenPlayerActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val controller = MediaControllerCompat.getMediaController(activity)
            val metadata = controller.metadata
            if (metadata != null) {
                intent.putExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION,
                        metadata.description)
            }
            startActivity(intent)
        }
        return rootView
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "fragment.onStart")
        val controller = MediaControllerCompat.getMediaController(activity)
        if (controller != null) {
            onConnected()
        }
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "fragment.onStop")
        val controller = MediaControllerCompat.getMediaController(activity)
        controller?.unregisterCallback(mCallback)
    }

    fun onConnected() {
        val controller = MediaControllerCompat.getMediaController(activity)
        LogHelper.d(TAG, "onConnected, mediaController==null? ", controller == null)
        if (controller != null) {
            onMetadataChanged(controller.metadata)
            onPlaybackStateChanged(controller.playbackState)
            controller.registerCallback(mCallback)
        }
    }

    private fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        LogHelper.d(TAG, "onMetadataChanged: $metadata ")
        if (activity == null) {
            LogHelper.w(TAG, "onMetadataChanged called when getActivity null," + "this should not happen if the callback was properly unregistered. Ignoring.")
            return
        }
        if (metadata == null) {
            return
        }

        mTitle!!.text = metadata.description.title
        mSubtitle!!.text = metadata.description.subtitle
        var artUrl: String? = null
        if (metadata.description.iconUri != null) {
            artUrl = metadata.description.iconUri!!.toString()
        }
        if (!TextUtils.equals(artUrl, mArtUrl)) {
            mArtUrl = artUrl
            var art = metadata.description.iconBitmap
            val cache = AlbumArtCache.instance
            if (art == null) {
                art = cache.getIconImage(mArtUrl!!)
            }
            if (art != null) {
                mAlbumArt!!.setImageBitmap(art)
            } else {
                cache.fetch(artUrl!!, object : AlbumArtCache.FetchListener() {

                    override fun onFetched(artUrl: String, bitmap: Bitmap, icon: Bitmap?) {
                        if (icon != null) {
                            LogHelper.d(TAG, "album art icon of w=", icon.width,
                                    " h=", icon.height)
                            if (isAdded) {
                                mAlbumArt!!.setImageBitmap(icon)
                            }
                        }
                    }
                }
                )
            }
        }
    }

    fun setExtraInfo(extraInfo: String?) {
        if (extraInfo == null) {
            mExtraInfo!!.visibility = View.GONE
        } else {
            mExtraInfo!!.text = extraInfo
            mExtraInfo!!.visibility = View.VISIBLE
        }
    }

    private fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
        LogHelper.d(TAG, "onPlaybackStateChanged ", state!!)
        if (activity == null) {
            LogHelper.w(TAG, "onPlaybackStateChanged called when getActivity null," + "this should not happen if the callback was properly unregistered. Ignoring.")
            return
        }
        if (state == null) {
            return
        }
        var enablePlay = false
        when (state.state) {
            PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> enablePlay = true
            PlaybackStateCompat.STATE_ERROR -> {
                LogHelper.e(TAG, "error playbackstate: ", state.errorMessage)
                Toast.makeText(activity, state.errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        if (enablePlay) {
            mPlayPause!!.setImageDrawable(
                    ContextCompat.getDrawable(activity, R.drawable.ic_play_arrow_black_36dp))
        } else {
            mPlayPause!!.setImageDrawable(
                    ContextCompat.getDrawable(activity, R.drawable.ic_pause_black_36dp))
        }

        val controller = MediaControllerCompat.getMediaController(activity)
        var extraInfo: String? = null
        if (controller != null && controller.extras != null) {
            val castName = controller.extras.getString(MusicService.EXTRA_CONNECTED_CAST)
            if (castName != null) {
                extraInfo = resources.getString(R.string.casting_to_device, castName)
            }
        }
        setExtraInfo(extraInfo)
    }

    private fun playMedia() {
        val controller = MediaControllerCompat.getMediaController(activity)
        controller?.transportControls?.play()
    }

    private fun pauseMedia() {
        val controller = MediaControllerCompat.getMediaController(activity)
        controller?.transportControls?.pause()
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(PlaybackControlsFragment::class.java)
    }
}
