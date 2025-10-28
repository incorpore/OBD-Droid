package com.obddroid.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.obddroid.R;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.EcuDataItem;
import com.obddroid.ecu.EcuDataPv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for displaying OBD data items in a staggered grid with sections
 */
public class ObdRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;
    private static final int VIEW_TYPE_MAP = 3;

    private final Context context;
    private List<ListItem> items = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangedListener selectionListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    // Wrapper class for list items (either header, data, or map)
    private static class ListItem {
        final boolean isHeader;
        final boolean isFullWidth;
        final boolean isMap;
        final String headerTitle;
        final EcuDataPv dataPv;

        ListItem(String headerTitle) {
            this.isHeader = true;
            this.isFullWidth = false;
            this.isMap = false;
            this.headerTitle = headerTitle;
            this.dataPv = null;
        }

        ListItem(EcuDataPv dataPv, boolean isFullWidth) {
            this.isHeader = false;
            this.isFullWidth = isFullWidth;
            this.isMap = false;
            this.headerTitle = null;
            this.dataPv = dataPv;
        }

        // Special constructor for map tile
        private ListItem(boolean isMapTile) {
            this.isHeader = false;
            this.isFullWidth = false;
            this.isMap = isMapTile;
            this.headerTitle = null;
            this.dataPv = null;
        }

        static ListItem createMapTile() {
            return new ListItem(true);
        }
    }

    public ObdRecyclerAdapter(Context context, TypedPvList<String, EcuDataPv> pvList, OnSelectionChangedListener listener) {
        this.context = context;
        this.selectionListener = listener;
        updateData(pvList);
    }

    public void updateData(TypedPvList<String, EcuDataPv> pvList) {
        items.clear();

        if (pvList == null || pvList.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        // Categorize items
        List<EcuDataPv> gpsItems = new ArrayList<>();
        List<EcuDataPv> motionItems = new ArrayList<>();
        List<EcuDataPv> oxygenSensorItems = new ArrayList<>();
        List<EcuDataPv> testStatusItems = new ArrayList<>();
        List<EcuDataPv> unidentifiedItems = new ArrayList<>();
        List<EcuDataPv> obdItems = new ArrayList<>();

        for (java.util.Map.Entry<String, EcuDataPv> entry : pvList.entrySetTyped()) {
            EcuDataPv pv = entry.getValue();
            String key = entry.getKey();
            Object description = pv.get(EcuDataPv.FID_DESCRIPT);
            String desc = description != null ? String.valueOf(description) : "";
            String descUpper = desc.toUpperCase();

            // Skip fields that are shown in the status bar (avoid duplication)
            if (descUpper.contains("NUMBER OF FAULT CODES") ||
                descUpper.contains("MIL STATUS") ||
                descUpper.contains("MALFUNCTION INDICATOR")) {
                continue; // Don't add to any category - filtered out
            }

            // Categorize based on key or description
            if (key.startsWith("F100") || descUpper.contains("GPS")) {
                gpsItems.add(pv);
            } else if (key.startsWith("F200") || descUpper.contains("ACCEL") || descUpper.contains("GYRO")) {
                motionItems.add(pv);
            } else if (descUpper.contains("OXYGEN SENSOR") && descUpper.contains("PRESENT")) {
                oxygenSensorItems.add(pv);
            } else if (desc.isEmpty() ||
                       descUpper.startsWith("PID ") ||
                       descUpper.startsWith("VID ") ||
                       descUpper.matches("^[0-9A-F]+$") ||
                       key.equals(desc)) {
                // Unidentified PIDs/VIDs: empty desc, starts with "PID "/"VID ", is hex value, or desc equals key
                unidentifiedItems.add(pv);
            } else if ((descUpper.contains("TEST") && descUpper.contains("STATUS")) ||
                       (descUpper.contains("MONITOR") && descUpper.contains("STATUS")) ||
                       (descUpper.contains("SYSTEM") && descUpper.contains("STATUS")) ||
                       descUpper.contains("MISFIRE")) {
                // Test/Monitor/System status fields + Misfire go to Other Data
                testStatusItems.add(pv);
            } else {
                // Everything else goes into Live OBD Data
                obdItems.add(pv);
            }
        }

        // Build sectioned list - Live OBD Data first
        if (!obdItems.isEmpty()) {
            items.add(new ListItem("🚗 Live OBD Data"));
            for (EcuDataPv pv : obdItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        if (!gpsItems.isEmpty()) {
            items.add(new ListItem("📍 GPS Telemetry"));
            for (EcuDataPv pv : gpsItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
            // Add map preview tile at the end
            items.add(ListItem.createMapTile());
        }

        if (!motionItems.isEmpty()) {
            items.add(new ListItem("📱 Motion Telemetry"));
            for (EcuDataPv pv : motionItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        // Combine oxygen sensors and test/status fields into "Other Data" section
        if (!oxygenSensorItems.isEmpty() || !testStatusItems.isEmpty()) {
            items.add(new ListItem("📊 Other Data"));

            // Add oxygen sensor fields first
            for (EcuDataPv pv : oxygenSensorItems) {
                items.add(new ListItem(pv, true)); // FULL WIDTH
            }

            // Then add test/status fields
            for (EcuDataPv pv : testStatusItems) {
                items.add(new ListItem(pv, true)); // FULL WIDTH
            }
        }

        // Separate section for unidentified PIDs/VIDs at the bottom
        if (!unidentifiedItems.isEmpty()) {
            items.add(new ListItem("❓ Unidentified PID/VID"));
            for (EcuDataPv pv : unidentifiedItems) {
                items.add(new ListItem(pv, true)); // FULL WIDTH
            }
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ListItem item = items.get(position);
        if (item.isHeader) {
            return VIEW_TYPE_HEADER;
        } else if (item.isMap) {
            return VIEW_TYPE_MAP;
        } else if (item.isFullWidth) {
            return 2; // Full-width item type
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.obd_section_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == VIEW_TYPE_MAP) {
            // Map tile layout
            View view = LayoutInflater.from(context).inflate(R.layout.gps_map_tile, parent, false);
            return new MapViewHolder(view);
        } else if (viewType == 2) {
            // Full-width layout for diagnostic tests
            View view = LayoutInflater.from(context).inflate(R.layout.obd_item_full_width, parent, false);
            return new ItemViewHolder(view);
        } else {
            // Grid tile layout
            View view = LayoutInflater.from(context).inflate(R.layout.obd_item_grid, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = items.get(position);

        if (holder instanceof HeaderViewHolder) {
            // Bind header
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.title.setText(item.headerTitle);

            // Make header span full width
            StaggeredGridLayoutManager.LayoutParams layoutParams =
                (StaggeredGridLayoutManager.LayoutParams) headerHolder.itemView.getLayoutParams();
            layoutParams.setFullSpan(true);

        } else if (holder instanceof MapViewHolder) {
            // Bind map tile
            MapViewHolder mapHolder = (MapViewHolder) holder;

            // Make map span full width
            StaggeredGridLayoutManager.LayoutParams layoutParams =
                (StaggeredGridLayoutManager.LayoutParams) mapHolder.itemView.getLayoutParams();
            layoutParams.setFullSpan(true);

            // Load map with GPS coordinates from the GPS telemetry data
            loadMapForGpsData(mapHolder);

        } else if (holder instanceof ItemViewHolder) {
            // Bind data item
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            EcuDataPv pv = item.dataPv;

            if (pv == null) return;

            // Set full-width for test items
            StaggeredGridLayoutManager.LayoutParams layoutParams =
                (StaggeredGridLayoutManager.LayoutParams) itemHolder.itemView.getLayoutParams();
            layoutParams.setFullSpan(item.isFullWidth);

            // Description
            Object description = pv.get(EcuDataPv.FID_DESCRIPT);
            itemHolder.label.setText(description != null ? String.valueOf(description) : "");

            // Format value
            Object colVal = pv.get(EcuDataPv.FID_VALUE);
            Object cnvObj = pv.get(EcuDataPv.FID_CNVID);
            String fmtText;

            try {
                if (cnvObj instanceof Conversion[] && ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem] != null) {
                    Conversion cnv = ((Conversion[]) cnvObj)[EcuDataItem.cnvSystem];
                    fmtText = cnv.physToPhysFmtString((Number) colVal, (String) pv.get(EcuDataPv.FID_FORMAT));
                } else {
                    fmtText = String.valueOf(colVal);
                }
            } catch (Exception e) {
                fmtText = String.valueOf(colVal);
            }

            itemHolder.value.setText(fmtText);
            itemHolder.units.setText(pv.getUnits());

            // Color coding
            int pidColor = ColorAdapter.getItemColor(pv);
            itemHolder.value.setTextColor(pidColor);

            // Progress bar (optional)
            itemHolder.progressBar.setVisibility(View.GONE);

            // Selection state
            boolean isSelected = selectedPositions.contains(position);
            itemHolder.cardView.setCardBackgroundColor(isSelected ?
                Color.parseColor("#3D5AFE") :
                context.getResources().getColor(R.color.background_secondary));

            // Click and long-click to toggle selection
            View.OnClickListener toggleListener = v -> toggleSelection(itemHolder.getAdapterPosition());
            itemHolder.itemView.setOnClickListener(toggleListener);
            itemHolder.itemView.setOnLongClickListener(v -> {
                toggleSelection(itemHolder.getAdapterPosition());
                return true;
            });
        }
    }

    public void toggleSelection(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }

        // Don't allow selection of headers
        if (items.get(position).isHeader) {
            return;
        }

        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }

        notifyDataSetChanged();

        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedPositions.size());
        }
    }

    public void clearSelection() {
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public int[] getSelectedPositions() {
        int[] positions = new int[selectedPositions.size()];
        int i = 0;
        for (int pos : selectedPositions) {
            positions[i++] = pos;
        }
        return positions;
    }

    /**
     * Get the actual EcuDataPv objects for selected positions (for chart compatibility)
     */
    public List<EcuDataPv> getSelectedItems() {
        List<EcuDataPv> selectedItems = new ArrayList<>();
        for (int pos : selectedPositions) {
            if (pos >= 0 && pos < items.size()) {
                ListItem item = items.get(pos);
                if (!item.isHeader && item.dataPv != null) {
                    selectedItems.add(item.dataPv);
                }
            }
        }
        return selectedItems;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Header ViewHolder
    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView title;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.section_title);
        }
    }

    // Map ViewHolder
    public static class MapViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView mapPreview;

        public MapViewHolder(@NonNull View itemView) {
            super(itemView);
            mapPreview = itemView.findViewById(R.id.map_preview);
        }
    }

    // Item ViewHolder
    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView label;
        TextView value;
        TextView units;
        ProgressBar progressBar;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            label = itemView.findViewById(R.id.obd_label);
            value = itemView.findViewById(R.id.obd_value);
            units = itemView.findViewById(R.id.obd_units);
            progressBar = itemView.findViewById(R.id.bar);
        }
    }

    /**
     * Load map tile with GPS coordinates from GPS telemetry data
     */
    private void loadMapForGpsData(MapViewHolder holder) {
        try {
            // Find GPS lat/lon from ObdProt.PidPvs
            com.obddroid.ecu.EcuDataPv latPv = com.obddroid.obd.ObdProt.PidPvs.getTyped("F100.0.0");
            com.obddroid.ecu.EcuDataPv lonPv = com.obddroid.obd.ObdProt.PidPvs.getTyped("F100.1.0");

            if (latPv != null && lonPv != null) {
                Object latValue = latPv.get(com.obddroid.ecu.EcuDataPv.FID_VALUE);
                Object lonValue = lonPv.get(com.obddroid.ecu.EcuDataPv.FID_VALUE);

                if (latValue instanceof Number && lonValue instanceof Number) {
                    double latitude = ((Number) latValue).doubleValue();
                    double longitude = ((Number) lonValue).doubleValue();

                    // Load map tile at zoom level 15 (street level)
                    com.obddroid.utils.MapTileHelper.loadMapTile(latitude, longitude, 15, holder.mapPreview);

                    // Make map clickable to open external maps app
                    holder.itemView.setOnClickListener(v -> openExternalMap(latitude, longitude));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ObdRecyclerAdapter", "Failed to load GPS map: " + e.getMessage());
        }
    }

    /**
     * Open external maps app with GPS coordinates
     */
    private void openExternalMap(double latitude, double longitude) {
        try {
            android.net.Uri geoUri = android.net.Uri.parse(String.format("geo:%f,%f?z=15", latitude, longitude));
            android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri);
            context.startActivity(mapIntent);
        } catch (Exception e) {
            android.util.Log.e("ObdRecyclerAdapter", "Failed to open map: " + e.getMessage());
        }
    }
}
