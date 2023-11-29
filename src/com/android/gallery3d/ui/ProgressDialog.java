/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.android.gallery3d.R;

import java.text.NumberFormat;

/**
 * A dialog showing a progress indicator and an optional text message or view.
 * Only a text message or a view can be used at the same time.
 *
 * <p>The dialog can be made cancelable on back key press.</p>
 *
 * <p>The progress range is 0 to {@link #getMax() max}.</p>
 *
 * @deprecated <code>ProgressDialog</code> is a modal dialog, which prevents the
 * user from interacting with the app. Instead of using this class, you should
 * use a progress indicator like {@link android.widget.ProgressBar}, which can
 * be embedded in your app's UI. Alternatively, you can use a
 * <a href="/guide/topics/ui/notifiers/notifications.html">notification</a>
 * to inform the user of the task's progress.
 */

 public class ProgressDialog extends AlertDialog {
    
    
    private ProgressBar mProgress;
    private TextView mMessageView;
    private int mMax;
    private int mProgressVal;
    private int mSecondaryProgressVal;
    private int mIncrementBy;
    private int mIncrementSecondaryBy;
    private Drawable mProgressDrawable;
    private Drawable mIndeterminateDrawable;
    private CharSequence mMessage;
    private boolean mIndeterminate;
    
    private boolean mHasStarted;

    /**
     * Creates a Progress dialog.
     *
     * @param context the parent context
     */
    public ProgressDialog(Context context) {
        super(context);
    }

    /**
     * Creates a Progress dialog.
     *
     * @param context the parent context
     * @param theme the resource ID of the theme against which to inflate
     *              this dialog, or {@code 0} to use the parent
     *              {@code context}'s default alert dialog theme
     */
    public ProgressDialog(Context context, int theme) {
        super(context, theme);
    }


    /**
     * Creates and shows a ProgressDialog.
     *
     * @param context the parent context
     * @param title the title text for the dialog's window
     * @param message the text to be displayed in the dialog
     * @return the ProgressDialog
     */
    public static ProgressDialog show(Context context, CharSequence title,
            CharSequence message) {
        return show(context, title, message, false);
    }

    /**
     * Creates and shows a ProgressDialog.
     *
     * @param context the parent context
     * @param title the title text for the dialog's window
     * @param message the text to be displayed in the dialog
     * @param indeterminate true if the dialog should be {@link #setIndeterminate(boolean)
     *        indeterminate}, false otherwise
     * @return the ProgressDialog
     */
    public static ProgressDialog show(Context context, CharSequence title,
            CharSequence message, boolean indeterminate) {
        return show(context, title, message, indeterminate, false, null);
    }

    /**
     * Creates and shows a ProgressDialog.
     *
     * @param context the parent context
     * @param title the title text for the dialog's window
     * @param message the text to be displayed in the dialog
     * @param indeterminate true if the dialog should be {@link #setIndeterminate(boolean)
     *        indeterminate}, false otherwise
     * @param cancelable true if the dialog is {@link #setCancelable(boolean) cancelable},
     *        false otherwise
     * @return the ProgressDialog
     */
    public static ProgressDialog show(Context context, CharSequence title,
            CharSequence message, boolean indeterminate, boolean cancelable) {
        return show(context, title, message, indeterminate, cancelable, null);
    }

    /**
     * Creates and shows a ProgressDialog.
     *
     * @param context the parent context
     * @param title the title text for the dialog's window
     * @param message the text to be displayed in the dialog
     * @param indeterminate true if the dialog should be {@link #setIndeterminate(boolean)
     *        indeterminate}, false otherwise
     * @param cancelable true if the dialog is {@link #setCancelable(boolean) cancelable},
     *        false otherwise
     * @param cancelListener the {@link #setOnCancelListener(OnCancelListener) listener}
     *        to be invoked when the dialog is canceled
     * @return the ProgressDialog
     */
    public static ProgressDialog show(Context context, CharSequence title,
            CharSequence message, boolean indeterminate,
            boolean cancelable, OnCancelListener cancelListener) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setIndeterminate(indeterminate);
        dialog.setCancelable(cancelable);
        dialog.setOnCancelListener(cancelListener);
        dialog.show();
        return dialog;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.progress_dialog, null);
        mProgress = (ProgressBar) view.findViewById(R.id.progress);
        mMessageView = (TextView) view.findViewById(R.id.message);
        setView(view);
        
        if (mMax > 0) {
            setMax(mMax);
        }
        if (mProgressVal > 0) {
            setProgress(mProgressVal);
        }
        if (mSecondaryProgressVal > 0) {
            setSecondaryProgress(mSecondaryProgressVal);
        }
        if (mIncrementBy > 0) {
            incrementProgressBy(mIncrementBy);
        }
        if (mIncrementSecondaryBy > 0) {
            incrementSecondaryProgressBy(mIncrementSecondaryBy);
        }
        if (mProgressDrawable != null) {
            setProgressDrawable(mProgressDrawable);
        }
        if (mIndeterminateDrawable != null) {
            setIndeterminateDrawable(mIndeterminateDrawable);
        }
        if (mMessage != null) {
            setMessage(mMessage);
        }
        setIndeterminate(mIndeterminate);
        super.onCreate(savedInstanceState);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        mHasStarted = true;
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        mHasStarted = false;
    }

    /**
     * Sets the current progress.
     *
     * @param value the current progress, a value between 0 and {@link #getMax()}
     *
     * @see ProgressBar#setProgress(int)
     */
    public void setProgress(int value) {
        if (mHasStarted) {
            mProgress.setProgress(value);
        } else {
            mProgressVal = value;
        }
    }

    /**
     * Sets the secondary progress.
     *
     * @param secondaryProgress the current secondary progress, a value between 0 and
     * {@link #getMax()}
     *
     * @see ProgressBar#setSecondaryProgress(int)
     */
    public void setSecondaryProgress(int secondaryProgress) {
        if (mProgress != null) {
            mProgress.setSecondaryProgress(secondaryProgress);
        } else {
            mSecondaryProgressVal = secondaryProgress;
        }
    }

    /**
     * Gets the current progress.
     *
     * @return the current progress, a value between 0 and {@link #getMax()}
     */
    public int getProgress() {
        if (mProgress != null) {
            return mProgress.getProgress();
        }
        return mProgressVal;
    }

    /**
     * Gets the current secondary progress.
     *
     * @return the current secondary progress, a value between 0 and {@link #getMax()}
     */
    public int getSecondaryProgress() {
        if (mProgress != null) {
            return mProgress.getSecondaryProgress();
        }
        return mSecondaryProgressVal;
    }

    /**
     * Gets the maximum allowed progress value. The default value is 100.
     *
     * @return the maximum value
     */
    public int getMax() {
        if (mProgress != null) {
            return mProgress.getMax();
        }
        return mMax;
    }

    /**
     * Sets the maximum allowed progress value.
     */
    public void setMax(int max) {
        if (mProgress != null) {
            mProgress.setMax(max);
        } else {
            mMax = max;
        }
    }

    /**
     * Increments the current progress value.
     *
     * @param diff the amount by which the current progress will be incremented,
     * up to {@link #getMax()}
     */
    public void incrementProgressBy(int diff) {
        if (mProgress != null) {
            mProgress.incrementProgressBy(diff);
        } else {
            mIncrementBy += diff;
        }
    }

    /**
     * Increments the current secondary progress value.
     *
     * @param diff the amount by which the current secondary progress will be incremented,
     * up to {@link #getMax()}
     */
    public void incrementSecondaryProgressBy(int diff) {
        if (mProgress != null) {
            mProgress.incrementSecondaryProgressBy(diff);
        } else {
            mIncrementSecondaryBy += diff;
        }
    }

    /**
     * Sets the drawable to be used to display the progress value.
     *
     * @param d the drawable to be used
     *
     * @see ProgressBar#setProgressDrawable(Drawable)
     */
    public void setProgressDrawable(Drawable d) {
        if (mProgress != null) {
            mProgress.setProgressDrawable(d);
        } else {
            mProgressDrawable = d;
        }
    }

    /**
     * Sets the drawable to be used to display the indeterminate progress value.
     *
     * @param d the drawable to be used
     *
     * @see ProgressBar#setProgressDrawable(Drawable)
     * @see #setIndeterminate(boolean)
     */
    public void setIndeterminateDrawable(Drawable d) {
        if (mProgress != null) {
            mProgress.setIndeterminateDrawable(d);
        } else {
            mIndeterminateDrawable = d;
        }
    }

    /**
     * Change the indeterminate mode for this ProgressDialog. In indeterminate
     * mode, the progress is ignored and the dialog shows an infinite
     * animation instead.
     *
     * <p><strong>Note:</strong> A ProgressDialog with style {@link #STYLE_SPINNER}
     * is always indeterminate and will ignore this setting.</p>
     *
     * @param indeterminate true to enable indeterminate mode, false otherwise
     *
     * @see #setProgressStyle(int)
     */
    public void setIndeterminate(boolean indeterminate) {
        if (mProgress != null) {
            mProgress.setIndeterminate(indeterminate);
        } else {
            mIndeterminate = indeterminate;
        }
    }

    /**
     * Whether this ProgressDialog is in indeterminate mode.
     *
     * @return true if the dialog is in indeterminate mode, false otherwise
     */
    public boolean isIndeterminate() {
        if (mProgress != null) {
            return mProgress.isIndeterminate();
        }
        return mIndeterminate;
    }
    
    @Override
    public void setMessage(CharSequence message) {
        if (mProgress != null) {
            mMessageView.setText(message);
        } else {
            mMessage = message;
        }
    }

}
