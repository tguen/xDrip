package com.eveningoutpost.dexdrip;

/**
 * Created by jamorham on 14/01/2017.
 * <p>
 * Multi-page plugin style status entry lists
 */
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.services.Ob1G5CollectionService;
import com.eveningoutpost.dexdrip.ui.helpers.FloatingLocaleActivityWithScreenshot;
import com.eveningoutpost.dexdrip.utilitymodels.PersistentStore;
import com.eveningoutpost.dexdrip.utilitymodels.StatusItem;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;
import lombok.val;

import static com.eveningoutpost.dexdrip.utils.DexCollectionType.DexcomG5;


public class MegaStatus extends FloatingLocaleActivityWithScreenshot {


    private static Activity mActivity;
    private SectionsPagerAdapter mSectionsPagerAdapter;

    private static final String menu_name = "Mega Status";
    private static final String TAG = "MegaStatus";
    private static final long autoFreshDelay = 500;

    private static boolean activityVisible = false;
    private static boolean autoFreshRunning = false;
    private static Runnable autoRunnable;
    private static int currentPage = 0;
    private boolean autoStart = false;

    private static final ArrayList<String> sectionList = new ArrayList<>();
    private static final ArrayList<String> sectionTitles = new ArrayList<>();
    private static final HashSet<String> sectionAlwaysOn = new HashSet<>(); //While viewing these pages, the screen won't time out.

    public static View runnableView;


    private ViewPager mViewPager;

    private Integer color_store1;
    private int padding_store_left_1, padding_store_bottom_1, padding_store_top_1, padding_store_right_1, gravity_store_1;

    private static ArrayList<MegaStatusListAdapter> MegaStatusAdapters = new ArrayList<>();
    private BroadcastReceiver serviceDataReceiver;

    private void addAsection(String section, String title) {
        sectionList.add(section);
        sectionTitles.add(title);
        MegaStatusAdapters.add(new MegaStatusListAdapter());
    }

    public static final String G5_STATUS = "Dex Status";


    static {
        sectionAlwaysOn.add(G5_STATUS);
    }

    public static PendingIntent getStatusPendingIntent(String section_name) {
        final Intent intent = new Intent(xdrip.getAppContext(), MegaStatus.class);
        intent.setAction(section_name);
        return PendingIntent.getActivity(xdrip.getAppContext(), 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void startStatus(String section_name) {
        try {
            getStatusPendingIntent(section_name).send();
        } catch (PendingIntent.CanceledException e) {
            UserError.Log.e(TAG, "Unable to start status: " + e);
        }
    }

    private void populateSectionList() {

        // TODO extract descriptions to resource strings

        if (sectionList.isEmpty()) {

            addAsection("Classic Status Page", "Legacy System Status");

            final DexCollectionType dexCollectionType = DexCollectionType.getDexCollectionType();

            // probably want a DexCollectionService related set
            if (dexCollectionType.equals(DexcomG5)) {
                addAsection(G5_STATUS, "Dex Collector/Transmitter Status");
            }
        } else {
            UserError.Log.d(TAG, "Section list already populated");
        }
    }

    private static void populate(MegaStatusListAdapter la, String section) {
        if ((la == null) || (section == null)) {
            UserError.Log.e(TAG, "Adapter or Section were null in populate()");
            return;
        }

        la.clear(false);
	    if (section.equals(G5_STATUS)) {
		    la.addRows(Ob1G5CollectionService.megaStatus());
	    }
        la.changed();
    }

    @Override
    public String getMenuName() {
        return menu_name;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;
        setContentView(R.layout.activity_mega_status);
        JoH.fixActionBar(this);

        sectionList.clear();
        sectionTitles.clear();
        populateSectionList();

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // switch to last used position if it exists
        int saved_position = (int) PersistentStore.getLong("mega-status-last-page");

        // if triggered from pending intent, flip to named section if we can
        final String action = getIntent().getAction();
        if ((action != null) && (action.length() > 0)) {
            int action_position = sectionList.indexOf(action);
            if (action_position > -1) saved_position = action_position;
        }

        if ((saved_position > 0) && (saved_position < sectionList.size())) {
            currentPage = saved_position;
            mViewPager.setCurrentItem(saved_position);
            autoStart = true; // run once activity becomes visible
            keepScreenOn(sectionAlwaysOn.contains(sectionList.get(currentPage)));
        }
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                UserError.Log.d(TAG, "Page selected: " + position);
                runnableView = null;
                currentPage = position;
                startAutoFresh();
                keepScreenOn(sectionAlwaysOn.contains(sectionList.get(currentPage)));
                PersistentStore.setLong("mega-status-last-page", currentPage);
            }
        });

        // streamed data from android wear
        requestWearCollectorStatus();
        serviceDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
            }
        };

        try {
            getSupportActionBar().setSubtitle(BuildConfig.VERSION_NAME);
            fixElipsusAndSize(null);
        } catch (Exception e) {
            UserError.Log.e(TAG, "Got exception trying to set subtitle: ", e);
        }
    }

    private void fixElipsusAndSize(ViewGroup root) {
        if (root == null)
            root = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content).getRootView();
        final int children = root.getChildCount();
        for (int i = 0; i < children; i++) {
            final View view = root.getChildAt(i);
            if (view instanceof TextView) {
                final String txt = ((TextView) view).getText().toString();
                if (txt.contains(BuildConfig.VERSION_NAME)) {
                    ((TextView) view).setEllipsize(null);
                    final float tsize = ((TextView) view).getTextSize();
                    if (tsize > 10f) {
                        ((TextView) view).setTextSize(Math.max(10f, tsize / 4));
                    }
                    return;
                }
            } else if (view instanceof ViewGroup) {
                fixElipsusAndSize((ViewGroup) view);
            }
        }
    }

    private void requestWearCollectorStatus() {
    }

    @Override
    public void onPause() {
        activityVisible = false;
        if (serviceDataReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(xdrip.getAppContext()).unregisterReceiver(serviceDataReceiver);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "broadcast receiver not registered", e);
            }
        }
        runnableView = null; // gc
        mActivity = null;
        super.onPause();
    }


    @Override
    protected void onResume() {
        mActivity = this;
        super.onResume();

        activityVisible = true;

        if ((autoRunnable != null) || (autoStart)) startAutoFresh();

        if (sectionList.size() > 1)
            startupInfo(); // show swipe message if there is a page to swipe to
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        if (FloatingLocaleActivityWithScreenshot.localeString == null) {
            // only if we are not forced to a locale
            getMenuInflater().inflate(R.menu.menu_mega_status, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_screenshot) {
            val lang = "en"; // force in this language
            val intent = JoH.getStartActivityIntent(MegaStatus.class);
            intent.putExtra(FORCE_ACTIVITY_LANGUAGE,lang);
            intent.putExtra(SCREENSHOT_AND_EXIT, true);
            FloatingLocaleActivityWithScreenshot.localeString = lang;
            xdrip.getAppContext().startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startupInfo() {
    }

    private void keepScreenOn(boolean on) {
        try {
            if (on) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) {
            UserError.Log.d(TAG, "Exception setting window flags: " + e);
        }
    }

    private synchronized void startAutoFresh() {
        if (autoFreshRunning) return;
        autoStart = false;
        if (autoRunnable == null) autoRunnable = new Runnable() {

            @Override
            public void run() {
                try {
                    if ((activityVisible) && (autoFreshRunning) && (currentPage != 0)) {
                        MegaStatus.populate(MegaStatusAdapters.get(currentPage), sectionList.get(currentPage));
                        requestWearCollectorStatus();
                        JoH.runOnUiThreadDelayed(autoRunnable, autoFreshDelay);
                    } else {
                        UserError.Log.d(TAG, "AutoFresh shutting down");
                        autoFreshRunning = false;
                    }
                } catch (Exception e) {
                    UserError.Log.e(TAG, "Exception in auto-fresh: " + e);
                    autoFreshRunning = false;
                }
            }
        };
        autoFreshRunning = true;
        JoH.runOnUiThreadDelayed(autoRunnable, 200);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {


            final int index = getArguments().getInt(ARG_SECTION_NUMBER);

            View rootView = inflater.inflate(R.layout.fragment_mega_status, container, false);
            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
            ListView listView = (ListView) rootView.findViewById(R.id.list_label);
            UserError.Log.d(TAG, "Setting Section " + index);

            textView.setText(sectionTitles.get(index));

            listView.setAdapter(MegaStatusAdapters.get(index));
            MegaStatus.populate((MegaStatusListAdapter) listView.getAdapter(), sectionList.get(index));


            return rootView;
        }
    }

    /**
     * A {@link FragmentStatePagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) return new SystemStatusFragment();
            return PlaceholderFragment.newInstance(position);
        }

        @Override
        public int getCount() {
            return sectionList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return sectionList.get(position);
        }
    }


    static class ViewHolder {
        TextView name;
        TextView value;
        TextView spacer;
        Button button;
        LinearLayout layout;
    }

    private class MegaStatusListAdapter extends BaseAdapter {
        private ArrayList<StatusItem> statusRows;
        private LayoutInflater mInflator;

        MegaStatusListAdapter() {
            super();
            statusRows = new ArrayList<>();
            mInflator = MegaStatus.this.getLayoutInflater();
        }

        public StatusItem getRow(int position) {
            return statusRows.get(position);
        }

        void addRow(StatusItem obj) {
            statusRows.add(obj);
        }

        void addRows(List<StatusItem> list) {
            for (StatusItem item : list) {
                addRow(item);
            }
        }

        public void changed() {
            notifyDataSetChanged();
        }

        public void clear(boolean refresh) {
            statusRows.clear();
            if (refresh) notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return statusRows.size();
        }

        @Override
        public Object getItem(int i) {
            return statusRows.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;

            if (view == null) {
                viewHolder = new ViewHolder();
                view = mInflator.inflate(R.layout.listitem_megastatus, null);
                viewHolder.value = (TextView) view.findViewById(R.id.value);
                viewHolder.button = (Button) view.findViewById(R.id.button);
                viewHolder.name = (TextView) view.findViewById(R.id.name);
                viewHolder.spacer = (TextView) view.findViewById(R.id.spacer);
                viewHolder.layout = (LinearLayout) view.findViewById(R.id.device_list_id);
                view.setTag(viewHolder);
                if (color_store1 == null) {
                    color_store1 = viewHolder.name.getCurrentTextColor();
                    padding_store_bottom_1 = viewHolder.layout.getPaddingBottom();
                    padding_store_top_1 = viewHolder.layout.getPaddingTop();
                    padding_store_left_1 = viewHolder.layout.getPaddingLeft();
                    padding_store_right_1 = viewHolder.layout.getPaddingRight();
                    gravity_store_1 = viewHolder.name.getGravity();
                }

            } else {
                viewHolder = (ViewHolder) view.getTag();
                //  reset all changed properties
                viewHolder.spacer.setVisibility(View.VISIBLE);
                viewHolder.name.setVisibility(View.VISIBLE);
                viewHolder.value.setVisibility(View.VISIBLE);
                viewHolder.button.setVisibility(View.GONE);
                viewHolder.name.setTextColor(color_store1);
                viewHolder.layout.setPadding(padding_store_left_1, padding_store_top_1, padding_store_right_1, padding_store_bottom_1);
                viewHolder.name.setGravity(gravity_store_1);
            }


            final StatusItem row = statusRows.get(i);

            // TODO  add buttons

            if (row.name.equals("line-break")) {
                viewHolder.spacer.setVisibility(View.GONE);
                viewHolder.name.setVisibility(View.GONE);
                viewHolder.value.setVisibility(View.GONE);
                viewHolder.layout.setPadding(10, 10, 10, 10);
            } else if (row.name.equals("heading-break")) {
                viewHolder.value.setVisibility(View.GONE);
                viewHolder.spacer.setVisibility(View.GONE);
                viewHolder.name.setText(row.value);
                viewHolder.name.setGravity(Gravity.CENTER_HORIZONTAL);
                viewHolder.name.setTextColor(Color.parseColor("#fff9c4"));
            } else {

                viewHolder.name.setText(row.name);
                viewHolder.value.setText(row.value);

                final int new_colour = row.highlight.color();
                //if (new_colour != -1) {
                viewHolder.value.setBackgroundColor(new_colour);
                viewHolder.spacer.setBackgroundColor(new_colour);
                viewHolder.name.setBackgroundColor(new_colour);
                //}
                view.setOnClickListener(null); // reset
                if ((row.runnable != null) && (row.button_name != null) && (row.button_name.equals("long-press"))) {
                    runnableView = view; // last one
                  /*  view.setLongClickable(true);
                    view.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            try {
                                runOnUiThread(row.runnable);
                            } catch (Exception e) {
                                //
                            }
                            return true;
                        }
                    });*/
                    view.setOnClickListener(v -> {
                        try {
                            runOnUiThread(row.runnable);
                        } catch (Exception e) {
                            //
                        }

                    });

                } else if ((row.runnable != null) && (row.button_name != null) && (row.button_name.equals("button-press"))) {
                        runnableView = view; // last one
                        viewHolder.button.setText(row.value);
                        viewHolder.value.setVisibility(View.GONE);
                        viewHolder.button.setVisibility(View.VISIBLE);
                        viewHolder.button.setOnClickListener(v -> {
                            try {
                                runOnUiThread(row.runnable);
                            } catch (Exception e) {
                                //
                            }
                        });

                        view.setOnClickListener(v -> {
                            try {
                                runOnUiThread(row.runnable);
                            } catch (Exception e) {
                                //
                            }

                        });


                } else {
                    view.setLongClickable(false);
                }
            }

            return view;
        }
    }
}
