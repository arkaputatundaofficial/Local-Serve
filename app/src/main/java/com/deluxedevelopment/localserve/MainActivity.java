package com.deluxedevelopment.localserve;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import com.deluxedevelopment.localserve.R;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements ServerSetup.ServerErrorListener {

    TextView tvPath, tvStatus, tvIP, tvPort, tvErrMsg;
    MaterialButton btnChangeDirectory, btnStartStop, btnCopyURL;
    ImageButton alertClose;
    LinearLayout layoutCurrentDir, layoutIP, layoutPort;
    RelativeLayout layoutError;
    boolean status = false;
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    private static final int DEFAULT_PORT = 8080;
    ServerSetup webServer;
    Uri selectedFolderUri;
    String errorMessage = "null";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvPath = findViewById(R.id.tvPath);
        tvStatus = findViewById(R.id.tvStatus);
        tvIP = findViewById(R.id.tvIP);
        tvPort = findViewById(R.id.tvPort);
        tvErrMsg = findViewById(R.id.tvErrMsg);
        alertClose = findViewById(R.id.alertClose);
        btnChangeDirectory = findViewById(R.id.btnChangeDirectory);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnCopyURL = findViewById(R.id.btnCopyURL);
        layoutCurrentDir = findViewById(R.id.layoutCurrentDir);
        layoutError = findViewById(R.id.layoutError);
        layoutIP = findViewById(R.id.layoutIP);
        layoutPort = findViewById(R.id.layoutPort);

        updateUi();

        btnChangeDirectory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
                updateUi();;
            }
        });

        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!status) {
                    if (!"null".equals(errorMessage)) return; // Don't start if errors exist

                    // 1. Set status first
                    status = true;

                    // 2. Update UI immediately
                    updateUi();

                    // 3. Create and start server
                    webServer = new ServerSetup(DEFAULT_PORT, getApplicationContext(), selectedFolderUri, new ServerSetup.ServerErrorListener() {
                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                errorMessage = message;
                                updateUi(); // Re-evaluate UI when error occurs
                            });
                        }
                    });

                    try {
                        webServer.start();
                    } catch (IOException e) {
                        errorMessage = "Server failed to start: " + e.getMessage();
                        status = false;
                        updateUi(); // Rollback UI if start fails
                    }

                } else {
                    if (webServer != null) {
                        webServer.stop();
                    }

                    // 1. Set status to false
                    status = false;

                    // 2. Immediately check for error after stopping
                    errorMessage = validateRootFolder(selectedFolderUri);

                    // 3. Update UI accordingly
                    updateUi();
                }
            }
        });

        btnCopyURL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "http://" + getPublicIpAddress() + ":" + DEFAULT_PORT;
                copyContent("Server URL", url);
                showToast(getApplicationContext(), "URL copied to Clipboard");
            }
        });

        layoutCurrentDir.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copyContent("Context URI", tvPath.getText().toString());
                showToast(getApplicationContext(), "Context copied to clipboard");
                return true;
            }
        });
        layoutError.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copyContent("Error Description", tvErrMsg.getText().toString());
                showToast(getApplicationContext(), "Error copied to clipboard");
                return true;
            }
        });
        alertClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutError.setVisibility(RelativeLayout.GONE);
            }
        });
        layoutIP.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copyContent("IP Address", tvIP.getText().toString());
                showToast(getApplicationContext(), "IP Address copied to clipboard");
                return true;
            }
        });
        layoutPort.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copyContent("Port Address", tvPort.getText().toString());
                showToast(getApplicationContext(), "Port Address copied to clipboard");
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                tvPath.setText(treeUri.toString());
                selectedFolderUri = treeUri;
                errorMessage = validateRootFolder(selectedFolderUri); // Instant check
            }
            updateUi();
        }
        updateUi();
    }

    public String getPublicIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface intf : Collections.list(interfaces)) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            // Do nothing (error handling removed)
        }
        return "Unavailable";
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            errorMessage = message;
            updateUi();
        });
    }

    void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    void copyContent(String label, String text){
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private void showViewAnimated(View view) {
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(300).start();
    }

    private void hideViewAnimated(View view) {
        view.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            view.setVisibility(View.GONE);
        }).start();
    }

    private void animateWeight(final View view, float fromWeight, float toWeight) {
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        ValueAnimator animator = ValueAnimator.ofFloat(fromWeight, toWeight);
        animator.setDuration(300);
        animator.addUpdateListener(animation -> {
            params.weight = (float) animation.getAnimatedValue();
            view.setLayoutParams(params);
        });
        animator.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi(); // Check for folder issues and update error UI when app resumes
    }

    private String validateRootFolder(Uri uri) {
        // 1. No folder selected
        if (uri == null) {
            return "No folder selected. Please choose a directory.";
        }

        // 2. Root folder not found or invalid
        DocumentFile root = DocumentFile.fromTreeUri(this, uri);
        if (root == null || !root.exists()) {
            return "Invalid or inaccessible folder. Please select a valid directory.";
        }

        // 3. Check for index.html presence
        for (DocumentFile file : root.listFiles()) {
            if (file.getName() != null && file.getName().equalsIgnoreCase("index.html")) {
                return "null"; // No error
            }
        }

        return "index.html not found in the selected folder.";
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateUi() {
        int margin = dpToPx(2.5f);
        // Update text & visibility based on status
        if (status) {
            btnStartStop.setText("Stop Server");

            if (btnCopyURL.getVisibility() != View.VISIBLE)
                showViewAnimated(btnCopyURL);
            btnStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.material_red_900)));
            tvStatus.setText("Running");
        } else {
            btnStartStop.setText("Start Server");

            if (btnCopyURL.getVisibility() == View.VISIBLE)
                hideViewAnimated(btnCopyURL);
            btnStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.material_blue_500)));
            tvStatus.setText("Stopped");
        }

        // Set IP and Port display
        tvIP.setText(getPublicIpAddress());
        tvPort.setText(String.valueOf(DEFAULT_PORT));

        // Animate layout weight changes
        LinearLayout.LayoutParams startStopParams = (LinearLayout.LayoutParams) btnStartStop.getLayoutParams();
        LinearLayout.LayoutParams copyURLParams = (LinearLayout.LayoutParams) btnCopyURL.getLayoutParams();
        float currentWeightStartStop = ((LinearLayout.LayoutParams) btnStartStop.getLayoutParams()).weight;
        float currentWeightCopyURL = ((LinearLayout.LayoutParams) btnCopyURL.getLayoutParams()).weight;

        if (status) { // running
            animateWeight(btnStartStop, currentWeightStartStop, 1f);
            startStopParams.setMargins(startStopParams.leftMargin, startStopParams.topMargin, margin, startStopParams.bottomMargin); // left-top-right-bottom
            animateWeight(btnCopyURL, currentWeightCopyURL, 1f);
            copyURLParams.setMargins(margin, copyURLParams.topMargin, copyURLParams.rightMargin, copyURLParams.bottomMargin); // left-top-right-bottom
        } else { // stopped
            animateWeight(btnStartStop, currentWeightStartStop, 2f);
            startStopParams.setMargins(startStopParams.leftMargin, startStopParams.topMargin, 0, startStopParams.bottomMargin);
            animateWeight(btnCopyURL, currentWeightCopyURL, 0f);
            copyURLParams.setMargins(0, copyURLParams.topMargin, copyURLParams.rightMargin, copyURLParams.bottomMargin);
        }

        // Error check
        errorMessage = validateRootFolder(selectedFolderUri);
        boolean hasError = !"null".equals(errorMessage);

        if (hasError) {
            tvErrMsg.setText(errorMessage);
            layoutError.setVisibility(View.VISIBLE);
        } else {
            layoutError.setVisibility(View.GONE);
        }

        // Enable/disable start button depending on errors
        if (status) {
            btnStartStop.setEnabled(true); // Always allow stop
            btnStartStop.setAlpha(1f);
        } else {
            if (hasError) {
                btnStartStop.setEnabled(false);
                btnStartStop.setAlpha(0.5f);
            } else {
                btnStartStop.setEnabled(true);
                btnStartStop.setAlpha(1f);
            }
        }
    }
}