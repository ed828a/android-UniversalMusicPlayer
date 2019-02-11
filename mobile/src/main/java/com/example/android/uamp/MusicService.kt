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

package com.example.android.uamp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.media.MediaRouter

import com.example.android.uamp.model.MusicProvider
import com.example.android.uamp.playback.CastPlayback
import com.example.android.uamp.playback.LocalPlayback
import com.example.android.uamp.playback.Playback
import com.example.android.uamp.playback.PlaybackManager
import com.example.android.uamp.playback.QueueManager
import com.example.android.uamp.ui.NowPlayingActivity
import com.example.android.uamp.utils.CarHelper
import com.example.android.uamp.utils.LogHelper
import com.example.android.uamp.utils.TvHelper
import com.example.android.uamp.utils.WearHelper
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

import java.lang.ref.WeakReference
import java.util.ArrayList

import com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_EMPTY_ROOT
import com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 * To implement a MediaBrowserService, you need to:
 *
 *
 *
 *  *  Extend [android.service.media.MediaBrowserService], implementing the media browsing
 * related methods [android.service.media.MediaBrowserService.onGetRoot] and
 * [android.service.media.MediaBrowserService.onLoadChildren];
 *  *  In onCreate, start a new [android.media.session.MediaSession] and notify its parent
 * with the session's token [android.service.media.MediaBrowserService.setSessionToken];
 *
 *  *  Set a callback on the
 * [android.media.session.MediaSession.setCallback].
 * The callback will receive all the user's actions, like play, pause, etc;
 *
 *  *  Handle all the actual music playing using any method your app prefers (for example,
 * [android.media.MediaPlayer])
 *
 *  *  Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * [android.media.session.MediaSession.setPlaybackState]
 * [android.media.session.MediaSession.setMetadata] and
 * [android.media.session.MediaSession.setQueue])
 *
 *  *  Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 *
 *
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 *
 *
 *  *  Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 *
 *
 *
 * @see [README.md](README.md) for more details.
 */
class MusicService : MediaBrowserServiceCompat(), PlaybackManager.PlaybackServiceCallback {

    private var mMusicProvider: MusicProvider? = null
    private var mPlaybackManager: PlaybackManager? = null

    private var mSession: MediaSessionCompat? = null
    private var mMediaNotificationManager: MediaNotificationManager? = null
    private var mSessionExtras: Bundle? = null
    private val mDelayedStopHandler = DelayedStopHandler(this)
    private var mMediaRouter: MediaRouter? = null
    private var mPackageValidator: PackageValidator? = null
    private var mCastSessionManager: SessionManager? = null
    private var mCastSessionManagerListener: SessionManagerListener<CastSession>? = null

    private var mIsConnectedToCar: Boolean = false
    private var mCarConnectionReceiver: BroadcastReceiver? = null

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    override fun onCreate() {
        super.onCreate()
        LogHelper.d(TAG, "onCreate")

        mMusicProvider = MusicProvider()

        // To make the app more responsive, fetch and cache catalog information now.
        // This can help improve the response time in the method
        // {@link #onLoadChildren(String, Result<List<MediaItem>>) onLoadChildren()}.
        mMusicProvider!!.retrieveMediaAsync(null/* Callback */)

        mPackageValidator = PackageValidator(this)

        val queueManager = QueueManager(mMusicProvider!!, resources,
                object : QueueManager.MetadataUpdateListener {
                    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                        mSession!!.setMetadata(metadata)
                    }

                    override fun onMetadataRetrieveError() {
                        mPlaybackManager!!.updatePlaybackState(
                                getString(R.string.error_no_metadata))
                    }

                    override fun onCurrentQueueIndexUpdated(queueIndex: Int) {
                        mPlaybackManager!!.handlePlayRequest()
                    }

                    override fun onQueueUpdated(title: String,
                                                newQueue: List<MediaSessionCompat.QueueItem>) {
                        mSession!!.setQueue(newQueue)
                        mSession!!.setQueueTitle(title)
                    }
                })

        val playback = LocalPlayback(this, mMusicProvider!!)
        mPlaybackManager = PlaybackManager(this, resources, mMusicProvider!!, queueManager,
                playback)

        // Start a new MediaSession
        mSession = MediaSessionCompat(this, "MusicService")
        setSessionToken(mSession!!.sessionToken)
        mSession!!.setCallback(mPlaybackManager!!.mediaSessionCallback)
        mSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        val context = applicationContext
        val intent = Intent(context, NowPlayingActivity::class.java)
        val pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT)
        mSession!!.setSessionActivity(pi)

        mSessionExtras = Bundle()
        CarHelper.setSlotReservationFlags(mSessionExtras!!, true, true, true)
        WearHelper.setSlotReservationFlags(mSessionExtras!!, true, true)
        WearHelper.setUseBackgroundFromTheme(mSessionExtras!!, true)
        mSession!!.setExtras(mSessionExtras)

        mPlaybackManager!!.updatePlaybackState(null)

        try {
            mMediaNotificationManager = MediaNotificationManager(this)
        } catch (e: RemoteException) {
            throw IllegalStateException("Could not create a MediaNotificationManager", e)
        }

        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)

        if (!TvHelper.isTvUiMode(this) && playServicesAvailable == ConnectionResult.SUCCESS) {
            mCastSessionManager = CastContext.getSharedInstance(this).sessionManager
            mCastSessionManagerListener = CastSessionManagerListener()
            mCastSessionManager!!.addSessionManagerListener(mCastSessionManagerListener!!,
                    CastSession::class.java)
        }

        mMediaRouter = MediaRouter.getInstance(applicationContext)

        registerCarConnectionReceiver()
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service.onStartCommand
     */
    override fun onStartCommand(startIntent: Intent?, flags: Int, startId: Int): Int {
        if (startIntent != null) {
            val action = startIntent.action
            val command = startIntent.getStringExtra(CMD_NAME)
            if (ACTION_CMD == action) {
                if (CMD_PAUSE == command) {
                    mPlaybackManager!!.handlePauseRequest()
                } else if (CMD_STOP_CASTING == command) {
                    CastContext.getSharedInstance(this).sessionManager.endCurrentSession(true)
                }
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mSession, startIntent)
            }
        }
        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        return START_STICKY
    }

    /*
     * Handle case when user swipes the app away from the recents apps list by
     * stopping the service (and any ongoing playback).
     */
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service.onDestroy
     */
    override fun onDestroy() {
        LogHelper.d(TAG, "onDestroy")
        unregisterCarConnectionReceiver()
        // Service is being killed, so make sure we release our resources
        mPlaybackManager!!.handleStopRequest(null)
        mMediaNotificationManager!!.stopNotification()

        if (mCastSessionManager != null) {
            mCastSessionManager!!.removeSessionManagerListener(mCastSessionManagerListener,
                    CastSession::class.java)
        }

        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mSession!!.release()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int,
                           rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName",
                "; clientUid=$clientUid ; rootHints=", rootHints!!)
        // To ensure you are not allowing any arbitrary app to browse your app's contents, you
        // need to check the origin:
        if (!mPackageValidator!!.isCallerAllowed(this, clientPackageName, clientUid)) {
            // If the request comes from an untrusted package, return an empty browser root.
            // If you return null, then the media browser will not be able to connect and
            // no further calls will be made to other media browsing methods.
            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                    + "Returning empty browser root so all apps can use MediaController."
                    + clientPackageName)
            return MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_EMPTY_ROOT, null)
        }

        if (CarHelper.isValidCarPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library to show a different subset
            // when connected to the car, this is where you should handle it.
            // If you want to adapt other runtime behaviors, like tweak ads or change some behavior
            // that should be different on cars, you should instead use the boolean flag
            // set by the BroadcastReceiver mCarConnectionReceiver (mIsConnectedToCar).
        }

        if (WearHelper.isValidWearCompanionPackage(clientPackageName)) {
            // Optional: if your app needs to adapt the music library for when browsing from a
            // Wear device, you should return a different MEDIA ROOT here, and then,
            // on onLoadChildren, handle it accordingly.
        }

        return MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, null)
    }

    override fun onLoadChildren(parentMediaId: String,
                                result: MediaBrowserServiceCompat.Result<List<MediaItem>>) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId)
        if (MEDIA_ID_EMPTY_ROOT.equals(parentMediaId)) {
            result.sendResult(ArrayList())
        } else if (mMusicProvider!!.isInitialized) {
            // if music library is ready, return immediately
            result.sendResult(mMusicProvider!!.getChildren(parentMediaId, resources))
        } else {
            // otherwise, only return results when the music library is retrieved
            result.detach()
            mMusicProvider!!.retrieveMediaAsync(object : MusicProvider.Callback {
                override fun onMusicCatalogReady(success: Boolean) {
                    result.sendResult(mMusicProvider!!.getChildren(parentMediaId, resources))
                }
            })
        }
    }

    /**
     * Callback method called from PlaybackManager whenever the music is about to play.
     */
    override fun onPlaybackStart() {
        mSession!!.isActive = true

        mDelayedStopHandler.removeCallbacksAndMessages(null)

        // The service needs to continue running even after the bound client (usually a
        // MediaController) disconnects, otherwise the music playback will stop.
        // Calling startService(Intent) will keep the service running until it is explicitly killed.
        startService(Intent(applicationContext, MusicService::class.java))
    }


    /**
     * Callback method called from PlaybackManager whenever the music stops playing.
     */
    override fun onPlaybackStop() {
        mSession!!.isActive = false
        // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
        // potentially stopping the service.
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        stopForeground(true)
    }

    override fun onNotificationRequired() {
        mMediaNotificationManager!!.startNotification()
    }

    override fun onPlaybackStateUpdated(newState: PlaybackStateCompat) {
        mSession!!.setPlaybackState(newState)
    }

    private fun registerCarConnectionReceiver() {
        val filter = IntentFilter(CarHelper.ACTION_MEDIA_STATUS)
        mCarConnectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val connectionEvent = intent.getStringExtra(CarHelper.MEDIA_CONNECTION_STATUS)
                mIsConnectedToCar = CarHelper.MEDIA_CONNECTED == connectionEvent
                LogHelper.i(TAG, "Connection event to Android Auto: ", connectionEvent,
                        " isConnectedToCar=", mIsConnectedToCar)
            }
        }
        registerReceiver(mCarConnectionReceiver, filter)
    }

    private fun unregisterCarConnectionReceiver() {
        unregisterReceiver(mCarConnectionReceiver)
    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private class DelayedStopHandler (service: MusicService) : Handler() {
        private val mWeakReference: WeakReference<MusicService>

        init {
            mWeakReference = WeakReference(service)
        }

        override fun handleMessage(msg: Message) {
            val service = mWeakReference.get()
            if (service != null && service.mPlaybackManager!!.playback != null) {
                if (service.mPlaybackManager!!.playback!!.isPlaying) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.")
                    return
                }
                LogHelper.d(TAG, "Stopping service with delay handler.")
                service.stopSelf()
            }
        }
    }

    /**
     * Session Manager Listener responsible for switching the Playback instances
     * depending on whether it is connected to a remote player.
     */
    private inner class CastSessionManagerListener : SessionManagerListener<CastSession> {

        override fun onSessionEnded(session: CastSession, error: Int) {
            LogHelper.d(TAG, "onSessionEnded")
            mSessionExtras!!.remove(EXTRA_CONNECTED_CAST)
            mSession!!.setExtras(mSessionExtras)
            val playback = LocalPlayback(this@MusicService, mMusicProvider!!)
            mMediaRouter!!.setMediaSessionCompat(null)
            mPlaybackManager!!.switchToPlayback(playback, false)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            // In case we are casting, send the device name as an extra on MediaSession metadata.
            mSessionExtras!!.putString(EXTRA_CONNECTED_CAST,
                    session.castDevice.friendlyName)
            mSession!!.setExtras(mSessionExtras)
            // Now we can switch to CastPlayback
            val playback = CastPlayback(mMusicProvider!!, this@MusicService)
            mMediaRouter!!.setMediaSessionCompat(mSession)
            mPlaybackManager!!.switchToPlayback(playback, true)
        }

        override fun onSessionStarting(session: CastSession) {}

        override fun onSessionStartFailed(session: CastSession, error: Int) {}

        override fun onSessionEnding(session: CastSession) {
            // This is our final chance to update the underlying stream position
            // In onSessionEnded(), the underlying CastPlayback#mRemoteMediaClient
            // is disconnected and hence we update our local value of stream position
            // to the latest position.
            mPlaybackManager!!.playback!!.updateLastKnownStreamPosition()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {}

        override fun onSessionResumeFailed(session: CastSession, error: Int) {}

        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(MusicService::class.java)

        // Extra on MediaSession that contains the Cast device name currently connected to
        val EXTRA_CONNECTED_CAST = "com.example.android.uamp.CAST_NAME"
        // The action of the incoming Intent indicating that it contains a command
        // to be executed (see {@link #onStartCommand})
        val ACTION_CMD = "com.example.android.uamp.ACTION_CMD"
        // The key in the extras of the incoming Intent indicating the command that
        // should be executed (see {@link #onStartCommand})
        val CMD_NAME = "CMD_NAME"
        // A value of a CMD_NAME key in the extras of the incoming Intent that
        // indicates that the music playback should be paused (see {@link #onStartCommand})
        val CMD_PAUSE = "CMD_PAUSE"
        // A value of a CMD_NAME key that indicates that the music playback should switch
        // to local playback from cast playback.
        val CMD_STOP_CASTING = "CMD_STOP_CASTING"
        // Delay stopSelf by using a handler.
        private val STOP_DELAY = 30000
    }
}
