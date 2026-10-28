package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.model.PlaybackState
import kotlin.math.abs

/**
 * Manages the floating Dynamic Island overlay displayed over other apps when exiting Aura Music.
 * Idle state: Small neat circle with musical indicator.
 * Tapped state: Enlarges into the dynamic island pill with controls.
 */
class FloatingIslandManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var isExpanded = false
    private var isAttached = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 36 // Slightly below the status bar
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun updateState(state: PlaybackState) {
        val track = state.currentTrack
        if (!state.isDynamicIslandEnabled || track == null || !canDrawOverlays()) {
            removeOverlay()
            return
        }

        if (!isAttached) {
            createAndAttachOverlay(state)
        } else {
            refreshViewContent(state)
        }
    }

    private fun createAndAttachOverlay(state: PlaybackState) {
        try {
            val root = FrameLayout(context).apply {
                clipToOutline = true
            }

            setupTouchListener(root)
            overlayView = root
            refreshViewContent(state)

            windowManager.addView(root, layoutParams)
            isAttached = true
        } catch (e: Exception) {
            Log.w("FloatingIslandManager", "Failed to add overlay window: ${e.message}")
            isAttached = false
            overlayView = null
        }
    }

    private fun refreshViewContent(state: PlaybackState) {
        val root = overlayView ?: return
        root.removeAllViews()

        val density = context.resources.displayMetrics.density
        val track = state.currentTrack ?: return

        if (!isExpanded) {
            // Small Circle (44dp x 44dp)
            val circleSize = (44 * density).toInt()
            val circleContainer = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF241C18.toInt()) // Deep Cocoa
                    setStroke((1.5 * density).toInt(), 0xFFD4A373.toInt()) // Caramel Gold border
                }
            }

            val icon = ImageView(context).apply {
                val iconSize = (20 * density).toInt()
                val lp = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                layoutParams = lp
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(0xFFD4A373.toInt())
            }
            circleContainer.addView(icon)

            root.addView(circleContainer)
        } else {
            // Enlarged Dynamic Island Pill (~320dp wide, ~72dp high)
            val pillWidth = (320 * density).toInt()
            val pillPadding = (10 * density).toInt()

            val islandContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(pillWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
                setPadding(pillPadding, pillPadding, pillPadding, pillPadding)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24 * density
                    setColor(0xFF1C1512.toInt()) // Deep Warm Brown
                    setStroke((1.5 * density).toInt(), 0xFFD4A373.toInt())
                }
            }

            // Disc thumbnail
            val discThumb = ImageView(context).apply {
                val thumbSize = (36 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(thumbSize, thumbSize).apply {
                    marginEnd = (10 * density).toInt()
                }
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(0xFFD4A373.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF2E231D.toInt())
                }
            }
            islandContainer.addView(discThumb)

            // Text Info (Title & Artist)
            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            val titleTv = TextView(context).apply {
                text = track.title
                setTextColor(0xFFF7EBE1.toInt()) // Cream text
                textSize = 12f
                isSingleLine = true
            }
            val artistTv = TextView(context).apply {
                text = track.artist
                setTextColor(0xFFB8A495.toInt()) // Muted Sand
                textSize = 10f
                isSingleLine = true
            }
            textLayout.addView(titleTv)
            textLayout.addView(artistTv)

            // Tap text layout to open app
            textLayout.setOnClickListener {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(openIntent)
            }
            islandContainer.addView(textLayout)

            // Controls: Prev, Play/Pause, Next, Collapse
            val prevBtn = ImageView(context).apply {
                val btnSize = (28 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    marginEnd = (4 * density).toInt()
                }
                setImageResource(android.R.drawable.ic_media_previous)
                setColorFilter(0xFFF7EBE1.toInt())
                setOnClickListener { PlaybackManager.previous(context) }
            }
            islandContainer.addView(prevBtn)

            val playPauseBtn = ImageView(context).apply {
                val btnSize = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    marginEnd = (4 * density).toInt()
                }
                setImageResource(if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                setColorFilter(0xFF140F0D.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFD4A373.toInt())
                }
                setOnClickListener { PlaybackManager.togglePlayPause(context) }
            }
            islandContainer.addView(playPauseBtn)

            val nextBtn = ImageView(context).apply {
                val btnSize = (28 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    marginEnd = (6 * density).toInt()
                }
                setImageResource(android.R.drawable.ic_media_next)
                setColorFilter(0xFFF7EBE1.toInt())
                setOnClickListener { PlaybackManager.next(context) }
            }
            islandContainer.addView(nextBtn)

            // Collapse to circle button
            val collapseBtn = ImageView(context).apply {
                val btnSize = (22 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(0xFFB8A495.toInt())
                setOnClickListener {
                    isExpanded = false
                    refreshViewContent(PlaybackManager.playbackState.value)
                }
            }
            islandContainer.addView(collapseBtn)

            root.addView(islandContainer)
        }

        try {
            windowManager.updateViewLayout(root, layoutParams)
        } catch (_: Exception) {}
    }

    private fun setupTouchListener(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // It was a tap!
                        isExpanded = !isExpanded
                        refreshViewContent(PlaybackManager.playbackState.value)
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun removeOverlay() {
        if (isAttached && overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {}
            isAttached = false
            overlayView = null
            isExpanded = false
        }
    }
}
