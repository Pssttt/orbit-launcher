package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Recycled horizontal shelves. Only focus changes and scrolling animate; idle Home schedules no frames. */
final class OrbitRowsView extends RecyclerView {
    private static final DecelerateInterpolator FOCUS_EASE = new DecelerateInterpolator(2f);
    private final AppAdapter apps;
    private final AppAdapter.TileListener listener;
    private final Runnable focusHeader;
    private final boolean animations, reducedMotion, rtl;
    private final float density;
    private final RecycledViewPool cards = new RecycledViewPool();
    private final Shelves shelves = new Shelves();
    private List<OrbitRows.Row> rows = Collections.emptyList();
    private List<AppAdapter.Entry> snapshot = Collections.emptyList();
    private final Map<OrbitRows.Section, Integer> columns = new EnumMap<>(OrbitRows.Section.class);
    private int focusedPosition = -1, focusedRow, focusedColumn;

    OrbitRowsView(Context context, AppAdapter apps, AppAdapter.TileListener listener,
                  Runnable focusHeader, boolean animations, boolean reducedMotion) {
        super(context);
        this.apps = apps;
        this.listener = listener;
        this.focusHeader = focusHeader;
        this.animations = animations;
        this.reducedMotion = reducedMotion;
        density = getResources().getDisplayMetrics().density;
        rtl = getResources().getBoolean(R.bool.rtl_app_grid);
        setLayoutManager(layoutManager(VERTICAL));
        setAdapter(shelves);
        setHasFixedSize(true);
        setItemAnimator(null);
        setPadding(dp(0), dp(64), dp(0), dp(24));
        setClipToPadding(false);
        setClipChildren(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private int dp(float n) { return Math.round(n * density); }

    private LinearLayoutManager layoutManager(int orientation) {
        return new LinearLayoutManager(getContext(), orientation, false) {
            @Override public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                    @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
                return super.requestChildRectangleOnScreen(parent, child, rect, immediate || reducedMotion, focusedChildVisible);
            }
        };
    }

    void reload() {
        List<String> packages = new ArrayList<>();
        int[] categories = new int[apps.appCount()];
        long[] opened = new long[apps.appCount()];
        List<AppAdapter.Entry> entries = new ArrayList<>();
        for (int i = 0; i < apps.appCount(); i++) {
            AppAdapter.Entry entry = apps.entryAt(i);
            entries.add(entry);
            packages.add(entry.kind == AppAdapter.Entry.EMPTY ? null : entry.pkg);
            categories[i] = entry.category;
            opened[i] = LastOpened.get(getContext(), entry.pkg);
        }
        List<OrbitRows.Row> next = OrbitRows.build(packages, categories, opened, apps.dockFilled());
        boolean same = rows.size() == next.size() && snapshot.equals(entries);
        for (int i = 0; same && i < rows.size(); i++) {
            same = rows.get(i).section == next.get(i).section && rows.get(i).positions.equals(next.get(i).positions);
        }
        if (same && getAdapter() != null) return;
        OrbitRows.Section section = rows.isEmpty() ? null : rows.get(Math.min(focusedRow, rows.size() - 1)).section;
        rows = next;
        snapshot = entries;
        focusedRow = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).section == section) focusedRow = i;
        if (getAdapter() == null) setAdapter(shelves);
        shelves.notifyDataSetChanged();
    }

    int focusedPosition() { return hasFocus() ? focusedPosition : -1; }

    boolean libraryHasFocus() { return findFocus() != null && findFocus().getId() == R.id.btnLibrary; }

    boolean focusLibrary() {
        if (!apps.isLibraryAt(apps.appCount())) return false;
        focusFooter(8);
        return true;
    }

    private void focusFooter(int attempts) {
        if (attempts == 0 || !isShown()) return;
        ViewHolder holder = findViewHolderForAdapterPosition(rows.size());
        if (holder != null) holder.itemView.findViewById(R.id.btnLibrary).requestFocus();
        else {
            scrollToPosition(rows.size());
            postOnAnimation(() -> focusFooter(attempts - 1));
        }
    }

    void resetToTop() {
        focusedRow = 0;
        focusedColumn = 0;
        scrollToPosition(0);
    }

    void focusPosition(int position) {
        if (rows.isEmpty()) { if (!focusLibrary()) focusHeader.run(); return; }
        if (focusedRow < rows.size() && rows.get(focusedRow).positions.contains(position)) {
            focus(focusedRow, rows.get(focusedRow).positions.indexOf(position), 8);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            int column = rows.get(i).positions.indexOf(position);
            if (column >= 0) { focus(i, column, 8); return; }
        }
        focus(0, 0, 8);
    }

    void focusLastRow() {
        if (!rows.isEmpty()) focus(rows.size() - 1, focusedColumn, 8);
        else focusHeader.run();
    }

    private void focus(int row, int column, int attempts) {
        if (attempts == 0 || !isShown() || row < 0 || row >= rows.size()) return;
        RowHolder holder = (RowHolder) findViewHolderForAdapterPosition(row);
        if (holder == null) {
            scrollToPosition(row);
            postOnAnimation(() -> focus(row, column, attempts - 1));
            return;
        }
        int target = Math.min(column, rows.get(row).positions.size() - 1);
        ViewHolder tile = holder.row.findViewHolderForAdapterPosition(target);
        if (tile == null) {
            holder.row.scrollToPosition(target);
            postOnAnimation(() -> focus(row, target, attempts - 1));
        } else {
            View card = tile.itemView.findViewById(R.id.card);
            if (card.isFocused()) {
                animateFocus(card, true);
                listener.onTileFocused(rows.get(row).positions.get(target), card);
            } else card.requestFocus();
        }
    }

    void stopAnimations() {
        stopScroll();
        for (int i = 0; i < getChildCount(); i++) {
            ViewHolder holder = getChildViewHolder(getChildAt(i));
            if (!(holder instanceof RowHolder)) continue;
            RecyclerView row = ((RowHolder) holder).row;
            row.stopScroll();
            for (int j = 0; j < row.getChildCount(); j++) {
                View card = row.getChildAt(j).findViewById(R.id.card);
                card.animate().cancel();
            }
        }
    }

    void release() {
        stopAnimations();
        setAdapter(null);
        getRecycledViewPool().clear();
        cards.clear();
        rows = Collections.emptyList();
        snapshot = Collections.emptyList();
    }

    private final class Shelves extends Adapter<ViewHolder> {
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == 1) {
                View footer = LayoutInflater.from(getContext()).inflate(R.layout.row_app_library, parent, false);
                footer.setLayoutParams(new LayoutParams(-1, dp(64)));
                View button = footer.findViewById(R.id.btnLibrary);
                button.setOnClickListener(v -> listener.onLibraryRequested());
                button.setOnFocusChangeListener((v, focused) -> { if (focused) listener.onLibraryFocused(); });
                button.setOnKeyListener((v, key, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (key == KeyEvent.KEYCODE_DPAD_UP) { focusLastRow(); return true; }
                    return key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT
                            || key == KeyEvent.KEYCODE_DPAD_RIGHT;
                });
                return new ViewHolder(footer) {};
            }
            LinearLayout shelf = new LinearLayout(getContext());
            shelf.setOrientation(LinearLayout.VERTICAL);
            shelf.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(212)));
            shelf.setClipChildren(false);
            TextView title = new TextView(getContext());
            title.setTextSize(14);
            title.setTextColor(0xBFFFFFFF);
            title.setTypeface(UiFont.get(getContext(), R.string.font_medium));
            androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
            title.setLetterSpacing(0.14f);
            title.setPadding(dp(12), 0, dp(12), 0);
            shelf.addView(title, new LinearLayout.LayoutParams(-1, dp(32)));
            RecyclerView row = new RecyclerView(getContext());
            row.setLayoutManager(layoutManager(HORIZONTAL));
            row.setLayoutDirection(rtl ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
            row.setRecycledViewPool(cards);
            row.setItemAnimator(null);
            row.setHasFixedSize(true);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setOverScrollMode(OVER_SCROLL_NEVER);
            shelf.addView(row, new LinearLayout.LayoutParams(-1, dp(180)));
            return new RowHolder(shelf, title, row);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder viewHolder, int row) {
            if (!(viewHolder instanceof RowHolder)) return;
            RowHolder holder = (RowHolder) viewHolder;
            int[] labels = {R.string.row_favorites, R.string.row_recent, R.string.row_streaming,
                    R.string.row_media, R.string.row_music, R.string.row_games, R.string.row_social,
                    R.string.row_news, R.string.row_tools, R.string.row_apps};
            holder.title.setText(getResources().getString(labels[rows.get(row).section.ordinal()])
                    .toUpperCase(getResources().getConfiguration().getLocales().get(0)));
            holder.row.setAdapter(new Tiles(row));
        }

        @Override public int getItemViewType(int position) { return position == rows.size() ? 1 : 0; }

        @Override public int getItemCount() { return rows.size() + (apps.isLibraryAt(apps.appCount()) ? 1 : 0); }
    }

    private static final class RowHolder extends ViewHolder {
        final TextView title;
        final RecyclerView row;
        RowHolder(View view, TextView title, RecyclerView row) {
            super(view); this.title = title; this.row = row;
        }
    }

    private final class Tiles extends Adapter<CardHolder> {
        private final OrbitRows.Row row;
        Tiles(int rowIndex) { row = rows.get(rowIndex); }

        @NonNull @Override public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_app, parent, false);
            item.setLayoutParams(new LayoutParams(dp(272), dp(180)));
            View card = item.findViewById(R.id.card);
            ViewGroup.LayoutParams size = card.getLayoutParams();
            size.width = dp(240); size.height = dp(135); card.setLayoutParams(size);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
                }
            });
            card.setClipToOutline(true);
            card.setBackgroundColor(0xEC000B25);
            TextView name = item.findViewById(R.id.name);
            FrameLayout.LayoutParams nameSize = (FrameLayout.LayoutParams) name.getLayoutParams();
            nameSize.width = dp(240); nameSize.topMargin = dp(142); name.setLayoutParams(nameSize);
            ImageView icon = item.findViewById(R.id.icon);
            FrameLayout.LayoutParams iconSize = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START | Gravity.CENTER_VERTICAL);
            iconSize.setMarginStart(dp(28));
            icon.setLayoutParams(iconSize);
            TextView label = item.findViewById(R.id.label);
            FrameLayout.LayoutParams labelSize = new FrameLayout.LayoutParams(-1, -2, Gravity.START | Gravity.CENTER_VERTICAL);
            labelSize.setMarginStart(dp(100)); labelSize.setMarginEnd(dp(16));
            label.setLayoutParams(labelSize);
            label.setPadding(0, 0, 0, 0);
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setTextSize(20);
            label.setTypeface(UiFont.get(getContext(), R.string.font_medium));
            return new CardHolder(item);
        }

        @Override public void onBindViewHolder(@NonNull CardHolder holder, int column) {
            int position = row.positions.get(column);
            AppAdapter.Entry entry = apps.entryAt(position);
            String entryKey = entry.component.flattenToShortString();
            holder.card.setContentDescription(entry.name);
            holder.card.animate().cancel();
            holder.card.setScaleX(1f); holder.card.setScaleY(1f); holder.card.setTranslationY(0);
            holder.ring.setAlpha(0);
            if (entry.banner != null) {
                Drawable.ConstantState state = entry.banner.getConstantState();
                holder.banner.setImageDrawable(state == null ? entry.banner : state.newDrawable(getResources()).mutate());
                holder.banner.setVisibility(VISIBLE); holder.icon.setVisibility(GONE); holder.label.setVisibility(GONE);
            } else {
                holder.banner.setImageDrawable(null); holder.banner.setVisibility(GONE);
                holder.icon.setImageDrawable(entry.icon); holder.icon.setVisibility(VISIBLE);
                holder.label.setText(entry.label); holder.label.setVisibility(VISIBLE);
            }
            holder.name.setText(entry.name);
            holder.name.setVisibility(entry.kind == AppAdapter.Entry.CARD ? VISIBLE : GONE);
            holder.card.setOnClickListener(v -> apps.launchAt(apps.positionOf(entryKey)));
            holder.card.setOnLongClickListener(v -> {
                int current = apps.positionOf(entryKey);
                if (current >= 0) listener.onTileLongPress(current, v);
                return true;
            });
            holder.card.setOnFocusChangeListener((v, focused) -> {
                int current = apps.positionOf(entryKey);
                if (current < 0) return;
                holder.ring.setStroke(dp(2), apps.ambientColorAt(current));
                holder.ring.setAlpha(focused ? 255 : 0);
                animateFocus(v, focused);
                if (focused) {
                    int slot = holder.getBindingAdapterPosition();
                    int shelf = rows.indexOf(row);
                    if (slot == NO_POSITION || shelf < 0) return;
                    focusedPosition = current; focusedRow = shelf; focusedColumn = slot;
                    columns.put(row.section, slot);
                    listener.onTileFocused(current, v);
                }
            });
            holder.card.setOnKeyListener((v, key, event) -> {
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    if (animations && event.getRepeatCount() == 0) {
                        v.animate().cancel();
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            float scale = reducedMotion ? 1.02f : 1.04f;
                            v.setScaleX(scale); v.setScaleY(scale);
                        } else animateFocus(v, true);
                    }
                    return false;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int slot = holder.getBindingAdapterPosition();
                int shelf = rows.indexOf(row);
                if (slot == NO_POSITION || shelf < 0) return true;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    boolean previous = (key == KeyEvent.KEYCODE_DPAD_LEFT) != rtl;
                    return previous ? slot == 0 : slot == getItemCount() - 1;
                }
                if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    if (shelf == 0) focusHeader.run();
                    else focus(shelf - 1, columns.getOrDefault(rows.get(shelf - 1).section, slot), 8);
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (shelf + 1 < rows.size()) focus(shelf + 1, columns.getOrDefault(rows.get(shelf + 1).section, slot), 8);
                    else focusLibrary();
                    return true;
                }
                return false;
            });
        }

        @Override public int getItemCount() { return row.positions.size(); }

        @Override public void onViewRecycled(@NonNull CardHolder holder) {
            holder.card.animate().cancel();
            holder.banner.setImageDrawable(null); holder.icon.setImageDrawable(null);
        }
    }

    private void animateFocus(View view, boolean focused) {
        view.animate().cancel();
        float scale = focused && animations ? (reducedMotion ? 1.04f : 1.08f) : 1f;
        float lift = focused && animations && !reducedMotion ? -dp(3) : 0;
        if (!animations) {
            view.setScaleX(scale); view.setScaleY(scale); view.setTranslationY(lift);
        } else view.animate().scaleX(scale).scaleY(scale).translationY(lift)
                .setDuration(reducedMotion ? 70 : 140).setInterpolator(FOCUS_EASE).start();
    }

    private final class CardHolder extends ViewHolder {
        final View card;
        final ImageView banner, icon;
        final TextView label, name;
        final GradientDrawable ring = new GradientDrawable();
        CardHolder(View item) {
            super(item);
            card = item.findViewById(R.id.card); banner = item.findViewById(R.id.banner);
            icon = item.findViewById(R.id.icon); label = item.findViewById(R.id.label);
            name = item.findViewById(R.id.name);
            ring.setColor(Color.TRANSPARENT); ring.setCornerRadius(dp(14)); ring.setAlpha(0);
            card.setForeground(ring);
        }
    }
}
