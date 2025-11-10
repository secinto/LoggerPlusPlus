package com.nccgroup.loggerplusplus.exports;

import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.nccgroup.loggerplusplus.filter.logfilter.LogTableFilter;
import com.nccgroup.loggerplusplus.filter.parser.ParseException;
import com.nccgroup.loggerplusplus.logentry.LogEntry;
import com.nccgroup.loggerplusplus.logentry.LogEntryField;
import com.nccgroup.loggerplusplus.logentry.Status;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for log shippers (Elasticsearch, Graylog, Splunk, etc.)
 * Provides common functionality for queue management, filtering, and scheduling
 */
@Log4j2
public abstract class LogShipperExporter extends AutomaticLogExporter implements ExportPanelProvider {

    protected LogTableFilter logFilter;
    protected List<LogEntryField> fields;
    protected ScheduledFuture<?> shipmentTask;
    protected final ScheduledExecutorService executorService;
    protected final BlockingQueue<LogEntry> pendingEntries;
    protected final AtomicInteger connectFailedCounter;

    // Configuration
    protected static final int MAX_QUEUE_SIZE = 10000;
    protected static final int MAX_CONSECUTIVE_FAILURES = 5;
    protected static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    // Metrics
    protected final AtomicInteger successfulShipments;
    protected final AtomicInteger failedShipments;

    protected LogShipperExporter(ExportController exportController, Preferences preferences) {
        super(exportController, preferences);
        this.executorService = Executors.newScheduledThreadPool(1);
        this.pendingEntries = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.connectFailedCounter = new AtomicInteger(0);
        this.successfulShipments = new AtomicInteger(0);
        this.failedShipments = new AtomicInteger(0);
    }

    /**
     * Initialize the connection to the log shipper
     * @throws Exception if connection cannot be established
     */
    protected abstract void initializeConnection() throws Exception;

    /**
     * Ship pending entries to the destination
     * @param entries List of entries to ship
     * @throws Exception if shipping fails
     */
    protected abstract void shipEntries(List<LogEntry> entries) throws Exception;

    /**
     * Get the filter preference key
     */
    protected abstract String getFilterPreferenceKey();

    /**
     * Get the fields preference key
     */
    protected abstract String getFieldsPreferenceKey();

    /**
     * Get the delay preference key
     */
    protected abstract String getDelayPreferenceKey();

    /**
     * Get the name of this exporter for logging/error messages
     */
    protected abstract String getExporterName();

    @Override
    void setup() throws Exception {
        // Validate fields
        if (this.fields == null || this.fields.isEmpty()) {
            throw new Exception("No fields configured for export.");
        }

        // Setup filter
        String filterString = preferences.getSetting(getFilterPreferenceKey());
        if (!StringUtils.isBlank(filterString)) {
            try {
                logFilter = new LogTableFilter(filterString);
            } catch (ParseException ex) {
                log.error("The log filter configured for the {} is invalid!", getExporterName(), ex);
                throw new Exception("The log filter configured for the " + getExporterName() + " is invalid!", ex);
            }
        }

        // Initialize connection
        initializeConnection();

        // Clear any pending entries and reset counters
        pendingEntries.clear();
        connectFailedCounter.set(0);
        successfulShipments.set(0);
        failedShipments.set(0);

        // Start scheduled shipment task
        int delay = preferences.getSetting(getDelayPreferenceKey());
        shipmentTask = executorService.scheduleAtFixedRate(
            this::processQueue,
            delay,
            delay,
            TimeUnit.SECONDS
        );

        log.info("{} started successfully", getExporterName());
    }

    @Override
    void exportNewEntry(final LogEntry logEntry) {
        if (logEntry.getStatus() == Status.PROCESSED) {
            if (logFilter != null && !logFilter.getFilterExpression().matches(logEntry)) {
                return;
            }

            // Use offer instead of add to avoid blocking if queue is full
            if (!pendingEntries.offer(logEntry)) {
                log.warn("{}: Queue is full ({} entries). Dropping log entry.",
                    getExporterName(), MAX_QUEUE_SIZE);
                failedShipments.incrementAndGet();
            }
        }
    }

    @Override
    void exportUpdatedEntry(final LogEntry updatedEntry) {
        if (updatedEntry.getStatus() == Status.PROCESSED) {
            if (logFilter != null && !logFilter.getFilterExpression().matches(updatedEntry)) {
                return;
            }

            if (!pendingEntries.offer(updatedEntry)) {
                log.warn("{}: Queue is full ({} entries). Dropping log entry.",
                    getExporterName(), MAX_QUEUE_SIZE);
                failedShipments.incrementAndGet();
            }
        }
    }

    @Override
    void shutdown() throws Exception {
        log.info("Shutting down {}...", getExporterName());

        // Cancel scheduled task
        if (shipmentTask != null && !shipmentTask.isCancelled()) {
            shipmentTask.cancel(false);
        }

        // Process remaining entries one last time
        if (!pendingEntries.isEmpty()) {
            log.info("Processing {} remaining entries before shutdown", pendingEntries.size());
            try {
                processQueue();
            } catch (Exception e) {
                log.error("Error processing final entries during shutdown", e);
            }
        }

        // Shutdown executor
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                log.warn("{} executor did not terminate gracefully, forcing shutdown", getExporterName());
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear queue
        pendingEntries.clear();

        log.info("{} shutdown complete. Stats - Successful: {}, Failed: {}",
            getExporterName(), successfulShipments.get(), failedShipments.get());
    }

    /**
     * Process the queue and ship entries
     */
    protected void processQueue() {
        if (pendingEntries.isEmpty()) {
            return;
        }

        try {
            // Drain queue into a list
            List<LogEntry> entriesToShip = new CopyOnWriteArrayList<>();
            pendingEntries.drainTo(entriesToShip);

            if (entriesToShip.isEmpty()) {
                return;
            }

            log.debug("Shipping {} entries to {}", entriesToShip.size(), getExporterName());

            // Ship entries
            shipEntries(entriesToShip);

            // Reset failure counter on success
            connectFailedCounter.set(0);
            successfulShipments.addAndGet(entriesToShip.size());

            log.debug("Successfully shipped {} entries", entriesToShip.size());

        } catch (Exception e) {
            int failures = connectFailedCounter.incrementAndGet();
            failedShipments.addAndGet(pendingEntries.size());

            log.error("{} failed to ship entries (failure {} of {})",
                getExporterName(), failures, MAX_CONSECUTIVE_FAILURES, e);

            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                log.error("{} has failed {} consecutive times. Shutting down exporter.",
                    getExporterName(), MAX_CONSECUTIVE_FAILURES);
                handleConsecutiveFailures();
            }
        }
    }

    /**
     * Handle consecutive failures by shutting down and notifying the user
     */
    protected void handleConsecutiveFailures() {
        try {
            // Disable this exporter through the controller
            exportController.disableExporter(this);

            // Show error dialog on EDT
            javax.swing.SwingUtilities.invokeLater(() -> {
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    String.format("%s could not connect after %d attempts. Exporter has been shut down.",
                        getExporterName(), MAX_CONSECUTIVE_FAILURES),
                    getExporterName() + " - Connection Failed",
                    javax.swing.JOptionPane.ERROR_MESSAGE
                );
            });
        } catch (Exception e) {
            log.error("Error disabling exporter after consecutive failures", e);
        }
    }

    /**
     * Get the current queue size
     */
    public int getQueueSize() {
        return pendingEntries.size();
    }

    /**
     * Get successful shipments count
     */
    public int getSuccessfulShipments() {
        return successfulShipments.get();
    }

    /**
     * Get failed shipments count
     */
    public int getFailedShipments() {
        return failedShipments.get();
    }

    /**
     * Get consecutive failure count
     */
    public int getConsecutiveFailures() {
        return connectFailedCounter.get();
    }

    public List<LogEntryField> getFields() {
        return fields;
    }

    public void setFields(List<LogEntryField> fields) {
        preferences.setSetting(getFieldsPreferenceKey(), fields);
        this.fields = fields;
    }
}
