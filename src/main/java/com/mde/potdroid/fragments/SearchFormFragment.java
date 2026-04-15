package com.mde.potdroid.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;

import com.mde.potdroid.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SearchFormFragment extends BaseFragment {

    public static final String SORT_RELEVANCE = "";
    public static final String SORT_DATE_DESC = "-time";
    public static final String SORT_DATE_ASC = "time";

    private EditText mQueryInput;
    private EditText mTopicInput;
    private EditText mUserInput;
    private EditText mDateFrom;
    private EditText mDateTo;
    private Spinner mSortSpinner;

    private String mDateFromValue = "";
    private String mDateToValue = "";

    private OnSearchSubmitListener mListener;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    public interface OnSearchSubmitListener {
        void onSearchSubmit(String query, String topic, String user,
                            String dateFrom, String dateTo, String sort);
    }

    public static SearchFormFragment newInstance() {
        return new SearchFormFragment();
    }

    public void setOnSearchSubmitListener(OnSearchSubmitListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.actionmenu_search, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.search_submit) {
            submitSearch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        View v = inflater.inflate(R.layout.layout_search_form, container, false);

        mQueryInput = (EditText) v.findViewById(R.id.search_query);
        mTopicInput = (EditText) v.findViewById(R.id.search_topic);
        mUserInput = (EditText) v.findViewById(R.id.search_user);
        mDateFrom = (EditText) v.findViewById(R.id.search_date_from);
        mDateTo = (EditText) v.findViewById(R.id.search_date_to);
        mSortSpinner = (Spinner) v.findViewById(R.id.search_sort);

        String[] sortOptions = new String[]{
                getString(R.string.search_sort_relevance),
                getString(R.string.search_sort_date_desc),
                getString(R.string.search_sort_date_asc)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getBaseActivity(), R.layout.spinner_item_search, sortOptions);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_search);
        mSortSpinner.setAdapter(adapter);

        mDateFrom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker(true);
            }
        });

        mDateTo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker(false);
            }
        });

        mDateFrom.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mDateFrom.setText("");
                mDateFromValue = "";
                return true;
            }
        });

        mDateTo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mDateTo.setText("");
                mDateToValue = "";
                return true;
            }
        });

        return v;
    }

    private void showDatePicker(final boolean isFrom) {
        final Calendar cal = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(getBaseActivity(),
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar selected = Calendar.getInstance();
                        selected.set(year, month, dayOfMonth);
                        String display = DATE_FORMAT.format(selected.getTime());
                        if (isFrom) {
                            mDateFrom.setText(display);
                            mDateFromValue = display;
                        } else {
                            mDateTo.setText(display);
                            mDateToValue = display;
                        }
                    }
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private void submitSearch() {
        String query = mQueryInput.getText().toString().trim();
        String topic = mTopicInput.getText().toString().trim();
        String user = mUserInput.getText().toString().trim();

        if (query.isEmpty() && topic.isEmpty() && user.isEmpty()) {
            showError(getString(R.string.search_empty_query));
            return;
        }

        String sort;
        switch (mSortSpinner.getSelectedItemPosition()) {
            case 1:
                sort = SORT_DATE_DESC;
                break;
            case 2:
                sort = SORT_DATE_ASC;
                break;
            default:
                sort = SORT_RELEVANCE;
                break;
        }

        if (mListener != null) {
            mListener.onSearchSubmit(query, topic, user,
                    mDateFromValue, mDateToValue, sort);
        }
    }
}
