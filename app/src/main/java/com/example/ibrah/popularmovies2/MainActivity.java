package com.example.ibrah.popularmovies2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayout;
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection;
import com.orhanobut.logger.Logger;


public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private EndlessRecyclerViewScrollListener mScrollListener;
    private SwipyRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private View mNoDataContainer;
    private TextView mNoDataContainerMsg;

    private MoviesApiManager.SortBy sortBy = MoviesApiManager.SortBy.MostPopular;

    public static final String BUNDLE_MOVIES_KEY = "movies";
    public static final String BUNDLE_RECYCLER_POSITION_KEY = "recycler_position";
    public static final int FAVOURITES_MOVIE_LOADER_ID = 89;

    private Movies mMovies = new Movies();

    private boolean mTwoPane;

    // Receivers
    private final BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (mRecyclerView.getAdapter() == null) {
                if (isNetworkAvailable()) {
                    loadMovies();
                } else {
                    if (sortBy == MoviesApiManager.SortBy.Favourite) {
                        loadMovies();
                    }
                    mNoDataContainerMsg.setText(R.string.no_internet);
                }
                toggleNoDataContainer();
            }

            if (mScrollListener != null) {
                mScrollListener.setLoading(false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_list);
        loadSortSelected();
        if (findViewById(R.id.movieDetailContainer) != null) {
            mTwoPane = true;
        }

        mNoDataContainer = findViewById(R.id.noDataContainer);
        mNoDataContainerMsg = findViewById(R.id.tvNoDataMsg);

        mRecyclerView = findViewById(R.id.rvMovieList);
        setupRecyclerView();

        mSwipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        setupSwipeRefreshLayout();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mScrollListener != null && !mMovies.getResults().isEmpty()) {
            outState.putInt(BUNDLE_RECYCLER_POSITION_KEY, mScrollListener.getFirstCompletelyVisibleItemPosition());
            outState.putParcelable(BUNDLE_MOVIES_KEY, mMovies);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        Movies tempMovie = savedInstanceState.getParcelable(BUNDLE_MOVIES_KEY);
        int position = savedInstanceState.getInt(BUNDLE_RECYCLER_POSITION_KEY);
        if (tempMovie != null) {
            mMovies = tempMovie;

            setRecyclerViewAdapter(new MoviesAdapter(this, mMovies, mTwoPane));
            toggleNoDataContainer();
            mRecyclerView.getLayoutManager().scrollToPosition(position);
        }
    }

    @Override
    protected void onResume() {
        Logger.i("onResume()");

        super.onResume();
        try {
            registerReceiver(networkChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception ex) {
            Logger.e(ex.getMessage());
        }
    }

    @Override
    protected void onPause() {
        Logger.i("onPause()");
        try {
            unregisterReceiver(networkChangeReceiver);
        } catch (Exception ex) {
            Logger.e(ex.getMessage());
        }

        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_movie_list_menu, menu);

        // Restored Instance State
        if (sortBy == MoviesApiManager.SortBy.TopRated)
            menu.findItem(R.id.menu_sort_top_rated).setChecked(true);
        else if (sortBy == MoviesApiManager.SortBy.Favourite)
            menu.findItem(R.id.menu_sort_favourite).setChecked(true);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.menu_sort_popularity:
                if (sortBy != MoviesApiManager.SortBy.MostPopular) {
                    if (isNetworkAvailable()) {
                        sortBy = MoviesApiManager.SortBy.MostPopular;
                        loadMovies();
                        item.setChecked(true);
                        saveSortSelected();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.no_internet, Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case R.id.menu_sort_top_rated:
                if (sortBy != MoviesApiManager.SortBy.TopRated) {
                    if (isNetworkAvailable()) {
                        sortBy = MoviesApiManager.SortBy.TopRated;
                        loadMovies();
                        item.setChecked(true);
                        saveSortSelected();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.no_internet, Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case R.id.menu_sort_favourite:
                if (sortBy != MoviesApiManager.SortBy.Favourite) {
                    sortBy = MoviesApiManager.SortBy.Favourite;
                    loadMovies();
                    item.setChecked(true);
                    saveSortSelected();
                }
                break;
        }

        setTitleAccordingSort();
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        int spanCount = getHandySpanCount();

        GridLayoutManager layoutManager = new GridLayoutManager(getApplicationContext(), spanCount);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addItemDecoration(new SpacingItemDecoration((int) getResources().getDimension(R.dimen.movie_list_items_margin), SpacingItemDecoration.GRID));
        mScrollListener = new EndlessRecyclerViewScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int totalItemsCount, RecyclerView view) {
                // We don't want to display "no_internet" message on Endless Scroll so check if network is available
                if (isNetworkAvailable()) {
                    loadMoreMovies();
                }
            }
        };
        mRecyclerView.addOnScrollListener(mScrollListener);
    }

    private void setRecyclerViewAdapter(RecyclerView.Adapter adapter) {
        mRecyclerView.clearOnScrollListeners();

        if (adapter != null) {
            if (adapter instanceof MoviesAdapter) {
                mRecyclerView.addOnScrollListener(mScrollListener);
                mSwipeRefreshLayout.setDirection(SwipyRefreshLayoutDirection.BOTH);
            } else if (adapter instanceof FavouriteMoviesAdapter) {
                mSwipeRefreshLayout.setDirection(SwipyRefreshLayoutDirection.TOP);
            }
        }
        mRecyclerView.setAdapter(adapter);
        toggleNoDataContainer();
        mScrollListener.resetState();
        mSwipeRefreshLayout.setRefreshing(false);

        // If on two pane mode clear movie details fragment
        if (adapter == null) {
            ViewGroup view = findViewById(R.id.movieDetailContainer);
            if (view != null) {
                view.removeAllViews();
            }
        }
    }

    private void setupSwipeRefreshLayout() {
        mSwipeRefreshLayout.setOnRefreshListener(new SwipyRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh(SwipyRefreshLayoutDirection direction) {
                if (direction == SwipyRefreshLayoutDirection.TOP) {
                    loadMovies();
                } else {
                    loadMoreMovies();
                }
            }
        });
    }

    private void loadMovies() {
        // Get the movies
        getMovies(1);
    }

    private void loadMoreMovies() {
        getMovies(mMovies.getPage() + 1);
    }

    private void getMovies(final int page) {
        if (sortBy != MoviesApiManager.SortBy.Favourite) {
            if (isNetworkAvailable()) {
                getSupportLoaderManager().destroyLoader(FAVOURITES_MOVIE_LOADER_ID);

                MoviesApiManager.getInstance().getMovies(sortBy, page, new MoviesApiCallback<Movies>() {
                    @Override
                    public void onResponse(Movies result) {
                        if (result != null) {
                            if (page == 1) { // Refreshing movies
                                mMovies = result;
                                setRecyclerViewAdapter(new MoviesAdapter(MainActivity.this, mMovies, mTwoPane));
                            } else {
                                if (mRecyclerView.getAdapter() instanceof MoviesAdapter) {
                                    ((MoviesAdapter) mRecyclerView.getAdapter()).updateMovies(result);
                                }
                            }
                        } else {
                            mNoDataContainerMsg.setText(R.string.movie_detail_error_message);
                            Toast.makeText(getApplicationContext(), R.string.movie_detail_error_message, Toast.LENGTH_SHORT).show();
                        }
                        mSwipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onCancel() {
                    }
                });
            } else {
                mSwipeRefreshLayout.setRefreshing(false);
                mNoDataContainerMsg.setText(R.string.no_internet);
                Toast.makeText(getApplicationContext(), R.string.no_internet, Toast.LENGTH_SHORT).show();
            }
        } else {
            mMovies = new Movies();
            // Reset recycler adapter
            setRecyclerViewAdapter(null);
            getSupportLoaderManager().initLoader(FAVOURITES_MOVIE_LOADER_ID, null, this);
        }

    }

    private void toggleNoDataContainer() {
        if (mRecyclerView.getAdapter() != null && mRecyclerView.getAdapter().getItemCount() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mNoDataContainer.setVisibility(View.GONE);
        } else {
            mRecyclerView.setVisibility(View.GONE);
            mNoDataContainer.setVisibility(View.VISIBLE);
        }
    }

    private int getHandySpanCount() {
        double aa = (int) getResources().getDimension(R.dimen.movie_image_height) / 1.5;
        double ab = getResources().getDisplayMetrics().widthPixels;
        if (mTwoPane) {
            TypedValue typedValue = new TypedValue();
            getResources().getValue(R.dimen.movies_list_detail_separator_percent, typedValue, true);
            float ac = typedValue.getFloat();
            ab = ac * ab;
        }
        double ac = (ab / aa);
        return (int) Math.round(ac);
    }

    private boolean isNetworkAvailable() {
        return Misc.isNetworkAvailable(getApplicationContext());
    }

    private void saveSortSelected() {
        getPreferences(Context.MODE_PRIVATE).edit().putInt(getString(R.string.saved_sort_by_key), sortBy.ordinal()).commit();
    }

    private void setTitleAccordingSort() {
        switch (sortBy) {
            case MostPopular:
                setTitle(getString(R.string.most_popular) + " " + getString(R.string.movies));
                break;
            case TopRated:
                setTitle(getString(R.string.top_rated) + " " + getString(R.string.movies));
                break;
            case Favourite:
                setTitle(getString(R.string.favourite) + " " + getString(R.string.movies));
                break;
        }
    }

    private void loadSortSelected() {
        sortBy = MoviesApiManager.SortBy.fromId(getPreferences(Context.MODE_PRIVATE).getInt(getString(R.string.saved_sort_by_key), 0));
        setTitleAccordingSort();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new AsyncTaskLoader<Cursor>(this) {
            Cursor mTaskData = null;
            @Override
            protected void onStartLoading() {
                if (mTaskData != null) {
                    deliverResult(mTaskData);
                } else {
                    forceLoad();
                }
            }
            @Override
            public Cursor loadInBackground() {
                try {
                    return getContentResolver().query(MoviesContract.MoviesEntry.CONTENT_URI,
                            null,
                            null,
                            null,
                            MoviesContract.MoviesEntry._ID);

                } catch (Exception e) {
                    Logger.e("Failed to asynchronously load data.");
                    e.printStackTrace();
                    return null;
                }
            }
            public void deliverResult(Cursor data) {
                mTaskData = data;
                super.deliverResult(data);
            }
        };

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null && data.getCount() > 0) {
            setRecyclerViewAdapter(new FavouriteMoviesAdapter(this, data, mTwoPane));
        } else {
            mNoDataContainerMsg.setText(R.string.no_favourite_movies_message);
        }
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }
}
