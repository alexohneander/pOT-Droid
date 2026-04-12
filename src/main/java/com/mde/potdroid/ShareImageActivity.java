package com.mde.potdroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.mde.potdroid.fragments.ShareImageFragment;
import com.mde.potdroid.helpers.Utils;

/**
 * Entry point for sharing images from external apps via ACTION_SEND.
 * Extracts the shared image URI and displays the topic picker fragment.
 */
public class ShareImageActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Utils.isLoggedIn()) {
            finish();
            return;
        }

        Uri imageUri = null;
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null) {
            if (intent.getType().startsWith("image/")) {
                imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }

        if (imageUri == null) {
            finish();
            return;
        }

        ShareImageFragment fragment = (ShareImageFragment) getSupportFragmentManager()
                .findFragmentByTag("share_image");
        if (fragment == null) {
            fragment = ShareImageFragment.newInstance(imageUri);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.content, fragment, "share_image")
                    .commit();
        }
    }
}
