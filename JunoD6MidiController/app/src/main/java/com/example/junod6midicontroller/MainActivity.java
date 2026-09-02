package com.example.junod6midicontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "juno_d6_midi_prefs";
    private static final String KEY_BANKS_JSON = "banks_json";
    private static final String KEY_SELECTED_THEME = "selected_theme";

    private static final int THEME_AMBER = 0;
    private static final int THEME_MATRIX = 1;
    private static final int THEME_NEON = 2;

    private final List<Bank> banks = new ArrayList<>();

    private Spinner spinnerBanks;
    private LinearLayout layoutPresets;
    private TextView tvEmptyPresets;
    private Button btnNewPreset;
    private Button btnDeleteBank;
    private TextView tvMidiStatus;
    private TextView tvMidiStatusDot;

    private int selectedBankIndex = -1;
    private int activePresetIndex = -1;
    private int selectedTheme = THEME_AMBER;
    private boolean performanceMode = false;

    private MidiManager midiManager;
    private MidiDeviceInfo selectedMidiDeviceInfo;
    private ThemePalette palette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSelectedTheme();
        applySelectedTheme();
        configureSystemBars();
        loadBanks();

        midiManager = (MidiManager) getSystemService(Context.MIDI_SERVICE);

        buildMainScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMidiDevices();
    }

    @Override
    public void onBackPressed() {
        if (performanceMode) {
            showEditorMode();
        } else {
            super.onBackPressed();
        }
    }

    private void loadSelectedTheme() {
        SharedPreferences preferences = getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        selectedTheme = preferences.getInt(
                KEY_SELECTED_THEME,
                THEME_AMBER
        );

        if (selectedTheme < THEME_AMBER || selectedTheme > THEME_NEON) {
            selectedTheme = THEME_AMBER;
        }
    }

    private void saveSelectedTheme() {
        SharedPreferences preferences = getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        preferences.edit()
                .putInt(KEY_SELECTED_THEME, selectedTheme)
                .apply();
    }

    private void applySelectedTheme() {
        if (selectedTheme == THEME_MATRIX) {
            palette = new ThemePalette(
                    R.drawable.background_matrix,

                    Color.rgb(5, 8, 7),
                    Color.argb(70, 0, 0, 0),
                    Color.argb(176, 0, 5, 2),
                    Color.argb(195, 0, 4, 2),
                    Color.argb(187, 0, 3, 1),

                    Color.rgb(0, 232, 58),
                    Color.rgb(131, 255, 155),
                    Color.rgb(10, 122, 43),

                    Color.rgb(231, 255, 233),
                    Color.rgb(145, 220, 163),
                    Color.rgb(3, 22, 7),

                    Color.rgb(22, 112, 52),
                    Color.rgb(10, 33, 17),

                    Color.rgb(60, 255, 121),
                    Color.rgb(22, 124, 58),
                    Color.rgb(154, 255, 179)
            );
            return;
        }

        if (selectedTheme == THEME_NEON) {
            palette = new ThemePalette(
                    R.drawable.background_neon,

                    Color.rgb(8, 5, 21),
                    Color.argb(70, 0, 0, 0),
                    Color.argb(176, 5, 3, 16),
                    Color.argb(195, 4, 2, 14),
                    Color.argb(187, 3, 2, 11),

                    Color.rgb(222, 40, 157),
                    Color.rgb(255, 119, 210),
                    Color.rgb(118, 25, 101),

                    Color.rgb(248, 243, 255),
                    Color.rgb(196, 181, 220),
                    Color.rgb(21, 6, 23),

                    Color.rgb(74, 45, 114),
                    Color.rgb(25, 13, 50),

                    Color.rgb(40, 205, 255),
                    Color.rgb(25, 110, 157),
                    Color.rgb(125, 221, 255)
            );
            return;
        }

        palette = new ThemePalette(
                R.drawable.background_amber,

                Color.rgb(10, 9, 5),
                Color.argb(70, 0, 0, 0),
                Color.argb(176, 5, 4, 1),
                Color.argb(195, 4, 3, 1),
                Color.argb(187, 3, 3, 1),

                Color.rgb(255, 176, 0),
                Color.rgb(255, 214, 100),
                Color.rgb(173, 112, 0),

                Color.rgb(255, 249, 234),
                Color.rgb(218, 171, 82),
                Color.rgb(20, 15, 4),

                Color.rgb(106, 77, 21),
                Color.rgb(18, 15, 6),

                Color.rgb(255, 176, 0),
                Color.rgb(173, 112, 0),
                Color.rgb(255, 210, 92)
        );
    }

    private String getThemeName() {
        if (selectedTheme == THEME_MATRIX) {
            return "MATRIX";
        }

        if (selectedTheme == THEME_NEON) {
            return "NEON";
        }

        return "ÂMBAR";
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(palette.background);
        window.setNavigationBarColor(palette.background);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private void selectTheme(int theme) {
        if (theme < THEME_AMBER || theme > THEME_NEON) {
            return;
        }

        selectedTheme = theme;
        saveSelectedTheme();
        applySelectedTheme();
        configureSystemBars();

        if (performanceMode) {
            buildPerformanceScreen();
        } else {
            buildMainScreen();
        }
    }

    private void showEditorMode() {
        performanceMode = false;
        activePresetIndex = -1;
        buildMainScreen();
    }

    private void showPerformanceMode() {
        if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
            Toast.makeText(
                    this,
                    "Crie ou selecione uma música antes de entrar no modo Performance.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        performanceMode = true;
        activePresetIndex = -1;
        buildPerformanceScreen();
    }

    private FrameLayout createScreenWithBackground() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(palette.background);

        ImageView backgroundImage = new ImageView(this);
        backgroundImage.setImageResource(palette.backgroundImageResId);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setAlpha(1.0f);

        root.addView(
                backgroundImage,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        View imageOverlay = new View(this);
        imageOverlay.setBackgroundColor(palette.screenOverlay);

        root.addView(
                imageOverlay,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        return root;
    }

    private void buildMainScreen() {
        performanceMode = false;

        FrameLayout root = createScreenWithBackground();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(24));
        content.setBackgroundColor(Color.TRANSPARENT);

        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        addThemeSelector(content);
        addEditorHeader(content);
        addMidiSection(content);
        addPerformanceButton(content);
        addBankSection(content);
        addPresetSection(content);

        root.addView(
                scrollView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);
        configureBankSpinner();
    }

    private void buildPerformanceScreen() {
        FrameLayout root = createScreenWithBackground();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));
        content.setBackgroundColor(Color.TRANSPARENT);

        addThemeSelector(content);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(16));
        header.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.secondaryAccent,
                18
        ));

        TextView modeLabel = createAccentText(
                "PERFORMANCE MODE",
                11,
                true
        );
        modeLabel.setLetterSpacing(0.15f);

        Button btnSongSelector = createSongSelectorButton();
        btnSongSelector.setOnClickListener(
                view -> showPerformanceSongSelector(btnSongSelector)
        );

        TextView helper = createSecondaryText(
                "Toque em um pad para disparar o preset.",
                13,
                false
        );
        helper.setPadding(0, dp(8), 0, 0);

        header.addView(modeLabel);
        header.addView(
                btnSongSelector,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(54),
                        0,
                        dp(6),
                        0,
                        0
                )
        );
        header.addView(helper);

        content.addView(
                header,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        12
                )
        );

        LinearLayout midiMiniStatus = new LinearLayout(this);
        midiMiniStatus.setOrientation(LinearLayout.HORIZONTAL);
        midiMiniStatus.setGravity(Gravity.CENTER_VERTICAL);
        midiMiniStatus.setPadding(dp(12), dp(8), dp(12), dp(8));
        midiMiniStatus.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.secondaryAccentDark,
                14
        ));

        TextView midiDot = new TextView(this);
        midiDot.setText("●");
        midiDot.setTextSize(15);
        midiDot.setTextColor(palette.secondaryAccent);
        applySecondaryGlow(midiDot);

        TextView midiText = createSecondaryText(
                selectedMidiDeviceInfo == null
                        ? "MIDI: aguardando USB‑OTG"
                        : "MIDI: " + getMidiDeviceName(selectedMidiDeviceInfo),
                12,
                true
        );
        midiText.setPadding(dp(8), 0, 0, 0);

        midiMiniStatus.addView(midiDot);
        midiMiniStatus.addView(midiText);

        content.addView(
                midiMiniStatus,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        12
                )
        );

        ScrollView padsScrollView = new ScrollView(this);
        padsScrollView.setFillViewport(true);
        padsScrollView.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout padsContainer = new LinearLayout(this);
        padsContainer.setOrientation(LinearLayout.VERTICAL);
        padsContainer.setBackgroundColor(Color.TRANSPARENT);

        padsScrollView.addView(
                padsContainer,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        addPerformancePads(padsContainer);

        content.addView(
                padsScrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        Button btnExitPerformance = createOutlineButton(
                "← VOLTAR PARA EDIÇÃO"
        );
        btnExitPerformance.setTextSize(13);
        btnExitPerformance.setOnClickListener(view -> showEditorMode());

        content.addView(
                btnExitPerformance,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(50),
                        0,
                        12,
                        0,
                        0
                )
        );

        root.addView(
                content,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);
    }

    private void addThemeSelector(LinearLayout parent) {
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(Gravity.CENTER);
        themeRow.setPadding(0, 0, 0, dp(12));

        Button btnAmber = createThemeButton("ÂMBAR", THEME_AMBER);
        btnAmber.setOnClickListener(view -> selectTheme(THEME_AMBER));

        Button btnMatrix = createThemeButton("MATRIX", THEME_MATRIX);
        btnMatrix.setOnClickListener(view -> selectTheme(THEME_MATRIX));

        Button btnNeon = createThemeButton("NEON", THEME_NEON);
        btnNeon.setOnClickListener(view -> selectTheme(THEME_NEON));

        themeRow.addView(
                btnAmber,
                new LinearLayout.LayoutParams(0, dp(42), 1)
        );

        themeRow.addView(
                btnMatrix,
                createMarginLayoutParams(
                        0,
                        dp(42),
                        dp(7),
                        0,
                        dp(7),
                        0,
                        1
                )
        );

        themeRow.addView(
                btnNeon,
                new LinearLayout.LayoutParams(0, dp(42), 1)
        );

        parent.addView(
                themeRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private Button createThemeButton(String text, int themeId) {
        boolean isActive = selectedTheme == themeId;

        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setLetterSpacing(0.06f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);

        int accentColor = getThemeAccent(themeId);
        int accentBrightColor = getThemeAccentBright(themeId);
        int accentDarkColor = getThemeAccentDark(themeId);

        if (isActive) {
            button.setTextColor(getThemeTextDark(themeId));
            applyDarkTextShadow(button);

            button.setBackground(createRoundedBackground(
                    accentColor,
                    accentBrightColor,
                    18,
                    1
            ));
        } else {
            button.setTextColor(accentColor);
            applySpecificGlow(button, accentColor, 1.2f, 95);

            button.setBackground(createRoundedBackground(
                    palette.panelBackground,
                    accentDarkColor,
                    18,
                    1
            ));
        }

        return button;
    }

    private int getThemeAccent(int themeId) {
        if (themeId == THEME_MATRIX) {
            return Color.rgb(0, 232, 58);
        }

        if (themeId == THEME_NEON) {
            return Color.rgb(222, 40, 157);
        }

        return Color.rgb(255, 176, 0);
    }

    private int getThemeAccentBright(int themeId) {
        if (themeId == THEME_MATRIX) {
            return Color.rgb(131, 255, 155);
        }

        if (themeId == THEME_NEON) {
            return Color.rgb(255, 119, 210);
        }

        return Color.rgb(255, 214, 100);
    }

    private int getThemeAccentDark(int themeId) {
        if (themeId == THEME_MATRIX) {
            return Color.rgb(10, 122, 43);
        }

        if (themeId == THEME_NEON) {
            return Color.rgb(118, 25, 101);
        }

        return Color.rgb(173, 112, 0);
    }

    private int getThemeTextDark(int themeId) {
        if (themeId == THEME_MATRIX) {
            return Color.rgb(3, 22, 7);
        }

        if (themeId == THEME_NEON) {
            return Color.rgb(21, 6, 23);
        }

        return Color.rgb(20, 15, 4);
    }

    private void addEditorHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.border,
                18
        ));

        TextView title = createPrimaryText("JUNO‑D6", 28, true);
        title.setLetterSpacing(0.08f);

        TextView subtitle = createAccentText(
                "SCENE PART CONTROLLER",
                11,
                true
        );
        subtitle.setLetterSpacing(0.14f);
        subtitle.setPadding(0, dp(4), 0, dp(10));

        TextView description = createSecondaryText(
                "Bancos, presets e controle de Parts para performance ao vivo.",
                13,
                false
        );

        header.addView(title);
        header.addView(subtitle);
        header.addView(description);

        parent.addView(
                header,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        16
                )
        );
    }

    private void addMidiSection(LinearLayout parent) {
        TextView sectionTitle = createSectionTitle("CONEXÃO MIDI");
        parent.addView(sectionTitle);

        LinearLayout midiCard = new LinearLayout(this);
        midiCard.setOrientation(LinearLayout.VERTICAL);
        midiCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        midiCard.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.border,
                14
        ));

        LinearLayout statusHeader = new LinearLayout(this);
        statusHeader.setOrientation(LinearLayout.HORIZONTAL);
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);

        tvMidiStatusDot = new TextView(this);
        tvMidiStatusDot.setText("●");
        tvMidiStatusDot.setTextSize(18);
        tvMidiStatusDot.setTextColor(palette.secondaryAccent);
        applySecondaryGlow(tvMidiStatusDot);

        TextView statusLabel = createSecondaryText(
                "STATUS USB‑MIDI",
                11,
                true
        );
        statusLabel.setLetterSpacing(0.08f);
        statusLabel.setPadding(dp(8), 0, 0, 0);

        statusHeader.addView(tvMidiStatusDot);
        statusHeader.addView(statusLabel);
        midiCard.addView(statusHeader);

        tvMidiStatus = createPrimaryText(
                "Verificando dispositivos MIDI...",
                14,
                false
        );
        tvMidiStatus.setPadding(0, dp(8), 0, dp(12));
        midiCard.addView(tvMidiStatus);

        Button btnRefreshMidi = createOutlineButton(
                "ATUALIZAR DISPOSITIVOS MIDI"
        );
        btnRefreshMidi.setTextSize(12);
        btnRefreshMidi.setOnClickListener(view -> refreshMidiDevices());

        midiCard.addView(
                btnRefreshMidi,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(46)
                )
        );

        parent.addView(
                midiCard,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        10
                )
        );
    }

    private void addPerformanceButton(LinearLayout parent) {
        Button btnPerformance = createPrimaryActionButton(
                "▶ MODO PERFORMANCE"
        );
        btnPerformance.setTextSize(13);
        btnPerformance.setOnClickListener(view -> showPerformanceMode());

        parent.addView(
                btnPerformance,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52),
                        0,
                        0,
                        0,
                        18
                )
        );
    }

    private void addBankSection(LinearLayout parent) {
        TextView sectionTitle = createSectionTitle("MÚSICAS");
        parent.addView(sectionTitle);

        LinearLayout bankCard = new LinearLayout(this);
        bankCard.setOrientation(LinearLayout.VERTICAL);
        bankCard.setPadding(dp(12), dp(10), dp(12), dp(12));
        bankCard.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.border,
                14
        ));

        spinnerBanks = new Spinner(this);
        spinnerBanks.setPadding(dp(8), 0, dp(8), 0);
        spinnerBanks.setBackground(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                12,
                1
        ));

        bankCard.addView(
                spinnerBanks,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(50)
                )
        );

        LinearLayout bankButtonsRow = new LinearLayout(this);
        bankButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        bankButtonsRow.setPadding(0, dp(10), 0, 0);

        Button btnNewBank = createPrimaryActionButton(
                "+ NOVA MÚSICA"
        );
        btnNewBank.setOnClickListener(view -> showCreateBankDialog());

        btnDeleteBank = createOutlineButton("EXCLUIR");
        btnDeleteBank.setOnClickListener(view -> showDeleteBankDialog());

        bankButtonsRow.addView(
                btnNewBank,
                new LinearLayout.LayoutParams(0, dp(46), 1)
        );

        bankButtonsRow.addView(
                btnDeleteBank,
                createMarginLayoutParams(
                        0,
                        dp(46),
                        dp(8),
                        0,
                        0,
                        0,
                        1
                )
        );

        bankCard.addView(bankButtonsRow);

        parent.addView(
                bankCard,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        18
                )
        );
    }

    private void addPresetSection(LinearLayout parent) {
        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView sectionTitle = createSectionTitle("PRESETS");
        sectionHeader.addView(
                sectionTitle,
                new LinearLayout.LayoutParams(0, dp(28), 1)
        );

        TextView helper = createSecondaryText(
                "toque no preset para editar",
                11,
                false
        );
        helper.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        sectionHeader.addView(
                helper,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(28)
                )
        );

        parent.addView(sectionHeader);

        btnNewPreset = createPrimaryActionButton(
                "+ NOVO PRESET"
        );
        btnNewPreset.setTextSize(13);
        btnNewPreset.setOnClickListener(view -> {
            if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
                Toast.makeText(
                        this,
                        "Crie ou selecione uma música primeiro.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            showPresetEditor(null, -1);
        });

        parent.addView(
                btnNewPreset,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52),
                        0,
                        0,
                        0,
                        10
                )
        );

        tvEmptyPresets = createSecondaryText(
                "Nenhum preset nesta música.",
                15,
                false
        );
        tvEmptyPresets.setGravity(Gravity.CENTER);
        tvEmptyPresets.setPadding(dp(16), dp(24), dp(16), dp(24));
        tvEmptyPresets.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.border,
                14
        ));

        parent.addView(
                tvEmptyPresets,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        8
                )
        );

        layoutPresets = new LinearLayout(this);
        layoutPresets.setOrientation(LinearLayout.VERTICAL);

        parent.addView(
                layoutPresets,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private Button createSongSelectorButton() {
        Button button = new Button(this);

        button.setText(getSelectedBankName().toUpperCase() + "  ▾");
        button.setAllCaps(false);
        button.setTextSize(22);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(palette.textPrimary);
        applyPrimaryGlow(button);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(14), 0);

        button.setBackground(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                16,
                1
        ));

        return button;
    }

    private void showPerformanceSongSelector(View anchor) {
        if (banks.isEmpty()) {
            Toast.makeText(
                    this,
                    "Nenhuma música disponível.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        LinearLayout popupContent = new LinearLayout(this);
        popupContent.setOrientation(LinearLayout.VERTICAL);
        popupContent.setPadding(dp(6), dp(6), dp(6), dp(6));
        popupContent.setBackground(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                16,
                1
        ));

        final PopupWindow popupWindow = new PopupWindow(
                popupContent,
                anchor.getWidth(),
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setBackgroundDrawable(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                16,
                1
        ));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dp(10));

        for (int i = 0; i < banks.size(); i++) {
            final int bankIndex = i;
            Bank bank = banks.get(i);

            Button songButton = new Button(this);
            songButton.setText(bank.name.toUpperCase());
            songButton.setAllCaps(false);
            songButton.setTextSize(16);
            songButton.setTypeface(Typeface.DEFAULT_BOLD);

            boolean isSelected = bankIndex == selectedBankIndex;

            songButton.setTextColor(
                    isSelected
                            ? palette.textDark
                            : palette.textPrimary
            );

            if (isSelected) {
                applyDarkTextShadow(songButton);
            } else {
                applyPrimaryGlow(songButton);
            }

            songButton.setGravity(Gravity.CENTER_VERTICAL);
            songButton.setPadding(dp(14), 0, dp(14), 0);

            songButton.setBackground(
                    isSelected
                            ? createRoundedBackground(
                                    palette.secondaryAccent,
                                    palette.secondaryAccent,
                                    14,
                                    1
                            )
                            : createRoundedBackground(
                                    palette.panelSoft,
                                    Color.TRANSPARENT,
                                    14,
                                    0
                            )
            );

            songButton.setOnClickListener(view -> {
                if (bankIndex != selectedBankIndex) {
                    selectedBankIndex = bankIndex;
                    activePresetIndex = -1;
                }

                popupWindow.dismiss();
                buildPerformanceScreen();
            });

            popupContent.addView(
                    songButton,
                    createMarginLayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(52),
                            0,
                            0,
                            0,
                            dp(4)
                    )
            );
        }

        popupWindow.showAsDropDown(anchor, 0, dp(4));
    }

    private void addPerformancePads(LinearLayout parent) {
        if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
            addPerformanceEmptyMessage(parent, "Nenhuma música selecionada.");
            return;
        }

        Bank bank = banks.get(selectedBankIndex);

        if (bank.presets.isEmpty()) {
            addPerformanceEmptyMessage(
                    parent,
                    "Esta música ainda não tem presets.\nVolte para edição e crie-os."
            );
            return;
        }

        int presetLimit = Math.min(bank.presets.size(), 8);

        for (int row = 0; row < presetLimit; row += 2) {
            LinearLayout padRow = new LinearLayout(this);
            padRow.setOrientation(LinearLayout.HORIZONTAL);

            Preset firstPreset = bank.presets.get(row);

            padRow.addView(
                    createPerformancePad(firstPreset, row),
                    new LinearLayout.LayoutParams(0, dp(158), 1)
            );

            if (row + 1 < presetLimit) {
                Preset secondPreset = bank.presets.get(row + 1);

                padRow.addView(
                        createPerformancePad(secondPreset, row + 1),
                        createMarginLayoutParams(
                                0,
                                dp(158),
                                dp(10),
                                0,
                                0,
                                0,
                                1
                        )
                );
            } else {
                TextView spacer = new TextView(this);

                padRow.addView(
                        spacer,
                        createMarginLayoutParams(
                                0,
                                dp(158),
                                dp(10),
                                0,
                                0,
                                0,
                                1
                        )
                );
            }

            parent.addView(
                    padRow,
                    createMarginLayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(158),
                            0,
                            0,
                            0,
                            10
                    )
            );
        }

        if (bank.presets.size() > 8) {
            TextView limitWarning = createAccentText(
                    "Há " + bank.presets.size()
                            + " presets nesta música. A tela mostra os primeiros 8.",
                    12,
                    false
            );

            limitWarning.setGravity(Gravity.CENTER);
            limitWarning.setPadding(dp(10), dp(8), dp(10), dp(8));

            parent.addView(limitWarning);
        }
    }

    private void addPerformanceEmptyMessage(
            LinearLayout parent,
            String message
    ) {
        TextView empty = createSecondaryText(message, 16, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(30), dp(18), dp(30));
        empty.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.border,
                16
        ));

        parent.addView(
                empty,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private LinearLayout createPerformancePad(
            Preset preset,
            int presetIndex
    ) {
        boolean isActive = presetIndex == activePresetIndex;

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER);
        pad.setPadding(dp(10), dp(12), dp(10), dp(10));
        pad.setClickable(true);

        int fillColor = isActive
                ? palette.accent
                : palette.padBackground;

        int borderColor = isActive
                ? palette.accentBright
                : palette.secondaryAccentDark;

        int borderWidth = isActive ? 2 : 1;

        pad.setBackground(createRoundedBackground(
                fillColor,
                borderColor,
                20,
                borderWidth
        ));

        int mainTextColor = isActive
                ? palette.textDark
                : palette.textPrimary;

        int secondaryTextColor = isActive
                ? palette.activeSecondaryText
                : palette.textSecondary;

        TextView padNumber = createAccentText(
                String.format("%02d", presetIndex + 1),
                11,
                true
        );
        padNumber.setTextColor(secondaryTextColor);

        if (isActive) {
            applyDarkTextShadow(padNumber);
        } else {
            applyAccentGlow(padNumber);
        }

        padNumber.setGravity(Gravity.CENTER);

        TextView presetName = createPrimaryText(
                preset.name.toUpperCase(),
                18,
                true
        );
        presetName.setTextColor(mainTextColor);

        if (isActive) {
            applyDarkTextShadow(presetName);
        } else {
            applyPrimaryGlow(presetName);
        }

        presetName.setGravity(Gravity.CENTER);
        presetName.setMaxLines(2);
        presetName.setPadding(0, dp(7), 0, dp(5));

        TextView scene = createSecondaryAccentText(
                "SCENE " + preset.scene,
                11,
                true
        );

        scene.setTextColor(
                isActive ? palette.textDark : palette.secondaryAccent
        );

        if (isActive) {
            applyDarkTextShadow(scene);
        } else {
            applySecondaryGlow(scene);
        }

        scene.setLetterSpacing(0.05f);
        scene.setGravity(Gravity.CENTER);

        TextView parts = createSecondaryText(
                preset.getShortActivePartsSummary(),
                11,
                false
        );
        parts.setTextColor(secondaryTextColor);

        if (isActive) {
            applyDarkTextShadow(parts);
        } else {
            applyAccentGlow(parts);
        }

        parts.setGravity(Gravity.CENTER);
        parts.setMaxLines(2);
        parts.setPadding(0, dp(8), 0, 0);

        TextView activeLabel = createSecondaryText(
                isActive ? "● ATIVO" : "TOQUE PARA DISPARAR",
                10,
                true
        );
        activeLabel.setLetterSpacing(0.05f);
        activeLabel.setTextColor(secondaryTextColor);

        if (isActive) {
            applyDarkTextShadow(activeLabel);
        } else {
            applyAccentGlow(activeLabel);
        }

        activeLabel.setGravity(Gravity.CENTER);
        activeLabel.setPadding(0, dp(10), 0, 0);

        pad.addView(padNumber);
        pad.addView(presetName);
        pad.addView(scene);
        pad.addView(parts);
        pad.addView(activeLabel);

        pad.setOnClickListener(view -> {
            activePresetIndex = presetIndex;

            if (selectedBankIndex >= 0 && selectedBankIndex < banks.size()) {
                applyPreset(banks.get(selectedBankIndex), preset);
            }

            buildPerformanceScreen();
        });

        return pad;
    }

    private void refreshMidiDevices() {
        selectedMidiDeviceInfo = null;

        if (midiManager == null) {
            if (tvMidiStatusDot != null) {
                tvMidiStatusDot.setTextColor(palette.secondaryAccent);
                applySecondaryGlow(tvMidiStatusDot);
            }

            if (tvMidiStatus != null) {
                tvMidiStatus.setTextColor(palette.accent);
                applyAccentGlow(tvMidiStatus);
                tvMidiStatus.setText(
                        "MIDI não está disponível neste aparelho Android."
                );
            }

            return;
        }

        MidiDeviceInfo[] devices = midiManager.getDevices();

        if (devices == null || devices.length == 0) {
            if (tvMidiStatusDot != null) {
                tvMidiStatusDot.setTextColor(palette.secondaryAccent);
                applySecondaryGlow(tvMidiStatusDot);
            }

            if (tvMidiStatus != null) {
                tvMidiStatus.setTextColor(palette.textSecondary);
                applyAccentGlow(tvMidiStatus);
                tvMidiStatus.setText(
                        "Nenhum dispositivo MIDI USB detectado.\n"
                                + "Conecte o Juno pelo adaptador USB‑OTG quando ele chegar."
                );
            }

            return;
        }

        StringBuilder status = new StringBuilder();
        MidiDeviceInfo preferredDevice = null;

        for (int i = 0; i < devices.length; i++) {
            MidiDeviceInfo deviceInfo = devices[i];

            String deviceName = getMidiDeviceName(deviceInfo);
            int outputPortCount = deviceInfo.getOutputPortCount();
            int inputPortCount = deviceInfo.getInputPortCount();

            status.append(deviceName)
                    .append("\n")
                    .append("Entradas: ")
                    .append(inputPortCount)
                    .append("  •  Saídas: ")
                    .append(outputPortCount);

            if (i < devices.length - 1) {
                status.append("\n\n");
            }

            if (preferredDevice == null && outputPortCount > 0) {
                preferredDevice = deviceInfo;
            }
        }

        selectedMidiDeviceInfo = preferredDevice;

        if (tvMidiStatusDot != null) {
            tvMidiStatusDot.setTextColor(palette.secondaryAccent);
            applySecondaryGlow(tvMidiStatusDot);
        }

        if (tvMidiStatus != null) {
            if (selectedMidiDeviceInfo != null) {
                tvMidiStatus.setTextColor(palette.textPrimary);
                applyPrimaryGlow(tvMidiStatus);
                status.append("\n\nDISPOSITIVO MIDI DETECTADO.");
            } else {
                tvMidiStatus.setTextColor(palette.accent);
                applyAccentGlow(tvMidiStatus);
                status.append("\n\nDispositivo sem porta MIDI utilizável.");
            }

            tvMidiStatus.setText(status.toString());
        }
    }

    private String getMidiDeviceName(MidiDeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return "Dispositivo desconhecido";
        }

        String name = deviceInfo.getProperties().getString(
                MidiDeviceInfo.PROPERTY_NAME
        );

        if (name != null && !name.trim().isEmpty()) {
            return name;
        }

        String manufacturer = deviceInfo.getProperties().getString(
                MidiDeviceInfo.PROPERTY_MANUFACTURER
        );

        String product = deviceInfo.getProperties().getString(
                MidiDeviceInfo.PROPERTY_PRODUCT
        );

        StringBuilder nameBuilder = new StringBuilder();

        if (manufacturer != null && !manufacturer.trim().isEmpty()) {
            nameBuilder.append(manufacturer.trim());
        }

        if (product != null && !product.trim().isEmpty()) {
            if (nameBuilder.length() > 0) {
                nameBuilder.append(" ");
            }

            nameBuilder.append(product.trim());
        }

        if (nameBuilder.length() > 0) {
            return nameBuilder.toString();
        }

        return "Dispositivo MIDI #" + deviceInfo.getId();
    }

    private String getSelectedBankName() {
        if (selectedBankIndex >= 0 && selectedBankIndex < banks.size()) {
            return banks.get(selectedBankIndex).name;
        }

        return "SEM MÚSICA";
    }

    private ArrayAdapter<String> createThemeBankAdapter(List<String> bankNames) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                bankNames
        ) {
            @Override
            public View getView(
                    int position,
                    View convertView,
                    android.view.ViewGroup parent
            ) {
                TextView view = (TextView) super.getView(
                        position,
                        convertView,
                        parent
                );

                view.setTextColor(palette.textPrimary);
                view.setTextSize(16);
                view.setPadding(dp(12), 0, dp(12), 0);
                applyPrimaryGlow(view);

                return view;
            }

            @Override
            public View getDropDownView(
                    int position,
                    View convertView,
                    android.view.ViewGroup parent
            ) {
                TextView view = (TextView) super.getDropDownView(
                        position,
                        convertView,
                        parent
                );

                view.setTextColor(palette.textPrimary);
                view.setTextSize(16);
                view.setPadding(dp(16), dp(12), dp(16), dp(12));
                view.setBackgroundColor(palette.panelStrong);
                applyPrimaryGlow(view);

                return view;
            }
        };

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        return adapter;
    }

    private void configureBankSpinner() {
        if (spinnerBanks == null) {
            return;
        }

        List<String> bankNames = new ArrayList<>();

        for (Bank bank : banks) {
            bankNames.add(bank.name);
        }

        if (bankNames.isEmpty()) {
            bankNames.add("Nenhuma música criada");
        }

        spinnerBanks.setAdapter(createThemeBankAdapter(bankNames));

        spinnerBanks.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        selectedBankIndex = banks.isEmpty() ? -1 : position;
                        refreshPresetList();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        selectedBankIndex = -1;
                        refreshPresetList();
                    }
                }
        );

        if (banks.isEmpty()) {
            selectedBankIndex = -1;
            spinnerBanks.setEnabled(false);
        } else {
            spinnerBanks.setEnabled(true);

            if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
                selectedBankIndex = 0;
            }

            spinnerBanks.setSelection(selectedBankIndex);
        }

        refreshPresetList();
    }

    private void refreshPresetList() {
        if (layoutPresets == null
                || tvEmptyPresets == null
                || btnNewPreset == null
                || btnDeleteBank == null) {
            return;
        }

        layoutPresets.removeAllViews();

        boolean hasSelectedBank =
                selectedBankIndex >= 0 && selectedBankIndex < banks.size();

        btnNewPreset.setEnabled(hasSelectedBank);
        btnDeleteBank.setEnabled(hasSelectedBank);

        updatePrimaryButtonEnabledStyle(btnNewPreset, hasSelectedBank);
        updateOutlineButtonEnabledStyle(btnDeleteBank, hasSelectedBank);

        if (!hasSelectedBank) {
            tvEmptyPresets.setText(
                    "Crie uma música para começar.\n"
                            + "Exemplo: nome da música ou setlist."
            );
            tvEmptyPresets.setVisibility(View.VISIBLE);
            return;
        }

        Bank bank = banks.get(selectedBankIndex);

        if (bank.presets.isEmpty()) {
            tvEmptyPresets.setText(
                    "Nenhum preset nesta música.\n"
                            + "Toque em “NOVO PRESET” para criar o primeiro."
            );
            tvEmptyPresets.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyPresets.setVisibility(View.GONE);

        for (int i = 0; i < bank.presets.size(); i++) {
            addPresetCard(bank, bank.presets.get(i), i);
        }
    }

    private void addPresetCard(
            Bank bank,
            Preset preset,
            int presetIndex
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.setBackground(createPanelBackground(
                palette.panelBackground,
                palette.border,
                18
        ));
        card.setClickable(true);

        card.setOnClickListener(
                view -> showPresetEditor(preset, presetIndex)
        );

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout indicator = new LinearLayout(this);

        indicator.setBackground(createRoundedBackground(
                preset.hasActivePart()
                        ? palette.accent
                        : palette.partOff,
                Color.TRANSPARENT,
                99,
                0
        ));

        topRow.addView(
                indicator,
                new LinearLayout.LayoutParams(dp(10), dp(10))
        );

        TextView presetName = createPrimaryText(
                preset.name,
                19,
                true
        );
        presetName.setPadding(dp(10), 0, 0, 0);

        topRow.addView(
                presetName,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        TextView sceneBadge = createSecondaryAccentText(
                "SCENE " + preset.scene,
                10,
                true
        );

        sceneBadge.setLetterSpacing(0.05f);
        sceneBadge.setGravity(Gravity.CENTER);
        sceneBadge.setPadding(dp(9), dp(6), dp(9), dp(6));

        sceneBadge.setBackground(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                99,
                1
        ));

        topRow.addView(sceneBadge);
        card.addView(topRow);

        TextView partsText = createSecondaryText(
                preset.getActivePartsSummary(),
                13,
                false
        );
        partsText.setPadding(dp(20), dp(8), 0, dp(12));
        card.addView(partsText);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnApply = createPrimaryActionButton("APLICAR");
        btnApply.setTextSize(12);
        btnApply.setOnClickListener(view -> applyPreset(bank, preset));

        Button btnDelete = createOutlineButton("×");
        btnDelete.setTextSize(22);
        btnDelete.setOnClickListener(
                view -> showDeletePresetDialog(preset, presetIndex)
        );

        actionRow.addView(
                btnApply,
                new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        actionRow.addView(
                btnDelete,
                createMarginLayoutParams(
                        dp(52),
                        dp(44),
                        dp(8),
                        0,
                        0,
                        0
                )
        );

        card.addView(actionRow);

        layoutPresets.addView(
                card,
                createMarginLayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0,
                        0,
                        0,
                        10
                )
        );
    }

    private void showCreateBankDialog() {
        EditText inputName = createThemeEditText(
                "Ex.: Enjoy the Silence"
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("NOVA MÚSICA")
                .setMessage("Digite o nome da música, setlist ou cena principal.")
                .setView(inputName)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("CRIAR", null)
                .create();

        dialog.setOnShowListener(listener -> {
            styleDialog(dialog);

            Button createButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            if (createButton != null) {
                createButton.setOnClickListener(view -> {
                    String name = inputName.getText().toString().trim();

                    if (name.isEmpty()) {
                        inputName.setError("Digite um nome.");
                        inputName.requestFocus();
                        return;
                    }

                    Bank newBank = new Bank(name);
                    banks.add(newBank);
                    saveBanks();

                    selectedBankIndex = banks.size() - 1;
                    configureBankSpinner();
                    spinnerBanks.setSelection(selectedBankIndex);

                    Toast.makeText(
                            this,
                            "Música criada: " + name,
                            Toast.LENGTH_SHORT
                    ).show();

                    dialog.dismiss();
                });
            }
        });

        dialog.show();
    }

    private void showDeleteBankDialog() {
        if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
            return;
        }

        Bank bank = banks.get(selectedBankIndex);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("EXCLUIR MÚSICA?")
                .setMessage(
                        "“" + bank.name + "” e todos os presets dela serão apagados."
                )
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("EXCLUIR", (d, which) -> {
                    banks.remove(selectedBankIndex);
                    saveBanks();

                    selectedBankIndex = -1;
                    configureBankSpinner();

                    Toast.makeText(
                            this,
                            "Música excluída.",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .create();

        dialog.setOnShowListener(listener -> {
            styleDialog(dialog);

            Button positiveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            if (positiveButton != null) {
                positiveButton.setTextColor(palette.accent);
            }
        });

        dialog.show();
    }

    private void showDeletePresetDialog(Preset preset, int presetIndex) {
        if (selectedBankIndex < 0 || selectedBankIndex >= banks.size()) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("EXCLUIR PRESET?")
                .setMessage("“" + preset.name + "” será apagado.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("EXCLUIR", (d, which) -> {
                    Bank bank = banks.get(selectedBankIndex);

                    if (presetIndex >= 0 && presetIndex < bank.presets.size()) {
                        bank.presets.remove(presetIndex);
                        saveBanks();
                        refreshPresetList();

                        Toast.makeText(
                                this,
                                "Preset excluído.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .create();

        dialog.setOnShowListener(listener -> {
            styleDialog(dialog);

            Button positiveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            if (positiveButton != null) {
                positiveButton.setTextColor(palette.accent);
            }
        });

        dialog.show();
    }

    private void showPresetEditor(
            Preset presetToEdit,
            int presetIndex
    ) {
        boolean isEditing = presetToEdit != null;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(palette.panelStrong);

        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(18), dp(8), dp(18), dp(14));
        editor.setBackgroundColor(palette.panelStrong);

        scrollView.addView(editor);

        TextView nameLabel = createDialogLabel("NOME DO PRESET");
        editor.addView(nameLabel);

        EditText inputName = createThemeEditText(
                "Ex.: Verso, Refrão, Solo"
        );
        inputName.setInputType(InputType.TYPE_CLASS_TEXT);
        inputName.setText(isEditing ? presetToEdit.name : "");

        editor.addView(
                inputName,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView sceneLabel = createDialogLabel("NÚMERO DA SCENE");
        sceneLabel.setPadding(0, dp(16), 0, dp(6));
        editor.addView(sceneLabel);

        EditText inputScene = createThemeEditText("Ex.: 1");
        inputScene.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputScene.setText(
                isEditing ? String.valueOf(presetToEdit.scene) : "1"
        );

        editor.addView(
                inputScene,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView partsLabel = createDialogLabel(
                "PARTS ATIVAS NESTE PRESET"
        );
        partsLabel.setPadding(0, dp(20), 0, dp(4));
        editor.addView(partsLabel);

        TextView helpLabel = createSecondaryText(
                "Destaque = ON  •  Escuro = OFF",
                13,
                false
        );
        helpLabel.setPadding(0, 0, 0, dp(10));
        editor.addView(helpLabel);

        boolean[] partStates = new boolean[8];

        if (isEditing) {
            for (int i = 0; i < 8; i++) {
                partStates[i] = presetToEdit.partStates[i];
            }
        } else {
            partStates[0] = true;
        }

        LinearLayout partsGrid = new LinearLayout(this);
        partsGrid.setOrientation(LinearLayout.VERTICAL);

        for (int row = 0; row < 4; row++) {
            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);

            for (int col = 0; col < 2; col++) {
                int partIndex = (row * 2) + col;

                Button partButton = createPartButton(
                        partIndex,
                        partStates[partIndex]
                );

                final int currentPartIndex = partIndex;

                partButton.setOnClickListener(view -> {
                    partStates[currentPartIndex] =
                            !partStates[currentPartIndex];

                    updatePartButton(
                            partButton,
                            currentPartIndex,
                            partStates[currentPartIndex]
                    );
                });

                LinearLayout.LayoutParams partParams =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(56),
                                1
                        );

                if (col == 1) {
                    partParams.setMargins(dp(8), 0, 0, 0);
                }

                buttonRow.addView(partButton, partParams);
            }

            LinearLayout.LayoutParams buttonRowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(56)
                    );

            buttonRowParams.setMargins(0, dp(4), 0, dp(4));
            partsGrid.addView(buttonRow, buttonRowParams);
        }

        editor.addView(partsGrid);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEditing ? "EDITAR PRESET" : "NOVO PRESET")
                .setView(scrollView)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("SALVAR", null)
                .create();

        dialog.setOnShowListener(listener -> {
            styleDialog(dialog);

            Button positiveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            if (positiveButton != null) {
                positiveButton.setTextColor(palette.accent);

                positiveButton.setOnClickListener(view -> {
                    String name = inputName.getText().toString().trim();

                    if (name.isEmpty()) {
                        inputName.setError("Digite um nome.");
                        inputName.requestFocus();
                        return;
                    }

                    int scene = parseSceneNumber(
                            inputScene.getText().toString()
                    );

                    if (scene < 1) {
                        inputScene.setError(
                                "Digite uma Scene válida (1 ou maior)."
                        );
                        inputScene.requestFocus();
                        return;
                    }

                    if (selectedBankIndex < 0
                            || selectedBankIndex >= banks.size()) {
                        Toast.makeText(
                                this,
                                "Nenhuma música selecionada.",
                                Toast.LENGTH_SHORT
                        ).show();

                        dialog.dismiss();
                        return;
                    }

                    Bank selectedBank = banks.get(selectedBankIndex);

                    if (isEditing) {
                        presetToEdit.name = name;
                        presetToEdit.scene = scene;

                        for (int i = 0; i < 8; i++) {
                            presetToEdit.partStates[i] = partStates[i];
                        }

                        Toast.makeText(
                                this,
                                "Preset atualizado.",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        boolean[] statesToSave = new boolean[8];

                        for (int i = 0; i < 8; i++) {
                            statesToSave[i] = partStates[i];
                        }

                        selectedBank.presets.add(
                                new Preset(
                                        name,
                                        scene,
                                        statesToSave
                                )
                        );

                        Toast.makeText(
                                this,
                                "Preset criado.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    saveBanks();
                    refreshPresetList();
                    dialog.dismiss();
                });
            }
        });

        dialog.show();
    }

    private void updatePartButton(
            Button button,
            int partIndex,
            boolean enabled
    ) {
        button.setText(
                "PART " + (partIndex + 1)
                        + "\n"
                        + (enabled ? "ON" : "OFF")
        );

        if (enabled) {
            button.setTextColor(palette.textDark);
            applyDarkTextShadow(button);

            button.setBackground(createRoundedBackground(
                    palette.accent,
                    palette.accentBright,
                    18,
                    1
            ));
        } else {
            button.setTextColor(palette.secondaryAccent);
            applySecondaryGlow(button);

            button.setBackground(createRoundedBackground(
                    palette.partOff,
                    palette.secondaryAccentDark,
                    18,
                    1
            ));
        }
    }

    private Button createPartButton(int partIndex, boolean enabled) {
        Button button = new Button(this);

        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);

        updatePartButton(button, partIndex, enabled);

        return button;
    }

    private int parseSceneNumber(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void applyPreset(Bank bank, Preset preset) {
        String midiStatus;

        if (selectedMidiDeviceInfo == null) {
            midiStatus = "MIDI: aguardando conexão USB‑OTG.";
        } else {
            midiStatus =
                    "MIDI detectado: "
                            + getMidiDeviceName(selectedMidiDeviceInfo)
                            + "\nEnvio MIDI será ativado após validar o "
                            + "mapeamento do Juno.";
        }

        Toast.makeText(
                this,
                "PRESET SELECIONADO\n"
                        + preset.name.toUpperCase()
                        + "\n\nMúsica: " + bank.name
                        + "\nScene: " + preset.scene
                        + "\n" + preset.getActivePartsSummary()
                        + "\n\n" + midiStatus,
                Toast.LENGTH_LONG
        ).show();
    }

    private void saveBanks() {
        JSONArray banksArray = new JSONArray();

        try {
            for (Bank bank : banks) {
                JSONObject bankObject = new JSONObject();
                bankObject.put("name", bank.name);

                JSONArray presetsArray = new JSONArray();

                for (Preset preset : bank.presets) {
                    JSONObject presetObject = new JSONObject();

                    presetObject.put("name", preset.name);
                    presetObject.put("scene", preset.scene);

                    JSONArray statesArray = new JSONArray();

                    for (boolean partState : preset.partStates) {
                        statesArray.put(partState);
                    }

                    presetObject.put("partStates", statesArray);
                    presetsArray.put(presetObject);
                }

                bankObject.put("presets", presetsArray);
                banksArray.put(bankObject);
            }
        } catch (JSONException exception) {
            Toast.makeText(
                    this,
                    "Erro ao preparar os dados para salvar.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        SharedPreferences preferences = getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        preferences.edit()
                .putString(KEY_BANKS_JSON, banksArray.toString())
                .apply();
    }

    private void loadBanks() {
        banks.clear();

        SharedPreferences preferences = getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        String savedJson = preferences.getString(KEY_BANKS_JSON, null);

        if (savedJson == null || savedJson.trim().isEmpty()) {
            createInitialExampleData();
            saveBanks();
            return;
        }

        try {
            JSONArray banksArray = new JSONArray(savedJson);

            for (
                    int bankIndex = 0;
                    bankIndex < banksArray.length();
                    bankIndex++
            ) {
                JSONObject bankObject = banksArray.getJSONObject(bankIndex);

                String bankName = bankObject.optString(
                        "name",
                        "Música sem nome"
                );

                Bank bank = new Bank(bankName);

                JSONArray presetsArray = bankObject.optJSONArray("presets");

                if (presetsArray != null) {
                    for (
                            int presetIndex = 0;
                            presetIndex < presetsArray.length();
                            presetIndex++
                    ) {
                        JSONObject presetObject =
                                presetsArray.getJSONObject(presetIndex);

                        String presetName = presetObject.optString(
                                "name",
                                "Preset sem nome"
                        );

                        int scene = presetObject.optInt("scene", 1);

                        boolean[] partStates = new boolean[8];

                        JSONArray statesArray =
                                presetObject.optJSONArray("partStates");

                        if (statesArray != null) {
                            for (
                                    int partIndex = 0;
                                    partIndex < 8
                                            && partIndex < statesArray.length();
                                    partIndex++
                            ) {
                                partStates[partIndex] =
                                        statesArray.optBoolean(
                                                partIndex,
                                                false
                                        );
                            }
                        }

                        bank.presets.add(
                                new Preset(
                                        presetName,
                                        scene,
                                        partStates
                                )
                        );
                    }
                }

                banks.add(bank);
            }
        } catch (JSONException exception) {
            banks.clear();
            createInitialExampleData();
            saveBanks();

            Toast.makeText(
                    this,
                    "Os dados salvos estavam inválidos. "
                            + "Exemplos foram restaurados.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void createInitialExampleData() {
        Bank musicaA = new Bank("Música A");

        musicaA.presets.add(
                new Preset(
                        "Verso",
                        1,
                        new boolean[]{
                                true, false, true, false,
                                false, false, false, false
                        }
                )
        );

        musicaA.presets.add(
                new Preset(
                        "Refrão",
                        1,
                        new boolean[]{
                                true, true, true, false,
                                false, false, false, false
                        }
                )
        );

        Bank musicaB = new Bank("Música B");

        musicaB.presets.add(
                new Preset(
                        "Intro",
                        2,
                        new boolean[]{
                                false, true, false, true,
                                false, false, false, false
                        }
                )
        );

        musicaB.presets.add(
                new Preset(
                        "Ponte",
                        2,
                        new boolean[]{
                                true, true, false, false,
                                true, false, false, false
                        }
                )
        );

        banks.add(musicaA);
        banks.add(musicaB);
    }

    private TextView createSectionTitle(String text) {
        TextView title = createSecondaryText(text, 12, true);

        title.setLetterSpacing(0.10f);
        title.setPadding(dp(4), 0, dp(4), dp(8));

        return title;
    }

    private TextView createDialogLabel(String text) {
        TextView label = createSecondaryText(text, 11, true);

        label.setLetterSpacing(0.08f);
        label.setPadding(0, dp(4), 0, dp(6));

        return label;
    }

    private TextView createPrimaryText(
            String text,
            float textSize,
            boolean bold
    ) {
        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(palette.textPrimary);

        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }

        applyPrimaryGlow(textView);

        return textView;
    }

    private TextView createSecondaryText(
            String text,
            float textSize,
            boolean bold
    ) {
        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(palette.textSecondary);

        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }

        applyAccentGlow(textView);

        return textView;
    }

    private TextView createAccentText(
            String text,
            float textSize,
            boolean bold
    ) {
        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(palette.accent);

        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }

        applyAccentGlow(textView);

        return textView;
    }

    private TextView createSecondaryAccentText(
            String text,
            float textSize,
            boolean bold
    ) {
        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(palette.secondaryAccent);

        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }

        applySecondaryGlow(textView);

        return textView;
    }

    private Button createPrimaryActionButton(String text) {
        Button button = new Button(this);

        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(palette.textDark);
        applyDarkTextShadow(button);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.03f);
        button.setPadding(dp(10), 0, dp(10), 0);

        button.setBackground(createRoundedBackground(
                palette.accent,
                palette.accentBright,
                20,
                1
        ));

        return button;
    }

    private Button createOutlineButton(String text) {
        Button button = new Button(this);

        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(palette.secondaryAccent);
        applySecondaryGlow(button);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.03f);
        button.setPadding(dp(10), 0, dp(10), 0);

        button.setBackground(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                20,
                1
        ));

        return button;
    }

    private EditText createThemeEditText(String hint) {
        EditText input = new EditText(this);

        input.setHint(hint);
        input.setHintTextColor(palette.textSecondary);
        input.setTextColor(palette.textPrimary);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(4), dp(12), dp(4));

        input.setBackground(createRoundedBackground(
                palette.panelStrong,
                palette.secondaryAccentDark,
                12,
                1
        ));

        return input;
    }

    private void updatePrimaryButtonEnabledStyle(
            Button button,
            boolean enabled
    ) {
        if (button == null) {
            return;
        }

        button.setAlpha(enabled ? 1.0f : 0.45f);
    }

    private void updateOutlineButtonEnabledStyle(
            Button button,
            boolean enabled
    ) {
        if (button == null) {
            return;
        }

        button.setAlpha(enabled ? 1.0f : 0.45f);

        button.setTextColor(
                enabled
                        ? palette.secondaryAccent
                        : palette.secondaryAccentDark
        );
    }

    private void applyPrimaryGlow(TextView textView) {
        applySpecificGlow(
                textView,
                palette.textPrimary,
                1.7f,
                110
        );
    }

    private void applyAccentGlow(TextView textView) {
        applySpecificGlow(
                textView,
                palette.accent,
                2.0f,
                135
        );
    }

    private void applySecondaryGlow(TextView textView) {
        applySpecificGlow(
                textView,
                palette.secondaryAccent,
                2.0f,
                150
        );
    }

    private void applySpecificGlow(
            TextView textView,
            int color,
            float radiusDp,
            int alpha
    ) {
        textView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        textView.setShadowLayer(
                dpFloat(radiusDp),
                0f,
                0f,
                withAlpha(color, alpha)
        );
    }

    private void applyDarkTextShadow(TextView textView) {
        textView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        textView.setShadowLayer(
                dpFloat(1.0f),
                0f,
                dpFloat(0.5f),
                Color.argb(140, 0, 0, 0)
        );
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private GradientDrawable createPanelBackground(
            int fillColor,
            int borderColor,
            int radiusDp
    ) {
        return createRoundedBackground(
                fillColor,
                borderColor,
                radiusDp,
                1
        );
    }

    private GradientDrawable createRoundedBackground(
            int fillColor,
            int strokeColor,
            int radiusDp,
            int strokeDp
    ) {
        GradientDrawable drawable = new GradientDrawable();

        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));

        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }

        return drawable;
    }

    private LinearLayout.LayoutParams createMarginLayoutParams(
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(width, height);

        params.setMargins(left, top, right, bottom);

        return params;
    }

    private LinearLayout.LayoutParams createMarginLayoutParams(
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float weight
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(width, height, weight);

        params.setMargins(left, top, right, bottom);

        return params;
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    createRoundedBackground(
                            palette.panelStrong,
                            palette.secondaryAccentDark,
                            18,
                            1
                    )
            );
        }

        int titleId = getResources().getIdentifier(
                "alertTitle",
                "id",
                "android"
        );

        TextView title = dialog.findViewById(titleId);

        if (title != null) {
            title.setTextColor(palette.textPrimary);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            applyPrimaryGlow(title);
        }

        int messageId = getResources().getIdentifier(
                "message",
                "id",
                "android"
        );

        TextView message = dialog.findViewById(messageId);

        if (message != null) {
            message.setTextColor(palette.textSecondary);
            applyAccentGlow(message);
        }

        Button negativeButton = dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
        );

        if (negativeButton != null) {
            negativeButton.setTextColor(palette.textSecondary);
        }

        Button positiveButton = dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
        );

        if (positiveButton != null) {
            positiveButton.setTextColor(palette.accent);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private float dpFloat(float value) {
        float density = getResources().getDisplayMetrics().density;
        return value * density;
    }

    private static class ThemePalette {
        private final int backgroundImageResId;
        private final int background;
        private final int screenOverlay;
        private final int panelBackground;
        private final int panelStrong;
        private final int padBackground;

        private final int accent;
        private final int accentBright;
        private final int accentDark;

        private final int textPrimary;
        private final int textSecondary;
        private final int textDark;

        private final int border;
        private final int partOff;

        private final int secondaryAccent;
        private final int secondaryAccentDark;
        private final int activeSecondaryText;

        private final int panelSoft;

        ThemePalette(
                int backgroundImageResId,
                int background,
                int screenOverlay,
                int panelBackground,
                int panelStrong,
                int padBackground,
                int accent,
                int accentBright,
                int accentDark,
                int textPrimary,
                int textSecondary,
                int textDark,
                int border,
                int partOff,
                int secondaryAccent,
                int secondaryAccentDark,
                int activeSecondaryText
        ) {
            this.backgroundImageResId = backgroundImageResId;
            this.background = background;
            this.screenOverlay = screenOverlay;
            this.panelBackground = panelBackground;
            this.panelStrong = panelStrong;
            this.padBackground = padBackground;
            this.accent = accent;
            this.accentBright = accentBright;
            this.accentDark = accentDark;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textDark = textDark;
            this.border = border;
            this.partOff = partOff;
            this.secondaryAccent = secondaryAccent;
            this.secondaryAccentDark = secondaryAccentDark;
            this.activeSecondaryText = activeSecondaryText;

            this.panelSoft = Color.argb(
                    165,
                    Color.red(panelStrong),
                    Color.green(panelStrong),
                    Color.blue(panelStrong)
            );
        }
    }

    private static class Bank {
        private final String name;
        private final List<Preset> presets = new ArrayList<>();

        Bank(String name) {
            this.name = name;
        }
    }

    private static class Preset {
        private String name;
        private int scene;
        private final boolean[] partStates;

        Preset(String name, int scene, boolean[] partStates) {
            this.name = name;
            this.scene = scene;
            this.partStates = new boolean[8];

            for (int i = 0; i < 8; i++) {
                if (i < partStates.length) {
                    this.partStates[i] = partStates[i];
                }
            }
        }

        boolean hasActivePart() {
            for (boolean partState : partStates) {
                if (partState) {
                    return true;
                }
            }

            return false;
        }

        String getActivePartsSummary() {
            StringBuilder result = new StringBuilder("PARTS: ");
            boolean hasActivePart = false;

            for (int i = 0; i < partStates.length; i++) {
                if (partStates[i]) {
                    if (hasActivePart) {
                        result.append(", ");
                    }

                    result.append(i + 1);
                    hasActivePart = true;
                }
            }

            if (!hasActivePart) {
                result.append("NENHUMA ATIVA");
            }

            return result.toString();
        }

        String getShortActivePartsSummary() {
            StringBuilder result = new StringBuilder("P: ");
            boolean hasActivePart = false;

            for (int i = 0; i < partStates.length; i++) {
                if (partStates[i]) {
                    if (hasActivePart) {
                        result.append(",");
                    }

                    result.append(i + 1);
                    hasActivePart = true;
                }
            }

            if (!hasActivePart) {
                result.append("OFF");
            }

            return result.toString();
        }
    }
}