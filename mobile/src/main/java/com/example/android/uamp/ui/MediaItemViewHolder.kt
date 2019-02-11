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

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import com.example.android.uamp.R
import com.example.android.uamp.utils.MediaIDHelper

class MediaItemViewHolder {

    private var mImageView: ImageView? = null
    private var mTitleView: TextView? = null
    private var mDescriptionView: TextView? = null

    companion object {

        val STATE_INVALID = -1
        val STATE_NONE = 0
        val STATE_PLAYABLE = 1
        val STATE_PAUSED = 2
        val STATE_PLAYING = 3

        private var sColorStatePlaying: ColorStateList? = null
        private var sColorStateNotPlaying: ColorStateList? = null

        // Returns a view for use in media item list.
        internal fun setupListView(activity: Activity, convertView: View?, parent: ViewGroup,
                                   item: MediaBrowserCompat.MediaItem): View {
            var convertView = convertView
            if (sColorStateNotPlaying == null || sColorStatePlaying == null) {
                initializeColorStateLists(activity)
            }

            val holder: MediaItemViewHolder

            var cachedState: Int? = STATE_INVALID

            if (convertView == null) {
                convertView = LayoutInflater.from(activity)
                        .inflate(R.layout.media_list_item, parent, false)
                holder = MediaItemViewHolder()
                holder.mImageView = convertView!!.findViewById<View>(R.id.play_eq) as ImageView
                holder.mTitleView = convertView.findViewById<View>(R.id.title) as TextView
                holder.mDescriptionView = convertView.findViewById<View>(R.id.description) as TextView
                convertView.tag = holder
            } else {
                holder = convertView.tag as MediaItemViewHolder
                cachedState = convertView.getTag(R.id.tag_mediaitem_state_cache) as Int
            }

            val description = item.description
            holder.mTitleView!!.text = description.title
            holder.mDescriptionView!!.text = description.subtitle

            // If the state of convertView is different, we need to adapt the view to the
            // new state.
            val state = getMediaItemState(activity, item)
            if (cachedState == null || cachedState != state) {
                val drawable = getDrawableByState(activity, state)
                if (drawable != null) {
                    holder.mImageView!!.setImageDrawable(drawable)
                    holder.mImageView!!.visibility = View.VISIBLE
                } else {
                    holder.mImageView!!.visibility = View.GONE
                }
                convertView.setTag(R.id.tag_mediaitem_state_cache, state)
            }

            return convertView
        }

        private fun initializeColorStateLists(ctx: Context) {
            sColorStateNotPlaying = ColorStateList.valueOf(ctx.resources.getColor(
                    R.color.media_item_icon_not_playing))
            sColorStatePlaying = ColorStateList.valueOf(ctx.resources.getColor(
                    R.color.media_item_icon_playing))
        }

        fun getDrawableByState(context: Context, state: Int): Drawable? {
            if (sColorStateNotPlaying == null || sColorStatePlaying == null) {
                initializeColorStateLists(context)
            }

            when (state) {
                STATE_PLAYABLE -> {
                    val pauseDrawable = ContextCompat.getDrawable(context,
                            R.drawable.ic_play_arrow_black_36dp)
                    DrawableCompat.setTintList(pauseDrawable, sColorStateNotPlaying)
                    return pauseDrawable
                }
                STATE_PLAYING -> {
                    val animation = ContextCompat.getDrawable(context, R.drawable.ic_equalizer_white_36dp) as AnimationDrawable
                    DrawableCompat.setTintList(animation, sColorStatePlaying)
                    animation.start()
                    return animation
                }
                STATE_PAUSED -> {
                    val playDrawable = ContextCompat.getDrawable(context,
                            R.drawable.ic_equalizer1_white_36dp)
                    DrawableCompat.setTintList(playDrawable, sColorStatePlaying)
                    return playDrawable
                }
                else -> return null
            }
        }

        fun getMediaItemState(context: Activity, mediaItem: MediaBrowserCompat.MediaItem): Int {
            var state = STATE_NONE
            // Set state to playable first, then override to playing or paused state if needed
            if (mediaItem.isPlayable) {
                state = STATE_PLAYABLE
                if (MediaIDHelper.isMediaItemPlaying(context, mediaItem)) {
                    state = getStateFromController(context)
                }
            }

            return state
        }

        fun getStateFromController(context: Activity): Int {
            val controller = MediaControllerCompat.getMediaController(context)
            val pbState = controller.playbackState
            return if (pbState == null || pbState.state == PlaybackStateCompat.STATE_ERROR) {
                MediaItemViewHolder.STATE_NONE
            } else if (pbState.state == PlaybackStateCompat.STATE_PLAYING) {
                MediaItemViewHolder.STATE_PLAYING
            } else {
                MediaItemViewHolder.STATE_PAUSED
            }
        }
    }
}
