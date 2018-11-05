package com.alamkanak.weekview.drawing;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.MotionEvent;

import com.alamkanak.weekview.model.WeekViewConfig;
import com.alamkanak.weekview.model.WeekViewEvent;

import static android.text.Layout.Alignment.ALIGN_NORMAL;

/**
 * A class to hold reference to the events and their visual representation. An EventRect is
 * actually the rectangle that is drawn on the calendar for a given event. There may be more
 * than one rectangle for a single event (an event that expands more than one day). In that
 * case two instances of the EventRect will be used for a single event. The given event will be
 * stored in "originalEvent". But the event that corresponds to rectangle the rectangle
 * instance will be stored in "event".
 */
public class EventChip<T> {

    public WeekViewEvent<T> event;
    public WeekViewEvent<T> originalEvent;

    public RectF rect;
    public float left;
    public float width;
    public float top;
    public float bottom;

    /**
     * Create a new instance of event rect. An EventRect is actually the rectangle that is drawn
     * on the calendar for a given event. There may be more than one rectangle for a single
     * event (an event that expands more than one day). In that case two instances of the
     * EventRect will be used for a single event. The given event will be stored in
     * "originalEvent". But the event that corresponds to rectangle the rectangle instance will
     * be stored in "event".
     *
     * @param event         Represents the event which this instance of rectangle represents.
     * @param originalEvent The original event that was passed by the user.
     * @param rect         The rectangle.
     */
    public EventChip(WeekViewEvent<T> event, WeekViewEvent<T> originalEvent, RectF rect) {
        this.event = event;
        this.rect = rect;
        this.originalEvent = originalEvent;
    }

    public void draw(WeekViewConfig config, Canvas canvas) {
        draw(config, null, canvas);
    }

    public void draw(WeekViewConfig config, @Nullable StaticLayout textLayout, Canvas canvas) {
        float cornerRadius = config.eventCornerRadius;
        Paint backgroundPaint = getBackgroundPaint();
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint);

        if (textLayout != null) {
            // The text height has already been calculated
            drawEventTitle(config, textLayout, canvas);
        } else {
            calculateTextHeightAndDrawTitle(config, canvas);
        }
    }

    private Paint getBackgroundPaint() {
        Paint paint = new Paint();
        paint.setColor(event.getColorOrDefault());
        return paint;
    }

    private void calculateTextHeightAndDrawTitle(WeekViewConfig config, Canvas canvas) {
        boolean negativeWidth = (rect.right - rect.left - config.eventPadding * 2) < 0;
        boolean negativeHeight = (rect.bottom - rect.top - config.eventPadding * 2) < 0;
        if (negativeWidth || negativeHeight) {
            return;
        }

        // Prepare the name of the event.
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        if (event.getTitle() != null) {
            stringBuilder.append(event.getTitle());
            stringBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, stringBuilder.length(), 0);
        }

        // Prepare the location of the event.
        if (event.getLocation() != null) {
            stringBuilder.append(' ');
            stringBuilder.append(event.getLocation());
        }

        int availableHeight = (int) (rect.bottom - rect.top - config.eventPadding * 2);
        int availableWidth = (int) (rect.right - rect.left - config.eventPadding * 2);

        // Get text dimensions.
        final TextPaint textPaint = config.drawingConfig.eventTextPaint;
        StaticLayout textLayout = new StaticLayout(stringBuilder,
                textPaint, availableWidth, ALIGN_NORMAL, 1.0f, 0.0f, false);

        int lineHeight = textLayout.getHeight() / textLayout.getLineCount();

        if (availableHeight >= lineHeight) {
            int availableLineCount = availableHeight / lineHeight;
            do {
                // TODO: Don't truncate
                // Ellipsize text to fit into event rect.
                int availableArea = availableLineCount * availableWidth;
                CharSequence ellipsized = TextUtils.ellipsize(stringBuilder,
                        textPaint, availableArea, TextUtils.TruncateAt.END);

                final int width = (int) (rect.right - rect.left - config.eventPadding * 2);
                textLayout = new StaticLayout(ellipsized, textPaint, width, ALIGN_NORMAL, 1.0f, 0.0f, false);

                // Repeat until text is short enough.
                availableLineCount--;
            } while (textLayout.getHeight() > availableHeight);

            // Draw text.
            drawEventTitle(config, textLayout, canvas);
        }
    }

    private void drawEventTitle(WeekViewConfig config, StaticLayout textLayout, Canvas canvas) {
        canvas.save();
        canvas.translate(rect.left + config.eventPadding, rect.top + config.eventPadding);
        textLayout.draw(canvas);
        canvas.restore();
    }

    public boolean isHit(MotionEvent e) {
        if (rect == null) {
            return false;
        }

        return e.getX() > rect.left
                && e.getX() < rect.right
                && e.getY() > rect.top
                && e.getY() < rect.bottom;
    }

}