package com.mde.potdroid.helpers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.browser.customtabs.CustomTabsIntent;

import com.mde.potdroid.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles OAuth authentication and image upload to an external image hosting service.
 * Configure the constants below for your specific image hoster.
 */
public class ImageUploadHelper {

    // TODO: Configure these for your image hoster
    public static final String OAUTH_AUTH_URL = "https://auth.potber.de/authorize";
    public static final String UPLOAD_URL = "https://imgpot.de/api/images";
    public static final String CLIENT_ID = "146a4c90-f191-4bfe-a837-80e0371329b4";
    public static final String REDIRECT_URI = BuildConfig.OAUTH_REDIRECT_URI;

    private Context mContext;
    private SettingsWrapper mSettings;
    private OkHttpClient mHttpClient;

    public interface OnImageUploadedListener {
        void onSuccess(String imageUrl);
        void onFailure(String error);
    }

    public ImageUploadHelper(Context context) {
        mContext = context;
        mSettings = new SettingsWrapper(context);
        mHttpClient = new Network(context).getHttpClient();
    }

    /**
     * Check if we have a valid OAuth token stored.
     */
    public boolean hasValidToken() {
        return mSettings.hasOAuthToken();
    }

    /**
     * Start the OAuth implicit flow in a Custom Tab.
     * The token is returned directly in the redirect URI fragment.
     */
    public void startOAuthFlow(Context context) {
        String authUrl = OAUTH_AUTH_URL
                + "?client_id=" + CLIENT_ID
                + "&response_type=token"
                + "&redirect_uri=" + Uri.encode(REDIRECT_URI);

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(context, Uri.parse(authUrl));
    }

    /**
     * Extract the access token from an OAuth implicit flow redirect URI.
     * The token is in the fragment: potdroid://auth/callback#access_token=xxx&token_type=bearer
     *
     * @return the access token, or null if not found
     */
    public static String extractTokenFromUri(Uri uri) {
        // Try fragment first (standard implicit flow)
        String fragment = uri.getFragment();
        if (fragment != null) {
            for (String param : fragment.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals("access_token")) {
                    return kv[1];
                }
            }
        }
        // Fallback: try query parameter
        String token = uri.getQueryParameter("access_token");
        if (token != null) {
            return token;
        }
        return null;
    }

    /**
     * Store an access token in settings.
     */
    public void saveToken(String accessToken) {
        mSettings.setOAuthAccessToken(accessToken);
    }

    /**
     * Upload an image from a content URI to the external image hoster.
     */
    public void upload(Uri imageUri, final OnImageUploadedListener listener) {
        if (!hasValidToken()) {
            listener.onFailure("No OAuth token available");
            return;
        }

        try {
            InputStream inputStream = mContext.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                listener.onFailure("Could not read image");
                return;
            }

            byte[] imageBytes = readBytes(inputStream);
            inputStream.close();

            String fileName = getFileName(imageUri);
            String mimeType = mContext.getContentResolver().getType(imageUri);
            if (mimeType == null) {
                mimeType = "image/jpeg";
            }

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName,
                            RequestBody.create(MediaType.parse(mimeType), imageBytes))
                    .build();

            String token = mSettings.getOAuthAccessToken();

            Request request = new Request.Builder()
                    .url(UPLOAD_URL)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Origin", "https://imgpot.de")
                    .post(requestBody)
                    .build();

            mHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    listener.onFailure(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            JSONArray variations = json.getJSONArray("variations");
                            String imageUrl = variations.getJSONObject(0).getString("cdnUrl");
                            listener.onSuccess(imageUrl);
                        } catch (Exception e) {
                            listener.onFailure("Failed to parse upload response");
                        }
                    } else {
                        listener.onFailure("Upload failed: " + response.code());
                    }
                }
            });

        } catch (IOException e) {
            listener.onFailure(e.getMessage());
        }
    }

    private byte[] readBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private String getFileName(Uri uri) {
        String name = "image.jpg";
        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        name = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return name;
    }

}
