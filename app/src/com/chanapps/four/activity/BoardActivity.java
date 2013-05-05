package com.chanapps.four.activity;

import java.util.Date;
import java.util.List;

import android.app.ActionBar;
import android.app.Activity;
import android.app.SearchManager;
import android.graphics.Typeface;
import android.support.v4.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.util.DisplayMetrics;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;

import com.chanapps.four.adapter.AbstractBoardCursorAdapter;
import com.chanapps.four.adapter.BoardGridCursorAdapter;
import com.chanapps.four.adapter.BoardListCursorAdapter;
import com.chanapps.four.component.*;
import com.chanapps.four.data.*;
import com.chanapps.four.data.ChanHelper.LastActivity;
import com.chanapps.four.handler.LoaderHandler;
import com.chanapps.four.loader.BoardCursorLoader;
import com.chanapps.four.loader.ChanImageLoader;
import com.chanapps.four.service.NetworkProfileManager;
import com.chanapps.four.service.profile.NetworkProfile;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.assist.PauseOnScrollListener;

public class BoardActivity
        extends FragmentActivity
        implements ClickableLoaderActivity, ChanIdentifiedActivity, RefreshableActivity, OnClickListener
{
	public static final String TAG = BoardActivity.class.getSimpleName();
	public static final boolean DEBUG = false;
    public static final int LOADER_RESTART_INTERVAL_SHORT_MS = 250;
    private static final int THUMB_WIDTH_PX = 150;
    private static final int THUMB_HEIGHT_PX = 150;

    protected AbstractBoardCursorAdapter adapter;
    protected AbsListView absListView;
    protected Class absListViewClass = GridView.class;
    protected Handler handler;
    protected BoardCursorLoader cursorLoader;
    protected int scrollOnNextLoaderFinished = -1;
    protected ImageLoader imageLoader;
    protected DisplayImageOptions displayImageOptions;
    protected Menu menu;
    protected SharedPreferences prefs;
    protected long tim;
    protected String boardCode;
    protected String query = "";
    protected int columnWidth = 0;
    protected int columnHeight = 0;
    protected MenuItem searchMenuItem;
    protected ViewType viewType = ViewType.AS_GRID;
    protected Typeface subjectTypeface = null;
    protected int padding4DP = 0;
    protected int padding8DP = 0;

    public enum ViewType {
        AS_GRID,
        AS_LIST
    }

    public static void startActivity(Activity from, String boardCode) {
        from.startActivity(createIntentForActivity(from, boardCode));
    }

    public static void startActivityForSearch(Activity from, String boardCode, String query) {
        from.startActivity(createIntentForActivity(from, boardCode, query));
    }

    public static Intent createIntentForActivity(Context context, String boardCode) {
        return createIntentForActivity(context, boardCode, "");
    }

    public static Intent createIntentForActivity(Context context, String boardCode, String query) {
        String intentBoardCode = boardCode == null || boardCode.isEmpty() ? ChanBoard.DEFAULT_BOARD_CODE : boardCode;
        Intent intent = new Intent(context, BoardActivity.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(ChanHelper.BOARD_CODE, intentBoardCode);
        intent.putExtra(ChanHelper.PAGE, 0);
        intent.putExtra(SearchManager.QUERY, query);
        intent.putExtra(ChanHelper.LAST_BOARD_POSITION, 0);
        intent.putExtra(ChanHelper.FROM_PARENT, true);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putInt(ChanHelper.LAST_BOARD_POSITION, 0); // reset it
        editor.commit();
        return intent;
    }

    protected void initImageLoader() {
        imageLoader = ChanImageLoader.getInstance(getApplicationContext());
        resetImageOptions(new ImageSize(THUMB_WIDTH_PX, THUMB_HEIGHT_PX)); // view pager needs micro images
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "************ onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        query = getIntent().hasExtra(SearchManager.QUERY)
                ? getIntent().getStringExtra(SearchManager.QUERY)
                : "";
        //loadFromIntentOrPrefs();
        initImageLoader();
        createAbsListView();
        ensureHandler();
        ensureSubjectTypeface();
        initPaddings();
        LoaderManager.enableDebugLogging(true);
        //getSupportLoaderManager().restartLoader(0, null, this);
        if (DEBUG) Log.v(TAG, "onCreate init loader");
        //progressBar = (ProgressBar)findViewById(R.id.board_progress_bar);
    }

    protected void initPaddings() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        padding4DP = ChanGridSizer.dpToPx(metrics, 4);
        padding8DP = ChanGridSizer.dpToPx(metrics, 8);
    }

    protected void sizeGridToDisplay() {
        Display display = getWindowManager().getDefaultDisplay();
        ChanGridSizer cg = new ChanGridSizer(absListView, display, ChanGridSizer.ServiceType.BOARD);
        cg.sizeGridToDisplay();
        columnWidth = cg.getColumnWidth();
        columnHeight = cg.getColumnHeight();
    }

    protected void initAdapter() {
        if (absListView instanceof GridView)
        adapter = new BoardGridCursorAdapter(this,
                R.layout.board_grid_item,
                this,
                new String[] {
                        ChanThread.THREAD_THUMBNAIL_URL,
                        ChanThread.THREAD_TITLE,
                        ChanThread.THREAD_SUBJECT,
                        ChanThread.THREAD_HEADLINE,
                        ChanThread.THREAD_TEXT,
                        ChanThread.THREAD_COUNTRY_FLAG_URL,
                        ChanThread.THREAD_NUM_REPLIES,
                        ChanThread.THREAD_NUM_IMAGES
                },
                new int[] {
                        R.id.grid_item_thread_thumb,
                        R.id.grid_item_thread_title,
                        R.id.grid_item_thread_subject,
                        R.id.grid_item_thread_headline,
                        R.id.grid_item_thread_text,
                        R.id.grid_item_country_flag,
                        R.id.grid_item_num_replies,
                        R.id.grid_item_num_images
                },
                columnWidth,
                columnHeight);
        else
            adapter = new BoardListCursorAdapter(this,
                    R.layout.board_list_item,
                    this,
                    new String[] {
                            ChanThread.THREAD_THUMBNAIL_URL,
                            ChanThread.THREAD_TITLE,
                            ChanThread.THREAD_SUBJECT,
                            ChanThread.THREAD_HEADLINE,
                            ChanThread.THREAD_TEXT,
                            ChanThread.THREAD_COUNTRY_FLAG_URL
                    },
                    new int[] {
                            R.id.grid_item_thread_thumb,
                            R.id.grid_item_thread_title,
                            R.id.grid_item_thread_subject,
                            R.id.grid_item_thread_headline,
                            R.id.grid_item_thread_text,
                            R.id.grid_item_country_flag
                    });
        absListView.setAdapter(adapter);
    }

    protected int getLayoutId() {
        if (GridView.class.equals(absListViewClass))
            return R.layout.board_grid_layout;
        else
            return R.layout.board_list_layout;
    }

    protected void setAbsListViewClass() { // override to change
        if (hasQuery()) viewType = ViewType.AS_LIST;
        absListViewClass = viewType == ViewType.AS_LIST ? ListView.class : GridView.class;
    }

    protected boolean hasQuery() {
        String searchQuery = getIntent() == null ? null : getIntent().getStringExtra(SearchManager.QUERY);
        return searchQuery != null && !searchQuery.isEmpty();
    }

    protected void resetImageOptions(ImageSize imageSize) {
        displayImageOptions = new DisplayImageOptions.Builder()
                .cacheOnDisc()
                .cacheInMemory()
                .resetViewBeforeLoading()
                .build();
    }

    protected void initAbsListView() {
        if (GridView.class.equals(absListViewClass)) {
            absListView = (GridView)findViewById(R.id.board_grid_view);
            sizeGridToDisplay();
            resetImageOptions(new ImageSize(columnWidth, columnHeight));
        }
        else {
            absListView = (ListView)findViewById(R.id.board_list_view);
        }
    }

    protected void createAbsListView() {
        setAbsListViewClass();
        setContentView(getLayoutId());
        initAbsListView();
        initAdapter();
        absListView.setClickable(true);
        absListView.setOnItemClickListener(this);
        absListView.setLongClickable(false);
        absListView.setOnScrollListener(new PauseOnScrollListener(imageLoader, true, true));
    }

    protected synchronized Handler ensureHandler() {
        if (handler == null && ChanHelper.onUIThread()) {
        	handler = new LoaderHandler(this);
        }
        return handler;
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensureHandler();
		if (DEBUG) Log.v(TAG, "onStart query=" + query);
    }

	@Override
	protected void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume query=" + query);
        ensureHandler();
        restoreInstanceState();
		NetworkProfileManager.instance().activityChange(this);
		getSupportLoaderManager().restartLoader(0, null, this);
	}

    protected String getLastPositionName() {
        return ChanHelper.LAST_BOARD_POSITION;
    }

    protected void scrollToLastPosition() {
        String intentExtra = getLastPositionName();
        int lastPosition = getIntent().getIntExtra(intentExtra, -1);
        if (lastPosition == -1) {
            lastPosition = ensurePrefs().getInt(intentExtra, -1);
        }
        if (lastPosition == -1) {
            lastPosition = 0;
        }
        if (lastPosition <= absListView.getCount()) { // we can scroll now
            absListView.smoothScrollToPosition(lastPosition);
            absListView.smoothScrollToPosition(0);
            scrollOnNextLoaderFinished = -1;
        }
        else { // scroll next time when list is loaded
            scrollOnNextLoaderFinished = lastPosition;
        }
        if (DEBUG) Log.v(TAG, "Scrolling to:" + lastPosition);
    }

    @Override
	public void onWindowFocusChanged (boolean hasFocus) {
		if (DEBUG) Log.v(TAG, "onWindowFocusChanged hasFocus: " + hasFocus);
	}

    @Override
	protected void onPause() {
        super.onPause();
        if (DEBUG) Log.v(TAG, "onPause query=" + query);
        saveInstanceState();
        handler = null;
    }

    protected SharedPreferences ensurePrefs() {
        if (prefs == null)
            prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs;
    }

    protected void loadFromIntentOrPrefs() {
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            List<String> params = data.getPathSegments();
            String uriBoardCode = params.get(0);
            if (ChanBoard.getBoardByCode(this, uriBoardCode) != null) {
                boardCode = uriBoardCode;
                if (DEBUG) Log.i(TAG, "loaded boardCode=" + boardCode + " from url intent");
            }
            else {
                if (DEBUG) Log.e(TAG, "Received invalid boardCode=" + uriBoardCode + " from url intent, ignoring");
            }
        }
        else if (intent.hasExtra(ChanHelper.BOARD_CODE)) {
            boardCode = intent.getStringExtra(ChanHelper.BOARD_CODE);
            if (DEBUG) Log.i(TAG, "loaded boardCode=" + boardCode + " from board code intent");
        }
        else {
            boardCode = ensurePrefs().getString(ChanHelper.BOARD_CODE, ChanBoard.DEFAULT_BOARD_CODE);
            if (DEBUG) Log.i(TAG, "loaded boardCode=" + boardCode + " from prefs or default");
        }
        query = intent.hasExtra(SearchManager.QUERY)
                ? intent.getStringExtra(SearchManager.QUERY)
                : prefs.getString(SearchManager.QUERY, "");
        // backup in case we are missing stuff
        if (boardCode == null || boardCode.isEmpty()) {
            Log.e(TAG, "Empty board code, redirecting to board selector");
            Intent selectorIntent = new Intent(this, BoardSelectorActivity.class);
            selectorIntent.putExtra(ChanHelper.BOARD_TYPE, BoardType.JAPANESE_CULTURE.toString());
            selectorIntent.putExtra(ChanHelper.IGNORE_DISPATCH, true);
            selectorIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(selectorIntent);
            finish();
        }
    }

    protected void restoreInstanceState() {
        if (DEBUG) Log.i(TAG, "Restoring instance state... query=" + query);
        loadFromIntentOrPrefs();
        setActionBarTitle();
        //scrollToLastPosition();
        invalidateOptionsMenu(); // for correct spinner display
    }

    protected void saveInstanceState() {
        if (DEBUG) Log.i(TAG, "Saving instance state...");
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(ChanHelper.BOARD_CODE, boardCode);
        editor.putLong(ChanHelper.THREAD_NO, 0);
        editor.putInt(ChanHelper.LAST_BOARD_POSITION, absListView.getFirstVisiblePosition());
        editor.commit();
        DispatcherHelper.saveActivityToPrefs(this);
    }

    @Override
    protected void onStop () {
    	super.onStop();
    	if (DEBUG) Log.v(TAG, "onStop query=" + query);
    	getLoaderManager().destroyLoader(0);
    	handler = null;
    }

    @Override
	protected void onDestroy () {
		super.onDestroy();
		if (DEBUG) Log.v(TAG, "onDestroy query=" + query);
		getLoaderManager().destroyLoader(0);
		handler = null;
	}

    @Override
    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        return setViewValue(view, cursor, columnIndex, imageLoader, displayImageOptions,
                boardCode, viewType, subjectTypeface, padding4DP);
    }

    public static boolean setViewValue(View view,
                                       Cursor cursor,
                                       int columnIndex,
                                       ImageLoader imageLoader,
                                       DisplayImageOptions options,
                                       String groupBoardCode,
                                       ViewType viewType,
                                       Typeface subjectTypeface,
                                       int padding4DP)
    {
        int flags = cursor.getInt(cursor.getColumnIndex(ChanThread.THREAD_FLAGS));
        if ((flags & ChanThread.THREAD_FLAG_TITLE) > 0) { // special case it to avoid needing a separate item layout
            return setThreadTitleView(view, cursor, flags, viewType);
        }
        view.setVisibility(View.VISIBLE);
        switch (view.getId()) {
            case R.id.list_item:
                return setThreadItem(view);
            case R.id.grid_item_board_abbrev:
                return setThreadBoardAbbrev((TextView) view, cursor, groupBoardCode, flags);
            case R.id.grid_item_thread_title:
                return setThreadTitle((TextView) view, cursor, flags, viewType);
            case R.id.grid_item_thread_subject:
                return setThreadSubject((TextView) view, cursor, viewType, subjectTypeface);
            case R.id.grid_item_thread_headline:
                return setThreadHeadline((TextView) view, cursor, padding4DP);
            case R.id.grid_item_thread_text:
                return setThreadText((TextView) view, cursor);
            case R.id.grid_item_thread_thumb:
                return setThreadThumb((ImageView) view, cursor, imageLoader, options, flags);
            case R.id.grid_item_country_flag:
                return setCountryFlag((ImageView) view, cursor, imageLoader, options);
            case R.id.grid_item_num_replies:
                return setThreadNumReplies((TextView) view, cursor, flags);
            case R.id.grid_item_num_images:
                return setThreadNumImages((TextView) view, cursor, flags);
        }
        return false;
    }

    protected static boolean setThreadTitleView(View view, Cursor cursor, int flags, ViewType viewType) {
        if (view.getId() == R.id.grid_item_thread_title) {
            setThreadTitle((TextView) view, cursor, flags, viewType);
            view.setVisibility(View.VISIBLE);
        }
        else if (view.getId() == R.id.grid_item) {
            view.setVisibility(View.VISIBLE);
        }
        else if (view.getId() == R.id.list_item) {
            view.setVisibility(View.VISIBLE);
            View v = view.findViewById(R.id.grid_item_thread_image_wrapper);
            if (v != null)
                v.setVisibility(View.GONE);
        }
        else {
            view.setVisibility(View.GONE);
        }
        return true;
    }

    protected static boolean setThreadItem(View view) {
        View v = view.findViewById(R.id.grid_item_thread_image_wrapper);
        if (v != null)
            v.setVisibility(View.VISIBLE);
        return true;
    }

    protected static boolean setThreadBoardAbbrev(TextView tv, Cursor cursor, String groupBoardCode, int flags) {
        String threadAbbrev = "";
        String boardCode = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_BOARD_CODE));
        if (boardCode != null && !boardCode.isEmpty() && !boardCode.equals(groupBoardCode))
            threadAbbrev += "/" + boardCode + "/";
        if ((flags & ChanThread.THREAD_FLAG_DEAD) > 0)
            threadAbbrev += (threadAbbrev.isEmpty()?"":" ") + tv.getContext().getString(R.string.dead_thread_abbrev);
        if ((flags & ChanThread.THREAD_FLAG_CLOSED) > 0)
            threadAbbrev += (threadAbbrev.isEmpty()?"":" ") + tv.getContext().getString(R.string.closed_thread_abbrev);
        if ((flags & ChanThread.THREAD_FLAG_STICKY) > 0)
            threadAbbrev += (threadAbbrev.isEmpty()?"":" ") + tv.getContext().getString(R.string.sticky_thread_abbrev);
        tv.setText(threadAbbrev);
        if (!threadAbbrev.isEmpty())
            tv.setVisibility(View.VISIBLE);
        else
            tv.setVisibility(View.GONE);
        return true;
    }

    protected static boolean setThreadTitle(TextView tv, Cursor cursor, int flags, ViewType viewType) {
        if ((flags & ChanThread.THREAD_FLAG_TITLE) > 0) {
            String text = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_TITLE));
            if (viewType == ViewType.AS_LIST) {
                text = text.toUpperCase();
            }
            tv.setText(Html.fromHtml(text));
            tv.setVisibility(View.VISIBLE);
        }
        else {
            tv.setVisibility(View.GONE);
        }
        return true;
    }

    protected static boolean setThreadSubject(TextView tv, Cursor cursor, ViewType viewType, Typeface subjectTypeface) {
        String text = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_SUBJECT));
        if (text != null && !text.isEmpty()) {
            tv.setText(Html.fromHtml(text));
            tv.setVisibility(View.VISIBLE);
        }
        else {
            tv.setText("");
            tv.setVisibility(View.GONE);
        }
        return true;
    }

    protected static boolean setThreadHeadline(TextView tv, Cursor cursor, int padding4DP) {
        String text = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_SUBJECT));
        if (text != null && !text.isEmpty()) {
            tv.setPadding(tv.getPaddingLeft(), 0, tv.getPaddingRight(), tv.getPaddingBottom());
        }
        else {
            tv.setPadding(tv.getPaddingLeft(), padding4DP, tv.getPaddingRight(), tv.getPaddingBottom());
        }
        tv.setText(Html.fromHtml(cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_HEADLINE))));
        return true;
    }

    protected static boolean setThreadText(TextView tv, Cursor cursor) {
        String text = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_TEXT));
        if (text != null && !text.isEmpty()) {
            tv.setText(Html.fromHtml(text));
            tv.setVisibility(View.VISIBLE);
        }
        else {
            tv.setText("");
            tv.setVisibility(View.GONE);
        }
        return true;
    }

    protected static boolean setThreadThumb(ImageView iv, Cursor cursor, ImageLoader imageLoader,
                                            DisplayImageOptions options, int flags) {
        if ((flags & ChanThread.THREAD_FLAG_TITLE) > 0) {
            iv.setImageBitmap(null);
        }
        else {
            String url = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_THUMBNAIL_URL));
            if (url == null || url.isEmpty()) {
                String boardCode = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_BOARD_CODE));
                long threadNo = cursor.getLong(cursor.getColumnIndex(ChanThread.THREAD_NO));
                int i = (new Long(threadNo % 3)).intValue();
                url = ChanBoard.getIndexedImageDrawableUrl(boardCode, i);
            }
            imageLoader.displayImage(
                    url,
                    iv,
                    options);
            //options.modifyCenterCrop(true)); // load async
        }
        return true;
    }

    protected static boolean setCountryFlag(ImageView iv, Cursor cursor, ImageLoader imageLoader, DisplayImageOptions options) {
        String url = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_COUNTRY_FLAG_URL));
        if (url != null && !url.isEmpty()) {
            iv.setVisibility(View.VISIBLE);
            imageLoader.displayImage(url, iv, options);
        }
        else {
            iv.setVisibility(View.GONE);
            iv.setImageResource(0);
        }
        return true;
    }

    protected static boolean setThreadNumReplies(TextView tv, Cursor cursor, int flags) {
        int n = cursor.getInt(cursor.getColumnIndex(ChanThread.THREAD_NUM_REPLIES));
        if ((flags & (ChanThread.THREAD_FLAG_AD
                | ChanThread.THREAD_FLAG_BOARD
                | ChanThread.THREAD_FLAG_TITLE)) == 0
                && n >= 0)
        {
            tv.setText(n + "r");
            tv.setVisibility(View.VISIBLE);
        }
        else {
            tv.setText("");
            tv.setVisibility(View.GONE);
        }
        return true;
    }

    protected static boolean setThreadNumImages(TextView tv, Cursor cursor, int flags) {
        int n = cursor.getInt(cursor.getColumnIndex(ChanThread.THREAD_NUM_IMAGES));
        if ((flags & (ChanThread.THREAD_FLAG_AD
                | ChanThread.THREAD_FLAG_BOARD
                | ChanThread.THREAD_FLAG_TITLE)) == 0
                && n >= 0)
        {
            tv.setText(n + "i");
            tv.setVisibility(View.VISIBLE);
        }
        else {
            tv.setText("");
            tv.setVisibility(View.GONE);
        }
        return true;
    }

    @Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		if (DEBUG) Log.d(TAG, ">>>>>>>>>>> onCreateLoader boardCode=" + boardCode);
        setProgressBarIndeterminateVisibility(true);
        cursorLoader = new BoardCursorLoader(this, boardCode, query);
        return cursorLoader;
	}

    @Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if (DEBUG) Log.v(TAG, ">>>>>>>>>>> onLoadFinished count=" + (data == null ? 0 : data.getCount()));
		adapter.swapCursor(data);
        if (DEBUG) Log.v(TAG, "listview count=" + absListView.getCount());
        if (absListView != null) {
            if (scrollOnNextLoaderFinished > -1) {
                absListView.setSelection(scrollOnNextLoaderFinished);
                scrollOnNextLoaderFinished = -1;
            }
        }

        // retry load if maybe data wasn't there yet
        if (data != null && data.getCount() < 1 && handler != null) {
            NetworkProfile.Health health = NetworkProfileManager.instance().getCurrentProfile().getConnectionHealth();
            if (health == NetworkProfile.Health.NO_CONNECTION || health == NetworkProfile.Health.BAD) {
                setProgressBarIndeterminateVisibility(false);
                String msg = String.format(getString(R.string.mobile_profile_health_status),
                        health.toString().toLowerCase().replaceAll("_", " "));
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
            else {
                handler.sendEmptyMessageDelayed(0, LOADER_RESTART_INTERVAL_SHORT_MS);
            }
        }
        else {
            setActionBarTitle(); // to reflect updated time
            setProgressBarIndeterminateVisibility(false);
        }
    }

    @Override
	public void onLoaderReset(Loader<Cursor> loader) {
		if (DEBUG) Log.v(TAG, ">>>>>>>>>>> onLoaderReset");
		adapter.swapCursor(null);
	}

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        Cursor cursor = (Cursor) adapterView.getItemAtPosition(position);
        int flags = cursor.getInt(cursor.getColumnIndex(ChanThread.THREAD_FLAGS));
        ChanHelper.simulateClickAnim(this, view);
        if ((flags & ChanThread.THREAD_FLAG_AD) == 0) {
            final long threadNo = cursor.getLong(cursor.getColumnIndex(ChanThread.THREAD_NO));
            ThreadActivity.startActivity(this, boardCode, threadNo);
        }
        else {
            final String clickUrl = cursor.getString(cursor.getColumnIndex(ChanThread.THREAD_CLICK_URL));
            ChanHelper.launchUrlInBrowser(this, clickUrl);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, BoardSelectorActivity.class);
                intent.putExtra(ChanHelper.BOARD_TYPE, ChanBoard.getBoardByCode(this, boardCode).boardType.toString());
                intent.putExtra(ChanHelper.IGNORE_DISPATCH, true);
                if (!NavUtils.shouldUpRecreateTask(this, intent)) {
                    NavUtils.navigateUpTo(this, intent);
                }
                return true;
            case R.id.refresh_menu:
                setProgressBarIndeterminateVisibility(true);
                NetworkProfileManager.instance().manualRefresh(this);
                return true;
            case R.id.toggle_view_type_menu:
                viewType = viewType == ViewType.AS_GRID ? ViewType.AS_LIST : ViewType.AS_GRID;
                invalidateOptionsMenu();
                createAbsListView();
                getSupportLoaderManager().restartLoader(0, null, this);
                return true;
            case R.id.new_thread_menu:
                Intent replyIntent = new Intent(this, PostReplyActivity.class);
                replyIntent.putExtra(ChanHelper.BOARD_CODE, boardCode);
                replyIntent.putExtra(ChanHelper.THREAD_NO, 0);
                replyIntent.putExtra(ChanPost.POST_NO, 0);
                replyIntent.putExtra(ChanHelper.TIM, 0);
                replyIntent.putExtra(ChanHelper.TEXT, "");
                startActivity(replyIntent);
                return true;
            case R.id.offline_board_view_menu:
            	GalleryViewActivity.startOfflineAlbumViewActivity(this, boardCode);
                return true;
            case R.id.offline_chan_view_menu:
            	GalleryViewActivity.startOfflineAlbumViewActivity(this, null);
                return true;
            case R.id.settings_menu:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.board_rules_menu:
                displayBoardRules();
                return true;
            case R.id.exit_menu:
                ChanHelper.exitApplication(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void displayBoardRules() {
        int boardRulesId = R.raw.global_rules_detail;
        try {
            boardRulesId = R.raw.class.getField("board_" + boardCode + "_rules").getInt(null);
        }
        catch (Exception e) {
            Log.e(TAG, "Couldn't find rules for board:" + boardCode);
        }
        RawResourceDialog rawResourceDialog
                = new RawResourceDialog(this, R.layout.board_rules_dialog, R.raw.board_rules_header, boardRulesId);
        rawResourceDialog.show();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int menuId = ChanBoard.showNSFW(this) ? R.menu.board_menu_adult : R.menu.board_menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(menuId, menu);
        ChanBoard.setupActionBarBoardSpinner(this, menu, boardCode);
        setupSearch(menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.toggle_view_type_menu);
        if (item != null) {
            item.setIcon(viewType == ViewType.AS_GRID ? R.drawable.collections_view_as_list : R.drawable.collections_view_as_grid);
            item.setTitle(viewType == ViewType.AS_GRID ? R.string.view_as_list_menu : R.string.view_as_grid_menu);
            item.setVisible(!hasQuery()); // force to list view when has query
        }
        return true;
    }

    protected void setupSearch(Menu menu) {
        SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        searchMenuItem = menu.findItem(R.id.search_menu);
        SearchView searchView = (SearchView)searchMenuItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
    }

    public void setActionBarTitle() {
        final ActionBar a = getActionBar();
        if (a == null)
            return;
        if (DEBUG) Log.i(TAG, "about to load board data for action bar board=" + boardCode);
        ChanBoard board = ChanFileStorage.loadBoardData(getApplicationContext(), boardCode);
        if (board == null) {
            board = ChanBoard.getBoardByCode(getApplicationContext(), boardCode);
        }
        String title = (board == null ? "Board" : board.name) + " /" + boardCode + "/";
        a.setTitle(title);

        StringBuffer msg = new StringBuffer();
        if (board.newThreads > 0 || board.updatedThreads > 0) {
			if (board.newThreads > 0) {
				msg.append("" + board.newThreads + " new");
			}
			if (board.updatedThreads > 0) {
				if (board.newThreads > 0) {
					msg.append(", ");
				}
				msg.append("" + board.updatedThreads + " updated");
			}
			msg.append(" thread");
			if (board.newThreads + board.updatedThreads > 1) {
				msg.append("s");
			}
			msg.append(" available");

			TextView refreshText = (TextView)findViewById(R.id.board_refresh_text);
	        refreshText.setText(msg.toString());
	        
	        LinearLayout refreshLayout = (LinearLayout)this.findViewById(R.id.board_refresh_bar);
	        refreshLayout.setVisibility(LinearLayout.VISIBLE);
	        
	        Button refreshButton = (Button)findViewById(R.id.board_refresh_button);
	        refreshButton.setClickable(true);
	        refreshButton.setOnClickListener(this);
	        Button ignoreButton = (Button)findViewById(R.id.board_ignore_button);
	        ignoreButton.setClickable(true);
	        ignoreButton.setOnClickListener(this);
		} else {
			if (board.defData || board.lastFetched == 0) {
    			msg.append("not yet fetched");
    		} else if (Math.abs(board.lastFetched - new Date().getTime()) < 60000) {
    			msg.append("fetched just now");
    		} else {
    			msg.append("fetched ").append(DateUtils.getRelativeTimeSpanString(
    					board.lastFetched, (new Date()).getTime(), 0, DateUtils.FORMAT_ABBREV_RELATIVE).toString());
    		}
			TextView refreshText = (TextView)findViewById(R.id.board_refresh_text);
	        refreshText.setText("Board is up to date");
	        
	        LinearLayout refreshLayout = (LinearLayout)this.findViewById(R.id.board_refresh_bar);
	        refreshLayout.setVisibility(LinearLayout.GONE);
		}
        
        a.setDisplayShowTitleEnabled(true);
        a.setDisplayHomeAsUpEnabled(true);
    }

	@Override
	public ChanActivityId getChanActivityId() {
		return new ChanActivityId(LastActivity.BOARD_ACTIVITY, boardCode);
	}

	@Override
	public Handler getChanHandler() {
        return handler;
	}

    @Override
    public void refresh() {
        setActionBarTitle(); // for update time
        invalidateOptionsMenu(); // in case spinner needs to be reset
        //if (absListView == null || absListView.getCount() < 1)
        //    createAbsListView();
        ChanBoard board = ChanFileStorage.loadBoardData(getApplicationContext(), boardCode);
        if (board == null) {
            board = ChanBoard.getBoardByCode(getApplicationContext(), boardCode);
        }
        if (board.newThreads == 0 && board.updatedThreads == 0) {
	        if (handler != null) {
	        	handler.sendEmptyMessageDelayed(0, LOADER_RESTART_INTERVAL_SHORT_MS);
	        }
        }
    }

    @Override
    public void closeSearch() {
        if (searchMenuItem != null)
            searchMenuItem.collapseActionView();
    }

    protected Typeface ensureSubjectTypeface() {
        if (subjectTypeface == null)
            subjectTypeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Condensed.ttf");
        return subjectTypeface;
    }

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.board_refresh_button) {
			setProgressBarIndeterminateVisibility(true);
            NetworkProfileManager.instance().manualRefresh(this);
		} else if (v.getId() == R.id.board_ignore_button) {
			TextView refreshText = (TextView)findViewById(R.id.board_refresh_text);
	        refreshText.setText("Board is up to date");
	        
	        LinearLayout refreshLayout = (LinearLayout)this.findViewById(R.id.board_refresh_bar);
	        refreshLayout.setVisibility(LinearLayout.GONE);
		}
	}

}
