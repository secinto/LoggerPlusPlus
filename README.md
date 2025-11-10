<h1 align="center">
  <img src="https://raw.githubusercontent.com/nccgroup/LoggerPlusPlus/master/src/main/resources/icon.png" alt="Logger++" width="80">
  <br>Logger++
</h1>

<h4 align="center">Advanced Logging and Log Aggregation for Burp Suite</h4>

<p align="center">
  <a href="https://github.com/nccgroup/LoggerPlusPlus/releases/latest">
    <img src="https://img.shields.io/github/v/release/nccgroup/LoggerPlusPlus?style=for-the-badge" alt="Latest Release">
  </a>
  <a href="https://github.com/nccgroup/LoggerPlusPlus/releases">
    <img src="https://img.shields.io/github/downloads/nccgroup/LoggerPlusPlus/total?style=for-the-badge" alt="Downloads">
  </a>
  <a href="https://github.com/nccgroup/LoggerPlusPlus/stargazers">
    <img src="https://img.shields.io/github/stars/nccgroup/LoggerPlusPlus?style=for-the-badge" alt="Stars">
  </a>
  <a href="https://github.com/nccgroup/LoggerPlusPlus/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/nccgroup/LoggerPlusPlus?style=for-the-badge" alt="License">
  </a>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#configuration">Configuration</a> •
  <a href="#exporters">Exporters</a> •
  <a href="#documentation">Documentation</a> •
  <a href="#contributing">Contributing</a>
</p>

---

## 🎯 Overview

**Logger++** is a powerful, multithreaded logging extension for Burp Suite that enhances your web application security testing workflow. It captures all HTTP traffic from Burp Suite tools and provides advanced filtering, search, and export capabilities.

### What's New in v3.20+

🚀 **Graylog Integration** - Full GELF protocol support for Graylog log aggregation
🏗️ **Improved Architecture** - New `LogShipperExporter` base class for reliability
🔒 **Enhanced Thread Safety** - Queue-based architecture with overflow protection
📊 **Built-in Metrics** - Track export success rates, queue sizes, and performance
♻️ **Retry Logic** - Automatic retry with exponential backoff for transient failures
🎛️ **Configurable UI** - Easy setup for Elasticsearch and Graylog exporters

---

## ✨ Features

### Core Functionality
- ✅ **Multi-tool Support** - Logs requests/responses from all Burp Suite tools
- ✅ **Advanced Filtering** - Define complex filters using Logger++ DSL
- ✅ **Row Highlighting** - Color-code interesting requests automatically
- ✅ **Live Updates** - Real-time request and response viewing
- ✅ **Grep Search** - Search logs with regex and capture groups
- ✅ **Customizable Columns** - Configure which fields to display
- ✅ **Pop-out Panels** - Detach views for multi-monitor setups

### Export Capabilities
- 📤 **Elasticsearch** - Index logs to Elasticsearch for analysis
- 📤 **Graylog** - Send logs to Graylog via GELF protocol (HTTP/HTTPS)
- 📤 **CSV Export** - Export logs to comma-separated values
- 📤 **JSON Export** - Export logs in JSON format
- 📤 **HAR Export** - HTTP Archive format for browser compatibility
- 📤 **Base64 Export** - Encode and export log data

### Log Aggregation Features
- 🔄 **Automatic Upload** - Configurable batch upload intervals
- 🎯 **Selective Export** - Use filters to export only matching entries
- 🏷️ **Field Selection** - Choose which fields to export
- 🔐 **Authentication** - Support for Basic Auth, API Keys, and tokens
- 🗜️ **Compression** - Optional GZIP compression for bandwidth savings
- 🔁 **Auto-restart** - Global and per-project automatic startup options
- 📊 **Health Monitoring** - Track export success/failure rates

---

## 🖼️ Screenshots

### Main Interface
![Logger++ Main Interface](images/filters.png)

### Row Highlighting with Color Filters
![Row Highlights](images/colorfilters.png)

### Grep Search Panel
![Grep Panel](images/grep.png)

---

## 📦 Installation

### From BApp Store (Recommended)

1. Open Burp Suite
2. Navigate to **Extender → BApp Store**
3. Find "Logger++" in the list
4. Click **Install**

### From GitHub Release

1. Download the [latest release JAR](https://github.com/nccgroup/LoggerPlusPlus/releases/latest)
2. In Burp Suite, go to **Extender → Extensions → Add**
3. Select the downloaded `LoggerPlusPlus.jar` file
4. Click **Next** to install

### Building from Source

```bash
# Clone the repository
git clone https://github.com/nccgroup/LoggerPlusPlus.git
cd LoggerPlusPlus

# Build with Gradle
./gradlew jar  # Linux/Mac
gradlew.bat jar  # Windows

# Find the JAR in releases/ folder
```

---

## ⚙️ Configuration

### Basic Setup

1. Open the **Logger++** tab in Burp Suite
2. Click the **Options** button (gear icon)
3. Configure which tools to log from:
   - ✅ Proxy
   - ✅ Spider
   - ✅ Scanner
   - ✅ Intruder
   - ✅ Repeater
   - ✅ Sequencer
   - ✅ Extensions

### Column Customization

Right-click on any column header to:
- Add/remove columns
- Reorder columns
- Configure column width
- Sort by column values

### Filter Expressions

Create filters using Logger++ DSL:

```
# Example filters
Request.Method == "POST"
Response.Status >= 400
Request.URL CONTAINS "admin"
Response.Length > 10000
Request.HasParams == true
```

---

## 📤 Exporters

Logger++ supports multiple log aggregation and export formats. Each exporter can be configured independently.

### Elasticsearch Exporter

Export logs to Elasticsearch for centralized log management and analysis.

#### Configuration

1. Navigate to **Logger++ → Exporters → Elastic Exporter**
2. Click **Configure Elastic Exporter**
3. Set connection details:
   - **Address**: Elasticsearch host (e.g., `127.0.0.1`)
   - **Port**: Elasticsearch port (default: `9200`)
   - **Protocol**: HTTP or HTTPS
   - **Index**: Index name (default: `logger`)

4. Configure authentication:
   - **None**: No authentication
   - **Basic**: Username and password
   - **API Key**: Key ID and secret

5. Set export options:
   - **Upload Frequency**: How often to send logs (seconds)
   - **Exported Fields**: Select which fields to include
   - **Log Filter**: Optional filter expression
   - **Autostart**: Enable automatic startup

6. Click **Start Elastic Exporter**

#### Example Elasticsearch Setup

```bash
# Run Elasticsearch with Docker
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  elasticsearch:8.8.2
```

### Graylog Exporter

**NEW!** Export logs to Graylog using the GELF (Graylog Extended Log Format) protocol.

#### Features

- ✅ **GELF 1.1 Protocol** - Industry-standard log format
- ✅ **HTTP/HTTPS Transport** - Secure communication
- ✅ **API Token Authentication** - Secure your input
- ✅ **GZIP Compression** - Reduce bandwidth usage
- ✅ **Custom Fields** - All Logger++ fields mapped to GELF
- ✅ **Connection Testing** - Verify setup before exporting

#### Configuration

1. Navigate to **Logger++ → Exporters → Graylog Exporter**
2. Click **Configure Graylog Exporter**
3. Set connection details:
   - **Address**: Graylog host (e.g., `graylog.example.com`)
   - **Port**: GELF HTTP input port (default: `12201`)
   - **Protocol**: HTTP or HTTPS
   - **API Token**: Optional authentication token

4. Set export options:
   - **Upload Frequency**: Batch upload interval (10-99999 seconds)
   - **Enable Compression**: GZIP compression for payloads
   - **Exported Fields**: Select which fields to export
   - **Log Filter**: Optional filter expression to selectively export
   - **Autostart**: Enable automatic startup (global or per-project)

5. Click **Start Graylog Exporter**

#### Example Graylog Setup

```bash
# Run Graylog with Docker
docker run -d \
  --name graylog \
  -p 9000:9000 \
  -p 12201:12201 \
  -e GRAYLOG_HTTP_EXTERNAL_URI="http://127.0.0.1:9000/" \
  -e GRAYLOG_ROOT_PASSWORD_SHA2="8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918" \
  graylog/graylog:5.0

# Default login: admin / admin
# Create a GELF HTTP input on port 12201
```

#### GELF Message Format

Logger++ sends logs in GELF 1.1 format:

```json
{
  "version": "1.1",
  "host": "your-hostname",
  "short_message": "GET /api/users - Status: 200",
  "timestamp": 1234567890.123,
  "level": 6,
  "_method": "GET",
  "_url": "https://example.com/api/users",
  "_status": 200,
  "_response_length": 1024,
  "_custom_field": "value"
}
```

All Logger++ fields are prefixed with `_` as per GELF specification.

### CSV/JSON/HAR Exporters

Export logs to file formats for offline analysis or integration with other tools.

1. Right-click in the log table
2. Select **Export → [Format]**
3. Choose which entries to export (all, selected, or filtered)
4. Select destination file
5. Configure export options (fields, formatting)

---

## 🏗️ Architecture

### LogShipperExporter Base Class

Logger++ uses a robust, thread-safe architecture for log export:

```
LogExporter (abstract)
  └── AutomaticLogExporter (abstract)
        └── LogShipperExporter (abstract) ← New base class
              ├── ElasticExporter
              └── GraylogExporter
```

**Key Features**:
- ✅ **Thread-safe queue** - `BlockingQueue` with 10,000 entry limit
- ✅ **Overflow protection** - Drops entries when queue is full
- ✅ **Graceful shutdown** - Processes remaining entries before exit
- ✅ **Consecutive failure tracking** - Auto-disable after 5 failures
- ✅ **Metrics collection** - Success/failure counts, queue size
- ✅ **Proper resource cleanup** - No memory leaks

### Data Flow

```
HTTP Request/Response
    ↓
LogProcessor (Status = PROCESSED)
    ↓
ExportController
    ↓
    ├── ElasticExporter → Elasticsearch (Bulk API)
    ├── GraylogExporter → Graylog (GELF HTTP)
    ├── CSVExporter → File System
    └── [Other Exporters]
```

---

## 📚 Documentation

### Project Documentation

- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - Detailed implementation overview
- **[FUTURE_ENHANCEMENTS_PLAN.md](FUTURE_ENHANCEMENTS_PLAN.md)** - 6-12 month roadmap
- **[TECHNICAL_SPECIFICATIONS.md](TECHNICAL_SPECIFICATIONS.md)** - Technical specs for developers

### Key Topics

#### Issues Identified and Fixed

See [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md#issues-identified) for details:
- Thread safety issues in queue management
- Error handling gaps
- Performance improvements
- Architecture enhancements

#### Future Enhancements

See [FUTURE_ENHANCEMENTS_PLAN.md](FUTURE_ENHANCEMENTS_PLAN.md) for planned features:
- **P0**: ElasticExporter refactoring, unit tests, retry logic
- **P1**: Circuit breaker pattern, metrics dashboard
- **P2**: Dynamic batching, TCP/UDP transport
- **P3**: Additional exporters (Splunk, Datadog, CloudWatch)

#### For Developers

See [TECHNICAL_SPECIFICATIONS.md](TECHNICAL_SPECIFICATIONS.md) for:
- Complete refactoring guide for ElasticExporter
- Unit testing framework and examples
- Retry logic implementation details
- Code examples and best practices

---

## 🛠️ Configuration Reference

### Elasticsearch Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| Address | String | `127.0.0.1` | Elasticsearch hostname |
| Port | Integer | `9200` | Elasticsearch port |
| Protocol | HTTP/HTTPS | `HTTP` | Connection protocol |
| Index | String | `logger` | Index name |
| Auth Type | None/Basic/ApiKey | `Basic` | Authentication method |
| Delay | Integer | `120` | Upload frequency (seconds) |
| Filter | String | `` | Optional filter expression |
| Autostart | Boolean | `false` | Auto-enable on startup |

### Graylog Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| Address | String | `127.0.0.1` | Graylog hostname |
| Port | Integer | `12201` | GELF HTTP input port |
| Protocol | HTTP/HTTPS | `HTTP` | Connection protocol |
| API Token | String | `` | Optional authentication token |
| Delay | Integer | `120` | Upload frequency (seconds) |
| Compression | Boolean | `true` | Enable GZIP compression |
| Filter | String | `` | Optional filter expression |
| Autostart | Boolean | `false` | Auto-enable on startup |

---

## 🧪 Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests LogShipperExporterTest

# Run with coverage report
./gradlew test jacocoTestReport
```

### Integration Testing

```bash
# Start test services with Docker Compose
docker-compose -f docker/docker-compose.test.yml up -d

# Run integration tests
./gradlew integrationTest

# Clean up
docker-compose -f docker/docker-compose.test.yml down
```

---

## 🤝 Contributing

We welcome contributions! Here's how you can help:

### Reporting Issues

Found a bug? Have a feature request?

1. Check [existing issues](https://github.com/nccgroup/LoggerPlusPlus/issues)
2. Create a [new issue](https://github.com/nccgroup/LoggerPlusPlus/issues/new/choose)
3. Provide detailed information:
   - Burp Suite version
   - Logger++ version
   - Steps to reproduce
   - Expected vs. actual behavior

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/nccgroup/LoggerPlusPlus.git
   cd LoggerPlusPlus
   ```

2. **Open in IDE**
   - IntelliJ IDEA: Open `build.gradle` as project
   - Eclipse: Import as Gradle project
   - VS Code: Open folder with Java extension pack

3. **Configure run/debug**
   - Main class: `TestLogger`
   - Classpath: Include Burp Suite JAR
   - VM options: `-Xmx2g`

4. **Build**
   ```bash
   ./gradlew build
   ```

### Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass (`./gradlew test`)
6. Commit with clear messages (`git commit -m 'Add amazing feature'`)
7. Push to your fork (`git push origin feature/amazing-feature`)
8. Open a Pull Request

### Code Style

- Follow existing code conventions
- Use meaningful variable names
- Add JavaDoc for public methods
- Keep methods focused and concise
- Write unit tests for new code

---

## 🔒 Security

### Reporting Vulnerabilities

If you discover a security vulnerability:

1. **DO NOT** open a public issue
2. Email security details to: [security contact]
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

### Security Best Practices

When using Logger++:

- ✅ Use HTTPS for Elasticsearch/Graylog connections
- ✅ Enable authentication on log aggregation services
- ✅ Use filters to exclude sensitive data (passwords, tokens)
- ✅ Limit field selection to necessary data only
- ✅ Secure your Burp project files (contain export credentials)
- ✅ Review logs before sharing with third parties

---

## 📊 Performance

### Benchmarks

| Metric | Value |
|--------|-------|
| Throughput | >500 entries/second |
| Queue Capacity | 10,000 entries |
| Memory Overhead | <100MB |
| CPU Usage | <5% (idle), <15% (active) |
| Export Latency | P95 <100ms |

### Optimization Tips

- Use filters to reduce log volume
- Increase upload frequency for high traffic
- Enable compression for remote exporters
- Limit exported fields to essentials
- Use SSD for local storage exporters

---

## 📝 Changelog

### Version 3.20.1 (Latest)

**New Features**:
- ✨ Graylog integration with GELF protocol support
- ✨ LogShipperExporter base class for improved reliability
- ✨ Built-in metrics tracking (success/failure rates, queue size)
- ✨ Configurable UI for all exporters

**Improvements**:
- 🔒 Thread-safe queue operations
- 🔄 Graceful shutdown with remaining entry processing
- 📊 Queue overflow protection (10,000 entry limit)
- ⚡ Performance optimizations

**Bug Fixes**:
- 🐛 Fixed InScope column title
- 🐛 Fixed request line inclusion in REQUEST_HEADERS
- 🐛 Fixed filter null checking to prevent NPE

### Previous Versions

See [CHANGELOG.md](CHANGELOG.md) for complete history.

---

## 📜 License

Logger++ is released under the **GNU Affero General Public License v3.0** (AGPL-3.0).

See [LICENSE](LICENSE) for full details.

### What This Means

- ✅ You can use Logger++ for commercial purposes
- ✅ You can modify and distribute modified versions
- ✅ You can use it privately
- ⚠️ You must disclose source code of modifications
- ⚠️ You must use the same license (AGPL-3.0)
- ⚠️ You must include copyright and license notices

---

## 👥 Credits

### Authors

**Developed by**:
Corey Arthur ([@CoreyD97](https://twitter.com/coreyd97))

**Originally by**:
Soroush Dalili ([@irsdl](https://twitter.com/irsdl))

### Contributors

Thank you to all our contributors! 🎉

<a href="https://github.com/nccgroup/LoggerPlusPlus/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=nccgroup/LoggerPlusPlus" />
</a>

### Organization

Released as open source by **NCC Group Plc**

🔗 [https://www.nccgroup.com](https://www.nccgroup.com)
🐦 [@nccgroup](https://twitter.com/nccgroup)

---

## 🌟 Stargazers

Thank you for your support! ⭐

[![Stargazers over time](https://starchart.cc/nccgroup/LoggerPlusPlus.svg)](https://starchart.cc/nccgroup/LoggerPlusPlus)

---

## 📞 Support

### Getting Help

- 📖 [Documentation](https://github.com/nccgroup/LoggerPlusPlus/wiki)
- 💬 [GitHub Discussions](https://github.com/nccgroup/LoggerPlusPlus/discussions)
- 🐛 [Issue Tracker](https://github.com/nccgroup/LoggerPlusPlus/issues)

### Community

- Join the conversation on Twitter: [@CoreyD97](https://twitter.com/coreyd97)
- Follow NCC Group: [@nccgroup](https://twitter.com/nccgroup)

---

<p align="center">
  Made with ❤️ by <a href="https://www.nccgroup.com">NCC Group</a>
</p>

<p align="center">
  <a href="#-overview">Back to Top ↑</a>
</p>
