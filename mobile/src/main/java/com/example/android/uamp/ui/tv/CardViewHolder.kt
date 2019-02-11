/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v17.leanback.widget.ImageCardView
import android.support.v17.leanback.widget.Presenter
import android.support.v4.media.MediaDescriptionCompat
import android.view.View

import com.example.android.uamp.AlbumArtCache
import com.example.android.uamp.ui.MediaItemViewHolder

class CardViewHolder(view: View) : Presenter.ViewHolder(view) {

    private val mCardView: ImageCardView?
    private var mItemState: Int = 0

    init {
        mCardView = view as ImageCardView
        mItemState = MediaItemViewHolder.STATE_NONE
    }

    fun setState(state: Int) {
        mItemState = state
    }

    fun attachView() {
        if (mItemState == MediaItemViewHolder.STATE_PLAYING) {
            val badgeDrawable = mCardView!!.badgeImage as AnimationDrawable
            badgeDrawable?.start()
        }
    }

    fun detachView() {
        if (mItemState == MediaItemViewHolder.STATE_PLAYING) {
            val badgeDrawable = mCardView!!.badgeImage as AnimationDrawable
            badgeDrawable?.stop()
        }
    }

    fun setBadgeImage(drawable: Drawable?) {
        mCardView!!.badgeImage = drawable
    }

    /**
     * Set the view in this holder to represent the media metadata in `description`
     *
     */
    fun setupCardView(context: Context, description: MediaDescriptionCompat) {
        mCardView!!.titleText = description.title
        mCardView.contentText = description.subtitle
        mCardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        // Based on state of item, set or unset badge
        val drawable = MediaItemViewHolder.getDrawableByState(context, mItemState)
        mCardView.badgeImage = drawable

        val artUri = description.iconUri
        if (artUri == null) {
            setCardImage(context, description.iconBitmap)
        } else {
            // IconUri potentially has a better resolution than iconBitmap.
            val artUrl = artUri.toString()
            val cache = AlbumArtCache.instance
            if (cache.getBigImage(artUrl) != null) {
                // So, we use it immediately if it's cached:
                setCardImage(context, cache.getBigImage(artUrl))
            } else {
                // Otherwise, we use iconBitmap if available while we wait for iconURI
                setCardImage(context, description.iconBitmap)
                cache.fetch(artUrl, object : AlbumArtCache.FetchListener() {
                    override fun onFetched(artUrl: String, bitmap: Bitmap, icon: Bitmap?) {
                        setCardImage(context, bitmap)
                    }
                })
            }
        }
    }

    private fun setCardImage(context: Context, art: Bitmap?) {
        if (mCardView == null) {
            return
        }
        var artDrawable: Drawable? = null
        if (art != null) {
            artDrawable = BitmapDrawable(context.resources, art)
        } else {
            val title = mCardView.titleText
            if (title != null && title.length > 0) {
                artDrawable = TextDrawable(title[0].toString())
            }
        }
        mCardView.mainImage = artDrawable
    }

    /**
     * Simple drawable that draws a text (letter, in this case). Used with the media title when
     * the MediaDescription has no corresponding album art.
     */
    private class TextDrawable(private val text: String) : Drawable() {
        private val paint: Paint

        init {
            this.paint = Paint()
            paint.color = Color.WHITE
            paint.textSize = 280f
            paint.isAntiAlias = true
            paint.isFakeBoldText = true
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val r = bounds
            val count = canvas.save()
            canvas.translate(r.left.toFloat(), r.top.toFloat())
            val midW = (r.width() / 2).toFloat()
            val midH = r.height() / 2 - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(text, midW, midH, paint)
            canvas.restoreToCount(count)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(cf: ColorFilter?) {
            paint.colorFilter = cf
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }
    }

    companion object {

        private val CARD_WIDTH = 300
        private val CARD_HEIGHT = 250
    }
}