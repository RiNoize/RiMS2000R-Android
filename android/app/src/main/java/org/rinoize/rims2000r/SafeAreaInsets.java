package org.rinoize.rims2000r;

import android.graphics.Insets;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

/** Keep interactive content clear of system bars without hiding tablet navigation. */
final class SafeAreaInsets {
    private SafeAreaInsets() {}

    @SuppressWarnings("deprecation")
    static void install(Window window, View root) {
        // Explicit edge-to-edge makes inset ownership consistent with target SDK 35.
        // The root applies them exactly once, on top of its original design padding.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        final int baseLeft = root.getPaddingLeft(), baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight(), baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                // Ignoring visibility keeps the controls out of transient system bars.
                // systemBars includes caption bars (multi-window), not only navigation.
                Insets stable = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets visible = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.tappableElement() | WindowInsets.Type.ime());
                left = Math.max(stable.left, visible.left);
                top = Math.max(stable.top, visible.top);
                right = Math.max(stable.right, visible.right);
                bottom = Math.max(stable.bottom, visible.bottom);
            } else {
                left = Math.max(insets.getSystemWindowInsetLeft(), insets.getStableInsetLeft());
                top = Math.max(insets.getSystemWindowInsetTop(), insets.getStableInsetTop());
                right = Math.max(insets.getSystemWindowInsetRight(), insets.getStableInsetRight());
                bottom = Math.max(insets.getSystemWindowInsetBottom(), insets.getStableInsetBottom());
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            }
            // Reapply from the captured base, never from previously inset padding.
            // This also handles taskbar/keyboard changes without cumulative growth.
            view.setPadding(baseLeft + left, baseTop + top,
                baseRight + right, baseBottom + bottom);
            return insets;
        });
        root.post(root::requestApplyInsets);
    }
}
