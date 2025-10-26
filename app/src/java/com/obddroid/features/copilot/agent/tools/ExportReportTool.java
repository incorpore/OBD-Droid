package com.obddroid.features.copilot.agent.tools;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.FileProvider;

import com.obddroid.scan.ReportArtifacts;
import com.obddroid.scan.ScanReport;
import com.obddroid.scan.ScanResultsManager;

import org.json.JSONObject;

import java.io.File;

/**
 * Tool handler for export_report.
 * Generates and shares a scan report via Android share sheet.
 */
public class ExportReportTool implements AgentTool {

    @Override
    public String getName() {
        return "export_report";
    }

    @Override
    public String execute(Context context, JSONObject parameters) throws Exception {
        String format = parameters.optString("format", "zip");
        boolean includeAiAnalysis = parameters.optBoolean("include_ai_analysis", true);

        ScanResultsManager resultsManager = ScanResultsManager.getInstance(context);
        ScanReport report = resultsManager.getLatestScan();

        if (report == null) {
            return "No scan results available to export. Please run a full vehicle scan first.";
        }

        ReportArtifacts artifacts = report.getArtifacts();
        if (artifacts == null) {
            throw new Exception("Report artifacts not available");
        }

        File fileToShare;
        String mimeType;

        switch (format.toLowerCase()) {
            case "markdown":
                fileToShare = artifacts.getMarkdownFile();
                mimeType = "text/markdown";
                break;
            case "json":
                fileToShare = artifacts.getJsonFile();
                mimeType = "application/json";
                break;
            case "zip":
            default:
                fileToShare = artifacts.getArchiveFile();
                mimeType = "application/zip";
                break;
        }

        if (fileToShare == null || !fileToShare.exists()) {
            throw new Exception("Report file not found: " + format);
        }

        // Create share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "OBD-Droid Scan Report: " + report.getScanId());

        android.net.Uri uri = FileProvider.getUriForFile(
            context,
            context.getPackageName() + ".provider",
            fileToShare
        );
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooser = Intent.createChooser(shareIntent, "Share Scan Report");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);

        return "Scan report (" + format + " format) is ready to share. " +
            "Android share sheet opened. You can share via email, messaging, cloud storage, etc. " +
            "File: " + fileToShare.getName() +
            (includeAiAnalysis && report.hasAiAnalysis() ? " (includes AI analysis)" : "");
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }
}
