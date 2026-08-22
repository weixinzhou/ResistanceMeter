# Resistance Meter Peak Detection APP Development Guide

## 1. Project Overview

### 1.1 Application Description
This application is a smart resistance meter application based on the Android platform, specifically designed for real-time resistance measurement and peak detection via Bluetooth connection to ESP32-S3 devices.

### 1.2 Core Features
- **Bluetooth Connection Management**: Scan, connect, and disconnect ESP32-S3 smart sensor devices
- **Real-time Data Acquisition**: Real-time monitoring of single-channel resistance values
- **Peak Detection**: Smart algorithm to detect peak points of resistance values
- **Data Visualization**: Real-time chart displaying the trend of resistance value changes
- **Data Export**: Supports exporting data in CSV, JSON, and TXT formats (auto-saving)
- **Baseline Setting**: Dynamic setting of resistance baseline values
- **Alarm System**: Sound alarm and recording function for exceeding thresholds
- **Device Control**: Remote control of the working status of measurement devices

### 1.3 Technology Stack
- **Development Language**: Kotlin
- **UI Framework**: Android View System + Jetpack Compose (Partial)
- **Architecture Pattern**: MVVM + LiveData + ViewModel
- **Bluetooth Communication**: Bluetooth LE (BLE)
- **Chart Library**: MPAndroidChart
- **Permission Management**: EasyPermissions
- **Data Storage**: SharedPreferences + Automatic File Saving
- **Audio System**: MediaPlayer + Vibration Feedback

## 2. Project Architecture

### 2.1 Overall Architecture Diagram
```
┌─────────────────────────────────────────┐
│                UI Layer                 │
│  ┌─────────────────────────────────────┐│
│  │        MainActivity                 ││
│  │    ├── ConnectionFragment          ││
│  │    ├── ChartFragment               ││
│  │    ├── ControlFragment             ││
│  │    └── SettingsFragment            ││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│              ViewModel Layer            │
│  ┌─────────────────────────────────────┐│
│  │    BluetoothViewModel               ││
│  │    ChartViewModel                   ││
│  │    DataViewModel                    ││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│               Service Layer             │
│  ┌─────────────────────────────────────┐│
│  │         BluetoothService            ││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│               Utils Layer               │
│  ┌─────────────────────────────────────┐│
│  │    PeakDetector                     ││
│  │    DataParser                       ││
│  │    DataExporter                     ││
│  │    BluetoothUtils                   ││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

### 2.2 Package Structure
```
com.smartsensor.resistancemeter/
├── MainActivity.kt
├── adapter/
│   └── DeviceAdapter.kt
├── fragment/
│   ├── ChartFragment.kt
│   ├── ConnectionFragment.kt
│   ├── ControlFragment.kt
│   └── SettingsFragment.kt
├── model/
│   ├── DataBatch.kt
│   ├── DataPacket.kt
│   ├── DeviceInfo.kt
│   ├── PeakData.kt
│   ├── PeakDetectionSettings.kt
│   ├── PeakStatistics.kt
│   ├── ResistanceData.kt
│   ├── BaselineSettings.kt
│   └── AlarmRecord.kt
├── service/
│   └── BluetoothService.kt
├── ui/
│   └── PeakSettingsDialog.kt
├── utils/
│   ├── AutoSaveFileManager.kt
│   ├── AutoSaveManager.kt
│   ├── BluetoothUtils.kt
│   ├── Constants.kt
│   ├── DataExporter.kt
│   ├── DataParser.kt
│   ├── DeviceFilter.kt
│   ├── PeakDetector.kt
│   ├── BaselineManager.kt
│   └── AlarmManager.kt
└── viewmodel/
    ├── BluetoothViewModel.kt
    ├── ChartViewModel.kt
    └── DataViewModel.kt
```

## 3. Core Modules Details

### 3.1 Bluetooth Communication Module (BluetoothService)

#### Features
- **Device Scanning**: Scan for nearby ESP32-S3 devices
- **Connection Management**: Establish and maintain BLE connections
- **Data Reception**: Receive resistance measurement data in real-time
- **Error Handling**: Handle connection exceptions and data parsing errors

#### Key Classes
- `BluetoothService`: Core Bluetooth service class
- `BluetoothUtils`: Bluetooth utility class, handling permissions and device compatibility
- `DeviceInfo`: Device information data model

#### Data Protocol
- Service UUID: `ab0b0000-0bb0-1000-8000-00805f9b34fb`
- Characteristic UUID: `ab0b0001-0bb0-1000-8000-00805f9b34fb`
- Data Packet Format: `[Header][Command][Data][Checksum]`

### 3.2 Data Processing Module

#### Data Models
```kotlin
data class ResistanceData(
    val resistance: Float,     // Resistance value
    val timestamp: Long,       // Timestamp
    val status: Int,          // Device status
    val isAlarmTriggered: Boolean = false  // Whether an alarm is triggered
)

// Newly added baseline settings data model
data class BaselineSettings(
    val baselineValue: Float = 0f,     // Baseline resistance value
    val alarmThreshold: Float = 10f,   // Alarm threshold percentage
    val isBaselineSet: Boolean = false, // Whether the baseline is set
    val baselineTimestamp: Long = 0L   // Baseline setting time
)

// Newly added alarm record data model
data class AlarmRecord(
    val timestamp: Long,               // Alarm time
    val resistanceValue: Float,       // Resistance value that triggered the alarm
    val baselineValue: Float,         // Baseline value at that time
    val thresholdPercentage: Float,   // Threshold percentage
    val exceedancePercentage: Float   // Actual exceeded percentage
)
```

#### Core Components
- `DataParser`: Raw data packet parsing
- `DataViewModel`: Data state management
- `DataExporter`: Data export functionality (manual export button removed, changed to auto-save)
- `BaselineManager`: Baseline setting and management
- `AlarmManager`: Alarm system management

### 3.3 Peak Detection Module (PeakDetector)

#### Algorithm Features
- **Single Channel Detection**: Focuses on peak detection for a single resistance path
- **Smart Filtering**: Data smoothing and noise filtering
- **Threshold Setting**: Configurable peak detection thresholds
- **Statistical Analysis**: Peak frequency and trend analysis
- **Baseline Tracking**: Relative detection based on dynamic baseline

#### Detection Parameters
```kotlin
data class PeakDetectionSettings(
    val minimumPeakHeight: Float = 1.0f,     // Minimum peak height
    val minimumDistance: Int = 3,            // Minimum distance between peaks
    val detectMaximum: Boolean = true,        // Detect maximum peaks
    val detectMinimum: Boolean = true,        // Detect minimum peaks
    val smoothingWindow: Int = 3,            // Smoothing window size
    val noiseThreshold: Float = 0.1f,        // Noise threshold
    val useBaselineRelative: Boolean = true  // Whether to use baseline relative detection
)
```

#### Peak Types
```kotlin
enum class PeakType {
    MAXIMUM,    // Maximum peak
    MINIMUM     // Minimum peak
}
```

### 3.4 Data Visualization Module (ChartFragment)

#### Chart Features
- **Real-time Plotting**: Real-time curve of single-channel resistance values
- **Peak Marking**: Automatically marks detected peak points
- **Baseline Display**: Displays the currently set baseline value
- **Alarm Region**: Highlights the alarm threshold region
- **Zoom & Scroll**: Supports chart zooming and horizontal scrolling
- **Data Point Limit**: Displays a maximum of 100 data points

#### Chart Configuration
- Uses MPAndroidChart library
- Main data line (resistance value) + baseline reference line
- Alarm threshold region highlighted with colors
- Custom timeline formatting
- Dynamic data updates and alarm status indicators

### 3.5 Baseline Management Module (BaselineManager)

#### Features
- **Dynamic Baseline Setting**: Obtains the current resistance value in real-time as the baseline
- **Baseline Storage**: Persistently saves baseline settings and historical records
- **Baseline Updating**: Supports resetting and adjusting the baseline value
- **Relative Calculation**: Calculates percentage changes based on the baseline

#### Core Methods
```kotlin
class BaselineManager {
    // Sets the current resistance value as the baseline
    fun setBaseline(currentResistance: Float): BaselineSettings
    
    // Gets the current baseline settings
    fun getCurrentBaseline(): BaselineSettings?
    
    // Calculates the percentage change relative to the baseline
    fun calculateChangePercentage(currentValue: Float, baseline: Float): Float
    
    // Resets the baseline settings
    fun resetBaseline()
    
    // Gets the baseline history records
    fun getBaselineHistory(): List<BaselineSettings>
}
```

### 3.6 Alarm System Module (AlarmManager)

#### Features
- **Real-time Monitoring**: Continuously monitors resistance value changes
- **Smart Judgment**: Alarm logic based on baseline and thresholds
- **Multimedia Alerts**: Sound + vibration alarms
- **Record Management**: Complete recording of alarm events

#### Alarm Trigger Conditions
```kotlin
// Alarm trigger logic
fun shouldTriggerAlarm(
    currentValue: Float,
    baselineSettings: BaselineSettings
): Boolean {
    if (!baselineSettings.isBaselineSet) return false
    
    val changePercentage = abs((currentValue - baselineSettings.baselineValue) / 
                             baselineSettings.baselineValue * 100)
    
    return changePercentage >= baselineSettings.alarmThreshold
}
```

#### Alarm Management Methods
```kotlin
class AlarmManager {
    // Checks and triggers the alarm
    fun checkAndTriggerAlarm(resistanceData: ResistanceData)
    
    // Plays the alarm sound
    fun playAlarmSound()
    
    // Triggers vibration
    fun triggerVibration()
    
    // Records the alarm event
    fun recordAlarmEvent(alarmRecord: AlarmRecord)
    
    // Gets the alarm history
    fun getAlarmHistory(): List<AlarmRecord>
    
    // Sets the alarm threshold
    fun setAlarmThreshold(percentage: Float)
}
```

## 4. Permissions and Configuration

### 4.1 Required Permissions
```xml
<!-- Bluetooth permissions (Android 12+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

<!-- Legacy Bluetooth permissions (Android 11-) -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

<!-- Location permissions (Required for Bluetooth scanning) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Storage permissions (Auto data saving) -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- Audio permissions (Alarm sound) -->
<uses-permission android:name="android.permission.VIBRATE" />
```

### 4.2 Hardware Requirements
```xml
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="true" />
```

### 4.3 Build Configuration
- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 35 (Android 15)
- **compileSdk**: 35

## 5. Development Workflow

### 5.1 Environment Setup
1. Install Android Studio
2. Configure Kotlin development environment
3. Add necessary dependency libraries
4. Configure permissions and hardware requirements

### 5.2 Core Development Steps
1. **Bluetooth Module Development**
   - Implement device scanning functionality
   - Establish BLE connections
   - Implement data communication protocols

2. **Data Processing Development**
   - Data packet parsing
   - Data model design
   - Data validation and filtering

3. **Peak Detection Development**
   - Algorithm implementation
   - Parameter configuration
   - Statistical analysis

4. **UI Interface Development**
   - Fragment page design
   - Data binding
   - User interaction

5. **Data Export Development**
   - File format support
   - Storage permission handling
   - Implementation of auto-save functionality

6. **Baseline Setup Development**
   - Baseline acquisition and storage
   - Dynamic baseline updating
   - Baseline-related calculations

7. **Alarm System Development**
   - Threshold detection algorithm
   - Sound and vibration alarms
   - Alarm record management

### 5.3 Testing Workflow
1. **Unit Testing**: Core algorithms and data processing
2. **Integration Testing**: Bluetooth communication and data flow
3. **UI Testing**: Interface interaction and data display
4. **Performance Testing**: Memory usage and data processing efficiency
5. **Compatibility Testing**: Various Android versions and devices

## 6. Functional Module Detailed Planning

### 6.1 Baseline and Alarm Features (New Core Features)

#### Baseline Settings Interface
- **Set Baseline Button**: Acquires the current resistance value as the baseline
- **Baseline Value Display**: Real-time display of the current baseline value and setup time
- **Baseline Reset Function**: Clears the currently set baseline
- **Baseline History View**: Views historical baseline setting records

#### Alarm Configuration Interface
- **Threshold Slider**: Visually adjusts the alarm threshold percentage
- **Alarm Switch**: Enables/disables the alarm feature
- **Alarm Type Selection**: Sound, vibration, or both
- **Test Alarm Button**: Tests the alarm effect

#### Alarm History Management
- **Alarm Record List**: Displays all alarm events
- **Alarm Details View**: Specific alarm time, value, and exceeded percentage
- **Alarm Statistical Analysis**: Alarm frequency and trend charts
- **Alarm Record Export**: Exports historical alarm data

### 6.2 Data Analysis Features (Enhancement of Existing Features)
- **Statistical Reports**: Relative change analysis based on the baseline
- **Data Comparison**: Comparison of data before and after baseline settings
- **Anomaly Detection**: Anomaly point identification based on the baseline
- **Trend Analysis**: Resistance value changing trend and period analysis

### 6.3 Device Management Features
- **Device Configuration**: Remotely configures measurement parameters
- **Firmware Upgrade**: OTA firmware updates
- **Device Status Monitoring**: Battery level, temperature, and other status info
- **Multi-device Support**: Connects multiple devices simultaneously

### 6.4 Data Storage Features
- **Local Database**: SQLite data storage
- **Cloud Synchronization**: Data cloud backup and sync
- **Data Compression**: Large data volume compression storage
- **Data Encryption**: Sensitive data encryption protection

### 6.5 User Experience Features
- **Theme Switching**: Dark/light themes
- **Internationalization**: Multi-language support
- **Custom Configuration**: User preference settings
- **Help Documentation**: Built-in help and tutorials

## 7. Performance Optimization Suggestions

### 7.1 Memory Management
- Make use of LiveData for data observation
- Timely release of memory for Bitmaps and large objects
- Proper use of circular buffers
- Prevent memory leaks

### 7.2 Data Processing Optimization
- Process data using background threads
- Process data packets in batches
- Set reasonable data sampling rates
- Utilize efficient data structures

### 7.3 UI Performance Optimization
- Use ViewBinding to minimize layout lookups
- Optimize RecyclerView performance
- Reduce unnecessary UI updates
- Utilize hardware acceleration

### 7.4 Battery Optimization
- Set reasonable Bluetooth scanning intervals
- Timely disconnect unused connections
- Use low power data transmissions
- Optimize background tasks

## 8. Common Issues and Solutions

### 8.1 Bluetooth Connection Issues
**Issue**: Device connection failure or unstable connections
**Solutions**:
- Check permission settings
- Implement a connection retry mechanism
- Add connection status monitoring
- Optimize connection parameters

### 8.2 Data Synchronization Issues
**Issue**: Data loss or disorder
**Solutions**:
- Add data packet sequence numbers
- Implement a data buffering mechanism
- Sort using timestamps
- Add data integrity verification

### 8.3 Peak Detection False Alarms
**Issue**: Noise interference causing false detections
**Solutions**:
- Adjust detection thresholds
- Increase data smoothing
- Use multiple verifications
- Add noise filtering

### 8.4 Performance Issues
**Issue**: App freezing or crashing
**Solutions**:
- Optimize data processing algorithms
- Use asynchronous processing
- Reduce UI update frequency
- Implement exception handling

## 9. Deployment and Release

### 9.1 Packaging Configuration
- Configure signature files
- Set ProGuard rules
- Optimize APK size
- Include version information

### 9.2 Test Deployment
- Internal testing release
- Compatibility testing
- Performance stress testing
- User feedback collection

### 9.3 Formal Release
- App store listing
- Version update management
- User support services
- Statistical data analysis

## 10. Technical Documentation

### 10.1 API Documentation
- Bluetooth communication interfaces
- Data processing interfaces
- Peak detection interfaces
- Export feature interfaces

### 10.2 Development Documentation
- Code specifications and conventions
- Module design documents
- Data flow charts
- Error handling mechanisms

### 10.3 User Manual
- Function usage instructions
- Troubleshooting guides
- Frequently Asked Questions
- Technical support contact info

## 11. New Feature Implementation Guidelines

### 11.1 Single-Channel Modification Guidelines

#### Data Model Modifications
```kotlin
// Modify ResistanceData.kt
data class ResistanceData(
    val resistance: Float,              // Single resistance value
    val timestamp: Long,
    val status: Int,
    val isAlarmTriggered: Boolean = false
)
```

#### UI Interface Adjustments
- The chart displays a single data line
- Remove the multi-channel selector
- Simplify the display interface layout

### 11.2 Baseline Feature Implementation Steps

#### Step 1: Create Data Model
```kotlin
// Create BaselineSettings.kt
data class BaselineSettings(
    val baselineValue: Float = 0f,
    val alarmThreshold: Float = 10f,
    val isBaselineSet: Boolean = false,
    val baselineTimestamp: Long = 0L
)
```

#### Step 2: Implement Baseline Manager
```kotlin
// Create BaselineManager.kt
class BaselineManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("baseline_prefs", Context.MODE_PRIVATE)
    
    fun setBaseline(currentResistance: Float): BaselineSettings {
        val settings = BaselineSettings(
            baselineValue = currentResistance,
            isBaselineSet = true,
            baselineTimestamp = System.currentTimeMillis()
        )
        saveBaseline(settings)
        return settings
    }
    
    private fun saveBaseline(settings: BaselineSettings) {
        prefs.edit().apply {
            putFloat("baseline_value", settings.baselineValue)
            putFloat("alarm_threshold", settings.alarmThreshold)
            putBoolean("is_baseline_set", settings.isBaselineSet)
            putLong("baseline_timestamp", settings.baselineTimestamp)
            apply()
        }
    }
}
```

#### Step 3: UI Interface Integration
- Add a "Set Baseline" button in `ControlFragment`
- Display a baseline reference line in the chart
- Add baseline configuration options in the settings interface

### 11.3 Alarm Feature Implementation Steps

#### Step 1: Create Alarm Manager
```kotlin
// Create AlarmManager.kt
class AlarmManager(private val context: Context) {
    private val mediaPlayer = MediaPlayer()
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    fun checkAndTriggerAlarm(
        currentValue: Float, 
        baselineSettings: BaselineSettings
    ) {
        if (shouldTriggerAlarm(currentValue, baselineSettings)) {
            triggerAlarm(currentValue, baselineSettings)
        }
    }
    
    private fun triggerAlarm(currentValue: Float, baseline: BaselineSettings) {
        playAlarmSound()
        triggerVibration()
        recordAlarmEvent(currentValue, baseline)
    }
}
```

#### Step 2: Integrate into Data Flow
- Integrate alarm checks into `DataViewModel`
- Automatically check if an alarm is triggered when new data is received
- Update the UI to display the alarm status

#### Step 3: Alarm Record Storage
```kotlin
// Create AlarmRecord.kt
data class AlarmRecord(
    val timestamp: Long,
    val resistanceValue: Float,
    val baselineValue: Float,
    val thresholdPercentage: Float,
    val exceedancePercentage: Float
)
```

### 11.4 Auto-Save Feature Modification

#### Remove Manual Save Button
- Remove the force save button from the UI layout
- Remove related click event processing

#### Enhance Auto-Save
```kotlin
// Modify AutoSaveManager.kt
class AutoSaveManager {
    // Add auto-saving for alarm events
    fun autoSaveAlarmEvent(alarmRecord: AlarmRecord) {
        // Automatically save the alarm record to a file
    }
    
    // Add auto-saving for baseline changes
    fun autoSaveBaselineChange(baselineSettings: BaselineSettings) {
        // Automatically save baseline setting changes
    }
}
```

### 11.5 Testing and Verification Checklist

#### Functional Testing
- [ ] Baseline setup feature functions properly
- [ ] Alarm trigger conditions are accurate
- [ ] Sound and vibration alarms work normally
- [ ] Auto-save feature is stable
- [ ] Single-channel data displays correctly

#### Performance Testing  
- [ ] Alarm checks do not affect data acquisition performance
- [ ] Audio playback does not block the UI thread
- [ ] Memory usage remains within a reasonable range

#### User Experience Testing
- [ ] Baseline setup operations are simple and intuitive
- [ ] Alarm alerts are timely and effective
- [ ] Interface layout is clear and logical

---

*This development guide will be continuously updated according to project progress and requirement changes.*