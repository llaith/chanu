/**
 * 4Channer
 */
package com.chanapps.four.data;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.CursorAdapter;
import android.widget.Toast;
import com.chanapps.four.activity.R;

/**
 * @author "Grzegorz Nittner" <grzegorz.nittner@gmail.com>
 *
 */
public class ChanDatabaseHelper extends SQLiteOpenHelper {
	static final String TAG = "ChanDatabaseHelper";
	
	public static final String DB_NAME = "4Channer";
	public static final int DB_VERSION = 6;
	
	public static final String POST_TABLE = "post";
	public static final String POST_ID = "_id";
	public static final String POST_BOARD_NAME = "board_name";
	public static final String POST_NOW = "now";
	public static final String POST_TIME = "time";
	public static final String POST_NAME = "name";
	public static final String POST_SUB = "sub";
    public static final String POST_COM = "com";
	public static final String POST_TIM = "tim";
	public static final String POST_FILENAME = "filename";
	public static final String POST_EXT = "ext";
	public static final String POST_W = "w";
	public static final String POST_H = "h";
	public static final String POST_TN_W = "tn_w";
	public static final String POST_TN_H = "tn_h";
	public static final String POST_FSIZE = "fsize";
	public static final String POST_RESTO = "resto";
	public static final String POST_LAST_UPDATE = "last_update";

    public static final String POST_TEXT = "text"; // we construct and filter this

	// version 2
	public static final String POST_STICKY = "sticky";
	public static final String POST_CLOSED = "closed";
	public static final String POST_OMITTED_POSTS = "omitted_posts";
	public static final String POST_OMITTED_IMAGES = "omitted_images";
	
	public ChanDatabaseHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION, new ErrorHandler());
	}

    public static SQLiteDatabase openDatabaseIfNecessary(Context context, SQLiteDatabase db) {
   		try {
   			if (db == null || !db.isOpen()) {
   				Log.i(TAG, "Opening Chan database");
   				db = new ChanDatabaseHelper(context).getReadableDatabase();
   			}
   		} catch (SQLException se) {
   			Log.e(TAG, "Cannot open database", se);
               Toast.makeText(context, R.string.board_activity_couldnt_open_db, Toast.LENGTH_SHORT).show();
   			db = null;
   		}
           return db;
   	}

   	public static SQLiteDatabase closeDatabase(CursorAdapter adapter, SQLiteDatabase db) {
   		try {
   			adapter.swapCursor(null);
   			if (db != null) {
   				db.close();
   			}
   		} finally {
   			db = null;
   		}
           return db;
   	}

	@Override
	public void onOpen(SQLiteDatabase db) {
	    super.onOpen(db);
	    if (!db.isReadOnly()) {
	        // Enable foreign key constraints
	        db.execSQL("PRAGMA foreign_keys=ON;");
	    }
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
	    createDb(db);
	}

    public void createDb(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + POST_TABLE + " ("
   				+ POST_ID + " INTEGER PRIMARY KEY, "
   				+ POST_RESTO + " INTEGER NOT NULL, "
   				+ POST_BOARD_NAME + " TEXT NOT NULL, "
   				+ POST_NAME + " TEXT, "
   				+ POST_NOW + " TEXT, "
   				+ POST_TIME + " INTEGER NOT NULL, "
   				+ POST_SUB + " TEXT, "
   				+ POST_COM + " TEXT, "
   				+ POST_TIM + " INTEGER, "
   				+ POST_FILENAME + " TEXT, "
   				+ POST_EXT + " TEXT, "
   				+ POST_W + " INTEGER, "
   				+ POST_H + " INTEGER, "
   				+ POST_TN_W + " INTEGER, "
   				+ POST_TN_H + " INTEGER, "
   				+ POST_FSIZE + " INTEGER, "
   				+ POST_LAST_UPDATE + " INTEGER, "
                   + POST_TEXT + " TEXT "
   				+ ");";
   		Log.e(TAG, "Executing: " + sql);
   		db.execSQL(sql);

   		sql = "CREATE INDEX " + POST_TABLE + "_resto_board " + "ON " + POST_TABLE + "(" + POST_RESTO + ", " + POST_BOARD_NAME + ");";
   		Log.e(TAG, "Executing: " + sql);
   		db.execSQL(sql);

   		sql = "CREATE INDEX " + POST_TABLE + "_resto_time " + "ON " + POST_TABLE + "(" + POST_RESTO + ", " + POST_TIME + ");";
   		Log.e(TAG, "Executing: " + sql);
   		db.execSQL(sql);
    }

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO migrate script
        try {
            dropDb(db);
        }
        catch (Exception e) {
            Log.e(TAG, "Error while dropping db: " + e.getMessage(), e);
        }
        createDb(db);
	}

    private void dropDb(SQLiteDatabase db) {
        String sql = "DROP INDEX " + POST_TABLE + "_resto_time";
        Log.e(TAG, "Executing: " + sql);
        db.execSQL(sql);

        sql = "DROP INDEX " + POST_TABLE + "_resto_board";
        Log.e(TAG, "Executing: " + sql);
        db.execSQL(sql);

        sql = "DROP TABLE " + POST_TABLE;
        Log.e(TAG, "Executing: " + sql);
        db.execSQL(sql);
    }

	
	static class ErrorHandler implements DatabaseErrorHandler {
		@Override
		public void onCorruption(SQLiteDatabase dbObj) {
			Log.e(TAG, "Database corruption!!!");
		}
	}
}