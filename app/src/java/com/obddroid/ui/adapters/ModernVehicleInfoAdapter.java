package com.obddroid.ui.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.core.pvs.PvChangeListener;
import com.obddroid.core.pvs.PvList;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.vehicle.VehicleManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.vindecoder.nhtsa.VehicleData;

/**
 * Modern adapter for Vehicle Info screen with hero card and expandable sections
 * Author: Wal33D
 */
public class ModernVehicleInfoAdapter extends BaseAdapter implements PvChangeListener {
    private static final String TAG = "ModernVehicleInfoAdapter";

    private final Context context;
    private final LayoutInflater inflater;
    private final VehicleManager vehicleManager;
    private PvList pvs;

    // Section data
    private static final int VIEW_TYPE_HERO = 0;
    private static final int VIEW_TYPE_SECTION = 1;

    private final List<Section> sections = new ArrayList<>();
    private final Map<String, Boolean> expandedStates = new HashMap<>();

    public ModernVehicleInfoAdapter(Context context, PvList pvs) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.pvs = pvs;
        this.vehicleManager = VehicleManager.getInstance();

        // Listen to PV changes
        pvs.addPvChangeListener(this);

        // Initialize sections
        buildSections();
    }

    @Override
    public int getViewTypeCount() {
        return 2; // Hero card + Section views
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_HERO : VIEW_TYPE_SECTION;
    }

    @Override
    public int getCount() {
        return 1 + sections.size(); // Hero card + sections
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) return null; // Hero card
        return sections.get(position - 1);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            return getHeroCardView(convertView, parent);
        } else {
            return getSectionView(position - 1, convertView, parent);
        }
    }

    /**
     * Create/update the hero card view
     */
    private View getHeroCardView(View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.vehicle_info_hero_card, parent, false);
        }

        TextView vehicleName = view.findViewById(R.id.vehicle_name);
        TextView vehicleEngine = view.findViewById(R.id.vehicle_engine);
        TextView vehicleVin = view.findViewById(R.id.vehicle_vin);
        ImageButton btnCopyVin = view.findViewById(R.id.btn_copy_vin);
        Button btnShare = view.findViewById(R.id.btn_share_info);
        Button btnNhtsa = view.findViewById(R.id.btn_view_nhtsa);

        // Get vehicle data from VehicleManager
        VehicleData vehicleData = vehicleManager.getCurrentVehicleData();
        String vin = vehicleManager.getCurrentVIN();

        if (vehicleData != null && vehicleData.getDisplayName() != null) {
            vehicleName.setText(vehicleData.getDisplayName());
            String engineDesc = vehicleData.getEngineDescription();
            if (engineDesc != null && !engineDesc.isEmpty()) {
                vehicleEngine.setText(engineDesc);
            } else {
                vehicleEngine.setText("");
            }
        } else {
            vehicleName.setText("Vehicle Information");
            vehicleEngine.setText("Waiting for VIN data...");
        }

        if (vin != null && vin.length() == 17) {
            vehicleVin.setText(vin);
            btnCopyVin.setVisibility(View.VISIBLE);
            btnCopyVin.setOnClickListener(v -> copyToClipboard("VIN", vin));
        } else {
            vehicleVin.setText("No VIN available");
            btnCopyVin.setVisibility(View.GONE);
        }

        // Share button
        btnShare.setOnClickListener(v -> shareVehicleInfo());

        // NHTSA button
        btnNhtsa.setOnClickListener(v -> {
            if (vin != null && vin.length() == 17) {
                String url = "https://vpic.nhtsa.dot.gov/decoder/?vin=" + vin;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(intent);
            } else {
                SnackbarHelper.showWarning(context, "VIN not available");
            }
        });

        return view;
    }

    /**
     * Create/update a section view
     */
    private View getSectionView(int sectionIndex, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.vehicle_info_expandable_section, parent, false);
        }

        Section section = sections.get(sectionIndex);
        String sectionId = section.getId();

        TextView sectionIcon = view.findViewById(R.id.section_icon);
        TextView sectionTitle = view.findViewById(R.id.section_title);
        TextView sectionSubtitle = view.findViewById(R.id.section_subtitle);
        ImageView expandIcon = view.findViewById(R.id.expand_icon);
        View divider = view.findViewById(R.id.section_divider);
        LinearLayout sectionContent = view.findViewById(R.id.section_content);
        LinearLayout sectionHeader = view.findViewById(R.id.section_header);

        // Set section info
        sectionIcon.setText(section.getIcon());
        sectionTitle.setText(section.getTitle());
        sectionSubtitle.setText(section.getItems().size() + " item" + (section.getItems().size() != 1 ? "s" : ""));

        // Get expanded state
        boolean isExpanded = expandedStates.getOrDefault(sectionId, false);

        // Update UI based on expanded state
        if (isExpanded) {
            sectionContent.setVisibility(View.VISIBLE);
            divider.setVisibility(View.VISIBLE);
            expandIcon.setRotation(180); // Flip arrow up

            // Populate content
            sectionContent.removeAllViews();
            for (DataItem item : section.getItems()) {
                View itemView = createDataItemView(item);
                sectionContent.addView(itemView);
            }
        } else {
            sectionContent.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
            expandIcon.setRotation(0); // Arrow down
        }

        // Handle expand/collapse
        sectionHeader.setOnClickListener(v -> {
            boolean newState = !isExpanded;
            expandedStates.put(sectionId, newState);
            notifyDataSetChanged();
        });

        return view;
    }

    /**
     * Create a data item view
     */
    private View createDataItemView(DataItem item) {
        View view = inflater.inflate(R.layout.vehicle_info_data_item, null);

        TextView label = view.findViewById(R.id.data_label);
        TextView value = view.findViewById(R.id.data_value);
        ImageButton btnCopy = view.findViewById(R.id.btn_copy_value);

        label.setText(item.getLabel());
        value.setText(item.getValue());

        if (item.isCopyable()) {
            btnCopy.setVisibility(View.VISIBLE);
            btnCopy.setOnClickListener(v -> copyToClipboard(item.getLabel(), item.getValue()));
        } else {
            btnCopy.setVisibility(View.GONE);
        }

        return view;
    }

    /**
     * Build sections from PV data
     */
    private void buildSections() {
        sections.clear();

        if (pvs == null || pvs.isEmpty()) {
            return;
        }

        // Group items by type
        List<DataItem> ecuItems = new ArrayList<>();
        List<DataItem> monitorItems = new ArrayList<>();
        List<DataItem> rawItems = new ArrayList<>();

        for (Object obj : pvs.values()) {
            if (obj instanceof EcuDataPv) {
                EcuDataPv pv = (EcuDataPv) obj;
                String desc = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                String value = String.valueOf(pv.get(EcuDataPv.FID_VALUE));

                // Skip VIN (shown in hero card)
                if (desc != null && desc.toLowerCase().contains("vehicle identification")) {
                    continue;
                }

                // Skip "Number of" items - they're just metadata
                if (desc != null && desc.toLowerCase().startsWith("number of")) {
                    continue;
                }

                DataItem item = new DataItem(desc, value);

                // Categorize
                if (desc != null) {
                    String descLower = desc.toLowerCase();
                    if (descLower.contains("calibration") || descLower.contains("ecu") ||
                        descLower.contains("verification")) {
                        item.setCopyable(true);
                        ecuItems.add(item);
                    } else if (descLower.contains("monitor") || descLower.contains("count") ||
                               descLower.contains("sensor")) {
                        monitorItems.add(item);
                    } else {
                        item.setCopyable(true);
                        rawItems.add(item);
                    }
                }
            }
        }

        // Create sections
        if (!ecuItems.isEmpty()) {
            sections.add(new Section("ecu", "💾", "ECU Information", ecuItems));
        }
        if (!monitorItems.isEmpty()) {
            sections.add(new Section("monitors", "📊", "Emission Monitors", monitorItems));
        }
        if (!rawItems.isEmpty()) {
            sections.add(new Section("raw", "⚙️", "Raw Mode 9 Data", rawItems));
        }
    }

    /**
     * Copy text to clipboard
     */
    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        SnackbarHelper.showSuccess(context, label + " copied", SnackbarHelper.Duration.SHORT);
    }

    /**
     * Share vehicle info
     */
    private void shareVehicleInfo() {
        StringBuilder sb = new StringBuilder();

        VehicleData vehicleData = vehicleManager.getCurrentVehicleData();
        String vin = vehicleManager.getCurrentVIN();

        if (vehicleData != null) {
            sb.append("Vehicle Information\n\n");
            sb.append(vehicleData.getDisplayName()).append("\n");
            String engineDesc = vehicleData.getEngineDescription();
            if (engineDesc != null && !engineDesc.isEmpty()) {
                sb.append(engineDesc).append("\n");
            }
            sb.append("\nVIN: ").append(vin != null ? vin : "N/A").append("\n\n");
        }

        // Add all sections
        for (Section section : sections) {
            sb.append("=== ").append(section.getTitle()).append(" ===\n");
            for (DataItem item : section.getItems()) {
                sb.append(item.getLabel()).append(": ").append(item.getValue()).append("\n");
            }
            sb.append("\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Vehicle Information");
        context.startActivity(Intent.createChooser(shareIntent, "Share Vehicle Info"));
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        // Rebuild sections when data changes
        buildSections();
        notifyDataSetChanged();
    }

    public void setPvList(PvList newPvs) {
        if (this.pvs != null) {
            this.pvs.removePvChangeListener(this);
        }
        this.pvs = newPvs;
        if (this.pvs != null) {
            this.pvs.addPvChangeListener(this);
        }
        buildSections();
        notifyDataSetChanged();
    }

    /**
     * Section data class
     */
    private static class Section {
        private final String id;
        private final String icon;
        private final String title;
        private final List<DataItem> items;

        Section(String id, String icon, String title, List<DataItem> items) {
            this.id = id;
            this.icon = icon;
            this.title = title;
            this.items = items;
        }

        String getId() { return id; }
        String getIcon() { return icon; }
        String getTitle() { return title; }
        List<DataItem> getItems() { return items; }
    }

    /**
     * Data item class
     */
    private static class DataItem {
        private final String label;
        private final String value;
        private boolean copyable;

        DataItem(String label, String value) {
            this.label = label;
            this.value = value;
            this.copyable = false;
        }

        String getLabel() { return label; }
        String getValue() { return value; }
        boolean isCopyable() { return copyable; }
        void setCopyable(boolean copyable) { this.copyable = copyable; }
    }
}
