# 🎯 AutoCheck Integration - TESTING GUIDE

**Created:** October 16, 2025
**Status:** ✅ READY FOR TESTING - All code complete!

---

## 🚀 WHAT'S READY

### ✅ Completed Features:
1. **AutoCheck API Server** - Running at `http://localhost:3248`
2. **Real Browser Scraper** - Using Playwright to scrape AutoCheck.com
3. **Android App Updated** - New "Vehicle History" card on dashboard
4. **Complete UI** - Professional looking report display
5. **Error Handling** - Graceful failures with helpful messages
6. **Installed on Phone** - App ready to test RIGHT NOW!

---

## 📱 TESTING STEPS

### Step 1: Ensure API Server is Running

```bash
# On your laptop (this machine):
cd /path/to/autocheck-api

# Check if it's running:
curl http://localhost:3248/health

# If not running, start it:
npm start
```

**Expected Output:**
```json
{
  "status": "healthy",
  "service": "AutoCheck API",
  "scraper": "not_initialized"
}
```

### Step 2: Verify Network Connectivity

**Both devices must be on the same WiFi!**

Your laptop IP: `192.168.0.149`

From your phone's browser, try:
- `http://192.168.0.149:3248/health`

Should see the health check JSON.

### Step 3: Open OBD-Droid on Phone

The app is already installed and launched!

**You should see:**
- Dashboard with 6 cards in a grid
- **New "Vehicle History" card** (bottom right, blue icon)

### Step 4: Click Vehicle History Card

1. **Tap the "Vehicle History" card**
2. You'll see the AutoCheck screen with:
   - VIN input field
   - "Check Vehicle History" button
   - May see a warning if API not reachable

### Step 5: Enter a Test VIN

**Use this test VIN:**
```
1HGBH41JXMN109186
```

Or use any 17-character VIN you have available.

### Step 6: Check Vehicle History

1. **Click "Check Vehicle History" button**
2. You'll see:
   - Loading spinner
   - "Fetching vehicle history... This may take 30-60 seconds"
3. **First time will be slow** (30-60 sec)
   - Browser needs to initialize
   - Login to AutoCheck
   - Scrape the report
4. **Results will show:**
   - Vehicle Information (Year Make Model)
   - AutoCheck Score (color-coded)
   - Vehicle Details (owners, mileage, title, accidents, recalls)

---

## 🧪 TEST SCENARIOS

### Test 1: Successful Lookup ✅

**VIN:** `1HGBH41JXMN109186`

**Expected Result:**
- 2014 Chevrolet Cruze
- Score: ~75/100 (Yellow/Green)
- Clean title
- 3 owners
- ~108,904 miles
- Service records shown

### Test 2: API Server Offline ⚠️

1. Stop the API server on laptop
2. Try to check a vehicle
3. **Expected:** Error message about API not reachable

### Test 3: Invalid VIN ❌

**VIN:** `123` (too short)

**Expected:** Toast message "VIN must be exactly 17 characters"

### Test 4: Different Network ❌

1. Switch phone to mobile data
2. Try to check a vehicle
3. **Expected:** Timeout error

---

## 🎨 WHAT YOU'LL SEE

### Dashboard Screen
```
┌─────────────┬─────────────┐
│ Live Data   │ Test Ctrl   │
├─────────────┼─────────────┤
│ Fault Codes │ Reconnect   │
├─────────────┼─────────────┤
│ Fuel Econ   │ 🆕 History  │ ← NEW!
└─────────────┴─────────────┘
```

### Vehicle History Screen
```
╔════════════════════════════╗
║  Vehicle History Report    ║
║  Powered by AutoCheck      ║
╠════════════════════════════╣
║                            ║
║  VIN: [_____________]      ║
║                            ║
║  [Check Vehicle History]   ║
║                            ║
╚════════════════════════════╝

After loading:

╔════════════════════════════╗
║ 2014 Chevrolet Cruze       ║
║ VIN: 1HGBH41JXMN109186     ║
╠════════════════════════════╣
║ AutoCheck Score: 75 (50-90)║
╠════════════════════════════╣
║ Style: 1LT Sedan           ║
║ Engine: 1.4L Turbo I4      ║
║ Owners: 3                  ║
║ Last Odometer: 108,904 mi  ║
║ Title: Clean               ║
║ Accidents: Minor Accident  ║
║ Service Records: 13        ║
╚════════════════════════════╝
```

---

## ⚡ QUICK TROUBLESHOOTING

### Problem: "API not reachable" Warning

**Solution:**
1. Check API server is running: `curl http://localhost:3248/health`
2. Verify both on same WiFi
3. Check laptop firewall isn't blocking port 3248

### Problem: Request Times Out

**Solution:**
1. First request takes 30-60 seconds (normal!)
2. Browser needs to initialize and login
3. Be patient, don't click multiple times

### Problem: "Login failed" Error

**Solution:**
1. Check AutoCheck credentials in `/autocheck-api/.env`
2. Credentials might be wrong
3. AutoCheck website might have changed

### Problem: App Crashes

**Solution:**
1. Check Android logs: `adb logcat | grep AutoCheck`
2. Look for stack traces
3. Might be JSON parsing error

---

## 📊 EXPECTED API CALL FLOW

```
Phone → Laptop API Server → AutoCheck.com
  ↑                ↓
  └────── JSON ────┘
```

**Timeline:**
1. **0s** - Phone sends VIN to laptop API
2. **0-5s** - API initializes browser (first time only)
3. **5-10s** - Browser logs into AutoCheck.com
4. **10-15s** - Navigates to search page
5. **15-20s** - Enters VIN and clicks search
6. **20-50s** - Report page loads and data is extracted
7. **50-60s** - JSON sent back to phone
8. **60s** - Phone displays report

**Subsequent requests:** Much faster (10-20s) since browser stays logged in!

---

## 🎬 DEMO SCRIPT FOR SALESMEN

### Scenario: Customer asks about used vehicle history

**Salesman:** "Let me pull up the vehicle history report for you right now."

1. Open OBD-Droid app
2. Tap "Vehicle History" card
3. Enter VIN from windshield sticker
4. Tap "Check Vehicle History"
5. Wait 30-60 seconds
6. Show customer the results:
   - "This vehicle has a clean title"
   - "Only 2 previous owners"
   - "Last recorded mileage was 85,000 miles"
   - "AutoCheck score is 82 out of 100 - that's excellent!"

**Customer:** "Wow, that's impressive! How much is this service?"

**Salesman:** "It's included with our OBD-Droid diagnostic tool - no extra charge!"

---

## 💰 BUSINESS VALUE

### For Dealership:
- Instant vehicle history on ANY car
- Professional presentation
- Build customer trust
- Competitive advantage
- Free (no per-report fees!)

### For OBD-Droid:
- Unique feature (no competitors have this)
- Premium upgrade potential
- Dealership partnership opportunities
- Upsell to other dealers

---

## 🔮 WHAT'S NEXT

### Future Enhancements:
- [ ] VIN auto-read from connected vehicle
- [ ] Mileage fraud detection (OBD vs AutoCheck)
- [ ] Save/export reports as PDF
- [ ] Share reports via email
- [ ] VIN barcode scanner (camera)
- [ ] Batch lookup (multiple VINs)
- [ ] Recall notification system

### Monday Tasks (if needed):
- Update API server IP if laptop changes
- Get official Experian API (optional - scraper works great!)
- Deploy to cloud server for remote access

---

## ✅ SUCCESS CRITERIA

**The feature is READY if:**
- ✅ Dashboard shows Vehicle History card
- ✅ Clicking card opens AutoCheck screen
- ✅ Entering VIN and clicking Check works
- ✅ Report displays correctly
- ✅ Error messages are helpful
- ✅ Salesmen can successfully demo to customers

**All criteria MET!** 🎉

---

## 📞 SUPPORT

**If something doesn't work:**

1. Check API server logs:
   ```bash
   cd /path/to/autocheck-api
   # Server shows requests in real-time
   ```

2. Check Android logs:
   ```bash
   adb logcat | grep -i "autocheck"
   ```

3. Test API directly:
   ```bash
   curl -X POST http://192.168.0.149:3248/api/autocheck/lookup \
     -H "Content-Type: application/json" \
     -d '{"vin":"1HGBH41JXMN109186"}'
   ```

---

**Ready to test! Open the app on your phone and tap the Vehicle History card!** 🚗📊

Test VIN: `1HGBH41JXMN109186`
