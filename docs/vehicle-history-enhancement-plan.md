# Vehicle History Page Enhancement Plan

## Available But Not Displayed Data

### 1. Score Analysis (HIGH PRIORITY)
**API Fields:**
- `vehicleComparison`: How this vehicle compares to similar vehicles
- `vehicleOutlook`: Future prognosis/outlook
- `increasingFactors[]`: Array of factors increasing the score
- `decreasingFactors[]`: Array of factors decreasing the score

**UI Implementation:**
- Add expandable "Score Analysis" card below the score circle
- Show comparison text in highlighted box
- List increasing factors with green checkmarks
- List decreasing factors with warning icons

### 2. Comprehensive "At a Glance" Checks (HIGH PRIORITY)
**API Fields (atAGlance object):**
- State Title Brand Check (status, count)
- Auction Brand Issues
- Open Recall Check
- Insurance Loss Transfer
- Odometer Check (with sub-checks)
- Certified Pre-Owned status
- Service/Repair summary
- Additional History

**Current State:** Only showing 5 items (Title, Accident, Structural, Airbag, Rollback)
**Enhancement:** Show all 9 comprehensive checks with status indicators

### 3. Detailed Recall Information (MEDIUM PRIORITY)
**API Fields:**
- `recallDetails[]`: Array of recall objects with:
  - Recall date and type
  - NHTSA recall number
  - OEM recall number
  - Campaign description
  - Current status

**Current State:** Just showing count as text
**Enhancement:** Expandable recall list with full details

### 4. Vehicle Usage & Classification (LOW PRIORITY)
**API Fields:**
- `vehicleUsage`: "Personal", "Lease", "Rental", "Fleet", etc.
- `vehicleClass`: Classification
- `trim`: Trim level

### 5. Owner History Details (LOW PRIORITY)
**API Fields:**
- `ownerHistory[]`: Per-owner details with dates, location, usage, events

### 6. Odometer Sub-Checks (LOW PRIORITY)
**API Fields:**
- State Title Odometer Check
- Auction Odometer Check
- Odometer Calculation Check

## Readability Improvements

### Typography
- Increase body text: 14sp → 15sp
- Increase section headers: 18sp → 20sp
- Increase card titles: 24sp → 26sp
- Add more line spacing for easier reading

### Spacing
- Increase card margins: 12dp → 16dp
- Add more padding in dense sections
- Better separation between timeline events

### Color Contrast
- Improve secondary text contrast
- Add subtle backgrounds to differentiate sections

## Implementation Priority

**Phase 1 (Immediate):**
1. ✅ Readability improvements (typography & spacing)
2. ✅ Score Analysis section
3. ✅ Enhanced "At a Glance" checks

**Phase 2 (Next):**
1. Detailed Recall expansion
2. Vehicle Usage badges
3. Odometer sub-checks

**Phase 3 (Future):**
1. Per-owner history timeline
2. Interactive charts/graphs
3. Export/share enhancements

## Data Model Changes Needed

```java
// Add to AutoCheckReport.java
private String vehicleComparison;
private String vehicleOutlook;
private List<String> increasingFactors;
private List<String> decreasingFactors;
private String vehicleUsage;
private AtAGlance atAGlance;
private List<RecallDetail> recallDetails;

// New nested classes
public static class AtAGlance {
    public GlanceCheck stateTitleBrand;
    public GlanceCheck auctionBrandIssues;
    public GlanceCheck accidentDamage;
    public GlanceCheck openRecallCheck;
    // ... etc
}

public static class GlanceCheck {
    public String status;
    public String statusType;
    public String subtitle;
    public Integer count;
}

public static class RecallDetail {
    public String recallDate;
    public String recallType;
    public String nhtsaRecallNo;
    public String campaignDescription;
    public String status;
}
```

## Estimated Impact

- **User Value:** ⭐⭐⭐⭐⭐ (Significantly more comprehensive)
- **Development Time:** 4-6 hours
- **Complexity:** Medium
- **Risk:** Low (additive only, no breaking changes)

## Next Steps

1. Review and approve enhancement plan
2. Implement Phase 1 changes
3. Test with real AutoCheck data
4. Iterate based on feedback
