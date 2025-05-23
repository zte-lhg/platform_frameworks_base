/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.media.quality;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.quality.AmbientBacklightSettings;
import android.media.quality.IAmbientBacklightCallback;
import android.media.quality.IMediaQualityManager;
import android.media.quality.IPictureProfileCallback;
import android.media.quality.ISoundProfileCallback;
import android.media.quality.MediaQualityContract.BaseParameters;
import android.media.quality.ParamCapability;
import android.media.quality.PictureProfile;
import android.media.quality.PictureProfileHandle;
import android.media.quality.SoundProfile;
import android.media.quality.SoundProfileHandle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Log;

import com.android.server.SystemService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This service manage picture profile and sound profile for TV setting. Also communicates with the
 * database to save, update the profiles.
 */
public class MediaQualityService extends SystemService {

    private static final boolean DEBUG = false;
    private static final String TAG = "MediaQualityService";
    private final Context mContext;
    private final MediaQualityDbHelper mMediaQualityDbHelper;
    private final BiMap<Long, String> mPictureProfileTempIdMap;
    private final BiMap<Long, String> mSoundProfileTempIdMap;

    public MediaQualityService(Context context) {
        super(context);
        mContext = context;
        mPictureProfileTempIdMap = new BiMap<>();
        mSoundProfileTempIdMap = new BiMap<>();
        mMediaQualityDbHelper = new MediaQualityDbHelper(mContext);
        mMediaQualityDbHelper.setWriteAheadLoggingEnabled(true);
        mMediaQualityDbHelper.setIdleConnectionTimeout(30);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.MEDIA_QUALITY_SERVICE, new BinderService());
    }

    // TODO: Add additional APIs. b/373951081
    private final class BinderService extends IMediaQualityManager.Stub {

        @Override
        public PictureProfile createPictureProfile(PictureProfile pp, UserHandle user) {
            SQLiteDatabase db = mMediaQualityDbHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(BaseParameters.PARAMETER_TYPE, pp.getProfileType());
            values.put(BaseParameters.PARAMETER_NAME, pp.getName());
            values.put(BaseParameters.PARAMETER_PACKAGE, pp.getPackageName());
            values.put(BaseParameters.PARAMETER_INPUT_ID, pp.getInputId());
            values.put(mMediaQualityDbHelper.SETTINGS, persistableBundleToJson(pp.getParameters()));

            // id is auto-generated by SQLite upon successful insertion of row
            Long id = db.insert(mMediaQualityDbHelper.PICTURE_QUALITY_TABLE_NAME,
                    null, values);
            populateTempIdMap(mPictureProfileTempIdMap, id);
            pp.setProfileId(mPictureProfileTempIdMap.getValue(id));
            return pp;
        }

        @Override
        public void updatePictureProfile(String id, PictureProfile pp, UserHandle user) {
            // TODO: implement
        }

        @Override
        public void removePictureProfile(String id, UserHandle user) {
            Long intId = mPictureProfileTempIdMap.getKey(id);
            if (intId != null) {
                SQLiteDatabase db = mMediaQualityDbHelper.getWritableDatabase();
                String selection = BaseParameters.PARAMETER_ID + " = ?";
                String[] selectionArgs = {Long.toString(intId)};
                db.delete(mMediaQualityDbHelper.PICTURE_QUALITY_TABLE_NAME, selection,
                        selectionArgs);
                mPictureProfileTempIdMap.remove(intId);
            }
        }

        @Override
        public PictureProfile getPictureProfile(int type, String name, boolean includeParams,
                UserHandle user) {
            String selection = BaseParameters.PARAMETER_TYPE + " = ? AND "
                    + BaseParameters.PARAMETER_NAME + " = ?";
            String[] selectionArguments = {Integer.toString(type), name};

            try (
                    Cursor cursor = getCursorAfterQuerying(
                            mMediaQualityDbHelper.PICTURE_QUALITY_TABLE_NAME,
                            getAllMediaProfileColumns(), selection, selectionArguments)
            ) {
                int count = cursor.getCount();
                if (count == 0) {
                    return null;
                }
                if (count > 1) {
                    Log.wtf(TAG, String.format(Locale.US, "%d entries found for type=%d and name=%s"
                                    + " in %s. Should only ever be 0 or 1.", count, type, name,
                            mMediaQualityDbHelper.PICTURE_QUALITY_TABLE_NAME));
                    return null;
                }
                cursor.moveToFirst();
                return getPictureProfileWithTempIdFromCursor(cursor);
            }
        }

        @Override
        public List<PictureProfile> getPictureProfilesByPackage(
                String packageName, boolean includeParams, UserHandle user) {
            String selection = BaseParameters.PARAMETER_PACKAGE + " = ?";
            String[] selectionArguments = {packageName};
            return getPictureProfilesBasedOnConditions(getAllMediaProfileColumns(), selection,
                    selectionArguments);
        }

        @Override
        public List<PictureProfile> getAvailablePictureProfiles(
                boolean includeParams, UserHandle user) {
            return new ArrayList<>();
        }

        @Override
        public boolean setDefaultPictureProfile(String profileId, UserHandle user) {
            // TODO: pass the profile ID to MediaQuality HAL when ready.
            return false;
        }

        @Override
        public List<String> getPictureProfilePackageNames(UserHandle user) {
            String [] column = {BaseParameters.PARAMETER_PACKAGE};
            List<PictureProfile> pictureProfiles = getPictureProfilesBasedOnConditions(column,
                    null, null);
            return pictureProfiles.stream()
                    .map(PictureProfile::getPackageName)
                    .distinct()
                    .collect(Collectors.toList());
        }

        @Override
        public List<PictureProfileHandle> getPictureProfileHandle(String[] id, UserHandle user) {
            return new ArrayList<>();
        }

        @Override
        public List<SoundProfileHandle> getSoundProfileHandle(String[] id, UserHandle user) {
            return new ArrayList<>();
        }

        @Override
        public SoundProfile createSoundProfile(SoundProfile sp, UserHandle user) {
            SQLiteDatabase db = mMediaQualityDbHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put(BaseParameters.PARAMETER_TYPE, sp.getProfileType());
            values.put(BaseParameters.PARAMETER_NAME, sp.getName());
            values.put(BaseParameters.PARAMETER_PACKAGE, sp.getPackageName());
            values.put(BaseParameters.PARAMETER_INPUT_ID, sp.getInputId());
            values.put(mMediaQualityDbHelper.SETTINGS, persistableBundleToJson(sp.getParameters()));

            // id is auto-generated by SQLite upon successful insertion of row
            Long id = db.insert(mMediaQualityDbHelper.SOUND_QUALITY_TABLE_NAME,
                    null, values);
            populateTempIdMap(mSoundProfileTempIdMap, id);
            sp.setProfileId(mSoundProfileTempIdMap.getValue(id));
            return sp;
        }

        @Override
        public void updateSoundProfile(String id, SoundProfile pp, UserHandle user) {
            // TODO: implement
        }

        @Override
        public void removeSoundProfile(String id, UserHandle user) {
            Long intId = mSoundProfileTempIdMap.getKey(id);
            if (intId != null) {
                SQLiteDatabase db = mMediaQualityDbHelper.getWritableDatabase();
                String selection = BaseParameters.PARAMETER_ID + " = ?";
                String[] selectionArgs = {Long.toString(intId)};
                db.delete(mMediaQualityDbHelper.SOUND_QUALITY_TABLE_NAME, selection,
                        selectionArgs);
                mSoundProfileTempIdMap.remove(intId);
            }
        }

        @Override
        public SoundProfile getSoundProfile(int type, String id, boolean includeParams,
                UserHandle user) {
            String selection = BaseParameters.PARAMETER_TYPE + " = ? AND "
                    + BaseParameters.PARAMETER_ID + " = ?";
            String[] selectionArguments = {String.valueOf(type), id};

            try (
                    Cursor cursor = getCursorAfterQuerying(
                            mMediaQualityDbHelper.SOUND_QUALITY_TABLE_NAME,
                            getAllMediaProfileColumns(), selection, selectionArguments)
            ) {
                int count = cursor.getCount();
                if (count == 0) {
                    return null;
                }
                if (count > 1) {
                    Log.wtf(TAG, String.format(Locale.US, "%d entries found for id=%s"
                                    + " in %s. Should only ever be 0 or 1.", count, id,
                            mMediaQualityDbHelper.SOUND_QUALITY_TABLE_NAME));
                    return null;
                }
                cursor.moveToFirst();
                return getSoundProfileWithTempIdFromCursor(cursor);
            }
        }

        @Override
        public List<SoundProfile> getSoundProfilesByPackage(
                String packageName, boolean includeParams, UserHandle user) {
            String selection = BaseParameters.PARAMETER_PACKAGE + " = ?";
            String[] selectionArguments = {packageName};
            return getSoundProfilesBasedOnConditions(getAllMediaProfileColumns(), selection,
                    selectionArguments);
        }

        @Override
        public List<SoundProfile> getAvailableSoundProfiles(
                boolean includeParams, UserHandle user) {
            return new ArrayList<>();
        }

        @Override
        public boolean setDefaultSoundProfile(String profileId, UserHandle user) {
            // TODO: pass the profile ID to MediaQuality HAL when ready.
            return false;
        }

        @Override
        public List<String> getSoundProfilePackageNames(UserHandle user) {
            String [] column = {BaseParameters.PARAMETER_NAME};
            List<SoundProfile> soundProfiles = getSoundProfilesBasedOnConditions(column,
                    null, null);
            return soundProfiles.stream()
                    .map(SoundProfile::getPackageName)
                    .distinct()
                    .collect(Collectors.toList());
        }

        private void populateTempIdMap(BiMap<Long, String> map, Long id) {
            if (id != null && map.getValue(id) == null) {
                String uuid = UUID.randomUUID().toString();
                while (map.getKey(uuid) != null) {
                    uuid = UUID.randomUUID().toString();
                }
                map.put(id, uuid);
            }
        }

        private String persistableBundleToJson(PersistableBundle bundle) {
            JSONObject json = new JSONObject();
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                try {
                    if (value instanceof String) {
                        json.put(key, bundle.getString(key));
                    } else if (value instanceof Integer) {
                        json.put(key, bundle.getInt(key));
                    } else if (value instanceof Long) {
                        json.put(key, bundle.getLong(key));
                    } else if (value instanceof Boolean) {
                        json.put(key, bundle.getBoolean(key));
                    } else if (value instanceof Double) {
                        json.put(key, bundle.getDouble(key));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Unable to serialize ", e);
                }
            }
            return json.toString();
        }

        private PersistableBundle jsonToBundle(String jsonString) {
            PersistableBundle bundle = new PersistableBundle();
            if (jsonString != null) {
                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject(jsonString);

                    Iterator<String> keys = jsonObject.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object value = jsonObject.get(key);

                        if (value instanceof String) {
                            bundle.putString(key, (String) value);
                        } else if (value instanceof Integer) {
                            bundle.putInt(key, (Integer) value);
                        } else if (value instanceof Boolean) {
                            bundle.putBoolean(key, (Boolean) value);
                        } else if (value instanceof Double) {
                            bundle.putDouble(key, (Double) value);
                        } else if (value instanceof Long) {
                            bundle.putLong(key, (Long) value);
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            return bundle;
        }

        private String[] getAllMediaProfileColumns() {
            return new String[]{
                    BaseParameters.PARAMETER_ID,
                    BaseParameters.PARAMETER_TYPE,
                    BaseParameters.PARAMETER_NAME,
                    BaseParameters.PARAMETER_INPUT_ID,
                    BaseParameters.PARAMETER_PACKAGE,
                    mMediaQualityDbHelper.SETTINGS
            };
        }

        private PictureProfile getPictureProfileWithTempIdFromCursor(Cursor cursor) {
            return new PictureProfile(
                    getTempId(mPictureProfileTempIdMap, cursor),
                    getType(cursor),
                    getName(cursor),
                    getInputId(cursor),
                    getPackageName(cursor),
                    jsonToBundle(getSettingsString(cursor)),
                    PictureProfileHandle.NONE
            );
        }

        private SoundProfile getSoundProfileWithTempIdFromCursor(Cursor cursor) {
            return new SoundProfile(
                    getTempId(mSoundProfileTempIdMap, cursor),
                    getType(cursor),
                    getName(cursor),
                    getInputId(cursor),
                    getPackageName(cursor),
                    jsonToBundle(getSettingsString(cursor)),
                    SoundProfileHandle.NONE
            );
        }

        private String getTempId(BiMap<Long, String> map, Cursor cursor) {
            int colIndex = cursor.getColumnIndex(BaseParameters.PARAMETER_ID);
            Long dbId = colIndex != -1 ? cursor.getLong(colIndex) : null;
            populateTempIdMap(map, dbId);
            return map.getValue(dbId);
        }

        private int getType(Cursor cursor) {
            int colIndex = cursor.getColumnIndex(BaseParameters.PARAMETER_TYPE);
            return colIndex != -1 ? cursor.getInt(colIndex) : 0;
        }

        private String getName(Cursor cursor) {
            int colIndex = cursor.getColumnIndex(BaseParameters.PARAMETER_NAME);
            return colIndex != -1 ? cursor.getString(colIndex) : null;
        }

        private String getInputId(Cursor cursor) {
            int colIndex = cursor.getColumnIndex(BaseParameters.PARAMETER_INPUT_ID);
            return colIndex != -1 ? cursor.getString(colIndex) : null;
        }

        private String getPackageName(Cursor cursor) {
            int colIndex = cursor.getColumnIndex(BaseParameters.PARAMETER_PACKAGE);
            return colIndex != -1 ? cursor.getString(colIndex) : null;
        }

        private String getSettingsString(Cursor cursor) {
            int colIndex = cursor.getColumnIndex(mMediaQualityDbHelper.SETTINGS);
            return colIndex != -1 ? cursor.getString(colIndex) : null;
        }

        private Cursor getCursorAfterQuerying(String table, String[] columns, String selection,
                String[] selectionArgs) {
            SQLiteDatabase db = mMediaQualityDbHelper.getReadableDatabase();
            return db.query(table, columns, selection, selectionArgs,
                    /*groupBy=*/ null, /*having=*/ null, /*orderBy=*/ null);
        }

        private List<PictureProfile> getPictureProfilesBasedOnConditions(String[] columns,
                String selection, String[] selectionArguments) {
            try (
                    Cursor cursor = getCursorAfterQuerying(
                            mMediaQualityDbHelper.PICTURE_QUALITY_TABLE_NAME, columns, selection,
                            selectionArguments)
            ) {
                List<PictureProfile> pictureProfiles = new ArrayList<>();
                while (cursor.moveToNext()) {
                    pictureProfiles.add(getPictureProfileWithTempIdFromCursor(cursor));
                }
                return pictureProfiles;
            }
        }

        private List<SoundProfile> getSoundProfilesBasedOnConditions(String[] columns,
                String selection, String[] selectionArguments) {
            try (
                    Cursor cursor = getCursorAfterQuerying(
                            mMediaQualityDbHelper.SOUND_QUALITY_TABLE_NAME, columns, selection,
                            selectionArguments)
            ) {
                List<SoundProfile> soundProfiles = new ArrayList<>();
                while (cursor.moveToNext()) {
                    soundProfiles.add(getSoundProfileWithTempIdFromCursor(cursor));
                }
                return soundProfiles;
            }
        }

        @Override
        public void registerPictureProfileCallback(final IPictureProfileCallback callback) {
        }
        @Override
        public void registerSoundProfileCallback(final ISoundProfileCallback callback) {
        }

        @Override
        public void registerAmbientBacklightCallback(IAmbientBacklightCallback callback) {
        }

        @Override
        public void setAmbientBacklightSettings(
                AmbientBacklightSettings settings, UserHandle user) {
        }

        @Override
        public void setAmbientBacklightEnabled(boolean enabled, UserHandle user) {
        }

        @Override
        public List<ParamCapability> getParamCapabilities(List<String> names, UserHandle user) {
            return new ArrayList<>();
        }

        @Override
        public List<String> getPictureProfileAllowList(UserHandle user) {
            return new ArrayList<>();
        }

        @Override
        public void setPictureProfileAllowList(List<String> packages, UserHandle user) {
        }

        @Override
        public List<String> getSoundProfileAllowList(UserHandle user) {
            return new ArrayList<>();
        }

        @Override
        public void setSoundProfileAllowList(List<String> packages, UserHandle user) {
        }

        @Override
        public boolean isSupported(UserHandle user) {
            return false;
        }

        @Override
        public void setAutoPictureQualityEnabled(boolean enabled, UserHandle user) {
        }

        @Override
        public boolean isAutoPictureQualityEnabled(UserHandle user) {
            return false;
        }

        @Override
        public void setSuperResolutionEnabled(boolean enabled, UserHandle user) {
        }

        @Override
        public boolean isSuperResolutionEnabled(UserHandle user) {
            return false;
        }

        @Override
        public void setAutoSoundQualityEnabled(boolean enabled, UserHandle user) {
        }

        @Override
        public boolean isAutoSoundQualityEnabled(UserHandle user) {
            return false;
        }

        @Override
        public boolean isAmbientBacklightEnabled(UserHandle user) {
            return false;
        }
    }
}
