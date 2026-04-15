package com.mde.potdroid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mde.potdroid.R;
import com.mde.potdroid.TopicActivity;
import com.mde.potdroid.helpers.Network;
import com.mde.potdroid.helpers.Utils;
import com.mde.potdroid.helpers.ptr.SwipyRefreshLayoutDirection;

import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Log;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SearchFragment extends BaseFragment {

    private static final String TAG = "SearchFragment";
    private static final String SEARCH_API_URL = "https://bbdb.jomx.net/api/search/";

    public static final String ARG_QUERY = "query";
    public static final String ARG_TOPIC = "topic";
    public static final String ARG_USER = "user";
    public static final String ARG_DATE_FROM = "date_from";
    public static final String ARG_DATE_TO = "date_to";
    public static final String ARG_SORT = "sort";

    private RecyclerView mResultsList;
    private SearchResultAdapter mAdapter;
    private OkHttpClient mHttpClient;

    private int mCurrentPage = 1;
    private int mTotalCount = 0;
    private boolean mIsLoading = false;

    private ArrayList<SearchResult> mResults = new ArrayList<>();

    public static SearchFragment newInstance(String query, String topic, String user,
                                             String dateFrom, String dateTo, String sort) {
        SearchFragment f = new SearchFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        args.putString(ARG_TOPIC, topic);
        args.putString(ARG_USER, user);
        args.putString(ARG_DATE_FROM, dateFrom);
        args.putString(ARG_DATE_TO, dateTo);
        args.putString(ARG_SORT, sort);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        View v = inflater.inflate(R.layout.layout_search, container, false);

        mResultsList = (RecyclerView) v.findViewById(R.id.search_results_list);

        mAdapter = new SearchResultAdapter(mResults);
        mResultsList.setAdapter(mAdapter);
        mResultsList.setLayoutManager(new LinearLayoutManager(getBaseActivity()));

        mHttpClient = new Network(getBaseActivity()).getHttpClient();

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mPullToRefreshLayout != null) {
            mPullToRefreshLayout.setDirection(SwipyRefreshLayoutDirection.TOP);
            mPullToRefreshLayout.setEnabled(true);
        }

        if (mResults.isEmpty()) {
            executeSearch();
        }
    }

    @Override
    public void onRefresh(SwipyRefreshLayoutDirection direction) {
        if (direction == SwipyRefreshLayoutDirection.TOP) {
            mCurrentPage = 1;
            mResults.clear();
            mAdapter.notifyDataSetChanged();
            executeSearch();
        }
    }

    private String buildUrl() {
        Bundle args = getArguments();
        StringBuilder sb = new StringBuilder(SEARCH_API_URL);
        sb.append("?page=").append(mCurrentPage);

        try {
            String query = args.getString(ARG_QUERY, "");
            if (!query.isEmpty()) {
                sb.append("&query=").append(URLEncoder.encode(query, "UTF-8"));
            }
            String topic = args.getString(ARG_TOPIC, "");
            if (!topic.isEmpty()) {
                sb.append("&topic=").append(URLEncoder.encode(topic, "UTF-8"));
            }
            String user = args.getString(ARG_USER, "");
            if (!user.isEmpty()) {
                sb.append("&author=").append(URLEncoder.encode(user, "UTF-8"));
            }
            String dateFrom = args.getString(ARG_DATE_FROM, "");
            if (!dateFrom.isEmpty()) {
                sb.append("&date_from=").append(URLEncoder.encode(dateFrom, "UTF-8"));
            }
            String dateTo = args.getString(ARG_DATE_TO, "");
            if (!dateTo.isEmpty()) {
                sb.append("&date_to=").append(URLEncoder.encode(dateTo, "UTF-8"));
            }
            String sort = args.getString(ARG_SORT, "");
            if (!sort.isEmpty()) {
                sb.append("&sortby=").append(URLEncoder.encode(sort, "UTF-8"));
            }
        } catch (Exception e) {
            return null;
        }

        return sb.toString();
    }

    private void executeSearch() {
        if (mIsLoading) return;
        mIsLoading = true;
        showLoadingAnimation();

        String token = mSettings.getOAuthAccessToken();
        if (token == null) {
            showError(getString(R.string.search_not_logged_in));
            mIsLoading = false;
            hideLoadingAnimation();
            return;
        }

        String url = buildUrl();
        if (url == null) {
            mIsLoading = false;
            hideLoadingAnimation();
            return;
        }

        Log.d(TAG, "Search request: " + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                Log.e(TAG, "Search request failed", e);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mIsLoading = false;
                        hideLoadingAnimation();
                        showError(getString(R.string.search_error));
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (getActivity() == null) return;
                if (response.isSuccessful()) {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, "Search response: " + body.substring(0, Math.min(body.length(), 2000)));
                        JSONObject json = new JSONObject(body);
                        mTotalCount = json.optInt("count", 0);
                        JSONArray results = json.getJSONArray("results");

                        final ArrayList<SearchResult> newResults = new ArrayList<>();
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject item = results.getJSONObject(i);
                            SearchResult result = new SearchResult();
                            result.content = item.optString("text", "");
                            result.postId = item.optInt("pid", 0);

                            JSONObject topic = item.optJSONObject("topic");
                            if (topic != null) {
                                result.topicId = topic.optInt("tid", 0);
                                result.threadTitle = topic.optString("name", "");
                            }

                            JSONObject board = item.optJSONObject("board");
                            if (board != null) {
                                result.boardTitle = board.optString("name", "");
                            }

                            JSONObject user = item.optJSONObject("user");
                            if (user != null) {
                                String unames = user.optString("unames", "");
                                if (unames.contains(",")) {
                                    result.userName = unames.substring(unames.lastIndexOf(",") + 1).trim();
                                } else {
                                    result.userName = unames.trim();
                                }
                            }

                            result.time = item.optString("time", "");
                            newResults.add(result);
                        }

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mResults.addAll(newResults);
                                mAdapter.notifyDataSetChanged();
                                mIsLoading = false;
                                hideLoadingAnimation();

                                if (mResults.isEmpty()) {
                                    showInfo(getString(R.string.search_no_results));
                                }
                            }
                        });
                    } catch (final Exception e) {
                        Log.e(TAG, "Search response parse error", e);
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mIsLoading = false;
                                hideLoadingAnimation();
                                showError(getString(R.string.search_error));
                            }
                        });
                    }
                } else {
                    final int code = response.code();
                    String errorBody = "";
                    try { errorBody = response.body().string(); } catch (Exception ignored) {}
                    Log.e(TAG, "Search HTTP error " + code + ": " + errorBody);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsLoading = false;
                            hideLoadingAnimation();
                            showError(getString(R.string.search_error));
                        }
                    });
                }
            }
        });
    }

    static class SearchResult {
        String content;
        int topicId;
        int postId;
        String threadTitle;
        String userName;
        String boardTitle;
        String time;
    }

    private static final SimpleDateFormat ISO_FORMAT;
    static {
        ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private String formatTime(String isoTime) {
        Log.d(TAG, "formatTime input: '" + isoTime + "'");
        try {
            Date date = ISO_FORMAT.parse(isoTime);
            Log.d(TAG, "formatTime parsed date: " + date);
            TimeZone tz = TimeZone.getTimeZone("Europe/Berlin");
            Calendar cal = Calendar.getInstance(tz);
            cal.setTime(date);
            Calendar today = Calendar.getInstance(tz);
            String fmt = "dd.MM.yyyy, HH:mm";
            if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                fmt = "HH:mm";
            } else if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                fmt = "dd.MM., HH:mm";
            }
            SimpleDateFormat out = new SimpleDateFormat(fmt, Locale.GERMANY);
            out.setTimeZone(tz);
            String result = out.format(date);
            Log.d(TAG, "formatTime result: '" + result + "'");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "formatTime failed for: '" + isoTime + "'", e);
            return isoTime;
        }
    }

    public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {
        private ArrayList<SearchResult> mDataset;

        public class ViewHolder extends RecyclerView.ViewHolder {
            public LinearLayout mContainer;
            public TextView mTitle;
            public TextView mSubtitle;
            public TextView mBoardName;
            public TextView mContent;

            public ViewHolder(FrameLayout itemView) {
                super(itemView);
                mContainer = (LinearLayout) itemView.findViewById(R.id.container);
                mTitle = (TextView) mContainer.findViewById(R.id.title);
                mSubtitle = (TextView) mContainer.findViewById(R.id.subtitle);
                mBoardName = (TextView) mContainer.findViewById(R.id.board_name);
                mContent = (TextView) mContainer.findViewById(R.id.result_content);
            }
        }

        public SearchResultAdapter(ArrayList<SearchResult> data) {
            mDataset = data;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout v = (FrameLayout) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_search_result, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final SearchResult result = mDataset.get(position);

            holder.mTitle.setText(result.threadTitle);

            String time = !result.time.isEmpty() ? formatTime(result.time) : "";
            if (!result.userName.isEmpty() && !time.isEmpty()) {
                holder.mSubtitle.setText(Utils.fromHtml(String.format(
                        getString(R.string.thread_lastpost), result.userName, time)));
            } else if (!result.userName.isEmpty()) {
                holder.mSubtitle.setText(Utils.fromHtml(
                        "<strong>" + result.userName + "</strong>"));
            } else {
                holder.mSubtitle.setText(time);
            }

            if (!result.boardTitle.isEmpty()) {
                holder.mBoardName.setVisibility(View.VISIBLE);
                holder.mBoardName.setText(result.boardTitle);
            } else {
                holder.mBoardName.setVisibility(View.GONE);
            }

            if (!result.content.isEmpty()) {
                holder.mContent.setVisibility(View.VISIBLE);
                holder.mContent.setText(Utils.fromHtml(result.content));
            } else {
                holder.mContent.setVisibility(View.GONE);
            }

            if (result.topicId > 0) {
                holder.mContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getBaseActivity(), TopicActivity.class);
                        intent.putExtra(TopicFragment.ARG_TOPIC_ID, result.topicId);
                        if (result.postId > 0) {
                            intent.putExtra(TopicFragment.ARG_POST_ID, result.postId);
                        }
                        startActivity(intent);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }
}
