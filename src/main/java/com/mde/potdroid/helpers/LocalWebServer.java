package com.mde.potdroid.helpers;

import android.content.Context;
import android.content.res.AssetManager;
import android.webkit.MimeTypeMap;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.io.InputStream;

public class LocalWebServer extends NanoHTTPD {
    private final AssetManager assets;
    public LocalWebServer(Context ctx, int port) {
        super(port);
        this.assets = ctx.getAssets();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.equals("/")) uri = "index.html";
        if (uri.startsWith("/")) uri = uri.substring(1);

        // Handle preflight OPTIONS
        if (Method.OPTIONS.equals(session.getMethod())) {
            Response resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
            addCorsHeaders(resp);
            return resp;
        }

        try {
            InputStream is = assets.open(uri); // throws if not found
            String mime = getMimeTypeForFile(uri);
            Response r = newFixedLengthResponse(Response.Status.OK, mime, is, is.available());
            addCorsHeaders(r);
            return r;
        } catch (IOException e) {
            Response r = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
            addCorsHeaders(r);
            return r;
        }
    }

    private void addCorsHeaders(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    public static String getMimeTypeForFile(String uri) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }
}