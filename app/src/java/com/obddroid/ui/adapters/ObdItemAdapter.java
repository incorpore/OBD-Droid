package com.obddroid.ui.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.obddroid.core.ecu.Conversion;
import com.obddroid.core.ecu.EcuDataItem;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.common.ProcessVariables.IndexedProcessVar;
import com.obddroid.core.common.ProcessVariables.PvChangeEvent;
import com.obddroid.core.common.ProcessVariables.PvChangeListener;
import com.obddroid.core.common.ProcessVariables.PvList;
import com.obddroid.core.common.ProcessVariables.TypedPvList;

import org.achartengine.model.XYSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.obddroid.ui.activities.SettingsActivity;
import com.obddroid.R;

/**
 * Adapter for OBD data items (PVs)
 *

 */
public class ObdItemAdapter extends ArrayAdapter<Object>
        implements PvChangeListener
{
    public transient PvList pvs;
    final transient LayoutInflater mInflater;
    public transient static final String FID_DATA_SERIES = "SERIES";
    /**
     * allow data updates to be handled
     */
    public static boolean allowDataUpdates = true;
    private final transient SharedPreferences prefs;


    public ObdItemAdapter(Context context, int resource, PvList pvs)
    {
        super(context, resource);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setPvList(pvs);
    }

    /**
     * set / update PV list
     *
     * @param pvs process variable list
     */
    @SuppressWarnings("unchecked")
    public synchronized void setPvList(PvList pvs)
    {
        this.pvs = pvs;
        // get set to be displayed (filtered with preferences */
        Collection<Object> filtered = getPreferredItems(pvs);
        // make it a sorted array
        Object[] pidPvs = filtered.toArray();
        Arrays.sort(pidPvs, pidSorter);

        clear();
        // add all elements
        addAll(pidPvs);
    }

    static final Comparator<Object> pidSorter = new Comparator<Object>()
    {
        @Override
        public int compare(Object lhs, Object rhs)
        {
            // criteria 1: ID string
            int result = lhs.toString().compareTo(rhs.toString());

            // criteria 2: description
            if (result == 0)
            {
                result = String.valueOf(((IndexedProcessVar) lhs).get(EcuDataPv.FID_DESCRIPT))
                        .compareTo(String.valueOf(((IndexedProcessVar) rhs).get(EcuDataPv.FID_DESCRIPT)));
            }
            // return compare result
            return result;
        }
    };

    /**
     * get set of data items filtered with set of preferred items to be displayed
     *
     * @param pvs list of PVs to be handled
     * @return Set of filtered data items
     */
    @SuppressWarnings("unchecked")
    Collection getPreferredItems(PvList pvs)
    {
        // Get preference selections
        Set<String> pidsToShow = prefs.getStringSet(SettingsActivity.KEY_DATA_ITEMS, null);

        // If no preferences set, or preferences are empty, show all available PIDs
        if (pidsToShow == null || pidsToShow.isEmpty()) {
            // Show all available PIDs by default
            if (pvs instanceof TypedPvList) {
                TypedPvList<String, ?> typed = (TypedPvList<String, ?>) pvs;
                Set<String> allKeys = new HashSet<>();
                for (Map.Entry<String, ?> entry : typed.entrySetTyped()) {
                    allKeys.add(entry.getKey());
                }
                pidsToShow = allKeys;
            } else {
                @SuppressWarnings("unchecked")
                Set<String> rawKeys = (Set<String>) pvs.keySet();
                pidsToShow = rawKeys;
            }
        }

        return getMatchingItems(pvs, pidsToShow);
    }

    /**
     * get set of data items filtered with set of preferred items to be displayed
     *
     * @param pvs        list of PVs to be handled
     * @param pidsToShow Set of keys to be used as filter
     * @return Set of filtered data items
     */
    private Collection<Object> getMatchingItems(PvList pvs, Set<String> pidsToShow)
    {
        HashSet<Object> filtered = new HashSet<>();
        int gpsCount = 0;
        for (String key : pidsToShow)
        {
            IndexedProcessVar pv = (IndexedProcessVar) pvs.get(key);
            if (pv != null) {
                filtered.add(pv);
                if (key.startsWith("F1")) {
                    gpsCount++;
                    android.util.Log.d("ObdItemAdapter", "Found GPS field with key: " + key);
                }
            } else if (key.startsWith("F1")) {
                android.util.Log.w("ObdItemAdapter", "GPS key in prefs but not found in PvList: " + key);
            }
        }
        if (gpsCount > 0) {
            android.util.Log.d("ObdItemAdapter", "Total GPS fields matched: " + gpsCount + " out of " + filtered.size() + " total items");
        }
        return (filtered);
    }

    public void filterPositions(int[] positions)
    {
        ArrayList<EcuDataPv> filtered = new ArrayList<>();
        for(int pos : positions)
        {
            filtered.add((EcuDataPv)getItem(pos));
        }
        clear();
        addAll(filtered);
    }

    /*
     * (non-Javadoc)
     *
     * @see android.widget.ArrayAdapter#getView(int, android.view.View,
     * android.view.ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        // get data PV
        EcuDataPv currPv = (EcuDataPv) getItem(position);

        if (convertView == null)
        {
            convertView = mInflater.inflate(R.layout.obd_item, parent, false);
        }

        // fill view fields with data

        // description text
        TextView tvDescr = convertView.findViewById(R.id.obd_label);
        tvDescr.setText(String.valueOf(currPv.get(EcuDataPv.FID_DESCRIPT)));
        TextView tvValue = convertView.findViewById(R.id.obd_value);
        TextView tvUnits = convertView.findViewById(R.id.obd_units);
        ProgressBar pb = convertView.findViewById(R.id.bar);

        // format value string
        String fmtText;
        Object colVal = currPv.get(EcuDataPv.FID_VALUE);
        Object cnvObj = currPv.get(EcuDataPv.FID_CNVID);
        Number min = (Number) currPv.get(EcuDataPv.FID_MIN);
        Number max = (Number) currPv.get(EcuDataPv.FID_MAX);
        int pid = currPv.getAsInt(EcuDataPv.FID_PID);
        // Get display color ...
        int pidColor = ColorAdapter.getItemColor(currPv);

        try
        {
            // format text output
            if (cnvObj instanceof Conversion[]
                && ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem] != null
            )
            {
                // format throuch assigned conversion
                Conversion cnv;
                cnv = ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem];
                // set formatted text
                fmtText = cnv.physToPhysFmtString((Number) colVal,
                                                  (String) currPv.get(EcuDataPv.FID_FORMAT));
            } else
            {
                // plain format
                fmtText = String.valueOf(colVal);
            }

            // set progress bar only on numeric values with min/max limits
            if (min != null
                    && max != null
                    && colVal instanceof Number)
            {
                pb.setVisibility(ProgressBar.VISIBLE);
                pb.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(pidColor, PorterDuff.Mode.SRC_IN));
                pb.setProgress((int) (100 * ((((Number) colVal).doubleValue() - min.doubleValue()) / (max.doubleValue() - min.doubleValue()))));
            } else
            {
                pb.setVisibility(ProgressBar.GONE);
            }

        } catch (Exception ex)
        {
            fmtText = String.valueOf(colVal);
        }
        // set value
        tvValue.setText(fmtText);
        tvUnits.setText(currPv.getUnits());

        return convertView;
    }

    /**
     * Handler for data item changes
     */
    PvChangeListener dataChangeHandler = new PvChangeListener()
    {
        @Override
        public void pvChanged(PvChangeEvent event)
        {
            // handle data item updates
            if (allowDataUpdates)
            {
                IndexedProcessVar pv = (IndexedProcessVar) event.getSource();
                XYSeries series = (XYSeries) pv.get(FID_DATA_SERIES);
                if (series != null)
                {
                    if (event.getValue() instanceof Number)
                    {
                        series.add(event.getTime(),
                                   ((Number) event.getValue()).doubleValue());

                    }
                }
            }
        }
    };

    /**
     * Add data series to all process variables
     */
    protected synchronized void addAllDataSeries()
    {
        for (int pos = 0; pos < getCount(); pos++)
        {
            IndexedProcessVar pv = (IndexedProcessVar)getItem(pos);
            XYSeries series = (XYSeries) pv.get(FID_DATA_SERIES);
            if (series == null)
            {
                series = new XYSeries(String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT)));
                pv.put(FID_DATA_SERIES, series);
                pv.addPvChangeListener(dataChangeHandler, PvChangeEvent.PV_MODIFIED);
            }
        }
    }

    @Override
    public void addAll(Collection<?> collection)
    {
        super.addAll(collection);
        // get array sorted
        sort(pidSorter);

        if (this.getClass() == ObdItemAdapter.class)
            addAllDataSeries();

    }

    @Override
    public void pvChanged(PvChangeEvent event)
    {
        // handle data list updates
        switch (event.getType())
        {
            case PvChangeEvent.PV_ADDED:
                PvList pvList = (PvList)event.getSource();
                clear();
                addAll(pvList.values());
                break;

            case PvChangeEvent.PV_DELETED:
                remove(event.getValue());
                break;

            case PvChangeEvent.PV_CLEARED:
                clear();
                break;
        }
    }
}
