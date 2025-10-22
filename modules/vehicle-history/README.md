# Vehicle History Module

This module provides vehicle history report functionality for OBD-Droid, including integration with AutoCheck services.

## Structure

- `/src/main/java` - Core Java classes (platform independent)
  - `com.obddroid.vehicle.AutoCheckReport` - Data model for vehicle history reports

- `/android/vehicle-history-android` - Android-specific implementation
  - `com.obddroid.vehiclehistory.AutoCheckService` - Service for fetching AutoCheck reports

## Features

- AutoCheck vehicle history report integration
- Report caching and storage
- PDF report generation
- Score analysis and vehicle comparison

## Dependencies

### Java Module
- JSON handling (org.json)

### Android Module
- OkHttp for network operations
- Gson for JSON serialization
- Android AppCompat

## Usage

Add the module dependency to your app's build.gradle:

```gradle
dependencies {
    implementation project(':vehicle-history-android')
}
```

Then use the AutoCheckService to fetch reports:

```java
import com.obddroid.vehiclehistory.AutoCheckService;
import com.obddroid.vehicle.AutoCheckReport;

AutoCheckService service = new AutoCheckService(context);
service.fetchReport(vin, new AutoCheckService.AutoCheckCallback() {
    @Override
    public void onSuccess(AutoCheckReport report) {
        // Handle successful report
    }

    @Override
    public void onError(String errorMessage) {
        // Handle error
    }
});
```

## API Configuration

The AutoCheckService connects to the backend API at `http://localhost:3248` using adb reverse tunnel. Make sure the backend service is running when testing.