package com.deluxedevelopment.localserve;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

public class ServerSetup extends NanoHTTPD {
    private final Context context;
    private final Uri rootUri;
    private final ServerErrorListener errorListener;

    public interface ServerErrorListener {
        void onError(String message);
    }

    public ServerSetup(int port, Context context, Uri rootUri, ServerErrorListener listener) {
        super(port);
        this.context = context;
        this.rootUri = rootUri;
        this.errorListener = listener;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uriPath = session.getUri();
        if (uriPath.equals("/")) uriPath = "/index.html";

        try {
            DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
            if (root == null || !root.exists()) {
                if (errorListener != null) errorListener.onError("Root folder not found.");
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Root folder not found.");
            }

            for (DocumentFile file : root.listFiles()) {
                if (file.getName() != null && ("/" + file.getName()).equals(uriPath)) {
                    if (errorListener != null) errorListener.onError("null"); // clear error message
                    InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
                    String mimeType = context.getContentResolver().getType(file.getUri());
                    return newChunkedResponse(Response.Status.OK, mimeType, inputStream);
                }
            }

            if (errorListener != null) errorListener.onError("File not found: " + uriPath);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found: " + uriPath);

        } catch (Exception e) {
            if (errorListener != null) errorListener.onError("Error: " + e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
        }
    }
}
