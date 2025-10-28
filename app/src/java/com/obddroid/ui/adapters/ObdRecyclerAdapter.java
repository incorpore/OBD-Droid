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

    private final Context context;
    private List<ListItem> items = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangedListener selectionListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    // Wrapper class for list items (either header or data)
    private static class ListItem {
        final boolean isHeader;
        final boolean isFullWidth;
        final String headerTitle;
        final EcuDataPv dataPv;

        ListItem(String headerTitle) {
            this.isHeader = true;
            this.isFullWidth = false;
            this.headerTitle = headerTitle;
            this.dataPv = null;
        }

        ListItem(EcuDataPv dataPv, boolean isFullWidth) {
            this.isHeader = false;
            this.isFullWidth = isFullWidth;
            this.headerTitle = null;
            this.dataPv = dataPv;
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
        List<EcuDataPv> testItems = new ArrayList<>();
        List<EcuDataPv> obdItems = new ArrayList<>();

        for (java.util.Map.Entry<String, EcuDataPv> entry : pvList.entrySetTyped()) {
            EcuDataPv pv = entry.getValue();
            String key = entry.getKey();
            Object description = pv.get(EcuDataPv.FID_DESCRIPT);
            String desc = description != null ? String.valueOf(description) : "";

            // Categorize based on key or description
            if (key.startsWith("F100") || desc.toUpperCase().contains("GPS")) {
                gpsItems.add(pv);
            } else if (key.startsWith("F200") || desc.toUpperCase().contains("ACCEL") || desc.toUpperCase().contains("GYRO")) {
                motionItems.add(pv);
            } else if (desc.toUpperCase().contains("TEST") || desc.toUpperCase().contains("STATUS") ||
                       desc.toUpperCase().contains("MONITOR")) {
                testItems.add(pv);
            } else {
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
        }

        if (!motionItems.isEmpty()) {
            items.add(new ListItem("📱 Motion Sensors"));
            for (EcuDataPv pv : motionItems) {
                items.add(new ListItem(pv, false)); // 2-column grid
            }
        }

        if (!testItems.isEmpty()) {
            items.add(new ListItem("🔬 Diagnostic Tests"));
            for (EcuDataPv pv : testItems) {
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
}
