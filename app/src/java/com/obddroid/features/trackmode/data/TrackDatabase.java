package com.obddroid.features.trackmode.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Database for storing tracks, sessions, and lap times
 */
public class TrackDatabase extends SQLiteOpenHelper {
    private static final String TAG = "TrackDatabase";
    private static final String DATABASE_NAME = "track_mode.db";
    private static final int DATABASE_VERSION = 1;

    // Table names
    private static final String TABLE_TRACKS = "tracks";
    private static final String TABLE_SESSIONS = "sessions";
    private static final String TABLE_LAPS = "laps";

    // Track table columns
    private static final String TRACK_ID = "id";
    private static final String TRACK_NAME = "name";
    private static final String TRACK_COUNTRY = "country";
    private static final String TRACK_LENGTH = "length";
    private static final String TRACK_FINISH_LINE = "finish_line";
    private static final String TRACK_FINISH_LINE_END = "finish_line_end";
    private static final String TRACK_BOUNDARY = "boundary";
    private static final String TRACK_SECTORS = "sectors";
    private static final String TRACK_BEST_LAP = "best_lap_time";
    private static final String TRACK_BEST_LAP_HOLDER = "best_lap_holder";
    private static final String TRACK_CREATED_AT = "created_at";
    private static final String TRACK_LAST_USED = "last_used_at";
    private static final String TRACK_IS_USER_CREATED = "is_user_created";

    // Session table columns
    private static final String SESSION_ID = "id";
    private static final String SESSION_TRACK_ID = "track_id";
    private static final String SESSION_TRACK_NAME = "track_name";
    private static final String SESSION_TYPE = "session_type";
    private static final String SESSION_START_TIME = "start_time";
    private static final String SESSION_END_TIME = "end_time";
    private static final String SESSION_VEHICLE = "vehicle_name";
    private static final String SESSION_DRIVER = "driver_name";
    private static final String SESSION_WEATHER = "weather";
    private static final String SESSION_AMBIENT_TEMP = "ambient_temp";
    private static final String SESSION_TRACK_TEMP = "track_temp";
    private static final String SESSION_TOTAL_LAPS = "total_laps";
    private static final String SESSION_VALID_LAPS = "valid_laps";
    private static final String SESSION_BEST_LAP = "best_lap_time";
    private static final String SESSION_BEST_LAP_NUM = "best_lap_number";
    private static final String SESSION_MAX_SPEED = "max_speed";
    private static final String SESSION_MAX_RPM = "max_rpm";
    private static final String SESSION_TOTAL_DISTANCE = "total_distance";

    // Lap table columns
    private static final String LAP_ID = "id";
    private static final String LAP_SESSION_ID = "session_id";
    private static final String LAP_TRACK_ID = "track_id";
    private static final String LAP_NUMBER = "lap_number";
    private static final String LAP_TIME = "lap_time";
    private static final String LAP_START_TIME = "start_time";
    private static final String LAP_END_TIME = "end_time";
    private static final String LAP_SECTOR_TIMES = "sector_times";
    private static final String LAP_IS_VALID = "is_valid";
    private static final String LAP_IS_PURPLE = "is_purple";
    private static final String LAP_MAX_SPEED = "max_speed";
    private static final String LAP_AVG_SPEED = "avg_speed";
    private static final String LAP_MAX_RPM = "max_rpm";
    private static final String LAP_MAX_G_FORCE = "max_g_force";
    private static final String LAP_MAX_BRAKING = "max_braking";
    private static final String LAP_MAX_THROTTLE = "max_throttle";
    private static final String LAP_DISTANCE = "distance";
    private static final String LAP_TELEMETRY = "telemetry";

    private final Gson gson = new Gson();

    public TrackDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create tracks table
        String createTracksTable = "CREATE TABLE " + TABLE_TRACKS + "(" +
                TRACK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                TRACK_NAME + " TEXT NOT NULL," +
                TRACK_COUNTRY + " TEXT," +
                TRACK_LENGTH + " REAL," +
                TRACK_FINISH_LINE + " TEXT," +
                TRACK_FINISH_LINE_END + " TEXT," +
                TRACK_BOUNDARY + " TEXT," +
                TRACK_SECTORS + " TEXT," +
                TRACK_BEST_LAP + " INTEGER," +
                TRACK_BEST_LAP_HOLDER + " TEXT," +
                TRACK_CREATED_AT + " INTEGER," +
                TRACK_LAST_USED + " INTEGER," +
                TRACK_IS_USER_CREATED + " INTEGER DEFAULT 1" +
                ")";
        db.execSQL(createTracksTable);

        // Create sessions table
        String createSessionsTable = "CREATE TABLE " + TABLE_SESSIONS + "(" +
                SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                SESSION_TRACK_ID + " INTEGER," +
                SESSION_TRACK_NAME + " TEXT," +
                SESSION_TYPE + " TEXT," +
                SESSION_START_TIME + " INTEGER," +
                SESSION_END_TIME + " INTEGER," +
                SESSION_VEHICLE + " TEXT," +
                SESSION_DRIVER + " TEXT," +
                SESSION_WEATHER + " TEXT," +
                SESSION_AMBIENT_TEMP + " REAL," +
                SESSION_TRACK_TEMP + " REAL," +
                SESSION_TOTAL_LAPS + " INTEGER," +
                SESSION_VALID_LAPS + " INTEGER," +
                SESSION_BEST_LAP + " INTEGER," +
                SESSION_BEST_LAP_NUM + " INTEGER," +
                SESSION_MAX_SPEED + " REAL," +
                SESSION_MAX_RPM + " REAL," +
                SESSION_TOTAL_DISTANCE + " REAL," +
                "FOREIGN KEY(" + SESSION_TRACK_ID + ") REFERENCES " + TABLE_TRACKS + "(" + TRACK_ID + ")" +
                ")";
        db.execSQL(createSessionsTable);

        // Create laps table
        String createLapsTable = "CREATE TABLE " + TABLE_LAPS + "(" +
                LAP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                LAP_SESSION_ID + " INTEGER," +
                LAP_TRACK_ID + " INTEGER," +
                LAP_NUMBER + " INTEGER," +
                LAP_TIME + " INTEGER," +
                LAP_START_TIME + " INTEGER," +
                LAP_END_TIME + " INTEGER," +
                LAP_SECTOR_TIMES + " TEXT," +
                LAP_IS_VALID + " INTEGER DEFAULT 1," +
                LAP_IS_PURPLE + " INTEGER DEFAULT 0," +
                LAP_MAX_SPEED + " REAL," +
                LAP_AVG_SPEED + " REAL," +
                LAP_MAX_RPM + " REAL," +
                LAP_MAX_G_FORCE + " REAL," +
                LAP_MAX_BRAKING + " REAL," +
                LAP_MAX_THROTTLE + " REAL," +
                LAP_DISTANCE + " REAL," +
                LAP_TELEMETRY + " TEXT," +
                "FOREIGN KEY(" + LAP_SESSION_ID + ") REFERENCES " + TABLE_SESSIONS + "(" + SESSION_ID + ")," +
                "FOREIGN KEY(" + LAP_TRACK_ID + ") REFERENCES " + TABLE_TRACKS + "(" + TRACK_ID + ")" +
                ")";
        db.execSQL(createLapsTable);

        // Create indexes for better performance
        db.execSQL("CREATE INDEX idx_sessions_track ON " + TABLE_SESSIONS + "(" + SESSION_TRACK_ID + ")");
        db.execSQL("CREATE INDEX idx_laps_session ON " + TABLE_LAPS + "(" + LAP_SESSION_ID + ")");
        db.execSQL("CREATE INDEX idx_laps_time ON " + TABLE_LAPS + "(" + LAP_TIME + ")");

        // Insert default tracks
        insertDefaultTracks(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrades
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
    }

    /**
     * Insert default popular tracks
     */
    private void insertDefaultTracks(SQLiteDatabase db) {
        // Laguna Seca
        insertTrack(db, createLagunaSeca());

        // Sonoma Raceway
        insertTrack(db, createSonomaRaceway());

        // Buttonwillow Raceway Park
        insertTrack(db, createButtonwillow());

        // Willow Springs
        insertTrack(db, createWillowSprings());

        // Thunderhill Raceway
        insertTrack(db, createThunderhill());
    }

    private Track createLagunaSeca() {
        Track track = new Track("Laguna Seca", "USA");
        track.setLength(3602); // 2.238 miles in meters
        track.setUserCreated(false);

        // Famous corkscrew coordinates
        Track.TrackPoint finishLine = new Track.TrackPoint(36.584506, -121.753340);
        Track.TrackPoint finishLineEnd = new Track.TrackPoint(36.584450, -121.753250);
        track.setFinishLine(finishLine);
        track.setFinishLineEnd(finishLineEnd);

        // Add sectors
        List<Track.SectorLine> sectors = new ArrayList<>();
        sectors.add(new Track.SectorLine("Sector 1",
            new Track.TrackPoint(36.583970, -121.752820),
            new Track.TrackPoint(36.583920, -121.752730)));
        sectors.add(new Track.SectorLine("Sector 2",
            new Track.TrackPoint(36.581470, -121.754890),
            new Track.TrackPoint(36.581420, -121.754800)));
        track.setSectors(sectors);

        return track;
    }

    private Track createSonomaRaceway() {
        Track track = new Track("Sonoma Raceway", "USA");
        track.setLength(4012); // 2.52 miles in meters
        track.setUserCreated(false);

        Track.TrackPoint finishLine = new Track.TrackPoint(38.161839, -122.456196);
        Track.TrackPoint finishLineEnd = new Track.TrackPoint(38.161789, -122.456106);
        track.setFinishLine(finishLine);
        track.setFinishLineEnd(finishLineEnd);

        return track;
    }

    private Track createButtonwillow() {
        Track track = new Track("Buttonwillow Raceway Park", "USA");
        track.setLength(4960); // 3.08 miles in meters
        track.setUserCreated(false);

        Track.TrackPoint finishLine = new Track.TrackPoint(35.491195, -119.547201);
        Track.TrackPoint finishLineEnd = new Track.TrackPoint(35.491145, -119.547111);
        track.setFinishLine(finishLine);
        track.setFinishLineEnd(finishLineEnd);

        return track;
    }

    private Track createWillowSprings() {
        Track track = new Track("Willow Springs - Big Willow", "USA");
        track.setLength(4000); // 2.5 miles in meters
        track.setUserCreated(false);

        Track.TrackPoint finishLine = new Track.TrackPoint(34.869156, -118.263824);
        Track.TrackPoint finishLineEnd = new Track.TrackPoint(34.869106, -118.263734);
        track.setFinishLine(finishLine);
        track.setFinishLineEnd(finishLineEnd);

        return track;
    }

    private Track createThunderhill() {
        Track track = new Track("Thunderhill Raceway Park", "USA");
        track.setLength(4804); // 3 miles in meters
        track.setUserCreated(false);

        Track.TrackPoint finishLine = new Track.TrackPoint(39.537778, -122.331111);
        Track.TrackPoint finishLineEnd = new Track.TrackPoint(39.537728, -122.331021);
        track.setFinishLine(finishLine);
        track.setFinishLineEnd(finishLineEnd);

        return track;
    }

    /**
     * Save a track to database
     */
    public long insertTrack(Track track) {
        SQLiteDatabase db = getWritableDatabase();
        long id = insertTrack(db, track);
        db.close();
        return id;
    }

    private long insertTrack(SQLiteDatabase db, Track track) {
        ContentValues values = new ContentValues();
        values.put(TRACK_NAME, track.getName());
        values.put(TRACK_COUNTRY, track.getCountry());
        values.put(TRACK_LENGTH, track.getLength());
        values.put(TRACK_FINISH_LINE, gson.toJson(track.getFinishLine()));
        values.put(TRACK_FINISH_LINE_END, gson.toJson(track.getFinishLineEnd()));
        values.put(TRACK_BOUNDARY, gson.toJson(track.getTrackBoundary()));
        values.put(TRACK_SECTORS, gson.toJson(track.getSectors()));
        values.put(TRACK_BEST_LAP, track.getBestLapTime());
        values.put(TRACK_BEST_LAP_HOLDER, track.getBestLapHolder());
        values.put(TRACK_CREATED_AT, track.getCreatedAt());
        values.put(TRACK_LAST_USED, track.getLastUsedAt());
        values.put(TRACK_IS_USER_CREATED, track.isUserCreated() ? 1 : 0);

        return db.insert(TABLE_TRACKS, null, values);
    }

    /**
     * Get all tracks
     */
    public List<Track> getAllTracks() {
        List<Track> tracks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(TABLE_TRACKS, null, null, null, null, null,
                TRACK_LAST_USED + " DESC");

        if (cursor.moveToFirst()) {
            do {
                Track track = cursorToTrack(cursor);
                tracks.add(track);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return tracks;
    }

    /**
     * Get track by ID
     */
    public Track getTrack(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRACKS, null, TRACK_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);

        Track track = null;
        if (cursor.moveToFirst()) {
            track = cursorToTrack(cursor);
        }

        cursor.close();
        db.close();
        return track;
    }

    /**
     * Update track last used time
     */
    public void updateTrackLastUsed(long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TRACK_LAST_USED, System.currentTimeMillis());

        db.update(TABLE_TRACKS, values, TRACK_ID + " = ?",
                new String[]{String.valueOf(trackId)});
        db.close();
    }

    /**
     * Save a session
     */
    public long insertSession(TrackSession session) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SESSION_TRACK_ID, session.getTrackId());
        values.put(SESSION_TRACK_NAME, session.getTrackName());
        values.put(SESSION_TYPE, session.getSessionType().name());
        values.put(SESSION_START_TIME, session.getStartTime());
        values.put(SESSION_END_TIME, session.getEndTime());
        values.put(SESSION_VEHICLE, session.getVehicleName());
        values.put(SESSION_DRIVER, session.getDriverName());
        values.put(SESSION_WEATHER, session.getWeatherCondition().name());
        values.put(SESSION_AMBIENT_TEMP, session.getAmbientTemp());
        values.put(SESSION_TRACK_TEMP, session.getTrackTemp());
        values.put(SESSION_TOTAL_LAPS, session.getTotalLaps());
        values.put(SESSION_VALID_LAPS, session.getValidLaps());
        values.put(SESSION_BEST_LAP, session.getBestLapTime());
        values.put(SESSION_BEST_LAP_NUM, session.getBestLapNumber());
        values.put(SESSION_MAX_SPEED, session.getMaxSpeed());
        values.put(SESSION_MAX_RPM, session.getMaxRpm());
        values.put(SESSION_TOTAL_DISTANCE, session.getTotalDistance());

        long id = db.insert(TABLE_SESSIONS, null, values);
        db.close();
        return id;
    }

    /**
     * Save a lap time
     */
    public long insertLap(LapTime lap) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LAP_SESSION_ID, lap.getSessionId());
        values.put(LAP_TRACK_ID, lap.getTrackId());
        values.put(LAP_NUMBER, lap.getLapNumber());
        values.put(LAP_TIME, lap.getLapTime());
        values.put(LAP_START_TIME, lap.getStartTime());
        values.put(LAP_END_TIME, lap.getEndTime());
        values.put(LAP_SECTOR_TIMES, gson.toJson(lap.getSectorTimes()));
        values.put(LAP_IS_VALID, lap.isValid() ? 1 : 0);
        values.put(LAP_IS_PURPLE, lap.isPurpleLap() ? 1 : 0);
        values.put(LAP_MAX_SPEED, lap.getMaxSpeed());
        values.put(LAP_AVG_SPEED, lap.getAvgSpeed());
        values.put(LAP_MAX_RPM, lap.getMaxRpm());
        values.put(LAP_MAX_G_FORCE, lap.getMaxGForce());
        values.put(LAP_MAX_BRAKING, lap.getMaxBraking());
        values.put(LAP_MAX_THROTTLE, lap.getMaxThrottle());
        values.put(LAP_DISTANCE, lap.getTotalDistance());

        // Store telemetry as compressed JSON (could optimize with binary format)
        values.put(LAP_TELEMETRY, gson.toJson(lap.getTelemetryPoints()));

        long id = db.insert(TABLE_LAPS, null, values);
        db.close();
        return id;
    }

    /**
     * Get best lap time for a track
     */
    public LapTime getBestLap(long trackId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_LAPS, null,
                LAP_TRACK_ID + " = ? AND " + LAP_IS_VALID + " = 1",
                new String[]{String.valueOf(trackId)},
                null, null, LAP_TIME + " ASC", "1");

        LapTime lap = null;
        if (cursor.moveToFirst()) {
            lap = cursorToLap(cursor);
        }

        cursor.close();
        db.close();
        return lap;
    }

    /**
     * Get session laps
     */
    public List<LapTime> getSessionLaps(long sessionId) {
        List<LapTime> laps = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(TABLE_LAPS, null,
                LAP_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)},
                null, null, LAP_NUMBER + " ASC");

        if (cursor.moveToFirst()) {
            do {
                LapTime lap = cursorToLap(cursor);
                laps.add(lap);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return laps;
    }

    /**
     * Convert cursor to Track object
     */
    private Track cursorToTrack(Cursor cursor) {
        Track track = new Track();
        track.setId(cursor.getLong(cursor.getColumnIndex(TRACK_ID)));
        track.setName(cursor.getString(cursor.getColumnIndex(TRACK_NAME)));
        track.setCountry(cursor.getString(cursor.getColumnIndex(TRACK_COUNTRY)));
        track.setLength(cursor.getDouble(cursor.getColumnIndex(TRACK_LENGTH)));

        String finishLineJson = cursor.getString(cursor.getColumnIndex(TRACK_FINISH_LINE));
        if (finishLineJson != null) {
            track.setFinishLine(gson.fromJson(finishLineJson, Track.TrackPoint.class));
        }

        String finishLineEndJson = cursor.getString(cursor.getColumnIndex(TRACK_FINISH_LINE_END));
        if (finishLineEndJson != null) {
            track.setFinishLineEnd(gson.fromJson(finishLineEndJson, Track.TrackPoint.class));
        }

        String boundaryJson = cursor.getString(cursor.getColumnIndex(TRACK_BOUNDARY));
        if (boundaryJson != null) {
            Type listType = new TypeToken<List<Track.TrackPoint>>(){}.getType();
            track.setTrackBoundary(gson.fromJson(boundaryJson, listType));
        }

        String sectorsJson = cursor.getString(cursor.getColumnIndex(TRACK_SECTORS));
        if (sectorsJson != null) {
            Type listType = new TypeToken<List<Track.SectorLine>>(){}.getType();
            track.setSectors(gson.fromJson(sectorsJson, listType));
        }

        track.setBestLapTime(cursor.getLong(cursor.getColumnIndex(TRACK_BEST_LAP)));
        track.setBestLapHolder(cursor.getString(cursor.getColumnIndex(TRACK_BEST_LAP_HOLDER)));
        track.setCreatedAt(cursor.getLong(cursor.getColumnIndex(TRACK_CREATED_AT)));
        track.setLastUsedAt(cursor.getLong(cursor.getColumnIndex(TRACK_LAST_USED)));
        track.setUserCreated(cursor.getInt(cursor.getColumnIndex(TRACK_IS_USER_CREATED)) == 1);

        return track;
    }

    /**
     * Convert cursor to LapTime object
     */
    private LapTime cursorToLap(Cursor cursor) {
        LapTime lap = new LapTime();
        lap.setId(cursor.getLong(cursor.getColumnIndex(LAP_ID)));
        lap.setSessionId(cursor.getLong(cursor.getColumnIndex(LAP_SESSION_ID)));
        lap.setTrackId(cursor.getLong(cursor.getColumnIndex(LAP_TRACK_ID)));
        lap.setLapNumber(cursor.getInt(cursor.getColumnIndex(LAP_NUMBER)));
        lap.setLapTime(cursor.getLong(cursor.getColumnIndex(LAP_TIME)));
        lap.setStartTime(cursor.getLong(cursor.getColumnIndex(LAP_START_TIME)));
        lap.setEndTime(cursor.getLong(cursor.getColumnIndex(LAP_END_TIME)));

        String sectorTimesJson = cursor.getString(cursor.getColumnIndex(LAP_SECTOR_TIMES));
        if (sectorTimesJson != null) {
            Type listType = new TypeToken<List<Long>>(){}.getType();
            lap.setSectorTimes(gson.fromJson(sectorTimesJson, listType));
        }

        lap.setValid(cursor.getInt(cursor.getColumnIndex(LAP_IS_VALID)) == 1);
        lap.setPurpleLap(cursor.getInt(cursor.getColumnIndex(LAP_IS_PURPLE)) == 1);
        lap.setMaxSpeed(cursor.getFloat(cursor.getColumnIndex(LAP_MAX_SPEED)));
        lap.setAvgSpeed(cursor.getFloat(cursor.getColumnIndex(LAP_AVG_SPEED)));
        lap.setMaxRpm(cursor.getFloat(cursor.getColumnIndex(LAP_MAX_RPM)));
        lap.setMaxGForce(cursor.getFloat(cursor.getColumnIndex(LAP_MAX_G_FORCE)));
        lap.setMaxBraking(cursor.getFloat(cursor.getColumnIndex(LAP_MAX_BRAKING)));
        lap.setMaxThrottle(cursor.getFloat(cursor.getColumnIndex(LAP_MAX_THROTTLE)));
        lap.setTotalDistance(cursor.getDouble(cursor.getColumnIndex(LAP_DISTANCE)));

        // Load telemetry if needed (might want to lazy load this)
        String telemetryJson = cursor.getString(cursor.getColumnIndex(LAP_TELEMETRY));
        if (telemetryJson != null) {
            Type listType = new TypeToken<List<LapTime.TelemetryPoint>>(){}.getType();
            lap.setTelemetryPoints(gson.fromJson(telemetryJson, listType));
        }

        return lap;
    }
}