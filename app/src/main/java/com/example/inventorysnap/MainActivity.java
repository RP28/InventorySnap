package com.example.inventorysnap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
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
    private static final String KEY_ITEMS_LEGACY = "items_json";
    private static final String KEY_LISTS = "lists_json_v2";
    private static final String KEY_ACTIVE_LIST_ID = "active_list_id";

    private final ArrayList<InventoryList> scanLists = new ArrayList<>();
    private Uri pendingPhotoUri;
    private boolean showingScanScreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadLists();
        showListsScreen();
    }

    @Override
    public void onBackPressed() {
        if (showingScanScreen) {
            showListsScreen();
        } else {
            super.onBackPressed();
        }
    }

    private void showListsScreen() {
        showingScanScreen = false;

        ScrollView scrollView = baseScrollView();
        LinearLayout root = baseRoot();
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout top = toolbar();
        TextView title = screenTitle("Lists");
        top.addView(title, weightedParams(dp(48), 1));

        Button addList = iconButton("+", true);
        addList.setOnClickListener(v -> showNewListDialog());
        top.addView(addList, squareParams(48));

        SpaceView menuGap = new SpaceView(this);
        top.addView(menuGap, new LinearLayout.LayoutParams(dp(8), 1));

        Button menu = iconButton("⋮", false);
        menu.setOnClickListener(v -> showAppMenu());
        top.addView(menu, squareParams(48));
        root.addView(top, fullWidthParams(dp(54)));

        for (InventoryList list : scanLists) {
            root.addView(listCard(list), fullWidthWithBottomMargin(ViewGroup.LayoutParams.WRAP_CONTENT, dp(10)));
        }

        setContentView(scrollView);
    }

    private View listCard(InventoryList list) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(10), dp(14));
        card.setBackground(cardBackground(Color.WHITE, dp(18), Color.rgb(229, 231, 235)));
        card.setOnClickListener(v -> {
            setActiveListId(list.id);
            saveLists();
            showScanScreen();
        });

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(textBox, weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView name = new TextView(this);
        name.setText(safeListName(list));
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Color.rgb(17, 24, 39));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        textBox.addView(name);

        int active = 0;
        int done = 0;
        for (InventoryItem item : list.items) {
            if (item.done) done++; else active++;
        }

        TextView count = new TextView(this);
        count.setText(active + " • " + done + "✓");
        count.setTextSize(13);
        count.setTextColor(Color.rgb(107, 114, 128));
        count.setPadding(0, dp(4), 0, 0);
        textBox.addView(count);

        Button delete = iconButton("×", false);
        delete.setTextColor(Color.rgb(185, 28, 28));
        delete.setOnClickListener(v -> confirmDeleteList(list));
        card.addView(delete, squareParams(42));

        return card;
    }

    private void showScanScreen() {
        showingScanScreen = true;
        InventoryList activeList = activeList();

        ScrollView scrollView = baseScrollView();
        LinearLayout root = baseRoot();
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout top = toolbar();

        Button back = iconButton("‹", false);
        back.setTextSize(30);
        back.setOnClickListener(v -> showListsScreen());
        top.addView(back, squareParams(48));

        TextView title = screenTitle(safeListName(activeList));
        title.setTextSize(22);
        top.addView(title, weightedParams(dp(48), 1));

        Button addScan = iconButton("+", true);
        addScan.setOnClickListener(v -> capturePhoto());
        top.addView(addScan, squareParams(48));

        root.addView(top, fullWidthParams(dp(54)));

        ArrayList<InventoryItem> visibleItems = orderedItems(activeList);
        for (int i = 0; i < visibleItems.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, dp(10));
            root.addView(row, fullWidthParams(ViewGroup.LayoutParams.WRAP_CONTENT));

            row.addView(itemCard(visibleItems.get(i)), weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            SpaceView gap = new SpaceView(this);
            row.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

            if (i + 1 < visibleItems.size()) {
                row.addView(itemCard(visibleItems.get(i + 1)), weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            } else {
                SpaceView emptyCell = new SpaceView(this);
                row.addView(emptyCell, weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
        }

        setContentView(scrollView);
    }

    private ArrayList<InventoryItem> orderedItems(InventoryList list) {
        ArrayList<InventoryItem> visibleItems = new ArrayList<>();
        for (InventoryItem item : list.items) {
            if (!item.done) visibleItems.add(item);
        }
        for (InventoryItem item : list.items) {
            if (item.done) visibleItems.add(item);
        }
        return visibleItems;
    }

    private View itemCard(InventoryItem item) {
        int cardColor = item.done ? Color.rgb(229, 231, 235) : Color.WHITE;
        int strokeColor = item.done ? Color.rgb(209, 213, 219) : Color.rgb(229, 231, 235);
        int primaryTextColor = item.done ? Color.rgb(107, 114, 128) : Color.rgb(17, 24, 39);
        int secondaryTextColor = item.done ? Color.rgb(156, 163, 175) : Color.rgb(55, 65, 81);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(cardBackground(cardColor, dp(16), strokeColor));

        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setAdjustViewBounds(true);
        photo.setBackgroundColor(Color.rgb(243, 244, 246));
        if (item.done) photo.setAlpha(0.42f);
        try {
            photo.setImageURI(Uri.parse(item.photoUri));
        } catch (Exception ignored) {
        }
        photo.setOnClickListener(v -> showFullScreenPhoto(item.photoUri));
        card.addView(photo, fullWidthParams(dp(138)));

        TextView qty = new TextView(this);
        qty.setText("× " + item.quantity);
        qty.setTextSize(18);
        qty.setTypeface(Typeface.DEFAULT_BOLD);
        qty.setTextColor(primaryTextColor);
        qty.setPadding(0, dp(8), 0, dp(2));
        if (item.done) qty.setPaintFlags(qty.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        card.addView(qty);

        if (item.notes != null && !item.notes.trim().isEmpty()) {
            TextView notes = new TextView(this);
            notes.setText(item.notes);
            notes.setTextSize(13);
            notes.setTextColor(secondaryTextColor);
            notes.setPadding(0, dp(2), 0, 0);
            notes.setMaxLines(2);
            notes.setEllipsize(TextUtils.TruncateAt.END);
            if (item.done) notes.setPaintFlags(notes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            card.addView(notes);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        card.addView(actions);

        Button done = iconButton(item.done ? "↺" : "✓", item.done);
        done.setTextSize(18);
        done.setOnClickListener(v -> toggleDone(item));
        actions.addView(done, weightedParams(dp(38), 1));

        SpaceView gap1 = new SpaceView(this);
        actions.addView(gap1, new LinearLayout.LayoutParams(dp(6), 1));

        Button edit = iconButton("✎", false);
        edit.setTextSize(18);
        edit.setOnClickListener(v -> showItemDialog(item, item.photoUri));
        actions.addView(edit, weightedParams(dp(38), 1));

        SpaceView gap2 = new SpaceView(this);
        actions.addView(gap2, new LinearLayout.LayoutParams(dp(6), 1));

        Button delete = iconButton("×", false);
        delete.setTextSize(20);
        delete.setTextColor(Color.rgb(185, 28, 28));
        delete.setOnClickListener(v -> confirmDelete(item));
        actions.addView(delete, weightedParams(dp(38), 1));

        return card;
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
            Toast.makeText(this, "Camera app not available.", Toast.LENGTH_LONG).show();
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
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        preview.setBackgroundColor(Color.rgb(229, 231, 235));
        try {
            preview.setImageURI(Uri.parse(photoUri));
        } catch (Exception ignored) {
        }
        preview.setOnClickListener(v -> showFullScreenPhoto(photoUri));
        form.addView(preview, fullWidthParams(dp(190)));

        EditText quantityInput = new EditText(this);
        quantityInput.setSingleLine(true);
        quantityInput.setHint("Qty");
        quantityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantityInput.setText(isNew ? "1" : existing.quantity);
        quantityInput.setSelectAllOnFocus(true);
        quantityInput.setTextSize(18);
        form.addView(quantityInput, fullWidthWithTopMargin(dp(54), dp(12)));

        EditText notesInput = new EditText(this);
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP | Gravity.START);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notesInput.setText(isNew ? "" : existing.notes);
        notesInput.setHint("Notes");
        notesInput.setTextSize(16);
        form.addView(notesInput, fullWidthWithTopMargin(dp(108), dp(8)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isNew ? "+" : "✎")
                .setView(form)
                .setPositiveButton("✓", null)
                .setNegativeButton("×", (d, which) -> {
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
                    quantityInput.setError("Qty");
                    return;
                }
                try {
                    int qty = Integer.parseInt(quantity);
                    if (qty < 0) {
                        quantityInput.setError("0+");
                        return;
                    }
                } catch (NumberFormatException e) {
                    quantityInput.setError("Number");
                    return;
                }

                InventoryList activeList = activeList();
                if (isNew) {
                    InventoryItem item = new InventoryItem();
                    item.id = String.valueOf(System.currentTimeMillis());
                    item.photoUri = photoUri;
                    item.quantity = quantity;
                    item.notes = notes;
                    item.createdAt = nowText();
                    item.done = false;
                    activeList.items.add(0, item);
                } else {
                    existing.quantity = quantity;
                    existing.notes = notes;
                }
                saveLists();
                hideKeyboard(notesInput);
                dialog.dismiss();
                showScanScreen();
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

    private void showFullScreenPhoto(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.BLACK);
        image.setPadding(dp(8), dp(8), dp(8), dp(8));
        try {
            image.setImageURI(Uri.parse(uriString));
        } catch (Exception ignored) {
        }
        image.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(image);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void toggleDone(InventoryItem item) {
        InventoryList activeList = activeList();
        activeList.items.remove(item);
        item.done = !item.done;
        if (item.done) {
            activeList.items.add(item);
        } else {
            activeList.items.add(0, item);
        }
        saveLists();
        showScanScreen();
    }

    private void confirmDelete(InventoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    activeList().items.remove(item);
                    deletePhoto(item.photoUri);
                    saveLists();
                    showScanScreen();
                })
                .setNegativeButton("×", null)
                .show();
    }

    private void confirmDeleteList(InventoryList list) {
        new AlertDialog.Builder(this)
                .setTitle("Delete list?")
                .setMessage(safeListName(list))
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (InventoryItem item : list.items) {
                        deletePhoto(item.photoUri);
                    }
                    scanLists.remove(list);
                    ensureDefaultList();
                    ensureActiveListExists();
                    saveLists();
                    showListsScreen();
                })
                .setNegativeButton("×", null)
                .show();
    }

    private void showNewListDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Name");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("+")
                .setView(input)
                .setPositiveButton("✓", null)
                .setNegativeButton("×", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    input.setError("Name");
                    return;
                }
                InventoryList list = new InventoryList();
                list.id = String.valueOf(System.currentTimeMillis());
                list.name = name;
                list.createdAt = nowText();
                scanLists.add(0, list);
                setActiveListId(list.id);
                saveLists();
                hideKeyboard(input);
                dialog.dismiss();
                showScanScreen();
            });
        });

        dialog.show();
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    private void showAppMenu() {
        String[] actions = {"Export", "Import"};
        new AlertDialog.Builder(this)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) exportJson();
                    if (which == 1) showImportDialog();
                })
                .show();
    }

    private void exportJson() {
        try {
            String json = toRootJson().toString(2);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT, "Inventory Snap export");
            send.putExtra(Intent.EXTRA_TEXT, json);
            startActivity(Intent.createChooser(send, "Export"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed.", Toast.LENGTH_LONG).show();
        }
    }

    private void showImportDialog() {
        EditText input = new EditText(this);
        input.setMinLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("JSON");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Import")
                .setView(input)
                .setPositiveButton("✓", null)
                .setNegativeButton("×", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    int imported = importJson(input.getText().toString().trim());
                    saveLists();
                    hideKeyboard(input);
                    dialog.dismiss();
                    Toast.makeText(this, imported + " list(s)", Toast.LENGTH_SHORT).show();
                    showListsScreen();
                } catch (Exception e) {
                    input.setError("Invalid JSON");
                }
            });
        });
        dialog.show();
    }

    private int importJson(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Empty import");
        raw = raw.trim();

        ArrayList<InventoryList> importedLists = new ArrayList<>();
        if (raw.startsWith("[")) {
            InventoryList legacyList = new InventoryList();
            legacyList.id = String.valueOf(System.currentTimeMillis());
            legacyList.name = "Imported " + nowText();
            legacyList.createdAt = nowText();
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                InventoryItem item = InventoryItem.fromJson(array.getJSONObject(i));
                if (item.id == null || item.id.trim().isEmpty()) item.id = String.valueOf(System.currentTimeMillis() + i);
                legacyList.items.add(item);
            }
            importedLists.add(legacyList);
        } else {
            JSONObject object = new JSONObject(raw);
            JSONArray listsArray = object.optJSONArray("lists");
            if (listsArray == null) throw new IllegalArgumentException("No lists");
            for (int i = 0; i < listsArray.length(); i++) {
                InventoryList list = InventoryList.fromJson(listsArray.getJSONObject(i));
                if (list.id == null || list.id.trim().isEmpty()) list.id = String.valueOf(System.currentTimeMillis() + i);
                if (list.name == null || list.name.trim().isEmpty()) list.name = "Imported " + (i + 1);
                importedLists.add(list);
            }
        }

        if (importedLists.isEmpty()) return 0;
        for (int i = importedLists.size() - 1; i >= 0; i--) {
            scanLists.add(0, importedLists.get(i));
        }
        setActiveListId(importedLists.get(0).id);
        return importedLists.size();
    }

    private void loadLists() {
        scanLists.clear();
        String listsRaw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LISTS, "");
        if (listsRaw != null && !listsRaw.trim().isEmpty()) {
            try {
                JSONObject root = new JSONObject(listsRaw);
                JSONArray lists = root.optJSONArray("lists");
                if (lists != null) {
                    for (int i = 0; i < lists.length(); i++) {
                        scanLists.add(InventoryList.fromJson(lists.getJSONObject(i)));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (scanLists.isEmpty()) {
            InventoryList defaultList = new InventoryList();
            defaultList.id = String.valueOf(System.currentTimeMillis());
            defaultList.name = "Default";
            defaultList.createdAt = nowText();

            String legacyRaw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ITEMS_LEGACY, "[]");
            try {
                JSONArray array = new JSONArray(legacyRaw);
                for (int i = 0; i < array.length(); i++) {
                    defaultList.items.add(InventoryItem.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception ignored) {
            }
            scanLists.add(defaultList);
            setActiveListId(defaultList.id);
            saveLists();
        }

        ensureDefaultList();
        ensureActiveListExists();
    }

    private void saveLists() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_LISTS, toRootJson().toString())
                .putString(KEY_ACTIVE_LIST_ID, activeList().id)
                .apply();
    }

    private JSONObject toRootJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("version", 3);
            root.put("activeListId", activeList().id);
            JSONArray array = new JSONArray();
            for (InventoryList list : scanLists) {
                array.put(list.toJson());
            }
            root.put("lists", array);
        } catch (Exception ignored) {
        }
        return root;
    }

    private InventoryList activeList() {
        ensureDefaultList();
        String activeId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ACTIVE_LIST_ID, "");
        for (InventoryList list : scanLists) {
            if (list.id != null && list.id.equals(activeId)) return list;
        }
        return scanLists.get(0);
    }

    private void setActiveListId(String id) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_LIST_ID, id == null ? "" : id)
                .apply();
    }

    private void ensureDefaultList() {
        if (!scanLists.isEmpty()) return;
        InventoryList list = new InventoryList();
        list.id = String.valueOf(System.currentTimeMillis());
        list.name = "Default";
        list.createdAt = nowText();
        scanLists.add(list);
        setActiveListId(list.id);
    }

    private void ensureActiveListExists() {
        String activeId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ACTIVE_LIST_ID, "");
        for (InventoryList list : scanLists) {
            if (list.id != null && list.id.equals(activeId)) return;
        }
        setActiveListId(scanLists.get(0).id);
    }

    private void deletePhoto(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) return;
        try {
            ContentResolver resolver = getContentResolver();
            resolver.delete(Uri.parse(uriString), null, null);
        } catch (Exception ignored) {
        }
    }

    private ScrollView baseScrollView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 248, 252));
        return scrollView;
    }

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(14), dp(12), dp(24));
        return root;
    }

    private LinearLayout toolbar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, 0, dp(6));
        return top;
    }

    private TextView screenTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    private Button iconButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(22);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, dp(2));
        button.setTextColor(primary ? Color.WHITE : Color.rgb(37, 99, 235));
        button.setBackground(cardBackground(primary ? Color.rgb(37, 99, 235) : Color.WHITE,
                dp(14),
                primary ? Color.rgb(37, 99, 235) : Color.rgb(191, 219, 254)));
        return button;
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

    private LinearLayout.LayoutParams fullWidthWithBottomMargin(int height, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.setMargins(0, 0, 0, bottomMargin);
        return params;
    }

    private LinearLayout.LayoutParams fullWidthWithTopMargin(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams weightedParams(int height, float weight) {
        return new LinearLayout.LayoutParams(0, height, weight);
    }

    private LinearLayout.LayoutParams squareParams(int size) {
        return new LinearLayout.LayoutParams(dp(size), dp(size));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String nowText() {
        return new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(new Date());
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeListName(InventoryList list) {
        if (list == null || list.name == null || list.name.trim().isEmpty()) return "Default";
        return list.name;
    }

    private void hideKeyboard(View view) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    private static class InventoryList {
        String id;
        String name;
        String createdAt;
        ArrayList<InventoryItem> items = new ArrayList<>();

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("name", name == null ? "Default" : name);
                object.put("createdAt", createdAt == null ? "" : createdAt);
                JSONArray itemArray = new JSONArray();
                for (InventoryItem item : items) {
                    itemArray.put(item.toJson());
                }
                object.put("items", itemArray);
            } catch (Exception ignored) {
            }
            return object;
        }

        static InventoryList fromJson(JSONObject object) {
            InventoryList list = new InventoryList();
            list.id = object.optString("id", "");
            list.name = object.optString("name", "Default");
            list.createdAt = object.optString("createdAt", "");
            JSONArray array = object.optJSONArray("items");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    try {
                        list.items.add(InventoryItem.fromJson(array.getJSONObject(i)));
                    } catch (Exception ignored) {
                    }
                }
            }
            return list;
        }
    }

    private static class InventoryItem {
        String id;
        String photoUri;
        String quantity;
        String notes;
        String createdAt;
        boolean done;

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("photoUri", photoUri);
                object.put("quantity", quantity);
                object.put("notes", notes == null ? "" : notes);
                object.put("createdAt", createdAt == null ? "" : createdAt);
                object.put("done", done);
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
            item.done = object.optBoolean("done", false);
            return item;
        }
    }

    private static class SpaceView extends View {
        SpaceView(Context context) {
            super(context);
        }
    }
}
