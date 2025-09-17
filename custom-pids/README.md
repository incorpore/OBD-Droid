# Custom PIDs Templates

This directory contains template files for adding custom PIDs (Parameter IDs) to OBDroid.

## Files Overview

### For Beginners
- **`example_basic_pids.csv`** - Simple PID examples (turbo boost, oil temp, transmission temp)
- **`example_basic_conversions.csv`** - Simple conversion formulas for the basic PIDs

### For Advanced Users
- **`example_advanced_pids.csv`** - Complex PID examples with bitmaps and hash conversions
- **`example_advanced_conversions.csv`** - Advanced conversion formulas including state mappings

### Blank Templates
- **`template_blank_pids.csv`** - Empty PID template with headers only
- **`template_blank_conversions.csv`** - Empty conversions template with headers only

## How to Use

1. **Choose your starting point:**
   - New to custom PIDs? Start with `example_basic_*` files
   - Experienced? Check `example_advanced_*` for complex examples
   - Want to start fresh? Use `template_blank_*` files

2. **Edit the CSV files:**
   - Add your vehicle-specific PIDs
   - Define conversion formulas for your sensors
   - Test with known values if possible

3. **Install on your device:**
   ```
   /sdcard/com.obddroid.ecu.gui.androbd/custom/
   ```
   Or select files through the app's settings menu

4. **Restart OBD-Droid** to load your custom PIDs

## Basic Example Explained

The basic example shows how to add a turbo boost pressure sensor:

### Conversion (in example_basic_conversions.csv):
```
TURBO_BOOST_PSI,LINEAR,0,IMPERIAL,0.145,1,0,-14.7,psi
```
- Converts raw data to PSI
- Applies atmospheric pressure correction (-14.7)
- Formula: `(raw_value * 0.145 / 1) - 14.7`

### PID Definition (in example_basic_pids.csv):
```
0x01,0x67,0,1,0,8,0xFF,TURBO_BOOST_PSI,%.1f,-14.7,30,turbo_boost,Turbo Boost
```
- Service: 0x01 (current data)
- PID: 0x67 (manufacturer specific)
- Links to TURBO_BOOST_PSI conversion
- Display range: -14.7 to 30 PSI

## Conversion Types

- **LINEAR** - Mathematical conversion (multiply, divide, add offset)
- **HASH** - Map numbers to text (1="On", 2="Off")
- **BITMAP** - Decode bit flags (bit 0="Ready", bit 1="Active")
- **ASCII** - Convert to text string

## Tips

- Always backup your working files
- Test one PID at a time when debugging
- Check vehicle documentation for PID specifications
- Use metric or imperial units as needed
- PIDs 0x00-0x20 are standard OBD-II (don't override these)
- PIDs 0x21+ are often manufacturer-specific

## Need Help?

Check the main README.md for detailed documentation on the CSV file format and field descriptions.