package com.invictus.xmd.ui;

/**
 * Theme color + dark mode. Picker/switch logic moved verbatim from
 * MainActivity.setupThemePicker()/toggleDarkMode() (old Settings dialog) --
 * same recreate()-on-change approach. recreate() here targets
 * SettingsActivity (this fragment's host), which now applies the theme
 * itself in onCreate() (like MainActivity/ChallengeActivity do) so the
 * recreate actually repaints this screen. MainActivity picks up the change
 * on its own next onResume (it compares the currently-applied theme style
 * against Settings and recreates itself if they've diverged), so backing
 * out of Settings repaints it immediately too, no app restart needed.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J$\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\b2\b\u0010\t\u001a\u0004\u0018\u00010\nH\u0016J\u001a\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u00042\b\u0010\t\u001a\u0004\u0018\u00010\nH\u0016J\u0010\u0010\u000e\u001a\u00020\f2\u0006\u0010\u0007\u001a\u00020\u000fH\u0002J\b\u0010\u0010\u001a\u00020\fH\u0002\u00a8\u0006\u0011"}, d2 = {"Lcom/invictus/xmd/ui/SettingsAppearanceFragment;", "Landroidx/fragment/app/Fragment;", "()V", "onCreateView", "Landroid/view/View;", "inflater", "Landroid/view/LayoutInflater;", "container", "Landroid/view/ViewGroup;", "savedInstanceState", "Landroid/os/Bundle;", "onViewCreated", "", "view", "setupThemePicker", "Landroid/widget/LinearLayout;", "toggleDarkMode", "app_liteDebug"})
public final class SettingsAppearanceFragment extends androidx.fragment.app.Fragment {
    
    public SettingsAppearanceFragment() {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.view.View onCreateView(@org.jetbrains.annotations.NotNull()
    android.view.LayoutInflater inflater, @org.jetbrains.annotations.Nullable()
    android.view.ViewGroup container, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
        return null;
    }
    
    @java.lang.Override()
    public void onViewCreated(@org.jetbrains.annotations.NotNull()
    android.view.View view, @org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void toggleDarkMode() {
    }
    
    private final void setupThemePicker(android.widget.LinearLayout container) {
    }
}