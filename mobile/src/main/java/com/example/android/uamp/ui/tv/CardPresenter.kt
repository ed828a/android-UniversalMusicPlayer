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

import android.app.Activity
import android.support.v17.leanback.widget.ImageCardView
import android.support.v17.leanback.widget.Presenter
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.ViewGroup

import com.example.android.uamp.R
import com.example.android.uamp.ui.MediaItemViewHolder
import com.example.android.uamp.utils.LogHelper
import com.example.android.uamp.utils.QueueHelper

class CardPresenter(activity: Activity) : Presenter() {
    private val mContext: Activity = activity

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        LogHelper.d(TAG, "onCreateViewHolder")

        val cardView = ImageCardView(mContext)
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setBackgroundColor(mContext.resources.getColor(R.color.default_background))
        return CardViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val description: MediaDescriptionCompat
        val cardViewHolder = viewHolder as CardViewHolder

        // Determine description and playing state of item based on instance type
        cardViewHolder.setState(MediaItemViewHolder.STATE_NONE)
        if (item is MediaBrowserCompat.MediaItem) {
            LogHelper.d(TAG, "onBindViewHolder MediaItem: ", item.toString())
            description = item.description
            cardViewHolder.setState(MediaItemViewHolder.getMediaItemState(mContext, item))
        } else if (item is MediaSessionCompat.QueueItem) {
            LogHelper.d(TAG, "onBindViewHolder QueueItem: ", item.toString())
            description = item.description
            if (QueueHelper.isQueueItemPlaying(mContext, item)) {
                cardViewHolder.setState(MediaItemViewHolder.getStateFromController(mContext))
            }
        } else {
            throw IllegalArgumentException("Object must be MediaItem or QueueItem, not " + item.javaClass.simpleName)
        }

        cardViewHolder.setupCardView(mContext, description)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        LogHelper.d(TAG, "onUnbindViewHolder")
        val cardViewHolder = viewHolder as CardViewHolder
        cardViewHolder.setState(MediaItemViewHolder.STATE_NONE)
        cardViewHolder.setBadgeImage(null)
    }

    override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder?) {
        LogHelper.d(TAG, "onViewAttachedToWindow")
        val cardViewHolder = viewHolder as CardViewHolder?
        cardViewHolder!!.attachView()
    }

    override fun onViewDetachedFromWindow(viewHolder: Presenter.ViewHolder) {
        LogHelper.d(TAG, "onViewDetachedFromWindow")
        val cardViewHolder = viewHolder as CardViewHolder
        cardViewHolder.detachView()
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(CardPresenter::class.java)


    }

}


