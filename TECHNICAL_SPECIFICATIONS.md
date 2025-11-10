# Technical Specifications - Immediate Priority Enhancements

## Document Information
- **Purpose**: Detailed technical specifications for P0 enhancements
- **Audience**: Developers implementing enhancements
- **Status**: Ready for Implementation

---

## Table of Contents
1. [P0.1: ElasticExporter Refactoring](#p01-elasticexporter-refactoring)
2. [P0.2: Comprehensive Unit Testing](#p02-comprehensive-unit-testing)
3. [P0.3: Retry Logic with Exponential Backoff](#p03-retry-logic-with-exponential-backoff)

---

## P0.1: ElasticExporter Refactoring

### Objective
Refactor `ElasticExporter` to extend `LogShipperExporter`, inheriting all thread safety and reliability improvements.

### Current Implementation Analysis

**File**: `src/main/java/com/nccgroup/loggerplusplus/exports/ElasticExporter.java`

**Current Class Hierarchy**:
```
LogExporter
  └── AutomaticLogExporter
        └── ElasticExporter (current)
```

**Problems**:
```java
public class ElasticExporter extends AutomaticLogExporter {
    // PROBLEM 1: Thread-unsafe ArrayList
    ArrayList<LogEntry> pendingEntries;  // Line 53

    // PROBLEM 2: Manual executor management
    private final ScheduledExecutorService executorService;  // Line 60

    // PROBLEM 3: Inconsistent error handling
    private int connectFailedCounter;  // Line 58 - not atomic

    // PROBLEM 4: No queue size limits
    pendingEntries.add(logEntry);  // Lines 162, 170 - unbounded growth

    // PROBLEM 5: Improper shutdown
    @Override
    void shutdown() throws Exception {
        if(this.indexTask != null){
            indexTask.cancel(true);  // Line 177 - abrupt cancellation
        }
        this.pendingEntries = null;  // Line 179 - no synchronization
    }
}
```

### Proposed Implementation

**New Class Hierarchy**:
```
LogExporter
  └── AutomaticLogExporter
        └── LogShipperExporter (new base class)
              ├── ElasticExporter (refactored)
              └── GraylogExporter (existing)
```

**Step-by-Step Refactoring**:

#### Step 1: Identify What to Remove

```java
public class ElasticExporter extends AutomaticLogExporter {

    // ❌ REMOVE - provided by LogShipperExporter
    ArrayList<LogEntry> pendingEntries;
    private ScheduledFuture indexTask;
    private int connectFailedCounter;
    private final ScheduledExecutorService executorService;

    // ✅ KEEP - Elasticsearch-specific
    ElasticsearchClient elasticClient;
    LogTableFilter logFilter;
    private List<LogEntryField> fields;
    private String indexName;
    private final ElasticExporterControlPanel controlPanel;
    private final ObjectMapper mapper;
    private Logger logger = LogManager.getLogger(this);
}
```

#### Step 2: Change Class Declaration

```java
// Before
public class ElasticExporter extends AutomaticLogExporter
    implements ExportPanelProvider, ContextMenuExportProvider

// After
public class ElasticExporter extends LogShipperExporter
    implements ContextMenuExportProvider
    // Note: ExportPanelProvider already implemented in LogShipperExporter
```

#### Step 3: Update Constructor

```java
// Before
protected ElasticExporter(ExportController exportController, Preferences preferences) {
    super(exportController, preferences);
    this.fields = new ArrayList<>(preferences.getSetting(Globals.PREF_PREVIOUS_ELASTIC_FIELDS));
    executorService = Executors.newScheduledThreadPool(1);  // ❌ Remove

    this.mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule("LogEntry Serializer",
        new Version(0,1,0,"",null, null));
    module.addSerializer(LogEntry.class, new ElasticExporter.EntrySerializer(LogEntry.class));
    mapper.registerModule(module);

    if ((boolean) preferences.getSetting(Globals.PREF_ELASTIC_AUTOSTART_GLOBAL)
            || (boolean) preferences.getSetting(Globals.PREF_ELASTIC_AUTOSTART_PROJECT)) {
        try {
            this.exportController.enableExporter(this);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(LoggerPlusPlus.instance.getLoggerFrame(),
                "Could not start elastic exporter: " + e.getMessage() +
                "\nSee the logs for more information.",
                "Elastic Exporter", JOptionPane.ERROR_MESSAGE);
            logger.error("Could not automatically start elastic exporter:", e);
        }
    }
    controlPanel = new ElasticExporterControlPanel(this);
}

// After
protected ElasticExporter(ExportController exportController, Preferences preferences) {
    super(exportController, preferences);  // ✅ Base class handles executor
    this.fields = new ArrayList<>(preferences.getSetting(Globals.PREF_PREVIOUS_ELASTIC_FIELDS));

    this.mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule("LogEntry Serializer",
        new Version(0,1,0,"",null, null));
    module.addSerializer(LogEntry.class, new ElasticExporter.EntrySerializer(LogEntry.class));
    mapper.registerModule(module);

    // ✅ Autostart logic stays the same
    if ((boolean) preferences.getSetting(Globals.PREF_ELASTIC_AUTOSTART_GLOBAL)
            || (boolean) preferences.getSetting(Globals.PREF_ELASTIC_AUTOSTART_PROJECT)) {
        try {
            this.exportController.enableExporter(this);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(LoggerPlusPlus.instance.getLoggerFrame(),
                "Could not start elastic exporter: " + e.getMessage() +
                "\nSee the logs for more information.",
                "Elastic Exporter", JOptionPane.ERROR_MESSAGE);
            log.error("Could not automatically start elastic exporter:", e);
        }
    }
    controlPanel = new ElasticExporterControlPanel(this);
}
```

#### Step 4: Implement Abstract Methods

```java
@Override
protected void initializeConnection() throws Exception {
    // Move content from setup() method (lines 91-156)

    if (this.fields == null || this.fields.isEmpty()) {
        throw new Exception("No fields configured for export.");
    }

    String filterString = preferences.getSetting(Globals.PREF_ELASTIC_FILTER);
    if (!StringUtils.isBlank(filterString)) {
        try {
            logFilter = new LogTableFilter(filterString);
        } catch (ParseException ex) {
            log.error("The log filter configured for the Elastic exporter is invalid!", ex);
            throw new Exception("The log filter configured for the Elastic exporter is invalid!", ex);
        }
    }

    InetAddress address = InetAddress.getByName(preferences.getSetting(Globals.PREF_ELASTIC_ADDRESS));
    int port = preferences.getSetting(Globals.PREF_ELASTIC_PORT);
    indexName = preferences.getSetting(Globals.PREF_ELASTIC_INDEX);
    String protocol = preferences.getSetting(Globals.PREF_ELASTIC_PROTOCOL).toString();

    RestClientBuilder restClientBuilder = RestClient.builder(
        new HttpHost(address, port, protocol));

    log.info(String.format("Starting ElasticSearch exporter. %s://%s:%s/%s",
        protocol, address, port, indexName));

    // Authentication setup
    Globals.ElasticAuthType authType = preferences.getSetting(Globals.PREF_ELASTIC_AUTH);
    String user = "", pass = "";
    switch (authType) {
        case ApiKey:
            user = preferences.getSetting(Globals.PREF_ELASTIC_API_KEY_ID);
            pass = preferences.getSetting(Globals.PREF_ELASTIC_API_KEY_SECRET);
            break;
        case Basic:
            user = preferences.getSetting(Globals.PREF_ELASTIC_USERNAME);
            pass = preferences.getSetting(Globals.PREF_ELASTIC_PASSWORD);
            break;
        default:
            break;
    }

    if (!"".equals(user) && !"".equalsIgnoreCase(pass)) {
        log.info(String.format("ElasticSearch using %s, Username: %s", authType, user));
        String authValue = Base64.getEncoder().encodeToString(
            (user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        restClientBuilder.setDefaultHeaders(new Header[]{
            new BasicHeader("Authorization", String.format("%s %s", authType, authValue))
        });
    }

    ElasticsearchTransport transport = new RestClientTransport(
        restClientBuilder.build(), new JacksonJsonpMapper(this.mapper));
    elasticClient = new ElasticsearchClient(transport);

    // Create index if needed
    createIndices();
}

@Override
protected void shipEntries(List<LogEntry> entries) throws Exception {
    // Move content from indexPendingEntries() method (lines 219-268)

    if (entries.isEmpty()) {
        return;
    }

    BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

    for (LogEntry logEntry : entries) {
        try {
            bulkBuilder.operations(op -> op
                .index(idx -> idx
                    .index(this.indexName)
                    .document(logEntry)
                )
            );
        } catch (Exception e) {
            log.error("Could not build elastic export request for entry: " + e.getMessage());
            // Note: Individual entry failures don't throw - allows partial success
        }
    }

    BulkResponse bulkResponse = elasticClient.bulk(bulkBuilder.build());

    if (bulkResponse.errors()) {
        StringBuilder errors = new StringBuilder();
        for (BulkResponseItem bulkResponseItem : bulkResponse.items()) {
            if (bulkResponseItem.error() != null) {
                errors.append(bulkResponseItem.error().reason()).append("; ");
            }
        }
        // Log but don't throw - partial success is acceptable
        log.error("Bulk indexing had errors: {}", errors.toString());
    }
}

@Override
protected String getFilterPreferenceKey() {
    return Globals.PREF_ELASTIC_FILTER;
}

@Override
protected String getFieldsPreferenceKey() {
    return Globals.PREF_PREVIOUS_ELASTIC_FIELDS;
}

@Override
protected String getDelayPreferenceKey() {
    return Globals.PREF_ELASTIC_DELAY;
}

@Override
protected String getExporterName() {
    return "Elastic Exporter";
}
```

#### Step 5: Remove Obsolete Methods

```java
// ❌ REMOVE - handled by LogShipperExporter
@Override
void setup() throws Exception { ... }

// ❌ REMOVE - handled by LogShipperExporter
@Override
public void exportNewEntry(final LogEntry logEntry) { ... }

// ❌ REMOVE - handled by LogShipperExporter
@Override
public void exportUpdatedEntry(final LogEntry updatedEntry) { ... }

// ❌ REMOVE - handled by LogShipperExporter
@Override
void shutdown() throws Exception { ... }

// ❌ REMOVE - handled by LogShipperExporter
private void indexPendingEntries() { ... }
```

#### Step 6: Update Shutdown to Close Elasticsearch Client

```java
@Override
void shutdown() throws Exception {
    // Call parent shutdown first (processes remaining entries)
    super.shutdown();

    // Clean up Elasticsearch-specific resources
    if (elasticClient != null) {
        try {
            // Close the transport (which closes the RestClient)
            elasticClient._transport().close();
        } catch (IOException e) {
            log.warn("Error closing Elasticsearch client", e);
        }
    }
}
```

### Complete Refactored ElasticExporter

```java
package com.nccgroup.loggerplusplus.exports;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.nccgroup.loggerplusplus.LoggerPlusPlus;
import com.nccgroup.loggerplusplus.logentry.LogEntry;
import com.nccgroup.loggerplusplus.logentry.LogEntryField;
import com.nccgroup.loggerplusplus.util.Globals;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.swing.*;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Log4j2
public class ElasticExporter extends LogShipperExporter implements ContextMenuExportProvider {

    private ElasticsearchClient elasticClient;
    private String indexName;
    private final ElasticExporterControlPanel controlPanel;
    private final ObjectMapper mapper;

    protected ElasticExporter(ExportController exportController, Preferences preferences) {
        super(exportController, preferences);
        this.fields = new ArrayList<>(preferences.getSetting(Globals.PREF_PREVIOUS_ELASTIC_FIELDS));

        this.mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule("LogEntry Serializer",
            new Version(0,1,0,"",null, null));
        module.addSerializer(LogEntry.class, new ElasticExporter.EntrySerializer(LogEntry.class));
        mapper.registerModule(module);

        if ((boolean) preferences.getSetting(Globals.PREF_ELASTIC_AUTOSTART_GLOBAL)
                || (boolean) preferences.getSetting(Globals.PREF_ELASTIC_AUTOSTART_PROJECT)) {
            try {
                this.exportController.enableExporter(this);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(LoggerPlusPlus.instance.getLoggerFrame(),
                        "Could not start elastic exporter: " + e.getMessage() +
                        "\nSee the logs for more information.",
                        "Elastic Exporter", JOptionPane.ERROR_MESSAGE);
                log.error("Could not automatically start elastic exporter:", e);
            }
        }
        controlPanel = new ElasticExporterControlPanel(this);
    }

    @Override
    protected void initializeConnection() throws Exception {
        if (this.fields == null || this.fields.isEmpty()) {
            throw new Exception("No fields configured for export.");
        }

        String filterString = preferences.getSetting(Globals.PREF_ELASTIC_FILTER);
        if (!StringUtils.isBlank(filterString)) {
            try {
                logFilter = new com.nccgroup.loggerplusplus.filter.logfilter.LogTableFilter(filterString);
            } catch (com.nccgroup.loggerplusplus.filter.parser.ParseException ex) {
                log.error("The log filter configured for the Elastic exporter is invalid!", ex);
                throw new Exception("The log filter configured for the Elastic exporter is invalid!", ex);
            }
        }

        InetAddress address = InetAddress.getByName(preferences.getSetting(Globals.PREF_ELASTIC_ADDRESS));
        int port = preferences.getSetting(Globals.PREF_ELASTIC_PORT);
        indexName = preferences.getSetting(Globals.PREF_ELASTIC_INDEX);
        String protocol = preferences.getSetting(Globals.PREF_ELASTIC_PROTOCOL).toString();
        RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost(address, port, protocol));
        log.info(String.format("Starting ElasticSearch exporter. %s://%s:%s/%s", protocol, address, port, indexName));

        Globals.ElasticAuthType authType = preferences.getSetting(Globals.PREF_ELASTIC_AUTH);
        String user = "", pass = "";
        switch (authType) {
            case ApiKey:
                user = preferences.getSetting(Globals.PREF_ELASTIC_API_KEY_ID);
                pass = preferences.getSetting(Globals.PREF_ELASTIC_API_KEY_SECRET);
                break;
            case Basic:
                user = preferences.getSetting(Globals.PREF_ELASTIC_USERNAME);
                pass = preferences.getSetting(Globals.PREF_ELASTIC_PASSWORD);
                break;
            default:
                break;
        }

        if (!"".equals(user) && !"".equalsIgnoreCase(pass)) {
            log.info(String.format("ElasticSearch using %s, Username: %s", authType, user));
            String authValue = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
            restClientBuilder.setDefaultHeaders(new Header[]{new BasicHeader("Authorization", String.format("%s %s", authType, authValue))});
        }

        ElasticsearchTransport transport = new RestClientTransport(restClientBuilder.build(), new JacksonJsonpMapper(this.mapper));
        elasticClient = new ElasticsearchClient(transport);

        createIndices();
    }

    @Override
    protected void shipEntries(List<LogEntry> entries) throws Exception {
        if (entries.isEmpty()) {
            return;
        }

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (LogEntry logEntry : entries) {
            try {
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(this.indexName)
                                .document(logEntry)
                        )
                );
            } catch (Exception e) {
                log.error("Could not build elastic export request for entry: " + e.getMessage());
            }
        }

        BulkResponse bulkResponse = elasticClient.bulk(bulkBuilder.build());
        if (bulkResponse.errors()) {
            for (BulkResponseItem bulkResponseItem : bulkResponse.items()) {
                if (bulkResponseItem.error() != null) {
                    log.error(bulkResponseItem.error().reason());
                }
            }
        }
    }

    @Override
    protected String getFilterPreferenceKey() {
        return Globals.PREF_ELASTIC_FILTER;
    }

    @Override
    protected String getFieldsPreferenceKey() {
        return Globals.PREF_PREVIOUS_ELASTIC_FIELDS;
    }

    @Override
    protected String getDelayPreferenceKey() {
        return Globals.PREF_ELASTIC_DELAY;
    }

    @Override
    protected String getExporterName() {
        return "Elastic Exporter";
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

        if (elasticClient != null) {
            try {
                elasticClient._transport().close();
            } catch (IOException e) {
                log.warn("Error closing Elasticsearch client", e);
            }
        }
    }

    private void createIndices() throws IOException {
        ExistsRequest existsRequest = new ExistsRequest.Builder().index(this.indexName).build();
        BooleanResponse exists = elasticClient.indices().exists(existsRequest);

        if(!exists.value()) {
            CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(this.indexName).build();
            elasticClient.indices().create(createIndexRequest);
        }
    }

    public ExportController getExportController() {
        return this.exportController;
    }

    private class EntrySerializer extends StdSerializer<LogEntry> {
        public EntrySerializer(Class<LogEntry> t) {
            super(t);
        }

        @Override
        public void serialize(LogEntry logEntry, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            for (LogEntryField field : ElasticExporter.this.fields) {
                Object value = logEntry.getValueByKey(field);
                if(value == null) continue;
                try {
                    switch (field.getType().getSimpleName()){
                        case "Integer": gen.writeNumberField(field.getFullLabel(), (Integer) value); break;
                        case "Short": gen.writeNumberField(field.getFullLabel(), (Short) value); break;
                        case "Double": gen.writeNumberField(field.getFullLabel(), (Double) value); break;
                        case "String": gen.writeStringField(field.getFullLabel(), value.toString()); break;
                        case "Boolean": gen.writeBooleanField(field.getFullLabel(), (Boolean) value); break;
                        case "Date": gen.writeNumberField(field.getFullLabel(), ((Date) value).getTime()); break;
                        default: log.error("Unhandled field type: " + field.getType().getSimpleName());
                    }
                }catch (Exception e){
                    log.error("ElasticExporter: Couldn't serialize field. The field was omitted from the export.");
                }
            }
            gen.writeEndObject();
        }
    }
}
```

### Testing Checklist

#### Pre-Refactor Tests
- [ ] Record current behavior (success/failure rates, performance)
- [ ] Capture baseline metrics (memory, CPU, throughput)
- [ ] Document edge cases and known issues

#### Refactoring Tests
- [ ] All existing tests pass
- [ ] No compilation errors
- [ ] No warnings about deprecated methods
- [ ] Code coverage maintained or improved

#### Post-Refactor Tests
- [ ] **Functional**: Same behavior as before
- [ ] **Thread Safety**: Stress test with concurrent operations
- [ ] **Performance**: Comparable or better throughput
- [ ] **Memory**: No memory leaks, bounded queue
- [ ] **Error Handling**: Failures handled gracefully
- [ ] **Integration**: Works with real Elasticsearch instance

#### Regression Tests
- [ ] Existing configurations load correctly
- [ ] Autostart works as expected
- [ ] Filter expressions work
- [ ] Field selection persists
- [ ] Authentication (Basic, API Key, None) works
- [ ] HTTPS connections work

### Migration Path for Users

**No Action Required** - Refactoring is backward compatible:
- ✅ Existing preferences preserved
- ✅ Same UI/UX
- ✅ Same configuration options
- ✅ Improved reliability (transparent to users)

---

## P0.2: Comprehensive Unit Testing

### Testing Framework Setup

```gradle
// build.gradle
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testImplementation 'org.mockito:mockito-core:5.5.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.5.0'
    testImplementation 'org.assertj:assertj-core:3.24.2'
}

test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
    }
}
```

### Test Structure

```
src/test/java/com/nccgroup/loggerplusplus/exports/
├── LogShipperExporterTest.java
├── GraylogExporterTest.java
├── ElasticExporterTest.java
└── testutil/
    ├── MockHttpClient.java
    ├── TestLogEntryFactory.java
    └── TestPreferences.java
```

### Example Test: LogShipperExporterTest

```java
package com.nccgroup.loggerplusplus.exports;

import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.nccgroup.loggerplusplus.logentry.LogEntry;
import com.nccgroup.loggerplusplus.logentry.LogEntryField;
import com.nccgroup.loggerplusplus.logentry.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LogShipperExporterTest {

    @Mock
    private ExportController exportController;

    @Mock
    private Preferences preferences;

    private TestLogShipperExporter exporter;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup default preferences
        when(preferences.getSetting(anyString())).thenReturn("");
        when(preferences.getSetting("testDelay")).thenReturn(1); // Fast for testing
        when(preferences.getSetting("testFields")).thenReturn(new ArrayList<LogEntryField>());
        when(preferences.getSetting("testFilter")).thenReturn("");

        exporter = new TestLogShipperExporter(exportController, preferences);
    }

    @Test
    public void testQueueOverflow_shouldDropEntriesAndTrackFailures() throws Exception {
        // Setup
        exporter.setup();
        int maxQueueSize = LogShipperExporter.MAX_QUEUE_SIZE;

        // Fill queue to capacity
        for (int i = 0; i < maxQueueSize; i++) {
            LogEntry entry = createTestEntry();
            exporter.exportNewEntry(entry);
        }

        assertThat(exporter.getQueueSize()).isEqualTo(maxQueueSize);
        assertThat(exporter.getFailedShipments()).isZero();

        // Attempt to add one more (should be dropped)
        LogEntry overflowEntry = createTestEntry();
        exporter.exportNewEntry(overflowEntry);

        assertThat(exporter.getQueueSize()).isEqualTo(maxQueueSize);
        assertThat(exporter.getFailedShipments()).isEqualTo(1);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testConcurrentExport_threadSafety() throws Exception {
        // Setup
        exporter.setup();
        int numThreads = 10;
        int entriesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        // Create threads that will export concurrently
        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    for (int j = 0; j < entriesPerThread; j++) {
                        exporter.exportNewEntry(createTestEntry());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        doneLatch.await(5, TimeUnit.SECONDS);

        // Verify: queue size should be <= total entries (some may have been shipped)
        assertThat(exporter.getQueueSize()).isLessThanOrEqualTo(numThreads * entriesPerThread);

        // Verify: no entries dropped (within queue limit)
        assertThat(exporter.getFailedShipments()).isZero();
    }

    @Test
    public void testConsecutiveFailures_shutdownAfterMax() throws Exception {
        // Setup: Configure exporter to always fail
        exporter.setShouldFailShipment(true);
        exporter.setup();

        // Add entry and trigger shipment
        for (int i = 0; i < LogShipperExporter.MAX_CONSECUTIVE_FAILURES; i++) {
            exporter.exportNewEntry(createTestEntry());
            exporter.forceShipment(); // Manual trigger for testing
        }

        // Verify: Export controller was notified to disable exporter
        verify(exportController, times(1)).disableExporter(exporter);
    }

    @Test
    public void testGracefulShutdown_processesRemainingEntries() throws Exception {
        // Setup
        exporter.setup();

        // Add entries
        int numEntries = 50;
        for (int i = 0; i < numEntries; i++) {
            exporter.exportNewEntry(createTestEntry());
        }

        assertThat(exporter.getQueueSize()).isEqualTo(numEntries);

        // Shutdown
        exporter.shutdown();

        // Verify: All entries were processed before shutdown
        assertThat(exporter.getQueueSize()).isZero();
        assertThat(exporter.getSuccessfulShipments()).isEqualTo(numEntries);
    }

    // Test implementation of LogShipperExporter
    private static class TestLogShipperExporter extends LogShipperExporter {
        private boolean shouldFailShipment = false;
        private List<List<LogEntry>> shippedBatches = new ArrayList<>();

        public TestLogShipperExporter(ExportController controller, Preferences prefs) {
            super(controller, prefs);
            this.fields = new ArrayList<>();
        }

        @Override
        protected void initializeConnection() throws Exception {
            // No-op for testing
        }

        @Override
        protected void shipEntries(List<LogEntry> entries) throws Exception {
            if (shouldFailShipment) {
                throw new RuntimeException("Simulated shipment failure");
            }
            shippedBatches.add(new ArrayList<>(entries));
        }

        @Override
        protected String getFilterPreferenceKey() {
            return "testFilter";
        }

        @Override
        protected String getFieldsPreferenceKey() {
            return "testFields";
        }

        @Override
        protected String getDelayPreferenceKey() {
            return "testDelay";
        }

        @Override
        protected String getExporterName() {
            return "Test Exporter";
        }

        public void setShouldFailShipment(boolean shouldFail) {
            this.shouldFailShipment = shouldFail;
        }

        public void forceShipment() {
            processQueue();
        }

        public List<List<LogEntry>> getShippedBatches() {
            return shippedBatches;
        }
    }

    private LogEntry createTestEntry() {
        LogEntry entry = mock(LogEntry.class);
        when(entry.getStatus()).thenReturn(Status.PROCESSED);
        return entry;
    }
}
```

---

## P0.3: Retry Logic with Exponential Backoff

### Implementation in LogShipperExporter

Complete implementation with configuration support:

```java
public abstract class LogShipperExporter extends AutomaticLogExporter {

    // Retry configuration
    protected int maxRetryAttempts;
    protected long initialRetryDelayMs;
    protected double backoffMultiplier;
    protected long maxRetryDelayMs;

    protected LogShipperExporter(ExportController exportController, Preferences preferences) {
        super(exportController, preferences);

        // Load retry configuration from preferences
        this.maxRetryAttempts = preferences.getSetting(Globals.PREF_EXPORTER_MAX_RETRIES);
        this.initialRetryDelayMs = preferences.getSetting(Globals.PREF_EXPORTER_RETRY_DELAY);
        this.backoffMultiplier = preferences.getSetting(Globals.PREF_EXPORTER_BACKOFF_MULTIPLIER);
        this.maxRetryDelayMs = preferences.getSetting(Globals.PREF_EXPORTER_MAX_RETRY_DELAY);

        // ... rest of constructor
    }

    protected void processQueue() {
        if (pendingEntries.isEmpty()) {
            return;
        }

        List<LogEntry> entriesToShip = new CopyOnWriteArrayList<>();
        pendingEntries.drainTo(entriesToShip);

        if (entriesToShip.isEmpty()) {
            return;
        }

        RetryResult result = shipWithRetry(entriesToShip);

        if (result.success) {
            connectFailedCounter.set(0);
            successfulShipments.addAndGet(entriesToShip.size());
            log.debug("Successfully shipped {} entries after {} attempts",
                entriesToShip.size(), result.attempts);
        } else {
            int failures = connectFailedCounter.incrementAndGet();
            failedShipments.addAndGet(entriesToShip.size());

            log.error("{} failed after {} attempts (consecutive failure {} of {})",
                getExporterName(), result.attempts, failures, MAX_CONSECUTIVE_FAILURES,
                result.lastException);

            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                handleConsecutiveFailures();
            }
        }
    }

    protected RetryResult shipWithRetry(List<LogEntry> entries) {
        int attempt = 0;
        long retryDelay = initialRetryDelayMs;
        Exception lastException = null;

        while (attempt <= maxRetryAttempts) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {}/{} for {} entries",
                        attempt, maxRetryAttempts, entries.size());
                }

                shipEntries(entries);

                // Success
                return new RetryResult(true, attempt + 1, null);

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (isRetryableException(e) && attempt <= maxRetryAttempts) {
                    log.warn("Shipment failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt, maxRetryAttempts + 1, retryDelay, e.getMessage());

                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // Exponential backoff with cap
                    retryDelay = Math.min(
                        (long)(retryDelay * backoffMultiplier),
                        maxRetryDelayMs
                    );
                } else {
                    if (!isRetryableException(e)) {
                        log.error("Non-retryable exception encountered: {}", e.getMessage());
                    }
                    break;
                }
            }
        }

        return new RetryResult(false, attempt, lastException);
    }

    protected boolean isRetryableException(Exception e) {
        // Network timeouts
        if (e instanceof java.net.SocketTimeoutException) return true;
        if (e instanceof java.net.ConnectException) return true;
        if (e instanceof java.net.NoRouteToHostException) return true;

        // HTTP connection failures
        if (e instanceof org.apache.http.conn.HttpHostConnectException) return true;
        if (e instanceof org.apache.http.conn.ConnectTimeoutException) return true;

        // IO exceptions with timeout in message
        if (e instanceof java.io.IOException) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("timeout") || msg.contains("timed out"))) {
                return true;
            }
        }

        // Subclasses can override to add specific logic
        return isCustomRetryableException(e);
    }

    /**
     * Override this method to add exporter-specific retry logic
     */
    protected boolean isCustomRetryableException(Exception e) {
        return false;
    }

    protected static class RetryResult {
        final boolean success;
        final int attempts;
        final Exception lastException;

        RetryResult(boolean success, int attempts, Exception lastException) {
            this.success = success;
            this.attempts = attempts;
            this.lastException = lastException;
        }
    }
}
```

### Configuration in Globals.java

```java
// Retry Configuration
public static final String PREF_EXPORTER_MAX_RETRIES = "exporterMaxRetries";
public static final String PREF_EXPORTER_RETRY_DELAY = "exporterInitialRetryDelay";
public static final String PREF_EXPORTER_BACKOFF_MULTIPLIER = "exporterBackoffMultiplier";
public static final String PREF_EXPORTER_MAX_RETRY_DELAY = "exporterMaxRetryDelay";
```

### Configuration in LoggerPreferenceFactory.java

```java
// Exporter Retry Settings
prefs.registerSetting(PREF_EXPORTER_MAX_RETRIES, Integer.class, 3);
prefs.registerSetting(PREF_EXPORTER_RETRY_DELAY, Integer.class, 2000);  // 2 seconds
prefs.registerSetting(PREF_EXPORTER_BACKOFF_MULTIPLIER, Double.class, 2.0);
prefs.registerSetting(PREF_EXPORTER_MAX_RETRY_DELAY, Integer.class, 32000);  // 32 seconds
```

### Testing Retry Logic

```java
@Test
public void testRetryLogic_successAfterRetries() throws Exception {
    TestLogShipperExporter exporter = new TestLogShipperExporter(exportController, preferences);
    exporter.setFailuresBeforeSuccess(2); // Fail 2 times, then succeed
    exporter.setup();

    exporter.exportNewEntry(createTestEntry());
    exporter.forceShipment();

    // Verify: Successful after retries
    assertThat(exporter.getSuccessfulShipments()).isEqualTo(1);
    assertThat(exporter.getConsecutiveFailures()).isZero();
}

@Test
public void testRetryLogic_permanentFailureAfterMaxRetries() throws Exception {
    TestLogShipperExporter exporter = new TestLogShipperExporter(exportController, preferences);
    exporter.setShouldFailShipment(true); // Always fail
    exporter.setup();

    exporter.exportNewEntry(createTestEntry());
    exporter.forceShipment();

    // Verify: Failed after max retries
    assertThat(exporter.getFailedShipments()).isEqualTo(1);
    assertThat(exporter.getConsecutiveFailures()).isEqualTo(1);
}

@Test
public void testRetryLogic_nonRetryableException() throws Exception {
    TestLogShipperExporter exporter = new TestLogShipperExporter(exportController, preferences);
    exporter.setNonRetryableException(new IllegalArgumentException("Bad request"));
    exporter.setup();

    exporter.exportNewEntry(createTestEntry());
    exporter.forceShipment();

    // Verify: Failed immediately without retries
    assertThat(exporter.getShipmentAttempts()).isEqualTo(1); // Only one attempt
}
```

---

## Summary

These technical specifications provide complete, implementation-ready details for the three immediate priority enhancements:

1. **P0.1**: Step-by-step refactoring guide with complete code
2. **P0.2**: Test framework setup and comprehensive test examples
3. **P0.3**: Full retry logic implementation with configuration

Each specification includes:
- ✅ Complete code examples
- ✅ Configuration changes
- ✅ Testing approaches
- ✅ Migration considerations
- ✅ Integration points

The specifications are ready for immediate implementation in Sprint 1-2 of the enhancement roadmap.
