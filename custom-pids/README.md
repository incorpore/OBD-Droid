# Custom PID Configuration Templates

These CSV files allow you to add support for vehicle-specific PIDs and data conversions that aren't part of the standard OBD-II protocol.

## Files

### cust_pids.csv
Defines custom Parameter IDs (PIDs) for your vehicle. Use this to add:
- Manufacturer-specific PIDs
- Aftermarket sensor PIDs
- Custom calculated parameters

### cust_conversions.csv
Defines how to convert raw OBD data into human-readable values. Supports:
- **LINEAR**: Mathematical conversions (multiply, divide, offset)
- **HASH**: Map numbers to text states
- **BITMAP**: Decode bit-masked status flags

## How to Use

1. **Edit these template files** with your custom PIDs
2. **Copy to your Android device**: `/sdcard/com.obddroid.ecu.gui.androbd/custom/`
3. **Select in app settings** or files will auto-load on startup
4. **Restart the app** to load your custom PIDs

## Example: Adding a Custom Sensor

To add a boost pressure sensor at PID 0x99:

**In cust_conversions.csv:**
```csv
BOOST_PSI,LINEAR,0,IMPERIAL,0.145,1,0,0,psi,Boost pressure conversion
```

**In cust_pids.csv:**
```csv
0x01,0x99,0,2,0,16,0xFFFF,BOOST_PSI,%.1f,0,30,boost,Boost Pressure,Custom boost sensor
```

## Column Descriptions

### cust_pids.csv Columns:
- `svc`: Service mode (0x01, 0x02, etc.)
- `pid`: Parameter ID in hex
- `ofs`: Byte offset in response
- `len`: Data length in bytes
- `bit_ofs`: Bit offset for partial byte data
- `bit_len`: Number of bits to read
- `bit_mask`: Bitmask for data extraction
- `formula`: Name of conversion formula to use
- `format`: Display format (printf style)
- `min/max`: Expected value range
- `mnemonic`: Short identifier
- `label`: Display name in app
- `Remarks`: Notes/description

### cust_conversions.csv Columns:
- `CONVERSION_ID`: Unique name for the conversion
- `TYPE`: LINEAR, HASH, or BITMAP
- `VARIANT`: Version number (usually 0)
- `SYSTEM`: METRIC or IMPERIAL
- `FACT`: Multiplication factor
- `DIV`: Division factor
- `OFFS`: Offset to add
- `PhOf`: Physical offset (rarely used)
- `UNIT`: Display unit (psi, g/min, etc.)
- `Remark`: Conversion description
- `Parameters`: For HASH/BITMAP - value mappings
- `Beschreibung`: German description (optional)

## Tips

- Test with one PID at a time to debug issues
- Use existing entries as templates
- Keep backups of working configurations
- Check app logs if PIDs don't appear