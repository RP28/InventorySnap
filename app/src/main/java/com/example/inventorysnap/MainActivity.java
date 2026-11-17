
package com.example.inventorysnap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int CAMERA_REQUEST = 101;
    private static final String PREFS = "inventory_snap_store";
    private static final String LISTS_KEY = "lists_json_v2";
    private static final String ACTIVE_KEY = "active_list_id";
    private static final String LEGACY_KEY = "items_json";

    private static final int BACKGROUND = 0xfff6f8fc;
    private static final int BLUE = 0xff2563eb;
    private static final int DARK = 0xff111827;

    private final ArrayList<InventoryList> lists = new ArrayList<>();
    private final ExecutorService imageWorker =
        Executors.newSingleThreadExecutor();
    private final Set<String> visited = new HashSet<>();

    private Uri pendingPhoto;
    private String screen = "lists";
    private String reviewingListId = "";
    private String currentItemId = "";
    private int imageGeneration = 0;
    private ImageView activeImage;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        load();

        if (state != null) {
            String uri = state.getString("pendingPhoto");
            if (uri != null) {
                pendingPhoto = Uri.parse(uri);
            }
        }

        showLists();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (pendingPhoto != null) {
            state.putString("pendingPhoto", pendingPhoto.toString());
        }
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if ("review".equals(screen)) {
            showCapture();
        } else if ("capture".equals(screen)) {
            showLists();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        imageGeneration++;
        imageWorker.shutdownNow();
        super.onDestroy();
    }

    // Create only the current screen's views.
    private LinearLayout root() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
            dp(18),
            dp(18) + statusBarHeight(),
            dp(18),
            dp(24) + navigationBarHeight()
        );

        scroll.addView(root);

        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(
                dp(18) + insets.getSystemWindowInsetLeft(),
                dp(18) + insets.getSystemWindowInsetTop(),
                dp(18) + insets.getSystemWindowInsetRight(),
                dp(24) + insets.getSystemWindowInsetBottom()
            );
            return insets;
        });

        setContentView(scroll);
        scroll.requestApplyInsets();
        return root;
    }

    // INVENTORY LIST SCREEN
    private void showLists() {
        screen = "lists";
        invalidateImage();

        LinearLayout root = root();
        LinearLayout header = horizontal();

        TextView heading = title("Inventory Snap");
        header.addView(
            heading,
            new LinearLayout.LayoutParams(0, dp(56), 1)
        );

        Button add = button("+ List", true);
        add.setOnClickListener(v -> addList());
        header.addView(add);
        root.addView(header, matchWrap());

        root.addView(
            text(
                "Choose a list to capture photos or review what you found.",
                15,
                0xff6b7280
            ),
            spaced(0, 14)
        );

        for (InventoryList list : lists) {
            LinearLayout row = horizontal();
            row.setPadding(dp(14), dp(12), dp(10), dp(12));
            row.setBackground(shape(Color.WHITE, 16, 0xffe5e7eb));

            LinearLayout left = vertical();

            TextView name = text(list.name, 19, DARK);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            left.addView(name);

            int found = 0;
            int missing = 0;

            for (InventoryItem item : list.items) {
                if ("found".equals(item.status)) found++;
                if ("not_found".equals(item.status)) missing++;
            }

            left.addView(
                text(
                    list.items.size() + " photos  •  " +
                    found + " found  •  " +
                    missing + " not found",
                    12,
                    0xff6b7280
                )
            );

            row.addView(
                left,
                new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
                )
            );

            Button delete = button("×", false);
            delete.setTextColor(0xffb91c1c);
            delete.setOnClickListener(
                v -> confirmDeleteList(list)
            );
            row.addView(delete);

            row.setOnClickListener(v -> {
                setActive(list.id);
                showCapture();
            });

            root.addView(row, spaced(0, 12));
        }

        Button backup = button("Import / Export JSON", false);
        backup.setOnClickListener(v -> showBackupMenu());
        root.addView(backup, spaced(8, 14));
    }

    // CAPTURE SCREEN
    private void showCapture() {
        screen = "capture";
        invalidateImage();

        InventoryList list = active();
        LinearLayout root = root();
        LinearLayout bar = horizontal();

        Button back = button("‹", false);
        back.setOnClickListener(v -> showLists());
        bar.addView(back);

        TextView heading = title(list.name);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(
            heading,
            new LinearLayout.LayoutParams(0, dp(56), 1)
        );

        root.addView(bar);

        root.addView(
            text(
                list.items.size() + " photos saved",
                17,
                0xff4b5563
            ),
            spaced(20, 10)
        );

        TextView info = text(
            "Capture mode\n\n" +
            "Take a photo to add it to this list. " +
            "You can enter quantity and notes immediately. " +
            "Photos are not loaded on this screen.",
            17,
            DARK
        );

        info.setPadding(dp(22), dp(28), dp(22), dp(28));
        info.setBackground(
            shape(Color.WHITE, 20, 0xffe5e7eb)
        );

        root.addView(info, spaced(0, 22));

        Button camera = button("📷  Take photo", true);
        camera.setTextSize(20);
        camera.setOnClickListener(v -> takePhoto());
        root.addView(camera, spaced(0, 14));

        if (!list.items.isEmpty()) {
            Button editLast = button(
                "✎  Edit last photo details",
                false
            );

            editLast.setOnClickListener(v ->
                editItem(
                    list.items.get(list.items.size() - 1)
                )
            );

            root.addView(editLast, spaced(0, 10));
        }

        Button find = button("🔎  Find mode", false);
        find.setTextSize(20);
        find.setEnabled(!list.items.isEmpty());
        find.setOnClickListener(v -> startReview());
        root.addView(find, spaced(0, 12));

        root.addView(
            text(
                "In Find mode, view one photo at a time. " +
                "Mark Found, Skip or Not found. " +
                "Skipped photos remain unresolved.",
                14,
                0xff6b7280
            ),
            spaced(14, 12)
        );
    }

    // FIND MODE
    private void startReview() {
        visited.clear();
        currentItemId = "";
        reviewingListId = active().id;
        showNextCard();
    }

    private void showNextCard() {
        InventoryList list = active();

        if (!list.id.equals(reviewingListId)) {
            showCapture();
            return;
        }

        InventoryItem chosen = null;

        // Show unresolved items first.
        for (InventoryItem item : list.items) {
            if (!visited.contains(item.id) &&
                "pending".equals(item.status)) {
                chosen = item;
                break;
            }
        }

        // Then previously reviewed items.
        if (chosen == null) {
            for (InventoryItem item : list.items) {
                if (!visited.contains(item.id)) {
                    chosen = item;
                    break;
                }
            }
        }

        if (chosen == null) {
            showReviewComplete();
            return;
        }

        currentItemId = chosen.id;
        showCard(chosen);
    }

    private void showCard(InventoryItem item) {
        screen = "review";
        invalidateImage();

        LinearLayout root = root();
        LinearLayout bar = horizontal();

        Button back = button("‹", false);
        back.setOnClickListener(v -> showCapture());
        bar.addView(back);

        TextView heading = title("Find mode");
        bar.addView(
            heading,
            new LinearLayout.LayoutParams(0, dp(56), 1)
        );

        Button exit = button("Done", false);
        exit.setOnClickListener(v -> showCapture());
        bar.addView(exit);
        root.addView(bar);

        root.addView(
            text(
                (visited.size() + 1) + " / " +
                active().items.size() + "   •   " +
                label(item.status),
                15,
                0xff6b7280
            ),
            spaced(16, 10)
        );

        // Only a single image is rendered.
        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setBackground(
            shape(0xffe5e7eb, 18, 0xffd1d5db)
        );
        photo.setContentDescription(
            "Current inventory photo"
        );

        int availableHeight =
            getResources().getDisplayMetrics().heightPixels -
            dp(390);

        root.addView(
            photo,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(dp(210), availableHeight)
            )
        );

        activeImage = photo;
        loadScaledPhoto(photo, item.photoUri);

        // Quantity and notes displayed with the image.
        String details =
            "Quantity: " + item.quantity +
            (item.notes.isEmpty() ?
                "" : "\n" + item.notes);

        root.addView(
            text(details, 16, DARK),
            spaced(14, 10)
        );

        LinearLayout actions = horizontal();

        Button missing = button("Not found", false);
        missing.setTextColor(0xffb91c1c);
        missing.setOnClickListener(
            v -> decide(item, "not_found")
        );

        Button skip = button("Skip", false);
        skip.setOnClickListener(
            v -> decide(item, null)
        );

        Button found = button("Found", true);
        found.setOnClickListener(
            v -> decide(item, "found")
        );

        actions.addView(
            missing,
            new LinearLayout.LayoutParams(0, dp(56), 1)
        );
        actions.addView(space(6));
        actions.addView(
            skip,
            new LinearLayout.LayoutParams(0, dp(56), 0.75f)
        );
        actions.addView(space(6));
        actions.addView(
            found,
            new LinearLayout.LayoutParams(0, dp(56), 0.9f)
        );

        root.addView(actions, spaced(18, 6));

        LinearLayout more = horizontal();

        Button edit = button("Edit details", false);
        edit.setOnClickListener(v -> editItem(item));

        Button delete = button("Delete photo", false);
        delete.setTextColor(0xffb91c1c);
        delete.setOnClickListener(
            v -> confirmDeleteItem(item)
        );

        more.addView(
            edit,
            new LinearLayout.LayoutParams(0, dp(48), 1)
        );
        more.addView(space(8));
        more.addView(
            delete,
            new LinearLayout.LayoutParams(0, dp(48), 1)
        );

        root.addView(more, spaced(10, 6));
    }

    private void decide(InventoryItem item, String status) {
        if (!item.id.equals(currentItemId)) return;

        if (status != null) {
            item.status = status;
            save();
        }

        visited.add(item.id);
        showNextCard();
    }

    private void showReviewComplete() {
        screen = "review";
        invalidateImage();

        LinearLayout root = root();

        root.addView(
            title("Review complete"),
            spaced(30, 8)
        );

        root.addView(
            text(
                "You have seen every photo in this session. " +
                "Skipped photos remain pending, and saved " +
                "decisions will be available next time.",
                17,
                DARK
            ),
            spaced(10, 20)
        );

        Button again = button("Review again", true);
        again.setOnClickListener(v -> startReview());
        root.addView(again, spaced(0, 12));

        Button back = button("Back to capture", false);
        back.setOnClickListener(v -> showCapture());
        root.addView(back, spaced(0, 12));
    }

    // Decode a reduced-resolution image off the UI thread.
    private void loadScaledPhoto(
        ImageView target,
        String uri
    ) {
        final int generation = imageGeneration;

        imageWorker.execute(() -> {
            Bitmap bitmap = null;

            try {
                Uri parsed = Uri.parse(uri);

                BitmapFactory.Options bounds =
                    new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;

                try (InputStream stream =
                    getContentResolver().openInputStream(parsed)) {
                    BitmapFactory.decodeStream(
                        stream, null, bounds
                    );
                }

                int sample = 1;

                while (
                    bounds.outWidth / sample > 1100 ||
                    bounds.outHeight / sample > 1100
                ) {
                    sample *= 2;
                }

                BitmapFactory.Options options =
                    new BitmapFactory.Options();

                options.inSampleSize = sample;
                options.inPreferredConfig =
                    Bitmap.Config.RGB_565;

                try (InputStream stream =
                    getContentResolver().openInputStream(parsed)) {
                    bitmap = BitmapFactory.decodeStream(
                        stream, null, options
                    );
                }

            } catch (Exception ignored) {
            }

            final Bitmap result = bitmap;

            runOnUiThread(() -> {
                if (
                    generation == imageGeneration &&
                    target == activeImage &&
                    !isFinishing()
                ) {
                    if (result != null) {
                        target.setImageBitmap(result);
                    } else {
                        target.setContentDescription(
                            "Image unavailable"
                        );
                    }
                } else if (result != null) {
                    result.recycle();
                }
            });
        });
    }

    private void invalidateImage() {
        imageGeneration++;

        if (activeImage != null) {
            activeImage.setImageDrawable(null);
        }

        activeImage = null;
    }

    // CAMERA
    private void takePhoto() {
        ContentValues values = new ContentValues();

        values.put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "inventory_" +
            System.currentTimeMillis() +
            ".jpg"
        );

        values.put(
            MediaStore.Images.Media.MIME_TYPE,
            "image/jpeg"
        );

        values.put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES +
            "/InventorySnap"
        );

        try {
            pendingPhoto = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            );

            if (pendingPhoto == null) {
                throw new IllegalStateException(
                    "Could not create image"
                );
            }

            Intent camera = new Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            );

            camera.putExtra(
                MediaStore.EXTRA_OUTPUT,
                pendingPhoto
            );

            camera.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            startActivityForResult(camera, CAMERA_REQUEST);

        } catch (Exception ex) {
            if (pendingPhoto != null) {
                deletePhoto(pendingPhoto.toString());
            }

            pendingPhoto = null;

            Toast.makeText(
                this,
                "Camera unavailable",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
        int request,
        int result,
        Intent data
    ) {
        super.onActivityResult(request, result, data);

        if (request != CAMERA_REQUEST) return;

        InventoryItem newlyCaptured = null;

        if (pendingPhoto != null) {
            if (result == RESULT_OK) {
                InventoryItem item = new InventoryItem();

                item.id = UUID.randomUUID().toString();
                item.photoUri = pendingPhoto.toString();
                item.createdAt = now();

                active().items.add(item);
                save();

                newlyCaptured = item;

                Toast.makeText(
                    this,
                    "Photo saved",
                    Toast.LENGTH_SHORT
                ).show();

            } else {
                deletePhoto(pendingPhoto.toString());
            }
        }

        pendingPhoto = null;
        showCapture();

        // Automatically open quantity and notes after capture.
        if (newlyCaptured != null) {
            editItem(newlyCaptured);
        }
    }

    // QUANTITY AND NOTES
    private void editItem(InventoryItem item) {
        LinearLayout form = vertical();
        form.setPadding(
            dp(20), dp(10), dp(20), 0
        );

        EditText qty = new EditText(this);
        qty.setHint("Quantity");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER);
        qty.setText(item.quantity);
        form.addView(qty);

        EditText notes = new EditText(this);
        notes.setHint("Notes");
        notes.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        notes.setMinLines(2);
        notes.setText(item.notes);
        form.addView(notes);

        AlertDialog dialog =
            new AlertDialog.Builder(this)
                .setTitle("Quantity and notes")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                String value =
                    qty.getText().toString().trim();

                try {
                    if (Integer.parseInt(value) < 0) {
                        throw new NumberFormatException();
                    }
                } catch (Exception ex) {
                    qty.setError(
                        "Enter a non-negative number"
                    );
                    return;
                }

                item.quantity = value;
                item.notes =
                    notes.getText().toString().trim();

                save();
                dialog.dismiss();

                if ("review".equals(screen)) {
                    showCard(item);
                } else {
                    showCapture();
                }
            })
        );

        dialog.show();
    }

    // DELETE ITEM
    private void confirmDeleteItem(InventoryItem item) {
        new AlertDialog.Builder(this)
            .setTitle("Delete this photo?")
            .setMessage(
                "This also deletes the saved photo " +
                "from your gallery."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (d, w) -> {
                active().items.remove(item);
                deletePhoto(item.photoUri);
                visited.remove(item.id);
                save();
                showNextCard();
            })
            .show();
    }

    // LIST MANAGEMENT
    private void addList() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("List name");

        input.setPadding(
            dp(20), dp(8), dp(20), dp(8)
        );

        AlertDialog dialog =
            new AlertDialog.Builder(this)
                .setTitle("New list")
                .setView(input)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(v ->
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(w -> {

                String name =
                    input.getText().toString().trim();

                if (name.isEmpty()) {
                    input.setError("Required");
                    return;
                }

                InventoryList list = new InventoryList();
                list.id = UUID.randomUUID().toString();
                list.name = name;
                list.createdAt = now();

                lists.add(0, list);
                setActive(list.id);
                save();

                dialog.dismiss();
                showCapture();
            })
        );

        dialog.show();
    }

    private void confirmDeleteList(InventoryList list) {
        new AlertDialog.Builder(this)
            .setTitle("Delete " + list.name + "?")
            .setMessage(
                "This also deletes all photos belonging " +
                "to this list from your gallery."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (d, w) -> {

                for (InventoryItem item : list.items) {
                    deletePhoto(item.photoUri);
                }

                lists.remove(list);
                ensureDefault();
                setActive(lists.get(0).id);
                save();
                showLists();
            })
            .show();
    }

    private void deletePhoto(String uri) {
        try {
            getContentResolver().delete(
                Uri.parse(uri), null, null
            );
        } catch (Exception ignored) {
        }
    }

    // IMPORT / EXPORT
    private void showBackupMenu() {
        new AlertDialog.Builder(this)
            .setItems(
                new String[]{
                    "Export JSON",
                    "Import JSON"
                },
                (d, which) -> {

                    if (which == 0) {
                        Intent send = new Intent(
                            Intent.ACTION_SEND
                        );

                        send.setType("application/json");

                        send.putExtra(
                            Intent.EXTRA_SUBJECT,
                            "Inventory Snap export"
                        );

                        send.putExtra(
                            Intent.EXTRA_TEXT,
                            toRootJson().toString()
                        );

                        startActivity(
                            Intent.createChooser(
                                send, "Export"
                            )
                        );
                    } else {
                        importDialog();
                    }
                }
            )
            .show();
    }

    private void importDialog() {
        EditText input = new EditText(this);
        input.setMinLines(8);
        input.setGravity(Gravity.TOP);
        input.setHint("Paste exported JSON");

        AlertDialog dialog =
            new AlertDialog.Builder(this)
                .setTitle("Import JSON")
                .setView(input)
                .setPositiveButton("Import", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(x ->
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                try {
                    String raw =
                        input.getText().toString().trim();

                    ArrayList<InventoryList> imported =
                        new ArrayList<>();

                    if (raw.startsWith("[")) {
                        InventoryList legacy =
                            new InventoryList();

                        legacy.id =
                            UUID.randomUUID().toString();

                        legacy.name = "Imported " + now();
                        legacy.createdAt = now();

                        JSONArray arr = new JSONArray(raw);

                        for (int i = 0; i < arr.length(); i++) {
                            legacy.items.add(
                                InventoryItem.fromJson(
                                    arr.getJSONObject(i)
                                )
                            );
                        }

                        imported.add(legacy);
                    } else {
                        JSONArray arr =
                            new JSONObject(raw)
                                .getJSONArray("lists");

                        for (int i = 0; i < arr.length(); i++) {
                            imported.add(
                                InventoryList.fromJson(
                                    arr.getJSONObject(i)
                                )
                            );
                        }
                    }

                    if (imported.isEmpty()) {
                        throw new IllegalArgumentException(
                            "No lists"
                        );
                    }

                    for (InventoryList list : imported) {
                        list.id =
                            UUID.randomUUID().toString();

                        for (InventoryItem item : list.items) {
                            item.id =
                                UUID.randomUUID().toString();
                        }
                    }

                    lists.addAll(0, imported);
                    setActive(imported.get(0).id);
                    save();

                    dialog.dismiss();
                    showLists();

                } catch (Exception ex) {
                    input.setError("Invalid JSON");
                }
            })
        );

        dialog.show();
    }

    // PERSISTENCE
    private void load() {
        lists.clear();

        String raw =
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(LISTS_KEY, "");

        try {
            JSONArray arr =
                new JSONObject(raw).getJSONArray("lists");

            for (int i = 0; i < arr.length(); i++) {
                lists.add(
                    InventoryList.fromJson(
                        arr.getJSONObject(i)
                    )
                );
            }

        } catch (Exception ignored) {
        }

        if (lists.isEmpty()) {
            InventoryList legacy = new InventoryList();

            legacy.id = UUID.randomUUID().toString();
            legacy.name = "Default";
            legacy.createdAt = now();

            try {
                JSONArray arr = new JSONArray(
                    getSharedPreferences(
                        PREFS, MODE_PRIVATE
                    ).getString(LEGACY_KEY, "[]")
                );

                for (int i = 0; i < arr.length(); i++) {
                    legacy.items.add(
                        InventoryItem.fromJson(
                            arr.getJSONObject(i)
                        )
                    );
                }

            } catch (Exception ignored) {
            }

            lists.add(legacy);
            setActive(legacy.id);
            save();
        }
    }

    private void save() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(
                LISTS_KEY,
                toRootJson().toString()
            )
            .putString(
                ACTIVE_KEY,
                active().id
            )
            .apply();
    }

    private JSONObject toRootJson() {
        JSONObject obj = new JSONObject();
        JSONArray arr = new JSONArray();

        try {
            obj.put("version", 4);
            obj.put("activeListId", active().id);

            for (InventoryList list : lists) {
                arr.put(list.toJson());
            }

            obj.put("lists", arr);

        } catch (Exception ignored) {
        }

        return obj;
    }

    private InventoryList active() {
        ensureDefault();

        String id =
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(ACTIVE_KEY, "");

        for (InventoryList list : lists) {
            if (list.id.equals(id)) return list;
        }

        return lists.get(0);
    }

    private void ensureDefault() {
        if (!lists.isEmpty()) return;

        InventoryList list = new InventoryList();
        list.id = UUID.randomUUID().toString();
        list.name = "Default";
        list.createdAt = now();

        lists.add(list);
    }

    private void setActive(String id) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(ACTIVE_KEY, id)
            .apply();
    }

    private static String label(String status) {
        if ("found".equals(status)) {
            return "Previously found";
        }

        if ("not_found".equals(status)) {
            return "Previously not found";
        }

        return "Pending";
    }

    // UI UTILITIES
    private int statusBarHeight() {
        int id = getResources().getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        );

        return id > 0 ?
            getResources().getDimensionPixelSize(id) :
            dp(24);
    }

    private int navigationBarHeight() {
        int id = getResources().getIdentifier(
            "navigation_bar_height",
            "dimen",
            "android"
        );

        return id > 0 ?
            getResources().getDimensionPixelSize(id) :
            dp(16);
    }

    private int dp(int value) {
        return Math.round(
            value *
            getResources().getDisplayMetrics().density
        );
    }

    private static String now() {
        return new SimpleDateFormat(
            "dd MMM yyyy, h:mm a",
            Locale.getDefault()
        ).format(new Date());
    }

    private LinearLayout horizontal() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.HORIZONTAL);
        x.setGravity(Gravity.CENTER_VERTICAL);
        return x;
    }

    private LinearLayout vertical() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        return x;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams spaced(
        int top,
        int bottom
    ) {
        LinearLayout.LayoutParams p = matchWrap();

        p.setMargins(
            0, dp(top), 0, dp(bottom)
        );

        return p;
    }

    private View space(int width) {
        View v = new View(this);

        v.setLayoutParams(
            new LinearLayout.LayoutParams(
                dp(width), 1
            )
        );

        return v;
    }

    private TextView text(
        String s,
        int size,
        int color
    ) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView title(String s) {
        TextView t = text(s, 23, DARK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable shape(
        int color,
        int radius,
        int border
    ) {
        GradientDrawable b = new GradientDrawable();

        b.setColor(color);
        b.setCornerRadius(dp(radius));
        b.setStroke(dp(1), border);

        return b;
    }

    private Button button(
        String s,
        boolean primary
    ) {
        Button b = new Button(this);

        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);

        b.setTextColor(
            primary ? Color.WHITE : BLUE
        );

        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(8), 0, dp(8), 0);

        b.setBackground(
            shape(
                primary ? BLUE : Color.WHITE,
                14,
                primary ? BLUE : 0xffbfdbfe
            )
        );

        b.setMinHeight(dp(48));

        return b;
    }

    // DATA MODELS
    private static class InventoryList {
        String id = "";
        String name = "Default";
        String createdAt = "";

        ArrayList<InventoryItem> items =
            new ArrayList<>();

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            JSONArray arr = new JSONArray();

            try {
                obj.put("id", id);
                obj.put("name", name);
                obj.put("createdAt", createdAt);

                for (InventoryItem item : items) {
                    arr.put(item.toJson());
                }

                obj.put("items", arr);

            } catch (Exception ignored) {
            }

            return obj;
        }

        static InventoryList fromJson(JSONObject obj) {
            InventoryList list = new InventoryList();

            list.id = obj.optString(
                "id",
                UUID.randomUUID().toString()
            );

            list.name = obj.optString(
                "name", "Default"
            );

            list.createdAt = obj.optString(
                "createdAt", ""
            );

            JSONArray arr = obj.optJSONArray("items");

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        list.items.add(
                            InventoryItem.fromJson(
                                arr.getJSONObject(i)
                            )
                        );
                    } catch (Exception ignored) {
                    }
                }
            }

            return list;
        }
    }

    private static class InventoryItem {
        String id = UUID.randomUUID().toString();
        String photoUri = "";
        String quantity = "1";
        String notes = "";
        String createdAt = "";
        String status = "pending";

        JSONObject toJson() {
            JSONObject obj = new JSONObject();

            try {
                obj.put("id", id);
                obj.put("photoUri", photoUri);
                obj.put("quantity", quantity);
                obj.put("notes", notes);
                obj.put("createdAt", createdAt);
                obj.put("status", status);

                // Backward compatibility.
                obj.put(
                    "done",
                    "found".equals(status)
                );

            } catch (Exception ignored) {
            }

            return obj;
        }

        static InventoryItem fromJson(JSONObject obj) {
            InventoryItem item = new InventoryItem();

            item.id = obj.optString(
                "id", item.id
            );

            item.photoUri = obj.optString(
                "photoUri", ""
            );

            item.quantity = obj.optString(
                "quantity", "1"
            );

            item.notes = obj.optString(
                "notes", ""
            );

            item.createdAt = obj.optString(
                "createdAt", ""
            );

            String saved = obj.optString(
                "status", ""
            );

            if (
                "found".equals(saved) ||
                "not_found".equals(saved) ||
                "pending".equals(saved)
            ) {
                item.status = saved;
            } else {
                item.status = obj.optBoolean(
                    "done", false
                ) ? "found" : "pending";
            }

            return item;
        }
    }
}
