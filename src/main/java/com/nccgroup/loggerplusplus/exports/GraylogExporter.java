package com.nccgroup.loggerplusplus.exports;

import biz.paluch.logging.gelf.intern.GelfMessage;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nccgroup.loggerplusplus.LoggerPlusPlus;
import com.nccgroup.loggerplusplus.logentry.LogEntry;
import com.nccgroup.loggerplusplus.logentry.LogEntryField;
import com.nccgroup.loggerplusplus.util.Globals;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.zip.GZIPOutputStream;

@Log4j2
public class GraylogExporter extends LogShipperExporter implements ContextMenuExportProvider {

    private CloseableHttpClient httpClient;
    private String graylogUrl;
    private String apiToken;
    private boolean compressionEnabled;
    private final ObjectMapper objectMapper;
    private final GraylogExporterControlPanel controlPanel;

    protected GraylogExporter(ExportController exportController, Preferences preferences) {
        super(exportController, preferences);
        this.fields = new ArrayList<>(preferences.getSetting(Globals.PREF_PREVIOUS_GRAYLOG_FIELDS));
        this.objectMapper = new ObjectMapper();

        if ((boolean) preferences.getSetting(Globals.PREF_GRAYLOG_AUTOSTART_GLOBAL)
                || (boolean) preferences.getSetting(Globals.PREF_GRAYLOG_AUTOSTART_PROJECT)) {
            try {
                this.exportController.enableExporter(this);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(LoggerPlusPlus.instance.getLoggerFrame(),
                        "Could not start Graylog exporter: " + e.getMessage() +
                        "\nSee the logs for more information.",
                        "Graylog Exporter", JOptionPane.ERROR_MESSAGE);
                log.error("Could not automatically start Graylog exporter:", e);
            }
        }
        controlPanel = new GraylogExporterControlPanel(this);
    }

    @Override
    protected void initializeConnection() throws Exception {
        InetAddress address = InetAddress.getByName(preferences.getSetting(Globals.PREF_GRAYLOG_ADDRESS));
        int port = preferences.getSetting(Globals.PREF_GRAYLOG_PORT);
        String protocol = preferences.getSetting(Globals.PREF_GRAYLOG_PROTOCOL).toString();
        this.apiToken = preferences.getSetting(Globals.PREF_GRAYLOG_API_TOKEN);
        this.compressionEnabled = preferences.getSetting(Globals.PREF_GRAYLOG_COMPRESSION_ENABLED);

        // Build Graylog GELF HTTP endpoint URL
        this.graylogUrl = String.format("%s://%s:%d/gelf",
            protocol.toLowerCase(), address.getHostAddress(), port);

        log.info("Starting Graylog exporter. URL: {}", graylogUrl);

        // Initialize HTTP client
        this.httpClient = HttpClients.createDefault();

        // Test connection
        testConnection();
    }

    @Override
    protected void shipEntries(List<LogEntry> entries) throws Exception {
        for (LogEntry entry : entries) {
            try {
                Map<String, Object> gelfMessage = createGELFMessage(entry);
                sendGELFMessage(gelfMessage);
            } catch (Exception e) {
                log.error("Failed to send entry to Graylog", e);
                throw e; // Re-throw to trigger failure handling
            }
        }
    }

    /**
     * Create a GELF message from a LogEntry
     */
    private Map<String, Object> createGELFMessage(LogEntry logEntry) {
        Map<String, Object> gelfMessage = new HashMap<>();

        // GELF required fields
        gelfMessage.put("version", "1.1");
        gelfMessage.put("host", getHostName());
        gelfMessage.put("short_message", buildShortMessage(logEntry));
        gelfMessage.put("timestamp", System.currentTimeMillis() / 1000.0);

        // GELF level (informational)
        gelfMessage.put("level", 6);

        // Add custom fields (must start with underscore in GELF)
        for (LogEntryField field : this.fields) {
            Object value = logEntry.getValueByKey(field);
            if (value == null) continue;

            try {
                String fieldName = sanitizeFieldName(field.getFullLabel());
                Object gelfValue = convertToGELFValue(value, field);
                gelfMessage.put("_" + fieldName, gelfValue);
            } catch (Exception e) {
                log.warn("Could not serialize field {}: {}", field.getFullLabel(), e.getMessage());
            }
        }

        return gelfMessage;
    }

    /**
     * Build a short message for the log entry
     */
    private String buildShortMessage(LogEntry logEntry) {
        try {
            String method = logEntry.getValueByKey(LogEntryField.METHOD) != null ?
                logEntry.getValueByKey(LogEntryField.METHOD).toString() : "UNKNOWN";
            String url = logEntry.getValueByKey(LogEntryField.URL) != null ?
                logEntry.getValueByKey(LogEntryField.URL).toString() : "unknown";
            Object statusObj = logEntry.getValueByKey(LogEntryField.STATUS);
            String status = statusObj != null ? statusObj.toString() : "N/A";

            return String.format("%s %s - Status: %s", method, url, status);
        } catch (Exception e) {
            return "Logger++ Entry";
        }
    }

    /**
     * Sanitize field name for GELF (remove spaces, special chars)
     */
    private String sanitizeFieldName(String fieldName) {
        return fieldName.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * Convert value to GELF-compatible format
     */
    private Object convertToGELFValue(Object value, LogEntryField field) {
        switch (field.getType().getSimpleName()) {
            case "Integer":
            case "Short":
            case "Double":
            case "Boolean":
                return value;
            case "Date":
                return ((Date) value).getTime() / 1000.0;
            case "String":
            default:
                return value.toString();
        }
    }

    /**
     * Send GELF message to Graylog
     */
    private void sendGELFMessage(Map<String, Object> gelfMessage) throws IOException {
        HttpPost httpPost = new HttpPost(graylogUrl);

        // Set headers
        if (apiToken != null && !apiToken.trim().isEmpty()) {
            httpPost.setHeader("Authorization", "Bearer " + apiToken);
        }
        httpPost.setHeader("Content-Type", "application/json");

        // Serialize message
        byte[] jsonBytes = objectMapper.writeValueAsBytes(gelfMessage);

        // Compress if enabled
        if (compressionEnabled) {
            httpPost.setHeader("Content-Encoding", "gzip");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(jsonBytes);
            }
            jsonBytes = baos.toByteArray();
        }

        // Set entity
        httpPost.setEntity(new ByteArrayEntity(jsonBytes, ContentType.APPLICATION_JSON));

        // Execute request
        HttpResponse response = httpClient.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != 202 && statusCode != 200) {
            throw new IOException("Graylog returned status code: " + statusCode);
        }
    }

    /**
     * Test connection to Graylog
     */
    private void testConnection() throws Exception {
        // Create a simple test message
        Map<String, Object> testMessage = new HashMap<>();
        testMessage.put("version", "1.1");
        testMessage.put("host", getHostName());
        testMessage.put("short_message", "Logger++ Graylog Exporter Connection Test");
        testMessage.put("timestamp", System.currentTimeMillis() / 1000.0);
        testMessage.put("level", 6);
        testMessage.put("_test", true);

        try {
            sendGELFMessage(testMessage);
            log.info("Graylog connection test successful");
        } catch (Exception e) {
            log.error("Graylog connection test failed", e);
            throw new Exception("Failed to connect to Graylog: " + e.getMessage(), e);
        }
    }

    /**
     * Get hostname for GELF messages
     */
    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    protected String getFilterPreferenceKey() {
        return Globals.PREF_GRAYLOG_FILTER;
    }

    @Override
    protected String getFieldsPreferenceKey() {
        return Globals.PREF_PREVIOUS_GRAYLOG_FIELDS;
    }

    @Override
    protected String getDelayPreferenceKey() {
        return Globals.PREF_GRAYLOG_DELAY;
    }

    @Override
    protected String getExporterName() {
        return "Graylog Exporter";
    }

    @Override
    public JComponent getExportPanel() {
        return controlPanel;
    }

    @Override
    public JMenuItem getExportEntriesMenuItem(List<LogEntry> entries) {
        return null;
    }

    @Override
    void shutdown() throws Exception {
        super.shutdown();

        // Close HTTP client
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.warn("Error closing HTTP client", e);
            }
        }
    }

    public ExportController getExportController() {
        return this.exportController;
    }
}
