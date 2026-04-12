package com.mde.potdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.widget.Toast;

import com.mde.potdroid.helpers.ImageUploadHelper;

/**
 * Handles the OAuth implicit flow redirect callback for image upload authentication.
 * Extracts the access token from the redirect URI fragment and stores it.
 */
public class OAuthActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri uri = getIntent().getData();
        if (uri != null) {
            String accessToken = ImageUploadHelper.extractTokenFromUri(uri);
            if (accessToken != null) {
                ImageUploadHelper helper = new ImageUploadHelper(this);
                helper.saveToken(accessToken);
                Toast.makeText(this, "Authentifizierung erfolgreich", Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }
}
