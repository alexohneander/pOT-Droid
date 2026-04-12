package com.mde.potdroid.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;

import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.ksoichiro.android.observablescrollview.ObservableRecyclerView;
import com.mde.potdroid.EditorActivity;
import com.mde.potdroid.R;
import com.mde.potdroid.helpers.AsyncHttpLoader;
import com.mde.potdroid.helpers.ImageUploadHelper;
import com.mde.potdroid.helpers.Utils;
import com.mde.potdroid.models.Bookmark;
import com.mde.potdroid.models.Topic;
import com.mde.potdroid.parsers.BookmarkParser;
import com.mde.potdroid.parsers.TopicParser;

import org.apache.http.Header;

import java.util.ArrayList;

/**
 * Fragment for picking a topic to share an image to.
 * Shows the user's bookmarks for quick topic selection.
 */
public class ShareImageFragment extends BaseFragment {

    public static final String ARG_IMAGE_URI = "image_uri";

    private static final int LOADER_BOOKMARKS = 0;
    private static final int LOADER_TOPIC = 2;

    private Uri mImageUri;
    private ObservableRecyclerView mListView;

    private BookmarkListAdapter mBookmarkAdapter;

    public static ShareImageFragment newInstance(Uri imageUri) {
        ShareImageFragment f = new ShareImageFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_IMAGE_URI, imageUri);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mImageUri = getArguments().getParcelable(ARG_IMAGE_URI);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        View v = inflater.inflate(R.layout.layout_share_image, container, false);

        mListView = (ObservableRecyclerView) v.findViewById(R.id.forum_list_content);
        mListView.setLayoutManager(new LinearLayoutManager(getBaseActivity()));

        mBookmarkAdapter = new BookmarkListAdapter(new ArrayList<Bookmark>());

        getActionbar().setTitle(R.string.share_pick_topic);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        loadBookmarks();
    }

    private void loadBookmarks() {
        mListView.setAdapter(mBookmarkAdapter);
        getLoaderManager().restartLoader(LOADER_BOOKMARKS, null, mBookmarkLoaderCallbacks);
    }

    private void onTopicSelected(final int topicId) {
        showLoadingAnimation();

        getLoaderManager().restartLoader(LOADER_TOPIC, null, new LoaderManager.LoaderCallbacks<Topic>() {
            @Override
            public Loader<Topic> onCreateLoader(int id, Bundle args) {
                return new AsyncTopicLoader(getBaseActivity(), topicId);
            }

            @Override
            public void onLoadFinished(Loader<Topic> loader, Topic topic) {
                hideLoadingAnimation();
                if (topic != null && topic.getNewreplytoken() != null) {
                    uploadAndOpenEditor(topic);
                } else {
                    showError(getString(R.string.msg_loading_error));
                }
            }

            @Override
            public void onLoaderReset(Loader<Topic> loader) {
                hideLoadingAnimation();
            }
        });
    }

    private void uploadAndOpenEditor(final Topic topic) {
        ImageUploadHelper uploadHelper = new ImageUploadHelper(getBaseActivity());

        if (!uploadHelper.hasValidToken()) {
            uploadHelper.startOAuthFlow(getBaseActivity());
            showInfo(getString(R.string.share_oauth_required));
            return;
        }

        showLoadingAnimation();
        uploadHelper.upload(mImageUri, new ImageUploadHelper.OnImageUploadedListener() {
            @Override
            public void onSuccess(final String imageUrl) {
                getBaseActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoadingAnimation();
                        openEditor(topic, imageUrl);
                    }
                });
            }

            @Override
            public void onFailure(final String error) {
                getBaseActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoadingAnimation();
                        showError(getString(R.string.share_upload_failed) + ": " + error);
                    }
                });
            }
        });
    }

    private void openEditor(Topic topic, String imageUrl) {
        Intent intent = new Intent(getBaseActivity(), EditorActivity.class);
        intent.putExtra(EditorFragment.ARG_MODE, EditorFragment.MODE_REPLY);
        intent.putExtra(EditorFragment.ARG_TOPIC_ID, topic.getId());
        intent.putExtra(EditorFragment.ARG_TOKEN, topic.getNewreplytoken());
        intent.putExtra(EditorFragment.ARG_TEXT, "[img]" + imageUrl + "[/img]");
        startActivity(intent);
        getBaseActivity().finish();
    }

    // --- Bookmark loading ---

    private final LoaderManager.LoaderCallbacks<BookmarkParser.BookmarksContainer> mBookmarkLoaderCallbacks =
            new LoaderManager.LoaderCallbacks<BookmarkParser.BookmarksContainer>() {
                @Override
                public Loader<BookmarkParser.BookmarksContainer> onCreateLoader(int id, Bundle args) {
                    showLoadingAnimation();
                    return new AsyncBookmarkLoader(getBaseActivity());
                }

                @Override
                public void onLoadFinished(Loader<BookmarkParser.BookmarksContainer> loader,
                                           BookmarkParser.BookmarksContainer result) {
                    hideLoadingAnimation();
                    if (result != null && result.getException() == null) {
                        mBookmarkAdapter.setItems(result.getBookmarks());
                    } else {
                        showError(getString(R.string.msg_loading_error));
                    }
                }

                @Override
                public void onLoaderReset(Loader<BookmarkParser.BookmarksContainer> loader) {
                    hideLoadingAnimation();
                }
            };

    // --- Async loaders ---

    static class AsyncBookmarkLoader extends AsyncHttpLoader<BookmarkParser.BookmarksContainer> {
        AsyncBookmarkLoader(Context cx) {
            super(cx, BookmarkParser.URL);
        }

        @Override
        public BookmarkParser.BookmarksContainer processNetworkResponse(String response) {
            try {
                BookmarkParser parser = new BookmarkParser();
                return parser.parse(response);
            } catch (Exception e) {
                BookmarkParser.BookmarksContainer c = new BookmarkParser.BookmarksContainer();
                c.setException(e);
                return c;
            }
        }

        @Override
        protected void onNetworkFailure(int statusCode, Header[] headers,
                                        String responseBody, Throwable error) {
            Utils.printException(error);
            deliverResult(null);
        }
    }

    static class AsyncTopicLoader extends AsyncHttpLoader<Topic> {
        AsyncTopicLoader(Context cx, int topicId) {
            super(cx, TopicParser.getUrl(topicId, 1, 0));
        }

        @Override
        public Topic processNetworkResponse(String response) {
            try {
                TopicParser parser = new TopicParser();
                return parser.parse(response);
            } catch (Exception e) {
                Utils.printException(e);
                return null;
            }
        }

        @Override
        protected void onNetworkFailure(int statusCode, Header[] headers,
                                        String responseBody, Throwable error) {
            Utils.printException(error);
            deliverResult(null);
        }
    }

    // --- Adapter ---

    class BookmarkListAdapter extends RecyclerView.Adapter<BookmarkListAdapter.ViewHolder> {
        private ArrayList<Bookmark> mDataset;

        class ViewHolder extends RecyclerView.ViewHolder {
            FrameLayout mRoot;
            RelativeLayout mContainer;
            TextView mTextTitle;
            TextView mTextBoard;
            TextView mTextPages;

            ViewHolder(FrameLayout container) {
                super(container);
                mRoot = container;
                mContainer = (RelativeLayout) container.findViewById(R.id.container);
                mTextTitle = (TextView) mContainer.findViewById(R.id.title);
                mTextBoard = (TextView) mContainer.findViewById(R.id.board);
                mTextPages = (TextView) mContainer.findViewById(R.id.pages);
            }
        }

        BookmarkListAdapter(ArrayList<Bookmark> data) {
            mDataset = data;
        }

        void setItems(ArrayList<Bookmark> data) {
            mDataset = data;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout v = (FrameLayout) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.listitem_bookmark, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final Bookmark b = mDataset.get(position);

            if (b.getNumberOfNewPosts() > 0) {
                holder.mRoot.setBackgroundColor(Utils.getColorByAttr(getActivity(), R.attr.bbDarkerItemBackground));
            } else {
                holder.mRoot.setBackgroundColor(Utils.getColorByAttr(getActivity(), R.attr.bbItemBackground));
            }

            holder.mTextTitle.setText(b.getThread().getTitle());
            if (b.getThread().isClosed())
                holder.mTextTitle.setPaintFlags(holder.mTextTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            else
                holder.mTextTitle.setPaintFlags(holder.mTextTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));

            holder.mTextBoard.setText(b.getThread().getBoard().getName());

            Spanned description = Utils.fromHtml(String.format(getString(
                    R.string.new_posts_description),
                    b.getNumberOfNewPosts(), b.getThread().getNumberOfPages()));
            holder.mTextPages.setText(description);

            holder.mContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onTopicSelected(b.getThread().getId());
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }
}
