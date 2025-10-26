package com.obddroid.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.obddroid.R;
import com.obddroid.ecu.EcuInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying ECU information in a RecyclerView
 */
public class EcuAdapter extends RecyclerView.Adapter<EcuAdapter.EcuViewHolder> {

    private final List<EcuInfo> ecuList = new ArrayList<>();

    public void setEcuList(List<EcuInfo> newList) {
        ecuList.clear();
        if (newList != null) {
            ecuList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public void addEcu(EcuInfo ecu) {
        ecuList.add(ecu);
        notifyItemInserted(ecuList.size() - 1);
    }

    public void updateEcu(EcuInfo ecu) {
        for (int i = 0; i < ecuList.size(); i++) {
            if (ecuList.get(i).getAddress() == ecu.getAddress()) {
                ecuList.set(i, ecu);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public List<EcuInfo> getEcuList() {
        return new ArrayList<>(ecuList);  // Return copy to prevent modification
    }

    @NonNull
    @Override
    public EcuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_ecu, parent, false);
        return new EcuViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EcuViewHolder holder, int position) {
        EcuInfo ecu = ecuList.get(position);
        holder.bind(ecu);
    }

    @Override
    public int getItemCount() {
        return ecuList.size();
    }

    static class EcuViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView typeTextView;
        private final TextView addressTextView;
        private final TextView calibrationIdTextView;
        private final TextView calibrationId2TextView;
        private final TextView calibrationVerificationTextView;
        private final View calibrationIdContainer;
        private final View calibrationId2Container;
        private final View calibrationVerificationContainer;

        public EcuViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.ecu_name);
            typeTextView = itemView.findViewById(R.id.ecu_type);
            addressTextView = itemView.findViewById(R.id.ecu_address);
            calibrationIdTextView = itemView.findViewById(R.id.ecu_calibration_id);
            calibrationId2TextView = itemView.findViewById(R.id.ecu_calibration_id2);
            calibrationVerificationTextView = itemView.findViewById(R.id.ecu_calibration_verification);
            calibrationIdContainer = itemView.findViewById(R.id.calibration_id_container);
            calibrationId2Container = itemView.findViewById(R.id.calibration_id2_container);
            calibrationVerificationContainer = itemView.findViewById(R.id.calibration_verification_container);
        }

        public void bind(EcuInfo ecu) {
            nameTextView.setText(ecu.getDisplayName());
            typeTextView.setText(ecu.getEcuType());
            addressTextView.setText(ecu.getAddressHex());

            // Calibration ID
            if (ecu.getCalibrationId() != null && !ecu.getCalibrationId().isEmpty()) {
                calibrationIdTextView.setText(ecu.getCalibrationId());
                calibrationIdContainer.setVisibility(View.VISIBLE);
            } else {
                calibrationIdContainer.setVisibility(View.GONE);
            }

            // Calibration ID 2
            if (ecu.getCalibrationId2() != null && !ecu.getCalibrationId2().isEmpty()) {
                calibrationId2TextView.setText(ecu.getCalibrationId2());
                calibrationId2Container.setVisibility(View.VISIBLE);
            } else {
                calibrationId2Container.setVisibility(View.GONE);
            }

            // Calibration Verification
            if (ecu.getCalibrationVerification() != null && !ecu.getCalibrationVerification().isEmpty()) {
                calibrationVerificationTextView.setText(ecu.getCalibrationVerification());
                calibrationVerificationContainer.setVisibility(View.VISIBLE);
            } else {
                calibrationVerificationContainer.setVisibility(View.GONE);
            }
        }
    }
}
