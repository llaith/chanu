package com.chanapps.four.loader;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.chanapps.four.activity.BoardSelectorActivity;
import com.chanapps.four.activity.R;
import com.chanapps.four.data.*;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class BoardSelectorCursorLoader extends AsyncTaskLoader<Cursor> {

    private static final String TAG = BoardSelectorCursorLoader.class.getSimpleName();

    protected final ForceLoadContentObserver mObserver;

    protected Cursor mCursor;
    protected Context context;
    protected ChanBoard.Type boardType;

    public BoardSelectorCursorLoader(Context context, ChanBoard.Type boardType) {
        super(context);
        this.context = context;
        this.boardType = boardType;
        mObserver = new ForceLoadContentObserver();
    }

    /* Runs on a worker thread */
    @Override
    public Cursor loadInBackground() {
        Log.d(BoardSelectorActivity.TAG, "Loading boardType=" + boardType);

        List<ChanBoard> boards = ChanBoard.getBoardsByType(context, boardType);
        if (boards == null || boards.isEmpty()) {
            Log.e(TAG, "Null board list, something went wrong for boardType=" + boardType);
            return null; // shouldn't happen
        }
        Log.i(TAG, "Creating board selector cursor for boardType=" + boardType);
        MatrixCursor cursor = new MatrixCursor(ChanHelper.SELECTOR_COLUMNS);
        for (ChanBoard board : boards) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            row.add(board.link.hashCode());
            row.add(board.link);
            row.add(getImageResourceId(board.link));
            row.add(board.name);
        }

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
        }

        registerContentObserver(cursor, mObserver);
  		return cursor;
    }

    private int getImageResourceId(String boardCode) {
        int imageId = 0;
        try {
            imageId = R.drawable.class.getField(boardCode).getInt(null);
        } catch (Exception e) {
            try {
                imageId = R.drawable.class.getField("board_" + boardCode).getInt(null);
            } catch (Exception e1) {
                imageId = R.drawable.stub_image;
            }
        }
        Log.v(BoardSelectorActivity.TAG, "Found image for board " + boardCode + " image Id: " + imageId);
        return imageId;
    }

    /**
     * Registers an observer to get notifications from the content provider
     * when the cursor needs to be refreshed.
     */
    void registerContentObserver(Cursor cursor, ContentObserver observer) {
        cursor.registerContentObserver(mObserver);
    }

    /* Runs on the UI thread */
    @Override
    public void deliverResult(Cursor cursor) {
		Log.i(TAG, "deliverResult isReset(): " + isReset());
        if (isReset()) {
            // An async query came in while the loader is stopped
            if (cursor != null) {
                cursor.close();
            }
            return;
        }
        Cursor oldCursor = mCursor;
        mCursor = cursor;

        if (isStarted()) {
            super.deliverResult(cursor);
        }

        if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
            oldCursor.close();
        }
    }

    /**
     * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
     * will be called on the UI thread. If a previous load has been completed and is still valid
     * the result may be passed to the callbacks immediately.
     *
     * Must be called from the UI thread
     */
    @Override
    protected void onStartLoading() {
    	Log.i(TAG, "onStartLoading mCursor: " + mCursor);
        if (mCursor != null) {
            deliverResult(mCursor);
        }
        if (takeContentChanged() || mCursor == null) {
            forceLoad();
        }
    }

    /**
     * Must be called from the UI thread
     */
    @Override
    protected void onStopLoading() {
    	Log.i(TAG, "onStopLoading");
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    public void onCanceled(Cursor cursor) {
    	Log.i(TAG, "onCanceled cursor: " + cursor);
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    @Override
    protected void onReset() {
        super.onReset();
        Log.i(TAG, "onReset cursor: " + mCursor);
        // Ensure the loader is stopped
        onStopLoading();

        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
        mCursor = null;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.print(prefix); writer.print("mCursor="); writer.println(mCursor);
    }
}
