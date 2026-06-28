package com.example.inventorysnap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE_PHOTO = 101;
    private static final String PREFS = "inventory_snap_store";
    private static final String KEY_ITEMS = "items_json";

    private final ArrayList<InventoryItem> items = new ArrayList<>();
    private LinearLayout listContainer;
    private TextView statsText;
    private TextView emptyText;
    private Uri pendingPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadItems();
        buildUi();
        renderList();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 248, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Inventory Snap");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(17, 24, 39));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Take a photo, add quantity, add notes, and keep a simple visual stock list on your phone.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(75, 85, 99));
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, 0, 0, dp(12));
        root.addView(controls);

        Button addButton = button("Take Photo + Add", true);
        addButton.setOnClickListener(v -> capturePhoto());
        controls.addView(addButton, fullWidthParams(dp(50)));

        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setOrientation(LinearLayout.HORIZONTAL);
        secondaryRow.setPadding(0, dp(8), 0, 0);
        controls.addView(secondaryRow);

        Button exportButton = button("Export JSON", false);
        exportButton.setOnClickListener(v -> exportJson());
        secondaryRow.addView(exportButton, weightedParams(dp(46), 1));

        SpaceView gap = new SpaceView(this);
        secondaryRow.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        Button importButton = button("Import JSON", false);
        importButton.setOnClickListener(v -> showImportDialog());
        secondaryRow.addView(importButton, weightedParams(dp(46), 1));

        statsText = new TextView(this);
        statsText.setTextSize(14);
        statsText.setTextColor(Color.rgb(75, 85, 99));
        statsText.setPadding(0, dp(4), 0, dp(10));
        root.addView(statsText);

        emptyText = new TextView(this);
        emptyText.setText("No inventory added yet. Tap “Take Photo + Add” to start scanning items.");
        emptyText.setTextSize(16);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextColor(Color.rgb(107, 114, 128));
        emptyText.setPadding(dp(20), dp(50), dp(20), dp(50));
        root.addView(emptyText, fullWidthParams(ViewGroup.LayoutParams.WRAP_CONTENT));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer, fullWidthParams(ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scrollView);
    }

    private void capturePhoto() {
        ContentValues values = new ContentValues();
        String fileName = "inventory_" + System.currentTimeMillis() + ".jpg";
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/InventorySnap");

        try {
            pendingPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (pendingPhotoUri == null) {
                Toast.makeText(this, "Could not create photo file.", Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAPTURE_PHOTO);
        } catch (Exception e) {
            if (pendingPhotoUri != null) deletePhoto(pendingPhotoUri.toString());
            pendingPhotoUri = null;
            Toast.makeText(this, "Camera app not available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE_PHOTO) {
            if (resultCode == RESULT_OK && pendingPhotoUri != null) {
                showItemDialog(null, pendingPhotoUri.toString());
            } else {
                if (pendingPhotoUri != null) deletePhoto(pendingPhotoUri.toString());
            }
            pendingPhotoUri = null;
        }
    }

    private void showItemDialog(InventoryItem existing, String photoUri) {
        boolean isNew = existing == null;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        form.setPadding(pad, dp(8), pad, 0);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(Color.rgb(229, 231, 235));
        try {
            preview.setImageURI(Uri.parse(photoUri));
        } catch (Exception ignored) {
        }
        form.addView(preview, fullWidthParams(dp(180)));

        TextView quantityLabel = label("Quantity");
        quantityLabel.setPadding(0, dp(14), 0, dp(4));
        form.addView(quantityLabel);

        EditText quantityInput = new EditText(this);
        quantityInput.setSingleLine(true);
        quantityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantityInput.setText(isNew ? "1" : existing.quantity);
        quantityInput.setSelectAllOnFocus(true);
        quantityInput.setTextSize(18);
        form.addView(quantityInput, fullWidthParams(dp(52)));

        TextView notesLabel = label("Notes");
        notesLabel.setPadding(0, dp(14), 0, dp(4));
        form.addView(notesLabel);

        EditText notesInput = new EditText(this);
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP | Gravity.START);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notesInput.setText(isNew ? "" : existing.notes);
        notesInput.setHint("Example: shelf A, fragile, supplier name, expiry date…");
        notesInput.setTextSize(16);
        form.addView(notesInput, fullWidthParams(dp(110)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Add inventory item" : "Edit inventory item")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, which) -> {
                    hideKeyboard(quantityInput);
                    if (isNew) deletePhoto(photoUri);
                })
                .create();

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String quantity = quantityInput.getText().toString().trim();
                String notes = notesInput.getText().toString().trim();
                if (quantity.isEmpty()) {
                    quantityInput.setError("Add a quantity");
                    return;
                }
                try {
                    int qty = Integer.parseInt(quantity);
                    if (qty < 0) {
                        quantityInput.setError("Quantity cannot be negative");
                        return;
                    }
                } catch (NumberFormatException e) {
                    quantityInput.setError("Use a whole number");
                    return;
                }

                if (isNew) {
                    InventoryItem item = new InventoryItem();
                    item.id = String.valueOf(System.currentTimeMillis());
                    item.photoUri = photoUri;
                    item.quantity = quantity;
                    item.notes = notes;
                    item.createdAt = nowText();
                    items.add(0, item);
                } else {
                    existing.quantity = quantity;
                    existing.notes = notes;
                }
                saveItems();
                renderList();
                hideKeyboard(notesInput);
                dialog.dismiss();
            });
        });

        dialog.setOnCancelListener(d -> {
            if (isNew) deletePhoto(photoUri);
        });

        dialog.show();
        quantityInput.requestFocus();
        quantityInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(quantityInput, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    private void renderList() {
        listContainer.removeAllViews();
        int totalQty = 0;
        for (InventoryItem item : items) {
            try {
                totalQty += Integer.parseInt(item.quantity);
            } catch (Exception ignored) {
            }
        }
        statsText.setText(items.size() + " scan" + (items.size() == 1 ? "" : "s") + " • total quantity " + totalQty);
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        for (InventoryItem item : items) {
            listContainer.addView(itemCard(item), fullWidthParams(ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View itemCard(InventoryItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(cardBackground(Color.WHITE, dp(18), Color.rgb(229, 231, 235)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);

        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackgroundColor(Color.rgb(229, 231, 235));
        try {
            photo.setImageURI(Uri.parse(item.photoUri));
        } catch (Exception ignored) {
        }
        card.addView(photo, fullWidthParams(dp(210)));

        TextView qty = new TextView(this);
        qty.setText("Qty: " + item.quantity);
        qty.setTextSize(22);
        qty.setTypeface(Typeface.DEFAULT_BOLD);
        qty.setTextColor(Color.rgb(17, 24, 39));
        qty.setPadding(0, dp(12), 0, dp(2));
        card.addView(qty);

        TextView date = new TextView(this);
        date.setText(item.createdAt == null ? "" : item.createdAt);
        date.setTextSize(13);
        date.setTextColor(Color.rgb(107, 114, 128));
        card.addView(date);

        if (item.notes != null && !item.notes.trim().isEmpty()) {
            TextView notes = new TextView(this);
            notes.setText(item.notes);
            notes.setTextSize(15);
            notes.setTextColor(Color.rgb(55, 65, 81));
            notes.setPadding(0, dp(8), 0, 0);
            card.addView(notes);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        card.addView(actions);

        Button edit = button("Edit", false);
        edit.setOnClickListener(v -> showItemDialog(item, item.photoUri));
        actions.addView(edit, weightedParams(dp(44), 1));

        SpaceView gap = new SpaceView(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        Button delete = button("Delete", false);
        delete.setTextColor(Color.rgb(185, 28, 28));
        delete.setOnClickListener(v -> confirmDelete(item));
        actions.addView(delete, weightedParams(dp(44), 1));

        return card;
    }

    private void confirmDelete(InventoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete item?")
                .setMessage("This removes the scan from the app and deletes the saved photo from the phone gallery folder used by this app.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    items.remove(item);
                    deletePhoto(item.photoUri);
                    saveItems();
                    renderList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportJson() {
        try {
            String json = toJsonArray().toString(2);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT, "Inventory Snap export");
            send.putExtra(Intent.EXTRA_TEXT, json);
            startActivity(Intent.createChooser(send, "Export inventory JSON"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not export inventory.", Toast.LENGTH_LONG).show();
        }
    }

    private void showImportDialog() {
        EditText input = new EditText(this);
        input.setMinLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Paste JSON exported from this app here. Imported items are added to the top of the current list.");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Import JSON")
                .setView(input)
                .setPositiveButton("Import", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    JSONArray array = new JSONArray(input.getText().toString().trim());
                    ArrayList<InventoryItem> imported = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        InventoryItem item = InventoryItem.fromJson(array.getJSONObject(i));
                        if (item.id == null || item.id.trim().isEmpty()) item.id = String.valueOf(System.currentTimeMillis() + i);
                        imported.add(item);
                    }
                    items.addAll(0, imported);
                    saveItems();
                    renderList();
                    hideKeyboard(input);
                    dialog.dismiss();
                    Toast.makeText(this, "Imported " + imported.size() + " item(s).", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    input.setError("Invalid JSON export");
                }
            });
        });
        dialog.show();
    }

    private void loadItems() {
        items.clear();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                items.add(InventoryItem.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveItems() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_ITEMS, toJsonArray().toString())
                .apply();
    }

    private JSONArray toJsonArray() {
        JSONArray array = new JSONArray();
        for (InventoryItem item : items) {
            array.put(item.toJson());
        }
        return array;
    }

    private void deletePhoto(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) return;
        try {
            ContentResolver resolver = getContentResolver();
            resolver.delete(Uri.parse(uriString), null, null);
        } catch (Exception ignored) {
        }
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(37, 99, 235));
        button.setBackground(cardBackground(primary ? Color.rgb(37, 99, 235) : Color.WHITE, dp(14), primary ? Color.rgb(37, 99, 235) : Color.rgb(191, 219, 254)));
        return button;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(Color.rgb(55, 65, 81));
        return label;
    }

    private GradientDrawable cardBackground(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthParams(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams weightedParams(int height, float weight) {
        return new LinearLayout.LayoutParams(0, height, weight);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String nowText() {
        return new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(new Date());
    }

    private void hideKeyboard(View view) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private static class InventoryItem {
        String id;
        String photoUri;
        String quantity;
        String notes;
        String createdAt;

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("photoUri", photoUri);
                object.put("quantity", quantity);
                object.put("notes", notes == null ? "" : notes);
                object.put("createdAt", createdAt == null ? "" : createdAt);
            } catch (Exception ignored) {
            }
            return object;
        }

        static InventoryItem fromJson(JSONObject object) {
            InventoryItem item = new InventoryItem();
            item.id = object.optString("id", "");
            item.photoUri = object.optString("photoUri", "");
            item.quantity = object.optString("quantity", "1");
            item.notes = object.optString("notes", "");
            item.createdAt = object.optString("createdAt", "");
            return item;
        }
    }

    private static class SpaceView extends View {
        SpaceView(Context context) {
            super(context);
        }
    }
}
