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
package com.example.android.uamp.ui.tv

import android.content.ComponentName
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat

import com.example.android.uamp.MusicService
import com.example.android.uamp.R
import com.example.android.uamp.utils.LogHelper

class TvVerticalGridActivity : FragmentActivity(), TvVerticalGridFragment.MediaFragmentListener {

    private var mMediaBrowser: MediaBrowserCompat? = null
    private var mMediaId: String? = null
    private var mTitle: String? = null

    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected: session token ",
                    mMediaBrowser!!.sessionToken)

            try {
                val mediaController = MediaControllerCompat(
                        this@TvVerticalGridActivity, mMediaBrowser!!.sessionToken)
                MediaControllerCompat.setMediaController(this@TvVerticalGridActivity, mediaController)
                browse()
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
            }

        }

        override fun onConnectionFailed() {
            LogHelper.d(TAG, "onConnectionFailed")
        }

        override fun onConnectionSuspended() {
            LogHelper.d(TAG, "onConnectionSuspended")
            MediaControllerCompat.setMediaController(this@TvVerticalGridActivity, null)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_vertical_grid)

        mMediaId = intent.getStringExtra(TvBrowseActivity.SAVED_MEDIA_ID)
        mTitle = intent.getStringExtra(TvBrowseActivity.BROWSE_TITLE)

        window.setBackgroundDrawableResource(R.drawable.bg)

        mMediaBrowser = MediaBrowserCompat(this,
                ComponentName(this, MusicService::class.java),
                mConnectionCallback, null)
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "Activity onStart: mMediaBrowser connect")
        mMediaBrowser!!.connect()
    }

    override fun onStop() {
        super.onStop()
        mMediaBrowser!!.disconnect()
    }

    protected fun browse() {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mMediaId!!)
        val fragment = supportFragmentManager
                .findFragmentById(R.id.vertical_grid_fragment) as TvVerticalGridFragment
        fragment.setMediaId(mMediaId)
        fragment.title = mTitle
    }

    override fun getMediaBrowser(): MediaBrowserCompat? {
        return mMediaBrowser
    }


    companion object {

        private val TAG = LogHelper.makeLogTag(TvVerticalGridActivity::class.java)
        val SHARED_ELEMENT_NAME = "hero"
    }

}
