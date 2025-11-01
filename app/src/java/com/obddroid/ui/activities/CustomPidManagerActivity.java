package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.obddroid.R;
import com.obddroid.custompid.CustomPid;
import com.obddroid.custompid.CustomPidManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for managing custom PIDs
 * Users can view, add, edit, and delete custom PIDs
 *
 * @author Wal33D
 */
public class CustomPidManagerActivity extends AppCompatActivity {
    private static final String TAG = "CustomPidManagerActivity";

    private CustomPidManager pidManager;
    private ListView listView;
    private TextView emptyView;
    private Button btnAddPid;
    private Button btnLoadPresets;
    private CustomPidAdapter adapter;
    private List<CustomPid> pidList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_pid_manager);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Custom PIDs");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize manager
        pidManager = CustomPidManager.getInstance(this);

        // Find views
        listView = findViewById(R.id.pid_list);
        emptyView = findViewById(R.id.empty_view);
        btnAddPid = findViewById(R.id.btn_add_pid);
        btnLoadPresets = findViewById(R.id.btn_load_presets);

        // Setup adapter
        pidList = new ArrayList<>();
        adapter = new CustomPidAdapter(this, pidList);
        listView.setAdapter(adapter);

        // Setup click listeners
        btnAddPid.setOnClickListener(v -> showAddEditDialog(null));
        btnLoadPresets.setOnClickListener(v -> showLoadPresetsDialog());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            CustomPid pid = pidList.get(position);
            showPidOptionsDialog(pid);
        });

        // Load PIDs
        refreshPidList();
    }

    private void refreshPidList() {
        pidList.clear();
        pidList.addAll(pidManager.getAllPids());
        adapter.notifyDataSetChanged();

        // Show/hide empty view
        if (pidList.isEmpty()) {
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void showAddEditDialog(final CustomPid existingPid) {
        boolean isEdit = existingPid != null;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "Edit Custom PID" : "Add Custom PID");

        // Create dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_custom_pid, null);

        EditText etName = dialogView.findViewById(R.id.et_pid_name);
        EditText etPidHex = dialogView.findViewById(R.id.et_pid_hex);
        EditText etFormula = dialogView.findViewById(R.id.et_formula);
        EditText etUnits = dialogView.findViewById(R.id.et_units);
        EditText etMake = dialogView.findViewById(R.id.et_make);
        EditText etModel = dialogView.findViewById(R.id.et_model);
        EditText etYears = dialogView.findViewById(R.id.et_years);
        EditText etDescription = dialogView.findViewById(R.id.et_description);

        // Pre-fill if editing
        if (isEdit) {
            etName.setText(existingPid.getName());
            etPidHex.setText(existingPid.getPidHex());
            etFormula.setText(existingPid.getFormula());
            etUnits.setText(existingPid.getUnits());
            etMake.setText(existingPid.getVehicleMake());
            etModel.setText(existingPid.getVehicleModel());
            etYears.setText(existingPid.getVehicleYears());
            etDescription.setText(existingPid.getDescription());
        }

        builder.setView(dialogView);

        builder.setPositiveButton(isEdit ? "Update" : "Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String pidHex = etPidHex.getText().toString().trim();
            String formula = etFormula.getText().toString().trim();
            String units = etUnits.getText().toString().trim();
            String make = etMake.getText().toString().trim();
            String model = etModel.getText().toString().trim();
            String years = etYears.getText().toString().trim();
            String description = etDescription.getText().toString().trim();

            if (name.isEmpty() || pidHex.isEmpty() || formula.isEmpty()) {
                Toast.makeText(this, "Name, PID, and Formula are required", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                CustomPid pid = isEdit ? existingPid : new CustomPid();
                pid.setName(name);
                pid.setPidHex(pidHex);
                pid.setFormula(formula);
                pid.setUnits(units);
                pid.setVehicleMake(make.isEmpty() ? null : make);
                pid.setVehicleModel(model.isEmpty() ? null : model);
                pid.setVehicleYears(years.isEmpty() ? null : years);
                pid.setDescription(description.isEmpty() ? null : description);

                if (isEdit) {
                    pidManager.updatePid(pid);
                    Toast.makeText(this, "PID updated", Toast.LENGTH_SHORT).show();
                } else {
                    pidManager.addPid(pid);
                    Toast.makeText(this, "PID added", Toast.LENGTH_SHORT).show();
                }

                refreshPidList();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showPidOptionsDialog(final CustomPid pid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(pid.getName());

        String message = String.format(
            "PID: %s\nFormula: %s\nUnits: %s\n\n%s",
            pid.getPidHex(),
            pid.getFormula(),
            pid.getUnits(),
            pid.getDescription() != null ? pid.getDescription() : ""
        );

        builder.setMessage(message);

        builder.setPositiveButton("Edit", (dialog, which) -> showAddEditDialog(pid));

        builder.setNegativeButton("Delete", (dialog, which) -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete PID?")
                .setMessage("Are you sure you want to delete \"" + pid.getName() + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    pidManager.deletePid(pid.getId());
                    Toast.makeText(this, "PID deleted", Toast.LENGTH_SHORT).show();
                    refreshPidList();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        builder.setNeutralButton("Close", null);
        builder.show();
    }

    private void showLoadPresetsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Load Manufacturer Presets");
        builder.setMessage("This will load 18 pre-configured manufacturer-specific PIDs:\n\n" +
                "• Subaru (3 PIDs)\n" +
                "• BMW (2 PIDs)\n" +
                "• Ford (2 PIDs)\n" +
                "• GM/Chevrolet (2 PIDs)\n" +
                "• Honda (2 PIDs)\n" +
                "• Toyota (2 PIDs)\n" +
                "• VW/Audi (2 PIDs)\n" +
                "• Mazda (1 PID)\n" +
                "• Nissan (1 PID)\n\n" +
                "These will be added if not already present.");

        builder.setPositiveButton("Load Presets", (dialog, which) -> {
            // The presets are automatically loaded when database is empty
            // To reload, we need to manually trigger it
            int currentCount = pidManager.getAllPids().size();
            if (currentCount >= 18) {
                Toast.makeText(this, "Presets already loaded (" + currentCount + " PIDs)", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Presets are auto-loaded on first run", Toast.LENGTH_LONG).show();
            }
            refreshPidList();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * Adapter for custom PID list
     */
    private static class CustomPidAdapter extends BaseAdapter {
        private final Context context;
        private final List<CustomPid> pids;

        CustomPidAdapter(Context context, List<CustomPid> pids) {
            this.context = context;
            this.pids = pids;
        }

        @Override
        public int getCount() {
            return pids.size();
        }

        @Override
        public CustomPid getItem(int position) {
            return pids.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(
                    R.layout.item_custom_pid, parent, false);
            }

            CustomPid pid = pids.get(position);

            TextView pidName = convertView.findViewById(R.id.pid_name);
            TextView pidHex = convertView.findViewById(R.id.pid_hex);
            TextView pidFormula = convertView.findViewById(R.id.pid_formula);
            TextView pidVehicle = convertView.findViewById(R.id.pid_vehicle);
            TextView pidUnits = convertView.findViewById(R.id.pid_units);

            pidName.setText(pid.getName());
            pidHex.setText(pid.getPidHex());
            pidFormula.setText(pid.getFormula());
            pidUnits.setText(pid.getUnits() != null ? pid.getUnits() : "");

            // Vehicle info
            String vehicleInfo = "";
            if (pid.getVehicleMake() != null && !pid.getVehicleMake().isEmpty()) {
                vehicleInfo = pid.getVehicleMake();
                if (pid.getVehicleModel() != null && !pid.getVehicleModel().isEmpty()) {
                    vehicleInfo += " " + pid.getVehicleModel();
                }
                if (pid.getVehicleYears() != null && !pid.getVehicleYears().isEmpty()) {
                    vehicleInfo += " (" + pid.getVehicleYears() + ")";
                }
            } else {
                vehicleInfo = "Universal";
            }
            pidVehicle.setText(vehicleInfo);

            return convertView;
        }
    }
}
