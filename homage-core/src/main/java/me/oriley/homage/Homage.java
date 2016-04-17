/*
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.oriley.homage;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.annotation.StringRes;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.util.Log;
import me.oriley.homage.utils.ResourceUtils.ResourceType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import static java.util.Locale.US;
import static me.oriley.homage.Homage.CoreLicense.*;
import static me.oriley.homage.utils.IOUtils.closeQuietly;
import static me.oriley.homage.utils.ResourceUtils.getResourceId;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Homage {

    private static final String JSON_KEY_LICENSES = "licenses";
    private static final String JSON_KEY_NAME = "name";
    private static final String JSON_KEY_ICON = "icon";
    private static final String JSON_KEY_VERSION = "version";
    private static final String JSON_KEY_DESCRIPTION = "description";
    private static final String JSON_KEY_YEAR = "year";
    private static final String JSON_KEY_OWNER = "owner";
    private static final String JSON_KEY_URL = "url";
    private static final String JSON_KEY_LICENSE = "license";

    public enum CoreLicense {
        CC0, CC3, APACHE2, BSD2, BSD3, LGPL3, MIT, UNRECOGNISED, NONE
    }

    private static final String TAG = Homage.class.getSimpleName();

    @NonNull
    private final Map<String, License> mLicenses = new HashMap<>();

    @NonNull
    private final ArrayList<Library> mLibraries = new ArrayList<>();

    @NonNull
    private final Context mContext;

    @Nullable
    private String mAssetPaths[];

    @Nullable
    @RawRes
    private int[] mResourceIds;


    // Application context so don't stress :)
    private Homage(@NonNull Context context) {
        mContext = context.getApplicationContext();

        addLicense(CC0, R.string.homage_license_cc0_10_licenseName, R.string.homage_license_cc0_10_licenseWebsite, R.string.homage_license_cc0_10_licenseDescription);
        addLicense(CC3, R.string.homage_license_cc30_licenseName, R.string.homage_license_cc30_licenseWebsite, R.string.homage_license_cc30_licenseDescription);
        addLicense(APACHE2, R.string.homage_license_Apache_2_0_licenseName, R.string.homage_license_Apache_2_0_licenseWebsite, R.string.homage_license_Apache_2_0_licenseDescription);
        addLicense(BSD2, R.string.homage_license_bsd_2_licenseName, R.string.homage_license_bsd_2_licenseWebsite, R.string.homage_license_bsd_2_licenseDescription);
        addLicense(BSD3, R.string.homage_license_bsd_3_licenseName, R.string.homage_license_bsd_3_licenseWebsite, R.string.homage_license_bsd_3_licenseDescription);
        addLicense(LGPL3, R.string.homage_license_lgpl_3_0_licenseName, R.string.homage_license_lgpl_3_0_licenseWebsite, R.string.homage_license_lgpl_3_0_licenseDescription);
        addLicense(MIT, R.string.homage_license_mit_licenseName, R.string.homage_license_mit_licenseWebsite, R.string.homage_license_mit_licenseDescription);
        addLicense(UNRECOGNISED, R.string.homage_empty_license, R.string.homage_empty_license, R.string.homage_unrecognised_license);
        addLicense(NONE, R.string.homage_empty_license, R.string.homage_empty_license, R.string.homage_empty_license);
    }

    public Homage(@NonNull Context context, @NonNull @RawRes int... licensesResourceIds) {
        this(context);
        mResourceIds = licensesResourceIds;
    }

    public Homage(@NonNull Context context, @NonNull String... assetPaths) {
        this(context);
        mAssetPaths = assetPaths;
    }


    public void refreshLibraries() {
        mLibraries.clear();

        List<Library> newLibraries = new ArrayList<>();
        if (mAssetPaths != null) {
            for (String assetPath : mAssetPaths) {
                if (TextUtils.isEmpty(assetPath)) {
                    Log.w(TAG, "Empty asset path passed, ignoring");
                    continue;
                }

                Library[] libs = getLibraryArray(mContext, assetPath);
                if (libs != null) {
                    Collections.addAll(newLibraries, libs);
                }
            }
        }
        if (mResourceIds != null) {
            for (int resourceId : mResourceIds) {
                if (resourceId <= 0) {
                    Log.w(TAG, "Invalid resource ID passed: " + resourceId + ", ignoring");
                    continue;
                }

                Library[] libs = getLibraryArray(mContext, resourceId);
                if (libs != null) {
                    Collections.addAll(newLibraries, libs);
                }
            }
        }

        if (newLibraries.isEmpty()) {
            Log.w(TAG, "No libraries found");
            return;
        }

        for (Library library : newLibraries) {
            String licenseCode = library.getLicenseCode();

            License license;
            if (!TextUtils.isEmpty(licenseCode)) {
                if (mLicenses.containsKey(licenseCode)) {
                    license = mLicenses.get(licenseCode);
                } else {
                    license = mLicenses.get(UNRECOGNISED.name().toLowerCase(US));
                }
            } else {
                license = mLicenses.get(NONE.name().toLowerCase(US));
            }
            library.setLicense(license);

            int iconRes = -1;
            String icon = library.getLibraryIcon();
            if (!TextUtils.isEmpty(icon)) {
                iconRes = getResourceId(mContext, icon, ResourceType.DRAWABLE);
                if (iconRes <= 0) {
                    iconRes = getResourceId(mContext, icon, ResourceType.MIPMAP);
                }
                if (iconRes <= 0) {
                    iconRes = android.R.drawable.sym_def_app_icon;
                }
            }
            library.setIconResource(iconRes);

            mLibraries.add(library);
        }
    }

    @NonNull
    public List<Library> getLibraries() {
        return Collections.unmodifiableList(mLibraries);
    }

    private void addLicense(@NonNull CoreLicense coreLicense, @StringRes int nameRes, @StringRes int urlRes, @StringRes int descRes) {
        addLicense(coreLicense.name().toLowerCase(US), nameRes, urlRes, descRes);
    }

    public void addLicense(@NonNull String key, @StringRes int nameRes, @StringRes int urlRes, @StringRes int descRes) {
        String name = mContext.getString(nameRes);
        String url = mContext.getString(urlRes);
        Spanned description = Html.fromHtml(mContext.getString(descRes));
        addLicense(key, name, url, description);
    }

    public void addLicense(@NonNull String key, @NonNull String name, @NonNull String url, @NonNull String description) {
        addLicense(key, name, url, new SpannedString(description));
    }

    public void addLicense(@NonNull String key, @NonNull String name, @NonNull String url, @NonNull Spanned description) {
        License license = new License(name, url, description);
        mLicenses.put(key, license);
    }

    @Nullable
    private static Library[] getLibraryArray(@NonNull Context context, @RawRes int rawResourceId) {
        try {
            InputStream stream = context.getResources().openRawResource(rawResourceId);
            return parseLibraries(stream);
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "NotFoundException reading license file: " + rawResourceId, e);
            return null;
        }
    }

    @Nullable
    private static Library[] getLibraryArray(@NonNull Context context, @NonNull String assetPath) {
        try {
            InputStream stream = context.getAssets().open(assetPath);
            return parseLibraries(stream);
        } catch (IOException e) {
            Log.e(TAG, "IOException reading license file: " + assetPath, e);
            return null;
        }
    }

    @Nullable
    private static Library[] parseLibraries(@NonNull InputStream inputStream) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder jsonBuilder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JSONObject json = new JSONObject(jsonBuilder.toString());

            List<Library> libraries = new ArrayList<>();
            JSONArray array = json.getJSONArray(JSON_KEY_LICENSES);
            for (int i = 0 ; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                libraries.add(parseLibrary(object));
            }

            return libraries.toArray(new Library[libraries.size()]);
        } catch (IOException e) {
            Log.e(TAG, "Exception reading input stream", e);
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "Exception parsing JSON", e);
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    @NonNull
    private static Library parseLibrary(@NonNull JSONObject json) throws JSONException {
        String name = getOptionalString(json, JSON_KEY_NAME);
        String icon = getOptionalString(json, JSON_KEY_ICON);
        String version = getOptionalString(json, JSON_KEY_VERSION);
        String description = getOptionalString(json, JSON_KEY_DESCRIPTION);
        String year = getOptionalString(json, JSON_KEY_YEAR);
        String owner = getOptionalString(json, JSON_KEY_OWNER);
        String url = getOptionalString(json, JSON_KEY_URL);
        String license = getOptionalString(json, JSON_KEY_LICENSE);
        return new Library(name, icon, version, description, year, owner, url, license);
    }

    @Nullable
    private static String getOptionalString(@NonNull JSONObject jsonObject, @NonNull String key) {
        try {
            return getString(jsonObject, key);
        } catch (JSONException e) {
            return null;
        }
    }

    @Nullable
    private static String getString(@NonNull JSONObject jsonObject, @NonNull String key) throws JSONException {
        return jsonObject.getString(key);
    }
}