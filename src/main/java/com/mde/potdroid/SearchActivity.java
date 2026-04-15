package com.mde.potdroid;

import android.os.Bundle;

import com.mde.potdroid.fragments.SearchFormFragment;
import com.mde.potdroid.fragments.SearchFragment;
import com.mde.potdroid.helpers.Utils;

public class SearchActivity extends BaseActivity
        implements SearchFormFragment.OnSearchSubmitListener {

    private SearchFormFragment mFormFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Utils.isLoggedIn())
            finish();

        if (savedInstanceState == null) {
            mFormFragment = SearchFormFragment.newInstance();
            mFormFragment.setOnSearchSubmitListener(this);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.content, mFormFragment, "search_form")
                    .commit();
        } else {
            mFormFragment = (SearchFormFragment) getSupportFragmentManager()
                    .findFragmentByTag("search_form");
            if (mFormFragment != null) {
                mFormFragment.setOnSearchSubmitListener(this);
            }
        }
    }

    @Override
    public void onSearchSubmit(String query, String topic, String user,
                               String dateFrom, String dateTo, String sort) {
        SearchFragment resultsFragment = SearchFragment.newInstance(
                query, topic, user, dateFrom, dateTo, sort);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, resultsFragment, "search_results")
                .addToBackStack(null)
                .commit();
    }
}
